package musheor.modules.automation;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BlockSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Names;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import musheor.musheor;
import musheor.mixin.AxeItemAccessor;
import musheor.modules.features.KekMine;
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
import net.minecraft.block.Oxidizable;
import net.minecraft.item.AxeItem;
import net.minecraft.item.HoneycombItem;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class Stripper extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<Block> block = this.sgGeneral
        .add(
            new Builder()
                .name("block")
                .description("Block to place and strip.")
                .filter(Stripper::isStrippable)
                .defaultValue(Blocks.OAK_LOG)
                .onChanged(block -> {
                    if (this.targetState != null) {
                        List<Block> chain = getStripChain(block);
                        if (!chain.isEmpty()) {
                            this.targetState.set(chain.getLast());
                        }
                    }
                })
                .build()
        );
    private final Setting<Block> targetState = this.sgGeneral
        .add(
            new Builder()
                .name("target-state")
                .description("Keep interacting until the block reaches this state.")
                .filter(block -> getStripChain(this.block.get()).contains(block))
                .defaultValue(Blocks.STRIPPED_OAK_LOG)
                .visible(() -> getStripChain(this.block.get()).size() > 1)
                .build()
        );
    private final Setting<Integer> stepDelay = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("step-delay")
                .description("Ticks to wait between placing, stripping and breaking. 0 runs one step per tick (3 ticks per block).")
                .defaultValue(0)
                .sliderRange(0, 20)
                .build()
        );
    private final Setting<Boolean> selfDisable = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("self-disable")
                .description("Disables when you run out of blocks to strip, or when there is no axe left in your inventory.")
                .defaultValue(true)
                .build()
        );
    private Stripper.Stage stage = Stripper.Stage.PLACE;
    private BlockPos targetPos;
    private BlockPos lastBrokenPos;
    private int breakCount;
    private int delayTicks;

    public Stripper() {
        super(musheor.AUTOMATION, "stripper", "Automatically places, strips and breaks logs and waxed copper blocks");
    }

    @Override
    public void onActivate() {
        this.stage = Stripper.Stage.PLACE;
        this.targetPos = null;
        this.lastBrokenPos = null;
        this.breakCount = 0;
        this.delayTicks = 0;
    }

    @Override
    public void onDeactivate() {
        this.targetPos = null;
        this.lastBrokenPos = null;
        this.delayTicks = 0;
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player != null && this.mc.world != null) {
            if (!HighwayBuilder.isEating() && !HighwayBuilder.isKillAuraAttacking()) {
                if (this.delayTicks > 0) {
                    this.delayTicks--;
                } else if (!this.hasAxe()) {
                    if (this.selfDisable.get()) {
                        this.warning("No axe left in your inventory, disabling.", new Object[0]);
                        this.toggle();
                    }
                } else {
                    BlockPos pos = this.mc.player.getBlockPos().offset(this.mc.player.getHorizontalFacing());
                    if (!pos.equals(this.targetPos)) {
                        this.targetPos = pos;
                        this.breakCount = 0;
                        this.stage = Stripper.Stage.PLACE;
                    }

                    switch (this.stage) {
                        case PLACE:
                            this.doPlace();
                            break;
                        case STRIP:
                            this.doStrip();
                            break;
                        case BREAK:
                            this.doBreak();
                    }
                }
            }
        }
    }

    private void doPlace() {
        BlockState state = this.mc.world.getBlockState(this.targetPos);
        if (state.getBlock() == this.block.get()) {
            this.setStage(Stripper.Stage.STRIP);
        } else if (!state.isReplaceable()) {
            this.setStage(Stripper.Stage.BREAK);
        } else if (InventoryManager.countItem(this.block.get().asItem()) == 0) {
            if (this.selfDisable.get()) {
                this.info("§2Out of %s, disabling.", new Object[]{Names.get(this.block.get())});
                this.toggle();
            }
        } else {
            boolean silent = MusheorSystem.Manager.silentPlace.get();
            if (!InventoryManager.selectItem(this.block.get().asItem())) {
                this.delayTicks = MusheorSystem.Manager.swapDelay.get();
            } else {
                MusheorSystem.PlacementType type = HighwayState.getInstance().getPlacementType();
                if (type.equals(MusheorSystem.PlacementType.Rotate)) {
                    HighwayUtils.rotateToFace(this.targetPos, Direction.DOWN);
                }

                BlockPlacer.place(this.targetPos, Direction.DOWN, type, silent);
                this.setStage(Stripper.Stage.STRIP);
            }
        }
    }

    private void doStrip() {
        Block block = this.mc.world.getBlockState(this.targetPos).getBlock();
        int needed = this.countInteractions(block);
        if (needed == 0) {
            this.setStage(Stripper.Stage.BREAK);
        } else if (!(this.mc.player.getMainHandStack().getItem() instanceof AxeItem)) {
            if (this.swapToAxe()) {
                this.delayTicks = MusheorSystem.Manager.swapDelay.get();
            }
        } else {
            Direction side = this.getInteractSide(this.targetPos);

            for (int i = 0; i < needed; i++) {
                if (!PacketLimiter.canSendPlacePacket()) {
                    return;
                }

                this.interactBlock(this.targetPos, side);
            }

            this.setStage(Stripper.Stage.BREAK);
        }
    }

    private void doBreak() {
        BlockState state = this.mc.world.getBlockState(this.targetPos);
        if (state.isAir()) {
            this.setStage(Stripper.Stage.PLACE);
        } else {
            if (this.targetPos.equals(this.lastBrokenPos) && this.breakCount > 0) {
                InventoryManager.selectBestTool(this.lastBrokenPos);
                KekMine.INSTANCE.sendAction(Action.STOP_DESTROY_BLOCK, this.targetPos);
                if (!KekMine.INSTANCE.validateBreak.get()) {
                    this.mc.world.syncWorldEvent(2001, this.targetPos, Block.getRawIdFromState(this.mc.world.getBlockState(this.targetPos)));
                    this.mc.world.setBlockState(this.targetPos, Blocks.AIR.getDefaultState(), 3);
                }
            } else if (KekMine.INSTANCE.isActive()) {
                KekMine.INSTANCE.startMining(this.targetPos, state, false);
            } else {
                BlockUtils.breakBlock(this.targetPos, true);
            }

            this.lastBrokenPos = this.targetPos;
            this.breakCount++;
            this.setStage(Stripper.Stage.PLACE);
        }
    }

    private void setStage(Stripper.Stage next) {
        this.stage = next;
        this.delayTicks = this.stepDelay.get();
    }

    private boolean hasAxe() {
        return InvUtils.find(stack -> stack.getItem() instanceof AxeItem).found();
    }

    private boolean swapToAxe() {
        FindItemResult axe = InvUtils.find(stack -> stack.getItem() instanceof AxeItem);
        if (!axe.found()) {
            return false;
        } else {
            InventoryManager.swapToItem(this.mc.player.getInventory().getStack(axe.slot()).getItem());
            return true;
        }
    }

    private void interactBlock(BlockPos pos, Direction side) {
        if (this.mc.interactionManager != null) {
            Vec3d hitPos = Vec3d.ofCenter(pos).add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
            BlockUtils.interact(new BlockHitResult(hitPos, side, pos, false), Hand.MAIN_HAND, true);
        }
    }

    private Direction getInteractSide(BlockPos pos) {
        return KekMine.INSTANCE != null ? KekMine.INSTANCE.getBreakFace(pos) : Direction.UP;
    }

    public static boolean isStrippable(Block block) {
        return getNextState(block) != null;
    }

    public static Block getNextState(Block block) {
        Block stripped = AxeItemAccessor.getStrippedBlocks().get(block);
        if (stripped != null) {
            return stripped;
        } else {
            Block unwaxed = (Block)HoneycombItem.WAXED_TO_UNWAXED_BLOCKS.get().get(block);
            return unwaxed != null ? unwaxed : Oxidizable.getDecreasedOxidationBlock(block).orElse(null);
        }
    }

    public static List<Block> getStripChain(Block from) {
        List<Block> chain = new ArrayList<>(4);
        Block current = from;

        while ((current = getNextState(current)) != null && !chain.contains(current)) {
            chain.add(current);
        }

        return chain;
    }

    private int countInteractions(Block from) {
        List<Block> chain = getStripChain(from);
        int index = chain.indexOf(this.targetState.get());
        return index < 0 ? chain.size() : index + 1;
    }

    public int countInteractions() {
        return this.countInteractions(this.block.get());
    }

    @Override
    public String getInfoString() {
        int needed = this.countInteractions();
        return needed > 1 ? Names.get(this.block.get()) + " x" + needed : Names.get(this.block.get());
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (this.mc.player != null && this.mc.world != null && this.targetPos != null) {
            BlockRenderUtils.renderPosition(event, this.targetPos);
        }
    }

    private static enum Stage {
        PLACE,
        STRIP,
        BREAK;
    }
}
