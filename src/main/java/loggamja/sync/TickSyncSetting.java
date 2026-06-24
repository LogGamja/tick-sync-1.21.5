package loggamja.sync;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.resource.language.LanguageManager;
import net.minecraft.text.Text;

import java.util.Objects;

public class TickSyncSetting extends Screen {

    static String[][] TOGGLE_OPTIONS = {
            { "TickSync: OFF", "TickSync: ON" },
            { "Margin: Fixed(10ms)", "Margin: Auto(default)" },
            { "Thread: Render", "Thread: Netty" },
            { "Debug: OFF", "Debug: ON" }
    };
    static String[] tooltips = {
            "Toggles all functions of TickSync.",
            "Select control method related with internet quality.",
            "Select 'Render' if you don't know well about this.",
            "Toggles debug screen at the corner."
    };

    private final int[] toggleIndices = new int[TOGGLE_OPTIONS.length];

    public TickSyncSetting() {
        super(Text.literal("Setting"));
    }
    void setTextLang() {
        MinecraftClient client = MinecraftClient.getInstance();
        LanguageManager lm = client.getLanguageManager();

        if (Objects.equals(lm.getLanguage(), "ko_kr")) {
            TOGGLE_OPTIONS = new String[][] {
                    { "틱 동기화: 꺼짐", "틱 동기화: 켜짐" },
                    { "정확도: 고정(10ms)", "정확도: 자동(기본)" },
                    { "기준 스레드: Render", "기준 스레드: Netty" },
                    { "디버그: 꺼짐", "디버그: 켜짐" }
            };
            tooltips = new String[] {
                    "모든 틱 동기화 기능을 켜거나 끕니다.",
                    "인터넷 품질과 관련된 제어 방법을 결정합니다.",
                    "잘 알지 못한다면 무조건 Render를 쓰세요!",
                    "우상단 디버그 화면을 켜거나 끕니다."
            };
        }
    }
    @Override
    protected void init() {
        super.init();

        int buttonWidth = 169;
        int buttonHeight = 20;
        int spacing = 25;

        int startX = this.width / 2 - buttonWidth / 2;
        int startY = this.height / 5;

        setTextLang();

        // Config load
        TickSyncConfig cfg = TickSyncConfig.INSTANCE;
        toggleIndices[0] = cfg.isTickSyncOn ? 1 : 0;
        toggleIndices[1] = cfg.useAutoMargin ? 1 : 0;
        toggleIndices[2] = cfg.useNettyCriteria ? 1 : 0;
        toggleIndices[3] = cfg.useDebugScreen ? 1 : 0;


        // Buttons
        for (int i = 0; i < TOGGLE_OPTIONS.length; i++) {
            final int index = i;

            ButtonWidget toggleButton = ButtonWidget.builder(
                            Text.literal(TOGGLE_OPTIONS[index][toggleIndices[index]]),
                            button -> {
                                toggleIndices[index] = (toggleIndices[index] + 1) % TOGGLE_OPTIONS[index].length;
                                button.setMessage(Text.literal(TOGGLE_OPTIONS[index][toggleIndices[index]]));
                                onButtonClick(index, toggleIndices[index]);

                                // Save config
                                TickSyncConfig.INSTANCE.save();
                            })
                    .position(startX, startY + i * spacing)
                    .size(buttonWidth, buttonHeight)
                    .tooltip(Tooltip.of(Text.literal(tooltips[i])))
                    .build();

            this.addDrawableChild(toggleButton);
        }

        // Exit button
        this.addDrawableChild(
                ButtonWidget.builder(Text.literal("OK"), button -> {
                            Objects.requireNonNull(this.client);
                            this.client.setScreen(new GameMenuScreen(true));
                            TickSyncConfig.INSTANCE.save();
                        })
                        .position(startX, startY + TOGGLE_OPTIONS.length * spacing)
                        .size(buttonWidth, buttonHeight)
                        .build()
        );
    }
    void onButtonClick(int button, int index) {
        TickSyncConfig cfg = TickSyncConfig.INSTANCE;
        if (button == 0) {
            cfg.isTickSyncOn = (index != 0);
        }
        else if (button == 1) {
            cfg.useAutoMargin = (index != 0);
        }
        else if (button == 2) {
            cfg.useNettyCriteria = (index != 0);
        }
        else if (button == 3) {
            cfg.useDebugScreen = (index != 0);
        }
    }
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        //this.renderBackground(context, mouseX, mouseY, delta); 2중 블러 배경 렌더링. 21.6+에서 버그 발생
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                "Setting",
                this.width / 2,
                20,
                0xFFFFFF
        );
        super.render(context, mouseX, mouseY, delta);
    }
}
