package io.github.ezylabshq.prismatica.ui;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;

/**
 * Primitive drawing helpers.
 *
 * <p>Everything is built from horizontal spans on top of a single
 * {@code AbstractGui.fill(MatrixStack, ...)} call, so there are no shaders, no
 * textures and no RenderTypes that could break between Minecraft versions.
 */
public final class Draw {

    private Draw() {
    }

    public static void fill(MatrixStack ms, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        AbstractGui.fill(ms, x, y, x + w, y + h, color);
    }

    public static void rounded(MatrixStack ms, int x, int y, int w, int h, int r, int color) {
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            fill(ms, x, y, w, h, color);
            return;
        }
        fill(ms, x + r, y, w - 2 * r, r, color);
        fill(ms, x + r, y + h - r, w - 2 * r, r, color);
        fill(ms, x, y + r, w, h - 2 * r, color);
        arc(ms, x + r, y + r, r, color, -1, -1);
        arc(ms, x + w - r, y + r, r, color, 1, -1);
        arc(ms, x + r, y + h - r, r, color, -1, 1);
        arc(ms, x + w - r, y + h - r, r, color, 1, 1);
    }

    private static void arc(MatrixStack ms, int cx, int cy, int r, int color, int dirX, int dirY) {
        for (int i = 1; i <= r; i++) {
            int span = Math.max(1, Math.round((float) Math.sqrt((double) r * r - (double) i * i)));
            int xx = dirX < 0 ? cx - span : cx;
            int yy = dirY < 0 ? cy - i : cy;
            fill(ms, xx, yy, span, 1, color);
        }
    }

    public static void circle(MatrixStack ms, int cx, int cy, int r, int color) {
        for (int i = -r; i <= r; i++) {
            int span = Math.round((float) Math.sqrt((double) r * r - (double) i * i));
            if (span <= 0) {
                continue;
            }
            fill(ms, cx - span, cy + i, span * 2 + 1, 1, color);
        }
    }

    /** Two pass fake drop shadow: a wide soft pass plus a tight one. */
    public static void shadow(MatrixStack ms, int x, int y, int w, int h, int r, float alpha) {
        rounded(ms, x - 3, y + 6, w + 6, h + 6, r + 3, Theme.rgba(Theme.BLACK, 0.28f * alpha));
        rounded(ms, x - 1, y + 2, w + 2, h + 2, r + 1, Theme.rgba(Theme.BLACK, 0.22f * alpha));
    }

    public static void text(MatrixStack ms, String s, float x, float y, int color) {
        Minecraft.getInstance().font.draw(ms, s, x, y, color);
    }

    public static void textShadow(MatrixStack ms, String s, float x, float y, int color) {
        Minecraft.getInstance().font.drawShadow(ms, s, x, y, color);
    }

    public static int textWidth(String s) {
        return Minecraft.getInstance().font.width(s);
    }

    public static void textRight(MatrixStack ms, String s, float rightX, float y, int color) {
        text(ms, s, rightX - textWidth(s), y, color);
    }
}
