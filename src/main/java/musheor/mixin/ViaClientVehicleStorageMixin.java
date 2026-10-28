package musheor.mixin;

import musheor.modules.features.HorseFly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(
    targets = {"com/viaversion/viaversion/protocols/v1_21to1_21_2/storage/ClientVehicleStorage"},
    remap = false
)
public class ViaClientVehicleStorageMixin {
    @Inject(
        method = {"forwardMovement", "sidewaysMovement"},
        at = {@At("HEAD")},
        cancellable = true,
        require = 0
    )
    private void musheor$horseFlyNanSteer(CallbackInfoReturnable<Float> cir) {
        if (HorseFly.shouldSendNan()) {
            cir.setReturnValue(Float.NaN);
        }
    }
}
