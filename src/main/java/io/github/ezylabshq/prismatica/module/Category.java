package io.github.ezylabshq.prismatica.ui.module;

import io.github.ezylabshq.prismatica.config.Lang;

/** GUI sections. Each one owns its colour. */
public enum Category {

    UI("category.ui", 0x7C5CFF),
    RENDER("category.render", 0x2ED3B7),
    HUD("category.hud", 0xFFB020);

    /** Translation key for the sidebar label. */
    public final String labelKey;
    public final int color;

    Category(String labelKey, int color) {
        this.labelKey = labelKey;
        this.color = color;
    }

    /** Resolved on read so a language change shows up without a restart. */
    public String display() {
        return Lang.t(labelKey);
    }
}
