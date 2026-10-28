package musheor.mixin;

import java.util.List;
import meteordevelopment.meteorclient.systems.modules.Modules;
import musheor.modules.features.CoordHider;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({DebugHud.class})
public class DebugHudMixin {
    @Inject(
        method = {"drawText"},
        at = {@At("HEAD")}
    )
    private void filterCoords(DrawContext context, List<String> text, boolean left, CallbackInfo ci) {
        CoordHider ch = Modules.get().get(CoordHider.class);
        if (ch != null && ch.isActive()) {
            if (left) {
                text.removeIf(line -> line != null && (line.startsWith("XYZ:") || line.startsWith("Block:") || line.startsWith("Chunk:")));
            } else {
                text.removeIf(line -> line != null && (line.contains("Targeted Block") || line.contains("Targeted Fluid")));
            }
        }
    }
}
