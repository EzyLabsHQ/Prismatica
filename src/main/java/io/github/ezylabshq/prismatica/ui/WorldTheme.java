package io.github.ezylabshq.prismatica.ui;

import io.github.ezylabshq.prismatica.config.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

/**
 * Reads the world and turns it into a palette.
 *
 * <p>Every frame this samples the actual sky colour, grass tint, water tint,
 * time of day, weather and dimension of the world the player is standing in,
 * and blends that into a target palette. {@link Theme} then eases the live
 * palette towards that target, so walking from a forest into a desert, or
 * watching a sunset, or stepping into the Nether, re-tints the whole interface.
 *
 * <p>Nothing here is a hardcoded "theming preset": the colours come from the
 * game's own biome and sky definitions.
 */
public final class WorldTheme {

    private WorldTheme() {
    }

    /** Set by tools/ThemeProbe to run the sampler without a real world. */
    private static boolean overrideActive;
    private static int overrideSky;
    private static int overrideGrass;
    private static int overrideWater;
    private static long overrideDayTime;
    private static float overrideRain;

    /**
     * Test hook: feed synthetic world data instead of reading a live world.
     * Pass {@code false} to go back to reading the real world.
     */
    public static void override(int sky, int grass, int water, long dayTime, float rain, boolean active) {
        overrideActive = active;
        overrideSky = sky;
        overrideGrass = grass;
        overrideWater = water;
        overrideDayTime = dayTime;
        overrideRain = rain;
    }

    /** Sample the world and write a target palette into {@link Theme}. */
    public static void sample(float dt) {
        // With the reactive theme switched off the world is not read at all and
        // the palette is derived from the chosen accent instead.
        if (!Settings.boolOf("setting.reactive")) {
            applyFixed();
            return;
        }

        int sky;
        int grass;
        int water;
        long dayTime;
        float rain;

        if (overrideActive) {
            sky = overrideSky;
            grass = overrideGrass;
            water = overrideWater;
            dayTime = overrideDayTime;
            rain = overrideRain;
        } else {
            Minecraft mc = Minecraft.getInstance();

            // Outside a world (main menu, loading) keep the resting palette.
            if (mc.level == null || mc.player == null) {
                Theme.setTarget(Theme.REST_ACCENT, Theme.REST_PANEL, Theme.REST_SIDEBAR, 1f);
                return;
            }

            Biome biome = mc.level.getBiome(mc.player.blockPosition());
            BlockPos pos = mc.player.blockPosition();

            sky = biome.getSkyColor();
            grass = biome.getGrassColor(pos.getX(), pos.getZ());
            water = biome.getWaterColor();
            dayTime = mc.level.getDayTime() % 24000L;
            rain = mc.level.getRainLevel(1.0f);
        }

        float sunHeight = skyLightCurve(dayTime);

        int accent = mixMany(sky, grass, water, sunHeight, rain);
        int panel = shade(accent, 0.16f);
        int sidebar = shade(accent, 0.09f);

        // Faster when the world changes quickly (dawn/dusk), slower when idle.
        float speed = 1f + Math.abs(sunHeight - 0.5f) * 1.5f;
        Theme.setTarget(accent, panel, sidebar, speed);
    }

    /**
     * Palette used when the reactive theme is off: the chosen accent, with the
     * same panel and sidebar shading the world driven path applies.
     */
    private static void applyFixed() {
        int accent = Settings.fixedAccent();
        Theme.setTarget(accent, shade(accent, 0.16f), shade(accent, 0.09f), 1f);
    }

    /** 0 at deep night, 1 at noon, smooth through dawn and dusk. */
    private static float skyLightCurve(long dayTime) {
        // Noon sits at tick 6000, midnight at 18000.
        double phase = (dayTime - 6000L) / 24000.0 * Math.PI * 2.0;
        return (float) ((Math.cos(phase) + 1.0) * 0.5);
    }

    /**
     * Blend the sampled colours into one accent.
     *
     * <p>Sky carries most of the weight because it is what the player perceives
     * as "the colour of right now", grass grounds it in the biome, and water
     * only tints the result in oceans and rivers.
     */
    private static int mixMany(int sky, int grass, int water, float sunHeight, float rain) {
        int base = mix(sky, grass, 0.38f);
        int withWater = mix(base, water, 0.14f);

        float[] hsb = rgbToHsb(getR(withWater), getG(withWater), getB(withWater));

        // Night cools the hue towards blue instead of just dimming it.
        float night = 1f - sunHeight;
        hsb[0] = (hsb[0] - 0.08f * night + 1f) % 1f;

        // Rain washes the colour out.
        hsb[1] = hsb[1] * (1f - rain * 0.5f);

        // Daylight drives value. Rain darkens further, and we keep a floor so
        // the accent is still readable as an accent at midnight.
        hsb[2] = clampf(0.50f + 0.34f * sunHeight - rain * 0.16f, 0.42f, 0.92f);

        int[] rgb = hsbToRgb(hsb[0], hsb[1], hsb[2]);
        return (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
    }

    /** Derive a dark surface colour from an accent, keeping the hue readable. */
    private static int shade(int accent, float valueScale) {
        float[] hsb = rgbToHsb(getR(accent), getG(accent), getB(accent));
        hsb[2] = clampf(hsb[2] * valueScale, 0f, 1f);
        hsb[1] = hsb[1] * 0.72f;
        int[] rgb = hsbToRgb(hsb[0], hsb[1], hsb[2]);
        return (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
    }

    private static int mix(int c1, int c2, float t) {
        t = clamp01(t);
        int r = (int) (lerp(getR(c1), getR(c2), t));
        int g = (int) (lerp(getG(c1), getG(c2), t));
        int b = (int) (lerp(getB(c1), getB(c2), t));
        return (r << 16) | (g << 8) | b;
    }

    /** Rotate hue by rotating the normalised rgb triple. */
    private static int shiftHue(int c, float amount) {
        float[] hsb = rgbToHsb(getR(c), getG(c), getB(c));
        hsb[0] = (hsb[0] + amount + 1f) % 1f;
        int[] rgb = hsbToRgb(hsb[0], hsb[1], hsb[2]);
        return (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
    }

    private static float[] rgbToHsb(int r, int g, int b) {
        float rf = r / 255f;
        float gf = g / 255f;
        float bf = b / 255f;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float delta = max - min;

        float hue;
        if (delta == 0f) {
            hue = 0f;
        } else if (max == rf) {
            hue = ((gf - bf) / delta) % 6f;
        } else if (max == gf) {
            hue = ((bf - rf) / delta) + 2f;
        } else {
            hue = ((rf - gf) / delta) + 4f;
        }
        hue /= 6f;
        if (hue < 0f) {
            hue += 1f;
        }
        return new float[]{hue, max == 0f ? 0f : delta / max, max};
    }

    private static int[] hsbToRgb(float h, float s, float v) {
        int i = (int) (h * 6f);
        float f = h * 6f - i;
        float p = v * (1f - s);
        float q = v * (1f - f * s);
        float t = v * (1f - (1f - f) * s);

        int r;
        int g;
        int b;
        switch (i % 6) {
            case 0: r = ch(v); g = ch(t); b = ch(p); break;
            case 1: r = ch(q); g = ch(v); b = ch(p); break;
            case 2: r = ch(p); g = ch(v); b = ch(t); break;
            case 3: r = ch(p); g = ch(q); b = ch(v); break;
            case 4: r = ch(t); g = ch(p); b = ch(v); break;
            default: r = ch(v); g = ch(p); b = ch(q); break;
        }
        return new int[]{r, g, b};
    }

    /** Normalised 0..1 channel to an 8 bit channel. */
    private static int ch(float normalised) {
        return clamp255(Math.round(normalised * 255f));
    }

    private static int desaturate(int c, float amount) {
        amount = clamp01(amount);
        int r = getR(c);
        int g = getG(c);
        int b = getB(c);
        int grey = (int) ((r + g + b) / 3f);
        return (f2i(lerp(r, grey, amount)) << 16)
                | (f2i(lerp(g, grey, amount)) << 8)
                | f2i(lerp(b, grey, amount));
    }

    private static int scale(int c, float factor) {
        return (clamp255(f2i(getR(c) * factor)) << 16)
                | (clamp255(f2i(getG(c) * factor)) << 8)
                | clamp255(f2i(getB(c) * factor));
    }

    private static int getR(int c) {
        return (c >> 16) & 0xFF;
    }

    private static int getG(int c) {
        return (c >> 8) & 0xFF;
    }

    private static int getB(int c) {
        return c & 0xFF;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static int f2i(float f) {
        return Math.round(f);
    }

    private static int clamp255(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    private static float clamp01(float v) {
        return clampf(v, 0f, 1f);
    }

    private static float clampf(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }
}
