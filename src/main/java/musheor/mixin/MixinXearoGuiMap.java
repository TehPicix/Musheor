package musheor.mixin;

import java.util.ArrayList;
import musheor.compat.XearoMapHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.gui.GuiMap;
import xaero.map.gui.IRightClickableElement;
import xaero.map.gui.MapTileSelection;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

@Mixin(
    value = {GuiMap.class},
    remap = false
)
public abstract class MixinXearoGuiMap implements IRightClickableElement {
    @Shadow
    private MapTileSelection mapTileSelection;

    @Inject(
        method = {"getRightClickOptions"},
        at = {@At("RETURN")}
    )
    private void musheor$addOptions(CallbackInfoReturnable<ArrayList<RightClickOption>> cir) {
        MapTileSelection sel = this.mapTileSelection;
        if (sel != null) {
            XearoMapHooks.apply((ArrayList<RightClickOption>)cir.getReturnValue(), this, sel);
        }
    }
}
