package loggamja.sync.mixin;

import loggamja.sync.TickSyncMain;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;

import net.minecraft.network.packet.s2c.play.UpdateTickRateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {
    @Inject(method = "onEntity", at = @At("HEAD"))
    private void onEntityUpdate(EntityS2CPacket packet, CallbackInfo ci) {
        TickSyncMain.INSTANCE.onEntityPacket();
    }
    @Inject(method = "onUpdateTickRate", at = @At("HEAD"))
    private void onUpdateTickRate(UpdateTickRateS2CPacket packet, CallbackInfo ci) {
        TickSyncMain.INSTANCE.serverTPS = packet.tickRate();
    }
}
