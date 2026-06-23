package loggamja.ticksync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class TickSyncConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(TickSyncMain.MOD_ID);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "ticksync-config.json");

    public boolean isTickSyncOn = true;
    public boolean useAutoMargin = true;
    public boolean useDebugScreen = false;

    // 싱글톤
    public static final TickSyncConfig INSTANCE = new TickSyncConfig();

    public void load() {
        if (!CONFIG_FILE.exists()) return;

        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            TickSyncConfig loaded = GSON.fromJson(reader, TickSyncConfig.class);
            if (loaded != null) {
                this.isTickSyncOn = loaded.isTickSyncOn;
                this.useAutoMargin = loaded.useAutoMargin;
                this.useDebugScreen = loaded.useDebugScreen;
            }
        } catch (IOException e) {
            LOGGER.error("Config 로드 실패", e);
        }
    }

    public void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            LOGGER.error("Config 저장 실패", e);
        }
    }
}
