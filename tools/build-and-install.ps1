<#
  Builds Prismatica and copies the jar into the test instance.

  Exists because the two steps were being chained by hand, and a hand chained
  "build; copy" copies whatever jar happens to be in build/libs even when the
  build failed. That shipped a stale jar into the instance and looked like a
  successful install, which sent a whole diagnosis of the mouse click bug down
  a wrong path: the logging that was supposed to explain it was never in the
  running game at all.

  This refuses to install on a failed build, and says which jar it installed.

  Usage:
      .\tools\build-and-install.ps1
      .\tools\build-and-install.ps1 -NoInstall
#>
[CmdletBinding()]
param(
    [switch] $NoInstall
)

$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectDir = [IO.Path]::GetFullPath((Join-Path $scriptDir '..'))
$instanceMods = Join-Path $projectDir 'instance\mods'

if (-not $env:JAVA_HOME) {
    throw 'JAVA_HOME is not set. This project targets Java 8; point it at a JDK 8.'
}

Write-Host "building ..." -ForegroundColor Cyan
Push-Location $projectDir
try {
    & .\gradlew.bat build --offline
} finally {
    Pop-Location
}

# $LASTEXITCODE is gradlew's own status. Checking it is the entire point of this
# script: without the check the copy below runs on a failed build.
if ($LASTEXITCODE -ne 0) {
    Write-Host ''
    Write-Host ("BUILD FAILED (exit {0}) - not installing." -f $LASTEXITCODE) -ForegroundColor Red
    Write-Host 'The jar in build\libs is the previous one. Installing it would' -ForegroundColor DarkGray
    Write-Host 'put a stale build in the instance while looking like a fresh one.' -ForegroundColor DarkGray
    exit $LASTEXITCODE
}

$jar = Get-ChildItem (Join-Path $projectDir 'build\libs') -Filter '*.jar' |
    Where-Object { $_.Name -notmatch 'sources|javadoc' } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if (-not $jar) {
    Write-Host 'BUILD OK but no jar in build\libs' -ForegroundColor Red
    exit 1
}

Write-Host ''
Write-Host ("build ok: {0}  {1} bytes  {2}" -f $jar.Name, $jar.Length, $jar.LastWriteTime) -ForegroundColor Green

if ($NoInstall) {
    Write-Host 'not installing (-NoInstall)' -ForegroundColor DarkGray
    exit 0
}

if (-not (Test-Path $instanceMods)) {
    Write-Host ("instance\mods not found: {0}" -f $instanceMods) -ForegroundColor Red
    Write-Host 'Build the instance first with tools\setup-instance.ps1.' -ForegroundColor DarkGray
    exit 1
}

# Replace by name rather than adding, so a renamed or versioned jar cannot
# leave two copies of the mod in the folder.
Get-ChildItem $instanceMods -Filter 'prismatica*.jar' -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -ne $jar.Name } |
    ForEach-Object {
        Write-Host ("removing older {0}" -f $_.Name) -ForegroundColor DarkGray
        Remove-Item $_.FullName -Force
    }
Copy-Item $jar.FullName $instanceMods -Force
Write-Host ("installed {0} -> {1}" -f $jar.Name, $instanceMods) -ForegroundColor Green
