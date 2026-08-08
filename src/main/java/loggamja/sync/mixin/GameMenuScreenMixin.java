package loggamja.sync.mixin;

import loggamja.sync.TickSyncSetting;
import loggamja.sync.api.TickSyncAPI;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.gui.screens.PauseScreen;

@Mixin(PauseScreen.class)
public abstract class GameMenuScreenMixin extends Screen {
    protected GameMenuScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void ticksync$addCustomButton(CallbackInfo ci) {
        if (TickSyncAPI.isMenuButtonHidden()) return;

        this.addRenderableWidget(
                Button.builder(Component.translatable("ticksync.setting.menu_button"), button -> {
                            assert this.minecraft != null;
                            this.minecraft.setScreen(new TickSyncSetting(this));
                        })
                        .pos(10, 10)
                        .size(100, 20)
                        .build()
        );
    }
}
