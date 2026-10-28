package musheor.mixin.meteor;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.player.AutoGap;
import musheor.utils.HighwayBuilderLogic;
import net.minecraft.entity.effect.StatusEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
    value = {AutoGap.class},
    remap = false
)
public abstract class MixinAutoGap {
    @Shadow
    private SettingGroup sgPotions;
    @Unique
    private Setting<Boolean> burnEat;

    @Inject(
        method = {"<init>"},
        at = {@At("TAIL")}
    )
    private void onInit(CallbackInfo ci) {
        this.burnEat = this.sgPotions
            .add(new Builder().name("burning").description("Eat if you are burning and don't have Fire Resistance.").defaultValue(false).build());
    }

    @Inject(
        method = {"shouldEatPotions"},
        at = {@At("RETURN")},
        cancellable = true
    )
    private void onShouldEatPotions(CallbackInfoReturnable<Boolean> cir) {
        if (!(Boolean)cir.getReturnValue()) {
            if (this.burnEat.get()) {
                if (HighwayBuilderLogic.mc.player != null
                    && HighwayBuilderLogic.mc.player.isOnFire()
                    && !HighwayBuilderLogic.mc.player.hasStatusEffect(StatusEffects.FIRE_RESISTANCE)) {
                    cir.setReturnValue(true);
                }
            }
        }
    }
}
