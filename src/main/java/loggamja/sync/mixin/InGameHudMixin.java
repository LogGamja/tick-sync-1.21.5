package loggamja.sync.mixin;

import loggamja.sync.TickSyncHUDManager;
import loggamja.sync.TickSyncMain;
import loggamja.sync.TickSyncConfig;
//import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
//import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
//import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;

import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.*;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Shadow @Final private MinecraftClient client;

    @Shadow
    public abstract void tick(boolean paused);

    // render(MatrixStack, float, CallbackInfo)
    @Inject(method = "render", at = @At("HEAD"))
    private void onRender(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (client.player == null) return;

        TickSyncConfig cfg = TickSyncConfig.INSTANCE;
        if (!cfg.useDebugScreen) return;

        final int space = 8;
        final int term = 10;

        // 안정성
        String text4 = "Stability: " + TickSyncHUDManager.INSTANCE.packetRange + "ms";
        int x4 = client.getWindow().getScaledWidth() - space;
        int y4 = space;
        x4 -= client.textRenderer.getWidth(text4);
        context.drawTextWithShadow(client.textRenderer, text4, x4, y4, 0xFFFF00);

        // 딜레이
        String text = "Tick Delay: " + TickSyncHUDManager.INSTANCE.avgPacketDelay + "ms";
        int x = client.getWindow().getScaledWidth() - space;
        int y = space + term;
        x -= client.textRenderer.getWidth(text);
        context.drawTextWithShadow(client.textRenderer, text, x, y, 0x00FFFF);

        // 싱크가 불가능하다고 알리는 텍스트
        if (!TickSyncHUDManager.INSTANCE.canSync) {
            String text3 = "\uD83D\uDEC7";
            int x3 = client.getWindow().getScaledWidth() - 90;
            int y3 = space + term;
            x3 -= client.textRenderer.getWidth(text3);
            context.drawTextWithShadow(client.textRenderer, text3, x3, y3, 0xFF0000);
        }

        // 싱크 중임을 알리는 텍스트
        if (TickSyncHUDManager.INSTANCE.syncTextAlpha > 0 && TickSyncHUDManager.INSTANCE.canSync) {
            String text3 = "\uD83D\uDD04";
            int x3 = client.getWindow().getScaledWidth() - 90;
            int y3 = space + term;
            x3 -= client.textRenderer.getWidth(text3);
            context.drawTextWithShadow(client.textRenderer, text3, x3, y3, 0x00FFFF);
        }

        // histogram by towercrain
        var histogramHeight = space + term * 2;
        var histogramSize = TickSyncMain.INSTANCE.samplingRange;
        int x2 = client.getWindow().getScaledWidth() - space;
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
            context.drawTextWithShadow(client.textRenderer, "|", x2 + histogramSize - (i + 1), histogramHeight, c3fr<<16|c3fg<<8|c3fb);
        }

        //final Identifier K_HUD_ID =Identifier.of("khudexample", "k_hud");

        //HudElementRegistry.attachElementBefore(
        //
        //        VanillaHudElements.CHAT,
        //        K_HUD_ID,
        //        new HudElement() {
        //            @Override
        //            public void render(DrawContext context, RenderTickCounter tickCounter) {
        //                MinecraftClient mc = MinecraftClient.getInstance();
        //                int x = 10;
        //                int y = 10;
        //                int color = 0xFFFFFFFF; // 흰색
        //                Text t = Text.literal("ㅋ");
        //                context.drawTextWithShadow(mc.textRenderer, t, x, y, color);
        //            }
        //        }
        //);

    }
}

