<#
  Recreates the standalone Forge instance in .\instance from nothing.

  This exists because instance\ is gitignored: 196 MB of libraries, natives,
  shader packs and a Minecraft jar. Everything the directory contains is
  generated here, so it can always be thrown away and rebuilt.

  Steps, all verified:
    1. vanilla 1.16.5 client jar and its version json from Mojang
    2. every library the version needs, plus the separate natives classifier
       jars, each checked against the SHA1 Mojang publishes
    3. a minimal launcher profile, without which the Forge installer refuses
    4. the Forge 36.2.42 installer, which patches the client jar in place
    5. the mod pack and the built Prismatica jar
    6. showLoadWarnings = false, so Forge does not stop on the LazyDFU notice

  Usage:
      .\tools\setup-instance.ps1
#>
[CmdletBinding()]
param(
    [string] $MinecraftVersion = '1.16.5',
    [string] $ForgeVersion     = '36.2.42',
    [string] $JavaHome          = $env:JAVA_HOME,
    [string] $AssetsDir         = (Join-Path $env:USERPROFILE '.gradle\caches\forge_gradle\assets')
)

$ErrorActionPreference = 'Stop'

$scriptDir  = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectDir = [IO.Path]::GetFullPath((Join-Path $scriptDir '..'))
$instance   = Join-Path $projectDir 'instance'
$versionId  = "$MinecraftVersion-forge-$ForgeVersion"
$versionDir = Join-Path $instance "versions\$versionId"
$vanillaDir = Join-Path $instance "versions\$MinecraftVersion"
$libraryDir = Join-Path $instance 'libraries'
$modsDir    = Join-Path $instance 'mods'
$shadersDir = Join-Path $instance 'shaderpacks'

$apiHeaders  = @{ 'User-Agent' = 'prismatica-setup/0.1 (local build script)' }
$mirror      = 'https://libraries.minecraft.net/'

function Write-Step([string]$Text) {
    Write-Host ''
    Write-Host "== $Text" -ForegroundColor Cyan
}

function Get-FileOrDownload {
    param([string]$Url, [string]$Target, [string]$Sha1)

    if ((Test-Path $Target) -and $Sha1) {
        if ((Get-FileHash $Target -Algorithm SHA1).Hash.ToLower() -eq $Sha1.ToLower()) {
            return $true
        }
    }
    New-Item -ItemType Directory -Force -Path (Split-Path $Target) | Out-Null
    Invoke-WebRequest -Uri $Url -OutFile $Target -UseBasicParsing -TimeoutSec 900
    if ($Sha1) {
        $actual = (Get-FileHash $Target -Algorithm SHA1).Hash.ToLower()
        if ($actual -ne $Sha1.ToLower()) {
            throw "SHA1 mismatch for $Target: expected $($Sha1.ToLower()) got $actual"
        }
    }
    return $true
}

New-Item -ItemType Directory -Force -Path $vanillaDir, $libraryDir, $modsDir, $shadersDir | Out-Null

# ---- 1. the vanilla version definition ------------------------------------
Write-Step 'vanilla version json'
$manifest = Invoke-RestMethod -Uri 'https://launchermeta.mojang.com/mc/game/version_manifest_v2.json' -Headers $apiHeaders
$entry = $manifest.versions | Where-Object { $_.id -eq $MinecraftVersion } | Select-Object -First 1
if (-not $entry) { throw "$MinecraftVersion is not in the Mojang version manifest" }
$version = Invoke-RestMethod -Uri $entry.url -Headers $apiHeaders
Write-Host "  id=$($version.id) mainClass=$($version.mainClass) libraries=$(($version.libraries | Measure-Object).Count)"

# ---- 2. the client jar and every library ----------------------------------
Write-Step 'client jar'
$client = $version.downloads.client
$clientTarget = Join-Path $vanillaDir "$MinecraftVersion.jar"
Get-FileOrDownload -Url $client.url -Target $clientTarget -Sha1 $client.sha1 | Out-Null
Write-Host "  ok  $MinecraftVersion.jar  sha1 $($client.sha1)"

$versionTarget = Join-Path $vanillaDir "$MinecraftVersion.json"
if (-not (Test-Path $versionTarget)) {
    Invoke-WebRequest -Uri $entry.url -OutFile $versionTarget -UseBasicParsing -TimeoutSec 120
}
Write-Host "  ok  $MinecraftVersion.json"

Write-Step 'libraries and natives'
$libs = 0
$natives = 0
foreach ($lib in $version.libraries) {
    if ($lib.downloads -and $lib.downloads.artifact) {
        $rel = $lib.downloads.artifact.path -replace '/', '\'
        Get-FileOrDownload -Url ($mirror + $lib.downloads.artifact.path) `
                            -Target (Join-Path $libraryDir $rel) `
                            -Sha1 $lib.downloads.artifact.sha1 | Out-Null
        $libs++
    }
    # Natives live in a separate classifier jar, not inside the main artifact.
    # Fetching only the artifact yields hundreds of files and not a single .dll.
    if ($lib.natives) {
        $marker = if ($lib.natives -is [string]) { $lib.natives } else { $lib.natives.windows }
        $classifier = if ($marker) { $lib.downloads.classifiers.$marker } else { $null }
        if ($classifier) {
            $rel = $classifier.path -replace '/', '\'
            Get-FileOrDownload -Url ($mirror + $classifier.path) `
                                -Target (Join-Path $libraryDir $rel) `
                                -Sha1 $classifier.sha1 | Out-Null
            $natives++
        }
    }
}
Write-Host "  ok  $libs libraries, $natives natives jars"

# ---- 3. a launcher profile, which the Forge installer insists on -----------
Write-Step 'launcher profile'
$profilePath = Join-Path $instance 'launcher_profiles.json'
if (-not (Test-Path $profilePath)) {
    $profile = [ordered]@{
        profiles = [ordered]@{
            'prismatica-dev' = [ordered]@{
                name          = 'prismatica-dev'
                type          = 'custom'
                created       = (Get-Date).ToString('yyyyMMdd')
                lastVersionId = $MinecraftVersion
                lastUsed      = (Get-Date).ToString('yyyyMMdd')
                gameDir       = '.'
                javaArgs      = ''
                javaArgsJVM   = ''
                createdBy     = 'prismatica'
                icon          = 'Grass'
            }
        }
        selectedProfile      = 'prismatica-dev'
        clientToken          = 'prismatica'
        authenticationDatabase = @{}
        profilesVersion      = 1
        lastVersionId        = $MinecraftVersion
        launcherVersion      = [ordered]@{ name = 'prismatica'; format = 21 }
    }
    # UTF8 without BOM: the Forge installer reads this with a strict parser.
    $json = $profile | ConvertTo-Json -Depth 8
    [System.IO.File]::WriteAllText($profilePath, $json, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host '  written'
} else {
    Write-Host '  already present'
}

# ---- 4. Forge itself -------------------------------------------------------
Write-Step "Forge $MinecraftVersion-$ForgeVersion"
$installer = Join-Path $instance "forge-$MinecraftVersion-$ForgeVersion-installer.jar"
if (-not (Test-Path (Join-Path $versionDir "$versionId.json"))) {
    $installerUrl = "https://maven.minecraftforge.net/net/minecraftforge/forge/$MinecraftVersion-$ForgeVersion/forge-$MinecraftVersion-$ForgeVersion-installer.jar"
    Get-FileOrDownload -Url $installerUrl -Target $installer -Sha1 $null | Out-Null
    Write-Host "  downloaded installer ($([math]::Round((Get-Item $installer).Length / 1MB, 1)) MB)"
    Push-Location $instance
    & (Join-Path $JavaHome 'bin\java.exe') -jar $installer --installClient
    if ($LASTEXITCODE -ne 0) { Pop-Location; throw "Forge installer failed with exit code $LASTEXITCODE" }
    Pop-Location
    Write-Host '  installed'
} else {
    Write-Host '  already installed'
}

# ---- 5. the mods -----------------------------------------------------------
Write-Step 'mods'
$packMods = Join-Path $projectDir 'pack\mods'
$jar = Join-Path $projectDir "build\libs\prismatica-$((Get-Content (Join-Path $projectDir 'gradle.properties') | Select-String '^mod_version=').Line -replace '^mod_version=', '').jar"
Get-ChildItem "$packMods\*.jar" -ErrorAction SilentlyContinue | ForEach-Object {
    Copy-Item $_.FullName (Join-Path $modsDir $_.Name) -Force
}
if (Test-Path $jar) {
    Copy-Item $jar (Join-Path $modsDir (Split-Path $jar -Leaf)) -Force
} else {
    Write-Host "  WARNING: $jar not found, run 'gradlew build' first so Prismatica is missing" -ForegroundColor Yellow
}
Write-Host "  $(@(Get-ChildItem "$modsDir\*.jar" -ErrorAction SilentlyContinue).Count) mods"

$packShaders = Join-Path $projectDir 'pack\shaderpacks'
Get-ChildItem "$packShaders\*.zip" -ErrorAction SilentlyContinue | ForEach-Object {
    Copy-Item $_.FullName (Join-Path $shadersDir $_.Name) -Force
}
Write-Host "  $(@(Get-ChildItem "$shadersDir\*.zip" -ErrorAction SilentlyContinue).Count) shader packs"

# ---- 6. quiet the load warning --------------------------------------------
Write-Step 'forge-client.toml'
# Byte level, never Set-Content: PowerShell 5.1 writes a BOM and nightconfig
# then refuses the file with "Invalid bare key: ?", which kills startup.
$forgeClient = Join-Path $instance 'config\forge-client.toml'
if (Test-Path $forgeClient) {
    $bytes = [System.IO.File]::ReadAllBytes($forgeClient)
    $offset = 0
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) { $offset = 3 }
    $text = [System.Text.Encoding]::UTF8.GetString($bytes, $offset, $bytes.Length - $offset)
    if ($text -match 'showLoadWarnings\s*=\s*true') {
        $text = $text -replace 'showLoadWarnings\s*=\s*true', 'showLoadWarnings = false'
    } elseif ($text -notmatch 'showLoadWarnings') {
        # Only write the key if Forge has not generated the file yet.
        $text = "[client]`r`n`tshowLoadWarnings = false`r`n" + $text
    }
    [System.IO.File]::WriteAllText($forgeClient, $text, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host '  showLoadWarnings = false'
} else {
    Write-Host '  not generated yet, will be handled on first launch' -ForegroundColor DarkGray
}

Write-Step 'done'
Write-Host "instance -> $instance"
Write-Host "launch    -> .\tools\launch-instance.ps1"
Write-Host "assets    -> $AssetsDir (reused from the Gradle cache, no need to download 300 MB)"
Write-Host ''
