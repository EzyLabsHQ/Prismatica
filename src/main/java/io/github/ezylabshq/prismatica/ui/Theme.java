package io.github.ezylabshq.prismatica.ui;

/**
 * Live interface palette.
 *
 * <p>Colours are not constants here: {@link WorldTheme} feeds a target palette
 * sampled from the world, and the values below ease towards that target with a
 * critically damped spring. That is what makes the interface feel attached to
 * the world instead of painted on top of it.
 */
public final class Theme {

    private Theme() {
    }

    /** Resting palette, used in the main menu and while loading. */
    public static final int REST_ACCENT = 0x7C5CFF;
    public static final int REST_PANEL = 0x14161E;
    public static final int REST_SIDEBAR = 0x101219;

    // Fixed surfaces, these do not react to the world.
    public static final int BG = 0x05060A;
    public static final int PANEL_2 = 0x1A1D27;
    public static final int TEXT = 0xF2F3F7;
    public static final int TEXT_2 = 0x9A9DB0;
    public static final int TRACK = 0x2A2D3A;
    public static final int WHITE = 0xFFFFFF;
    public static final int BLACK = 0x000000;

    public static final int RADIUS = 9;
    public static final int RADIUS_SM = 6;

    // Live palette.
    private static int accent = REST_ACCENT;
    private static int panel = REST_PANEL;
    private static int sidebar = REST_SIDEBAR;

    // Spring state, one per live colour.
    private static final Spring ACCENT_SPRING = new Spring(0.22f);
    private static final Spring PANEL_SPRING = new Spring(0.18f);
    private static final Spring SIDEBAR_SPRING = new Spring(0.18f);

    private static int targetAccent = REST_ACCENT;
    private static int targetPanel = REST_PANEL;
    private static int targetSidebar = REST_SIDEBAR;

    static {
        ACCENT_SPRING.snap(REST_ACCENT);
        PANEL_SPRING.snap(REST_PANEL);
        SIDEBAR_SPRING.snap(REST_SIDEBAR);
    }

    /** Advance the springs. Call once per frame. */
    public static void tick(float dt) {
        accent = ACCENT_SPRING.step(accent, targetAccent, dt);
        panel = PANEL_SPRING.step(panel, targetPanel, dt);
        sidebar = SIDEBAR_SPRING.step(sidebar, targetSidebar, dt);
    }

    /** Called by {@link WorldTheme} with a freshly sampled palette. */
    public static void setTarget(int newAccent, int newPanel, int newSidebar, float speedScale) {
        targetAccent = newAccent;
        targetPanel = newPanel;
        targetSidebar = newSidebar;
        // Clamp the speed scale: a spike here would stiffen the spring so much
        // that a channel could ring instead of settling.
        float clamped = speedScale < 1f ? 1f : (speedScale > 1.8f ? 1.8f : speedScale);
        ACCENT_SPRING.speedScale = clamped;
    }

    public static int accent() {
        return accent;
    }

    /** Diagnostics: the colour the springs are travelling towards. */
    public static int targetAccent() {
        return targetAccent;
    }

    public static int targetPanel() {
        return targetPanel;
    }

    public static int panel() {
        return panel;
    }

    public static int sidebar() {
        return sidebar;
    }

    public static int rgba(int rgb, float alpha) {
        int a = Math.round(clampf(alpha, 0f, 1f) * 255f);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    /** Scales the alpha channel of an existing ARGB colour. */
    public static int fade(int argb, float alpha) {
        int a = Math.round(clampf(alpha, 0f, 1f) * ((argb >>> 24) & 0xFF));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    public static int withAlpha(int argb, int alpha) {
        return ((clampi(alpha, 0, 255)) << 24) | (argb & 0xFFFFFF);
    }

    public static int mix(int argb1, int argb2, float t) {
        t = clampf(t, 0f, 1f);
        int a = (int) Anim.lerp((argb1 >>> 24) & 0xFF, (argb2 >>> 24) & 0xFF, t);
        int r = (int) Anim.lerp((argb1 >> 16) & 0xFF, (argb2 >> 16) & 0xFF, t);
        int g = (int) Anim.lerp((argb1 >> 8) & 0xFF, (argb2 >> 8) & 0xFF, t);
        int b = (int) Anim.lerp(argb1 & 0xFF, argb2 & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static float clampf(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }

    private static int clampi(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }

    /**
     * Critically damped spring over a colour.
     *
     * <p>Unlike exponential smoothing this carries real velocity, so a fast
     * world change (sunset) can be caught mid-flight instead of trailing behind.
     * Each colour channel gets its own independent spring, so the palette can
     * visibly overshoot and settle rather than sliding.
     */
    private static final class Spring {

        private static final int CHANNELS = 3;
        private static final int SHIFT_R = 16;
        private static final int SHIFT_G = 8;
        private static final int SHIFT_B = 0;

        private final float baseStiffness;
        private final float[] position = new float[CHANNELS];
        private final float[] velocity = new float[CHANNELS];

        float speedScale = 1f;

        Spring(float stiffness) {
            this.baseStiffness = stiffness;
        }

        /** Seed the spring from a colour without any visible motion. */
        void snap(int colour) {
            position[0] = (colour >> SHIFT_R) & 0xFF;
            position[1] = (colour >> SHIFT_G) & 0xFF;
            position[2] = (colour >> SHIFT_B) & 0xFF;
            for (int i = 0; i < CHANNELS; i++) {
                velocity[i] = 0f;
            }
        }

        int step(int current, int target, float dt) {
            int targetR = (target >> SHIFT_R) & 0xFF;
            int targetG = (target >> SHIFT_G) & 0xFF;
            int targetB = (target >> SHIFT_B) & 0xFF;

            float[] goals = {targetR, targetG, targetB};
            float step = Math.min(dt, 0.05f);

            float k = baseStiffness * speedScale;
            // Critically damped: damping ratio 1 -> c = 2*sqrt(k)
            float damping = 2f * (float) Math.sqrt(k);

            // Sub-step so the spring stays stable even at 200+ fps.
            int subSteps = step > 1f / 90f ? 2 : 1;
            float h = step / subSteps;

            for (int i = 0; i < CHANNELS; i++) {
                for (int s = 0; s < subSteps; s++) {
                    float accel = -k * (position[i] - goals[i]) - damping * velocity[i];
                    velocity[i] += accel * h;
                    position[i] += velocity[i] * h;
                }
                if (position[i] < 0f) {
                    position[i] = 0f;
                    velocity[i] = 0f;
                } else if (position[i] > 255f) {
                    position[i] = 255f;
                    velocity[i] = 0f;
                }
                // Snap once we are visually settled, so the last pixel of a
                // channel can never leave a permanent 1-unit offset.
                if (Math.abs(position[i] - goals[i]) < 0.5f) {
                    position[i] = goals[i];
                    velocity[i] = 0f;
                }
            }

            return (clampi(Math.round(position[0]), 0, 255) << SHIFT_R)
                    | (clampi(Math.round(position[1]), 0, 255) << SHIFT_G)
                    | clampi(Math.round(position[2]), 0, 255);
        }
    }
}
