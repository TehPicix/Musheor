package musheor.modules.tech;

import java.util.List;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import musheor.musheor;
import musheor.commands.RestockConfig;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

public class AntiInteract extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final MinecraftClient client = MinecraftClient.getInstance();
    private final Setting<Boolean> beds = this.sgGeneral
        .add(new Builder().name("beds").description("Stops you from interacting with beds").defaultValue(true).build());
    private final Setting<Boolean> respawnAnchors = this.sgGeneral
        .add(new Builder().name("respawn-anchors").description("Stops you from interacting with respawn anchors").defaultValue(true).build());
    private final Setting<List<Block>> blockList = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BlockListSetting.Builder()
                .name("block-list")
                .description("When enabled, stops you from interacting with beds")
                .defaultValue()
                .build()
        );

    @EventHandler
    private void onInteractBlock(InteractBlockEvent event) {
        if (this.client.player != null && this.client.world != null) {
            BlockPos pos = event.result.getBlockPos();
            BlockState state = this.client.world.getBlockState(pos);
            if (this.beds.get() && state.getBlock() instanceof BedBlock) {
                event.cancel();
                ChatUtils.info("§cPrevented interaction with §e%s", RestockConfig.getItemId(state.getBlock().asItem()));
            } else if (this.respawnAnchors.get() && state.getBlock() instanceof RespawnAnchorBlock) {
                event.cancel();
                ChatUtils.info("§cPrevented interaction with §e%s", RestockConfig.getItemId(state.getBlock().asItem()));
            } else if (this.blockList.get().contains(state.getBlock())) {
                event.cancel();
                ChatUtils.info("§cPrevented interaction with §e%s", RestockConfig.getItemId(state.getBlock().asItem()));
            }
        }
    }

    public AntiInteract() {
        super(musheor.MAIN, "anti-interact", "Prevents you from interacting with certain blocks e.g. beds and respawn anchors");
    }
}
