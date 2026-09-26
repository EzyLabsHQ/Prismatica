# Mixin targets

## Why every `@Inject` lists two names

A real Forge 1.16.5 client runs Minecraft with **SRG** member names, while the
ForgeGradle development client runs the **Mojang** names that the `official`
mapping channel provides. The same method therefore has two different names:

| target class | Mojang name | SRG name |
|---|---|---|
| `net.minecraft.client.gui.screen.Screen` | `renderBackground` | `func_230446_a_` |
| `net.minecraft.client.gui.screen.MainMenuScreen` | `render` | `func_230430_a_` |
| `net.minecraft.client.gui.widget.button.Button` | `renderButton` | `func_230431_b_` |

So each injector lists both and matches literally:

```java
@Inject(method = {
        "renderBackground(Lcom/mojang/blaze3d/matrix/MatrixStack;)V",
        "func_230446_a_(Lcom/mojang/blaze3d/matrix/MatrixStack;)V"
}, at = @At("HEAD"), cancellable = true, remap = false, expect = 1, require = 1)
```

Exactly one of the two exists in any given environment, and `expect = 1` states
that. If both were ever present the injection would fire twice.

## Why not a refmap

The obvious fix is a refmap, and it does not work here. Two separate reasons,
both verified on this machine:

1. **A refmap cannot be generated.** ForgeGradle 6 does not produce one for
   1.16.5 on the `official` channel, and the `snapshot` channel that would have
   removed the need for it is no longer published anywhere:

   ```
   Execution failed for task ':createMcpToSrg'.
   > Invalid mappings: task ':createMcpToSrg' property 'mappings' Could not find archive
   ```

   The archive 404s on `maven.minecraftforge.net`, `mcp.mojang.com` no longer
   resolves, and Modrinth has no copy.

2. **A refmap would not be applied anyway.** A hand written one loads, Mixin
   even reports `Using refmap prismatica-refmap.json`, but never builds a
   reference mapper. Scanning all 247 classes of
   `forge-1.16.5-36.2.42-launcher.jar` finds no reference to `ObfuscationService`,
   `ReferenceMapper` or `setRefMapper` anywhere: Forge 1.16.5 never installs a
   Mixin obfuscation service, so Mixin treats every name as already final. With
   `remap = true` the Mojang name is looked up verbatim and simply not found.

   The failure looks like this, and it only appears in a real client, never in
   `runClient`:

   ```
   InvalidInjectionException: @Inject ... could not find any targets matching
   'renderBackground(Lcom/mojang/blaze3d/matrix/MatrixStack;)V' in
   net.minecraft.client.gui.screen.Screen.
   ```

## Where the SRG names came from

Read out of the official to SRG table inside `mapping-1.16.5-mapping.zip`
(`methods.csv`, columns `searge,name`) and cross checked with `javap` against
`forge-1.16.5-36.2.42-client.jar`.

Note that `Screen` has two `renderBackground` overloads, `func_230433_a_` and
`func_230446_a_`. The one taking a single `MatrixStack` is `func_230446_a_`,
confirmed by `javap` on the production jar.

**If a mixin target ever changes, the mixin has to change with it.** The build
will still pass and the failure will only show up at runtime.
