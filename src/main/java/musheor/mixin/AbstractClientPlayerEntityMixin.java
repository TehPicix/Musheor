package musheor.mixin;

import musheor.utils.system.MutualManager;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo.TextureAsset;
import net.minecraft.util.AssetInfo.TextureAssetInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({AbstractClientPlayerEntity.class})
public abstract class AbstractClientPlayerEntityMixin {
    @Inject(
        method = {"getSkin"},
        at = {@At("RETURN")},
        cancellable = true
    )
    private void onGetSkinTextures(CallbackInfoReturnable<SkinTextures> cir) {
        AbstractClientPlayerEntity player = (AbstractClientPlayerEntity)(Object)this;
        MutualManager m = MutualManager.getInstance();
        if (m.hasCape(player)) {
            SkinTextures original = (SkinTextures)cir.getReturnValue();
            TextureAsset capeAsset = new TextureAssetInfo(MutualManager.PLUS_CAPE, MutualManager.PLUS_CAPE);
            SkinTextures modified = new SkinTextures(original.body(), capeAsset, original.elytra(), original.model(), original.secure());
            cir.setReturnValue(modified);
        }
    }
}
