package loggamja.sync.mixin;

import loggamja.sync.TickSyncMain;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.tick.TickManager;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Shadow @Nullable public ClientWorld world;
    @Shadow @Nullable public ClientPlayerEntity player;

    @Shadow private final RenderTickCounter.Dynamic renderTickCounter = new RenderTickCounter.Dynamic(20.0f, 0L, this::getTargetMillisPerTick);
    @Unique
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