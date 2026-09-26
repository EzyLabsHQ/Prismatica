package io.github.ezylabshq.prismatica.ui;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.ezylabshq.prismatica.config.Lang;
import io.github.ezylabshq.prismatica.config.Settings;
import io.github.ezylabshq.prismatica.ui.hud.Watermark;
import io.github.ezylabshq.prismatica.ui.menu.MenuStyler;
import io.github.ezylabshq.prismatica.ui.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.command.CommandSource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

/**
 * Prismatica - a reactive client interface with cosmetic visual modules.
 *
 * <p>Client side only. No gameplay automation, no network hooks, nothing that
 * would change how the game plays.
 *
 * <p>Copyright (C) 2026 EzyLabsHQ
 *
 * <p>This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details.
 *
 * <p>You should have received a copy of the GNU General Public License along
 * with this program. If not, see &lt;https://www.gnu.org/licenses/&gt;.
 */
@Mod(Prismatica.MOD_ID)
public class Prismatica {

    public static final String MOD_ID = "prismatica";
    public static final String VERSION = "0.1.0";

    private static final Logger LOGGER = LogManager.getLogger("Prismatica");

    private static KeyBinding openKey;
    private static KeyBinding openKeyAlt;
    private int langPoll;

    public Prismatica() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        // Before anything samples the palette, so the first frame the player
        // sees already uses their saved values and the right language.
        //
        // There is no save-on-exit hook: Forge 36.2.42 has no client stopping
        // event (only FMLServerStoppingEvent), and every value is written the
        // moment it is changed, so there is nothing left to flush at shutdown.
        Settings.load();
        Lang.detect();

        // Vanilla translation keys, not resolved strings. KeyBindingList wraps
        // the description in a TranslationTextComponent when it builds the row,
        // so the controls screen translates it at render time and picks up a
        // language change on its own. Passing an already translated string
        // would work once and then break, because vanilla would look that
        // sentence up as a key, miss, and fall back to showing the key.
        openKey = new KeyBinding("key.prismatica.open", GLFW.GLFW_KEY_RIGHT_SHIFT, "key.categories.prismatica");
        openKeyAlt = new KeyBinding("key.prismatica.open_alt", GLFW.GLFW_KEY_B, "key.categories.prismatica");
        ClientRegistry.registerKeyBinding(openKey);
        ClientRegistry.registerKeyBinding(openKeyAlt);

        MinecraftForge.EVENT_BUS.register(new Watermark());
        MinecraftForge.EVENT_BUS.register(new MenuStyler());
        ModuleManager.init();

        // The language is deliberately not printed here: the first read happens
        // before the language manager has loaded, so it would report the wrong
        // one. It is logged from the poll above once it settles.
        LOGGER.info("Prismatica {} ready. Press RIGHT SHIFT or B, or run /prismatica in chat.", VERSION);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                LiteralArgumentBuilder.<CommandSource>literal("prismatica").executes(context -> {
                    toggleGui();
                    return 1;
                }));
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        // Keep the world-driven palette alive at all times, not just while the
        // menu is open, so the HUD watermark re-tints as you travel.
        float dt = 1f / 20f;
        WorldTheme.sample(dt);
        Theme.tick(dt);

        // Forge 36.2.42 has no language change event to subscribe to, so the
        // selected language is re-read once a second instead. The menus and the
        // settings labels resolve through Lang on every draw, so picking up the
        // new code is all it takes for the whole interface to follow the game
        // setting. A second is slow enough to be invisible and fast enough that
        // nobody notices the wait after changing the language in Options.
        //
        // The first read has to be treated as provisional: at client setup the
        // language manager has not loaded the resource pack languages yet, so it
        // still reports the previous or default language. Logging it here as
        // though it were final is what produced a startup line claiming en_us
        // while the interface was plainly Russian.
        if (++langPoll >= 20) {
            langPoll = 0;
            String before = Lang.code();
            Lang.detect();
            String after = Lang.code();
            if (!after.equals(before)) {
                LOGGER.info("Prismatica interface language: {}", after);
            }
        }

        if (openKey != null) {
            while (openKey.consumeClick()) {
                toggleGui();
            }
        }
        if (openKeyAlt != null) {
            while (openKeyAlt.consumeClick()) {
                toggleGui();
            }
        }
    }

    private static void toggleGui() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ClickGuiScreen) {
            mc.setScreen(null);
        } else {
            mc.setScreen(new ClickGuiScreen());
            LOGGER.info("Prismatica menu opened");
        }
    }
}
