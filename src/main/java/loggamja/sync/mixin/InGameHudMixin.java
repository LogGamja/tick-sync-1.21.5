package loggamja.sync.mixin;

import loggamja.sync.TickSyncHUDManager;
import loggamja.sync.TickSyncMain;
import loggamja.sync.TickSyncConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.DeltaTracker;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class InGameHudMixin {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "render", at = @At("HEAD"))
    private void ticksync$onRender(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (minecraft.player == null) return;

        TickSyncConfig cfg = TickSyncConfig.INSTANCE;
        if (!cfg.useDebugScreen) return;

        final int space = 8;
        final int term = 10;

        // 안정성
        String text4 = "Stability: " + TickSyncHUDManager.INSTANCE.packetRange + "ms";
        int x4 = minecraft.getWindow().getGuiScaledWidth() - space;
        int y4 = space;
        x4 -= minecraft.font.width(text4);
        context.drawString(minecraft.font, text4, x4, y4, 0xFFFFFF00);

        // 딜레이
        String text = "Tick Delay: " + TickSyncHUDManager.INSTANCE.avgPacketDelay + "ms";
        int x = minecraft.getWindow().getGuiScaledWidth() - space;
        int y = space + term;
        x -= minecraft.font.width(text);
        context.drawString(minecraft.font, text, x, y, 0xFF00FFFF);

        // 싱크가 불가능하다고 알리는 텍스트
        if (!TickSyncHUDManager.INSTANCE.canSync) {
            String text3 = "\uD83D\uDEC7";
            int x3 = minecraft.getWindow().getGuiScaledWidth() - 90;
            int y3 = space + term;
            x3 -= minecraft.font.width(text3);
            context.drawString(minecraft.font, text3, x3, y3, 0xFFFF0000);
        }

        // 싱크 중임을 알리는 텍스트
        if (TickSyncHUDManager.INSTANCE.syncTextAlpha > 0 && TickSyncHUDManager.INSTANCE.canSync) {
            String text3 = "\uD83D\uDD04";
            int x3 = minecraft.getWindow().getGuiScaledWidth() - 90;
            int y3 = space + term;
            x3 -= minecraft.font.width(text3);
            context.drawString(minecraft.font, text3, x3, y3, 0xFF00FFFF);
        }

        // histogram by towercrain
        var histogramHeight = space + term * 2;
        var histogramSize = TickSyncMain.SAMPLING_RANGE;
        int x2 = minecraft.getWindow().getGuiScaledWidth() - space;
        x2 -= histogramSize;

        for (int i = 0; i < histogramSize; i++) {
            float c3Intensity = TickSyncHUDManager.INSTANCE.packetDelayHistogram[i];
            float c3r = c3Intensity;
            float c3g = c3Intensity;
            float c3b = c3Intensity;
            if (i < 25) { c3r *= 0.01f; c3g *= 0.10f; c3b *= 1.00f; }
            else                   { c3r *= 1.00f; c3g *= 0.10f; c3b *= 0.01f; }
            if (i % 50 == 0)       { c3r += 0.01f; c3g += 0.10f; c3b += 0.01f; }
            int c3fr = Math.clamp(Math.round(255.0 * Math.pow(Math.max(c3r, 0.0), 1.0/2.2)), 0, 255);
            int c3fg = Math.clamp(Math.round(255.0 * Math.pow(Math.max(c3g, 0.0), 1.0/2.2)), 0, 255);
            int c3fb = Math.clamp(Math.round(255.0 * Math.pow(Math.max(c3b, 0.0), 1.0/2.2)), 0, 255);
            int c3color = 0xFF000000 | (c3fr << 16) | (c3fg << 8) | c3fb;
            context.drawString(minecraft.font, "|", x2 + histogramSize - (i + 1), histogramHeight, c3color, false);
        }
    }
}
