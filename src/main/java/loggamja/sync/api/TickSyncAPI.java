package loggamja.sync.api;

import loggamja.sync.TickSyncConfig;
import loggamja.sync.TickSyncOptionTable;
import loggamja.sync.TickSyncSetting;
import net.minecraft.client.MinecraftClient;

import java.util.OptionalInt;

// 다른 모드가 TickSync를 제어하기 위한 공개 API
public final class TickSyncAPI {
    private TickSyncAPI() {}

    private static volatile boolean menuButtonHidden = false;

    // ESC 메뉴의 설정 버튼을 숨긴다 (래치형 — 해제하기 전까지 계속 숨겨진 상태 유지)
    public static void setMenuButtonHidden(boolean hidden) {
        menuButtonHidden = hidden;
    }

    public static boolean isMenuButtonHidden() {
        return menuButtonHidden;
    }

    // id로 지정한 토글 옵션 값을 강제로 바꾼다. 존재하지 않는 id면 false 반환
    public static boolean setToggleOption(String id, int value) {
        TickSyncOptionTable.ToggleDef def = TickSyncOptionTable.findToggle(id);
        if (def == null) return false;

        int clamped = Math.max(0, Math.min(value, def.stateCount() - 1));
        def.setter().accept(clamped);
        TickSyncConfig.INSTANCE.save();
        return true;
    }

    // id로 지정한 토글 옵션의 현재 값을 읽는다. 존재하지 않는 id면 빈 값 반환
    public static OptionalInt getToggleOption(String id) {
        TickSyncOptionTable.ToggleDef def = TickSyncOptionTable.findToggle(id);
        return def == null ? OptionalInt.empty() : OptionalInt.of(def.getter().getAsInt());
    }

    // 현재 화면 위에 TickSync 설정 화면을 연다 (ESC 메뉴를 거치지 않아도 됨)
    public static void openSettings() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.setScreen(new TickSyncSetting(client.currentScreen));
    }
}
