package musheor.mixin.meteor;

import java.util.Set;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura.EntityAge;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura.ShieldMode;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MagmaCubeEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
    value = {KillAura.class},
    remap = false
)
public abstract class KillAuraMixin extends Module {
    @Shadow
    @Final
    private Setting<ShieldMode> shieldMode;
    @Shadow
    @Final
    private Setting<Set<EntityType<?>>> entities;
    @Shadow
    @Final
    private Setting<Double> range;
    @Shadow
    @Final
    private Setting<Double> wallsRange;
    @Shadow
    @Final
    private Setting<Boolean> ignoreNamed;
    @Shadow
    @Final
    private Setting<Boolean> ignorePassive;
    @Shadow
    @Final
    private Setting<Boolean> ignoreTamed;

    public KillAuraMixin(Category category, String name, String description) {
        super(category, name, description);
    }

    @Unique
    private EntityAge ageFilter(String... names) {
        for (String name : names) {
            Setting<?> setting = this.settings.get(name);
            if (setting != null && setting.get() instanceof EntityAge age) {
                return age;
            }
        }

        return EntityAge.Both;
    }

    @Unique
    private EntityAge passiveAgeFilter() {
        return this.ageFilter("passive-mob-age-filter", "mob-age-filter");
    }

    @Unique
    private EntityAge hostileAgeFilter() {
        return this.ageFilter("hostile-mob-age-filter", "mob-age-filter");
    }

    @Inject(
        method = {"entityCheck"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void onEntityCheck(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!entity.equals(this.mc.player) && !entity.equals(this.mc.getCameraEntity())) {
            if (!(entity instanceof LivingEntity livingEntity && livingEntity.isDead()) && entity.isAlive()) {
                Box hitbox = entity.getBoundingBox();
                if (!PlayerUtils.isWithin(
                    MathHelper.clamp(this.mc.player.getX(), hitbox.minX, hitbox.maxX),
                    MathHelper.clamp(this.mc.player.getY(), hitbox.minY, hitbox.maxY),
                    MathHelper.clamp(this.mc.player.getZ(), hitbox.minZ, hitbox.maxZ),
                    this.range.get()
                )) {
                    cir.setReturnValue(false);
                } else if (!this.entities.get().contains(entity.getType())) {
                    cir.setReturnValue(false);
                } else if (this.ignoreNamed.get() && entity.hasCustomName()) {
                    cir.setReturnValue(false);
                } else if (!PlayerUtils.canSeeEntity(entity) && !PlayerUtils.isWithin(entity, this.wallsRange.get())) {
                    cir.setReturnValue(false);
                } else if (this.ignoreTamed.get()
                    && entity instanceof Tameable tameable
                    && tameable.getOwner() != null
                    && tameable.getOwner().equals(this.mc.player)) {
                    cir.setReturnValue(false);
                } else {
                    if (this.ignorePassive.get()) {
                        if (entity instanceof MobEntity mob && !mob.isAttacking() && !(mob instanceof MagmaCubeEntity)) {
                            cir.setReturnValue(false);
                            return;
                        }

                        if (entity instanceof HostileEntity hostile && !hostile.isAttacking()) {
                            cir.setReturnValue(false);
                            return;
                        }
                    }

                    if (entity instanceof PlayerEntity player) {
                        if (player.isCreative()) {
                            cir.setReturnValue(false);
                            return;
                        }

                        if (!Friends.get().shouldAttack(player)) {
                            cir.setReturnValue(false);
                            return;
                        }

                        if (this.shieldMode.get() == ShieldMode.Ignore && player.isBlocking()) {
                            cir.setReturnValue(false);
                            return;
                        }
                    }

                    if (entity instanceof PassiveEntity passive) {
                        cir.setReturnValue(switch (this.passiveAgeFilter()) {
                            case Baby -> passive.isBaby();
                            case Adult -> !passive.isBaby();
                            case Both -> true;
                        });
                    } else {
                        cir.setReturnValue(true);
                    }
                }
            } else {
                cir.setReturnValue(false);
            }
        } else {
            cir.setReturnValue(false);
        }
    }
}
