package loggamja.ticksync.mixin;

import loggamja.ticksync.TickSyncSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.resource.language.LanguageManager;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin extends Screen {
    protected GameMenuScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void ticksync$addCustomButton(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        LanguageManager lm = client.getLanguageManager();

        String buttonName = "TickSync Setting";
        if (Objects.equals(lm.getLanguage(), "ko_kr")) {
            buttonName = "틱 동기화 설정";
        }
        this.addDrawableChild(
                ButtonWidget.builder(Text.literal(buttonName), button -> {
                            assert this.client != null;
                            this.client.setScreen(new TickSyncSetting());
                        })
                        .position(10, 10)
                        .size(100, 20)
                        .build()
        );
    }
}
