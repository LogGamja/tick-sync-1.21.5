package loggamja.sync;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

// 토글 옵션의 번역 키 + config 필드 getter/setter 정의 테이블
// TickSyncSetting(화면)과 api.TickSyncAPI(외부 모드 연동) 양쪽이 이 테이블 하나만 보고 동작하므로
// 옵션을 추가 / 변경할 때 이 파일 한 곳만 수정하면 된다
public final class TickSyncOptionTable {
    private TickSyncOptionTable() {}

    public record ToggleDef(String id, String[] labelKeys, String tooltipKey, IntSupplier getter, IntConsumer setter) {
        public int stateCount() { return labelKeys.length; }
    }

    public static final ToggleDef[] TOGGLES = {
            new ToggleDef(
                    "tick_sync",
                    new String[]{"ticksync.option.tick_sync.off", "ticksync.option.tick_sync.on"},
                    "ticksync.tooltip.tick_sync",
                    () -> TickSyncConfig.INSTANCE.isTickSyncOn ? 1 : 0,
                    v -> TickSyncConfig.INSTANCE.isTickSyncOn = v != 0
            ),
            new ToggleDef(
                    "auto_margin",
                    new String[]{"ticksync.option.auto_margin.fixed", "ticksync.option.auto_margin.auto"},
                    "ticksync.tooltip.auto_margin",
                    () -> TickSyncConfig.INSTANCE.useAutoMargin ? 1 : 0,
                    v -> TickSyncConfig.INSTANCE.useAutoMargin = v != 0
            ),
            new ToggleDef(
                    "netty_criteria",
                    new String[]{"ticksync.option.netty_criteria.render", "ticksync.option.netty_criteria.netty"},
                    "ticksync.tooltip.netty_criteria",
                    () -> TickSyncConfig.INSTANCE.useNettyCriteria ? 1 : 0,
                    v -> TickSyncConfig.INSTANCE.useNettyCriteria = v != 0
            ),
            new ToggleDef(
                    "debug_screen",
                    new String[]{"ticksync.option.debug_screen.off", "ticksync.option.debug_screen.on"},
                    "ticksync.tooltip.debug_screen",
                    () -> TickSyncConfig.INSTANCE.useDebugScreen ? 1 : 0,
                    v -> TickSyncConfig.INSTANCE.useDebugScreen = v != 0
            ),
    };

    public static ToggleDef findToggle(String id) {
        for (ToggleDef def : TOGGLES) {
            if (def.id().equals(id)) return def;
        }
        return null;
    }
}
