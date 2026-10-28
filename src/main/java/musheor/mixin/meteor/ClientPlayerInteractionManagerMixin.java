package musheor.mixin.meteor;

import musheor.modules.features.DepthInteract;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ClientPlayerInteractionManager.class})
public class ClientPlayerInteractionManagerMixin {
    @Unique
    private static boolean depthInteract$spoofing;

    @Inject(
        method = {"interactBlock"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void depthInteract$swapHit(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
        DepthInteract mod = DepthInteract.INSTANCE;
        if (mod != null && mod.isActive()) {
            if (!depthInteract$spoofing) {
                if (mod.targetContainerPos != null) {
                    BlockPos pos = mod.targetContainerPos;
                    BlockHitResult newHit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
                    depthInteract$spoofing = true;
                    ClientPlayerInteractionManager self = (ClientPlayerInteractionManager)(Object)this;
                    cir.setReturnValue(self.interactBlock(player, hand, newHit));
                    depthInteract$spoofing = false;
                }
            }
        }
    }
}
