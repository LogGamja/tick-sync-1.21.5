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
import java.util.stream.Collectors;

public class TickSyncMain implements ModInitializer {
    public static final String MOD_ID = "tick-sync";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    boolean isTickRateChangedLastTick;
    boolean isPacketReceivedThisTick;

    long lastSyncTime = System.currentTimeMillis();
    long lastServerPacketTime;

    public int avgPacketDelay;
    public int packetDeviation;
    List<Integer> packetDelayBuffer = new ArrayList<>();
    List<Integer> packetDeviationBuffer = new ArrayList<>();

    float tickRatioConst; // (20 / serverTPS)
    public float clientTPS = 20;
    public float serverTPS = 20;

    public final int samplingRange = 55;
    public float[] packetDelayHistogram = new float[samplingRange];

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

        //MinecraftClient client = MinecraftClient.getInstance();
        //boolean useLastPacket = cfg.useLastPacket && client.getCurrentFps() >= 95;
        // 나중에 마지막 걸로 바꿔야 합니다
        if (!isPacketReceivedThisTick) {
            long now = System.currentTimeMillis();

            int delta = (int)(now - lastServerPacketTime);
            float tickRatioConst = (20 / serverTPS);

            if (0 < delta && delta < samplingRange * tickRatioConst) {
                int deviation = (int)(delta - (tickRatioConst * 50));
                addToPacketDeviationBuffer(deviation);
            }
            lastServerPacketTime = now;
        }
        isPacketReceivedThisTick = true;
    }
    public void onClientTickStart() {
        tickRatioConst = 20 / serverTPS;

        if (isPlayingInGame()) {
            long now = System.currentTimeMillis();
            int packetDelay = (int)(now - lastServerPacketTime);


            if (0 < packetDelay && packetDelay < samplingRange * tickRatioConst) {
                addToTickDeltaBuffer(packetDelay);
            }
            else {
                addToTickDeltaBuffer(avgPacketDelay);
            }

            // for debug histogram
            if (0 < packetDelay && packetDelay < samplingRange) {
                packetDelayHistogram[packetDelay]++;
            }
            avgPacketDelay = getAvgPacketDelay();
            packetDeviation = getPacketDeviation();
            if (packetDeviation > 16) {
                packetDeviationBuffer.clear();
                addToPacketDeviationBuffer(0);
            }
        }
        isPacketReceivedThisTick = false;

        // for debug histogram
        for(int i = 0; i < samplingRange; i++) {
            packetDelayHistogram[i] *= 0.95f;
        }
    }
    public boolean isPlayingInGame() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.world != null && client.player != null && !client.isPaused() && client.getCurrentFps() >= 40;
    }
    void addToTickDeltaBuffer(int newDelta) {
        packetDelayBuffer.add(newDelta);

        int tickBufferSize = cfg.tickSyncMargin;
        while (packetDelayBuffer.size() > tickBufferSize) {
            packetDelayBuffer.removeFirst();
        }
    }
    void addToPacketDeviationBuffer(int newDelta) {
        int tickBufferSize = 100;
        packetDeviationBuffer.add(newDelta);

        while (packetDeviationBuffer.size() > tickBufferSize) {
            packetDeviationBuffer.removeFirst();
        }
    }
    int getAvgPacketDelay() {
        int avgPacketDelay = 0;

        int size = packetDelayBuffer.size();
        if (size > 0) {
            int sum = 0;
            for (int v : packetDelayBuffer) {
                sum += v;
            }
            avgPacketDelay = sum / size;
        }
        return avgPacketDelay;
    }
    int getPacketDeviation() {
        if (packetDeviationBuffer.isEmpty()) return 10;
        System.out.println(packetDeviationBuffer);
        int med = (int)simpleMedian(packetDeviationBuffer);

        List<Integer> deviations = packetDeviationBuffer.stream()
                .map(v -> Math.abs(v - med))
                .collect(Collectors.toList());

        return (int)simpleMedian(deviations) * 4;
    }
    float simpleMedian(List<Integer> values) {
        // 원본을 건드리지 않고 복사본 생성
        List<Integer> sorted = new ArrayList<>(values);
        Collections.sort(sorted);

        int n = sorted.size();
        if (n % 2 == 1) {
            // 홀수 개일 때 중앙값
            return sorted.get(n / 2);
        } else {
            // 짝수 개일 때 중앙 두 값의 평균
            return (float)(sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2;
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
    public long quantizeToFrame(float margin) {
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