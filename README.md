# Prismatica

A client-side visual interface for **Minecraft 1.16.5 / Forge 36.2.x**.

Cosmetic only: no gameplay automation, no network hooks, nothing that changes
how the game plays or gives an advantage over other players.

## The idea

Most clients ship a fixed, hand-picked colour scheme baked into the code.
Prismatica reads the world instead:

- the **sky colour** of the biome you are standing in
- the **grass tint** and **water tint** of that biome
- the **time of day** (dawn, noon, sunset, midnight)
- **weather** (rain washes the palette out)
- the **dimension** (the Nether reads as embers, the End as violet)

Those values are blended into a single accent colour, and the whole interface
follows it. Walk from a forest into a desert, watch a sunset, or step through a
portal, and the menu re-tints itself.

The transitions run on a **critically damped spring** rather than plain lerp, so
the palette carries real velocity: fast world changes are caught mid-flight and
settle with a slight overshoot instead of sliding.

## Modules

Interface, HUD and cosmetics only.

| Module | What it does |
|---|---|
| Rounded Corners | soft 9px radius on every surface |
| Smooth Animations | eased transitions, staggered row entrance |
| Scroll Momentum | inertial list scrolling |
| Accent Border | 1px accent outline on the panel |
| Watermark | corner info line, also world-reactive |

## Build

Requires a **JDK 8** toolchain. Point Gradle at one in `gradle.properties`:

```properties
org.gradle.java.installations.paths=C:/path/to/jdk8
```

Then:

```bash
gradlew.bat build      # -> build/libs/prismatica-0.1.0.jar
gradlew.bat runClient  # dev client with the mod loaded
```

Install the built jar into the `mods/` folder of a Forge 1.16.5 profile.

## Open the menu

- **B** or **Right Shift**
- or type `/prismatica` in chat

## Visual pack

`tools/fetch-pack.ps1` downloads a curated set of client-side visual and
performance mods (11 of them, every file SHA1 verified against the Modrinth API)
plus two shader packs:

```powershell
powershell -ExecutionPolicy Bypass -File tools\fetch-pack.ps1
```

See [pack/README.md](pack/README.md) for what is included and what is
deliberately left out.

## Layout

```
src/main/java/io/github/ezylabshq/prismatica/
  ui/Prismatica.java      mod entry, keybinds, /prismatica command
  ui/ClickGuiScreen.java  the animated module browser
  ui/WorldTheme.java      samples the world, produces a target palette
  ui/Theme.java           live palette, spring physics
  ui/Draw.java            span-based primitives (rounded rects, circles, text)
  ui/Anim.java            frame-rate independent easing
  ui/hud/Watermark.java   corner info line
  module/                 Module, Category, ModuleManager
tools/
  fetch-pack.ps1          downloads the visual pack from Modrinth
  ThemeProbe.java         offline check that the reactive palette moves
  MixProbe.java           colour maths diagnostics
```

## Rendering notes

Everything is drawn with a single primitive, `AbstractGui.fill(MatrixStack, ...)`,
with rounded corners and circles approximated by horizontal spans. No shaders,
no textures, no RenderType juggling, so nothing here can break between
Minecraft versions.

## License

GNU General Public License v3.0. See [LICENSE](LICENSE).
