package musheor.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import musheor.modules.features.MoreTags;
import musheor.utils.HighwayBuilderLogic;
import musheor.utils.manager.ElytraManager;
import musheor.utils.manager.RotationManager;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({PlayerEntityRenderer.class})
public class MixinPlayerEntityRenderer {
    @Inject(
        method = {"updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V"},
        at = {@At("TAIL")}
    )
    private void applyServerRotation(PlayerLikeEntity player, PlayerEntityRenderState state, float tickDelta, CallbackInfo ci) {
        if (player == HighwayBuilderLogic.mc.player) {
            if (RotationManager.isActive()) {
                Float yaw = RotationManager.getPendingYaw();
                Float pitch = RotationManager.getPendingPitch();
                if (yaw != null) {
                    state.bodyYaw = yaw;
                }

                if (pitch != null) {
                    state.pitch = pitch;
                }

                state.relativeHeadYaw = 0.0F;
                state.flyingRotation = 0.0F;
            }

            if (ElytraManager.isGlideActive() && !player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
                state.pose = EntityPose.STANDING;
                state.isGliding = false;
                state.isSwimming = false;
                state.leaningPitch = 0.0F;
                state.flyingRotation = 0.0F;
                state.glidingTicks = 0.0F;
                state.applyFlyingRotation = false;
            }
        }
    }

    @Inject(
        method = {"renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void cancelDefaultNametag(
        PlayerEntityRenderState state, MatrixStack matrixStack, OrderedRenderCommandQueue queue, CameraRenderState cameraRenderState, CallbackInfo ci
    ) {
        MoreTags module = Modules.get().get(MoreTags.class);
        if (module != null && module.isActive() && module.enabled.get()) {
            ci.cancel();
        }
    }
}
