package musheor.modules.automation;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.selection.ISelection;
import baritone.api.selection.ISelectionManager;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import musheor.musheor;
import musheor.compat.LitematicaHelper;
import musheor.modules.features.KekMine;
import musheor.modules.hud.HudInfoPlus;
import musheor.utils.BlockRenderUtils;
import musheor.utils.HighwayPositions;
import musheor.utils.HighwayUtils;
import musheor.utils.baritone.BaritoneHelper;
import musheor.utils.highway.HighwayDetector;
import musheor.utils.highway.HighwayState;
import musheor.utils.system.MusheorSystem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.BlockPos.Mutable;

public class KekNuker extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgPathfind = this.settings.createGroup("Pathfind");
    private final SettingGroup sgRender = this.settings.createGroup("Render");
    private static final MinecraftClient client = MinecraftClient.getInstance();
    public static KekNuker INSTANCE;
    public final Setting<KekNuker.NukerMode> nukerMode = this.sgGeneral
        .add(new Builder<KekNuker.NukerMode>().name("nuker-mode").defaultValue(KekNuker.NukerMode.Normal).build());
    private final Setting<KekNuker.LayerType> layerType = this.sgGeneral
        .add(
            new Builder<KekNuker.LayerType>().name("layer-type")
                            .description("Controls where blocks are allowed to be broken (layer handling)")
                        .defaultValue(KekNuker.LayerType.Flatten)
                    .visible(() -> this.nukerMode.get() != KekNuker.NukerMode.Highway)
                .build()
        );
    private final Setting<KekNuker.SortMode> sortMode = this.sgGeneral
        .add(
            new Builder<KekNuker.SortMode>().name("sort-mode").description("Choose what order to mine the blocks in")
                    .defaultValue(KekNuker.SortMode.Efficient)
                .build()
        );
    public final Setting<KekNuker.Shape> shape = this.sgGeneral
        .add(
            new Builder<KekNuker.Shape>().name("shape").defaultValue(KekNuker.Shape.Sphere)
                    .visible(() -> this.nukerMode.get() != KekNuker.NukerMode.Highway)
                .build()
        );
    public final Setting<Double> range = this.sgGeneral
        .add(new meteordevelopment.meteorclient.settings.DoubleSetting.Builder().name("range").defaultValue(5.5).min(2.0).max(8.0).decimalPlaces(1).build());
    private final Setting<Boolean> belowFeetLast = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("below-feet-last")
                .description("Makes it so the block you are standing on is only mined when all other blocks in range are broken")
                .defaultValue(false)
                .visible(() -> this.nukerMode.get() != KekNuker.NukerMode.Highway)
                .build()
        );
    private final Setting<Boolean> ignoreAir = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("ignore-air")
                .description("Ignores air blocks in the schematic.")
                .visible(() -> LitematicaHelper.isLoaded() && this.nukerMode.get() == KekNuker.NukerMode.Litematica)
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> avoidSpillingLiquids = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("avoid-spilling-liquids")
                .description("Prevents you from mining blocks that will cause liquid to flow everywhere")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> preventPacketKick = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("prevent-packet-kick")
                .description("+100000 aura setting")
                .defaultValue(true)
                .build()
        );
    public final Setting<Integer> blocksPerTick = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("blocks-per-tick")
                .description("Maximum blocks to try to break per tick. (Only for instant-breakable-blocks)")
                .defaultValue(35)
                .min(1)
                .sliderRange(1, 100)
                .build()
        );
    private final Setting<KekNuker.ListMode> listMode = this.sgGeneral
        .add(new Builder<KekNuker.ListMode>().name("list-mode").description("Selection mode").defaultValue(KekNuker.ListMode.Disabled).build());
    private final Setting<List<Block>> whitelist = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BlockListSetting.Builder()
                .name("whitelist")
                .description("The blocks you want to mine.")
                .defaultValue()
                .visible(() -> this.listMode.get() == KekNuker.ListMode.Whitelist)
                .build()
        );
    private final Setting<List<Block>> blacklist = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BlockListSetting.Builder()
                .name("blacklist")
                .description("The blocks you don't want to mine.")
                .defaultValue()
                .visible(() -> this.listMode.get() == KekNuker.ListMode.Blacklist)
                .build()
        );
    private final Setting<Boolean> autoPathfind = this.sgPathfind
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("auto-pathfind")
                .description("Automatically pathfinds when no blocks are in range")
                .defaultValue(false)
                .visible(() -> this.nukerMode.get() != KekNuker.NukerMode.Highway)
                .build()
        );
    private final Setting<Integer> pathfindChunkRange = this.sgPathfind
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("pathfind-chunk-range")
                .description("Maximum range in chunks the pathfinder can scan for more blocks to break")
                .defaultValue(4)
                .sliderMax(8)
                .visible(() -> this.nukerMode.get() != KekNuker.NukerMode.Highway)
                .build()
        );
    private final Setting<Integer> chunksPerTick = this.sgPathfind
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("chunks-per-tick")
                .description("How many chunks to scan per tick when searching for more blocks. Higher values find targets faster at the cost of CPU per tick.")
                .defaultValue(2)
                .min(1)
                .sliderMax(8)
                .visible(() -> this.nukerMode.get() != KekNuker.NukerMode.Highway)
                .build()
        );
    private final Setting<Boolean> globalRendering = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("global-rendering")
                .defaultValue(true)
                .description("Synchronize rendering with Musheor-Tab")
                .build()
        );
    private final Setting<SettingColor> renderColor = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("color")
                .defaultValue(new SettingColor(Color.cyan))
                .description("Custom color for rendering (lines / wireframe)")
                .visible(() -> !this.globalRendering.get())
                .build()
        );
    public static List<BlockPos> blocksToMine = new ArrayList<>();
    private static List<BlockPos> validTargets = new ArrayList<>();
    public static final Map<BlockPos, Integer> recentlyMined = new HashMap<>();
    private int tickCounter;
    private int scanRadius = -1;
    private int scanIndex = 0;
    private BlockPos pathTarget = null;
    private BlockPos bestCandidate = null;
    private boolean hadTargets = false;

    public KekNuker() {
        super(musheor.AUTOMATION, "kek-nuker", "Mines blocks in radius of the player");
        INSTANCE = this;
    }

    public static boolean isMining() {
        return !validTargets.isEmpty() && INSTANCE.isActive() || !KekMine.INSTANCE.isIdle();
    }

    public static List<Block> getBlacklist() {
        return INSTANCE.blacklist.get();
    }

    @Override
    public void onActivate() {
        recentlyMined.clear();
        this.tickCounter = 0;
        this.scanRadius = -1;
        this.scanIndex = 0;
        this.pathTarget = null;
        this.bestCandidate = null;
        this.hadTargets = false;
    }

    @Override
    public void onDeactivate() {
        blocksToMine.clear();
        validTargets.clear();
        KekMine.INSTANCE.breakQueue.clear();
        this.pathTarget = null;
        this.bestCandidate = null;
        this.scanRadius = -1;
        if (this.autoPathfind.get() && (BaritoneHelper.isPathing() || BaritoneHelper.hasPath())) {
            BaritoneHelper.cancelAll();
        }
    }

    @EventHandler
    public void onTick(Pre event) {
        if (client.player != null && client.world != null) {
            if (!HighwayBuilder.isEating()) {
                this.tickCounter++;
                recentlyMined.entrySet()
                    .removeIf(
                        entry -> client.world.getBlockState(entry.getKey()).isAir()
                            ? true
                            : this.tickCounter - entry.getValue() > KekMine.INSTANCE.breakTimeout.get()
                    );
                Module kekMine = Modules.get().get(KekMine.class);
                if (this.nukerMode.get() == KekNuker.NukerMode.Normal) {
                    blocksToMine.clear();
                    blocksToMine = getBlocksInShape(this.shape.get(), this.range.get());
                } else if (this.nukerMode.get() == KekNuker.NukerMode.Litematica) {
                    if (!LitematicaHelper.isLoaded()) {
                        this.warning("Litematica is not installed. Install Litematica or switch to another mode.", new Object[0]);
                        this.toggle();
                        return;
                    }

                    blocksToMine.clear();
                    blocksToMine.addAll(LitematicaHelper.get().getWrongSchematicBlocks(this.range.get(), this.ignoreAir.get()));
                } else if (this.nukerMode.get() == KekNuker.NukerMode.BaritoneSelection) {
                    blocksToMine.clear();
                    blocksToMine.addAll(this.getSelectionBlocks());
                }

                validTargets.clear();

                for (BlockPos pos : blocksToMine) {
                    BlockState state = client.world.getBlockState(pos);
                    if ((
                            this.nukerMode.get() == KekNuker.NukerMode.Highway
                                || (this.layerType.get() != KekNuker.LayerType.Flatten || pos.getY() >= client.player.getBlockPos().getY())
                                    && (this.layerType.get() != KekNuker.LayerType.LitematicaLayers || LitematicaHelper.get().isPositionInRenderLayer(pos))
                        )
                        && !state.isLiquid()
                        && !state.isAir()
                        && BlockUtils.canBreak(pos)
                        && (!this.avoidSpillingLiquids.get() || !HighwayUtils.isNextToLiquid(pos))
                        && !(squaredDistanceToBlock(client.player.getEyePos(), pos) > this.range.get() * this.range.get())
                        && (
                            this.listMode.get() == KekNuker.ListMode.Disabled
                                || (this.listMode.get() != KekNuker.ListMode.Whitelist || this.whitelist.get().contains(state.getBlock()))
                                    && (this.listMode.get() != KekNuker.ListMode.Blacklist || !this.blacklist.get().contains(state.getBlock()))
                        )) {
                        validTargets.add(pos);
                    }
                }

                this.sortTargets(validTargets);
                if (this.belowFeetLast.get()) {
                    BlockPos p = client.player.getBlockPos();
                    List<BlockPos> below = validTargets.stream()
                        .filter(posx -> posx.getX() == p.getX() && posx.getZ() == p.getZ() && posx.getY() < p.getY())
                        .sorted(Comparator.comparingInt(Vec3i::getY).reversed())
                        .toList();
                    validTargets.removeAll(below);
                    validTargets.addAll(below);
                }

                if (validTargets.isEmpty() && this.autoPathfind.get() && this.nukerMode.get() != KekNuker.NukerMode.Highway) {
                    if (this.hadTargets) {
                        this.hadTargets = false;
                        this.scanRadius = -1;
                        this.scanIndex = 0;
                        this.pathTarget = null;
                        this.bestCandidate = null;
                    }

                    if (this.pathTarget != null) {
                        if (HighwayUtils.isInRange(this.pathTarget, this.range.get())) {
                            BaritoneHelper.cancelAll();
                            this.pathTarget = null;
                            this.bestCandidate = null;
                            this.scanRadius = 0;
                            this.scanIndex = 0;
                        } else if (!BaritoneHelper.isPathing()) {
                            BlockPos standPos = this.findStandPos(this.pathTarget);
                            if (standPos == null) {
                                this.pathTarget = null;
                                this.bestCandidate = null;
                            } else {
                                BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalNear(standPos, 1));
                            }
                        }
                    } else {
                        if (this.scanRadius == -1) {
                            this.scanRadius = 0;
                            this.scanIndex = 0;
                        }

                        int maxChunkRadius = this.pathfindChunkRange.get();
                        ChunkPos playerChunk = client.player.getChunkPos();

                        for (int i = 0; i < this.chunksPerTick.get() && this.scanRadius <= maxChunkRadius && this.pathTarget == null; i++) {
                            ChunkPos toScan = this.getRingChunk(playerChunk, this.scanRadius, this.scanIndex);
                            BlockPos candidate = this.findClosestTargetInChunk(toScan);
                            if (candidate != null
                                && (
                                    this.bestCandidate == null
                                        || client.player.squaredDistanceTo(candidate.getX() + 0.5, candidate.getY() + 0.5, candidate.getZ() + 0.5)
                                            < client.player
                                                .squaredDistanceTo(
                                                    this.bestCandidate.getX() + 0.5, this.bestCandidate.getY() + 0.5, this.bestCandidate.getZ() + 0.5
                                                )
                                )) {
                                this.bestCandidate = candidate;
                            }

                            int ringSize = this.scanRadius == 0 ? 1 : 8 * this.scanRadius;
                            this.scanIndex++;
                            if (this.scanIndex >= ringSize) {
                                this.scanIndex = 0;
                                this.scanRadius++;
                                if (this.bestCandidate != null) {
                                    BlockPos standPos = this.findStandPos(this.bestCandidate);
                                    if (standPos != null) {
                                        this.pathTarget = this.bestCandidate;
                                        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalNear(standPos, 1));
                                    }

                                    this.bestCandidate = null;
                                }
                            }
                        }
                    }
                }

                if (!validTargets.isEmpty()) {
                    this.hadTargets = true;
                    int count = 0;

                    for (BlockPos posx : validTargets) {
                        BlockState state = client.world.getBlockState(posx);
                        if (HudInfoPlus.getGlobalPacketCount() >= MusheorSystem.Manager.globalPacketLimit.get().intValue() * 0.9
                            && this.preventPacketKick.get()) {
                            break;
                        }

                        if (!recentlyMined.containsKey(posx)) {
                            KekMine.MineContext ctx = new KekMine.MineContext(posx, client.world.getBlockState(posx), true);
                            if (kekMine.isActive()) {
                                if (!KekMine.INSTANCE.hasOnlyQueued()) {
                                    Direction face = getClosestFace(client.player.getEyePos(), posx);
                                    if (ctx.instant) {
                                        KekMine.instaMine(posx, face, false);
                                        count++;
                                    } else {
                                        KekMine.INSTANCE.startMining(posx, state, face, false);
                                    }

                                    recentlyMined.put(posx, this.tickCounter);
                                }
                            } else {
                                KekMine.INSTANCE.swapToBestTool(posx);
                                BlockUtils.breakBlock(posx, true);
                                count++;
                                if (!BlockUtils.canInstaBreak(posx)) {
                                    break;
                                }
                            }

                            if (count >= this.blocksPerTick.get()) {
                                break;
                            }
                        }
                    }

                    if (kekMine.isActive() && !KekMine.INSTANCE.breakQueue.isEmpty()) {
                        List<BlockPos> queueList = new ArrayList<>(KekMine.INSTANCE.breakQueue);
                        this.sortTargets(queueList);
                        if (this.belowFeetLast.get()) {
                            BlockPos p = client.player.getBlockPos();
                            List<BlockPos> below = queueList.stream()
                                .filter(posx -> posx.getX() == p.getX() && posx.getZ() == p.getZ() && posx.getY() < p.getY())
                                .sorted(Comparator.comparingInt(Vec3i::getY).reversed())
                                .toList();
                            queueList.removeAll(below);
                            queueList.addAll(below);
                        }

                        KekMine.INSTANCE.breakQueue.clear();
                        KekMine.INSTANCE.breakQueue.addAll(queueList);
                    }
                }
            }
        }
    }

    private void sortTargets(List<BlockPos> list) {
        switch ((KekNuker.SortMode)this.sortMode.get()) {
            case Closest:
                list.sort(Comparator.comparingDouble(p -> client.player.squaredDistanceTo(Vec3d.of(p))));
                break;
            case Furthest:
                list.sort(Comparator.comparingDouble(p -> -client.player.squaredDistanceTo(Vec3d.of(p))));
                break;
            case TopDown:
                list.sort(Comparator.comparingDouble(p -> -p.getY()));
                break;
            case BottomUp:
                list.sort(Comparator.comparingDouble(Vec3i::getY));
                break;
            case Efficient:
                this.sortEfficient(list);
            case null, default:
        }
    }

    private void sortEfficient(List<BlockPos> list) {
        if (client.player != null && client.world != null && !list.isEmpty()) {
            Map<BlockPos, KekNuker.EffKey> keys = new HashMap<>();

            for (BlockPos pos : list) {
                BlockState state = client.world.getBlockState(pos);
                boolean toolMatch = KekMine.isHoldingEffectiveTool(state);
                KekMine.MineContext ctx = new KekMine.MineContext(pos, state, true);
                boolean insta = ctx.instant;
                keys.put(pos, new KekNuker.EffKey(toolMatch, insta, ctx.getBreakDelta()));
            }

            list.sort(
                Comparator.<BlockPos>comparingInt(p -> keys.get(p).insta() ? 0 : 1)
                    .thenComparingInt(p -> keys.get(p).toolMatch() ? 0 : 1)
                    .thenComparingDouble(p -> -keys.get(p).delta())
            );
        }
    }

    public static boolean isMiningHighwayZone() {
        HighwayUtils.HighwayDirection dir = HighwayBuilder.getDirection();
        if (dir == null) {
            return false;
        } else {
            boolean isCardinal = dir == HighwayUtils.HighwayDirection.NORTH
                || dir == HighwayUtils.HighwayDirection.SOUTH
                || dir == HighwayUtils.HighwayDirection.EAST
                || dir == HighwayUtils.HighwayDirection.WEST;
            BlockPos[] zone = isCardinal ? HighwayPositions.getCardinalClearArea(0, 2) : HighwayPositions.getDiagonalClearArea(0, 2);
            Set<BlockPos> zoneSet = new HashSet<>(Arrays.asList(zone));
            HighwayState state = HighwayState.getInstance();
            HighwayDetector.DetectedHighway detected = state.getDetectedHighway();
            if (detected != null && state.getAnchorY() != null) {
                int railY = state.getAnchorY();
                BlockPos[] railZone = isCardinal ? HighwayPositions.getCardinalFloor(0, 2, true, true) : HighwayPositions.getDiagonalFloor(0, 2, true, true);

                for (BlockPos pos : railZone) {
                    if (pos.getY() == railY && HighwayDetector.isOnCrossingHighway(pos, detected)) {
                        zoneSet.add(pos);
                    }
                }
            }

            BlockPos p = KekMine.INSTANCE.getPrimaryPos();
            if (p != null && zoneSet.contains(p)) {
                return true;
            } else {
                p = KekMine.INSTANCE.getSecondaryPos();
                if (p != null && zoneSet.contains(p)) {
                    return true;
                } else {
                    for (BlockPos posx : validTargets) {
                        if (zoneSet.contains(posx)) {
                            return true;
                        }
                    }

                    return false;
                }
            }
        }
    }

    public static List<BlockPos> getBlocksInShape(KekNuker.Shape shape, double radius) {
        List<BlockPos> positions = new ArrayList<>();
        Vec3d eyePos = client.player.getEyePos();
        int blockRadius = (int)Math.ceil(radius);

        for (int x = -blockRadius; x < blockRadius; x++) {
            for (int y = -blockRadius; y < blockRadius; y++) {
                for (int z = -blockRadius; z < blockRadius; z++) {
                    BlockPos pos = BlockPos.ofFloored(eyePos.x + x, eyePos.y + y, eyePos.z + z);
                    if (BlockUtils.canBreak(pos)) {
                        if (shape == KekNuker.Shape.Sphere) {
                            if (squaredDistanceToBlock(eyePos, pos) <= radius * radius) {
                                positions.add(pos);
                            }
                        } else if (shape == KekNuker.Shape.Cube) {
                            positions.add(pos);
                        }
                    }
                }
            }
        }

        return positions;
    }

    static double squaredDistanceToBlock(Vec3d eye, BlockPos pos) {
        double cx = Math.max((double)pos.getX(), Math.min(eye.x, pos.getX() + 1.0));
        double cy = Math.max((double)pos.getY(), Math.min(eye.y, pos.getY() + 1.0));
        double cz = Math.max((double)pos.getZ(), Math.min(eye.z, pos.getZ() + 1.0));
        double dx = eye.x - cx;
        double dy = eye.y - cy;
        double dz = eye.z - cz;
        return dx * dx + dy * dy + dz * dz;
    }

    public static Direction getClosestFace(Vec3d eye, BlockPos pos) {
        double dx = eye.x - (pos.getX() + 0.5);
        double dy = eye.y - (pos.getY() + 0.5);
        double dz = eye.z - (pos.getZ() + 0.5);
        double adx = Math.abs(dx);
        double ady = Math.abs(dy);
        double adz = Math.abs(dz);
        if (adx >= ady && adx >= adz) {
            return dx > 0.0 ? Direction.EAST : Direction.WEST;
        } else if (ady >= adz) {
            return dy > 0.0 ? Direction.UP : Direction.DOWN;
        } else {
            return dz > 0.0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

    private ChunkPos getRingChunk(ChunkPos center, int radius, int index) {
        if (radius == 0) {
            return center;
        } else {
            int cx = center.x;
            int cz = center.z;
            if (index < 2 * radius + 1) {
                return new ChunkPos(cx - radius + index, cz - radius);
            } else {
                index -= 2 * radius + 1;
                if (index < 2 * radius) {
                    return new ChunkPos(cx + radius, cz - radius + 1 + index);
                } else {
                    index -= 2 * radius;
                    if (index < 2 * radius) {
                        return new ChunkPos(cx + radius - 1 - index, cz + radius);
                    } else {
                        index -= 2 * radius;
                        return new ChunkPos(cx - radius, cz + radius - 1 - index);
                    }
                }
            }
        }
    }

    private BlockPos findClosestTargetInChunk(ChunkPos chunk) {
        if (client.world == null || client.player == null) {
            return null;
        } else if (!client.world.isChunkLoaded(chunk.x, chunk.z)) {
            return null;
        } else {
            int minX = chunk.getStartX();
            int maxX = chunk.getEndX();
            int minZ = chunk.getStartZ();
            int maxZ = chunk.getEndZ();
            BlockPos closest = null;
            double closestDist = Double.MAX_VALUE;
            double px = client.player.getX();
            double py = client.player.getY();
            double pz = client.player.getZ();
            if (this.nukerMode.get() == KekNuker.NukerMode.Litematica) {
                if (!LitematicaHelper.isLoaded()) {
                    return null;
                }

                int minY = client.world.getBottomY();
                int maxY = client.world.getBottomY() + client.world.getHeight() - 1;

                for (BlockPos pos : LitematicaHelper.get()
                    .getWrongBlocksInBox(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), !this.ignoreAir.get(), 64)) {
                    BlockState state = client.world.getBlockState(pos);
                    if (this.isAllowedByLayer(pos)
                        && BlockUtils.canBreak(pos)
                        && (!this.avoidSpillingLiquids.get() || !HighwayUtils.isNextToLiquid(pos))
                        && this.isAllowedByList(state)
                        && this.hasGroundBelow(pos, 8)) {
                        double dist = pos.getSquaredDistance(px, py, pz);
                        if (dist < closestDist) {
                            closestDist = dist;
                            closest = pos;
                        }
                    }
                }
            } else {
                ISelection[] selections = null;
                if (this.nukerMode.get() == KekNuker.NukerMode.BaritoneSelection) {
                    selections = BaritoneAPI.getProvider().getPrimaryBaritone().getSelectionManager().getSelections();
                    if (selections == null || selections.length == 0) {
                        return null;
                    }
                }

                int feetY = client.player.getBlockPos().getY();
                int r = (int)Math.ceil(this.range.get());
                int minY = this.layerType.get() == KekNuker.LayerType.Flatten ? feetY : Math.max(client.world.getBottomY(), feetY - r);
                int maxY = Math.min(client.world.getBottomY() + client.world.getHeight() - 1, feetY + r);
                Mutable mpos = new Mutable();

                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        for (int y = minY; y <= maxY; y++) {
                            mpos.set(x, y, z);
                            BlockState state = client.world.getBlockState(mpos);
                            if (!state.isAir()
                                && !state.isLiquid()
                                && BlockUtils.canBreak(mpos)
                                && (!this.avoidSpillingLiquids.get() || !HighwayUtils.isNextToLiquid(mpos))
                                && this.isAllowedByLayer(mpos)
                                && this.isAllowedByList(state)
                                && (selections == null || isInSelections(mpos, selections))
                                && this.hasGroundBelow(mpos, 8)) {
                                double dist = mpos.getSquaredDistance(px, py, pz);
                                if (dist < closestDist) {
                                    closestDist = dist;
                                    closest = mpos.toImmutable();
                                }
                            }
                        }
                    }
                }
            }

            return closest;
        }
    }

    private BlockPos findStandPos(BlockPos candidate) {
        int playerY = client.player.getBlockY();
        int r = (int)Math.ceil(this.range.get());
        double rangeSq = this.range.get() * this.range.get();
        int dy = playerY - candidate.getY();
        if (dy * dy > rangeSq) {
            return null;
        } else {
            BlockPos best = null;
            double bestDist = Double.MAX_VALUE;
            Mutable standMut = new Mutable();
            Mutable groundMut = new Mutable();

            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (!(dx * dx + dy * dy + dz * dz > rangeSq)) {
                        int sx = candidate.getX() + dx;
                        int sz = candidate.getZ() + dz;
                        groundMut.set(sx, playerY - 1, sz);
                        BlockState ground = client.world.getBlockState(groundMut);
                        if (!ground.isAir() && !ground.isLiquid()) {
                            standMut.set(sx, playerY, sz);
                            double dist = standMut.getSquaredDistance(candidate);
                            if (dist < bestDist) {
                                bestDist = dist;
                                best = standMut.toImmutable();
                            }
                        }
                    }
                }
            }

            return best;
        }
    }

    private boolean hasGroundBelow(BlockPos pos, int depth) {
        int bottom = client.world.getBottomY();
        int x = pos.getX();
        int z = pos.getZ();
        Mutable check = new Mutable(x, 0, z);

        for (int dy = 1; dy <= depth; dy++) {
            int y = pos.getY() - dy;
            if (y <= bottom) {
                break;
            }

            check.set(x, y, z);
            BlockState s = client.world.getBlockState(check);
            if (!s.isAir() && !s.isLiquid()) {
                return true;
            }
        }

        return false;
    }

    private boolean isAllowedByLayer(BlockPos pos) {
        return this.layerType.get() == KekNuker.LayerType.Flatten && pos.getY() < client.player.getBlockPos().getY()
            ? false
            : this.layerType.get() != KekNuker.LayerType.LitematicaLayers
                || !LitematicaHelper.isLoaded()
                || LitematicaHelper.get().isPositionInRenderLayer(pos);
    }

    private boolean isAllowedByList(BlockState state) {
        if (this.listMode.get() == KekNuker.ListMode.Whitelist) {
            return this.whitelist.get().contains(state.getBlock());
        } else {
            return this.listMode.get() == KekNuker.ListMode.Blacklist ? !this.blacklist.get().contains(state.getBlock()) : true;
        }
    }

    private static boolean isInSelections(BlockPos pos, ISelection[] selections) {
        for (ISelection sel : selections) {
            BlockPos min = sel.min();
            BlockPos max = sel.max();
            if (pos.getX() >= min.getX()
                && pos.getX() <= max.getX()
                && pos.getY() >= min.getY()
                && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ()
                && pos.getZ() <= max.getZ()) {
                return true;
            }
        }

        return false;
    }

    private List<BlockPos> getSelectionBlocks() {
        List<BlockPos> list = new ArrayList<>();
        if (client.player != null && client.world != null) {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            ISelectionManager selectionManager = baritone.getSelectionManager();
            if (selectionManager.getSelections() == null) {
                return list;
            } else {
                for (ISelection selection : selectionManager.getSelections()) {
                    BlockPos min = selection.min();
                    BlockPos max = selection.max();
                    int regionMinX = Math.min(min.getX(), max.getX());
                    int regionMinY = Math.min(min.getY(), max.getY());
                    int regionMinZ = Math.min(min.getZ(), max.getZ());
                    int regionMaxX = Math.max(min.getX(), max.getX());
                    int regionMaxY = Math.max(min.getY(), max.getY());
                    int regionMaxZ = Math.max(min.getZ(), max.getZ());
                    int px = client.player.getBlockX();
                    int py = client.player.getBlockY();
                    int pz = client.player.getBlockZ();
                    int minX = Math.max(regionMinX, px - 12);
                    int minY = Math.max(regionMinY, py - 12);
                    int minZ = Math.max(regionMinZ, pz - 12);
                    int maxX = Math.min(regionMaxX, px + 12);
                    int maxY = Math.min(regionMaxY, py + 12);
                    int maxZ = Math.min(regionMaxZ, pz + 12);
                    if (minX <= maxX && minY <= maxY && minZ <= maxZ) {
                        for (int x = minX; x <= maxX; x++) {
                            for (int y = minY; y <= maxY; y++) {
                                for (int z = minZ; z <= maxZ; z++) {
                                    BlockPos pos = new BlockPos(x, y, z);
                                    if (!client.world.getBlockState(pos).isAir()
                                        && !client.world.getBlockState(pos).isLiquid()
                                        && !list.contains(pos)
                                        && HighwayUtils.isInRange(pos, this.range.get())) {
                                        list.add(pos);
                                    }
                                }
                            }
                        }
                    }
                }

                return list;
            }
        } else {
            return list;
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (client.player != null && client.world != null) {
            if (this.globalRendering.get()) {
                BlockRenderUtils.renderPositions(event, validTargets);
            } else {
                BlockRenderUtils.renderPositions(event, validTargets, this.renderColor.get(), this.renderColor.get(), ShapeMode.Lines);
            }
        }
    }

    private record EffKey(boolean toolMatch, boolean insta, float delta) {
    }

    private static enum LayerType {
        Normal,
        Flatten,
        LitematicaLayers;
    }

    public static enum ListMode {
        Disabled,
        Whitelist,
        Blacklist;
    }

    public static enum NukerMode {
        Normal,
        Highway,
        Litematica,
        BaritoneSelection;
    }

    public static enum Shape {
        Cube,
        Sphere;
    }

    public static enum SortMode {
        None,
        Efficient,
        Closest,
        Furthest,
        TopDown,
        BottomUp;
    }
}
