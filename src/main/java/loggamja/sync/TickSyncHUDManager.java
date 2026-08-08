package loggamja.sync;

public class TickSyncHUDManager {
    public boolean canSync = true;

    public int packetRange;
    public int avgPacketDelay;
    public int syncTextAlpha = 20;

    public float[] packetDelayHistogram = new float[TickSyncMain.SAMPLING_RANGE];

    // 싱글톤
    public static final TickSyncHUDManager INSTANCE = new TickSyncHUDManager();

    public void updateHUD(int packetRange, int avgPacketDelay, boolean canSync) {
        this.packetRange    = packetRange;
        this.avgPacketDelay = avgPacketDelay;
        this.canSync        = canSync;

        fadeSyncText();
        fadeDebugHistogram();
    }
    void fadeSyncText() {
        if (syncTextAlpha > 0) syncTextAlpha--;
    }
    void fadeDebugHistogram() {
        for (int i = 0; i < TickSyncMain.SAMPLING_RANGE; i++) {
            packetDelayHistogram[i] *= 0.95f;
        }
    }
    public void addToDebugHistogram(int packetDelay) {
        if (0 < packetDelay && packetDelay < TickSyncMain.SAMPLING_RANGE) {
            packetDelayHistogram[packetDelay]++;
        }
    }
}
