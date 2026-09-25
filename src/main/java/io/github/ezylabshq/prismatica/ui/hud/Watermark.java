package io.github.ezylabshq.prismatica.ui.hud;

import com.mojang.blaze3d.matrix.MatrixStack;
import io.github.ezylabshq.prismatica.ui.Draw;
import io.github.ezylabshq.prismatica.ui.Prismatica;
import io.github.ezylabshq.prismatica.ui.Theme;
import io.github.ezylabshq.prismatica.ui.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Corner info line: mod version on the left, fps on the right. Decorative only. */
public class Watermark {

    private static final Logger LOGGER = LogManager.getLogger("Prismatica");

    private boolean logged;

    @SubscribeEvent
    public void onRenderText(RenderGameOverlayEvent.Text event) {
        if (!ModuleManager.isOn("Watermark")) {
            return;
        }

        if (!logged) {
            logged = true;
            LOGGER.info("Prismatica watermark rendering");
        }

        MatrixStack ms = event.getMatrixStack();
        int width = event.getWindow().getGuiScaledWidth();
        Minecraft mc = Minecraft.getInstance();

        String left = "Prismatica " + Prismatica.VERSION;
        String right = mc.fpsString;

        int leftW = Draw.textWidth(left);
        int rightW = Draw.textWidth(right);

        Draw.rounded(ms, 3, 2, leftW + 8, 11, 4, Theme.rgba(Theme.BLACK, 0.35f));
        Draw.text(ms, left, 7, 3, Theme.rgba(Theme.TEXT, 0.9f));

        Draw.rounded(ms, width - rightW - 11, 2, rightW + 8, 11, 4, Theme.rgba(Theme.BLACK, 0.35f));
        Draw.text(ms, right, width - rightW - 7, 3, Theme.rgba(Theme.accent(), 0.95f));
    }
}
