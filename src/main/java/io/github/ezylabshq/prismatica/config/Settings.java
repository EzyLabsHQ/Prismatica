package io.github.ezylabshq.prismatica.config;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Every visual option Prismatica has, and where it is persisted.
 *
 * <p>The whole point of this class is that each entry here is actually read by
 * the renderer. The earlier module list looked like configuration but nothing
 * outside the ClickGui consulted it, so toggling a module changed nothing you
 * could see in the game.
 *
 * <p>Saved as a flat properties file in the game directory. Flat and hand rolled
 * on purpose: no serializer, no schema version to migrate, and a stray unknown
 * key is ignored rather than fatal.
 */
public final class Settings {

    // Accent presets. A colour picker is not really drawable with spans, and a
    // curated list is quicker to cycle through anyway.
    private static final String[] ACCENT_NAMES = {
            "VIOLET", "AZURE", "MINT", "AMBER", "CORAL", "ROSE", "LIME", "SLATE"
    };
    private static final int[] ACCENT_VALUES = {
            0x7C5CFF, 0x4C9BFF, 0x2ED3B7, 0xFFB020, 0xFF6B5A, 0xFF5CA8, 0x9BD65A, 0x8A93A8
    };

    // Backdrop styles, matched by index in MenuChrome.
    private static final String[] BACKDROP_NAMES = {"SOLID", "GRADIENT", "VIGNETTE"};
    private static final String[] FONT_NAMES = {"MICRO", "VANILLA"};
    private static final String[] CORNER_NAMES = {"TOP LEFT", "TOP RIGHT", "BOTTOM LEFT", "BOTTOM RIGHT"};

    private static final Map<String, Setting> REGISTRY = new LinkedHashMap<>();

    private static boolean loaded;

    private Settings() {
    }

    private static Setting reg(Setting setting) {
        REGISTRY.put(setting.labelKey, setting);
        return setting;
    }

    static {
        // --- palette -------------------------------------------------------
        reg(Setting.bool("setting.reactive", true));
        reg(Setting.choice("setting.accent", 0, ACCENT_NAMES, ACCENT_VALUES));
        reg(Setting.decimal("setting.speed", 1.0f, 0.20f, 3.0f, 0.1f));

        // --- shape ---------------------------------------------------------
        reg(Setting.bool("setting.rounded", true));
        reg(Setting.integer("setting.radius", 6, 0, 14, 1));
        reg(Setting.bool("setting.accentBorder", true));

        // --- menus ---------------------------------------------------------
        reg(Setting.choice("setting.backdrop", 1, BACKDROP_NAMES));
        reg(Setting.decimal("setting.backdropOpacity", 1.0f, 0.2f, 1.0f, 0.05f));
        reg(Setting.bool("setting.header", true));
        reg(Setting.choice("setting.headerFont", 0, FONT_NAMES));
        reg(Setting.bool("setting.buttons", true));
        reg(Setting.bool("setting.wordmark", true));

        // --- hud -----------------------------------------------------------
        reg(Setting.bool("setting.watermark", true));
        reg(Setting.bool("setting.fps", true));
        reg(Setting.bool("setting.biome", true));
        reg(Setting.bool("setting.coords", false));
        reg(Setting.choice("setting.corner", 0, CORNER_NAMES));

        // --- motion --------------------------------------------------------
        reg(Setting.bool("setting.smooth", true));
        reg(Setting.bool("setting.momentum", true));
        reg(Setting.decimal("setting.hover", 0.25f, 0.05f, 1.0f, 0.05f));
    }

    public static List<Setting> all() {
        return Collections.unmodifiableList(new ArrayList<>(REGISTRY.values()));
    }

    public static Setting get(String key) {
        return REGISTRY.get(key);
    }

    public static boolean boolOf(String key) {
        Setting s = REGISTRY.get(key);
        return s != null && s.boolValue;
    }

    public static int intOf(String key) {
        Setting s = REGISTRY.get(key);
        return s == null ? 0 : s.intValue;
    }

    public static float floatOf(String key) {
        Setting s = REGISTRY.get(key);
        return s == null ? 0f : s.floatValue;
    }

    public static int choiceOf(String key) {
        Setting s = REGISTRY.get(key);
        return s == null ? 0 : s.choice;
    }

    /** Corner radius, honouring the rounded-corners switch. */
    public static int radius() {
        return boolOf("setting.rounded") ? Math.max(0, Math.min(14, intOf("setting.radius"))) : 0;
    }

    /** Corner radius one step smaller, for the small elements. */
    public static int radiusSmall() {
        return Math.max(0, radius() - 2);
    }

    /** The accent to use when the reactive theme is switched off. */
    public static int fixedAccent() {
        Setting s = REGISTRY.get("setting.accent");
        int v = s == null ? ACCENT_VALUES[0] : s.asChoiceValue();
        return v < 0 ? ACCENT_VALUES[0] : v;
    }

    // ---- persistence -----------------------------------------------------

    private static Path configFile() {
        Minecraft mc = Minecraft.getInstance();
        return mc.gameDirectory.toPath().resolve("config").resolve("prismatica.properties");
    }

    /**
     * Reads the file once, before anything renders.
     *
     * <p>Unknown or unparseable entries fall back to the built in default
     * rather than stopping the game, because a hand edited config should never
     * be able to prevent the client from starting.
     */
    public static void load() {
        if (loaded) {
            return;
        }
        loaded = true;

        Path file = configFile();
        if (!Files.exists(file)) {
            return;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            return;
        }

        for (Setting setting : REGISTRY.values()) {
            String raw = props.getProperty(setting.labelKey);
            if (raw == null) {
                continue;
            }
            switch (setting.kind) {
                case BOOL:
                    setting.boolValue = Boolean.parseBoolean(raw);
                    break;
                case INT:
                    try {
                        int v = Integer.parseInt(raw.trim());
                        setting.intValue = Math.max(setting.min, Math.min(setting.max, v));
                    } catch (NumberFormatException ignored) {
                        // keep the default
                    }
                    break;
                case FLOAT:
                    try {
                        float v = Float.parseFloat(raw.trim());
                        setting.floatValue = Math.max(setting.fmin, Math.min(setting.fmax, v));
                    } catch (NumberFormatException ignored) {
                        // keep the default
                    }
                    break;
                case CHOICE:
                    try {
                        int v = Integer.parseInt(raw.trim());
                        if (setting.choices != null && v >= 0 && v < setting.choices.length) {
                            setting.choice = v;
                        }
                    } catch (NumberFormatException ignored) {
                        // keep the default
                    }
                    break;
                default:
                    break;
            }
        }
    }

    public static void save() {
        Path file = configFile();
        try {
            Files.createDirectories(file.getParent());
            Properties props = new Properties();
            for (Setting setting : REGISTRY.values()) {
                switch (setting.kind) {
                    case BOOL:
                        props.setProperty(setting.labelKey, Boolean.toString(setting.boolValue));
                        break;
                    case INT:
                        props.setProperty(setting.labelKey, Integer.toString(setting.intValue));
                        break;
                    case FLOAT:
                        props.setProperty(setting.labelKey, Float.toString(setting.floatValue));
                        break;
                    case CHOICE:
                        props.setProperty(setting.labelKey, Integer.toString(setting.choice));
                        break;
                    default:
                        break;
                }
            }
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "Prismatica visual settings");
            }
        } catch (IOException ignored) {
            // A read only game directory should not break the interface.
        }
    }
}
