package io.github.ezylabshq.prismatica.mixin;

import com.mojang.blaze3d.matrix.MatrixStack;
import io.github.ezylabshq.prismatica.ui.menu.MenuChrome;
import io.github.ezylabshq.prismatica.ui.menu.MenuStyler;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the vanilla menu backdrop.
 *
 * <p>Every screen in the game funnels its background through
 * {@code Screen.renderBackground}, so intercepting that single method is enough
 * to restyle the whole menu tree without touching each screen individually.
 *
 * <p>Both target names are listed and remapping is off on purpose. A real Forge
 * client runs Minecraft with SRG member names, where this method is
 * {@code func_230446_a_}, while the development client runs the Mojang name
 * {@code renderBackground}. Forge 1.16.5 never installs a Mixin obfuscation
 * service, so with remapping enabled Mixin looks the name up literally and
 * finds neither; listing both and matching literally works in both places.
 * A refmap cannot bridge this, and the {@code snapshot} mapping channel that
 * would have avoided it is no longer published for 1.16.5.
 */
@Mixin(Screen.class)
public abstract class ScreenBackgroundMixin {

    @Inject(
            method = {
                    "renderBackground(Lcom/mojang/blaze3d/matrix/MatrixStack;)V",
                    "func_230446_a_(Lcom/mojang/blaze3d/matrix/MatrixStack;)V"
            },
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            expect = 1,
            require = 1)
    private void prismatica$customBackdrop(MatrixStack ms, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        if (!MenuStyler.isStyled(self)) {
            return;
        }
        MenuChrome.beginStyledFrame();
        MenuChrome.drawBackdrop(ms, self.width, self.height);
        ci.cancel();
    }
}
