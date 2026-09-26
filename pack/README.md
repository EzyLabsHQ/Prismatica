# Prismatica Visual Pack — Minecraft 1.16.5 / Forge 36.2.42

Client-side only. Interface, performance and cosmetics. Nothing here automates
gameplay, reads other players' data or gives an advantage in PvP.

Fetched by `tools/fetch-pack.ps1` from the Modrinth API, every file checked
against the SHA1 the API reports. Re-run the script any time to refresh.

## mods/

| Mod | Why |
|---|---|
| rubidium | the renderer rewrite, see the note below |
| ferrite-core | smaller, faster memory usage |
| modernfix | batch of optimisations and fixes |
| spark | low overhead profiler (`/spark`) |
| fps-reducer | caps FPS when the window is not focused |
| appleskin | hunger and saturation bars |
| journeymap | full map, waypoints, minimap |
| betterf3 | readable F3 debug overlay |
| entitytexturefeatures | random emissive textures on mobs |
| customskinloader | 3D skin layers, capes, slim arms |
| better-third-person | reworked third person camera |
| controlling | searchable key binding list |

### Why rubidium and not sodium

Sodium has no Forge build for 1.16.5. Querying the Modrinth API with
`loaders=["forge"]&game_versions=["1.16.5"]` returns nothing; the only 1.16.x
releases are `mc1.16.5-0.2.0` and `mc1.16.3-0.1.0`, both Fabric. Same story for
Iris (21 versions, all `fabric+quilt`) and Indium (1 version, Fabric).

Rubidium is the same project before the 1.17 rename, by the same author
(CaffeineMC), and it does ship a Forge 1.16.5 build. It is also LGPL-3.0, while
Sodium moved to Polyform Shield 1.0.0 — so if you ever want to fork and tune the
renderer yourself, Rubidium is the one you can legally do that to.

It is not a build dependency and does not go into `runClient`. It rewrites the
whole renderer, ships an access transformer plus an SRG refmap, and our dev
workspace is on `official` mappings, so it belongs in a real instance's `mods/`.

## shaderpacks/

| Pack | Notes |
|---|---|
| IterationT-3.2.0.zip | by L1MA, 40.9 MB; `shaders.properties` declares `version.1.16.5=G8`, so it targets 1.16.5 explicitly |
| BSL_v10.1.8.zip | the most used OptiFine shader pack; if 1.16.5 glitches, use BSL 8.1 instead |
| ComplementaryReimagined_r5.9.3.zip | softer lighting, easier on the eyes |

IterationT is authored against Iris, and Iris cannot run on Forge 1.16.5, so
this one goes through OptiFine. That works: the pack guards its Iris-only bits
with `#ifdef IS_IRIS`, keeps pre-1.13 and pre-1.16 fallbacks behind `MC_VERSION`
checks, and the two `.gsh` geometry-shader files it ships are not `#include`d by
anything, so OptiFine simply ignores them. If it renders wrong on 1.16.5, fall
back to BSL 8.1.

## Install

1. Install **Forge 1.16.5** (36.2.x) in your launcher.
2. Copy everything from `pack/mods/` into the version's `mods/` folder.
3. Copy `prismatica-0.1.0.jar` (from `build/libs/`) into the same folder.
4. **OptiFine** is not on Modrinth, so download it by hand from
   <https://www.optifine.net/downloads> (1.16.5, `HD_U_G8`) and put the jar in
   `mods/` too — it is what actually renders the shader packs. The direct
   `downloadOptiFine` link that used to work now 404s: downloads go through an
   adfoc.us interstitial, and the OptiFine EULA forbids redistribution, so this
   repo will not vendor it.
5. In OptiFine's video settings turn **Fast Render off** and set
   **Chunk Loading to Threaded**. Rubidium rewrites the same chunk pipeline, and
   Fast Render makes the two fight over the same buffers.
6. Copy `pack/shaderpacks/*.zip` into the version's `shaderpacks/` folder and
   pick one in `Options > Video > Shaders`.

## Not included, and why

- **Sodium, Iris, Indium** — for 1.16.5 they exist on Fabric and Quilt only.
  Getting the real stack means leaving Forge; see the rubidium note above.
- Any gameplay automation: auto-aim, auto-farm, reach, ESP, xray, rotation
  locks, and similar. Not part of this pack by design.
