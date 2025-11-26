package loggamja.sync;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.tick.TickManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class TickSyncMain implements ModInitializer {
    public static final String MOD_ID = "tick-sync";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    boolean isTickRateChangedLastTick;
    public static boolean isPacketReceivedThisTick;

    long lastSyncTime = System.currentTimeMillis();
    static long lastServerPacketTime;

    public static long avgPacketDelay;
    List<Long> packetDelayBuffer = new ArrayList<>();

    float tickRatioConst; // (20 / serverTPS)
    public static float clientTPS = 20;
    public static float serverTPS = 20;

    public static final int samplingRange = 55;
    public static float[] packetDelayHistogram = new float[samplingRange];

    static TickSyncConfig cfg;

    @Override
    public void onInitialize() {
        ClientTickEvents.START_CLIENT_TICK.register(client -> onClientTickStart());
        ClientTickEvents.END_CLIENT_TICK.register(client -> onClientTickEnd());

        TickSyncConfig.INSTANCE.load();
        cfg = TickSyncConfig.INSTANCE;
    }
    public static void onEntityPacket() {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean useLastPacket = cfg.useLastPacket && client.getCurrentFps() >= 95;

        if (useLastPacket || !isPacketReceivedThisTick)
            lastServerPacketTime = System.currentTimeMillis();

        isPacketReceivedThisTick = true;
    }
    public void onClientTickStart() {
        tickRatioConst = 20 / serverTPS;

        if (isPlayingInGame()) {
            long now = System.currentTimeMillis();
            long packetDelay = now - lastServerPacketTime;

            if (0 < packetDelay && packetDelay < samplingRange * tickRatioConst)
                addToTickDeltaBuffer(packetDelay);
            else
                addToTickDeltaBuffer(avgPacketDelay);

            // for debug histogram
            if (0 < packetDelay && packetDelay < samplingRange) {
                packetDelayHistogram[(int)packetDelay]++;
            }
            avgPacketDelay = (long)packetDelayBuffer.stream().mapToLong(Long::longValue).average().orElse(0);
        }
        isPacketReceivedThisTick = false;

        // for debug histogram
        for(int i = 0; i < samplingRange; i++) {
            packetDelayHistogram[i] *= 0.95f;
        }
    }
    public static boolean isPlayingInGame() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.world != null && client.player != null && !client.isPaused() && client.getCurrentFps() >= 40;
    }
    void addToTickDeltaBuffer(long newDelta) {
        packetDelayBuffer.add(newDelta);

        int tickBufferSize = cfg.tickSyncMargin;
        while (packetDelayBuffer.size() > tickBufferSize) {
            packetDelayBuffer.removeFirst();
        }
    }
    void onClientTickEnd() {
        if (isTickRateChangedLastTick) {
            isTickRateChangedLastTick = false;
            setTickRate(Math.min(serverTPS, 20));
        }

        long now = System.currentTimeMillis();
        if (cfg.isTickSyncOn && isPlayingInGame() && (now - lastSyncTime) > 1000) {
            if (isWeirdSyncOccurred()) {
                fixWeirdSync();
            }
            if (isTickSyncRequired()) {
                matchTickSync();
            }
        }
    }
    boolean isWeirdSyncOccurred() {
        if (packetDelayBuffer.isEmpty()) return false;

        int tickBufferSize = cfg.tickSyncMargin;
        float min = Collections.min(packetDelayBuffer);
        float max = Collections.max(packetDelayBuffer);

        return (packetDelayBuffer.size() >= tickBufferSize) && (max - min) > (35 * tickRatioConst) && serverTPS <= 40;
    }
    void fixWeirdSync() {
        if (packetDelayBuffer.isEmpty()) return;

        lastSyncTime = System.currentTimeMillis();

        float min = Math.min(cfg.tickSyncMargin / 2, Collections.min(packetDelayBuffer));
        long tickToPush = quantizeToFrame((cfg.tickSyncMargin - min) * tickRatioConst);
        shiftNextTickDuration(tickToPush);
    }
    boolean isTickSyncRequired() {
        return getThreshold() < avgPacketDelay && serverTPS <= 40;
    }
    long getThreshold() {
        MinecraftClient client = MinecraftClient.getInstance();
        float frameDuration = 1000f / client.getCurrentFps();
        long threshold = (long)(cfg.tickSyncMargin * tickRatioConst) + 4;

        if (threshold < frameDuration) {
            return (long)(frameDuration * 1.5f);
        }
        else {
            return threshold;
        }
    }
    void matchTickSync() {
        lastSyncTime = System.currentTimeMillis();

        int tickDuration = (int)(1000 / serverTPS);
        long tickToPush = (tickDuration - avgPacketDelay) + quantizeToFrame(cfg.tickSyncMargin * tickRatioConst);

        if (tickToPush > tickDuration / 2)
            tickToPush -= tickDuration; // pull tick

        shiftNextTickDuration(tickToPush);
    }
    // util
    public static long quantizeToFrame(float margin) {
        MinecraftClient client = MinecraftClient.getInstance();
        float frameDuration = 1000f / client.getCurrentFps();

        if (margin < frameDuration) return (long)frameDuration;
        return (long)(Math.round(margin / frameDuration) * frameDuration);
    }
    void shiftNextTickDuration(long term) {
        long tickDuration = (long)(1000 / serverTPS);
        float tickRate = 1000 / (float)(term + tickDuration);

        setTickRate(tickRate);

        packetDelayBuffer.clear();
        addToTickDeltaBuffer(cfg.tickSyncMargin);
        avgPacketDelay = cfg.tickSyncMargin;
        isTickRateChangedLastTick = true;
    }
    void setTickRate(float tickRate) {
        clientTPS = tickRate;

        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        if (world != null) {
            TickManager tickManager = world.getTickManager();
            tickManager.setTickRate(tickRate);
        }
    }
    // -------------
    public static boolean isMinecraftAtLeast(String versionString) {
        Optional<ModMetadata> mcMeta = FabricLoader.getInstance()
                .getModContainer("minecraft")
                .map(container -> container.getMetadata());

        if (mcMeta.isEmpty()) return false;

        Version current = mcMeta.get().getVersion();
        try {
            Version target = Version.parse(versionString);

            // Fabric API의 Version 클래스는 비교 가능
            return current.compareTo(target) >= 0;
        } catch (VersionParsingException e) {
            return false;
        }

    }
}