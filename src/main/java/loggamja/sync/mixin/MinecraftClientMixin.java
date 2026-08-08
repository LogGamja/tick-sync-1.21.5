package loggamja.sync.mixin;

import loggamja.sync.TickSyncMain;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {
    @Shadow @Nullable public ClientLevel level;

    @Inject(method = "getTickTargetMillis", at = @At("HEAD"), cancellable = true)
    private void ticksync$uncapClientTickRate(float millis, CallbackInfoReturnable<Float> cir) {
        if (TickSyncMain.INSTANCE.clientTPS > 20 && this.level != null && this.level.tickRateManager().runsNormally()) {
            cir.setReturnValue(1000f / TickSyncMain.INSTANCE.clientTPS);
        }
    }
}