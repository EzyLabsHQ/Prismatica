package io.github.ezylabshq.prismatica.mixin;

import com.mojang.blaze3d.matrix.MatrixStack;
import io.github.ezylabshq.prismatica.config.Settings;
import io.github.ezylabshq.prismatica.ui.Draw;
import io.github.ezylabshq.prismatica.ui.Theme;
import io.github.ezylabshq.prismatica.ui.menu.MenuChrome;
import net.minecraft.client.gui.widget.button.ImageButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Puts the Prismatica panel behind image buttons.
 *
 * <p>{@link ImageButton} extends {@code Button} but overrides
 * {@code renderButton} outright, and the override replaces the base
 * implementation rather than calling it. So {@code ButtonMixin}, which injects
 * into {@code Button.renderButton}, never runs for these: an inject into a
 * method that an override shadows does not reach the override. That is why the
 * accessibility toggle on the main menu kept its stock look while every other
 * button was restyled.
 *
 * <p>Unlike the other two button mixins this one does not cancel. The bytecode
 * of {@code ImageButton.renderButton} binds a texture and calls {@code blit}
 * twice, with no fill anywhere in it, so the icon is the only thing it draws.
 * Drawing the panel at HEAD and letting the original run leaves the icon on top
 * of it, which is what is wanted; cancelling would leave a bare panel.
 *
 * <p>Both target names are listed and remapping is off, see the note in
 * {@link ScreenBackgroundMixin} for why. The obvious guess here is that
 * {@code ImageButton} is a Forge class and therefore has no SRG alias, so a
 * single name would do. That is wrong, and it fails at apply time with "No
 * refMap loaded". {@code ImageButton} is a Forge class, but the method it
 * declares is the vanilla one: in the production srg jar it is
 * {@code func_230431_b_}, the same name {@code Button} uses, which is what lets
 * the override still dispatch. Verified against
 * {@code client-1.16.5-20210115.111550-srg.jar}, where the class is absent from
 * the slim jar and declares {@code func_191746_c(int,int)} and
 * {@code func_230431_b_(MatrixStack,int,int,float)}.
 */
@Mixin(ImageButton.class)
public abstract class ImageButtonMixin {

    @Inject(
            method = {
                    "renderButton(Lcom/mojang/blaze3d/matrix/MatrixStack;IIF)V",
                    "func_230431_b_(Lcom/mojang/blaze3d/matrix/MatrixStack;IIF)V"
            },
            at = @At("HEAD"),
            remap = false,
            expect = 1,
            require = 1)
    private void prismatica$panelBehind(MatrixStack ms, int mouseX, int mouseY,
                                        float partialTick, CallbackInfo ci) {
        if (!MenuChrome.styledFrame() || !Settings.boolOf("setting.buttons")) {
            return;
        }

        ImageButton self = (ImageButton) (Object) this;
        if (!self.visible) {
            return;
        }

        boolean hovered = self.isHovered() && self.active;
        float t = MenuChrome.buttonHover(self, hovered);

        int accent = Theme.accent();
        int fill = Theme.mix(
                Theme.rgba(Theme.WHITE, 0.045f),
                Theme.rgba(accent, 0.30f),
                t);
        int border = Theme.mix(
                Theme.rgba(Theme.WHITE, 0.10f),
                Theme.rgba(accent, 0.85f),
                t);

        int r = Settings.radiusSmall();
        Draw.rounded(ms, self.x, self.y, self.getWidth(), self.getHeight(), r, fill);
        Draw.rounded(ms, self.x + 1, self.y + 1, self.getWidth() - 2, self.getHeight() - 2,
                Math.max(0, r - 1), Theme.rgba(Theme.BLACK, 0.0f));
        if (Settings.boolOf("setting.accentBorder")) {
            MenuChrome.drawOutline(ms, self.x, self.y, self.getWidth(), self.getHeight(), r, border);
        }
    }
}
