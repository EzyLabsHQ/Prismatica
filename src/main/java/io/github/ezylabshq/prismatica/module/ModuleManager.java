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
        add(new Module("Rounded Corners", "Soft 9px radius", Category.UI, 0x7C5CFF));
        add(new Module("Smooth Animations", "Eased transitions", Category.UI, 0x9B6BFF));
        add(new Module("Scroll Momentum", "Inertial scrolling", Category.UI, 0x5AC8FA));

        // Render
        add(new Module("Accent Border", "1px accent outline", Category.RENDER, 0x2ED3B7));

        // HUD
        add(new Module("Watermark", "Corner info line", Category.HUD, 0xFFB020));

        set("Rounded Corners", true);
        set("Smooth Animations", true);
        set("Scroll Momentum", true);
        set("Watermark", true);
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

    private static Module find(String name) {
        for (Module module : MODULES) {
            if (module.name.equals(name)) {
                return module;
            }
        }
        return null;
    }
}
