package musheor.modules.automation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import musheor.musheor;
import musheor.utils.BlockRenderUtils;
import musheor.utils.HighwayUtils;
import musheor.utils.highway.HighwayState;
import musheor.utils.manager.InventoryManager;
import musheor.utils.network.PacketLimiter;
import musheor.utils.placement.BlockPlacer;
import musheor.utils.system.MusheorSystem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.BubbleColumnBlock;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class SourceRemover extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<Boolean> onlySources = this.sgGeneral
        .add(new Builder().name("only-sources").description("Only fills source blocks, not flowing fluids").defaultValue(true).build());
    private final Setting<Boolean> lava = this.sgGeneral.add(new Builder().name("lava").description("Fills lava source blocks").defaultValue(true).build());
    private final Setting<Boolean> water = this.sgGeneral.add(new Builder().name("water").description("Fills water source blocks").defaultValue(false).build());
    private final Setting<Boolean> bubbleColumn = this.sgGeneral
        .add(new Builder().name("bubble-column").description("Fills bubble column source blocks").defaultValue(false).visible(this.water::get).build());
    private final Setting<SourceRemover.ListMode> listMode = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.EnumSetting.Builder<SourceRemover.ListMode>()
                            .name("list-mode")
                        .description("Selection mode.")
                    .defaultValue(SourceRemover.ListMode.None)
                .build()
        );
    private final Setting<List<Block>> whitelist = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BlockListSetting.Builder()
                .name("whitelist")
                .description("The allowed blocks that it will use to fill up the lava.")
                .defaultValue(Blocks.NETHERRACK, Blocks.COBBLESTONE, Blocks.DIRT, Blocks.BLACKSTONE, Blocks.OBSIDIAN)
                .visible(() -> this.listMode.get() == SourceRemover.ListMode.Whitelist)
                .build()
        );
    private final Setting<List<Block>> blacklist = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BlockListSetting.Builder()
                .name("blacklist")
                .description("The denied blocks that it not will use to fill up the lava.")
                .visible(() -> this.listMode.get() == SourceRemover.ListMode.Blacklist)
                .build()
        );
    private final Setting<Boolean> render = this.sgGeneral
        .add(new Builder().name("render").description("Render source blocks in specific range").defaultValue(true).build());
    private final Setting<Integer> renderRange = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("render-range")
                .description("Maximum range at which sourceblocks are rendered")
                .defaultValue(16)
                .sliderRange(1, 64)
                .visible(this.render::get)
                .build()
        );
    private final List<BlockPos> renderPositions = new ArrayList<>();
    private int tickCount;
    private int delayTicks;
    private int renderScanTimer = 0;
    public static boolean removingLava;

    public static boolean isRemovingLava() {
        return removingLava;
    }

    public SourceRemover() {
        super(musheor.AUTOMATION, "source-filler", "Fill up lava and or water source blocks");
    }

    @Override
    public void onActivate() {
        HighwayState.getInstance().getPendingPlacements().clear();
        this.tickCount = 0;
        this.renderScanTimer = 0;
        this.delayTicks = 0;
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player != null && this.mc.world != null) {
            if (!HighwayBuilder.isKillAuraAttacking() && !HighwayBuilder.isEchestFarmerActive()) {
                this.tickCount++;
                HighwayUtils.clearExpiredPlacements(this.tickCount, Blocks.AIR);
                if (++this.renderScanTimer > 5 && this.render.get()) {
                    this.renderScanTimer = 0;
                    this.updateRenderPositions();
                }

                Item bestItem = null;

                for (int i = 0; i < 9; i++) {
                    ItemStack itemStack = this.mc.player.getInventory().getStack(i);
                    if (this.isValidFillItem(itemStack)) {
                        bestItem = itemStack.getItem();
                    }
                }

                if (bestItem == null) {
                    for (int ix = 9; ix < this.mc.player.getInventory().main.size(); ix++) {
                        ItemStack itemStack = this.mc.player.getInventory().getStack(ix);
                        if (this.isValidFillItem(itemStack)) {
                            InventoryManager.moveToHotbar(itemStack.getItem());
                            return;
                        }
                    }
                } else {
                    List<BlockPos> positions = this.findFillPositions(MusheorSystem.Manager.placementRange.get());
                    positions.sort(Comparator.comparingDouble(posx -> this.mc.player.squaredDistanceTo(Vec3d.of(posx))));
                    boolean place = false;

                    for (BlockPos pos : positions) {
                        if (BlockUtils.canPlace(pos, true) && HighwayUtils.isInPlacementRange(pos)) {
                            RenderUtils.renderTickingBlock(pos, Color.PINK, Color.PINK, ShapeMode.Lines, 0, 1, false, false);
                            place = true;
                            break;
                        }
                    }

                    if (this.delayTicks > 0) {
                        this.delayTicks--;
                    } else {
                        if (place) {
                            if (!InventoryManager.selectItem(bestItem)) {
                                this.delayTicks = MusheorSystem.Manager.swapDelay.get();
                                return;
                            }

                            for (BlockPos posx : positions) {
                                if (BlockUtils.canPlace(posx, true)) {
                                    Direction side = KekNuker.getClosestFace(this.mc.player.getEyePos(), posx);
                                    if (!PacketLimiter.canSendPlacePacket()) {
                                        break;
                                    }

                                    BlockPlacer.place(posx, side, HighwayState.getInstance().getPlacementType(), MusheorSystem.Manager.silentPlace.get());
                                    HighwayState.getInstance().getPendingPlacements().put(posx, this.tickCount);
                                    MusheorSystem.debug(
                                        "Filled liquid at %d, %d, %d with a distance of: %s",
                                        posx.getX(),
                                        posx.getY(),
                                        posx.getZ(),
                                        Math.sqrt(KekNuker.squaredDistanceToBlock(this.mc.player.getEyePos(), posx))
                                    );
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isValidFillItem(ItemStack stack) {
        return this.listMode.get() == SourceRemover.ListMode.Blacklist && !this.blacklist.get().contains(Block.getBlockFromItem(stack.getItem()))
            || this.listMode.get() == SourceRemover.ListMode.Whitelist && this.whitelist.get().contains(Block.getBlockFromItem(stack.getItem()))
            || this.listMode.get() == SourceRemover.ListMode.None
                && stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock().getDefaultState().isSolid();
    }

    private void updateRenderPositions() {
        if (this.mc.world != null && this.mc.player != null) {
            this.renderPositions.clear();
            BlockPos playerPos = this.mc.player.getBlockPos();
            int dist = this.renderRange.get();

            for (int x = -dist; x <= dist; x++) {
                for (int y = -dist; y <= dist; y++) {
                    for (int z = -dist; z <= dist; z++) {
                        BlockPos pos = playerPos.add(x, y, z);
                        if (this.mc.player.getEyePos().distanceTo(new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) <= dist) {
                            BlockState state = this.mc.world.getBlockState(pos);
                            FluidState fluid = state.getFluidState();
                            if (!this.onlySources.get() || fluid.isStill()) {
                                if (this.lava.get() && (fluid.getFluid() == Fluids.LAVA || fluid.getFluid() == Fluids.FLOWING_LAVA)) {
                                    this.renderPositions.add(pos);
                                }

                                if (this.water.get()
                                    && (fluid.getFluid() == Fluids.WATER || fluid.getFluid() == Fluids.FLOWING_WATER)
                                    && (!(state.getBlock() instanceof BubbleColumnBlock) || this.bubbleColumn.get())) {
                                    this.renderPositions.add(pos);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private List<BlockPos> findFillPositions(double radius) {
        List<BlockPos> positions = new ArrayList<>();
        Vec3d eyePos = this.mc.player.getEyePos();
        int blockRadius = (int)Math.ceil(radius);

        for (int x = -blockRadius; x < radius; x++) {
            for (int y = -blockRadius; y < blockRadius; y++) {
                for (int z = -blockRadius; z < blockRadius; z++) {
                    BlockPos pos = BlockPos.ofFloored(eyePos.x + x, eyePos.y + y, eyePos.z + z);
                    BlockState blockState = this.mc.world.getBlockState(pos);
                    FluidState fluid = blockState.getFluidState();
                    if (fluid != null && !(KekNuker.squaredDistanceToBlock(eyePos, pos) > radius * radius) && (!this.onlySources.get() || fluid.isStill())) {
                        if (this.water.get() && (fluid.getFluid() == Fluids.WATER || fluid.getFluid() == Fluids.FLOWING_WATER)) {
                            if (!(blockState.getBlock() instanceof BubbleColumnBlock) || this.bubbleColumn.get()) {
                                positions.add(pos);
                            }
                        } else if (this.lava.get() && (fluid.getFluid() == Fluids.LAVA || fluid.getFluid() == Fluids.FLOWING_LAVA)) {
                            positions.add(pos);
                        }
                    }
                }
            }
        }

        return positions;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (this.render.get() && !this.renderPositions.isEmpty() && this.mc.world != null) {
            BlockRenderUtils.renderPositions(event, this.renderPositions);
        }
    }

    public static enum ListMode {
        None,
        Whitelist,
        Blacklist;
    }
}
