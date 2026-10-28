package musheor.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import musheor.modules.features.MoreTags;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Entity.class})
public class MixinPearlOutline {
    @Inject(
        method = {"isGlowing"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void onIsGlowing(CallbackInfoReturnable<Boolean> cir) {
        Entity entity = (Entity)(Object)this;
        if (entity instanceof EnderPearlEntity pearl) {
            MoreTags moreTags = Modules.get().get(MoreTags.class);
            if (moreTags != null && moreTags.shouldOutlinePearl(pearl)) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(
        method = {"getTeamColorValue"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void onGetTeamColorValue(CallbackInfoReturnable<Integer> cir) {
        Entity entity = (Entity)(Object)this;
        if (entity instanceof EnderPearlEntity pearl) {
            MoreTags moreTags = Modules.get().get(MoreTags.class);
            if (moreTags != null && moreTags.shouldOutlinePearl(pearl)) {
                cir.setReturnValue(moreTags.getPearlOutlineColor(pearl));
            }
        }
    }
}
