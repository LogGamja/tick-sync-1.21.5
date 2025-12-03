package loggamja.sync;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
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
    boolean isPacketReceivedThisTick;
    boolean isPacketRangeUpdatedThisTick;

    int afterLazyPacketCooldown;
    public int packetRange;
    public int instantPacketRange;
    public int avgPacketDelay;

    long lastSyncTime = System.currentTimeMillis();
    long lastServerPacketTime = System.currentTimeMillis();
    long lastPacketRangeUpdatedTime = System.currentTimeMillis();

    public float clientTPS = 20;
    public float serverTPS = 20;

    // 상수 정의
    final int tickBufferSize = 10;
    final int rangeBufferSize = 40;
    final int fastRangeBufferSize = 10;

    final int outlierLimit = 8;
    final int syncThresholdOffset = 4;

    public final int samplingRange = 55;

    List<Integer> packetDelayBuffer = new ArrayList<>(Collections.nCopies(tickBufferSize, 12));
    List<Integer> packetRangeBuffer = new ArrayList<>(Collections.nCopies(rangeBufferSize, 0));
    List<Integer> fastPacketRangeBuffer = new ArrayList<>(Collections.nCopies(fastRangeBufferSize, 0));

    static TickSyncConfig cfg;
    public static final TickSyncMain INSTANCE = new TickSyncMain();

    @Override
    public void onInitialize() {
        ClientTickEvents.START_CLIENT_TICK.register(client -> INSTANCE.onClientTickStart());
        ClientTickEvents.END_CLIENT_TICK.register(client -> INSTANCE.onClientTickEnd());

        TickSyncConfig.INSTANCE.load();
        cfg = TickSyncConfig.INSTANCE;
    }
    public void onEntityPacket() {
        final long now = System.currentTimeMillis();

        // 패킷 수신 시간 업데이트
        if (cfg.useUnstableEnvOption && getCurrentFPS() < 95) {
            // 기준: 처음 패킷
            if (!isPacketReceivedThisTick) {
                lastServerPacketTime = now;
                isPacketReceivedThisTick = true;
            }
        }
        else {
            // 기준: 마지막 패킷
            lastServerPacketTime = now;
            isPacketReceivedThisTick = true;
        }

        // Range 버퍼 업데이트
        if (!isPacketRangeUpdatedThisTick) {
            final int delta = (int)(now - lastPacketRangeUpdatedTime);

            if (0 < delta) {
                final int halfDuration = (int)applyRatio(25);
                final int normalDeviation = (delta % getTickDuration() + halfDuration) % getTickDuration() - halfDuration;
                addToPacketRangeBuffer(normalDeviation);
            }
            // 패킷이 드물게 올 경우
            if (getTickDuration() * 2 <= delta) {
                afterLazyPacketCooldown = 20;
            }
            lastPacketRangeUpdatedTime = now;
            isPacketRangeUpdatedThisTick = true;
        }
    }
    int getTickDuration() { return (int)(1000 / serverTPS); }
    float applyRatio(float x) { return x * (20 / serverTPS); }

    boolean isPlayingInGame() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.world != null && client.player != null && !client.isPaused();
    }
    public boolean canSync() {
        if (cfg.useAutoMargin) return serverTPS <= 20 && getCurrentFPS() > 40 && instantPacketRange < applyRatio(25);
        else                   return serverTPS <= 20 && getCurrentFPS() > 40;
    }
    int getCurrentFPS() {
        return MinecraftClient.getInstance().getCurrentFps();
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

            if (afterLazyPacketCooldown > 0) afterLazyPacketCooldown--;
            TickSyncHUDManager.INSTANCE.addToDebugHistogram(packetDelay);
            TickSyncHUDManager.INSTANCE.updateHUD();

            avgPacketDelay = calculateMean(packetDelayBuffer);
            packetRange = calculateRange(packetRangeBuffer, rangeBufferSize);
            instantPacketRange = calculateRange(fastPacketRangeBuffer, fastRangeBufferSize);
        }
        isPacketRangeUpdatedThisTick = false;
        isPacketReceivedThisTick = false;
    }
    void addToTickDeltaBuffer(int newDelta) {
        packetDelayBuffer.add(newDelta);

        if (packetDelayBuffer.size() > tickBufferSize) {
            packetDelayBuffer.removeFirst();
        }
    }
    void addToPacketRangeBuffer(int newDelta) {
        if (Math.abs(newDelta) <= outlierLimit) packetRangeBuffer.add(newDelta);
        if (packetRangeBuffer.size() > rangeBufferSize) {
            packetRangeBuffer.removeFirst();
        }

        fastPacketRangeBuffer.add(newDelta);
        if (fastPacketRangeBuffer.size() > fastRangeBufferSize) {
            fastPacketRangeBuffer.removeFirst();
        }
    }
    int calculateMean(List<Integer> list) {
        final int size = list.size();
        if (size == 0) return 10;

        int sum = 0;
        for (int v : list) {
            sum += v;
        }
        return sum / size;
    }
    int calculateRange(List<Integer> list, int requireBufferSize) {
        final int size = list.size();
        if (size < requireBufferSize) return 12;

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;

        for (int i : list) {
            if (i < min) min = i;
            if (i > max) max = i;
        }

        return max - min;
    }
    void onClientTickEnd() {
        if (isTickRateChangedLastTick) {
            isTickRateChangedLastTick = false;
            setTickRate(Math.min(20, serverTPS));
        }
        final long now = System.currentTimeMillis();

        if (cfg.isTickSyncOn && isPlayingInGame() && canSync() && (now - lastSyncTime) > 1000) {
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

        final int range = calculateRange(packetDelayBuffer, tickBufferSize);
        return (packetDelayBuffer.size() >= tickBufferSize) && range > (applyRatio(35));
    }
    void fixWeirdSync() {
        if (packetDelayBuffer.isEmpty()) return;

        lastSyncTime = System.currentTimeMillis();

        final float min = Math.min(getPacketMargin() / 2, Collections.min(packetDelayBuffer));
        final int tickToPush = quantizeToFrame(applyRatio((getPacketMargin() - min)));
        shiftNextTickDuration(tickToPush);

        TickSyncHUDManager.INSTANCE.syncTextAlpha = 10;
    }
    int getPacketMargin() {
        final int max = outlierLimit * 2;
        final int min = afterLazyPacketCooldown > 0 ? 8 : 6;
        return cfg.useAutoMargin ? Math.clamp(packetRange, min, max) : 10;
    }
    boolean isTickSyncRequired() {
        return getThreshold() < avgPacketDelay;
    }
    int getThreshold() {
        final int threshold = (int)applyRatio(getPacketMargin()) + syncThresholdOffset;

        if (threshold < getFrameDuration()) {
            return (int)(getFrameDuration() * 1.5f);
        }
        else {
            return threshold;
        }
    }
    float getFrameDuration() { return 1000f / getCurrentFPS(); }
    void matchTickSync() {
        lastSyncTime = System.currentTimeMillis();
        int tickToPush = (getTickDuration() - avgPacketDelay) + quantizeToFrame(applyRatio(getPacketMargin() + getMatchSyncOffset()));

        if (tickToPush > getTickDuration() / 2)
            tickToPush -= getTickDuration(); // pull tick

        shiftNextTickDuration(tickToPush);
        TickSyncHUDManager.INSTANCE.syncTextAlpha = 20;
    }
    int quantizeToFrame(float margin) {
        if (margin < getFrameDuration()) return (int)getFrameDuration();
        if (cfg.useAutoMargin) return (int)(Math.floor(margin / getFrameDuration()) * getFrameDuration());
        else                   return (int)(Math.round(margin / getFrameDuration()) * getFrameDuration());
    }
    int getMatchSyncOffset() {
        if (!cfg.useAutoMargin) return 0;

        if (getCurrentFPS() > 240) return 0;
        if (getCurrentFPS() > 120) return 1;
        return 2;
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

        final MinecraftClient client = MinecraftClient.getInstance();
        final ClientWorld world = client.world;
        if (world != null) {
            TickManager tickManager = world.getTickManager();
            tickManager.setTickRate(tickRate);
        }
    }
    // -------------
    public boolean isMinecraftAtLeast(String versionString) {
        Optional<ModMetadata> mcMeta = FabricLoader.getInstance()
                .getModContainer("minecraft")
                .map(ModContainer::getMetadata);

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