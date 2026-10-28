package musheor.mixin;

import com.mojang.authlib.GameProfile;
import musheor.utils.manager.RotationManager;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ClientPlayerEntity.class})
public abstract class MixinClientPlayerEntity extends AbstractClientPlayerEntity {
    @Unique
    private boolean musheor$spoofed;
    @Unique
    private float musheor$yaw;
    @Unique
    private float musheor$pitch;
    @Unique
    private int musheor$sinceResync;
    @Unique
    private static final int RESYNC_TICKS = 20;
    @Unique
    private static final float RESYNC_NUDGE = 0.001F;

    public MixinClientPlayerEntity(ClientWorld world, GameProfile profile) {
        super(world, profile);
    }

    @Inject(
        method = {"sendMovementPackets"},
        at = {@At("HEAD")}
    )
    private void musheor$spoofPacketRotation(CallbackInfo ci) {
        this.musheor$spoofed = false;
        if (!RotationManager.isActive()) {
            this.musheor$sinceResync = 0;
        } else {
            this.musheor$yaw = this.getYaw();
            this.musheor$pitch = this.getPitch();
            float yaw = RotationManager.yawOr(this.musheor$yaw);
            float pitch = RotationManager.pitchOr(this.musheor$pitch);
            if (++this.musheor$sinceResync >= 20) {
                this.musheor$sinceResync = 0;
                yaw += 0.001F;
            }

            this.setYaw(yaw);
            this.setPitch(pitch);
            this.musheor$spoofed = true;
        }
    }

    @Inject(
        method = {"sendMovementPackets"},
        at = {@At("RETURN")}
    )
    private void musheor$restorePacketRotation(CallbackInfo ci) {
        if (this.musheor$spoofed) {
            this.setYaw(this.musheor$yaw);
            this.setPitch(this.musheor$pitch);
            this.musheor$spoofed = false;
        }
    }
}
