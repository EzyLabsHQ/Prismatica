package io.github.ezylabshq.prismatica.ui.hud;

import com.mojang.blaze3d.matrix.MatrixStack;
import io.github.ezylabshq.prismatica.config.Settings;
import io.github.ezylabshq.prismatica.ui.Draw;
import io.github.ezylabshq.prismatica.ui.Prismatica;
import io.github.ezylabshq.prismatica.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.RegistryKey;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * One line of information in a corner of the screen.
 *
 * <p>Which segments appear, and which corner the line sits in, both come from
 * settings. Nothing here is derived from other players or from server
 * information: it is the mod version, the frame rate the client already
 * measures for its own debug overlay, the biome name from the world, and the
 * player's own position. All four are things vanilla already shows you in F3.
 */
public class Watermark {

    private static final Logger LOGGER = LogManager.getLogger("Prismatica");

    private boolean logged;

    @SubscribeEvent
    public void onRenderText(RenderGameOverlayEvent.Text event) {
        if (!Settings.boolOf("setting.watermark")) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ClientWorld world = mc.level;
        PlayerEntity player = mc.player;
        if (world == null || player == null) {
            return;
        }

        if (!logged) {
            logged = true;
            LOGGER.info("Prismatica watermark rendering");
        }

        List<String> parts = new ArrayList<>();
        parts.add("Prismatica " + Prismatica.VERSION);
        if (Settings.boolOf("setting.fps")) {
            parts.add(mc.fpsString);
        }
        if (Settings.boolOf("setting.biome")) {
            String biome = biomeName(world, player);
            if (!biome.isEmpty()) {
                parts.add(biome);
            }
        }
        if (Settings.boolOf("setting.coords")) {
            parts.add(String.format(Locale.ROOT, "%.0f %.0f %.0f",
                    player.getX(), player.getY(), player.getZ()));
        }
        if (parts.isEmpty()) {
            return;
        }

        MatrixStack ms = event.getMatrixStack();
        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();
        render(ms, width, height, parts);
    }

    /**
     * Lays the segments out left to right inside one pill.
     *
     * <p>Right hand corners mirror the whole line rather than each segment, so
     * the version number always ends up on the outside edge.
     */
    private void render(MatrixStack ms, int width, int height, List<String> parts) {
        int padX = 4;
        int gap = 5;
        int h = 11;

        int[] widths = new int[parts.size()];
        int total = padX * 2;
        for (int i = 0; i < parts.size(); i++) {
            widths[i] = Draw.textWidth(parts.get(i));
            total += widths[i];
            if (i > 0) {
                total += gap;
            }
        }

        int corner = Settings.choiceOf("setting.corner");
        boolean right = corner == 1 || corner == 3;
        boolean bottom = corner >= 2;

        int margin = 3;
        int x = right ? width - total - margin : margin;
        int y = bottom ? height - h - margin : margin;

        Draw.rounded(ms, x, y, total, h, Settings.radiusSmall(), Theme.rgba(Theme.BLACK, 0.35f));

        int cursor = x + padX;
        for (int i = 0; i < parts.size(); i++) {
            // The version is dimmed, the live values are accent coloured, so the
            // line reads as "brand" plus "state" rather than four equal labels.
            boolean first = i == 0;
            int color = first
                    ? Theme.rgba(Theme.TEXT, 0.9f)
                    : Theme.rgba(Theme.accent(), 0.95f);
            Draw.text(ms, parts.get(i), cursor, y + 2, color);
            cursor += widths[i] + gap;
        }
    }

    /**
     * The biome's registry path, or an empty string when it cannot be read.
     *
     * <p>Read through {@code IBiomeReader.getBiomeName} rather than off the
     * {@code Biome} instance: in 1.16.5 biome instances carry no key of their
     * own, and the name is a property of the position in the world.
     */
    private static String biomeName(ClientWorld world, PlayerEntity player) {
        try {
            Optional<RegistryKey<Biome>> key = world.getBiomeName(player.blockPosition());
            if (!key.isPresent()) {
                return "";
            }
            return key.get().location().getPath();
        } catch (RuntimeException e) {
            // A modded biome or an unloaded chunk can throw here; a missing
            // biome name is not worth taking the whole overlay down for.
            return "";
        }
    }
}
