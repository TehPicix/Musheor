package musheor.mixin.meteor;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import musheor.modules.features.AntiCheat;
import musheor.utils.HighwayUtils;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
    value = {BlockUtils.class},
    remap = false
)
public class MixinBlockUtilsBypass {
    @Inject(
        method = {"place(Lnet/minecraft/util/math/BlockPos;Lmeteordevelopment/meteorclient/utils/player/FindItemResult;ZIZZ)Z"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private static void onPlace(
        BlockPos blockPos,
        FindItemResult findItemResult,
        boolean rotate,
        int rotationPriority,
        boolean swingHand,
        boolean checkEntities,
        CallbackInfoReturnable<Boolean> cir
    ) {
        AntiCheat antiCheat = Modules.get().get(AntiCheat.class);
        if (antiCheat.airPlaceBypass.get()) {
            boolean result = HighwayUtils.placeBlockOffhand(blockPos, Direction.DOWN);
            cir.setReturnValue(result);
            cir.cancel();
        }
    }
}
