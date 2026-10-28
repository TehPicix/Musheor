package musheor.mixin;

import musheor.compat.VersionHelper;
import musheor.utils.hud.CustomHudButton;
import musheor.utils.hud.DraggableButtonWidget;
import musheor.utils.system.MusheorSystem;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ChatScreen.class})
public abstract class ChatScreenMixin extends Screen {
    @Shadow
    protected TextFieldWidget chatField;

    protected ChatScreenMixin(Text title) {
        super(title);
    }

    @Inject(
        method = {"init"},
        at = {@At("TAIL")}
    )
    private void onInit(CallbackInfo info) {
        try {
            DraggableButtonWidget ircBtn = (DraggableButtonWidget)Class.forName("musheor.plus.PlusChatScreenInit")
                .getMethod("createChatButton", int.class)
                .invoke(null, this.height);
            if (ircBtn != null) {
                this.addDrawableChild(ircBtn);
            }
        } catch (Exception var4) {
        }

        for (CustomHudButton btn : MusheorSystem.get().customButtons) {
            if (btn.screenTarget.appliesTo(CustomHudButton.ScreenTarget.Chat)) {
                this.addDrawableChild(btn.createWidget(null));
            }
        }
    }

    @Inject(
        method = {"mouseClicked"},
        at = {@At("RETURN")}
    )
    private void onMouseClicked(Click click, boolean bl, CallbackInfoReturnable<Boolean> cir) {
        if (this.getFocused() instanceof DraggableButtonWidget && !VersionHelper.get().hasControlDown()) {
            this.setFocused(this.chatField);
        }
    }

    @Inject(
        method = {"keyPressed"},
        at = {@At("RETURN")}
    )
    private void onKeyPressed(KeyInput key, CallbackInfoReturnable<Boolean> cir) {
        if (this.getFocused() instanceof DraggableButtonWidget && !VersionHelper.get().hasControlDown()) {
            this.setFocused(this.chatField);
        }
    }
}
