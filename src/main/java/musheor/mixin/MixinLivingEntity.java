package musheor.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import musheor.modules.automation.KekBounce;
import musheor.modules.features.HorseFly;
import musheor.modules.features.NoMoreJumpDelay;
import musheor.utils.HighwayBuilderLogic;
import musheor.utils.manager.ElytraManager;
import musheor.utils.manager.RotationManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LivingEntity.class})
public abstract class MixinLivingEntity extends Entity {
    @Shadow
    private int jumpingCooldown;
    @Unique
    private boolean musheor$travelSpoof;
    @Unique
    private float musheor$travelYaw;
    @Unique
    private float musheor$travelPitch;
    @Unique
    private boolean musheor$jumpSpoof;
    @Unique
    private float musheor$jumpYaw;
    @Unique
    private float musheor$jumpPitch;

    public MixinLivingEntity(EntityType<?> type, World world) {
        super(type, world);
    }

    @Shadow
    public abstract Brain<?> getBrain();

    @Unique
    private boolean musheor$isLocalPlayer() {
        return HighwayBuilderLogic.mc.player != null && (Object)this == HighwayBuilderLogic.mc.player;
    }

    @Inject(
        method = {"travel"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void musheor$horseFlyTravel(Vec3d movementInput, CallbackInfo ci) {
        HorseFly horseFly = Modules.get() == null ? null : Modules.get().get(HorseFly.class);
        if (horseFly != null && horseFly.handleTravel((LivingEntity)(Object)this)) {
            ci.cancel();
        }
    }

    @Inject(
        method = {"travel"},
        at = {@At("HEAD")}
    )
    private void musheor$travelHead(Vec3d movementInput, CallbackInfo ci) {
        this.musheor$travelSpoof = false;
        if (this.musheor$isLocalPlayer() && RotationManager.isActive()) {
            this.musheor$travelYaw = this.getYaw();
            this.musheor$travelPitch = this.getPitch();
            this.setYaw(RotationManager.yawOr(this.musheor$travelYaw));
            this.setPitch(RotationManager.pitchOr(this.musheor$travelPitch));
            this.musheor$travelSpoof = true;
        }
    }

    @Inject(
        method = {"travel"},
        at = {@At("RETURN")}
    )
    private void musheor$travelTail(Vec3d movementInput, CallbackInfo ci) {
        if (this.musheor$travelSpoof) {
            this.setYaw(this.musheor$travelYaw);
            this.setPitch(this.musheor$travelPitch);
            this.musheor$travelSpoof = false;
        }
    }

    @Inject(
        method = {"jump"},
        at = {@At("HEAD")}
    )
    private void musheor$jumpHead(CallbackInfo ci) {
        this.musheor$jumpSpoof = false;
        if (this.musheor$isLocalPlayer() && RotationManager.isActive()) {
            this.musheor$jumpYaw = this.getYaw();
            this.musheor$jumpPitch = this.getPitch();
            this.setYaw(RotationManager.yawOr(this.musheor$jumpYaw));
            this.setPitch(RotationManager.pitchOr(this.musheor$jumpPitch));
            this.musheor$jumpSpoof = true;
        }
    }

    @Inject(
        method = {"jump"},
        at = {@At("RETURN")}
    )
    private void musheor$jumpTail(CallbackInfo ci) {
        if (this.musheor$jumpSpoof) {
            this.setYaw(this.musheor$jumpYaw);
            this.setPitch(this.musheor$jumpPitch);
            this.musheor$jumpSpoof = false;
        }
    }

    @Inject(
        at = {@At("HEAD")},
        method = {"tickMovement"}
    )
    private void tickMovement(CallbackInfo ci) {
        NoMoreJumpDelay njd = Modules.get().get(NoMoreJumpDelay.class);
        KekBounce bounce = Modules.get().get(KekBounce.class);
        if (HighwayBuilderLogic.mc.player != null
            && HighwayBuilderLogic.mc.player.getBrain().equals(this.getBrain())
            && (njd != null && njd.isActive() || ElytraManager.isSessionActive() || bounce != null && bounce.isBouncing())) {
            this.jumpingCooldown = 0;
        }
    }
}
