<#
  Launches the standalone Forge instance in .\instance without a launcher.

  The official installer refuses to run without a launcher profile, and once it
  is installed the vanilla launcher wants an account. Neither is needed to just
  start the game, so this does what a launcher does: resolves the version json
  and its parent, applies the library rules for this OS, extracts the natives,
  substitutes the argument placeholders and calls the main class.

  Usage:
      .\tools\launch-instance.ps1
      .\tools\launch-instance.ps1 -VersionId 1.16.5-forge-36.2.42
      .\tools\launch-instance.ps1 -Kill      # stop a leftover copy first

  JAVA_HOME must be a JDK 8. Assets are read from the Gradle cache, which is
  where setup-instance.ps1 populated them from, so no separate download is
  needed.
#>
[CmdletBinding()]
param(
    [string] $VersionId = '1.16.5-forge-36.2.42',
    [string] $JavaHome = $env:JAVA_HOME,
    [string] $UserName = 'Dev',
    [switch] $NoWait,
    [switch] $Kill,
    [string[]] $ExtraJvmArgs = @()
)

$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectDir = [IO.Path]::GetFullPath((Join-Path $scriptDir '..'))
$instanceDir = Join-Path $projectDir 'instance'
$versionDir = Join-Path $instanceDir "versions\$VersionId"
$versionFile = Join-Path $versionDir "$VersionId.json"
$libraryDir = Join-Path $instanceDir 'libraries'
$nativesDir = Join-Path $versionDir 'natives'
# The vanilla asset index, as downloaded by ForgeGradle during the initial
# project sync. setup-instance.ps1 points the instance at the same place.
$assetsDir = Join-Path $env:USERPROFILE '.gradle\caches\forge_gradle\assets'

if (-not (Test-Path $versionFile)) { throw "version json not found: $versionFile" }
if (-not (Test-Path (Join-Path $JavaHome 'bin\java.exe'))) { throw "java not found: $JavaHome" }

# ---- refuse to start on top of a running copy -------------------------------
# A live client holds lwjgl.dll and friends open out of the natives directory.
# Deleting that directory then fails, the next ExtractToFile dies on a locked
# file, and the only thing the operator sees is a bare IOException naming a dll
# with no hint that the real problem is an already running game. Two clients on
# one game directory would also fight over options.txt and the world saves, so
# this stops rather than trying to cope.
$running = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -and $_.CommandLine -like "*$instanceDir*" })
if ($running.Count -gt 0) {
    Write-Host ''
    Write-Host "ERROR: this instance is already running." -ForegroundColor Red
    foreach ($p in $running) {
        Write-Host ("  pid {0}  started {1}" -f $p.ProcessId, $p.CreationDate) -ForegroundColor Red
    }
    Write-Host ''
    Write-Host "It has the natives directory locked, so a second copy cannot be" -ForegroundColor DarkGray
    Write-Host "started. Close the running game, or re-run with -Kill to end it." -ForegroundColor DarkGray
    if ($Kill) {
        foreach ($p in $running) {
            Write-Host ("  stopping pid {0}" -f $p.ProcessId) -ForegroundColor Yellow
            Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
        }
        Start-Sleep -Seconds 2
    } else {
        exit 1
    }
}

# ---- resolve the version chain --------------------------------------------
# Forge's json inherits from vanilla, so the real argument and library list is
# the parent first and the child merged on top, exactly like a launcher does.
$chain = @()
$current = Get-Content $versionFile -Raw | ConvertFrom-Json
while ($current) {
    $chain = ,$current + $chain
    if (-not $current.inheritsFrom) { break }
    $parentFile = Join-Path $instanceDir "versions\$($current.inheritsFrom)\$($current.inheritsFrom).json"
    if (-not (Test-Path $parentFile)) { break }
    $current = Get-Content $parentFile -Raw | ConvertFrom-Json
}
$version = $chain[-1]
Write-Host ("version chain: " + (($chain | ForEach-Object { $_.id }) -join ' <- ')) -ForegroundColor DarkGray
Write-Host ("main class   : " + $version.mainClass) -ForegroundColor DarkGray

# ---- library rules --------------------------------------------------------
function Test-OsRule {
    param($Os)
    if (-not $Os) { return $true }
    if ($Os.name -and $Os.name -ne 'windows') { return $false }
    if ($Os.arch) {
        # Mojang uses x86 for 32 bit and x86_64 for 64 bit; the JVM reports amd64.
        $is64 = ([Environment]::Is64BitProcess)
        if ($Os.arch -eq 'x86' -and $is64) { return $false }
        if ($Os.arch -eq 'x86_64' -and -not $is64) { return $false }
    }
    if ($Os.version -and $Os.version -ne '^10\.') { return $false }
    return $true
}

function Test-LibraryAllowed {
    param($Lib)
    if (-not $Lib.rules) { return $true }
    $allowed = $false
    foreach ($rule in $Lib.rules) {
        if (-not (Test-OsRule $rule.os)) { continue }
        $allowed = ($rule.action -eq 'allow')
    }
    return $allowed
}

# group:artifact:version[:classifier][@ext] -> libraries/<path>
function Get-LibraryPath {
    param([string]$Name, [string]$Extension = 'jar')
    $parts = $Name.Split(':')
    $group = $parts[0]
    $artifact = $parts[1]
    $version = $parts[2]
    $classifier = if ($parts.Count -gt 3) { $parts[3] } else { '' }
    $file = if ($classifier) { "$artifact-$version-$classifier.$Extension" } else { "$artifact-$version.$Extension" }
    return Join-Path $libraryDir (($group -replace '\.', '\') + "\" + $artifact + "\" + $version + "\" + $file)
}

# Keyed by group:artifact, not by file path, so that the child entry replaces the
# parent's instead of sitting next to it. Without this the classpath ends up with
# both the vanilla log4j 2.8.1 and the Forge log4j 2.15.0 that ModLauncher
# needs, and startup dies with NoSuchMethodError on ThrowablePatternConverter.
$resolved = [ordered]@{}
$nativeJars = New-Object System.Collections.Generic.List[string]

foreach ($entry in $chain) {
    foreach ($lib in $entry.libraries) {
        if (-not (Test-LibraryAllowed $lib)) { continue }
        if ($lib.name -match '^native') { continue }
        $key = ($lib.name.Split(':')[0..1] -join ':')
        $path = Get-LibraryPath $lib.name
        $resolved[$key] = $lib

        $natives = $lib.natives
        if ($natives) {
            $marker = if ($natives -is [string]) { $natives } else { $natives.windows }
            if ($marker) {
                # The natives are not inside the main artifact, they ship as a
                # separate classifier jar such as lwjgl-3.2.2-natives-windows.
                # Extracting from the main jar yields hundreds of files and not
                # a single .dll.
                $classifier = $null
                if ($lib.downloads -and $lib.downloads.classifiers) {
                    $classifier = $lib.downloads.classifiers.$marker
                }
                if ($classifier) {
                    $nativeJars.Add((Join-Path $libraryDir ($classifier.path -replace '/', '\')))
                } else {
                    Write-Host ("  no natives classifier for {0} [{1}]" -f $lib.name, $marker) -ForegroundColor DarkYellow
                }
            }
        }
    }
}

$classpath = New-Object System.Collections.Generic.List[string]
foreach ($key in $resolved.Keys) {
    $path = Get-LibraryPath $resolved[$key].name
    if (Test-Path $path) { $classpath.Add($path) }
    else { Write-Host ("  MISSING library: {0}" -f $path) -ForegroundColor DarkYellow }
}
$classpath.Add((Join-Path $versionDir "$VersionId.jar"))

# ---- natives --------------------------------------------------------------
if ($nativeJars.Count -gt 0) {
    Remove-Item $nativesDir -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path $nativesDir | Out-Null
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $extracted = 0
    foreach ($jar in $nativeJars) {
        if (-not (Test-Path $jar)) {
            Write-Host ("  MISSING natives jar: {0}" -f $jar) -ForegroundColor Red
            continue
        }
        $zip = [IO.Compression.ZipFile]::OpenRead($jar)
        try {
            foreach ($entry in $zip.Entries) {
                if ($entry.FullName -like 'META-INF/*') { continue }
                if ($entry.Name -eq '') { continue }
                $dest = Join-Path $nativesDir $entry.Name
                [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $dest, $true)
                $extracted++
            }
        } finally { $zip.Dispose() }
    }
    Write-Host ("natives: {0} files from {1} jars" -f $extracted, $nativeJars.Count) -ForegroundColor DarkGray
    $dlls = (Get-ChildItem $nativesDir -Filter *.dll -ErrorAction SilentlyContinue).Count
    if ($dlls -eq 0) { throw "no .dll extracted into $nativesDir" }
    Write-Host ("natives: {0} .dll present" -f $dlls) -ForegroundColor DarkGray
}

# ---- arguments ------------------------------------------------------------
$uuid = '00000000-0000-0000-0000-000000000000'
$replacements = [ordered]@{
    '${natives_directory}'      = $nativesDir
    '${library_directory}'      = $libraryDir
    '${classpath}'              = ($classpath -join ';')
    '${classpath_separator}'    = ';'
    '${version_name}'           = $VersionId
    '${version_type}'           = $VersionId
    '${game_directory}'         = $instanceDir
    '${assets_root}'            = $assetsDir
    '${assets_index_name}'      = '1.16'
    '${auth_player_name}'       = $UserName
    '${auth_access_token}'      = 'DONT_CRASH'
    '${auth_uuid}'              = $uuid
    '${auth_session}'           = 'token:DONT_CRASH:'.$uuid
    '${user_properties}'        = '{}'
    '${user_properties_json}'   = '{}'
    '${user_type}'              = 'legacy'
    '${launcher_name}'          = 'prismatica'
    '${launcher_version}'       = '0.1'
    '${resolution_width}'       = '1920'
    '${resolution_height}'      = '1080'
    '${quickPlayPath}'          = ''
    '${quickPlaySingleplayer}'  = ''
    '${quickPlayMultiplayer}'   = ''
    '${quickPlayRealms}'        = ''
}

function Expand-Arg {
    param([string]$Value)
    foreach ($key in $replacements.Keys) { $Value = $Value.Replace($key, $replacements[$key]) }
    return $Value
}

# The arguments lists are per entry and entries can hold either a plain string
# or an object with rules, so both shapes have to be walked.
function Collect-Args {
    param($Node)
    $out = @()
    foreach ($item in $Node) {
        if ($item -is [string]) { $out += $item; continue }
        $ok = $true
        if ($item.rules) {
            $ok = $false
            foreach ($rule in $item.rules) {
                if (Test-OsRule $rule.os) { $ok = ($rule.action -eq 'allow') }
            }
        }
        if (-not $ok) { continue }
        $value = $item.value
        if ($value -is [string]) { $out += $value } else { $out += $value }
    }
    return $out
}

$jvmArgs = @()
$gameArgs = @()
foreach ($entry in $chain) {
    if (-not $entry.arguments) { continue }
    if ($entry.arguments.jvm)  { $jvmArgs  += Collect-Args $entry.arguments.jvm }
    if ($entry.arguments.game) { $gameArgs += Collect-Args $entry.arguments.game }
}

$finalJvm = @()
foreach ($a in $jvmArgs) { if ($a.Trim()) { $finalJvm += (Expand-Arg $a) } }
$finalGame = @()
foreach ($a in $gameArgs) { $finalGame += (Expand-Arg $a) }

# The vanilla version json always lists --demo; a real launcher only passes it for
# a demo account. Forwarding it verbatim starts the game in demo mode, which
# recreates the demo world on every launch and shows Play Demo World on the main
# menu. This instance plays offline, so the flag is dropped.
$beforeGame = $finalGame.Count
$finalGame = @($finalGame | Where-Object { $_ -ne '--demo' })
if ($finalGame.Count -ne $beforeGame) {
    Write-Host 'dropped --demo (vanilla json always carries it, we are not a demo account)' -ForegroundColor DarkGray
}

Write-Host ("classpath: {0} entries" -f $classpath.Count) -ForegroundColor DarkGray
Write-Host ("jvm args : {0}" -f $finalJvm.Count) -ForegroundColor DarkGray

$java = Join-Path $JavaHome 'bin\java.exe'
$logFile = Join-Path $instanceDir 'logs\launcher-stdout.log'
New-Item -ItemType Directory -Force -Path (Split-Path $logFile) | Out-Null
$all = @()
$all += '-Xmx4G'
$all += $finalJvm
$all += $ExtraJvmArgs
$all += $version.mainClass
$all += $finalGame

Write-Host ''
Write-Host ("launching {0} as {1} ..." -f $VersionId, $UserName) -ForegroundColor Green
Write-Host ("log file : {0}" -f $logFile) -ForegroundColor DarkGray
Write-Host ''

# The log is teed rather than just printed. Vanilla's log4j 2.8.1 is dropped from
# the classpath below, so without a config file of its own it writes straight to
# stdout and leaves no logs/latest.log behind; piping into a file is the only
# way to read a crash back after the window has closed.
if ($NoWait) {
    Start-Process -FilePath $java -ArgumentList $all -WorkingDirectory $instanceDir `
        -RedirectStandardOutput "$logFile" -RedirectStandardError "$logFile.err"
    Write-Host 'started in the background' -ForegroundColor Cyan
} else {
    & $java @all 2>&1 | Tee-Object -FilePath $logFile
    Write-Host ("exit code: {0}" -f $LASTEXITCODE) -ForegroundColor DarkGray
    Write-Host ("log file : {0}" -f $logFile) -ForegroundColor DarkGray
}
