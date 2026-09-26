package io.github.ezylabshq.prismatica.ui.menu;

import com.mojang.blaze3d.matrix.MatrixStack;
import io.github.ezylabshq.prismatica.config.Lang;
import io.github.ezylabshq.prismatica.config.Settings;
import io.github.ezylabshq.prismatica.ui.Anim;
import io.github.ezylabshq.prismatica.ui.Draw;
import io.github.ezylabshq.prismatica.ui.Theme;
import net.minecraft.client.gui.widget.button.Button;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared look for every styled menu: backdrop, wordmark, headers, outlines.
 *
 * <p>Colours come from the live {@link Theme} palette, so a styled menu is
 * tinted by whatever is in the world the player came from.
 */
public final class MenuChrome {

    private static boolean styledFrame;
    private static final Map<Button, Float> HOVER = new HashMap<>();

    private MenuChrome() {
    }

    /** Marks the current frame as belonging to a styled screen. */
    public static void beginStyledFrame() {
        styledFrame = true;
    }

    /** Cleared at the end of a styled frame; see the draw-post event handler. */
    public static void endStyledFrame() {
        styledFrame = false;
    }

    public static boolean styledFrame() {
        return styledFrame;
    }

    public static int radius() {
        return Settings.radiusSmall();
    }

    /**
     * Eased hover amount per button. Buttons keep their own animation so the
     * highlight glides in and out instead of snapping.
     *
     * <p>With {@code motion.smooth} off the value is simply the target, so the
     * buttons snap; {@code motion.hover} sets how far it travels per frame, so a
     * low value is a slow glide rather than a jitter.
     */
    public static float buttonHover(Button button, boolean hovered) {
        float target = hovered ? 1f : 0f;
        if (!Settings.boolOf("setting.smooth")) {
            HOVER.put(button, target);
            return target;
        }
        float current = HOVER.getOrDefault(button, 0f);
        float step = Math.max(0.02f, Settings.floatOf("setting.hover"));
        float next = Anim.approach(current, target, step, 1f / 60f);
        HOVER.put(button, next);
        return next;
    }

    /**
     * The menu backdrop.
     *
     * <p>Three styles, picked by {@code menu.backdrop}: a flat fill, a flat fill
     * with the accent wash on top, or that plus the vignette. The opacity
     * setting scales the base fill, so a translucent backdrop lets whatever was
     * behind the screen show through.
     */
    public static void drawBackdrop(MatrixStack ms, int width, int height) {
        int accent = Theme.accent();
        int panel = Theme.panel();
        int style = Settings.choiceOf("setting.backdrop");
        float opacity = Settings.floatOf("setting.backdropOpacity");

        Draw.fill(ms, 0, 0, width, height, Theme.rgba(panel, opacity));

        if (style >= 1) {
            // Accent wash, strongest at the top, fading out by mid screen.
            int washHeight = (int) (height * 0.55f);
            for (int y = 0; y < washHeight; y++) {
                float t = 1f - (float) y / washHeight;
                float a = 0.16f * t * t * opacity;
                Draw.fill(ms, 0, y, width, 1, Theme.rgba(accent, a));
            }
        }

        if (style >= 2) {
            // Vignette on the bottom half to anchor content.
            int vigStart = (int) (height * 0.55f);
            for (int y = vigStart; y < height; y++) {
                float t = (float) (y - vigStart) / Math.max(1, height - vigStart);
                Draw.fill(ms, 0, y, width, 1, Theme.rgba(Theme.BLACK, 0.22f * t * opacity));
            }
        }
    }

    /**
     * Wordmark for the main menu.
     *
     * <p>{@code top} is the first row of the wordmark. The caller passes a
     * position derived from the buttons rather than a fraction of the window
     * height: pinning the header to a percentage left a large empty band above
     * the button column on tall windows, because the vanilla column sits much
     * lower than 16% of the screen.
     *
     * <p>Skipped entirely when {@code menu.wordmark} is off.
     */
    public static void drawMainMenuHeader(MatrixStack ms, int width, int top) {
        if (!Settings.boolOf("setting.wordmark")) {
            return;
        }
        int cx = width / 2;

        String title = "PRISMATICA";
        int titleW = Draw.textWidth(title);

        // Mark: a small prism glyph built from two stacked bars.
        int markSize = 9;
        int markX = cx - titleW / 2 - markSize - 6;
        int markY = top + 1;
        Draw.rounded(ms, markX, markY, markSize, markSize, 3, Theme.rgba(Theme.accent(), 0.95f));
        Draw.rounded(ms, markX + 2, markY + 2, markSize - 4, 2, 1, Theme.rgba(Theme.WHITE, 0.85f));

        Draw.text(ms, title, cx - titleW / 2f, top + 1, Theme.rgba(Theme.WHITE, 1f));

        Draw.textCenter(ms, Lang.t("chrome.wordmark.subtitle"), cx, top + 14,
                Theme.rgba(Theme.TEXT_2, 0.7f));

        // Thin accent rule, width tied to the title so it reads as one unit.
        int ruleW = Math.max(60, titleW);
        Draw.rounded(ms, cx - ruleW / 2, top + 26, ruleW, 1, 0, Theme.rgba(Theme.accent(), 0.45f));
    }

    /**
     * Header strip for the sub menus.
     *
     * <p>Each vanilla screen draws its own title inside its own {@code render},
     * after the background but before its widgets, and each one puts it at a
     * different height: the options screen at y=15, the world and server
     * screens at y=20, the pause screen at y=40. The bar is opaque and tall
     * enough for the screen it is given, so that title is covered rather than
     * tinted.
     */
    public static void drawHeader(MatrixStack ms, int width, int barH, String title, String subtitle) {
        int accent = Theme.accent();
        int pad = 12;

        Draw.fill(ms, 0, 0, width, barH, Theme.rgba(Theme.sidebar(), 1.0f));
        Draw.fill(ms, 0, barH - 1, width, 1, Theme.rgba(accent, 0.30f));

        // Accent tab, sized to the text rather than to the bar.
        int tabH = 11;
        Draw.rounded(ms, pad, (barH - tabH) / 2, 3, tabH, 2, Theme.rgba(accent, 0.95f));

        int x = pad + 10;

        // The pixel font is the default because the vanilla one cannot be made
        // small: FontRenderer has no size argument, and scaling the matrix
        // blurs the glyphs instead of shrinking them.
        //
        // It only covers printable ASCII though, so a translated label that
        // needs anything else falls through to the vanilla renderer below. The
        // check is per string and the call site asks for the micro font anyway,
        // because the font preference is a user choice and the language is not:
        // someone who picks VANILLA gets the vanilla font in every language.
        if (Settings.choiceOf("setting.headerFont") == 0 && Lang.microCapable(title)
                && Lang.microCapable(subtitle)) {
            int y = (barH - Draw.MICRO_H) / 2;
            int titleW = Draw.microWidth(title, 1);
            Draw.micro(ms, title, x, y, Theme.rgba(Theme.WHITE, 1f), 1);
            if (subtitle != null && !subtitle.isEmpty()) {
                Draw.micro(ms, subtitle, x + titleW + 8, y + 1, Theme.rgba(Theme.TEXT_2, 0.6f), 1);
            }
        } else {
            int y = (barH - 9) / 2;
            Draw.text(ms, title, x, y, Theme.rgba(Theme.WHITE, 1f));
            if (subtitle != null && !subtitle.isEmpty()) {
                Draw.text(ms, subtitle, x + Draw.textWidth(title) + 8, y, Theme.rgba(Theme.TEXT_2, 0.6f));
            }
        }
    }

    /**
     * 1px rounded outline, drawn as a rounded rect with the interior punched
     * back out. Cheaper and more consistent than four separate edge draws.
     */
    public static void drawOutline(MatrixStack ms, int x, int y, int w, int h, int r, int color) {
        Draw.rounded(ms, x, y, w, h, r, color);
        Draw.rounded(ms, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), Theme.rgba(Theme.BLACK, 0.0f));
    }
}
