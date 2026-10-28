package musheor.modules.automation;

import baritone.api.pathing.goals.GoalBlock;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import musheor.musheor;
import musheor.modules.features.KekMine;
import musheor.utils.HighwayUtils;
import musheor.utils.PlayerControlUtils;
import musheor.utils.baritone.BaritoneHelper;
import musheor.utils.highway.HighwayState;
import musheor.utils.manager.InventoryManager;
import musheor.utils.placement.BlockPlacer;
import musheor.utils.system.MusheorSystem;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class EchestFarmer extends Module {
    private final Setting<Boolean> selfToggle = this.settings
        .getDefaultGroup()
        .add(new Builder().name("self-toggle").description("Disables when you reach the desired amount of ender chests.").defaultValue(true).build());
    private final Setting<Integer> amount = this.settings
        .getDefaultGroup()
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("amount")
                .description("The amount of ender chests to farm.")
                .defaultValue(64)
                .sliderRange(1, 64)
                .visible(this.selfToggle::get)
                .build()
        );
    private final Setting<Boolean> superFarm20bps = this.settings
        .getDefaultGroup()
        .add(
            new Builder()
                .name("super-farm-20bps")
                .description("Uses instant rebreak when mining enderchests to speed up the farming process up to 20 blocks per second.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Integer> rebreakStallTimeout = this.settings
        .getDefaultGroup()
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("rebreak-stall-timeout")
                .description("Ticks without progress during instant rebreak before falling back to one normal break.")
                .defaultValue(40)
                .min(5)
                .sliderRange(10, 200)
                .visible(this.superFarm20bps::get)
                .build()
        );
    public static BlockPos echestPos;
    private int placedCount;
    private boolean positionInitialized = false;
    private boolean justPlaced = false;
    private int delayTicks;
    private int placeCooldown;
    private Block lastBlock;
    private int stallTicks;
    private int fallbackBreakTicks;
    public static EchestFarmer INSTANCE;

    public EchestFarmer() {
        super(
            musheor.AUTOMATION, "obi-farmer", "An enderchest farmer that considers the player's inventory space when mining for obsidian, its also really fast"
        );
        INSTANCE = this;
    }

    @Override
    public void onDeactivate() {
        echestPos = null;
        PlayerControlUtils.cancelBaritone();
        this.delayTicks = 0;
    }

    @Override
    public void onActivate() {
        echestPos = null;
        this.placedCount = 0;
        this.positionInitialized = false;
        this.justPlaced = false;
        this.placeCooldown = 0;
        this.lastBlock = null;
        this.stallTicks = 0;
        this.fallbackBreakTicks = 0;
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player != null && this.mc.world != null) {
            HighwayState state = HighwayState.getInstance();
            if (!HighwayBuilder.isEating() && !HighwayBuilder.isKillAuraAttacking() && !SourceRemover.isRemovingLava() && !BaritoneHelper.isPathing()) {
                if (this.delayTicks > 0) {
                    this.delayTicks--;
                } else {
                    if (this.placeCooldown > 0 && this.justPlaced) {
                        this.placeCooldown--;
                    }

                    if (!this.positionInitialized) {
                        state.setEchestFarmPos(this.mc.player.getBlockPos());
                        if (Modules.get().get(HighwayBuilder.class).isActive()) {
                            echestPos = HighwayUtils.getPosBehind(HighwayBuilder.getDirection());
                        } else {
                            echestPos = HighwayUtils.getPosBehind(HighwayUtils.getPlayerDirection().opposite());
                        }

                        this.positionInitialized = true;
                    } else {
                        boolean isEchestHere = this.mc.world.getBlockState(echestPos).getBlock() == Blocks.ENDER_CHEST;
                        boolean isEmpty = this.mc.world.getBlockState(echestPos).isReplaceable();
                        boolean isObstructed = !isEchestHere && !isEmpty;
                        boolean notAligned = HighwayUtils.horizontalDistance(state.getEchestFarmPos(), this.mc.player.getBlockPos()) > 1.25;
                        if (notAligned) {
                            BaritoneHelper.setGoalAndPath(new GoalBlock(state.getEchestFarmPos()));
                        } else if ((!this.selfToggle.get() || this.placedCount < this.amount.get()) && InventoryManager.countPickaxes(false) != 0) {
                            FindItemResult echest = InvUtils.findInHotbar(Items.ENDER_CHEST);
                            if (!echest.found()) {
                                if (InventoryManager.countItem(Items.ENDER_CHEST) < 8) {
                                    state.setEchestFarming(false);
                                    this.toggle();
                                } else {
                                    InventoryManager.moveToHotbar(Items.ENDER_CHEST);
                                }
                            } else {
                                if (this.mc.currentScreen instanceof GenericContainerScreen) {
                                    this.mc.currentScreen.close();
                                }

                                if (isObstructed && this.placedCount < 2) {
                                    if (KekMine.INSTANCE.isActive()) {
                                        KekMine.INSTANCE.startMining(echestPos, this.mc.world.getBlockState(echestPos), false);
                                    } else {
                                        BlockUtils.breakBlock(echestPos, true);
                                    }
                                } else {
                                    if (this.superFarm20bps.get()) {
                                        if (this.placedCount < 2) {
                                            if (isEmpty && (!this.justPlaced || this.placeCooldown == 0)) {
                                                if (!InventoryManager.selectItem(Items.ENDER_CHEST)) {
                                                    this.delayTicks = MusheorSystem.Manager.swapDelay.get();
                                                    return;
                                                }

                                                this.placeEchest();
                                                this.justPlaced = true;
                                                this.placeCooldown = 10;
                                            } else if (isEchestHere) {
                                                if (KekMine.INSTANCE.isActive()) {
                                                    KekMine.INSTANCE.startMining(echestPos, this.mc.world.getBlockState(echestPos), false);
                                                } else {
                                                    InventoryManager.selectEchestTool(false);
                                                    BlockUtils.breakBlock(echestPos, true);
                                                }

                                                this.justPlaced = false;
                                                this.placeCooldown = 0;
                                            }
                                        } else {
                                            Block here = this.mc.world.getBlockState(echestPos).getBlock();
                                            if (here != this.lastBlock) {
                                                this.lastBlock = here;
                                                this.stallTicks = 0;
                                            } else if (!isEmpty && this.fallbackBreakTicks == 0) {
                                                this.stallTicks++;
                                            }

                                            if (this.fallbackBreakTicks > 0) {
                                                if (!isEmpty) {
                                                    this.fallbackBreakTicks--;
                                                    InventoryManager.selectEchestTool(false);
                                                    if (KekMine.INSTANCE.isActive()) {
                                                        KekMine.INSTANCE.startMining(echestPos, this.mc.world.getBlockState(echestPos), false);
                                                    } else {
                                                        BlockUtils.breakBlock(echestPos, true);
                                                    }

                                                    return;
                                                }

                                                this.fallbackBreakTicks = 0;
                                            } else if (this.stallTicks >= this.rebreakStallTimeout.get()) {
                                                MusheorSystem.debug("Rebreak stalled for %s ticks, falling back to a normal break", this.stallTicks);
                                                this.stallTicks = 0;
                                                this.fallbackBreakTicks = 40;
                                                return;
                                            }

                                            if (isEmpty && this.placeCooldown == 0) {
                                                this.placeEchest();
                                            }

                                            InventoryManager.selectEchestTool(false);
                                            KekMine.INSTANCE.sendAction(Action.STOP_DESTROY_BLOCK, echestPos);
                                        }
                                    } else {
                                        if (isEchestHere) {
                                            InventoryManager.selectEchestTool(false);
                                            if (KekMine.INSTANCE.isActive()) {
                                                KekMine.INSTANCE.startMining(echestPos, this.mc.world.getBlockState(echestPos), false);
                                            } else {
                                                BlockUtils.breakBlock(echestPos, true);
                                            }

                                            this.justPlaced = false;
                                            this.placeCooldown = 0;
                                            return;
                                        }

                                        if (isEmpty && (!this.justPlaced || this.placeCooldown == 0)) {
                                            if (!InventoryManager.selectItem(Items.ENDER_CHEST)) {
                                                this.delayTicks = MusheorSystem.Manager.swapDelay.get();
                                                return;
                                            }

                                            this.placeEchest();
                                            this.justPlaced = true;
                                            this.placeCooldown = 10;
                                        }
                                    }
                                }
                            }
                        } else {
                            MusheorSystem.debug("Mining last enderchest...");
                            if (!isEchestHere && !isObstructed) {
                                state.setEchestFarming(false);
                                this.toggle();
                            } else {
                                InventoryManager.selectEchestTool(false);
                                if (KekMine.INSTANCE.isActive()) {
                                    KekMine.INSTANCE.startMining(echestPos, this.mc.world.getBlockState(echestPos), false);
                                } else {
                                    BlockUtils.breakBlock(echestPos, true);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void placeEchest() {
        InventoryManager.swapToItem(Items.ENDER_CHEST, MusheorSystem.Manager.silentPlace.get());
        if (HighwayState.getInstance().getPlacementType().equals(MusheorSystem.PlacementType.Rotate)) {
            HighwayUtils.rotateToFace(echestPos, Direction.DOWN);
        }

        BlockPlacer.place(echestPos, Direction.DOWN, HighwayState.getInstance().getPlacementType(), MusheorSystem.Manager.silentPlace.get());
        this.placedCount++;
    }
}
