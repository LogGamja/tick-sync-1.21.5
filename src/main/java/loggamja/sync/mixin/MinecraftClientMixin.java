package loggamja.sync.mixin;

import loggamja.sync.TickSyncMain;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Shadow @Nullable public ClientWorld world;

    @Inject(method = "getTargetMillisPerTick", at = @At("HEAD"), cancellable = true)
    private void ticksync$uncapClientTickRate(float millis, CallbackInfoReturnable<Float> cir) {
        if (TickSyncMain.INSTANCE.clientTPS > 20 && this.world != null && this.world.getTickManager().shouldTick()) {
            cir.setReturnValue(1000f / TickSyncMain.INSTANCE.clientTPS);
        }
    }
}