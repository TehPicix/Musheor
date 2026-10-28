package musheor.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import meteordevelopment.meteorclient.systems.modules.Modules;
import musheor.modules.features.CoordHider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;
import xaero.map.gui.GuiMap;

@Mixin(
    value = {GuiMap.class},
    remap = false
)
public abstract class MixinXaeroGuiMapCoords {
    @ModifyExpressionValue(
        method = {"render", "method_25394"},
        at = {@At(
            value = "INVOKE",
            target = "Lxaero/lib/client/config/ClientConfigManager;getEffective(Lxaero/lib/common/config/option/ConfigOption;)Ljava/lang/Object;",
            ordinal = 0,
            remap = false
        )},
        slice = {@Slice(
            from = @At(
                value = "FIELD",
                target = "Lxaero/map/common/config/option/WorldMapProfiledConfigOptions;COORDINATES:Lxaero/lib/common/config/option/BooleanConfigOption;",
                remap = false
            )
        )}
    )
    private Object musheor$hideCursorCoords(Object original) {
        CoordHider ch = Modules.get().get(CoordHider.class);
        return ch != null && ch.isActive() ? Boolean.FALSE : original;
    }
}
