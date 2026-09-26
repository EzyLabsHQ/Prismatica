package io.github.ezylabshq.prismatica.mixin;

import com.mojang.blaze3d.matrix.MatrixStack;
import io.github.ezylabshq.prismatica.ui.menu.MenuChrome;
import io.github.ezylabshq.prismatica.ui.menu.MenuStyler;
import net.minecraft.client.gui.IGuiEventListener;
import net.minecraft.client.gui.IRenderable;
import net.minecraft.client.gui.screen.MainMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.Widget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rebuilds the main menu.
 *
 * <p>The vanilla main menu paints its panorama, dirt, vignette, logo and splash
 * text inside one monolithic {@code render} method, so the whole method is
 * replaced: our backdrop and wordmark go down, then the widgets are drawn.
 *
 * <p>The widgets are rendered by walking {@code children} directly. Calling
 * {@code Screen.render} would not work: that call is virtual, so it lands back
 * in this injected method and recurses until the stack overflows.
 *
 * <p>Both target names are listed and remapping is off, see the note in
 * {@link ScreenBackgroundMixin} for why. The descriptors are left off here on
 * purpose: at runtime this class carries the screen's render method under a
 * signature that does not match the development one, and matching by name
 * alone is what actually lands.
 */
@Mixin(MainMenuScreen.class)
public abstract class MainMenuMixin {

    @Inject(
            method = {"render", "func_230430_a_"},
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            expect = 1,
            require = 1)
    private void prismatica$customMainMenu(MatrixStack ms, int mouseX, int mouseY,
                                          float partialTick, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        if (!MenuStyler.isStyled(self)) {
            return;
        }

        MenuChrome.beginStyledFrame();
        MenuChrome.drawBackdrop(ms, self.width, self.height);
        MenuChrome.drawMainMenuHeader(ms, self.width, headerTop(self));

        // Same loop Screen.render performs, minus the virtual dispatch.
        for (IGuiEventListener child : self.children()) {
            if (child instanceof IRenderable) {
                ((IRenderable) child).render(ms, mouseX, mouseY, partialTick);
            }
        }

        ci.cancel();
    }

    /**
     * Where the wordmark goes: just above the highest button.
     *
     * <p>Deriving it from the widgets rather than from the window height is what
     * removes the dead band. The vanilla column starts much lower than any fixed
     * fraction of the screen, so a percentage based header left a wide empty
     * strip on a 16:9 window and the menu looked like it was missing something.
     */
    private static int headerTop(Screen screen) {
        int firstButtonY = Integer.MAX_VALUE;
        for (IGuiEventListener child : screen.children()) {
            if (child instanceof Widget && ((Widget) child).y < firstButtonY) {
                firstButtonY = ((Widget) child).y;
            }
        }
        if (firstButtonY == Integer.MAX_VALUE) {
            return Math.max(8, screen.height / 4);
        }
        // 42 leaves room for the wordmark, the subtitle and the accent rule.
        return Math.max(8, firstButtonY - 42);
    }
}
