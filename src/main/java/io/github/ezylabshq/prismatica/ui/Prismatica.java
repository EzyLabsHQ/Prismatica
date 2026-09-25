package io.github.ezylabshq.prismatica.ui;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.ezylabshq.prismatica.ui.hud.Watermark;
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
 * <p>Copyright (C) 2026 EzyLabs
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

    public Prismatica() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        openKey = new KeyBinding("key.prismatica.open", GLFW.GLFW_KEY_RIGHT_SHIFT, "key.categories.prismatica");
        openKeyAlt = new KeyBinding("key.prismatica.open_alt", GLFW.GLFW_KEY_B, "key.categories.prismatica");
        ClientRegistry.registerKeyBinding(openKey);
        ClientRegistry.registerKeyBinding(openKeyAlt);

        MinecraftForge.EVENT_BUS.register(new Watermark());
        ModuleManager.init();

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
