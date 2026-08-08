package loggamja.sync.api;

import loggamja.sync.TickSyncConfig;
import loggamja.sync.TickSyncOptionTable;
import loggamja.sync.TickSyncSetting;
import net.minecraft.client.Minecraft;

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
    // 실제 쓰기/저장은 클라이언트 메인 스레드로 마샬링해 게임 루프와의 경합을 방지한다
    public static boolean setToggleOption(String id, int value) {
        TickSyncOptionTable.ToggleDef def = TickSyncOptionTable.findToggle(id);
        if (def == null) return false;

        int clamped = Math.max(0, Math.min(value, def.stateCount() - 1));
        Minecraft.getInstance().execute(() -> {
            def.setter().accept(clamped);
            TickSyncConfig.INSTANCE.save();
        });
        return true;
    }

    // id로 지정한 토글 옵션의 현재 값을 읽는다. 존재하지 않는 id면 빈 값 반환
    public static OptionalInt getToggleOption(String id) {
        TickSyncOptionTable.ToggleDef def = TickSyncOptionTable.findToggle(id);
        return def == null ? OptionalInt.empty() : OptionalInt.of(def.getter().getAsInt());
    }

    // 모든 옵션을 기본값으로 되돌린다. 실제 쓰기/저장은 클라이언트 메인 스레드로 마샬링한다
    public static void resetToDefaults() {
        Minecraft.getInstance().execute(() -> {
            TickSyncConfig.INSTANCE = new TickSyncConfig();
            TickSyncConfig.INSTANCE.save();
        });
    }

    // 현재 화면 위에 TickSync 설정 화면을 연다 (ESC 메뉴를 거치지 않아도 됨)
    // setScreen은 메인 스레드 전용이므로 마샬링한다. currentScreen은 실행 시점(메인 스레드)에 읽어 정확한 화면을 parent로 삼는다
    public static void openSettings() {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> client.setScreen(new TickSyncSetting(client.screen)));
    }
}
