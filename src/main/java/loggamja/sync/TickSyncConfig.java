package loggamja.sync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class TickSyncConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "tick-sync-config.json");

    public boolean isTickSyncOn = true;
    public int tickSyncMargin = 10;
    public boolean useDebugScreen = false;

    // 싱글톤
    public static final TickSyncConfig INSTANCE = new TickSyncConfig();

    public void load() {
        if (!CONFIG_FILE.exists()) return;
        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            TickSyncConfig loaded = GSON.fromJson(reader, TickSyncConfig.class);
            if (loaded != null) {
                this.isTickSyncOn = loaded.isTickSyncOn;
                this.tickSyncMargin = loaded.tickSyncMargin;
                this.useDebugScreen = loaded.useDebugScreen;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
