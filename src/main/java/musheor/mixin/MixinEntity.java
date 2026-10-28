package musheor.mixin;

import java.util.UUID;
import meteordevelopment.meteorclient.systems.modules.Modules;
import musheor.modules.automation.KekBounce;
import musheor.utils.HighwayBuilderLogic;
import musheor.utils.manager.ElytraManager;
import musheor.utils.manager.RotationManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Entity.class})
public class MixinEntity {
    @Shadow
    protected UUID uuid;

    @Inject(
        at = {@At("RETURN")},
        method = {"getRotationVector()Lnet/minecraft/util/math/Vec3d;"},
        cancellable = true
    )
    private void getRotationVector(CallbackInfoReturnable<Vec3d> cir) {
        if (HighwayBuilderLogic.mc.player != null && this.uuid == HighwayBuilderLogic.mc.player.getUuid() && RotationManager.isActive()) {
            float yaw = RotationManager.yawOr(HighwayBuilderLogic.mc.player.getYaw());
            float pitch = RotationManager.pitchOr(HighwayBuilderLogic.mc.player.getPitch());
            float f = pitch * (float) (Math.PI / 180.0);
            float g = -yaw * (float) (Math.PI / 180.0);
            float cosG = MathHelper.cos(g);
            float sinG = MathHelper.sin(g);
            float cosF = MathHelper.cos(f);
            float sinF = MathHelper.sin(f);
            cir.setReturnValue(new Vec3d(sinG * cosF, -sinF, cosG * cosF));
        }
    }

    @Redirect(
        method = {"updateVelocity"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/Entity;getYaw()F"
        )
    )
    private float musheor$travelYaw(Entity self) {
        float yaw = self.getYaw();
        return HighwayBuilderLogic.mc.player != null && this.uuid == HighwayBuilderLogic.mc.player.getUuid() && RotationManager.isInputManaged()
            ? RotationManager.yawOr(yaw)
            : yaw;
    }

    @Inject(
        at = {@At("HEAD")},
        method = {"getPose"},
        cancellable = true
    )
    private void getPose(CallbackInfoReturnable<EntityPose> cir) {
        KekBounce bounce = Modules.get().get(KekBounce.class);
        if (HighwayBuilderLogic.mc.player != null && this.uuid == HighwayBuilderLogic.mc.player.getUuid()) {
            if (ElytraManager.isGlideActive()) {
                boolean wearingElytra = HighwayBuilderLogic.mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
                cir.setReturnValue(wearingElytra ? EntityPose.GLIDING : EntityPose.STANDING);
            } else if (bounce != null && bounce.isBouncing()) {
                cir.setReturnValue(EntityPose.GLIDING);
            }
        }
    }
}
