package loggamja.sync;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class TickSyncSetting extends Screen {
    private final Screen parent;

    // ESC 메뉴가 아닌 다른 곳(API 등)에서도 열릴 수 있으므로, 닫을 때 돌아갈 화면을 직접 기억한다
    public TickSyncSetting(Screen parent) {
        super(Text.translatable("ticksync.setting.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int buttonWidth = 169;
        int buttonHeight = 20;
        int spacing = 25;

        int startX = this.width / 2 - buttonWidth / 2;
        int startY = this.height / 5;

        var toggles = TickSyncOptionTable.TOGGLES;

        for (int i = 0; i < toggles.length; i++) {
            var def = toggles[i];
            int current = def.getter().getAsInt();

            ButtonWidget toggleButton = ButtonWidget.builder(
                            Text.translatable(def.labelKeys()[current]),
                            button -> {
                                int next = (def.getter().getAsInt() + 1) % def.stateCount();
                                def.setter().accept(next);
                                button.setMessage(Text.translatable(def.labelKeys()[next]));
                                TickSyncConfig.INSTANCE.save();
                            })
                    .position(startX, startY + i * spacing)
                    .size(buttonWidth, buttonHeight)
                    .tooltip(Tooltip.of(Text.translatable(def.tooltipKey())))
                    .build();

            this.addDrawableChild(toggleButton);
        }

        this.addDrawableChild(
                ButtonWidget.builder(Text.translatable("ticksync.setting.ok"), button -> this.close())
                        .position(startX, startY + toggles.length * spacing)
                        .size(buttonWidth, buttonHeight)
                        .build()
        );
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("ticksync.setting.title"), this.width / 2, 20, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        assert this.client != null;
        this.client.setScreen(parent);
        TickSyncConfig.INSTANCE.save();
    }
}
