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
        // The bottom centres are one pixel higher than the top ones.
        //
        // A quarter arc here runs i = 0..r inclusive, which is r + 1 rows. The top
        // pair is centred on y + r, so it spans y .. y + 2r and the last row is
        // y + r, comfortably inside the shape. The bottom pair centred on
        // y + h - r spans y + h - r .. y + h, and y + h is one row past the
        // bottom edge. That row gets only the i = r chord, a single pixel, so
        // every rounded panel in the client carried two stray pixels floating
        // just below it, one at each end. The fix is to lift the centres by one
        // so the span ends on y + h - 1, the last row that belongs to the shape.
        arc(ms, x + r, y + r, r, color, -1, -1);
        arc(ms, x + w - r, y + r, r, color, 1, -1);
        arc(ms, x + r, y + h - r - 1, r, color, -1, 1);
        arc(ms, x + w - r, y + h - r - 1, r, color, 1, 1);
    }

    /**
     * One quarter of a circle, from the centre row outwards.
     *
     * <p>Only the outer half of each chord is drawn. The inner half always
     * falls inside the straight bands {@link #rounded} has already laid down, so
     * painting it would be wasted fills, and that is why the width here is the
     * half chord rather than twice it.
     *
     * <p>Two details are load bearing, and both of them only misbehave at the
     * bottom, which is why they survived as long as they did.
     *
     * <p>The downward step must be {@code cy + i}. Without the {@code + i}
     * every row of a bottom corner was painted at the same {@code cy}, so the
     * corner had no vertical spread at all: the straight bottom band was left
     * showing as a narrow stem under a full width cap, and every rounded panel
     * in the client looked like a mushroom.
     *
     * <p>The loop must also start at {@code i = 0}, where the chord is the full
     * diameter. For the bottom pair that row is {@code y + h - r}, which is the
     * first row of the bottom band, and the band only covers the middle columns
     * there, so skipping it leaves a one pixel notch above the bottom edge. The
     * top pair has no such problem: their {@code i = 0} row is the first row of
     * the full width middle band.
     */
    private static void arc(MatrixStack ms, int cx, int cy, int r, int color, int dirX, int dirY) {
        for (int i = 0; i <= r; i++) {
            int span = Math.round((float) Math.sqrt((double) r * r - (double) i * i));
            int xx;
            int width;
            if (span < 1) {
                // The extreme row of a circle is a single pixel, and it sits on
                // the centre, not one step towards the corner.
                xx = cx;
                width = 1;
            } else {
                xx = dirX < 0 ? cx - span : cx;
                width = span;
            }
            int yy = dirY < 0 ? cy - i : cy + i;
            fill(ms, xx, yy, width, 1, color);
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

    public static void textCenter(MatrixStack ms, String s, float centerX, float y, int color) {
        text(ms, s, centerX - textWidth(s) / 2f, y, color);
    }

    // ---------------------------------------------------------------------
    // Micro font: 3x5 pixels per glyph, drawn with plain spans.
    //
    // The vanilla font renderer has no size parameter and scaling it would
    // blur the glyphs, so a smaller label needs its own bitmap. Five rows is
    // below the 7px cap height of the vanilla font, which is what makes a
    // header read as a caption instead of a heading.
    // ---------------------------------------------------------------------

    public static final int MICRO_W = 3;
    public static final int MICRO_H = 5;
    public static final int MICRO_ADVANCE = MICRO_W + 1;

    /**
     * Glyph rows, indexed by {@code char - MICRO_FIRST}. Bit 2 is the leftmost
     * pixel of the row.
     *
     * <p>Sized for the whole printable ASCII range rather than just
     * space-to-tilde: a glyph outside it used to throw out of the static
     * initialiser, which takes the client down during the load overlay and
     * reports it as a rendering failure far from the real cause.
     */
    private static final int MICRO_FIRST = 32;
    private static final int MICRO_SIZE = 128 - MICRO_FIRST;
    private static final int[][] MICRO = new int[MICRO_SIZE][];

    private static void glyph(char c, int r0, int r1, int r2, int r3, int r4) {
        int index = c - MICRO_FIRST;
        if (index < 0 || index >= MICRO_SIZE) {
            return;
        }
        MICRO[index] = new int[]{r0, r1, r2, r3, r4};
    }

    static {
        glyph(' ', 0b000, 0b000, 0b000, 0b000, 0b000);

        glyph('A', 0b010, 0b101, 0b111, 0b101, 0b101);
        glyph('B', 0b110, 0b101, 0b110, 0b101, 0b110);
        glyph('C', 0b011, 0b100, 0b100, 0b100, 0b011);
        glyph('D', 0b110, 0b101, 0b101, 0b101, 0b110);
        glyph('E', 0b111, 0b100, 0b110, 0b100, 0b111);
        glyph('F', 0b111, 0b100, 0b110, 0b100, 0b100);
        glyph('G', 0b011, 0b100, 0b111, 0b101, 0b011);
        glyph('H', 0b101, 0b101, 0b111, 0b101, 0b101);
        glyph('I', 0b111, 0b010, 0b010, 0b010, 0b111);
        glyph('J', 0b001, 0b001, 0b001, 0b101, 0b010);
        glyph('K', 0b101, 0b101, 0b110, 0b101, 0b101);
        glyph('L', 0b100, 0b100, 0b100, 0b100, 0b111);
        glyph('M', 0b101, 0b111, 0b111, 0b101, 0b101);
        glyph('N', 0b110, 0b101, 0b101, 0b101, 0b011);
        glyph('O', 0b010, 0b101, 0b101, 0b101, 0b010);
        glyph('P', 0b110, 0b101, 0b110, 0b100, 0b100);
        glyph('Q', 0b010, 0b101, 0b101, 0b110, 0b011);
        glyph('R', 0b110, 0b101, 0b110, 0b101, 0b101);
        glyph('S', 0b011, 0b100, 0b010, 0b001, 0b110);
        glyph('T', 0b111, 0b010, 0b010, 0b010, 0b010);
        glyph('U', 0b101, 0b101, 0b101, 0b101, 0b010);
        glyph('V', 0b101, 0b101, 0b101, 0b010, 0b010);
        glyph('W', 0b101, 0b101, 0b111, 0b111, 0b101);
        glyph('X', 0b101, 0b101, 0b010, 0b101, 0b101);
        glyph('Y', 0b101, 0b101, 0b010, 0b010, 0b010);
        glyph('Z', 0b111, 0b001, 0b010, 0b100, 0b111);

        glyph('0', 0b010, 0b101, 0b101, 0b101, 0b010);
        glyph('1', 0b010, 0b110, 0b010, 0b010, 0b111);
        glyph('2', 0b110, 0b001, 0b010, 0b100, 0b111);
        glyph('3', 0b110, 0b001, 0b010, 0b001, 0b110);
        glyph('4', 0b101, 0b101, 0b111, 0b001, 0b001);
        glyph('5', 0b111, 0b100, 0b110, 0b001, 0b110);
        glyph('6', 0b011, 0b100, 0b111, 0b101, 0b111);
        glyph('7', 0b111, 0b001, 0b010, 0b010, 0b010);
        glyph('8', 0b010, 0b101, 0b010, 0b101, 0b010);
        glyph('9', 0b111, 0b101, 0b111, 0b001, 0b110);

        glyph('.', 0b000, 0b000, 0b000, 0b000, 0b010);
        glyph(',', 0b000, 0b000, 0b000, 0b010, 0b100);
        glyph(':', 0b000, 0b010, 0b000, 0b010, 0b000);
        glyph('-', 0b000, 0b000, 0b111, 0b000, 0b000);
        glyph('_', 0b000, 0b000, 0b000, 0b000, 0b111);
        glyph('+', 0b000, 0b010, 0b111, 0b010, 0b000);
        glyph('=', 0b000, 0b111, 0b000, 0b111, 0b000);
        glyph('/', 0b001, 0b001, 0b010, 0b100, 0b100);
        glyph('(', 0b100, 0b010, 0b010, 0b010, 0b100);
        glyph(')', 0b001, 0b010, 0b010, 0b010, 0b001);
        glyph('[', 0b011, 0b010, 0b010, 0b010, 0b011);
        glyph(']', 0b110, 0b010, 0b010, 0b010, 0b110);
        glyph('!', 0b010, 0b010, 0b010, 0b000, 0b010);
        glyph('?', 0b110, 0b001, 0b010, 0b000, 0b010);
        glyph('#', 0b010, 0b111, 0b010, 0b111, 0b010);
        glyph('%', 0b101, 0b001, 0b010, 0b100, 0b101);
        glyph('*', 0b000, 0b101, 0b010, 0b101, 0b000);
        glyph('<', 0b001, 0b010, 0b100, 0b010, 0b001);
        glyph('>', 0b100, 0b010, 0b001, 0b010, 0b100);
        glyph('|', 0b010, 0b010, 0b010, 0b010, 0b010);
        glyph('^', 0b010, 0b101, 0b000, 0b000, 0b000);
        glyph('\'', 0b010, 0b010, 0b000, 0b000, 0b000);
        glyph('"', 0b101, 0b101, 0b000, 0b000, 0b000);
    }

    /**
     * Draws {@code s} in the 3x5 font. The bitmap is uppercase only, so
     * lowercase input is folded rather than dropped.
     *
     * @param tracking extra pixels between glyphs, for a looser label
     */
    public static void micro(MatrixStack ms, String s, int x, int y, int color, int tracking) {
        if (s == null || s.isEmpty()) {
            return;
        }
        int pen = x;
        for (int i = 0; i < s.length(); i++) {
            char c = Character.toUpperCase(s.charAt(i));
            int index = c - MICRO_FIRST;
            int[] rows = index >= 0 && index < MICRO_SIZE ? MICRO[index] : null;
            if (rows != null) {
                for (int row = 0; row < MICRO_H; row++) {
                    int bits = rows[row];
                    if (bits == 0) {
                        continue;
                    }
                    // Merge horizontal runs so a row costs one fill, not three.
                    int col = 0;
                    while (col < MICRO_W) {
                        if (((bits >> (MICRO_W - 1 - col)) & 1) == 0) {
                            col++;
                            continue;
                        }
                        int run = 1;
                        while (col + run < MICRO_W && ((bits >> (MICRO_W - 1 - col - run)) & 1) != 0) {
                            run++;
                        }
                        fill(ms, pen + col, y + row, run, 1, color);
                        col += run;
                    }
                }
            }
            pen += MICRO_ADVANCE + tracking;
        }
    }

    public static void micro(MatrixStack ms, String s, int x, int y, int color) {
        micro(ms, s, x, y, color, 0);
    }

    public static int microWidth(String s, int tracking) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        // The trailing gap after the last glyph is not part of the run.
        return s.length() * (MICRO_ADVANCE + tracking) - 1 - tracking;
    }

    public static int microWidth(String s) {
        return microWidth(s, 0);
    }

    public static void microCenter(MatrixStack ms, String s, int centerX, int y, int color, int tracking) {
        micro(ms, s, centerX - microWidth(s, tracking) / 2, y, color, tracking);
    }

    public static void microRight(MatrixStack ms, String s, int rightX, int y, int color, int tracking) {
        micro(ms, s, rightX - microWidth(s, tracking), y, color, tracking);
    }
}
