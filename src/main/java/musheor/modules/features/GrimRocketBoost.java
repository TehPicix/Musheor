package musheor.modules.features;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.DoubleSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import musheor.musheor;
import musheor.utils.manager.ElytraManager;
import musheor.utils.manager.RotationManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.util.math.Vec3d;

public class GrimRocketBoost extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<Double> boostSpeed = this.sgGeneral
        .add(
            new Builder()
                .name("boost-speed")
                .description("Target speed in blocks/tick to fly at while boosting.")
                .defaultValue(1.65)
                .sliderRange(0.5, 2.5)
                .decimalPlaces(2)
                .build()
        );
    private int tickCounter;
    private float lastYaw;
    private float lastPitch;
    private Float appliedYaw;
    private Float appliedPitch;

    public GrimRocketBoost() {
        super(
            musheor.MAIN,
            "grim-rocket-boost",
            "While a rocket is active, aims velocity along your flight direction at 'boost-speed' then rescales it up to a per-axis box, so flying diagonally (e.g. north + up) boosts every axis at once and total speed passes the single-axis cap. Jitters yaw ±0.01°/tick so GrimAC credits the boost."
        );
    }

    @Override
    public void onActivate() {
        this.tickCounter = 0;
        this.appliedYaw = null;
        this.appliedPitch = null;
        if (this.mc.player != null) {
            this.lastYaw = this.mc.player.getYaw();
            this.lastPitch = this.mc.player.getPitch();
        }
    }

    @Override
    public void onDeactivate() {
        this.releaseRotation();
    }

    @EventHandler(
        priority = -200
    )
    private void onTick(Pre event) {
        if (this.mc.player != null && this.mc.world != null) {
            this.tickCounter++;
            this.releaseRotation();
            if (this.mc.player.isGliding()) {
                if (this.isRocketActive()) {
                    if (!RotationManager.outranksMovement()) {
                        Float pendingYaw = RotationManager.getPendingYaw();
                        Float pendingPitch = RotationManager.getPendingPitch();
                        float flightYaw = pendingYaw != null ? pendingYaw : this.mc.player.getYaw();
                        float flightPitch = pendingPitch != null ? pendingPitch : this.mc.player.getPitch();
                        Vec3d look = this.mc.player.getRotationVector(flightPitch, flightYaw);
                        this.mc.player.setVelocity(this.scaleVelocityToBox(look.multiply(this.boostSpeed.get()), look));
                        float yaw = flightYaw + (this.tickCounter % 2 == 0 ? 0.01F : -0.01F);
                        if (RotationManager.setYaw(yaw, 0) && RotationManager.setPitch(flightPitch, 0)) {
                            this.appliedYaw = yaw;
                            this.appliedPitch = flightPitch;
                        }

                        this.lastYaw = flightYaw;
                        this.lastPitch = flightPitch;
                    }
                }
            }
        }
    }

    private void releaseRotation() {
        if (this.appliedYaw != null) {
            if (this.appliedYaw.equals(RotationManager.getPendingYaw()) && this.appliedPitch.equals(RotationManager.getPendingPitch())) {
                RotationManager.clear();
            }

            this.appliedYaw = null;
            this.appliedPitch = null;
        }
    }

    private boolean isRocketActive() {
        for (Entity entity : this.mc.world.getEntities()) {
            if (entity instanceof FireworkRocketEntity rocket && rocket.getOwner() == this.mc.player) {
                return true;
            }
        }

        return ElytraManager.isRocketBoosting();
    }

    private Vec3d scaleVelocityToBox(Vec3d motion, Vec3d look) {
        if (motion.lengthSquared() < 1.0E-6) {
            return motion;
        } else if (this.mc.player.isTouchingWater()) {
            return motion;
        } else {
            Vec3d currentLook = look.normalize();
            Vec3d lastLook = this.mc.player.getRotationVector(this.lastPitch, this.lastYaw).normalize();
            double a = 0.05;
            double minX = Math.min(-a, currentLook.x) + Math.min(-a, lastLook.x);
            double minY = Math.min(-a, currentLook.y) + Math.min(-a, lastLook.y);
            double minZ = Math.min(-a, currentLook.z) + Math.min(-a, lastLook.z);
            double maxX = Math.max(a, currentLook.x) + Math.max(a, lastLook.x);
            double maxY = Math.max(a, currentLook.y) + Math.max(a, lastLook.y);
            double maxZ = Math.max(a, currentLook.z) + Math.max(a, lastLook.z);
            double threshold = Math.min(this.boostSpeed.get(), motion.length());
            minX = Math.max(-threshold, minX * threshold);
            minY = Math.max(-threshold, minY * threshold);
            minZ = Math.max(-threshold, minZ * threshold);
            maxX = Math.min(threshold, maxX * threshold);
            maxY = Math.min(threshold, maxY * threshold);
            maxZ = Math.min(threshold, maxZ * threshold);
            double gravity = this.mc.player.hasNoGravity() ? 0.0 : (this.mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING) ? 0.01 : 0.08);
            Vec3d used = motion.add(0.0, -gravity, 0.0);
            double scaleX = Math.abs(used.x) < 1.0E-6 ? 0.0 : (used.x < 0.0 ? used.x / minX : used.x / maxX);
            double scaleY = Math.abs(used.y) < 1.0E-6 ? 0.0 : (used.y < 0.0 ? (-used.y - gravity + 0.02) / -minY : (used.y + gravity + 0.02) / maxY);
            double scaleZ = Math.abs(used.z) < 1.0E-6 ? 0.0 : (used.z < 0.0 ? used.z / minZ : used.z / maxZ);
            double maxScale = Math.max(scaleX, Math.max(scaleY, scaleZ));
            return !(maxScale < 1.0E-6) && !(maxScale > 1.0) ? motion.multiply(1.0 / maxScale) : motion;
        }
    }
}
