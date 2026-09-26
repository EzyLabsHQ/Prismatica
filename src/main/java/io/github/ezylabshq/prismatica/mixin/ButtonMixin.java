package io.github.ezylabshq.prismatica.mixin;

import com.mojang.blaze3d.matrix.MatrixStack;
import io.github.ezylabshq.prismatica.config.Settings;
import io.github.ezylabshq.prismatica.ui.Anim;
import io.github.ezylabshq.prismatica.ui.Draw;
import io.github.ezylabshq.prismatica.ui.Theme;
import io.github.ezylabshq.prismatica.ui.menu.MenuChrome;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.button.Button;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders plain buttons with the Prismatica look.
 *
 * <p>Only active while a styled frame is being drawn (see
 * {@link MenuChrome#styledFrame()}), so widgets inside the HUD, the inventory
 * or the world selection list keep their vanilla appearance.
 *
 * <p>Both target names are listed and remapping is off, see the note in
 * {@link ScreenBackgroundMixin} for why.
 */
@Mixin(Button.class)
public abstract class ButtonMixin {

    @Inject(
            method = {
                    "renderButton(Lcom/mojang/blaze3d/matrix/MatrixStack;IIF)V",
                    "func_230431_b_(Lcom/mojang/blaze3d/matrix/MatrixStack;IIF)V"
            },
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            expect = 1,
            require = 1)
    private void prismatica$customButton(MatrixStack ms, int mouseX, int mouseY,
                                        float partialTick, CallbackInfo ci) {
        if (!MenuChrome.styledFrame()) {
            return;
        }
        // menu.buttons turns the whole button treatment off, which is useful
        // when someone wants the backdrop and header but the stock widgets.
        if (!Settings.boolOf("setting.buttons")) {
            return;
        }

        Button self = (Button) (Object) this;
        if (!self.visible) {
            return;
        }

        int w = self.getWidth();
        int h = self.getHeight();
        int x = self.x;
        int y = self.y;

        boolean hovered = self.isHovered() && self.active;
        // Ease the hover so the highlight glides instead of snapping.
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

        int r = MenuChrome.radius();
        Draw.rounded(ms, x, y, w, h, r, fill);
        // 1px inner border drawn as a slightly inset rounded rect outline.
        Draw.rounded(ms, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), Theme.rgba(Theme.BLACK, 0.0f));
        if (Settings.boolOf("setting.accentBorder")) {
            MenuChrome.drawOutline(ms, x, y, w, h, r, border);
        }

        if (self.active) {
            int textColor = Theme.mix(
                    Theme.rgba(Theme.TEXT_2, 0.95f),
                    Theme.rgba(Theme.WHITE, 1f),
                    t);
            String label = self.getMessage().getString();
            float tx = x + (w - Draw.textWidth(label)) / 2f;
            float ty = y + (h - 8) / 2f;
            Draw.text(ms, label, tx, ty, textColor);
        } else {
            String label = self.getMessage().getString();
            float tx = x + (w - Draw.textWidth(label)) / 2f;
            float ty = y + (h - 8) / 2f;
            Draw.text(ms, label, tx, ty, Theme.rgba(Theme.TEXT_2, 0.45f));
        }

        ci.cancel();
    }
}
