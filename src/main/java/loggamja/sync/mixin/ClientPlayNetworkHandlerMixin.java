package loggamja.sync.mixin;

import loggamja.sync.TickSyncMain;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTickingStatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPlayNetworkHandlerMixin {
    @Inject(method = "handleMoveEntity", at = @At("HEAD"))
    private void ticksync$onEntityUpdate(ClientboundMoveEntityPacket packet, CallbackInfo ci) {
        TickSyncMain.INSTANCE.onEntityPacket();
    }

    @Inject(method = "handleTickingState", at = @At("HEAD"))
    private void ticksync$onUpdateTickRate(ClientboundTickingStatePacket packet, CallbackInfo ci) {
        TickSyncMain.INSTANCE.serverTPS = packet.tickRate();
        TickSyncMain.INSTANCE.clientTPS = Math.min(20, packet.tickRate());
    }
}
