package loggamja.sync;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class TickSyncSetting extends Screen {
    private final Screen parent;

    // ESC 메뉴가 아닌 다른 곳(API 등)에서도 열릴 수 있으므로, 닫을 때 돌아갈 화면을 직접 기억한다
    public TickSyncSetting(Screen parent) {
        super(Component.translatable("ticksync.setting.title"));
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

            Button toggleButton = Button.builder(
                            Component.translatable(def.labelKeys()[current]),
                            button -> {
                                int next = (def.getter().getAsInt() + 1) % def.stateCount();
                                def.setter().accept(next);
                                button.setMessage(Component.translatable(def.labelKeys()[next]));
                                TickSyncConfig.INSTANCE.save();
                            })
                    .pos(startX, startY + i * spacing)
                    .size(buttonWidth, buttonHeight)
                    .tooltip(Tooltip.create(Component.translatable(def.tooltipKey())))
                    .build();

            this.addRenderableWidget(toggleButton);
        }

        this.addRenderableWidget(
                Button.builder(Component.translatable("ticksync.setting.ok"), button -> this.onClose())
                        .pos(startX, startY + toggles.length * spacing)
                        .size(buttonWidth, buttonHeight)
                        .build()
        );
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.drawCenteredString(this.font, Component.translatable("ticksync.setting.title"), this.width / 2, 20, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        assert this.minecraft != null;
        this.minecraft.setScreen(parent);
        TickSyncConfig.INSTANCE.save();
    }
}
