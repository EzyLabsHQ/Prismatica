package io.github.ezylabshq.prismatica.ui.menu;

import io.github.ezylabshq.prismatica.config.Lang;
import io.github.ezylabshq.prismatica.config.Settings;
import io.github.ezylabshq.prismatica.ui.Theme;
import io.github.ezylabshq.prismatica.ui.WorldTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.IngameMenuScreen;
import net.minecraft.client.gui.screen.MainMenuScreen;
import net.minecraft.client.gui.screen.MultiplayerScreen;
import net.minecraft.client.gui.screen.OptionsScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.WorldSelectionScreen;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Applies the Prismatica look to the vanilla menu screens.
 *
 * <p>Only the chrome is replaced here: the backdrop, the header strip and the
 * button styling (which lives in {@code ButtonMixin}). Widget positions are
 * deliberately left alone, because each vanilla screen has its own layout
 * contract: the options screen is a two column grid, the world and server
 * screens put their buttons along the bottom, and flattening any of those into
 * a single column is what makes the interface look broken.
 */
public final class MenuStyler {

    public MenuStyler() {
    }

    /** True for the screens we restyle. */
    public static boolean isStyled(Screen screen) {
        return screen instanceof MainMenuScreen
                || screen instanceof WorldSelectionScreen
                || screen instanceof MultiplayerScreen
                || screen instanceof OptionsScreen
                || screen instanceof IngameMenuScreen;
    }

    private static String headerTitle(Screen screen) {
        if (screen instanceof WorldSelectionScreen) {
            return Lang.t("chrome.screen.worlds");
        }
        if (screen instanceof MultiplayerScreen) {
            return Lang.t("chrome.screen.servers");
        }
        if (screen instanceof OptionsScreen) {
            return Lang.t("chrome.screen.options");
        }
        if (screen instanceof IngameMenuScreen) {
            return Lang.t("chrome.screen.paused");
        }
        return "";
    }

    private static String headerSubtitle(Screen screen) {
        if (screen instanceof WorldSelectionScreen) {
            return Lang.t("chrome.sub.singleplayer");
        }
        if (screen instanceof MultiplayerScreen) {
            return Lang.t("chrome.sub.multiplayer");
        }
        if (screen instanceof OptionsScreen) {
            return Lang.t("chrome.sub.video");
        }
        if (screen instanceof IngameMenuScreen) {
            return Lang.t("chrome.sub.game");
        }
        return "";
    }

    /**
     * Bar height per screen, sized to cover the title that screen draws itself.
     *
     * <p>Read off the vanilla bytecode: {@code OptionsScreen} centres its title
     * at y=15, {@code WorldSelectionScreen} and {@code MultiplayerScreen} at
     * y=20, {@code IngameMenuScreen} at y=40. The font is 9px tall, so those
     * need 24, 29 and 49 pixels of cover respectively. A single height for all
     * of them either leaves the pause screen's title showing or swallows the
     * first row of options widgets, which start at y=48.
     */
    private static int headerHeight(Screen screen) {
        if (screen instanceof OptionsScreen) {
            return 26;
        }
        if (screen instanceof IngameMenuScreen) {
            return 52;
        }
        return 32;
    }

    /**
     * Draws the header strip once the screen has rendered its own title.
     *
     * <p>It has to run post: the vanilla title is drawn by the screen itself,
     * between the background and the widgets, so there is no earlier hook that
     * would still cover it.
     *
     * <p>With {@code menu.header} off nothing is drawn here, which leaves the
     * vanilla title visible on its own. That is the point of the switch: it is a
     * "cover the vanilla chrome" option, not a "hide the screen name" option.
     */
    @SubscribeEvent
    public void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
        Screen screen = event.getGui();
        if (!isStyled(screen)) {
            return;
        }
        if (Settings.boolOf("setting.header") && !(screen instanceof MainMenuScreen)) {
            MenuChrome.drawHeader(event.getMatrixStack(), screen.width, headerHeight(screen),
                    headerTitle(screen), headerSubtitle(screen));
        }
        MenuChrome.endStyledFrame();
    }

    /**
     * Keep the palette alive while the player is in menus, so the chrome still
     * reflects the world they are about to return to.
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null && isStyled(mc.screen)) {
            WorldTheme.sample(1f / 20f);
            Theme.tick(1f / 20f);
        }
    }
}
