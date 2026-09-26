package io.github.ezylabshq.prismatica.ui.module;

import io.github.ezylabshq.prismatica.config.Lang;
import io.github.ezylabshq.prismatica.ui.Anim;

/**
 * A single toggleable visual feature.
 *
 * <p>Names and hints are translation keys resolved on read, like the settings,
 * so changing the game language does not need a restart.
 */
public class Module {

    /** Translation keys, without the ".name" and ".hint" suffixes. */
    public final String nameKey;
    public final String hintKey;
    public final Category category;
    public final int color;

    private boolean enabled;
    private float toggle;
    private boolean seeded;

    public Module(String nameKey, String hintKey, Category category, int color) {
        this.nameKey = nameKey;
        this.hintKey = hintKey;
        this.category = category;
        this.color = color;
    }

    public String name() {
        return Lang.t(nameKey + ".name");
    }

    public String hint() {
        return Lang.t(hintKey + ".hint");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void toggle() {
        this.enabled = !this.enabled;
    }

    /** Animated 0..1 value used to drive the switch. */
    public float progress() {
        return toggle;
    }

    public void update(float dt) {
        if (!seeded) {
            toggle = enabled ? 1f : 0f;
            seeded = true;
        }
        toggle = Anim.approach(toggle, enabled ? 1f : 0f, 0.3f, dt);
    }
}
