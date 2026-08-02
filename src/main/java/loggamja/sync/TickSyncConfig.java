package loggamja.sync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.InstanceCreator;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class TickSyncConfig {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(TickSyncConfig.class, (InstanceCreator<TickSyncConfig>) type -> new TickSyncConfig())
            .create();
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "ticksync-config.json");

    public boolean isTickSyncOn = true;
    public boolean useAutoMargin = true;
    public boolean useDebugScreen = false;

    public boolean useNettyCriteria = false;
    public boolean useFastSync = false;

    // 싱글톤 (load()가 역직렬화된 인스턴스로 통째로 교체하므로 final이 아님)
    public static TickSyncConfig INSTANCE = new TickSyncConfig();

    public void load() {
        if (!CONFIG_FILE.exists()) return;

        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            TickSyncConfig loaded = GSON.fromJson(reader, TickSyncConfig.class);
            if (loaded != null) {
                INSTANCE = loaded;
            }
        } catch (IOException e) {
            TickSyncMain.LOGGER.error("Failed to load config", e);
        } catch (JsonParseException e) {
            TickSyncMain.LOGGER.error("Config file is malformed, falling back to defaults", e);
            INSTANCE = new TickSyncConfig();
        }
    }
    public void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            TickSyncMain.LOGGER.error("Failed to save config", e);
        }
    }
}
