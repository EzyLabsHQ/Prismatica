package io.github.ezylabshq.prismatica.ui;

/** Frame-rate independent animation helpers. */
public final class Anim {

    private Anim() {
    }

    /**
     * Exponential smoothing that behaves the same at any frame rate.
     *
     * @param perFrame fraction of the remaining distance covered in 1/60 s
     */
    public static float approach(float current, float target, float perFrame, float dt) {
        float p = clamp(perFrame, 0.0001f, 0.9999f);
        float f = 1f - (float) Math.pow(1f - p, dt * 60f);
        return current + (target - current) * f;
    }

    public static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Normalised progress of {@code t} inside the [start, end] window. */
    public static float range(float t, float start, float end) {
        return clamp((t - start) / (end - start), 0f, 1f);
    }

    public static float easeOutCubic(float t) {
        t = clamp(t, 0f, 1f);
        float u = 1f - t;
        return 1f - u * u * u;
    }

    public static float easeInOutCubic(float t) {
        t = clamp(t, 0f, 1f);
        return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3f) / 2f;
    }

    public static float easeOutQuint(float t) {
        t = clamp(t, 0f, 1f);
        float u = 1f - t;
        return 1f - u * u * u * u * u;
    }

    /** Slight overshoot, used for panel entry. */
    public static float easeOutBack(float t) {
        t = clamp(t, 0f, 1f);
        float c1 = 1.35f;
        float c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }
}
