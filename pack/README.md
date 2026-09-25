# Prismatica Visual Pack — Minecraft 1.16.5 / Forge 36.2.42

Client-side only. Interface, performance and cosmetics. Nothing here automates
gameplay, reads other players' data or gives an advantage in PvP.

Fetched by `tools/fetch-pack.ps1` from the Modrinth API, every file checked
against the SHA1 the API reports. Re-run the script any time to refresh.

## mods/

| Mod | Why |
|---|---|
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

## shaderpacks/

| Pack | Notes |
|---|---|
| BSL_v10.1.8.zip | the most used OptiFine shader pack; if 1.16.5 glitches, use BSL 8.1 instead |
| ComplementaryReimagined_r5.9.3.zip | softer lighting, easier on the eyes |

## Install

1. Install **Forge 1.16.5** (36.2.x) in your launcher.
2. Copy everything from `pack/mods/` into the version's `mods/` folder.
3. Copy `prismatica-0.1.0.jar` (from `build/libs/`) into the same folder.
4. **OptiFine** is not distributed through Modrinth, download it manually and
   put the jar in `mods/` too (it is what actually renders the shader packs):
   https://www.optifine.net/downloadOptiFine?f=OptiFine_1.16.5_HD_U_G8.jar
5. Copy `pack/shaderpacks/*.zip` into the version's `shaderpacks/` folder and
   pick one in `Options > Video > Shaders`.

## Not included, and why

- **Sodium / Phosphor / Indium / Starlight / Noisium / Dynamic Lights** — every
  1.16.5 build on Modrinth is Fabric only, so they cannot be used on Forge 1.16.5.
  (They become available if you move to Fabric 1.16.5 or any modern version.)
- Any gameplay automation: auto-aim, auto-farm, reach, ESP, xray, rotation
  locks, and similar. Not part of this pack by design.
