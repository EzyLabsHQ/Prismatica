<#
  Builds the client-side visual pack for Minecraft 1.16.5 + Forge.

  * mods    - client side visual / performance mods, verified to have a real
              Forge 1.16.5 build on Modrinth, each download checked against the
              SHA1 the API reports.
  * shaders - shader packs from their GitHub releases.

  Nothing here automates gameplay: interface, performance and cosmetics only.
#>
[CmdletBinding()]
param(
    [string] $MinecraftVersion = '1.16.5',
    [string] $Loader           = 'forge',
    [string] $BaseDir          = ''
)

$ErrorActionPreference = 'Stop'

if (-not $BaseDir) {
    $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
    $BaseDir   = [IO.Path]::GetFullPath((Join-Path $scriptDir '..'))
}
$ModsDir    = Join-Path $BaseDir 'pack\mods'
$ShadersDir = Join-Path $BaseDir 'pack\shaderpacks'
New-Item -ItemType Directory -Force -Path $ModsDir, $ShadersDir | Out-Null

# Only slugs that really have a Forge 1.16.5 build were kept here, verified
# against the Modrinth API.
$wanted = [ordered]@{
    'ferrite-core'         = 'performance: smaller and faster memory usage'
    'modernfix'            = 'performance: many small optimisations and fixes'
    'spark'                = 'diagnostics: profiler, low overhead'
    'fps-reducer'          = 'performance: caps FPS when the window is not focused'
    'appleskin'            = 'HUD: hunger and saturation bars'
    'journeymap'           = 'visuals: full map, waypoints, minimap'
    'betterf3'             = 'HUD: better F3 debug overlay'
    'entitytexturefeatures'= 'visuals: random emissive textures on mobs'
    'customskinloader'     = 'visuals: 3D skin layers, capes, slim arms'
    'better-third-person'  = 'visuals: reworked third person camera'
    'controlling'          = 'interface: search and filter key bindings'
}

function Get-ModrinthVersion {
    param([string]$Slug, [string]$GameVersion, [string]$LoaderFilter)

    $url = 'https://api.modrinth.com/v2/project/{0}/version?loaders=["{1}"]&game_versions=["{2}"]' -f $Slug, $LoaderFilter, $GameVersion
    try {
        $resp = Invoke-RestMethod -Uri $url -UseBasicParsing -TimeoutSec 60 `
                 -Headers @{ 'User-Agent' = 'prismatica-pack/0.1 (local build script)' }
    } catch {
        return $null
    }
    if (-not $resp) { return $null }
    return @($resp) | Sort-Object date_published -Descending | Select-Object -First 1
}

$ok  = @()
$bad = @()

foreach ($slug in $wanted.Keys) {
    $version = Get-ModrinthVersion -Slug $slug -GameVersion $MinecraftVersion -LoaderFilter $Loader
    if (-not $version) {
        $bad += $slug
        Write-Host ("  MISS  {0,-22} no {1} build for {2}" -f $slug, $Loader, $MinecraftVersion) -ForegroundColor DarkYellow
        continue
    }

    $file     = $version.files | Select-Object -First 1
    $target   = Join-Path $ModsDir $file.filename
    $expected = $file.hashes.sha1.ToLower()

    if ((Test-Path $target) -and ((Get-FileHash $target -Algorithm SHA1).Hash.ToLower() -eq $expected)) {
        $ok += [pscustomobject]@{ Mod = $slug; File = $file.filename; Version = $version.version_number; Status = 'cached' }
        Write-Host ("  OK    {0,-22} {1}" -f $slug, $file.filename) -ForegroundColor DarkGreen
        continue
    }

    try {
        Invoke-WebRequest -Uri $file.url -OutFile $target -UseBasicParsing -TimeoutSec 300
        $actual = (Get-FileHash $target -Algorithm SHA1).Hash.ToLower()
        if ($actual -ne $expected) { throw "SHA1 mismatch: expected $expected got $actual" }
        $ok += [pscustomobject]@{ Mod = $slug; File = $file.filename; Version = $version.version_number; Status = 'downloaded' }
        Write-Host ("  OK    {0,-22} {1}" -f $slug, $file.filename) -ForegroundColor Green
    } catch {
        if (Test-Path $target) { Remove-Item $target -Force }
        $bad += $slug
        Write-Host ("  FAIL  {0,-22} {1}" -f $slug, $_.Exception.Message) -ForegroundColor Red
    }
}

# ---- shader packs -------------------------------------------------------
# Shader packs have no loader in the API, they are filtered by game version only.
$shaders = [ordered]@{
    'bsl-shaders'                = 'visuals: BSL 8, the most used OptiFine shader pack'
    'complementary-reimagined'   = 'visuals: Complementary Reimagined, softer lighting'
}
$headers = @{ 'User-Agent' = 'prismatica-pack/0.1' }
foreach ($slug in $shaders.Keys) {
    try {
        $url = 'https://api.modrinth.com/v2/project/{0}/version?game_versions=["{1}"]' -f $slug, $MinecraftVersion
        $resp = Invoke-RestMethod -Uri $url -Headers $headers -TimeoutSec 60
        $version = @($resp) | Sort-Object date_published -Descending | Select-Object -First 1
        if (-not $version) { throw "no $MinecraftVersion build" }

        $file     = $version.files | Where-Object { $_.filename -match '\.zip$' } | Select-Object -First 1
        $target   = Join-Path $ShadersDir $file.filename
        $expected = $file.hashes.sha1.ToLower()

        if (-not ((Test-Path $target) -and ((Get-FileHash $target -Algorithm SHA1).Hash.ToLower() -eq $expected))) {
            Invoke-WebRequest -Uri $file.url -OutFile $target -UseBasicParsing -TimeoutSec 600
            $actual = (Get-FileHash $target -Algorithm SHA1).Hash.ToLower()
            if ($actual -ne $expected) { throw "SHA1 mismatch" }
        }
        Write-Host ("  SHDR  {0,-22} {1}" -f $slug, $file.filename) -ForegroundColor Green
    } catch {
        Write-Host ("  SHDR  {0,-22} {1}" -f $slug, $_.Exception.Message) -ForegroundColor DarkYellow
    }
}

Write-Host ''
Write-Host ("mods ok: {0}   missing: {1}" -f $ok.Count, $bad.Count) -ForegroundColor Cyan
if ($bad.Count) { Write-Host ('missing: ' + ($bad -join ', ')) }
Write-Host ("mods    -> {0}" -f $ModsDir)
Write-Host ("shaders -> {0}" -f $ShadersDir)
Write-Host ''
Write-Host 'OptiFine is not on Modrinth and needs a manual download:' -ForegroundColor Cyan
Write-Host '  https://www.optifine.net/downloadOptiFine?f=OptiFine_1.16.5_HD_U_G8.jar'
Write-Host ''
$ok | Sort-Object Mod | Format-Table -AutoSize
