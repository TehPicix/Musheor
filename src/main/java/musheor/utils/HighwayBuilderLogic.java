package musheor.utils;

import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalXZ;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import musheor.modules.automation.EchestFarmer;
import musheor.modules.automation.HighwayBuilder;
import musheor.modules.automation.KekBounce;
import musheor.modules.automation.KekNuker;
import musheor.modules.automation.SourceRemover;
import musheor.utils.baritone.BaritoneHelper;
import musheor.utils.highway.HighwayDetector;
import musheor.utils.highway.HighwayState;
import musheor.utils.manager.InventoryManager;
import musheor.utils.system.MusheorSystem;
import net.minecraft.block.AirBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class HighwayBuilderLogic {
    public static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final int CORNER_MARGIN = 2;
    private static final int CORNER_REACH_DISTANCE = 1;
    private static boolean bouncing = false;
    private static int bounceCooldown = 0;
    private static final int BOUNCE_COOLDOWN_TICKS = 20;
    private static final int LEG_END_MARGIN = 32;
    private static boolean recoveringFromFall = false;
    private static int offHighwayTicks = 0;
    private static int repathCooldown = 0;
    private static final int FALL_DETECT_TICKS = 15;
    private static final int REPATH_INTERVAL_TICKS = 40;
    private static final int PAVEMENT_SEARCH_RANGE = 24;

    public static void startEchestFarming() {
        HighwayState state = HighwayState.getInstance();
        PlayerControlUtils.setModuleSetting(EchestFarmer.class, "self-toggle", true);
        state.setEchestFarmPos(mc.player.getBlockPos());
        if (!HighwayUtils.isOnHighway()) {
            PlayerControlUtils.setAutoWalk(false);
            if (!BaritoneHelper.isPathing()) {
                HighwayUtils.returnToHighway();
            }
        } else {
            if (!state.isEchestFarming()) {
                assert mc.player != null;

                int remaining = InventoryManager.countItem(Items.ENDER_CHEST) - 8;
                if (remaining > 0) {
                    PlayerControlUtils.setModuleSetting(EchestFarmer.class, "amount", Math.min(remaining, InventoryManager.countEmptySlots() * 8));
                }

                PlayerControlUtils.setAutoWalk(false);
                state.setEchestFarming(true);
                Module BetterEchestFarmer = Modules.get().get(EchestFarmer.class);
                if (!BetterEchestFarmer.isActive()) {
                    state.setPostEchestFarming(true);
                    BetterEchestFarmer.toggle();
                }
            }
        }
    }

    public static void finishEchestFarming() {
        HighwayState state = HighwayState.getInstance();
        if (!HighwayBuilder.isEchestFarmerActive() && state.isPostEchestFarming()) {
            if (EchestFarmer.echestPos != null && mc.world.getBlockState(EchestFarmer.echestPos).getBlock() == Blocks.ENDER_CHEST) {
                MusheorSystem.debug("Attempting to break incorrect block...");
                BlockUtils.breakBlock(EchestFarmer.echestPos, true);
                return;
            }

            if (PlayerControlUtils.isGatheringItem()) {
                return;
            }

            PlayerControlUtils.startGatheringItem(Items.OBSIDIAN, false);
            state.setEchestFarming(false);
            state.setPostEchestFarming(false);
        }
    }

    public static void tickCardinalPave() {
        assert mc.player != null && mc.world != null;

        HighwayState state = HighwayState.getInstance();
        if (state.getDirection() != null && state.getAnchorX() != null && state.getAnchorY() != null && state.getAnchorZ() != null) {
            if (!handleFallRecovery(state)) {
                Module BetterEChestFarmer = Modules.get().get(EchestFarmer.class);
                Map<String, Boolean> conditions = new LinkedHashMap<>();
                conditions.put("BetterEchestFarmer", BetterEChestFarmer.isActive());
                conditions.put("Gathering Items", PlayerControlUtils.isGatheringItem());
                conditions.put("Removing Lava", SourceRemover.isRemovingLava());
                if (!BetterEChestFarmer.isActive() && !PlayerControlUtils.isGatheringItem() && !SourceRemover.isRemovingLava()) {
                    if (KekNuker.isMiningHighwayZone() || HighwayBuilder.isEating() || HighwayBuilder.isKillAuraAttacking() || InventoryManager.inventoryBusy) {
                        stopBouncing();
                        PlayerControlUtils.setAutoWalk(false);
                    }

                    if (HighwayBuilder.useAdvancedSourceFiller()) {
                        HighwayUtils.handleLavaObstruction();
                    }

                    if (HighwayBuilder.isAutoBounceEnabled() && HighwayBuilder.getMode() == HighwayBuilder.Mode.Semi) {
                        if (bounceCooldown > 0) {
                            bounceCooldown--;
                            return;
                        }

                        boolean interrupted = KekNuker.isMiningHighwayZone()
                            || HighwayBuilder.isEating()
                            || HighwayBuilder.isKillAuraAttacking()
                            || InventoryManager.inventoryBusy
                            || PlayerControlUtils.isGatheringItem()
                            || SourceRemover.isRemovingLava()
                            || BetterEChestFarmer.isActive()
                            || BaritoneHelper.isPathing();
                        if (!interrupted && isClearAhead(true, HighwayBuilder.getBounceDistanceCheck())) {
                            startBouncing();
                        } else {
                            stopBouncing();
                        }

                        if (bouncing) {
                            PlayerControlUtils.faceHighwayYaw();
                            return;
                        }
                    }

                    if (HighwayBuilder.getMode() == HighwayBuilder.Mode.Semi) {
                        PlayerControlUtils.pathToCardinalLane();
                        HighwayUtils.checkCollisionAhead();
                        PlayerControlUtils.faceHighwayDirection();
                        PlayerControlUtils.strafeToLaneCenter();
                    }

                    BlockPos[] positions = HighwayPositions.getCardinalFloor(2, 3, HighwayBuilder.shouldPlaceLeftRail(), HighwayBuilder.shouldPlaceRightRail());
                    if (HighwayBuilder.INSTANCE.toggleKekNuker.get()) {
                        KekNuker.blocksToMine.clear();
                    }

                    state.setPlacementBlocked(false);
                    HighwayUtils.spleefBlockingEntities(positions);
                    List<Block> blacklistedBlocks = KekNuker.getBlacklist();
                    state.setMissingBlock(false);
                    BlockPos nonObsidianBlock = null;
                    boolean foundBlockage = false;
                    BlockPos blockage = null;
                    if (HighwayBuilder.getMode() == HighwayBuilder.Mode.Semi) {
                        for (BlockPos currentPos : HighwayPositions.getCardinalFloor(
                            -3, 5, HighwayBuilder.shouldPlaceLeftRail(), HighwayBuilder.shouldPlaceRightRail()
                        )) {
                            Block block = mc.world.getBlockState(currentPos).getBlock();
                            if ((HighwayBuilder.shouldReplaceCryingObsidian() || block != Blocks.CRYING_OBSIDIAN)
                                && block != HighwayBuilder.getPavementBlock()
                                && HighwayStatsUtils.getDistanceTravelled() > 10
                                && !blacklistedBlocks.contains(block)) {
                                state.setMissingBlock(true);
                                nonObsidianBlock = currentPos;
                                break;
                            }
                        }

                        if (HighwayBuilder.shouldFillCeiling()) {
                            for (BlockPos currentPosx : HighwayPositions.getCardinalCeiling(
                                -3, 4, HighwayBuilder.shouldPlaceLeftRail(), HighwayBuilder.shouldPlaceRightRail()
                            )) {
                                Block block = mc.world.getBlockState(currentPosx).getBlock();
                                if ((HighwayBuilder.shouldReplaceCryingObsidian() || block != Blocks.CRYING_OBSIDIAN)
                                    && block != Blocks.BEDROCK
                                    && block == Blocks.AIR
                                    && HighwayStatsUtils.getDistanceTravelled() > 10
                                    && !blacklistedBlocks.contains(block)) {
                                    state.setMissingBlock(true);
                                    nonObsidianBlock = currentPosx;
                                    break;
                                }
                            }
                        }

                        for (BlockPos currentPosxx : HighwayPositions.getCardinalAreaBehind()) {
                            Block block = mc.world.getBlockState(currentPosxx).getBlock();
                            if (!(block instanceof AirBlock) && HighwayStatsUtils.getDistanceTravelled() > 10 && !blacklistedBlocks.contains(block)) {
                                foundBlockage = true;
                                blockage = currentPosxx;
                                break;
                            }
                        }
                    }

                    if (foundBlockage) {
                        PlayerControlUtils.setAutoWalk(false);
                        MusheorSystem.debug("Detected blockage behind player... %s", mc.world.getBlockState(blockage).getBlock().getName());

                        assert blockage != null;

                        Goal baritoneGoal = switch (HighwayBuilder.getDirection()) {
                            case NORTH -> new GoalBlock(state.getAnchorX(), state.getAnchorY(), blockage.getZ() + 2);
                            case EAST -> new GoalBlock(blockage.getX() - 2, state.getAnchorY(), state.getAnchorZ());
                            case SOUTH -> new GoalBlock(state.getAnchorX(), state.getAnchorY(), blockage.getZ() - 2);
                            case WEST -> new GoalBlock(blockage.getX() + 2, state.getAnchorY(), state.getAnchorZ());
                            default -> new GoalBlock(state.getAnchorX(), state.getAnchorY(), state.getAnchorZ());
                        };
                        BaritoneHelper.setGoalAndPath(baritoneGoal);
                    } else {
                        if (state.hasMissingBlock()) {
                            PlayerControlUtils.setAutoWalk(false);
                            MusheorSystem.debug("Found missing block, going back...");

                            assert nonObsidianBlock != null;
                            Goal baritoneGoal = switch (HighwayBuilder.getDirection()) {
                                case NORTH, SOUTH -> new GoalBlock(state.getAnchorX(), state.getAnchorY(), nonObsidianBlock.getZ());
                                case EAST, WEST -> new GoalBlock(nonObsidianBlock.getX(), state.getAnchorY(), state.getAnchorZ());
                                default -> new GoalBlock(nonObsidianBlock.getX(), state.getAnchorY(), nonObsidianBlock.getZ());
                            };
                            BaritoneHelper.setGoalAndPath(baritoneGoal);
                        }

                        if (HighwayBuilder.INSTANCE.toggleKekNuker.get()) {
                            for (BlockPos pos : positions) {
                                Block block = mc.world.getBlockState(pos).getBlock();
                                if (block != Blocks.NETHER_PORTAL
                                    && block != Blocks.BEDROCK
                                    && (HighwayBuilder.shouldReplaceCryingObsidian() || block != Blocks.CRYING_OBSIDIAN)
                                    && HighwayUtils.needsMining(pos, HighwayBuilder.getPavementBlock())) {
                                    KekNuker.blocksToMine.add(pos.toImmutable());
                                }
                            }

                            for (BlockPos posx : HighwayPositions.getCardinalClearArea(3, 2)) {
                                Block block = mc.world.getBlockState(posx).getBlock();
                                if (block != Blocks.NETHER_PORTAL && block != Blocks.BEDROCK && HighwayUtils.needsMining(posx, Blocks.AIR)) {
                                    KekNuker.blocksToMine.add(posx.toImmutable());
                                }
                            }
                        }

                        state.setPlacing(false);
                        HighwayUtils.placePositions(positions, false);
                        if (HighwayBuilder.shouldFillCeiling()) {
                            HighwayUtils.placePositions(
                                HighwayPositions.getCardinalCeiling(2, 2, HighwayBuilder.shouldPlaceLeftRail(), HighwayBuilder.shouldPlaceRightRail()), false
                            );
                        }

                        if (!state.isPlacing()
                            || !state.isInPlaceRange()
                            || !state.hasMissingBlock()
                            || !KekNuker.isMiningHighwayZone()
                            || !state.isPlacementBlocked()) {
                            PlayerControlUtils.setAutoWalk(true);
                        }
                    }
                } else {
                    PlayerControlUtils.setAutoWalk(false);

                    for (Entry<String, Boolean> entry : conditions.entrySet()) {
                        if (entry.getValue()) {
                            MusheorSystem.debug(entry.getKey() + " was triggered.");
                        }
                    }
                }
            }
        }
    }

    public static void tickDiagonalPave() {
        assert mc.player != null && mc.world != null;

        HighwayState state = HighwayState.getInstance();
        if (state.getDirection() != null && state.getAnchorX() != null && state.getAnchorY() != null && state.getAnchorZ() != null) {
            if (!handleFallRecovery(state)) {
                double dx = mc.player.getBlockX() - state.getStartPos().getX();
                double dz = mc.player.getBlockZ() - state.getStartPos().getZ();
                if (Math.abs(dx) == Math.abs(dz)) {
                    state.setAnchorZ(mc.player.getBlockZ());
                    state.setAnchorX(mc.player.getBlockX());
                }

                if (!HighwayUtils.isOnHighway()) {
                    PlayerControlUtils.setAutoWalk(false);
                    if (!BaritoneHelper.isPathing() && state.getLavaReturnPos() == null && state.getLavaPos() == null) {
                        HighwayUtils.returnToHighway();
                    }
                }

                Module BetterEChestFarmer = Modules.get().get(EchestFarmer.class);
                if (!BetterEChestFarmer.isActive() && !PlayerControlUtils.isGatheringItem() && !SourceRemover.isRemovingLava()) {
                    if (KekNuker.isMiningHighwayZone() || HighwayBuilder.isEating() || HighwayBuilder.isKillAuraAttacking() || InventoryManager.inventoryBusy) {
                        stopBouncing();
                        PlayerControlUtils.setAutoWalk(false);
                    }

                    if (HighwayBuilder.useAdvancedSourceFiller()) {
                        HighwayUtils.handleLavaObstruction();
                    }

                    if (HighwayBuilder.isAutoBounceEnabled() && HighwayBuilder.getMode() == HighwayBuilder.Mode.Semi) {
                        if (bounceCooldown > 0) {
                            bounceCooldown--;
                            return;
                        }

                        boolean interrupted = KekNuker.isMiningHighwayZone()
                            || HighwayBuilder.isEating()
                            || HighwayBuilder.isKillAuraAttacking()
                            || InventoryManager.inventoryBusy
                            || PlayerControlUtils.isGatheringItem()
                            || SourceRemover.isRemovingLava()
                            || BetterEChestFarmer.isActive()
                            || BaritoneHelper.isPathing();
                        if (!interrupted && isClearAhead(false, HighwayBuilder.getBounceDistanceCheck())) {
                            startBouncing();
                        } else {
                            stopBouncing();
                        }

                        if (bouncing) {
                            PlayerControlUtils.faceHighwayYaw();
                            return;
                        }
                    }

                    if (HighwayBuilder.getMode() == HighwayBuilder.Mode.Semi) {
                        PlayerControlUtils.pathToDiagonalAxis();
                        HighwayUtils.checkCollisionAhead();
                        PlayerControlUtils.faceHighwayDirection();
                    }

                    List<Block> blacklistedBlocks = KekNuker.getBlacklist();
                    boolean foundBlockage = false;
                    BlockPos blockage = null;
                    if (HighwayBuilder.getMode() == HighwayBuilder.Mode.Semi) {
                        for (BlockPos currentPos : HighwayPositions.getDiagonalClearArea(-2, 5)) {
                            Block block = mc.world.getBlockState(currentPos).getBlock();
                            if (!(block instanceof AirBlock) && HighwayStatsUtils.getDistanceTravelled() > 10 && !blacklistedBlocks.contains(block)) {
                                foundBlockage = true;
                                blockage = currentPos;
                                break;
                            }
                        }

                        if (foundBlockage) {
                            PlayerControlUtils.setAutoWalk(false);
                            MusheorSystem.debug(
                                "Detected blockage behind player... " + Registries.BLOCK.getId(mc.world.getBlockState(blockage).getBlock()).getPath()
                            );

                            assert blockage != null;

                            int distance = 4;

                            Goal baritoneGoal = switch (HighwayBuilder.getDirection()) {
                                case NORTH_EAST -> new GoalBlock(state.getAnchorX() - distance, state.getAnchorY(), state.getAnchorZ() + distance);
                                case NORTH_WEST -> new GoalBlock(state.getAnchorX() + distance, state.getAnchorY(), state.getAnchorZ() + distance);
                                case SOUTH_EAST -> new GoalBlock(state.getAnchorX() - distance, state.getAnchorY(), state.getAnchorZ() - distance);
                                case SOUTH_WEST -> new GoalBlock(state.getAnchorX() + distance, state.getAnchorY(), state.getAnchorZ() - distance);
                                default -> new GoalBlock(blockage.getX(), state.getAnchorY(), blockage.getZ());
                            };
                            BaritoneHelper.setGoalAndPath(baritoneGoal);
                            return;
                        }

                        state.setMissingBlock(false);
                        BlockPos nonObsidianBlock = null;

                        for (BlockPos currentPosx : HighwayPositions.getDiagonalFloor(
                            -3, 4, HighwayBuilder.shouldPlaceLeftRail(), HighwayBuilder.shouldPlaceRightRail()
                        )) {
                            Block block = mc.world.getBlockState(currentPosx).getBlock();
                            if ((HighwayBuilder.shouldReplaceCryingObsidian() || block != Blocks.CRYING_OBSIDIAN)
                                && block != HighwayBuilder.getPavementBlock()
                                && HighwayStatsUtils.getDistanceTravelled() > 10
                                && (block != Blocks.CRYING_OBSIDIAN || HighwayBuilder.shouldReplaceCryingObsidian())
                                && block != Blocks.BEDROCK
                                && !blacklistedBlocks.contains(block)) {
                                state.setMissingBlock(true);
                                nonObsidianBlock = currentPosx;
                                break;
                            }
                        }

                        if (HighwayBuilder.shouldFillCeiling()) {
                            for (BlockPos currentPosxx : HighwayPositions.getDiagonalCeiling(
                                -3, 4, HighwayBuilder.shouldPlaceLeftRail(), HighwayBuilder.shouldPlaceRightRail()
                            )) {
                                Block block = mc.world.getBlockState(currentPosxx).getBlock();
                                if ((HighwayBuilder.shouldReplaceCryingObsidian() || block != Blocks.CRYING_OBSIDIAN)
                                    && block == Blocks.AIR
                                    && HighwayStatsUtils.getDistanceTravelled() > 10
                                    && !blacklistedBlocks.contains(block)) {
                                    state.setMissingBlock(true);
                                    nonObsidianBlock = currentPosxx;
                                    break;
                                }
                            }
                        }

                        if (state.hasMissingBlock()) {
                            PlayerControlUtils.setAutoWalk(false);
                            MusheorSystem.debug("Found missing block, going back...");

                            assert nonObsidianBlock != null;

                            RenderUtils.renderTickingBlock(nonObsidianBlock, Color.WHITE, Color.WHITE, ShapeMode.Lines, 0, 20, true, false);

                            Goal baritoneGoal = switch (HighwayBuilder.getDirection()) {
                                case NORTH_EAST -> new GoalBlock(state.getAnchorX() - 8, state.getAnchorY(), state.getAnchorZ() + 8);
                                case NORTH_WEST -> new GoalBlock(state.getAnchorX() + 8, state.getAnchorY(), state.getAnchorZ() + 8);
                                case SOUTH_EAST -> new GoalBlock(state.getAnchorX() - 8, state.getAnchorY(), state.getAnchorZ() - 8);
                                case SOUTH_WEST -> new GoalBlock(state.getAnchorX() + 8, state.getAnchorY(), state.getAnchorZ() - 8);
                                default -> new GoalBlock(nonObsidianBlock.getX(), state.getAnchorY(), nonObsidianBlock.getZ());
                            };
                            BaritoneHelper.setGoalAndPath(baritoneGoal);
                            return;
                        }
                    }

                    BlockPos[] positions = HighwayPositions.getDiagonalFloor(2, 2, HighwayBuilder.shouldPlaceLeftRail(), HighwayBuilder.shouldPlaceRightRail());
                    if (HighwayBuilder.INSTANCE.toggleKekNuker.get()) {
                        KekNuker.blocksToMine.clear();
                    }

                    state.setPlacementBlocked(false);
                    HighwayUtils.spleefBlockingEntities(positions);
                    if (HighwayBuilder.INSTANCE.toggleKekNuker.get()) {
                        for (BlockPos pos : positions) {
                            Block block = mc.world.getBlockState(pos).getBlock();
                            if (block != Blocks.NETHER_PORTAL
                                && block != Blocks.BEDROCK
                                && (HighwayBuilder.shouldReplaceCryingObsidian() || block != Blocks.CRYING_OBSIDIAN)
                                && HighwayUtils.needsMining(pos, HighwayBuilder.getPavementBlock())) {
                                KekNuker.blocksToMine.add(pos.toImmutable());
                            }
                        }

                        for (BlockPos posx : HighwayPositions.getDiagonalClearArea(2, 1)) {
                            Block block = mc.world.getBlockState(posx).getBlock();
                            if (block != Blocks.NETHER_PORTAL && block != Blocks.BEDROCK && HighwayUtils.needsMining(posx, Blocks.AIR)) {
                                KekNuker.blocksToMine.add(posx.toImmutable());
                            }
                        }
                    }

                    state.setPlacing(false);
                    HighwayUtils.placePositions(positions, false);
                    BlockPos[] ceilingPositions = HighwayPositions.getDiagonalCeiling(
                        2, 1, HighwayBuilder.shouldPlaceLeftRail(), HighwayBuilder.shouldPlaceRightRail()
                    );
                    if (HighwayBuilder.shouldFillCeiling()) {
                        for (BlockPos posxx : ceilingPositions) {
                            if (HighwayUtils.isInPlacementRange(posxx) && BlockUtils.canPlace(posxx, true)) {
                                PlayerControlUtils.setAutoWalk(false);
                            }
                        }

                        HighwayUtils.placePositions(ceilingPositions, false);
                    }

                    if (!state.isPlacing()
                        || !state.isInPlaceRange()
                        || !state.hasMissingBlock()
                        || !KekNuker.isMiningHighwayZone()
                        || !state.isPlacementBlocked()) {
                        PlayerControlUtils.setAutoWalk(true);
                    }
                } else {
                    stopBouncing();
                    PlayerControlUtils.setAutoWalk(false);
                }
            }
        }
    }

    public static void tickCardinalDig() {
        if (mc.player != null && mc.world != null) {
            HighwayState state = HighwayState.getInstance();
            if (!PlayerControlUtils.isGatheringItem()
                && !HighwayBuilder.isEating()
                && !InventoryManager.inventoryBusy
                && !HighwayBuilder.isKillAuraAttacking()
                && (!SourceRemover.isRemovingLava() || !Modules.get().get("source-remover").isActive())) {
                if (mc.player.getY() < state.getAnchorY().intValue()) {
                    PlayerControlUtils.setAutoWalk(false);
                }

                if (HighwayBuilder.useAdvancedSourceFiller()) {
                    HighwayUtils.handleLavaObstruction();
                }

                PlayerControlUtils.faceHighwayDirection();
                PlayerControlUtils.strafeToLaneCenter();
                HighwayUtils.checkCollisionAhead();
                if (HighwayBuilder.getScaffoldMode() != HighwayBuilder.ScaffoldMode.Disabled) {
                    if (InventoryManager.findStack(HighwayBuilder.getScaffoldBlock().asItem()) == null) {
                        state.setGatherReturnPos(mc.player.getBlockPos());
                        HighwayUtils.pathToNearestItem(HighwayBuilder.getScaffoldBlock().asItem());
                        return;
                    }

                    if (state.getGatherReturnPos() != null && InventoryManager.findStack(HighwayBuilder.getScaffoldBlock().asItem()) != null) {
                        BaritoneHelper.pathTo(state.getGatherReturnPos());
                    }

                    state.setGatherReturnPos(null);
                    if (BaritoneHelper.isPathing()) {
                        return;
                    }
                }

                if (HighwayBuilder.getScaffoldMode() == HighwayBuilder.ScaffoldMode.Full) {
                    HighwayUtils.placePositions(
                        HighwayPositions.getCardinalFloor(3, 2, HighwayBuilder.shouldScaffoldLeftRail(), HighwayBuilder.shouldScaffoldRightRail()), true
                    );
                }

                if (HighwayBuilder.getScaffoldMode() == HighwayBuilder.ScaffoldMode.Simple) {
                    HighwayUtils.scaffoldAhead();
                }

                for (BlockPos currentPos : HighwayPositions.getCardinalClearArea(3, 1)) {
                    BlockState blockState = mc.world.getBlockState(currentPos);
                    if (!(mc.world.getBlockState(currentPos).getBlock() instanceof AirBlock)
                        && blockState.getBlock() != Blocks.NETHER_PORTAL
                        && blockState.getBlock() != Blocks.BEDROCK
                        && HighwayUtils.needsMining(currentPos, Blocks.AIR)) {
                        KekNuker.blocksToMine.add(currentPos.toImmutable());
                    }
                }

                for (BlockPos currentPosx : HighwayPositions.getCardinalAreaBehind()) {
                    if (!(mc.world.getBlockState(currentPosx).getBlock() instanceof AirBlock) && HighwayStatsUtils.cachedDistanceTravelled > 5) {
                        boolean hasLavaNeighbor = false;

                        for (Direction direction : Direction.values()) {
                            BlockPos neighborPos = currentPosx.offset(direction);
                            FluidState neighborFluid = mc.world.getFluidState(neighborPos);
                            if (neighborFluid.getFluid() == Fluids.LAVA) {
                                hasLavaNeighbor = true;
                                break;
                            }
                        }

                        if (!hasLavaNeighbor) {
                            PlayerControlUtils.setAutoWalk(false);

                            GoalXZ goal = switch (HighwayBuilder.getDirection()) {
                                case NORTH, SOUTH -> new GoalXZ(state.getAnchorX(), currentPosx.getZ());
                                case EAST, WEST -> new GoalXZ(currentPosx.getX(), state.getAnchorZ());
                                default -> new GoalXZ(currentPosx.getX(), currentPosx.getZ());
                            };
                            BaritoneHelper.setGoalAndPath(goal);
                            return;
                        }
                    } else {
                        PlayerControlUtils.setAutoWalk(true);
                    }
                }
            } else {
                PlayerControlUtils.setAutoWalk(false);
            }
        }
    }

    public static void tickDiagonalDig() {
        if (mc.player != null && mc.world != null) {
            HighwayState state = HighwayState.getInstance();
            if (state.getStartPos() == null) {
                ChatUtils.error("Set player center using .center");
                PlayerControlUtils.disableHighwayBuilder();
            } else {
                double dx = mc.player.getBlockX() - state.getStartPos().getX();
                double dz = mc.player.getBlockZ() - state.getStartPos().getZ();
                if (Math.abs(dx) == Math.abs(dz)) {
                    state.setAnchorX(mc.player.getBlockX());
                    state.setAnchorZ(mc.player.getBlockZ());
                }

                List<Block> nukerBlacklist = KekNuker.getBlacklist();
                Map<String, Boolean> conditions = new LinkedHashMap<>();
                conditions.put("Gathering Items", PlayerControlUtils.isGatheringItem());
                conditions.put("Removing Lava", SourceRemover.isRemovingLava());
                conditions.put("Eating", HighwayBuilder.isEating());
                if (!HighwayBuilder.isEating()
                    && !PlayerControlUtils.isGatheringItem()
                    && !SourceRemover.isRemovingLava()
                    && !HighwayBuilder.isKillAuraAttacking()) {
                    if (HighwayBuilder.useAdvancedSourceFiller()) {
                        HighwayUtils.handleLavaObstruction();
                    }

                    PlayerControlUtils.faceHighwayDirection();
                    PlayerControlUtils.pathToDiagonalAxis();
                    HighwayUtils.checkCollisionAhead();
                    if (HighwayBuilder.getScaffoldMode() == HighwayBuilder.ScaffoldMode.Full) {
                        if (InventoryManager.findStack(Items.NETHERRACK) == null) {
                            HighwayUtils.pathToNearestItem(Items.NETHERRACK);
                        }

                        HighwayUtils.placePositions(
                            HighwayPositions.getDiagonalFloor(2, 1, HighwayBuilder.shouldPlaceLeftRail(), HighwayBuilder.shouldPlaceRightRail()), true
                        );
                    }

                    if (HighwayBuilder.getScaffoldMode() == HighwayBuilder.ScaffoldMode.Simple) {
                        if (InventoryManager.findStack(Items.NETHERRACK) == null) {
                            HighwayUtils.pathToNearestItem(Items.NETHERRACK);
                        }

                        HighwayUtils.scaffoldAhead();
                    }

                    if (!KekNuker.isMiningHighwayZone()) {
                        if (HighwayUtils.placeDiagonalSupport()) {
                            MusheorSystem.debug("Placing block so player doesn't fall...");
                            PlayerControlUtils.setAutoWalk(false);
                        } else {
                            for (BlockPos currentPos : HighwayPositions.getDiagonalClearArea(3, 1)) {
                                BlockState blockState = mc.world.getBlockState(currentPos);
                                if (!(mc.world.getBlockState(currentPos).getBlock() instanceof AirBlock)
                                    && blockState.getBlock() != Blocks.NETHER_PORTAL
                                    && blockState.getBlock() != Blocks.BEDROCK
                                    && HighwayUtils.needsMining(currentPos, Blocks.AIR)) {
                                    KekNuker.blocksToMine.add(currentPos.toImmutable());
                                }
                            }

                            for (BlockPos currentPosx : HighwayPositions.getDiagonalClearArea(-2, 5)) {
                                if (!(mc.world.getBlockState(currentPosx).getBlock() instanceof AirBlock) && HighwayStatsUtils.cachedDistanceTravelled > 5) {
                                    boolean hasLavaNeighbor = false;

                                    for (Direction direction : Direction.values()) {
                                        BlockPos neighborPos = currentPosx.offset(direction);
                                        FluidState neighborFluid = mc.world.getFluidState(neighborPos);
                                        if (neighborFluid.getFluid() == Fluids.LAVA) {
                                            hasLavaNeighbor = true;
                                            break;
                                        }
                                    }

                                    if (!hasLavaNeighbor && !nukerBlacklist.contains(mc.world.getBlockState(currentPosx).getBlock())) {
                                        MusheorSystem.debug(
                                            "Detected obstruction behind player... "
                                                + Registries.BLOCK.getId(mc.world.getBlockState(currentPosx).getBlock()).getPath()
                                                + "at %s %s %s",
                                            currentPosx.getX(),
                                            currentPosx.getY(),
                                            currentPosx.getZ()
                                        );
                                        PlayerControlUtils.setAutoWalk(false);
                                        BaritoneHelper.setGoalAndPath(new GoalXZ(currentPosx.getX(), currentPosx.getX()));
                                        return;
                                    }
                                } else {
                                    PlayerControlUtils.setAutoWalk(true);
                                }
                            }
                        }
                    }
                } else {
                    PlayerControlUtils.setAutoWalk(false);

                    for (Entry<String, Boolean> entry : conditions.entrySet()) {
                        if (entry.getValue()) {
                            MusheorSystem.debug(entry.getKey() + " was triggered.");
                        }
                    }
                }
            }
        }
    }

    private static boolean isNearCorner(HighwayDetector.DetectedHighway d, int px, int pz) {
        double D = Math.abs(d.axisValue);
        double K = d.axisValue;
        switch (d.direction) {
            case NORTH:
                return pz <= -D + 2.0;
            case EAST:
                return px >= D - 2.0;
            case SOUTH:
                return pz >= D - 2.0;
            case WEST:
                return px <= -D + 2.0;
            case NORTH_EAST:
                return K > 0.0 ? pz <= 2 : px >= -2;
            case NORTH_WEST:
                return K > 0.0 ? px <= 2 : pz <= 2;
            case SOUTH_EAST:
                return K > 0.0 ? pz >= -2 : px >= -2;
            case SOUTH_WEST:
                return K > 0.0 ? px <= 2 : pz >= -2;
            default:
                return false;
        }
    }

    private static int[] getCornerPos(HighwayDetector.DetectedHighway d) {
        int D = (int)Math.abs(d.axisValue);
        int K = (int)d.axisValue;
        int ax = (int)d.axisValue;
        switch (d.direction) {
            case NORTH:
                return new int[]{ax, -D};
            case EAST:
                return new int[]{D, ax};
            case SOUTH:
                return new int[]{ax, D};
            case WEST:
                return new int[]{-D, ax};
            case NORTH_EAST:
                return K > 0 ? new int[]{K, 0} : new int[]{0, K};
            case NORTH_WEST:
                return K > 0 ? new int[]{0, -K} : new int[]{K, 0};
            case SOUTH_EAST:
                return K > 0 ? new int[]{K, 0} : new int[]{0, -K};
            case SOUTH_WEST:
                return K > 0 ? new int[]{0, K} : new int[]{K, 0};
            default:
                return null;
        }
    }

    private static HighwayUtils.HighwayDirection getNextLegDirection(HighwayDetector.DetectedHighway d) {
        boolean pos = d.axisValue > 0.0;
        switch (d.direction) {
            case NORTH:
            case SOUTH:
                return pos ? HighwayUtils.HighwayDirection.WEST : HighwayUtils.HighwayDirection.EAST;
            case EAST:
            case WEST:
                return pos ? HighwayUtils.HighwayDirection.NORTH : HighwayUtils.HighwayDirection.SOUTH;
            case NORTH_EAST:
            case SOUTH_WEST:
                return pos ? HighwayUtils.HighwayDirection.NORTH_WEST : HighwayUtils.HighwayDirection.SOUTH_EAST;
            case NORTH_WEST:
            case SOUTH_EAST:
                return pos ? HighwayUtils.HighwayDirection.SOUTH_WEST : HighwayUtils.HighwayDirection.NORTH_EAST;
            default:
                return null;
        }
    }

    private static boolean handleCornerTurn(HighwayDetector.DetectedHighway detected, HighwayState state) {
        assert mc.player != null;

        int px = mc.player.getBlockX();
        int pz = mc.player.getBlockZ();
        if (state.getAnchorY() == null) {
            return false;
        } else {
            int py = state.getAnchorY();
            if (!isNearCorner(detected, px, pz)) {
                return false;
            } else {
                int[] corner = getCornerPos(detected);
                if (corner == null) {
                    return false;
                } else {
                    int cx = corner[0];
                    int cz = corner[1];
                    double dist = Math.hypot(px - cx, pz - cz);
                    if (dist > 1.0) {
                        PlayerControlUtils.setAutoWalk(false);
                        BaritoneHelper.setGoalAndPath(new GoalBlock(cx, py, cz));
                        return true;
                    } else {
                        HighwayUtils.HighwayDirection nextDir = getNextLegDirection(detected);
                        if (nextDir == null) {
                            return false;
                        } else {
                            HighwayDetector.DetectedHighway newDetected = HighwayDetector.detect(mc.player.getBlockX(), mc.player.getBlockZ(), py, nextDir);
                            if (newDetected == null) {
                                return false;
                            } else {
                                state.setDetectedHighway(newDetected);
                                state.setDirection(newDetected.direction);
                                state.setStartX(newDetected.startX);
                                state.setStartZ(newDetected.startZ);
                                state.setAlignX(newDetected.alignX);
                                state.setAlignZ(newDetected.alignZ);
                                state.setAnchorX(mc.player.getBlockX());
                                state.setAnchorZ(mc.player.getBlockZ());
                                PlayerControlUtils.faceHighwayDirection();
                                return true;
                            }
                        }
                    }
                }
            }
        }
    }

    public static boolean isBouncing() {
        return bouncing;
    }

    private static boolean isClearAhead(boolean isCardinal, int distance) {
        if (mc.world == null) {
            return false;
        } else {
            List<Block> blacklist = KekNuker.getBlacklist();
            BlockPos[] floor = isCardinal
                ? HighwayPositions.getCardinalFloor(distance, 1, HighwayBuilder.shouldPlaceLeftRail(), HighwayBuilder.shouldPlaceRightRail())
                : HighwayPositions.getDiagonalFloor(distance, 1, HighwayBuilder.shouldPlaceLeftRail(), HighwayBuilder.shouldPlaceRightRail());

            for (BlockPos pos : floor) {
                Block block = mc.world.getBlockState(pos).getBlock();
                if (!blacklist.contains(block)
                    && (HighwayBuilder.shouldReplaceCryingObsidian() || block != Blocks.CRYING_OBSIDIAN)
                    && block != Blocks.BEDROCK
                    && block != HighwayBuilder.getPavementBlock()) {
                    return false;
                }
            }

            BlockPos[] tunnel = isCardinal ? HighwayPositions.getCardinalClearArea(distance, 1) : HighwayPositions.getDiagonalClearArea(distance, 1);

            for (BlockPos posx : tunnel) {
                Block block = mc.world.getBlockState(posx).getBlock();
                if (!blacklist.contains(block) && block != Blocks.BEDROCK && block != Blocks.NETHER_PORTAL && !mc.world.getBlockState(posx).isAir()) {
                    return false;
                }
            }

            return true;
        }
    }

    private static boolean isNearLegEnd(HighwayDetector.DetectedHighway d) {
        if (mc.player == null) {
            return false;
        } else {
            int px = mc.player.getBlockX();
            int pz = mc.player.getBlockZ();
            double D = Math.abs(d.axisValue);
            double K = d.axisValue;
            int m = 32;

            return switch (d.direction) {
                case NORTH -> pz <= -D + m;
                case EAST -> px >= D - m;
                case SOUTH -> pz >= D - m;
                case WEST -> px <= -D + m;
                case NORTH_EAST -> K > 0.0 ? pz <= m : px >= -m;
                case NORTH_WEST -> K > 0.0 ? px <= m : pz <= m;
                case SOUTH_EAST -> K > 0.0 ? pz >= -m : px >= -m;
                case SOUTH_WEST -> K > 0.0 ? px <= m : pz >= -m;
                default -> false;
            };
        }
    }

    private static void configureKekBounce() {
        PlayerControlUtils.setModuleSetting(KekBounce.class, "mode", KekBounce.Mode.Simple);
        PlayerControlUtils.setModuleSetting(KekBounce.class, "y-motion", false);
        if (mc.player != null) {
            PlayerControlUtils.setModuleSetting(KekBounce.class, "y-level", mc.player.getBlockY());
        }
    }

    private static void startBouncing() {
        if (!bouncing) {
            if (mc.player != null) {
                boolean hasElytra = false;

                for (int i = 0; i < mc.player.getInventory().size(); i++) {
                    if (mc.player.getInventory().getStack(i).getItem() == Items.ELYTRA) {
                        hasElytra = true;
                        break;
                    }
                }

                if (hasElytra) {
                    configureKekBounce();
                    Module kekBounce = Modules.get().get(KekBounce.class);
                    if (!kekBounce.isActive()) {
                        kekBounce.toggle();
                    }

                    bouncing = true;
                    bounceCooldown = 0;
                }
            }
        }
    }

    public static void stopBouncing() {
        if (bouncing) {
            Module kekBounce = Modules.get().get(KekBounce.class);
            if (kekBounce.isActive()) {
                kekBounce.toggle();
            }

            bouncing = false;
            bounceCooldown = 20;
        }
    }

    public static void reset() {
        bouncing = false;
        bounceCooldown = 0;
        recoveringFromFall = false;
        offHighwayTicks = 0;
        repathCooldown = 0;
    }

    public static boolean handleFallRecovery(HighwayState state) {
        if (mc.player == null || mc.world == null) {
            return false;
        } else if (state.getAnchorY() != null && state.getDirection() != null) {
            Module echestFarmer = Modules.get().get(EchestFarmer.class);
            if (!echestFarmer.isActive()
                && !PlayerControlUtils.isGatheringItem()
                && !SourceRemover.isRemovingLava()
                && !InventoryManager.inventoryBusy
                && !state.isOutOfMaterials()
                && !state.isEchestRestocking()
                && !state.isShulkerRestocking()
                && !state.isPostRestocking()) {
                if ((bouncing || bounceCooldown > 0) && !recoveringFromFall) {
                    offHighwayTicks = 0;
                    return false;
                } else {
                    int targetY = state.getAnchorY();
                    boolean atCorrectY = mc.player.getBlockY() == targetY;
                    if (!recoveringFromFall) {
                        if (atCorrectY) {
                            offHighwayTicks = 0;
                            return false;
                        }

                        if (++offHighwayTicks < 15) {
                            return false;
                        }

                        recoveringFromFall = true;
                        repathCooldown = 0;
                        stopBouncing();
                        PlayerControlUtils.setAutoWalk(false);
                        MusheorSystem.debug("Fell off the highway (Y %s != %s) — pathing back...", mc.player.getBlockY(), targetY);
                    }

                    if (atCorrectY && mc.player.isOnGround()) {
                        recoveringFromFall = false;
                        offHighwayTicks = 0;
                        if (BaritoneHelper.isPathing()) {
                            BaritoneHelper.cancelAll();
                        }

                        MusheorSystem.debug("Back on the highway — resuming paving.");
                        return false;
                    } else {
                        if (repathCooldown <= 0) {
                            pathBackToHighway(state, targetY);
                            repathCooldown = 40;
                        } else {
                            repathCooldown--;
                        }

                        return true;
                    }
                }
            } else {
                offHighwayTicks = 0;
                recoveringFromFall = false;
                return false;
            }
        } else {
            return false;
        }
    }

    private static void pathBackToHighway(HighwayState state, int targetY) {
        BlockPos nearest = findNearestPavementSpot(targetY, 24);
        if (nearest != null) {
            BaritoneHelper.setGoalAndPath(new GoalBlock(nearest));
        } else {
            if (state.getAnchorX() != null && state.getAnchorZ() != null) {
                BaritoneHelper.setGoalAndPath(new GoalBlock(state.getAnchorX(), targetY, state.getAnchorZ()));
            }
        }
    }

    private static BlockPos findNearestPavementSpot(int targetY, int range) {
        if (mc.player != null && mc.world != null) {
            BlockPos p = mc.player.getBlockPos();
            Block pavement = HighwayBuilder.getPavementBlock();
            BlockPos best = null;
            double bestDistSq = Double.MAX_VALUE;

            for (int x = -range; x <= range; x++) {
                for (int z = -range; z <= range; z++) {
                    BlockPos stand = new BlockPos(p.getX() + x, targetY, p.getZ() + z);
                    if (mc.world.getBlockState(stand.down()).getBlock() == pavement
                        && mc.world.getBlockState(stand).isAir()
                        && mc.world.getBlockState(stand.up()).isAir()) {
                        double distSq = p.getSquaredDistance(stand);
                        if (distSq < bestDistSq) {
                            bestDistSq = distSq;
                            best = stand;
                        }
                    }
                }
            }

            return best;
        } else {
            return null;
        }
    }

    public static void tickAutoPave() {
        assert mc.player != null && mc.world != null;

        HighwayState state = HighwayState.getInstance();
        if (state.getAnchorY() != null && state.getDirection() != null) {
            HighwayDetector.DetectedHighway detected = state.getDetectedHighway();
            if (detected != null) {
                if (!handleFallRecovery(state)) {
                    boolean isCardinal = detected.type == HighwayBuilder.HighwayType.Cardinal;
                    boolean isRingOrDiamond = detected.category == HighwayDetector.Category.RING || detected.category == HighwayDetector.Category.DIAMOND;
                    if (isRingOrDiamond && handleCornerTurn(detected, state)) {
                        stopBouncing();
                    }

                    Module betterEChestFarmer = Modules.get().get(EchestFarmer.class);
                    if (!betterEChestFarmer.isActive() && !PlayerControlUtils.isGatheringItem() && !SourceRemover.isRemovingLava()) {
                        if (KekNuker.isMiningHighwayZone()
                            || HighwayBuilder.isEating()
                            || HighwayBuilder.isKillAuraAttacking()
                            || InventoryManager.inventoryBusy) {
                            stopBouncing();
                            PlayerControlUtils.setAutoWalk(false);
                        }

                        if (HighwayBuilder.useAdvancedSourceFiller()) {
                            HighwayUtils.handleLavaObstruction();
                        }

                        if (HighwayBuilder.isAutoBounceEnabled()) {
                            if (bounceCooldown > 0) {
                                bounceCooldown--;
                                return;
                            }

                            boolean nearLegEnd = isRingOrDiamond && isNearLegEnd(detected);
                            boolean interrupted = KekNuker.isMiningHighwayZone()
                                || HighwayBuilder.isEating()
                                || HighwayBuilder.isKillAuraAttacking()
                                || InventoryManager.inventoryBusy
                                || PlayerControlUtils.isGatheringItem()
                                || SourceRemover.isRemovingLava()
                                || betterEChestFarmer.isActive()
                                || BaritoneHelper.isPathing();
                            if (!nearLegEnd && !interrupted && isClearAhead(isCardinal, HighwayBuilder.getBounceDistanceCheck())) {
                                startBouncing();
                            } else {
                                stopBouncing();
                            }

                            if (bouncing) {
                                PlayerControlUtils.faceHighwayYaw();
                                PlayerControlUtils.setAutoWalk(false);
                                return;
                            }
                        }

                        if (isCardinal) {
                            HighwayUtils.checkCollisionAhead();
                            PlayerControlUtils.faceHighwayDirection();
                            PlayerControlUtils.strafeToLaneCenter();
                        } else {
                            boolean isNESW = state.getDirection() == HighwayUtils.HighwayDirection.NORTH_EAST
                                || state.getDirection() == HighwayUtils.HighwayDirection.SOUTH_WEST;
                            int playerDiagVal = isNESW ? mc.player.getBlockX() + mc.player.getBlockZ() : mc.player.getBlockX() - mc.player.getBlockZ();
                            if (playerDiagVal == (int)detected.axisValue) {
                                state.setAnchorX(mc.player.getBlockX());
                                state.setAnchorZ(mc.player.getBlockZ());
                            }

                            PlayerControlUtils.pathToDiagonalAxis();
                            HighwayUtils.checkCollisionAhead();
                            PlayerControlUtils.faceHighwayDirection();
                        }

                        BlockPos[] allPositions = isCardinal
                            ? HighwayPositions.getCardinalFloor(
                                MusheorSystem.Manager.highwayForwardRange.get(), 2, HighwayBuilder.shouldPlaceLeftRail(), HighwayBuilder.shouldPlaceRightRail()
                            )
                            : HighwayPositions.getDiagonalFloor(
                                MusheorSystem.Manager.highwayForwardRange.get(), 2, HighwayBuilder.shouldPlaceLeftRail(), HighwayBuilder.shouldPlaceRightRail()
                            );
                        List<BlockPos> placePositions = new ArrayList<>();
                        List<BlockPos> clearPositions = new ArrayList<>();

                        for (BlockPos pos : allPositions) {
                            if (pos.getY() != state.getAnchorY() || !HighwayDetector.isOnCrossingHighway(pos, detected)) {
                                placePositions.add(pos);
                            } else if (HighwayDetector.isInsideHighway(pos, detected)) {
                                clearPositions.add(pos);
                            }
                        }

                        if (HighwayBuilder.INSTANCE.toggleKekNuker.get()) {
                            KekNuker.blocksToMine.clear();
                        }

                        state.setPlacementBlocked(false);
                        HighwayUtils.spleefBlockingEntities(allPositions);
                        if (HighwayBuilder.INSTANCE.toggleKekNuker.get()) {
                            for (BlockPos posx : placePositions) {
                                Block block = mc.world.getBlockState(posx).getBlock();
                                if (block != Blocks.NETHER_PORTAL
                                    && block != Blocks.BEDROCK
                                    && (HighwayBuilder.shouldReplaceCryingObsidian() || block != Blocks.CRYING_OBSIDIAN)
                                    && HighwayUtils.needsMining(posx, HighwayBuilder.getPavementBlock())) {
                                    KekNuker.blocksToMine.add(posx.toImmutable());
                                }
                            }

                            BlockPos[] tunnelClear = isCardinal
                                ? HighwayPositions.getCardinalClearArea(MusheorSystem.Manager.highwayForwardRange.get(), 1)
                                : HighwayPositions.getDiagonalClearArea(MusheorSystem.Manager.highwayForwardRange.get(), 1);

                            for (BlockPos posxx : tunnelClear) {
                                Block block = mc.world.getBlockState(posxx).getBlock();
                                if (block != Blocks.NETHER_PORTAL && block != Blocks.BEDROCK && HighwayUtils.needsMining(posxx, Blocks.AIR)) {
                                    KekNuker.blocksToMine.add(posxx.toImmutable());
                                }
                            }

                            for (BlockPos posxxx : clearPositions) {
                                Block block = mc.world.getBlockState(posxxx).getBlock();
                                if (block != Blocks.NETHER_PORTAL && block != Blocks.BEDROCK && HighwayUtils.needsMining(posxxx, Blocks.AIR)) {
                                    KekNuker.blocksToMine.add(posxxx.toImmutable());
                                }
                            }
                        }

                        state.setPlacing(false);
                        HighwayUtils.placePositions(placePositions.toArray(new BlockPos[0]), false);
                        if (HighwayBuilder.shouldFillCeiling()) {
                            BlockPos[] ceilingPositions = isCardinal
                                ? HighwayPositions.getCardinalCeiling(
                                    MusheorSystem.Manager.highwayForwardRange.get(),
                                    2,
                                    HighwayBuilder.shouldPlaceLeftRail(),
                                    HighwayBuilder.shouldPlaceRightRail()
                                )
                                : HighwayPositions.getDiagonalCeiling(
                                    MusheorSystem.Manager.highwayForwardRange.get(),
                                    1,
                                    HighwayBuilder.shouldPlaceLeftRail(),
                                    HighwayBuilder.shouldPlaceRightRail()
                                );
                            HighwayUtils.placePositions(ceilingPositions, false);
                        }

                        if (!state.isPlacing()
                            || !state.isInPlaceRange()
                            || !HighwayUtils.hasUnplacedBlocks()
                            || !state.hasMissingBlock()
                            || !KekNuker.isMiningHighwayZone()
                            || !state.isPlacing()) {
                            PlayerControlUtils.setAutoWalk(true);
                        }
                    } else {
                        stopBouncing();
                        PlayerControlUtils.setAutoWalk(false);
                    }
                }
            }
        }
    }
}
