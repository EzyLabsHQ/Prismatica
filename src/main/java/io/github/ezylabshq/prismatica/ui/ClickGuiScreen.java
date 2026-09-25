package io.github.ezylabshq.prismatica.ui;

import com.mojang.blaze3d.matrix.MatrixStack;
import io.github.ezylabshq.prismatica.ui.module.Category;
import io.github.ezylabshq.prismatica.ui.module.Module;
import io.github.ezylabshq.prismatica.ui.module.ModuleManager;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.StringTextComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Animated module browser.
 *
 * <p>The layout is designed once in a fixed 322x216 space and then scaled around
 * its centre, so proportions stay identical at every GUI scale.
 */
public class ClickGuiScreen extends Screen {

    private static final int W = 322;
    private static final int H = 216;
    private static final int SIDEBAR = 78;
    private static final int HEADER = 30;
    private static final int PAD = 10;
    private static final int ROW = 24;
    private static final int GAP = 4;
    private static final int CAT_H = 26;

    private final List<Row> rows = new ArrayList<>();

    private long lastFrame;
    private float open;
    private boolean closing;
    private boolean built;
    private int selected;
    private float selSlide;
    private float scroll;
    private float scrollTarget;

    // Cached layout, refreshed every frame.
    private int px;
    private int py;
    private int pw;
    private int ph;
    private int sw;
    private float scale = 1f;

    private static final class Row {
        final Module module;
        float appear;
        float hover;

        Row(Module module) {
            this.module = module;
        }
    }

    public ClickGuiScreen() {
        super(new StringTextComponent("Prismatica"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void buildRows() {
        rows.clear();
        for (Module module : ModuleManager.in(Category.values()[selected])) {
            rows.add(new Row(module));
        }
        built = true;
    }

    @Override
    public void onClose() {
        closing = true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 1) { // ESC
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scrollTarget = Anim.clamp(scrollTarget - (float) delta * 18f, 0f, maxScroll());
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        int x = (int) mouseX;
        int y = (int) mouseY;

        Category[] categories = Category.values();
        for (int i = 0; i < categories.length; i++) {
            if (inside(x, y, catX(), catY(i), catW(), CAT_H - 4)) {
                if (selected != i) {
                    selected = i;
                    scroll = 0f;
                    scrollTarget = 0f;
                    buildRows();
                }
                return true;
            }
        }

        for (int i = 0; i < rows.size(); i++) {
            if (inside(x, y, rowX(), rowY(i), rowW(), ROW)) {
                rows.get(i).module.toggle();
                return true;
            }
        }
        return true;
    }

    @Override
    public void render(MatrixStack ms, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        float dt = lastFrame == 0L ? 1f / 60f : Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;

        if (!built) {
            buildRows();
        }

        // Sample the world and advance the palette springs before anything is drawn.
        WorldTheme.sample(dt);
        Theme.tick(dt);

        ModuleManager.update(dt);

        boolean smooth = ModuleManager.isOn("Smooth Animations");
        boolean rounded = ModuleManager.isOn("Rounded Corners");
        boolean border = ModuleManager.isOn("Accent Border");
        boolean momentum = ModuleManager.isOn("Scroll Momentum");

        open = Anim.approach(open, closing ? 0f : 1f, smooth ? 0.2f : 1f, dt);
        if (closing && open <= 0.02f) {
            minecraft.setScreen(null);
            return;
        }

        float appear = Anim.easeOutCubic(open);
        scale = 0.94f + 0.06f * appear;
        pw = sc(W);
        ph = sc(H);
        sw = sc(SIDEBAR);
        px = (width - pw) / 2;
        py = (height - ph) / 2 + Math.round((1f - appear) * 12f * scale);

        int r = rounded ? Math.max(2, sc(Theme.RADIUS)) : 0;
        int rSmall = rounded ? Math.max(1, sc(Theme.RADIUS_SM)) : 0;

        // Backdrop
        Draw.fill(ms, 0, 0, width, height, Theme.rgba(Theme.BG, 0.62f * appear));

        // Panel, optionally with a 1px accent border
        if (rounded) {
            Draw.shadow(ms, px, py, pw, ph, r, appear);
        }
        if (border && rounded) {
            Draw.rounded(ms, px, py, pw, ph, r, Theme.rgba(Theme.accent(), 0.55f * appear));
            Draw.rounded(ms, px + 1, py + 1, pw - 2, ph - 2, Math.max(1, r - 1), Theme.fade(Theme.panel(), appear));
        } else {
            Draw.rounded(ms, px, py, pw, ph, r, Theme.fade(Theme.panel(), appear));
        }

        // Sidebar with a squared right edge
        Draw.rounded(ms, px, py, sw, ph, r, Theme.fade(Theme.sidebar(), appear));
        if (rounded) {
            Draw.fill(ms, px + sw - r, py, r, ph, Theme.fade(Theme.sidebar(), appear));
        }

        drawSidebar(ms, mouseX, mouseY, appear, dt, smooth, rounded);
        drawHeader(ms, appear, rounded);
        drawRows(ms, mouseX, mouseY, appear, dt, smooth, rounded, rSmall, momentum);
    }

    private void drawSidebar(MatrixStack ms, int mouseX, int mouseY, float appear, float dt,
                             boolean smooth, boolean rounded) {
        int logoY = py + sc(HEADER / 2 - 4);
        Draw.rounded(ms, catX() - 3, logoY - 1, sc(8), sc(8), rounded ? 3 : 0, Theme.rgba(Theme.accent(), appear));
        Draw.text(ms, "Prismatica", catX() + sf(10), logoY, Theme.rgba(Theme.TEXT, 0.92f * appear));

        // Sliding selection pill
        float targetY = catY(selected);
        if (selSlide == 0f) {
            selSlide = targetY;
        }
        selSlide = Anim.approach(selSlide, targetY, smooth ? 0.25f : 1f, dt);

        int pillY = Math.round(selSlide) - 2;
        Draw.rounded(ms, catX() - 3, pillY, catW() + 6, CAT_H, rounded ? 8 : 0,
                Theme.rgba(Theme.accent(), 0.15f * appear));
        Draw.rounded(ms, catX() - 3, pillY, Math.max(2, sc(3)), CAT_H, rounded ? 2 : 0,
                Theme.rgba(Theme.accent(), 0.95f * appear));

        Category[] categories = Category.values();
        for (int i = 0; i < categories.length; i++) {
            boolean hover = inside(mouseX, mouseY, catX(), catY(i), catW(), CAT_H - 4);
            int color;
            if (i == selected) {
                color = Theme.rgba(Theme.TEXT, 0.98f * appear);
            } else if (hover) {
                color = Theme.rgba(categories[i].color, 0.9f * appear);
            } else {
                color = Theme.rgba(Theme.TEXT_2, 0.75f * appear);
            }
            Draw.text(ms, categories[i].display, catX() + sf(9), catY(i) + sf(9), color);
        }

        Draw.textRight(ms, "v" + Prismatica.VERSION, px + sw - sf(6), py + ph - sf(14),
                Theme.rgba(Theme.TEXT_2, 0.55f * appear));
    }

    private void drawHeader(MatrixStack ms, float appear, boolean rounded) {
        int hx = px + sw;
        int hw = pw - sw;
        Draw.fill(ms, hx + 1, py + sc(HEADER - 1), hw - 1, 1, Theme.rgba(Theme.WHITE, 0.07f * appear));

        Category category = Category.values()[selected];
        float titleY = py + sf(HEADER / 2 - 4);
        Draw.text(ms, category.display, hx + sf(PAD), titleY, Theme.rgba(category.color, appear));
        Draw.textRight(ms, "ESC", px + pw - sf(PAD), titleY, Theme.rgba(Theme.TEXT_2, 0.6f * appear));

        Draw.rounded(ms, hx + sc(PAD), py + sc(HEADER - 3), sc(30), Math.max(1, sc(2)),
                rounded ? 1 : 0, Theme.rgba(category.color, 0.9f * appear));
    }

    private void drawRows(MatrixStack ms, int mouseX, int mouseY, float appear, float dt, boolean smooth,
                          boolean rounded, int rSmall, boolean momentum) {
        scroll = Anim.approach(scroll, Anim.clamp(scrollTarget, 0f, maxScroll()), momentum ? 0.3f : 1f, dt);

        int top = contentTop();
        int view = viewport();

        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            float delay = i * 0.05f;
            row.appear = smooth
                    ? Anim.easeOutCubic(Anim.clamp((open - delay) / Math.max(0.001f, 1f - delay), 0f, 1f))
                    : 1f;

            int ry = rowY(i);
            if (ry + ROW < top - 2 || ry > top + view + 2) {
                continue;
            }

            boolean hover = inside(mouseX, mouseY, rowX(), ry, rowW(), ROW);
            row.hover = Anim.approach(row.hover, hover ? 1f : 0f, 0.25f, dt);

            float a = row.appear * appear;
            int rx = rowX() + Math.round((1f - row.appear) * 20f * scale);
            int rw = rowW();
            int textY = ry + Math.round((ROW - 8) / 2f);

            Draw.rounded(ms, rx, ry, rw, ROW, rSmall,
                    Theme.rgba(Theme.PANEL_2, (0.35f + 0.65f * row.hover) * a));

            // Status dot
            float t = row.module.progress();
            Draw.circle(ms, rx + sc(8), ry + ROW / 2, Math.max(1, sc(2)),
                    Theme.rgba(row.module.color, (0.4f + 0.6f * t) * a));

            Draw.text(ms, row.module.name, rx + sf(15), textY, Theme.rgba(Theme.TEXT, 0.55f + 0.45f * a));

            // Switch
            int tw = sc(26);
            int th = sc(14);
            int tx = rx + rw - sc(10) - tw;
            int ty = ry + (ROW - th) / 2;
            int track = Theme.mix(Theme.rgba(Theme.TRACK, 0.95f * a), Theme.rgba(Theme.accent(), a), t);
            Draw.rounded(ms, tx, ty, tw, th, th / 2, track);
            float knobX = Anim.lerp(tx + th / 2f, tx + tw - th / 2f, t);
            Draw.circle(ms, Math.round(knobX), ry + ROW / 2, Math.max(2, sc(5)), Theme.rgba(Theme.WHITE, a));

            // Hint text, only when it actually fits
            String hint = row.module.hint;
            if (hint != null && !hint.isEmpty()) {
                float hintRight = tx - sf(8);
                float nameRight = rx + sf(15) + Draw.textWidth(row.module.name);
                if (Draw.textWidth(hint) < hintRight - nameRight - sf(6)) {
                    Draw.textRight(ms, hint, hintRight, textY, Theme.rgba(Theme.TEXT_2, 0.7f * a));
                }
            }
        }

        // Slim scrollbar
        float max = maxScroll();
        if (max > 1f) {
            int barH = Math.max(20, Math.round(view * (view / (view + max))));
            int barY = top + Math.round((view - barH) * (scroll / max));
            int barX = px + pw - sc(5);
            Draw.rounded(ms, barX, top, Math.max(1, sc(2)), view, 1, Theme.rgba(Theme.TRACK, 0.6f * appear));
            Draw.rounded(ms, barX, barY, Math.max(1, sc(2)), barH, 1, Theme.rgba(Theme.accent(), 0.85f * appear));
        }
    }

    private float maxScroll() {
        return Math.max(0f, contentHeight() - viewport());
    }

    private int contentHeight() {
        return rows.size() * sc(ROW + GAP);
    }

    private int viewport() {
        return ph - sc(HEADER + PAD * 2);
    }

    private int contentTop() {
        return py + sc(HEADER + PAD);
    }

    private int rowY(int index) {
        return contentTop() + index * sc(ROW + GAP) - Math.round(scroll);
    }

    private int rowX() {
        return px + sw + sc(PAD);
    }

    private int rowW() {
        return pw - sw - sc(PAD * 2);
    }

    private int catX() {
        return px + sc(6);
    }

    private int catW() {
        return sw - sc(12);
    }

    private int catY(int index) {
        return py + sc(HEADER + PAD + index * CAT_H);
    }

    /** Design units to scaled pixels, rounded. */
    private int sc(int v) {
        return Math.round(v * scale);
    }

    /** Design units to scaled pixels, fractional (for text positions). */
    private float sf(int v) {
        return v * scale;
    }

    private static boolean inside(int x, int y, int rx, int ry, int rw, int rh) {
        return x >= rx && x < rx + rw && y >= ry && y < ry + rh;
    }
}
