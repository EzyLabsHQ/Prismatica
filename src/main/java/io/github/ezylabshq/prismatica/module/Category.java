package io.github.ezylabshq.prismatica.ui.module;

/** GUI sections. Each one owns its colour. */
public enum Category {

    UI("Interface", 0x7C5CFF),
    RENDER("Render", 0x2ED3B7),
    HUD("HUD", 0xFFB020);

    public final String display;
    public final int color;

    Category(String display, int color) {
        this.display = display;
        this.color = color;
    }
}
