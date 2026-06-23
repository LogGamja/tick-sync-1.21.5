package loggamja.ticksync;

public class TickSyncHUDManager {
    public boolean canSync = true;

    public int packetRange;
    public int avgPacketDelay;
    public int syncTextAlpha = 20;

    public final int samplingRange = 55;

    public float[] packetDelayHistogram = new float[samplingRange];
    // 싱글톤
    public static final TickSyncHUDManager INSTANCE = new TickSyncHUDManager();

    public void updateHUD() {
        packetRange = TickSyncMain.INSTANCE.packetRange;
        avgPacketDelay = TickSyncMain.INSTANCE.avgPacketDelay;

        canSync = TickSyncMain.INSTANCE.canSync();
        fadeSyncText();
        fadeDebugHistogram();
    }

    void fadeSyncText() {
        if (syncTextAlpha > 0) syncTextAlpha--;
    }

    void fadeDebugHistogram() {
        for (int i = 0; i < samplingRange; i++) {
            packetDelayHistogram[i] *= 0.95f;
        }
    }

    public void addToDebugHistogram(int packetDelay) {
        if (0 < packetDelay && packetDelay < samplingRange) {
            packetDelayHistogram[packetDelay]++;
        }
    }
}
