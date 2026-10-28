package musheor.modules.features;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BlockListSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import musheor.musheor;
import musheor.utils.BlockRenderUtils;
import musheor.utils.highway.HighwayState;
import musheor.utils.manager.InventoryManager;
import musheor.utils.network.PacketLimiter;
import musheor.utils.placement.BlockPlacer;
import musheor.utils.system.MusheorSystem;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class GrimScaffold extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private static final MinecraftClient client = MinecraftClient.getInstance();
    private final Setting<List<Block>> allowedBlocks = this.sgGeneral
        .add(
            new Builder()
                .name("allowed-blocks")
                .description("Which blocks scaffold is allowed to place")
                .defaultValue(Blocks.OBSIDIAN, Blocks.NETHERRACK)
                .build()
        );
    private final Setting<Boolean> expand = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("expand")
                .description("Extend the scaffold area beyond directly under your feet")
                .defaultValue(false)
                .build()
        );
    private final Setting<Double> expandRange = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("expand-range")
                .description("How many blocks to expand the scaffold area")
                .defaultValue(2.0)
                .sliderRange(1.0, 5.0)
                .decimalPlaces(1)
                .visible(this.expand::get)
                .build()
        );
    private final Setting<Boolean> expandForwardOnly = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("expand-forward-only")
                .description("Only expand in the direction you are currently facing")
                .defaultValue(true)
                .visible(this.expand::get)
                .build()
        );
    private final Setting<Boolean> render = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("render")
                .description("Renders an overlay where blocks will be placed")
                .defaultValue(true)
                .build()
        );
    private final List<BlockPos> renderPositions = new ArrayList<>();
    private Block selectedBlock = null;

    public GrimScaffold() {
        super(musheor.MAIN, "simple-scaffold", "Place blocks under your feet, even if you are mid-air");
    }

    @Override
    public void onActivate() {
        this.renderPositions.clear();
        this.selectedBlock = null;
    }

    @Override
    public void onDeactivate() {
        this.renderPositions.clear();
        this.selectedBlock = null;
    }

    @EventHandler
    private void onTickPost(Post event) {
        if (client.player != null && client.world != null) {
            this.selectedBlock = this.findAllowedBlock();
            this.renderPositions.clear();
            List<BlockPos> toPlace = this.getScaffoldPositions();
            toPlace.removeIf(pos -> !BlockUtils.canPlace(pos, true));
            if (this.render.get()) {
                this.renderPositions.addAll(toPlace);
            }

            if (this.selectedBlock != null && !toPlace.isEmpty()) {
                if (!(client.player.getMainHandStack().getItem() instanceof BlockItem bi && bi.getBlock() == this.selectedBlock)) {
                    InventoryManager.swapToItem(this.selectedBlock.asItem(), MusheorSystem.Manager.silentPlace.get());
                }

                for (BlockPos pos : toPlace) {
                    if (!PacketLimiter.canSendPlacePacket()) {
                        break;
                    }

                    BlockPlacer.place(pos, Direction.UP, HighwayState.getInstance().getPlacementType(), MusheorSystem.Manager.silentPlace.get());
                }
            }
        }
    }

    private Block findAllowedBlock() {
        ItemStack main = client.player.getMainHandStack();
        if (main.getItem() instanceof BlockItem bi && this.allowedBlocks.get().contains(bi.getBlock()) && !main.isEmpty()) {
            return bi.getBlock();
        } else {
            for (int i = 0; i < 9; i++) {
                ItemStack s = client.player.getInventory().getStack(i);
                if (s.getItem() instanceof BlockItem bi && this.allowedBlocks.get().contains(bi.getBlock()) && !s.isEmpty()) {
                    return bi.getBlock();
                }
            }

            return null;
        }
    }

    private List<BlockPos> getScaffoldPositions() {
        List<BlockPos> positions = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        BlockPos below = client.player.getBlockPos().down();
        if (seen.add(below)) {
            positions.add(below);
        }

        if (this.expand.get()) {
            int range = (int)Math.ceil(this.expandRange.get());
            double rangeSq = this.expandRange.get() * this.expandRange.get();
            Direction facing = client.player.getHorizontalFacing();
            if (this.expandForwardOnly.get()) {
                for (int i = 1; i <= range; i++) {
                    BlockPos p = new BlockPos(below.getX() + facing.getOffsetX() * i, below.getY(), below.getZ() + facing.getOffsetZ() * i);
                    if (seen.add(p)) {
                        positions.add(p);
                    }
                }
            } else {
                for (int x = -range; x <= range; x++) {
                    for (int z = -range; z <= range; z++) {
                        if ((x != 0 || z != 0) && !(x * x + z * z > rangeSq)) {
                            BlockPos p = below.add(x, 0, z);
                            if (seen.add(p)) {
                                positions.add(p);
                            }
                        }
                    }
                }
            }
        }

        return positions;
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (this.render.get() && !this.renderPositions.isEmpty()) {
            Block b = this.selectedBlock != null ? this.selectedBlock : Blocks.OBSIDIAN;
            BlockRenderUtils.renderPositions(event, this.renderPositions, b);
        }
    }
}
