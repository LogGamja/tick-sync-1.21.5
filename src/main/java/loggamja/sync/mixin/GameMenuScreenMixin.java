package loggamja.sync.mixin;

import loggamja.sync.TickSyncSetting;
import loggamja.sync.api.TickSyncAPI;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.gui.screen.GameMenuScreen;

@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin extends Screen {
    protected GameMenuScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void ticksync$addCustomButton(CallbackInfo ci) {
        if (TickSyncAPI.isMenuButtonHidden()) return;

        this.addDrawableChild(
                ButtonWidget.builder(Text.translatable("ticksync.setting.menu_button"), button -> {
                            assert this.client != null;
                            this.client.setScreen(new TickSyncSetting(this));
                        })
                        .position(10, 10)
                        .size(100, 20)
                        .build()
        );
    }
}
