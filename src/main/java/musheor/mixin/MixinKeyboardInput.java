package musheor.mixin;

import musheor.utils.manager.RotationManager;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({KeyboardInput.class})
public abstract class MixinKeyboardInput extends Input {
    @Inject(
        method = {"tick"},
        at = {@At("TAIL")}
    )
    private void musheor$rewriteInput(CallbackInfo ci) {
        Vec2f rewritten = RotationManager.rewriteInput(this.getMovementInput());
        if (rewritten != null) {
            float length = (float)Math.sqrt(rewritten.x * rewritten.x + rewritten.y * rewritten.y);
            this.movementVector = length > 1.0F ? new Vec2f(rewritten.x / length, rewritten.y / length) : rewritten;
            this.playerInput = new PlayerInput(
                rewritten.y > 0.0F,
                rewritten.y < 0.0F,
                rewritten.x > 0.0F,
                rewritten.x < 0.0F,
                this.playerInput.jump(),
                this.playerInput.sneak(),
                this.playerInput.sprint()
            );
        }
    }
}
