package io.github.ezylabshq.prismatica.ui.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registry of visual modules.
 *
 * <p>Everything listed here is cosmetic only: interface styling, HUD decoration.
 * Nothing here automates gameplay or gives an advantage over other players.
 */
public final class ModuleManager {

    private static final List<Module> MODULES = new ArrayList<>();
    private static boolean initialised;

    private ModuleManager() {
    }

    public static void init() {
        if (initialised) {
            return;
        }
        initialised = true;

        // Interface
        add(new Module("module.rounded", "module.rounded", Category.UI, 0x7C5CFF));
        add(new Module("module.smooth", "module.smooth", Category.UI, 0x9B6BFF));
        add(new Module("module.momentum", "module.momentum", Category.UI, 0x5AC8FA));

        // Render
        add(new Module("module.border", "module.border", Category.RENDER, 0x2ED3B7));

        // HUD
        add(new Module("module.watermark", "module.watermark", Category.HUD, 0xFFB020));

        set("module.rounded", true);
        set("module.smooth", true);
        set("module.momentum", true);
        set("module.watermark", true);
    }

    private static void add(Module module) {
        MODULES.add(module);
    }

    public static List<Module> all() {
        return Collections.unmodifiableList(MODULES);
    }

    public static List<Module> in(Category category) {
        List<Module> out = new ArrayList<>();
        for (Module module : MODULES) {
            if (module.category == category) {
                out.add(module);
            }
        }
        return out;
    }

    public static void update(float dt) {
        for (Module module : MODULES) {
            module.update(dt);
        }
    }

    public static boolean isOn(String name) {
        Module module = find(name);
        return module != null && module.isEnabled();
    }

    public static void set(String name, boolean value) {
        Module module = find(name);
        if (module != null) {
            module.setEnabled(value);
        }
    }

    /**
     * Looked up by translation key, not by the visible name.
     *
     * <p>Matching on the name would break the moment the language changed,
     * since {@code name()} is resolved on read.
     */
    private static Module find(String nameKey) {
        for (Module module : MODULES) {
            if (module.nameKey.equals(nameKey)) {
                return module;
            }
        }
        return null;
    }
}
