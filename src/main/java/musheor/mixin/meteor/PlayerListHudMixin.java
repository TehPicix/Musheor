package musheor.mixin.meteor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.BetterTab;
import musheor.accessor.BetterTabAccessor;
import musheor.compat.VersionHelper;
import musheor.utils.system.MusheorSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({PlayerListHud.class})
public abstract class PlayerListHudMixin implements BetterTabAccessor {
    @Shadow
    @Final
    private MinecraftClient client;
    @Unique
    private boolean musheor$scaled;

    @Inject(
        method = {"collectPlayerEntries"},
        at = {@At("RETURN")},
        cancellable = true
    )
    private void meteor$filterTabList(CallbackInfoReturnable<List<PlayerListEntry>> cir) {
        BetterTab betterTab = Modules.get().get(BetterTab.class);
        if (betterTab.isActive()) {
            MusheorSystem.TabListMode mode = ((BetterTabAccessor)betterTab).musheor$getDisplayMode().get();
            if (mode != MusheorSystem.TabListMode.All) {
                List<PlayerListEntry> full = new ArrayList<>(Objects.requireNonNull(this.client.getNetworkHandler()).getPlayerList());
                List<PlayerListEntry> filtered = new ArrayList<>();

                for (PlayerListEntry entry : full) {
                    if (mode == MusheorSystem.TabListMode.Friends && Friends.get().isFriend(entry)) {
                        filtered.add(entry);
                    }
                }

                filtered.sort(PlayerListHud.ENTRY_ORDERING);
                int limit = betterTab.tabSize.get();
                if (filtered.size() > limit) {
                    filtered = filtered.subList(0, limit);
                }

                cir.setReturnValue(filtered);
            }
        }
    }

    @Unique
    private static float musheor$scale() {
        BetterTab betterTab = Modules.get().get(BetterTab.class);
        return betterTab != null && betterTab.isActive() ? ((BetterTabAccessor)betterTab).musheor$getScale().get().floatValue() : 1.0F;
    }

    @Inject(
        method = {"render"},
        at = {@At("HEAD")}
    )
    private void musheor$scaleTabList(DrawContext context, int scaledWindowWidth, Scoreboard scoreboard, ScoreboardObjective objective, CallbackInfo ci) {
        float scale = musheor$scale();
        this.musheor$scaled = scale != 1.0F;
        if (this.musheor$scaled) {
            VersionHelper.get().pushHudScale(context, scale);
        }
    }

    @ModifyVariable(
        method = {"render"},
        at = @At("HEAD"),
        ordinal = 0,
        argsOnly = true
    )
    private int musheor$scaleWidth(int scaledWindowWidth) {
        float scale = musheor$scale();
        return scale == 1.0F ? scaledWindowWidth : Math.round(scaledWindowWidth / scale);
    }

    @Inject(
        method = {"render"},
        at = {@At("RETURN")}
    )
    private void musheor$unscaleTabList(DrawContext context, int scaledWindowWidth, Scoreboard scoreboard, ScoreboardObjective objective, CallbackInfo ci) {
        if (this.musheor$scaled) {
            VersionHelper.get().popHudScale(context);
            this.musheor$scaled = false;
        }
    }
}
