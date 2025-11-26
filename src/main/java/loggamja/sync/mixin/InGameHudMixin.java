package loggamja.sync.mixin;

import loggamja.sync.TickSyncMain;
import loggamja.sync.TickSyncConfig;
//import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
//import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
//import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;

import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.text.Text;

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

        String text = "Tick Delay: " + TickSyncMain.avgPacketDelay + "ms";

        int x = client.getWindow().getScaledWidth() - 10;
        int y = 10;
        x -= client.textRenderer.getWidth(text);

        if (TickSyncMain.avgPacketDelay > 25) {
            context.drawTextWithShadow(client.textRenderer, text, x, y, 0xFFFF00);
        }
        else {
            context.drawTextWithShadow(client.textRenderer, text, x, y, 0x00FFFF);
        }

        // histogram by towercrain
        var histogramHeight = 20;
        var histogramSize = TickSyncMain.samplingRange;
        int x2 = client.getWindow().getScaledWidth() - 10;
        x2 -= histogramSize;

        for (int i = 0; i < histogramSize; i++) {
            float c3Intensity = TickSyncMain.packetDelayHistogram[i];
            float c3r = c3Intensity;
            float c3g = c3Intensity;
            float c3b = c3Intensity;
            if (i < 25) { c3r *= 0.01f; c3g *= 0.10f; c3b *= 1.00f; }
            else                   { c3r *= 1.00f; c3g *= 0.10f; c3b *= 0.01f; }
            if (i % 50 == 0)       { c3r += 0.01f; c3g += 0.10f; c3b += 0.01f; }
            int c3fr = Math.clamp(Math.round(255.0 * Math.pow(Math.max(c3r, 0.0), 1.0/2.2)), 0, 255);
            int c3fg = Math.clamp(Math.round(255.0 * Math.pow(Math.max(c3g, 0.0), 1.0/2.2)), 0, 255);
            int c3fb = Math.clamp(Math.round(255.0 * Math.pow(Math.max(c3b, 0.0), 1.0/2.2)), 0, 255);
            context.drawTextWithShadow(client.textRenderer, "|", x2 + histogramSize - (i + 1)  , histogramHeight, c3fr<<16|c3fg<<8|c3fb);
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

