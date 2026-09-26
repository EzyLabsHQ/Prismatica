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
    # Sodium itself has no Forge 1.16.5 build: the only 1.16.x releases of it
    # are Fabric only. Rubidium is the same renderer by the same author
    # (CaffeineMC) and is what it was called before the 1.17 rename, LGPL-3.0
    # rather than the Polyform Shield licence Sodium switched to.
    'rubidium'             = 'performance: Sodium, the 1.16.5 renderer rewrite'
    'ferrite-core'         = 'performance: smaller and faster memory usage'
    'modernfix'            = 'performance: many small optimisations and fixes'
    'spark'                = 'diagnostics: profiler, low overhead'
    'fps-reducer'          = 'performance: caps FPS when the window is not focused'
    'appleskin'            = 'HUD: hunger and saturation bars'
    'journeymap'           = 'visuals: full map, waypoints, minimap'
    'betterf3'             = 'HUD: better F3 debug overlay'
    'entitytexturefeatures'= 'visuals: random emissive textures on mobs'
    # customskinloader is deliberately absent: 15.0.1 on 1.16.5 / Forge 36.2.42
    # matches its skin-manager patch but rewrites nothing, so it throws during
    # Minecraft.<init>. Its own -Dcustomskinloader.ignorePatchFailure flag does
    # not fix it, it just swaps the failure for NoClassDefFoundError on
    # customskinloader/fake/FakeSkinManager.
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

function Get-ModrinthSlug {
    param([string]$ProjectId)

    try {
        $p = Invoke-RestMethod -Uri ('https://api.modrinth.com/v2/project/{0}' -f $ProjectId) `
                 -UseBasicParsing -TimeoutSec 60 `
                 -Headers @{ 'User-Agent' = 'prismatica-pack/0.1 (local build script)' }
        return $p.slug
    } catch {
        return $null
    }
}

# ---- resolve the dependency graph ------------------------------------------
# A missing required dependency is not a warning, FML refuses to start: the
# game dies with "Mod journeymap requires commonnetworking 1.0.5 or above". So
# walk the graph instead of trusting the hand written list. journeymap pulls in
# common-network and betterf3 pulls in cloth-config, neither of which is in
# $wanted.
$planned    = [ordered]@{}
$isDep      = @{}
$queue      = New-Object System.Collections.Generic.Queue[string]
foreach ($slug in $wanted.Keys) { $queue.Enqueue($slug) }

while ($queue.Count -gt 0) {
    $slug = $queue.Dequeue()
    if ($planned.Contains($slug)) { continue }

    $version = Get-ModrinthVersion -Slug $slug -GameVersion $MinecraftVersion -LoaderFilter $Loader
    $planned[$slug] = $version
    if (-not $version) { continue }

    foreach ($dep in @($version.dependencies)) {
        if ($dep.dependency_type -ne 'required') { continue }
        if (-not $dep.project_id) {
            Write-Host ("  DEPF  {0,-22} needs file-only dep {1} (not on Modrinth)" -f $slug, $dep.file_name) -ForegroundColor DarkYellow
            continue
        }
        $depSlug = Get-ModrinthSlug -ProjectId $dep.project_id
        if (-not $depSlug) {
            Write-Host ("  DEPX  {0,-22} dep {1} could not be resolved" -f $slug, $dep.project_id) -ForegroundColor DarkYellow
            continue
        }
        if (-not $planned.Contains($depSlug)) {
            $isDep[$depSlug] = $true
            $queue.Enqueue($depSlug)
        }
    }
}

$ok  = @()
$bad = @()

foreach ($slug in $planned.Keys) {
    $version = $planned[$slug]
    $tag = if ($isDep.ContainsKey($slug)) { 'dep' } else { 'mod' }
    if (-not $version) {
        $bad += $slug
        Write-Host ("  MISS  {0,-22} no {1} build for {2}" -f $slug, $Loader, $MinecraftVersion) -ForegroundColor DarkYellow
        continue
    }

    $file     = $version.files | Select-Object -First 1
    $target   = Join-Path $ModsDir $file.filename
    $expected = $file.hashes.sha1.ToLower()

    if ((Test-Path $target) -and ((Get-FileHash $target -Algorithm SHA1).Hash.ToLower() -eq $expected)) {
        $ok += [pscustomobject]@{ Kind = $tag; Mod = $slug; File = $file.filename; Version = $version.version_number; Status = 'cached' }
        Write-Host ("  OK    {0,-22} {1}" -f $slug, $file.filename) -ForegroundColor DarkGreen
        continue
    }

    try {
        Invoke-WebRequest -Uri $file.url -OutFile $target -UseBasicParsing -TimeoutSec 300
        $actual = (Get-FileHash $target -Algorithm SHA1).Hash.ToLower()
        if ($actual -ne $expected) { throw "SHA1 mismatch: expected $expected got $actual" }
        $ok += [pscustomobject]@{ Kind = $tag; Mod = $slug; File = $file.filename; Version = $version.version_number; Status = 'downloaded' }
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

# ---- shader packs outside Modrinth ----------------------------------------
# Pinned by hand. These are not in the Modrinth API, so the SHA1 was taken from
# the file after the first download: if the upstream file ever changes, the
# check below fails on purpose rather than silently pulling a different pack.
$direct = [ordered]@{
    'IterationT-3.2.0.zip' = @{
        # Served by minecraft-inside.ru, which 302s back to the article page
        # unless the request looks like it came from the article.
        Url  = 'https://minecraft-inside.ru/uploads/files/2025-05/iterationT3.2.0.zip'
        Ref  = 'https://minecraft-inside.ru/shaders/179320-iterationt.html'
        Sha1 = 'ddadd31e7c59cedadc1e704e5f83c872cb3d5e37'
        Note = 'visuals: IterationT 3.2.0 by L1MA, shaders.properties sets G8 for 1.16.5'
    }
}
$directHeaders = @{ 'User-Agent' = 'prismatica-pack/0.1 (local build script)' }
foreach ($name in $direct.Keys) {
    $spec   = $direct[$name]
    $target = Join-Path $ShadersDir $name
    try {
        if (-not ((Test-Path $target) -and ((Get-FileHash $target -Algorithm SHA1).Hash.ToLower() -eq $spec.Sha1))) {
            $h = $directHeaders.Clone()
            if ($spec.Ref) { $h['Referer'] = $spec.Ref }
            Invoke-WebRequest -Uri $spec.Url -Headers $h -OutFile $target -UseBasicParsing -TimeoutSec 900
            $actual = (Get-FileHash $target -Algorithm SHA1).Hash.ToLower()
            if ($actual -ne $spec.Sha1) { throw "SHA1 mismatch: expected $($spec.Sha1) got $actual" }
        }
        Write-Host ("  SHDR  {0,-22} {1}" -f $name, $spec.Note) -ForegroundColor Green
    } catch {
        if (Test-Path $target) { Remove-Item $target -Force }
        Write-Host ("  SHDR  {0,-22} {1}" -f $name, $_.Exception.Message) -ForegroundColor Red
    }
}

Write-Host ''
Write-Host ("mods ok: {0}   missing: {1}" -f $ok.Count, $bad.Count) -ForegroundColor Cyan
if ($bad.Count) { Write-Host ('missing: ' + ($bad -join ', ')) }
Write-Host ("mods    -> {0}" -f $ModsDir)
Write-Host ("shaders -> {0}" -f $ShadersDir)
Write-Host ''
Write-Host 'OptiFine is not on Modrinth and has to be downloaded by hand:' -ForegroundColor Cyan
Write-Host '  https://www.optifine.net/downloads  ->  1.16.5, HD_U_G8'
Write-Host 'The direct jar link no longer resolves: optifine.net now sends downloads'
Write-Host 'through an adfoc.us interstitial, and its EULA forbids redistribution,'
Write-Host 'so this script will not fetch it for you.'
Write-Host ''
Write-Host 'Rubidium and OptiFine overlap, so in OptiFine settings set:' -ForegroundColor Cyan
Write-Host '  Video settings > Fast Render  = OFF'
Write-Host '  Video settings > Chunk Loading = Threaded'
Write-Host 'Rubidium replaces the same chunk pipeline, and leaving Fast Render on'
Write-Host 'makes the two fight over the same buffers.'
Write-Host ''
$ok | Sort-Object Mod | Format-Table -AutoSize
