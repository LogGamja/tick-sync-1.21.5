package loggamja.sync;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.Packet;
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
    boolean isPacketReceivedThisTick;

    public int packetRange;
    public int avgPacketDelay;

    long lastSyncTime = System.currentTimeMillis();
    long lastServerPacketTime;

    public float clientTPS = 20;
    public float serverTPS = 20;

    // 상수 정의
    final int tickBufferSize = 10;
    final int rangeBufferSize = 100;
    final int outlierLimit = 8;
    public final int samplingRange = 55;

    List<Integer> packetDelayBuffer = new ArrayList<>();
    List<Integer> packetRangeBuffer = new ArrayList<>();
    public float[] packetDelayHistogram = new float[samplingRange];

    static TickSyncConfig cfg;
    public static final TickSyncMain INSTANCE = new TickSyncMain();

    int getTickDuration() { return (int)(1000 / serverTPS); }
    float applyRatio(float x) { return x * (20 / serverTPS); }

    @Override
    public void onInitialize() {
        ClientTickEvents.START_CLIENT_TICK.register(client -> INSTANCE.onClientTickStart());
        ClientTickEvents.END_CLIENT_TICK.register(client -> INSTANCE.onClientTickEnd());

        TickSyncConfig.INSTANCE.load();
        cfg = TickSyncConfig.INSTANCE;
    }
    public void onEntityPacket() {

        //MinecraftClient client = MinecraftClient.getInstance();
        //boolean useLastPacket = cfg.useLastPacket && client.getCurrentFps() >= 95;
        // 나중에 마지막 걸로 바꿔야 합니다
        if (!isPacketReceivedThisTick) {
            long now = System.currentTimeMillis();
            int delta = (int)(now - lastServerPacketTime);

            if (0 < delta && delta < applyRatio(samplingRange)) {
                int deviation = delta - (int)(applyRatio(1) * 50);
                addToPacketRangeBuffer(deviation);
            }
            lastServerPacketTime = now;
        }
        isPacketReceivedThisTick = true;
    }
    public void onClientTickStart() {

        if (isPlayingInGame()) {
            long now = System.currentTimeMillis();
            int packetDelay = (int)(now - lastServerPacketTime);

            if (0 < packetDelay && packetDelay < applyRatio(samplingRange)) {
                addToTickDeltaBuffer(packetDelay);
            }
            else {
                addToTickDeltaBuffer(avgPacketDelay);
            }

            // for debug histogram
            if (0 < packetDelay && packetDelay < samplingRange) {
                packetDelayHistogram[packetDelay]++;
            }
            // for debug histogram
            for (int i = 0; i < samplingRange; i++) {
                packetDelayHistogram[i] *= 0.95f;
            }
            avgPacketDelay = getAvgPacketDelay();
            packetRange = getPacketRange();
        }
        isPacketReceivedThisTick = false;
    }
    public boolean isPlayingInGame() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.world != null && client.player != null && !client.isPaused() && client.getCurrentFps() >= 40;
    }
    void addToTickDeltaBuffer(int newDelta) {
        packetDelayBuffer.add(newDelta);

        if (packetDelayBuffer.size() > tickBufferSize) {
            packetDelayBuffer.removeFirst();
        }
    }
    void addToPacketRangeBuffer(int newDelta) {
        packetRangeBuffer.add(newDelta);

        if (packetRangeBuffer.size() > rangeBufferSize) {
            if (Math.abs(newDelta) > outlierLimit) {
                packetRangeBuffer.removeLast();
            }
            else {
                packetRangeBuffer.removeFirst();
            }
        }
    }
    int getAvgPacketDelay() {
        int size = packetDelayBuffer.size();
        if (size == 0) return 10;

        int sum = 0;
        for (int v : packetDelayBuffer) {
            sum += v;
        }
        return sum / size;
    }
    int getPacketRange() {
        int size = packetRangeBuffer.size();
        if (size < 50) return 14;

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;

        for (int i : packetRangeBuffer) {
            if (i < min) min = i;
            if (i > max) max = i;
        }
        return Math.clamp(max - min, 6, 20);
    }
    void onClientTickEnd() {
        if (isTickRateChangedLastTick) {
            isTickRateChangedLastTick = false;
            setTickRate(Math.min(20, serverTPS));
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

        return (packetDelayBuffer.size() >= tickBufferSize) && (max - min) > (applyRatio(35)) && serverTPS <= 40;
    }
    void fixWeirdSync() {
        if (packetDelayBuffer.isEmpty()) return;

        lastSyncTime = System.currentTimeMillis();

        float min = Math.min(cfg.tickSyncMargin / 2, Collections.min(packetDelayBuffer));
        int tickToPush = quantizeToFrame(applyRatio((cfg.tickSyncMargin - min)));
        shiftNextTickDuration(tickToPush);
    }
    boolean isTickSyncRequired() {
        return getThreshold() < avgPacketDelay && serverTPS <= 40;
    }
    int getThreshold() {
        MinecraftClient client = MinecraftClient.getInstance();
        float frameDuration = 1000f / client.getCurrentFps();
        int threshold = (int)(applyRatio(cfg.tickSyncMargin)) + 4;

        if (threshold < frameDuration) {
            return (int)(frameDuration * 1.5f);
        }
        else {
            return threshold;
        }
    }
    void matchTickSync() {
        lastSyncTime = System.currentTimeMillis();
        int tickToPush = (getTickDuration() - avgPacketDelay) + quantizeToFrame(applyRatio(cfg.tickSyncMargin));

        if (tickToPush > getTickDuration() / 2)
            tickToPush -= getTickDuration(); // pull tick

        shiftNextTickDuration(tickToPush);
    }
    // util
    int quantizeToFrame(float margin) {
        MinecraftClient client = MinecraftClient.getInstance();
        float frameDuration = 1000f / client.getCurrentFps();

        if (margin < frameDuration) return (int)frameDuration;
        return (int)(Math.round(margin / frameDuration) * frameDuration);
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
    public boolean isMinecraftAtLeast(String versionString) {
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