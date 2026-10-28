package musheor.mixin;

import java.util.List;
import meteordevelopment.meteorclient.systems.modules.Modules;
import musheor.modules.features.CoordHider;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.hud.minimap.info.BuiltInInfoDisplays;
import xaero.hud.minimap.info.InfoDisplay;
import xaero.hud.minimap.info.render.compile.InfoDisplayCompiler;
import xaero.hud.minimap.module.MinimapSession;

@Mixin(
    value = {InfoDisplayCompiler.class},
    remap = false
)
public abstract class MixinXaeroInfoDisplayCompiler {
    @Shadow
    @Final
    private List<Text> compiledLines;

    @Inject(
        method = {"compile"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void musheor$hideCoords(InfoDisplay<?> infoDisplay, MinimapSession session, int size, BlockPos playerPos, CallbackInfoReturnable<List<Text>> cir) {
        if (infoDisplay == BuiltInInfoDisplays.COORDINATES
            || infoDisplay == BuiltInInfoDisplays.OVERWORLD_COORDINATES
            || infoDisplay == BuiltInInfoDisplays.CHUNK_COORDINATES) {
            CoordHider ch = Modules.get().get(CoordHider.class);
            if (ch != null && ch.isActive()) {
                this.compiledLines.clear();
                cir.setReturnValue(this.compiledLines);
            }
        }
    }
}
