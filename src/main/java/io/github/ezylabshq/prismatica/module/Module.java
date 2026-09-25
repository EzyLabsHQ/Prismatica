package io.github.ezylabshq.prismatica.ui.module;

import io.github.ezylabshq.prismatica.ui.Anim;

/** A single toggleable visual feature. */
public class Module {

    public final String name;
    public final String hint;
    public final Category category;
    public final int color;

    private boolean enabled;
    private float toggle;
    private boolean seeded;

    public Module(String name, String hint, Category category, int color) {
        this.name = name;
        this.hint = hint;
        this.category = category;
        this.color = color;
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
