package loggamja.sync;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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
    boolean isPacketRangeUpdatedThisTick;

    public int packetRange;
    public int avgPacketDelay;

    long lastSyncTime = System.currentTimeMillis();
    long lastServerPacketTime = System.currentTimeMillis();
    long lastPacketRangeUpdatedTime = System.currentTimeMillis();

    public float clientTPS = 20;
    public float serverTPS = 20;

    // 상수 정의
    final int tickBufferSize = 10;
    final int rangeBufferSize = 100;
    final int outlierLimit = 8;
    final int thresholdOffset = 4;
    final int matchSyncOffset = 0;
    public final int samplingRange = 55;

    List<Integer> packetDelayBuffer = new ArrayList<>();
    List<Integer> packetRangeBuffer = new ArrayList<>();
    public float[] packetDelayHistogram = new float[samplingRange];

    static TickSyncConfig cfg;
    public static final TickSyncMain INSTANCE = new TickSyncMain();

    int getPacketMargin() { return cfg.useAutoMargin ? Math.clamp(packetRange, 8, 20) : 12; }
    int getTickDuration() { return (int)(1000 / serverTPS); }
    float getFrameDuration() { return 1000f / MinecraftClient.getInstance().getCurrentFps(); }
    float applyRatio(float x) { return x * (20 / serverTPS); }

    @Override
    public void onInitialize() {
        ClientTickEvents.START_CLIENT_TICK.register(client -> INSTANCE.onClientTickStart());
        ClientTickEvents.END_CLIENT_TICK.register(client -> INSTANCE.onClientTickEnd());

        TickSyncConfig.INSTANCE.load();
        cfg = TickSyncConfig.INSTANCE;
    }
    public void onEntityPacket() {
        final long now = System.currentTimeMillis();
        lastServerPacketTime = now;

        // Range 버퍼 업데이트
        if (!isPacketRangeUpdatedThisTick) {
            final int delta = (int)(now - lastPacketRangeUpdatedTime);

            if (0 < delta && delta < applyRatio(samplingRange)) {
                final int deviation = delta - (int)(applyRatio(1) * 50);
                addToPacketRangeBuffer(deviation);
            }
            lastPacketRangeUpdatedTime = now;
            isPacketRangeUpdatedThisTick = true;
        }
    }
    public boolean isPlayingInGame() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.world != null && client.player != null && !client.isPaused() && serverTPS <= 20 && client.getCurrentFps() >= 40;
    }
    public void onClientTickStart() {
        if (isPlayingInGame()) {
            final long now = System.currentTimeMillis();
            final int packetDelay = (int)(now - lastServerPacketTime);

            if (0 < packetDelay && packetDelay < applyRatio(samplingRange)) {
                addToTickDeltaBuffer(packetDelay);
            }
            else {
                addToTickDeltaBuffer(avgPacketDelay);
            }
            updateDebugHistogram(packetDelay);
            avgPacketDelay = calculateAvgPacketDelay();
            packetRange = calculatePacketRange();
        }
        isPacketRangeUpdatedThisTick = false;
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
    int calculateAvgPacketDelay() {
        final int size = packetDelayBuffer.size();
        if (size == 0) return 10;

        int sum = 0;
        for (int v : packetDelayBuffer) {
            sum += v;
        }
        return sum / size;
    }
    int calculatePacketRange() {
        final int size = packetRangeBuffer.size();
        if (size < rangeBufferSize) return 12;

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;

        for (int i : packetRangeBuffer) {
            if (i < min) min = i;
            if (i > max) max = i;
        }
        return max - min;
    }
    void updateDebugHistogram(int packetDelay) {
        if (0 < packetDelay && packetDelay < samplingRange) {
            packetDelayHistogram[packetDelay]++;
        }
        for (int i = 0; i < samplingRange; i++) {
            packetDelayHistogram[i] *= 0.95f;
        }
    }
    void onClientTickEnd() {
        if (isTickRateChangedLastTick) {
            isTickRateChangedLastTick = false;
            setTickRate(Math.min(20, serverTPS));
        }
        final long now = System.currentTimeMillis();
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

        final int tickBufferSize = getPacketMargin();
        final float min = Collections.min(packetDelayBuffer);
        final float max = Collections.max(packetDelayBuffer);

        return (packetDelayBuffer.size() >= tickBufferSize) && (max - min) > (applyRatio(35));
    }
    void fixWeirdSync() {
        if (packetDelayBuffer.isEmpty()) return;

        lastSyncTime = System.currentTimeMillis();

        final float min = Math.min(getPacketMargin() / 2, Collections.min(packetDelayBuffer));
        final int tickToPush = quantizeToFrame(applyRatio((getPacketMargin() - min)));
        shiftNextTickDuration(tickToPush);
    }
    boolean isTickSyncRequired() {
        return getThreshold() < avgPacketDelay && serverTPS <= 40;
    }
    int getThreshold() {
        final int threshold = (int)applyRatio(getPacketMargin()) + thresholdOffset;

        if (threshold < getFrameDuration()) {
            return (int)(getFrameDuration() * 1.5f);
        }
        else {
            return threshold;
        }
    }
    void matchTickSync() {
        lastSyncTime = System.currentTimeMillis();
        int tickToPush = (getTickDuration() - avgPacketDelay) + quantizeToFrame(applyRatio(getPacketMargin() + matchSyncOffset));

        if (tickToPush > getTickDuration() / 2)
            tickToPush -= getTickDuration(); // pull tick

        shiftNextTickDuration(tickToPush);
    }
    int quantizeToFrame(float margin) {
        if (margin < getFrameDuration()) return (int)getFrameDuration();
        return (int)(Math.round(margin / getFrameDuration()) * getFrameDuration());
    }
    void shiftNextTickDuration(int term) {
        final float tickRate = 1000f / (term + getTickDuration());

        setTickRate(tickRate);

        packetDelayBuffer.clear();
        addToTickDeltaBuffer(getPacketMargin());

        avgPacketDelay = getPacketMargin();
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