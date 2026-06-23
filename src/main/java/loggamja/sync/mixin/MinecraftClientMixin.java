package loggamja.sync.mixin;

import loggamja.sync.TickSyncMain;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.tick.TickManager;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Shadow @Nullable public ClientWorld world;
    /**
     * @author LogGamja
     * @reason 일시적으로 틱 레이트를 20보다 더 빠르게 하기 위해서
     */
    @Overwrite
    private float getTargetMillisPerTick(float millis) {
        if (this.world != null) {
            TickManager tickManager = this.world.getTickManager();
            if (tickManager.shouldTick()) {
                if (TickSyncMain.INSTANCE.clientTPS > 20) {
                    return 1000 / TickSyncMain.INSTANCE.clientTPS;
                }
                else {
                    return Math.max(millis, tickManager.getMillisPerTick());
                }
            }
        }
        return millis;
    }
}