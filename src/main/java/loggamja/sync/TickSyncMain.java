package loggamja.sync;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
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

public class TickSyncMain implements ClientModInitializer {
    public static final String MOD_ID = "ticksync";
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
    static final int TICK_BUFFER_SIZE = 10;
    static final int RANGE_BUFFER_SIZE = 40;
    static final int FAST_RANGE_BUFFER_SIZE = 10;

    static final int OUTLIER_LIMIT = 8;
    static final int SYNC_THRESHOLD_OFFSET = 4;

    public static final int SAMPLING_RANGE = 55;

    List<Integer> packetDelayBuffer = new ArrayList<>(Collections.nCopies(1, 12));
    List<Integer> packetRangeBuffer = new ArrayList<>(Collections.nCopies(1, 0));
    List<Integer> fastPacketRangeBuffer = new ArrayList<>(Collections.nCopies(1, 0));

    public static TickSyncMain INSTANCE;

    @Override
    public void onInitializeClient() {
        INSTANCE = this;

        ClientTickEvents.START_CLIENT_TICK.register(client -> this.onClientTickStart());
        ClientTickEvents.END_CLIENT_TICK.register(client -> this.onClientTickEnd());

        // 퇴장 시 틱 레이트 초기화
        ClientPlayConnectionEvents.DISCONNECT.register((client, handler) -> {
            setTickRate(20);
            serverTPS = 20;
            clientTPS = 20;
        });
        // 접속 완료 후 초기화
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            serverTPS = 20;
            clientTPS = 20;
            setTickRate(20);

            // 직전 세션 상태가 새 서버로 새어 들어오는 것 방지
            isTickRateChangedLastTick = false;
            afterLazyPacketCooldown = 0;
            lastServerPacketTime = System.currentTimeMillis();
            lastSyncTime = System.currentTimeMillis();
            packetDelayBuffer.clear();
            packetRangeBuffer.clear();
            fastPacketRangeBuffer.clear();
        });

        TickSyncConfig.INSTANCE.load();
    }
    public void onEntityPacket() {
        final long now = System.currentTimeMillis();

        if (MinecraftClient.getInstance().isOnThread()) {
            // 기본값: Render 스레드의 시간 사용
            if (!TickSyncConfig.INSTANCE.useNettyCriteria) {
                lastServerPacketTime = now;
                isPacketReceivedThisTick = true;
            }
        }
        else {
            // 실험적 옵션 활성화: Netty의 시간 사용
            if (TickSyncConfig.INSTANCE.useNettyCriteria) {
                lastServerPacketTime = now;
                isPacketReceivedThisTick = true;
            }

            // Netty 에서 온 패킷을 기준으로 네트워크 안정성 판단
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
    }
    int getTickDuration() { return (int)(1000 / serverTPS); }
    float applyRatio(float x) { return x * (20 / serverTPS); }

    boolean isPlayingInGame() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.world != null && client.player != null && !client.isPaused();
    }
    public boolean canSync() {
        if (TickSyncConfig.INSTANCE.useAutoMargin) return serverTPS <= 20 && getCurrentFPS() > 40 && instantPacketRange < applyRatio(25);
        else                   return serverTPS <= 20 && getCurrentFPS() > 40;
    }
    int getCurrentFPS() {
        return MinecraftClient.getInstance().getCurrentFps();
    }
    public void onClientTickStart() {
        if (isPlayingInGame()) {
            final long now = System.currentTimeMillis();
            final int packetDelay = (int)(now - lastServerPacketTime);

            if (0 < packetDelay && packetDelay < applyRatio(SAMPLING_RANGE)) {
                addToTickDeltaBuffer(packetDelay);
            }
            else {
                addToTickDeltaBuffer(avgPacketDelay);
            }

            if (afterLazyPacketCooldown > 0) afterLazyPacketCooldown--;

            avgPacketDelay    = calculateMean(packetDelayBuffer);
            packetRange       = calculateRange(packetRangeBuffer, RANGE_BUFFER_SIZE);
            instantPacketRange = calculateRange(fastPacketRangeBuffer, FAST_RANGE_BUFFER_SIZE);

            TickSyncHUDManager.INSTANCE.addToDebugHistogram(packetDelay);
            TickSyncHUDManager.INSTANCE.updateHUD(packetRange, avgPacketDelay, canSync());
        }
        isPacketRangeUpdatedThisTick = false;
        isPacketReceivedThisTick = false;
    }
    void addToTickDeltaBuffer(int newDelta) {
        packetDelayBuffer.add(newDelta);

        if (packetDelayBuffer.size() > TICK_BUFFER_SIZE) {
            packetDelayBuffer.removeFirst();
        }
    }
    void addToPacketRangeBuffer(int newDelta) {
        if (Math.abs(newDelta) <= OUTLIER_LIMIT) packetRangeBuffer.add(newDelta);
        if (packetRangeBuffer.size() > RANGE_BUFFER_SIZE) {
            packetRangeBuffer.removeFirst();
        }

        fastPacketRangeBuffer.add(newDelta);
        if (fastPacketRangeBuffer.size() > FAST_RANGE_BUFFER_SIZE) {
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
        if (list == null || list.isEmpty()) return 12;
        if (list.size() < requireBufferSize) return 12;

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;

        List<Integer> snapshot = new ArrayList<>(list);

        for (Integer i : snapshot) {
            if (i == null) continue;
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

        if (TickSyncConfig.INSTANCE.isTickSyncOn && isPlayingInGame() && canSync() && (now - lastSyncTime) > 1000) {
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

        final int range = calculateRange(packetDelayBuffer, TICK_BUFFER_SIZE);
        return (packetDelayBuffer.size() >= TICK_BUFFER_SIZE) && range > (applyRatio(35));
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
        final int max = OUTLIER_LIMIT * 2;
        final int min = afterLazyPacketCooldown > 0 ? 8 : 6;
        return TickSyncConfig.INSTANCE.useAutoMargin ? Math.clamp(packetRange, min, max) : 10;
    }
    boolean isTickSyncRequired() {
        return getThreshold() < avgPacketDelay;
    }
    int getThreshold() {
        final int threshold = (int)applyRatio(getPacketMargin()) + SYNC_THRESHOLD_OFFSET;

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
        if (TickSyncConfig.INSTANCE.useAutoMargin) return (int)(Math.floor(margin / getFrameDuration()) * getFrameDuration());
        else                   return (int)(Math.round(margin / getFrameDuration()) * getFrameDuration());
    }
    int getMatchSyncOffset() {
        if (!TickSyncConfig.INSTANCE.useAutoMargin) return 0;

        if (getCurrentFPS() > 240) return 0;
        if (getCurrentFPS() > 120) return 1;
        return 2;
    }
    void shiftNextTickDuration(int term) {
        // 0/음수 나눗셈 방지: 다음 틱 길이(ms)에 최소값을 보장 (최대 200TPS)
        final int MIN_TICK_DURATION = 5;
        final int nextTickDuration = Math.max(MIN_TICK_DURATION, term + getTickDuration());
        final float tickRate = 1000f / nextTickDuration;

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
            LOGGER.error("Failed to parse version string: {}", versionString, e);
            return false;
        }
    }
}
