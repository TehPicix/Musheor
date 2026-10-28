package musheor.mixin.meteor;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.render.BetterTab;
import musheor.accessor.BetterTabAccessor;
import musheor.utils.system.MusheorSystem;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
    value = {BetterTab.class},
    remap = false
)
public abstract class BetterTabMixin implements BetterTabAccessor {
    @Final
    @Shadow
    private SettingGroup sgGeneral;
    @Unique
    private Setting<MusheorSystem.TabListMode> musheor$displayMode;
    @Unique
    private Setting<Double> musheor$scale;

    @Inject(
        method = {"<init>"},
        at = {@At("TAIL")}
    )
    private void onInit(CallbackInfo ci) {
        this.musheor$displayMode = this.sgGeneral
            .add(
                new Builder<MusheorSystem.TabListMode>().name("display-mode").description("Display mode for tablist entries.")
                        .defaultValue(MusheorSystem.TabListMode.All)
                    .build()
            );
        this.musheor$scale = this.sgGeneral
            .add(
                new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                    .name("scale")
                    .description("Size of the tab list, relative to the current GUI scale.")
                    .defaultValue(1.0)
                    .min(0.1)
                    .sliderRange(0.25, 2.0)
                    .build()
            );
    }

    @Override
    public Setting<MusheorSystem.TabListMode> musheor$getDisplayMode() {
        return this.musheor$displayMode;
    }

    @Override
    public Setting<Double> musheor$getScale() {
        return this.musheor$scale;
    }
}
