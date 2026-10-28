package musheor.modules.automation;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.selection.ISelection;
import baritone.api.selection.ISelectionManager;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.AutoTotem;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.systems.modules.player.AutoEat;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import musheor.musheor;
import musheor.commands.RestockConfig;
import musheor.compat.LitematicaHelper;
import musheor.compat.VersionHelper;
import musheor.utils.BlockRenderUtils;
import musheor.utils.HighwayUtils;
import musheor.utils.PlayerControlUtils;
import musheor.utils.baritone.BaritoneHelper;
import musheor.utils.highway.HighwayState;
import musheor.utils.manager.InventoryManager;
import musheor.utils.manager.RotationManager;
import musheor.utils.network.PacketLimiter;
import musheor.utils.placement.BlockPlacer;
import musheor.utils.printer.BlockStateCycler;
import musheor.utils.system.MusheorSystem;
import net.minecraft.block.AirBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.shape.VoxelShape;

public class Printer extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgPlacement = this.settings.createGroup("Placement");
    private final SettingGroup sgRotation = this.settings.createGroup("Rotation");
    private final SettingGroup sgPathfindingRestocking = this.settings.createGroup("Pathfinding/Restocking");
    private final SettingGroup sgRender = this.settings.createGroup("Render");
    private final SettingGroup sgLitematica = this.settings.createGroup("Litematica");
    private final MinecraftClient client = MinecraftClient.getInstance();
    private final Long2ObjectMap<Map<BlockPos, BlockState>> chunkTargets = new Long2ObjectOpenHashMap();
    private final List<Pair<BlockPos, BlockState>> posAndBlocks = new ArrayList<>();
    private final List<Pair<BlockPos, BlockState>> renderTargets = new ArrayList<>();
    private int tickCounter;
    private int restockDelayTimer = 0;
    private int containerOpenTimer = 0;
    private int swapTimer = 0;
    private int restockedCount = 0;
    private boolean pendingDesyncFix;
    private int desyncTicks;
    private final Set<BlockPos> emptyContainers = new HashSet<>();
    private Item restockItem = null;
    private final List<Item> restockQueue = new ArrayList<>();
    private boolean showSchematicPos = false;
    private int rotationTimer = 0;
    private Printer.PlacementStrategy pendingRotation = null;
    private Map<Block, Integer> remainingMaterials = new HashMap<>();
    private int materialRefreshTimer = 0;
    private int scanRadius = -1;
    private int ringIndex = 0;
    private BlockPos pathGoal = null;
    private BlockPos bestCandidate = null;
    private boolean wasPlacing = false;
    private boolean refillStarted = false;
    private final Map<BlockPos, Integer> stateWaitUntil = new HashMap<>();
    private final Map<BlockPos, Integer> stateLastNeeded = new HashMap<>();
    private final Map<BlockPos, Integer> stateStallRounds = new HashMap<>();
    private final Set<BlockPos> stateGivenUp = new HashSet<>();
    private int stateDelayTimer = 0;
    private static final Set<Property<?>> ORIENTATION_PROPERTIES = new HashSet<>(
        Arrays.asList(Properties.FACING, Properties.HORIZONTAL_FACING, Properties.BLOCK_HALF, Properties.SLAB_TYPE, Properties.AXIS, Properties.HOPPER_FACING)
    );
    private static final Direction[] HORIZONTAL_DIRECTIONS = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
    private static final float[] TEST_PITCHES = new float[]{-90.0F, 0.0F, 90.0F};
    private static final double[] HIT_Y_FRACTIONS = new double[]{0.25, 0.75};
    private final Map<BlockState, Printer.PlacementStrategy> strategyCache = new HashMap<>();
    private static Method getPlacementStateMethod = null;
    private final Setting<Printer.SelectionType> selectionType = this.sgGeneral
        .add(
            new Builder<Printer.SelectionType>().name("selection-type").description("Get selection from litematica or baritone")
                    .defaultValue(Printer.SelectionType.Litematica)
                .build()
        );
    private final Setting<Boolean> pauseWhenEating = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("pause-when-eating")
                .description("Pauses the printing process when the player eats (only when using meteor's auto-eat)")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> pauseOnAura = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("pause-on-aura")
                .description("Pauses the printing process when the player is killing mobs using killaura")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> fixDesync = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("fix-desync")
                .description("Attempts to fix your offhand / inventory desyncs from getting you stuck")
                .defaultValue(false)
                .build()
        );
    private final Setting<Printer.LayerType> layerType = this.sgPlacement
        .add(
            new Builder<Printer.LayerType>().name("layer-type").description("Choose how layering is handled when printing")
                    .defaultValue(Printer.LayerType.All)
                .build()
        );
    private final Setting<List<Block>> ignoredBlockList = this.sgPlacement
        .add(
            new meteordevelopment.meteorclient.settings.BlockListSetting.Builder()
                .name("ignored-block-list")
                .description("Avoids placing blocks configured in this list")
                .defaultValue()
                .build()
        );
    private final Setting<Block> block = this.sgPlacement
        .add(
            new meteordevelopment.meteorclient.settings.BlockSetting.Builder()
                .name("block")
                .description("What block to place (used for Baritone mode).")
                .defaultValue(Blocks.OBSIDIAN)
                .visible(() -> this.selectionType.get() == Printer.SelectionType.Baritone)
                .build()
        );
    private final Setting<Boolean> onlyAir = this.sgPlacement
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("only-air")
                .description("Places blocks only when the desired location is an air block (meaning it doesn't replace non-solid blocks)")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> gravityCheck = this.sgPlacement
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("gravity-check")
                .description("Skip gravity-affected blocks (sand, gravel, etc.) if there is no solid block below them to prevent unwanted falling.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> setCorrectStates = this.sgPlacement
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("set-correct-states")
                .description("Sets the correct states of a block by interacting with it (e.g: opening trapdoors, setting repeater ticks...)")
                .defaultValue(true)
                .build()
        );
    private final Setting<Integer> setStateDelay = this.sgPlacement
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("set-state-delay")
                .description("Ticks to wait between interacting with a block to achieve the desired state.")
                .defaultValue(0)
                .sliderRange(0, 10)
                .visible(this.setCorrectStates::get)
                .build()
        );
    private final Setting<List<Block>> ignoredBlockstatesList = this.sgPlacement
        .add(
            new meteordevelopment.meteorclient.settings.BlockListSetting.Builder()
                .name("ignored-blockstates-list")
                .description("Which blocks to ignore or avoid setting the correct state for (e.g: note blocks, doors...)")
                .defaultValue()
                .visible(this.setCorrectStates::get)
                .build()
        );
    private final Setting<Printer.RotationType> rotationType = this.sgRotation
        .add(
            new Builder<Printer.RotationType>().name("rotation-type")
                        .description("Automatically rotate the camera to the correct direction when placing directional blocks.")
                    .defaultValue(Printer.RotationType.Forced)
                .build()
        );
    private final Setting<Integer> rotationDelay = this.sgRotation
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("rotation-delay")
                .description("Ticks to wait after rotating before attempting placement (allows rotation to settle server-side).")
                .defaultValue(0)
                .min(0)
                .sliderMax(10)
                .visible(() -> this.rotationType.get() == Printer.RotationType.Forced)
                .build()
        );
    private final Setting<Boolean> autoPathfinding = this.sgPathfindingRestocking
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("auto-pathfinding")
                .description("Basically baritone building but better...")
                .defaultValue(true)
                .build()
        );
    private final Setting<Integer> pathfindChunkRange = this.sgPathfindingRestocking
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("pathfind-chunk-range")
                .description("The maximum distance in chunks to search for a block to pathfind to and place")
                .defaultValue(8)
                .visible(this.autoPathfinding::get)
                .build()
        );
    private final Setting<Printer.RestockType> autoRestock = this.sgPathfindingRestocking
        .add(
            new Builder<Printer.RestockType>().name("auto-restock")
                            .description("Automatically restock more materials from either configured containers or from shulkers you have on you")
                        .defaultValue(Printer.RestockType.Disabled)
                    .visible(this.autoPathfinding::get)
                .build()
        );
    private final Setting<Boolean> playSound = this.sgPathfindingRestocking
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("play-sound")
                .description("Plays a sound when its finished building or failed")
                .defaultValue(true)
                .visible(this.autoPathfinding::get)
                .build()
        );
    private final Setting<Boolean> debugMode = this.sgPathfindingRestocking
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("debug-mode")
                .description("Logs why each nearby candidate is rejected from placement, once per second")
                .defaultValue(false)
                .build()
        );
    private int debugTimer = 0;
    private final Setting<Boolean> render = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("render")
                .description("Renders placement positions")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> renderPlaceableOnly = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("render-placeable-only")
                .description("Only renders blocks that are placeable or stuff that can be replaced")
                .defaultValue(true)
                .build()
        );
    private final Setting<Integer> renderRadius = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("render-radius")
                .description("Only renders placeable blocks in a specified radius (lower if you experience FPS drops)")
                .defaultValue(16)
                .sliderMin(2)
                .sliderMax(128)
                .visible(MusheorSystem.Manager.placeRender::get)
                .build()
        );
    private final Setting<Integer> renderMaxResults = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("render-max-results")
                .description("How many blocks are allowed to be rendered at once (increasing can cause lag)")
                .defaultValue(2000)
                .sliderRange(50, 10000)
                .build()
        );
    private final Setting<String> schematicName = this.sgLitematica
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("schematic-name")
                .description("The name of the schematic you want to place")
                .visible(() -> LitematicaHelper.isLoaded() && this.selectionType.get() == Printer.SelectionType.Litematica)
                .defaultValue("")
                .build()
        );
    private final Setting<BlockPos> schematicPos = this.sgLitematica
        .add(
            new meteordevelopment.meteorclient.settings.BlockPosSetting.Builder()
                .name("schematic-pos")
                .description("The position of the schematic you want to place")
                .visible(() -> LitematicaHelper.isLoaded() && this.selectionType.get() == Printer.SelectionType.Litematica && this.showSchematicPos)
                .defaultValue(new BlockPos(0, 0, 0))
                .build()
        );

    private static BlockState invokeGetPlacementState(Block block, ItemPlacementContext ctx) {
        try {
            if (getPlacementStateMethod == null) {
                for (Method m : Block.class.getMethods()) {
                    if (m.getParameterCount() == 1
                        && BlockState.class.isAssignableFrom(m.getReturnType())
                        && m.getParameterTypes()[0].isAssignableFrom(ItemPlacementContext.class)) {
                        getPlacementStateMethod = m;
                        break;
                    }
                }

                if (getPlacementStateMethod == null) {
                    search:
                    for (Class<?> clazz = Block.class; clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
                        for (Method mx : clazz.getDeclaredMethods()) {
                            if (mx.getParameterCount() == 1
                                && BlockState.class.isAssignableFrom(mx.getReturnType())
                                && mx.getParameterTypes()[0].isAssignableFrom(ItemPlacementContext.class)) {
                                mx.setAccessible(true);
                                getPlacementStateMethod = mx;
                                break search;
                            }
                        }
                    }
                }
            }

            return getPlacementStateMethod != null ? (BlockState)getPlacementStateMethod.invoke(block, ctx) : null;
        } catch (Exception var7) {
            return null;
        }
    }

    public Printer() {
        super(musheor.AUTOMATION, "kek-printer", "Places blocks from schematics or fills baritone selections pretty damn fast");
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();
        WButton loadAndPlaceSchematic = list.add(theme.button("Load and place schematic")).widget();
        loadAndPlaceSchematic.action = () -> {
            if (this.client.player != null && this.client.world != null) {
                if (!LitematicaHelper.isLoaded()) {
                    this.error("Litematica is not installed", new Object[0]);
                } else {
                    String name = this.schematicName.get();
                    BlockPos pos = this.schematicPos.get();
                    if (name.isEmpty()) {
                        this.error("No schematic name specified", new Object[0]);
                    } else {
                        if (LitematicaHelper.get().ensureSchematicAt(name, pos)) {
                            this.info("Loaded schematic: §b%s", new Object[]{name});
                        }
                    }
                }
            }
        };
        WButton clearPlacement = list.add(theme.button("Remove placement")).widget();
        clearPlacement.action = () -> {
            if (LitematicaHelper.isLoaded()) {
                LitematicaHelper.get().clearAllPlacements();
            }
        };
        WButton toggleSettingVisibility = list.add(theme.button("Toggle BlockPos visibility")).widget();
        toggleSettingVisibility.action = () -> {
            this.showSchematicPos = !this.showSchematicPos;
            toggleSettingVisibility.set(this.showSchematicPos ? "Hide Coordinates" : "Show Coordinates");
            this.schematicPos.onChanged();
        };
        return list;
    }

    private void addChunkTarget(BlockPos pos, BlockState targetState) {
        long chunkKey = ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4);
        ((Map)this.chunkTargets.computeIfAbsent(chunkKey, k -> new HashMap())).put(pos, targetState);
    }

    private void addSelection(ISelection selection) {
        if (selection != null && this.client.world != null) {
            BlockPos min = selection.min();
            BlockPos max = selection.max();

            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int y = min.getY(); y <= max.getY(); y++) {
                    for (int z = min.getZ(); z <= max.getZ(); z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (this.onlyAir.get() ? this.client.world.getBlockState(pos).getBlock() instanceof AirBlock : BlockUtils.canPlace(pos, false)) {
                            this.addChunkTarget(pos, this.block.get().getDefaultState());
                        }
                    }
                }
            }
        }
    }

    private boolean isForcedRotation() {
        return this.rotationType.get() == Printer.RotationType.Forced;
    }

    @Override
    public void onActivate() {
        this.chunkTargets.clear();
        this.restockQueue.clear();
        HighwayState.getInstance().getPendingPlacements().clear();
        this.tickCounter = 0;
        this.swapTimer = 0;
        this.rotationTimer = 0;
        this.pendingRotation = null;
        this.strategyCache.clear();
        this.restockedCount = 0;
        this.desyncTicks = 0;
        this.pendingDesyncFix = false;
        this.remainingMaterials = new HashMap<>();
        this.materialRefreshTimer = 0;
        this.scanRadius = -1;
        this.ringIndex = 0;
        this.pathGoal = null;
        this.bestCandidate = null;
        this.wasPlacing = false;
        this.refillStarted = false;
        this.resetStateTracking();
        if (this.selectionType.get() == Printer.SelectionType.Baritone) {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            ISelectionManager selectionManager = baritone.getSelectionManager();
            if (selectionManager.getSelections() == null) {
                this.error("No Baritone selection found.", new Object[0]);
                this.toggle();
                return;
            }

            for (ISelection selection : selectionManager.getSelections()) {
                this.addSelection(selection);
            }
        }

        if (this.selectionType.get() == Printer.SelectionType.Litematica) {
            if (!LitematicaHelper.isLoaded()) {
                this.error("Litematica is not installed. Install Litematica or switch to Baritone mode.", new Object[0]);
                this.toggle();
                return;
            }

            if (!LitematicaHelper.get().verifySchematic()) {
                this.error("Invalid or missing schematic", new Object[0]);
                this.toggle();
                return;
            }

            this.remainingMaterials = LitematicaHelper.get().getRemainingMaterialCounts(this.ignoredBlockList.get(), this.onlyAir.get());
            int total = this.remainingMaterials.values().stream().mapToInt(Integer::intValue).sum();
            this.info("Schematic ready — §b" + total + "§r blocks remaining to place", new Object[0]);
        }
    }

    @Override
    public void onDeactivate() {
        PlayerControlUtils.cancelBaritone();
        RotationManager.release(10);
        this.renderTargets.clear();
        this.remainingMaterials.clear();
        this.materialRefreshTimer = 0;
        this.scanRadius = -1;
        this.ringIndex = 0;
        this.pathGoal = null;
        this.bestCandidate = null;
        this.wasPlacing = false;
        this.refillStarted = false;
        this.resetStateTracking();
        this.restockDelayTimer = 0;
        this.swapTimer = 0;
        this.containerOpenTimer = 0;
    }

    private void resetStateTracking() {
        this.stateWaitUntil.clear();
        this.stateLastNeeded.clear();
        this.stateStallRounds.clear();
        this.stateGivenUp.clear();
        this.stateDelayTimer = 0;
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.client.player != null && this.client.world != null) {
            HighwayState state = HighwayState.getInstance();
            this.tickCounter++;
            RotationManager.release(10);
            if (this.selectionType.get() == Printer.SelectionType.Litematica && this.autoPathfinding.get() && ++this.materialRefreshTimer >= 200) {
                this.materialRefreshTimer = 0;
                this.refreshMaterialCounts();
            }

            HighwayState.getInstance()
                .getPendingPlacements()
                .entrySet()
                .removeIf(entryx -> this.tickCounter - (Integer)entryx.getValue() > MusheorSystem.Manager.placementTimeout.get());
            if (!this.pauseWhenEating.get() || !Modules.get().get(AutoEat.class).eating) {
                if (!this.pauseOnAura.get() || !Modules.get().get(KillAura.class).attacking) {
                    if (this.fixDesync.get()) {
                        if (this.pendingDesyncFix) {
                            this.pendingDesyncFix = false;
                            VersionHelper.get().syncInventory();
                        } else if (this.client.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING && Modules.get().get(AutoTotem.class).isActive()) {
                            this.desyncTicks++;
                            if (this.desyncTicks >= 20) {
                                this.pendingDesyncFix = true;
                                this.desyncTicks = 0;
                            }
                        }
                    }

                    this.posAndBlocks.clear();
                    if (this.selectionType.get() == Printer.SelectionType.Litematica) {
                        BlockPos playerPos = this.client.player.getBlockPos();
                        int pRange = (int)Math.ceil(MusheorSystem.Manager.placementRange.get()) + 1;
                        Map<BlockPos, BlockState> placementCandidates = LitematicaHelper.get()
                            .getBlocksInBox(
                                playerPos.add(-pRange, -pRange, -pRange),
                                playerPos.add(pRange, pRange, pRange),
                                this.onlyAir.get(),
                                this.ignoredBlockList.get(),
                                1500
                            );
                        boolean doDebug = this.debugMode.get() && ++this.debugTimer >= 20;
                        if (doDebug) {
                            this.debugTimer = 0;
                        }

                        if (doDebug && placementCandidates.isEmpty()) {
                            this.info("[DBG] No schematic candidates in box (pRange=%d)", new Object[]{pRange});
                        }

                        BlockPos playerFeet = this.client.player.getBlockPos();

                        for (Entry<BlockPos, BlockState> entry : placementCandidates.entrySet()) {
                            BlockPos pos = entry.getKey();
                            BlockState targetState = entry.getValue();
                            String blockName = Registries.BLOCK.getId(targetState.getBlock()).getPath();
                            if (InventoryManager.countItem(targetState.getBlock().asItem()) <= 0) {
                                if (doDebug) {
                                    this.info("[DBG] %s @ %s — skip: no items in inventory", new Object[]{blockName, pos.toShortString()});
                                }
                            } else if (!BlockUtils.canPlace(pos, true)) {
                                if (doDebug) {
                                    this.info("[DBG] %s @ %s — skip: BlockUtils.canPlace=false", new Object[]{blockName, pos.toShortString()});
                                }
                            } else if (this.layerType.get() == Printer.LayerType.OnlyBelowFeet && pos.getY() >= Math.floor(this.client.player.getY())) {
                                if (doDebug) {
                                    this.info("[DBG] %s @ %s — skip: above feet layer", new Object[]{blockName, pos.toShortString()});
                                }
                            } else if (this.layerType.get() == Printer.LayerType.LitematicaLayer && !LitematicaHelper.get().isPositionInRenderLayer(pos)) {
                                if (doDebug) {
                                    this.info("[DBG] %s @ %s — skip: not in render layer", new Object[]{blockName, pos.toShortString()});
                                }
                            } else if (state.getPendingPlacements().containsKey(pos)) {
                                if (doDebug) {
                                    this.info(
                                        "[DBG] %s @ %s — skip: recent attempt (tick %d, now %d)",
                                        new Object[]{blockName, pos.toShortString(), state.getPendingPlacements().get(pos), this.tickCounter}
                                    );
                                }
                            } else if (!pos.equals(playerFeet) && !pos.equals(playerFeet.up())) {
                                Printer.PlacementStrategy strat = this.getStrategy(targetState);
                                if (!this.isInPlacementRange(pos, strat)) {
                                    if (doDebug) {
                                        this.info(
                                            "[DBG] %s @ %s — skip: not in placement range (hitDist=%.2f, range=%.1f)",
                                            new Object[]{
                                                blockName,
                                                pos.toShortString(),
                                                Math.sqrt(
                                                    this.client
                                                        .player
                                                        .getEyePos()
                                                        .squaredDistanceTo(this.getPlacementHit(pos, strat, this.canUseFreeHit(strat)).getPos())
                                                ),
                                                MusheorSystem.Manager.placementRange.get()
                                            }
                                        );
                                    }
                                } else {
                                    if (doDebug) {
                                        this.info("[DBG] %s @ %s — ADDED to posAndBlocks", new Object[]{blockName, pos.toShortString()});
                                    }

                                    this.posAndBlocks.add(Pair.of(pos, targetState));
                                }
                            } else if (doDebug) {
                                this.info("[DBG] %s @ %s — skip: player standing here", new Object[]{blockName, pos.toShortString()});
                            }
                        }

                        if (this.render.get()) {
                            int rRange = this.renderRadius.get();
                            Map<BlockPos, BlockState> renderCandidates = LitematicaHelper.get()
                                .getBlocksInBox(
                                    playerPos.add(-rRange, -rRange, -rRange),
                                    playerPos.add(rRange, rRange, rRange),
                                    this.onlyAir.get(),
                                    this.ignoredBlockList.get(),
                                    this.renderMaxResults.get()
                                );
                            this.renderTargets.clear();

                            for (Entry<BlockPos, BlockState> entryx : renderCandidates.entrySet()) {
                                if (InventoryManager.countItem(entryx.getValue().getBlock().asItem()) > 0
                                    && (
                                        this.layerType.get() != Printer.LayerType.OnlyBelowFeet
                                            || !(entryx.getKey().getY() >= Math.floor(this.client.player.getY()))
                                    )
                                    && (
                                        this.layerType.get() != Printer.LayerType.LitematicaLayer
                                            || LitematicaHelper.get().isPositionInRenderLayer(entryx.getKey())
                                    )
                                    && HighwayUtils.isInRange(entryx.getKey(), rRange)) {
                                    this.renderTargets.add(Pair.of(entryx.getKey(), entryx.getValue()));
                                }
                            }
                        }
                    } else {
                        this.renderTargets.clear();
                        ChunkPos playerChunk = this.client.player.getChunkPos();
                        int chunkRange = this.pathfindChunkRange.get();

                        for (int cx = playerChunk.x - chunkRange; cx <= playerChunk.x + chunkRange; cx++) {
                            for (int cz = playerChunk.z - chunkRange; cz <= playerChunk.z + chunkRange; cz++) {
                                long key = ChunkPos.toLong(cx, cz);
                                Map<BlockPos, BlockState> posMap = (Map<BlockPos, BlockState>)this.chunkTargets.get(key);
                                if (posMap != null && !posMap.isEmpty()) {
                                    for (Entry<BlockPos, BlockState> entryxx : posMap.entrySet()) {
                                        BlockPos pos = entryxx.getKey();
                                        BlockState currentState = this.client.world.getBlockState(pos);
                                        BlockState targetState = entryxx.getValue();
                                        if (InventoryManager.countItem(targetState.getBlock().asItem()) > 0
                                            && BlockUtils.canPlace(pos, true)
                                            && (
                                                this.layerType.get() != Printer.LayerType.OnlyBelowFeet
                                                    || !(pos.getY() >= Math.floor(this.client.player.getY()))
                                            )
                                            && (
                                                this.layerType.get() != Printer.LayerType.LitematicaLayer
                                                    || LitematicaHelper.isLoaded() && LitematicaHelper.get().isPositionInRenderLayer(pos)
                                            )
                                            && (currentState.getBlock() instanceof AirBlock || !this.onlyAir.get())) {
                                            if (this.render.get() && HighwayUtils.isInRange(pos, this.renderRadius.get().intValue())) {
                                                this.renderTargets.add(Pair.of(pos, targetState));
                                            }

                                            if (!state.getPendingPlacements().containsKey(pos)
                                                && this.isInPlacementRange(pos, this.getStrategy(targetState))
                                                && this.posAndBlocks.stream().noneMatch(pairx -> ((BlockPos)pairx.first()).equals(pos))) {
                                                this.posAndBlocks.add(Pair.of(pos, targetState));
                                            }
                                        }
                                    }

                                    if (posMap.isEmpty()) {
                                        this.chunkTargets.remove(key);
                                    }
                                }
                            }
                        }
                    }

                    this.posAndBlocks
                        .sort(
                            Comparator.comparingDouble(
                                pairx -> this.client
                                    .player
                                    .squaredDistanceTo(((BlockPos)pairx.first()).getX(), ((BlockPos)pairx.first()).getY(), ((BlockPos)pairx.first()).getZ())
                            )
                        );
                    this.fixBlockStates();
                    if (this.autoRestock.get() != Printer.RestockType.Disabled && this.autoPathfinding.get()) {
                        Block neededBlock = this.getNextBlock();
                        if (neededBlock == null) {
                            boolean nothingLeft = this.selectionType.get() == Printer.SelectionType.Litematica
                                ? this.posAndBlocks.isEmpty()
                                : this.posAndBlocks.isEmpty() && this.chunkTargets.isEmpty();
                            if (nothingLeft) {
                                this.info("All blocks placed!", new Object[0]);
                                if (this.playSound.get()) {
                                    VersionHelper.get().playSoundPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE);
                                }

                                this.toggle();
                                return;
                            }

                            if (this.selectionType.get() == Printer.SelectionType.Baritone) {
                                this.info("Cannot reach or find more blocks to place", new Object[0]);
                                if (this.playSound.get()) {
                                    VersionHelper.get().playSoundPlayer(SoundEvents.ENTITY_VILLAGER_NO);
                                }

                                this.toggle();
                                return;
                            }
                        }

                        if (InventoryManager.countItem(neededBlock.asItem()) <= 0 && this.restockQueue.isEmpty()) {
                            if (this.selectionType.get() == Printer.SelectionType.Litematica && this.remainingMaterials.getOrDefault(neededBlock, 0) == 0) {
                                this.remainingMaterials.remove(neededBlock);
                                return;
                            }

                            this.buildRestockQueue(neededBlock);
                        }

                        if (this.autoRestock.get() == Printer.RestockType.Refill) {
                            if (!this.restockQueue.isEmpty()) {
                                if (BaritoneHelper.isPathing()) {
                                    BaritoneHelper.cancelAll();
                                }

                                Refill refill = Modules.get().get(Refill.class);
                                if (refill.isActive()) {
                                    return;
                                }

                                if (this.refillStarted) {
                                    this.refillStarted = false;
                                    this.restockQueue.clear();
                                    this.restockDelayTimer = 0;
                                    return;
                                }

                                while (!this.restockQueue.isEmpty() && InventoryManager.countItemIncludingShulkers(this.restockQueue.getFirst()) <= 0) {
                                    this.restockQueue.removeFirst();
                                }

                                if (this.restockQueue.isEmpty()) {
                                    this.info("No shulkers found for needed blocks, disabling...", new Object[0]);
                                    if (this.playSound.get()) {
                                        VersionHelper.get().playSoundPlayer(SoundEvents.ENTITY_VILLAGER_NO);
                                    }

                                    this.toggle();
                                    return;
                                }

                                if (this.restockDelayTimer <= InventoryManager.INSTANCE.delayAfterPlacingContainer.get()) {
                                    this.restockDelayTimer++;
                                    return;
                                }

                                refill.item.set(this.restockQueue.getFirst());
                                refill.toggle();
                                this.refillStarted = true;
                                return;
                            }
                        } else if (!this.restockQueue.isEmpty() && this.posAndBlocks.isEmpty()) {
                            Item neededItem = this.restockQueue.getFirst();
                            int amount = this.getRemainingCount(Block.getBlockFromItem(neededItem));
                            if (this.restockItem != neededItem) {
                                this.emptyContainers.clear();
                                this.restockItem = neededItem;
                                this.restockedCount = 0;
                                this.info("Restocking %s", new Object[]{RestockConfig.getItemId(neededItem)});
                            }

                            if (!RestockConfig.hasContainers(neededItem)) {
                                if (neededItem == neededBlock.asItem()) {
                                    this.info("Cannot find container for %s, disabling...", new Object[]{RestockConfig.getItemId(neededItem)});
                                    if (this.playSound.get()) {
                                        VersionHelper.get().playSoundPlayer(SoundEvents.ENTITY_VILLAGER_NO);
                                    }

                                    this.restockQueue.clear();
                                    this.toggle();
                                    return;
                                }

                                this.restockQueue.removeFirst();
                                return;
                            }

                            BlockPos container = this.findClosestContainer(neededItem);
                            if (container == null) {
                                if (neededItem == neededBlock.asItem()) {
                                    this.info("All containers for %s are empty, disabling...", new Object[]{RestockConfig.getItemId(neededItem)});
                                    if (this.playSound.get()) {
                                        VersionHelper.get().playSoundPlayer(SoundEvents.ENTITY_VILLAGER_NO);
                                    }

                                    this.emptyContainers.clear();
                                    this.restockQueue.clear();
                                    this.toggle();
                                    return;
                                }

                                this.restockQueue.removeFirst();
                                return;
                            }

                            if (!HighwayUtils.isInPlacementRange(container)) {
                                if (!BaritoneHelper.isPathing()) {
                                    BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalNear(container, 2));
                                }

                                return;
                            }

                            PlayerControlUtils.cancelBaritone();
                            if (!InventoryManager.isContainerScreenOpen() && this.containerOpenTimer == 0) {
                                HighwayUtils.rotateToPlace(container);
                                InventoryManager.openContainer(container);
                                this.containerOpenTimer = 3;
                                return;
                            }

                            if (this.containerOpenTimer > 0) {
                                this.containerOpenTimer--;
                                return;
                            }

                            if (this.restockDelayTimer <= InventoryManager.INSTANCE.delayAfterOpeningContainer.get()) {
                                this.restockDelayTimer++;
                                return;
                            }

                            if (!PacketLimiter.canSendInventoryPacket()) {
                                return;
                            }

                            ScreenHandler handler = this.client.player.currentScreenHandler;
                            int containerSize = handler.slots.size() - 36;

                            for (int i = 0; i < containerSize; i++) {
                                ItemStack currentStack = handler.getSlot(i).getStack();
                                if (!currentStack.isEmpty()) {
                                    if (currentStack.getItem() == neededItem) {
                                        this.restockedCount = this.restockedCount + currentStack.getCount();
                                        this.client.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, this.client.player);
                                    }

                                    if (this.restockedCount >= amount || InventoryManager.countEmptySlots() <= 0 || !PacketLimiter.canSendInventoryPacket()) {
                                        break;
                                    }
                                }
                            }

                            if (this.restockedCount == 0) {
                                this.emptyContainers.add(container);
                            } else if (this.restockedCount < amount && InventoryManager.countEmptySlots() > 0) {
                                this.emptyContainers.add(container);
                            } else if (InventoryManager.countEmptySlots() > 0) {
                                this.emptyContainers.clear();
                                this.restockQueue.removeFirst();
                            }

                            this.restockDelayTimer = 0;
                            this.containerOpenTimer = 0;
                            this.client.player.closeScreen();
                            if (this.restockQueue.isEmpty() || InventoryManager.countEmptySlots() <= 0) {
                                this.restockQueue.clear();
                            }

                            return;
                        }
                    }

                    if (this.autoPathfinding.get()) {
                        if (this.posAndBlocks.isEmpty()) {
                            if (this.wasPlacing) {
                                this.scanRadius = -1;
                                this.ringIndex = 0;
                                this.pathGoal = null;
                                this.bestCandidate = null;
                                this.wasPlacing = false;
                            }

                            if (this.selectionType.get() == Printer.SelectionType.Litematica) {
                                if (this.pathGoal != null) {
                                    double distToGoal = this.client
                                        .player
                                        .squaredDistanceTo(this.pathGoal.getX() + 0.5, this.pathGoal.getY() + 0.5, this.pathGoal.getZ() + 0.5);
                                    if (this.debugMode.get()) {
                                        this.info(
                                            "[DBG-PF] goal=%s dist=%.1f pathing=%b radius=%d ringIdx=%d",
                                            new Object[]{
                                                this.pathGoal.toShortString(),
                                                Math.sqrt(distToGoal),
                                                BaritoneHelper.isPathing(),
                                                this.scanRadius,
                                                this.ringIndex
                                            }
                                        );
                                    }

                                    if (!BaritoneHelper.isPathing()) {
                                        if (distToGoal <= 16.0) {
                                            if (this.debugMode.get()) {
                                                this.info("[DBG-PF] arrived at goal, clearing and re-scanning", new Object[0]);
                                            }

                                            this.pathGoal = null;
                                            this.bestCandidate = null;
                                            this.scanRadius = -1;
                                            this.ringIndex = 0;
                                        } else {
                                            BaritoneHelper.setGoalAndPath(new GoalNear(this.pathGoal, 2));
                                        }
                                    }

                                    return;
                                }

                                if (this.scanRadius == -1) {
                                    Block priorityBlock = this.getNextBlock();
                                    if (priorityBlock != null
                                        && InventoryManager.countItem(priorityBlock.asItem()) > 0
                                        && this.remainingMaterials.getOrDefault(priorityBlock, 0) == 0) {
                                        this.remainingMaterials.remove(priorityBlock);
                                        return;
                                    }

                                    this.scanRadius = 0;
                                    this.ringIndex = 0;
                                }

                                int maxChunkRadius = this.pathfindChunkRange.get();
                                if (this.scanRadius > maxChunkRadius) {
                                    this.refreshMaterialCounts();
                                    Block exhaustedBlock = this.getNextBlock();
                                    if (exhaustedBlock != null && this.remainingMaterials.getOrDefault(exhaustedBlock, 0) == 0) {
                                        this.remainingMaterials.remove(exhaustedBlock);
                                        this.scanRadius = -1;
                                        this.ringIndex = 0;
                                        this.pathGoal = null;
                                        this.bestCandidate = null;
                                        return;
                                    }

                                    if (exhaustedBlock == null) {
                                        this.info("All blocks placed!", new Object[0]);
                                        if (this.playSound.get()) {
                                            VersionHelper.get().playSoundPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE);
                                        }

                                        this.toggle();
                                    } else {
                                        this.info("Cannot reach any blocks to place, disabling...", new Object[0]);
                                        if (this.playSound.get()) {
                                            VersionHelper.get().playSoundPlayer(SoundEvents.ENTITY_VILLAGER_NO);
                                        }

                                        this.toggle();
                                    }

                                    return;
                                }

                                ChunkPos playerChunk = this.client.player.getChunkPos();
                                int currentRingSize = this.scanRadius == 0 ? 1 : 8 * this.scanRadius;
                                int chunksThisTick = Math.max(1, (currentRingSize + 3) / 4);

                                for (int bi = 0; bi < chunksThisTick && this.pathGoal == null && this.scanRadius <= maxChunkRadius; bi++) {
                                    ChunkPos toScan = this.getRingChunk(playerChunk, this.scanRadius, this.ringIndex);
                                    BlockPos candidate = this.findClosestInChunk(toScan);
                                    if (candidate != null
                                        && (
                                            this.bestCandidate == null
                                                || this.client.player.squaredDistanceTo(candidate.getX() + 0.5, candidate.getY() + 0.5, candidate.getZ() + 0.5)
                                                    < this.client
                                                        .player
                                                        .squaredDistanceTo(
                                                            this.bestCandidate.getX() + 0.5, this.bestCandidate.getY() + 0.5, this.bestCandidate.getZ() + 0.5
                                                        )
                                        )) {
                                        this.bestCandidate = candidate;
                                    }

                                    int ringSize = this.scanRadius == 0 ? 1 : 8 * this.scanRadius;
                                    this.ringIndex++;
                                    if (this.ringIndex >= ringSize) {
                                        this.ringIndex = 0;
                                        this.scanRadius++;
                                        if (this.bestCandidate != null) {
                                            this.pathGoal = this.bestCandidate;
                                            this.bestCandidate = null;
                                            if (this.debugMode.get()) {
                                                this.info(
                                                    "[DBG-PF] new goal from ring %d: %s (dist=%.1f)",
                                                    new Object[]{
                                                        this.scanRadius - 1,
                                                        this.pathGoal.toShortString(),
                                                        Math.sqrt(
                                                            this.client
                                                                .player
                                                                .squaredDistanceTo(
                                                                    this.pathGoal.getX() + 0.5, this.pathGoal.getY() + 0.5, this.pathGoal.getZ() + 0.5
                                                                )
                                                        )
                                                    }
                                                );
                                            }

                                            BaritoneHelper.setGoalAndPath(new GoalNear(this.pathGoal, 2));
                                            return;
                                        }

                                        currentRingSize = this.scanRadius == 0 ? 1 : 8 * this.scanRadius;
                                        chunksThisTick = Math.max(1, (currentRingSize + 3) / 4);
                                    }
                                }
                            } else if (!this.chunkTargets.isEmpty()) {
                                BlockPos closest = this.findClosestChunkTarget(this.chunkTargets);
                                if (closest != null) {
                                    if (HighwayUtils.horizontalDistance(this.client.player.getBlockPos(), closest) > 2.5) {
                                        BaritoneHelper.setGoalAndPath(new GoalNear(closest, 2));
                                        return;
                                    }

                                    PlayerControlUtils.cancelBaritone();
                                    if (this.client.player.getBlockPos() == closest) {
                                        BaritoneHelper.setGoalAndPath(new GoalBlock(closest.east()));
                                    }
                                }
                            }
                        } else {
                            this.wasPlacing = true;
                        }
                    }

                    if (this.swapTimer > 0) {
                        this.swapTimer--;
                    } else {
                        if (!this.posAndBlocks.isEmpty()) {
                            Pair<BlockPos, BlockState> firstPair = null;

                            for (Pair<BlockPos, BlockState> candidatex : this.posAndBlocks) {
                                if (this.canPlaceSafely((BlockPos)candidatex.first(), (BlockState)candidatex.second())) {
                                    firstPair = candidatex;
                                    break;
                                }
                            }

                            if (this.debugMode.get()) {
                                if (firstPair == null) {
                                    this.info("[DBG] posAndBlocks has %d entries but all failed canPlaceSafely", new Object[]{this.posAndBlocks.size()});
                                } else {
                                    this.info(
                                        "[DBG] firstPair=%s, swap=%d, rotationTimer=%d, hand=%s",
                                        new Object[]{
                                            ((BlockPos)firstPair.first()).toShortString(),
                                            this.swapTimer,
                                            this.rotationTimer,
                                            this.client.player.getMainHandStack().getItem()
                                        }
                                    );
                                }
                            }

                            label578:
                            if (firstPair != null) {
                                BlockState firstState = (BlockState)firstPair.second();
                                Block firstBlock = firstState.getBlock();
                                Printer.PlacementStrategy firstStrat = this.getStrategy(firstState);
                                if (this.rotationTimer > 0) {
                                    this.rotationTimer--;
                                    if (this.isForcedRotation() && this.pendingRotation != null) {
                                        this.applyRotation(this.pendingRotation);
                                    }

                                    return;
                                }

                                Printer.PlacementStrategy appliedStrategy = this.pendingRotation;
                                this.pendingRotation = null;
                                boolean rotationApplied = appliedStrategy != null;
                                boolean needsRotation = this.isForcedRotation() && (firstStrat.yawRequired() != null || firstStrat.pitchRequired() != null);
                                if (needsRotation) {
                                    if (this.isForcedRotation()) {
                                        if (!rotationApplied) {
                                            this.applyRotation(firstStrat);
                                            this.pendingRotation = firstStrat;
                                            this.rotationTimer = this.rotationDelay.get();
                                            if (this.debugMode.get()) {
                                                this.info(
                                                    "[DBG] Waiting for rotation: yaw=%s pitch=%s",
                                                    new Object[]{firstStrat.yawRequired(), firstStrat.pitchRequired()}
                                                );
                                            }

                                            return;
                                        }

                                        this.applyRotation(firstStrat);
                                    } else if (!this.isFacingCorrect(firstStrat)) {
                                        if (this.debugMode.get()) {
                                            this.info(
                                                "[DBG] Wrong facing for %s — waiting (yaw=%s pitch=%s)",
                                                new Object[]{
                                                    Registries.BLOCK.getId(firstBlock).getPath(), firstStrat.yawRequired(), firstStrat.pitchRequired()
                                                }
                                            );
                                        }

                                        this.swapTimer = MusheorSystem.Manager.swapDelay.get();
                                        return;
                                    }
                                }

                                try (BlockPlacer.PlaceBatch batch = BlockPlacer.beginBatch(firstBlock, state.getPlacementType())) {
                                    if (batch.isReady()) {
                                        for (Pair<BlockPos, BlockState> pair : this.posAndBlocks) {
                                            if (!BlockUtils.canPlace((BlockPos)pair.first(), true)) {
                                                if (this.debugMode.get()) {
                                                    this.info(
                                                        "[DBG] %s @ %s — inner skip: canPlace=false",
                                                        new Object[]{
                                                            Registries.BLOCK.getId(((BlockState)pair.second()).getBlock()).getPath(),
                                                            ((BlockPos)pair.first()).toShortString()
                                                        }
                                                    );
                                                }
                                            } else {
                                                Printer.PlacementStrategy pairStrat = this.getStrategy((BlockState)pair.second());
                                                if (!this.isInPlacementRange((BlockPos)pair.first(), pairStrat)) {
                                                    if (this.debugMode.get()) {
                                                        this.info(
                                                            "[DBG] %s @ %s — inner skip: out of range (hitDist=%.2f, range=%.1f)",
                                                            new Object[]{
                                                                Registries.BLOCK.getId(((BlockState)pair.second()).getBlock()).getPath(),
                                                                ((BlockPos)pair.first()).toShortString(),
                                                                Math.sqrt(
                                                                    this.client
                                                                        .player
                                                                        .getEyePos()
                                                                        .squaredDistanceTo(
                                                                            this.getPlacementHit(
                                                                                    (BlockPos)pair.first(), pairStrat, this.canUseFreeHit(pairStrat)
                                                                                )
                                                                                .getPos()
                                                                        )
                                                                ),
                                                                MusheorSystem.Manager.placementRange.get()
                                                            }
                                                        );
                                                    }
                                                } else if (((BlockState)pair.second()).getBlock() != firstBlock) {
                                                    if (this.debugMode.get()) {
                                                        this.info(
                                                            "[DBG] %s @ %s — inner skip: wrong block type (want %s)",
                                                            new Object[]{
                                                                Registries.BLOCK.getId(((BlockState)pair.second()).getBlock()).getPath(),
                                                                ((BlockPos)pair.first()).toShortString(),
                                                                Registries.BLOCK.getId(firstBlock).getPath()
                                                            }
                                                        );
                                                    }
                                                } else if (!this.canPlaceSafely((BlockPos)pair.first(), (BlockState)pair.second())) {
                                                    if (this.debugMode.get()) {
                                                        this.info(
                                                            "[DBG] %s @ %s — inner skip: canPlaceSafely=false",
                                                            new Object[]{
                                                                Registries.BLOCK.getId(firstBlock).getPath(), ((BlockPos)pair.first()).toShortString()
                                                            }
                                                        );
                                                    }
                                                } else {
                                                    boolean pairNeedsRotation = this.isForcedRotation()
                                                        && (pairStrat.yawRequired() != null || pairStrat.pitchRequired() != null);
                                                    if (pairNeedsRotation) {
                                                        boolean mismatch;
                                                        if (this.isForcedRotation() && rotationApplied) {
                                                            mismatch = pairStrat.yawRequired() != appliedStrategy.yawRequired()
                                                                || !Objects.equals(pairStrat.pitchRequired(), appliedStrategy.pitchRequired());
                                                        } else {
                                                            mismatch = !this.isFacingCorrect(pairStrat);
                                                        }

                                                        if (mismatch) {
                                                            if (!this.isForcedRotation()) {
                                                                this.swapTimer = MusheorSystem.Manager.swapDelay.get();
                                                            }

                                                            return;
                                                        }
                                                    }

                                                    if (!PacketLimiter.canSendPlacePacket()) {
                                                        if (this.debugMode.get()) {
                                                            this.info("[DBG] canInteract()=false, skipping placement this tick", new Object[0]);
                                                        }

                                                        return;
                                                    }

                                                    boolean freeHit = this.canUseFreeHit(pairStrat);
                                                    BlockHitResult hit = this.getPlacementHit((BlockPos)pair.first(), pairStrat, freeHit);
                                                    if (!batch.place((BlockPos)pair.first(), hit, freeHit)) {
                                                        return;
                                                    }

                                                    if (((BlockState)pair.second()).getBlock() == Blocks.OBSIDIAN) {
                                                        HighwayState.getInstance().incrementObsidianPlaced();
                                                    }

                                                    state.getPendingPlacements().put((BlockPos)pair.first(), this.tickCounter);
                                                    if (this.selectionType.get() == Printer.SelectionType.Litematica) {
                                                        this.remainingMaterials
                                                            .computeIfPresent(((BlockState)pair.second()).getBlock(), (k, v) -> v <= 1 ? null : v - 1);
                                                    }
                                                }
                                            }
                                        }
                                        break label578;
                                    }

                                    if (this.debugMode.get()) {
                                        this.info(
                                            "[DBG] Wrong item in hand (%s), switching to %s",
                                            new Object[]{this.client.player.getMainHandStack().getItem(), firstBlock.asItem()}
                                        );
                                    }

                                    this.swapTimer = MusheorSystem.Manager.swapDelay.get();
                                }

                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    private void buildRestockQueue(Block primaryBlock) {
        this.restockQueue.clear();
        this.restockQueue.add(primaryBlock.asItem());
        Set<Item> seen = new HashSet<>();
        seen.add(primaryBlock.asItem());
        List<Entry<Item, Integer>> others = new ArrayList<>();
        if (this.selectionType.get() == Printer.SelectionType.Litematica) {
            for (Entry<Block, Integer> entry : this.remainingMaterials.entrySet()) {
                if (entry.getValue() > 0) {
                    Item item = entry.getKey().asItem();
                    if (!seen.contains(item)) {
                        seen.add(item);
                        if (InventoryManager.countItem(item) <= 0 && RestockConfig.hasContainers(item)) {
                            others.add(Map.entry(item, entry.getValue()));
                        }
                    }
                }
            }
        } else {
            ObjectIterator var9 = this.chunkTargets.values().iterator();

            while (var9.hasNext()) {
                Map<BlockPos, BlockState> posMap = (Map<BlockPos, BlockState>)var9.next();

                for (BlockState blockState : posMap.values()) {
                    Item item = blockState.getBlock().asItem();
                    if (!seen.contains(item)) {
                        seen.add(item);
                        if (InventoryManager.countItem(item) <= 0 && RestockConfig.hasContainers(item)) {
                            others.add(Map.entry(item, this.getRemainingCount(blockState.getBlock())));
                        }
                    }
                }
            }
        }

        others.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        for (Entry<Item, Integer> entryx : others) {
            this.restockQueue.add(entryx.getKey());
        }
    }

    private BlockPos findClosestContainer(Item item) {
        List<BlockPos> containers = RestockConfig.getContainers(item);
        BlockPos playerPos = this.client.player.getBlockPos();
        BlockPos closest = null;
        double closestDist = Double.MAX_VALUE;

        for (BlockPos pos : containers) {
            if (!this.emptyContainers.contains(pos)) {
                double dist = playerPos.getSquaredDistance(pos);
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = pos;
                }
            }
        }

        return closest;
    }

    private int getRemainingCount(Block block) {
        if (this.selectionType.get() == Printer.SelectionType.Litematica) {
            return this.remainingMaterials.getOrDefault(block, 0);
        } else {
            int count = 0;
            ObjectIterator var3 = this.chunkTargets.values().iterator();

            while (var3.hasNext()) {
                Map<BlockPos, BlockState> posMap = (Map<BlockPos, BlockState>)var3.next();

                for (BlockState blockState : posMap.values()) {
                    if (blockState.getBlock() == block) {
                        count++;
                    }
                }
            }

            return count;
        }
    }

    private Block getNextBlock() {
        Block firstInInventory = null;
        String firstInInventoryName = null;
        Block firstOverall = null;
        String firstOverallName = null;
        Iterable<Block> blockSource;
        if (this.selectionType.get() == Printer.SelectionType.Litematica) {
            blockSource = this.remainingMaterials.keySet();
        } else {
            Set<Block> fromMap = new HashSet<>();
            ObjectIterator b = this.chunkTargets.values().iterator();

            while (b.hasNext()) {
                Map<BlockPos, BlockState> posMap = (Map<BlockPos, BlockState>)b.next();

                for (BlockState s : posMap.values()) {
                    fromMap.add(s.getBlock());
                }
            }

            blockSource = fromMap;
        }

        for (Block b : blockSource) {
            String name = Registries.BLOCK.getId(b).getPath();
            if (firstOverall == null || name.compareTo(firstOverallName) < 0) {
                firstOverall = b;
                firstOverallName = name;
            }

            if (InventoryManager.countItem(b.asItem()) > 0 && (firstInInventory == null || name.compareTo(firstInInventoryName) < 0)) {
                firstInInventory = b;
                firstInInventoryName = name;
            }
        }

        if (firstInInventory != null) {
            return firstInInventory;
        } else if (firstOverall != null) {
            return firstOverall;
        } else {
            return this.selectionType.get() == Printer.SelectionType.Baritone ? this.block.get() : null;
        }
    }

    private void refreshMaterialCounts() {
        if (LitematicaHelper.isLoaded() && this.selectionType.get() == Printer.SelectionType.Litematica) {
            Map<Block, Integer> fresh = LitematicaHelper.get().getRemainingMaterialCounts(this.ignoredBlockList.get(), this.onlyAir.get());
            fresh.entrySet().removeIf(e -> e.getValue() <= 0);
            this.remainingMaterials = fresh;
        }
    }

    private Printer.PlacementStrategy getStrategy(BlockState targetState) {
        return this.strategyCache.computeIfAbsent(targetState, this::computeStrategy);
    }

    private Printer.PlacementStrategy computeStrategy(BlockState targetState) {
        if (this.client.player != null && this.client.world != null) {
            boolean hasOrientation = ORIENTATION_PROPERTIES.stream().anyMatch(targetState::contains);
            if (!hasOrientation) {
                return new Printer.PlacementStrategy(Direction.DOWN, 0.5, null, null, true);
            } else {
                Item item = targetState.getBlock().asItem();
                if (item == Items.AIR) {
                    return new Printer.PlacementStrategy(Direction.DOWN, 0.5, null, null, true);
                } else {
                    ItemStack stack = new ItemStack(item);
                    BlockPos simPos = this.client.player.getBlockPos();
                    float savedYaw = this.client.player.getYaw();
                    float savedPitch = this.client.player.getPitch();

                    try {
                        for (Direction hitDir : Direction.values()) {
                            double[] yFracs = hitDir.getAxis() == Axis.Y ? new double[]{hitDir == Direction.UP ? 1.0 : 0.0} : HIT_Y_FRACTIONS;

                            for (double hitFracY : yFracs) {
                                Vec3d hitVec = new Vec3d(
                                    simPos.getX() + 0.5 + hitDir.getOffsetX() * 0.5, simPos.getY() + hitFracY, simPos.getZ() + 0.5 + hitDir.getOffsetZ() * 0.5
                                );
                                BlockHitResult hit = new BlockHitResult(hitVec, hitDir, simPos, false);
                                boolean anyMatch = false;
                                boolean mismatchFound = false;

                                label292:
                                for (Direction yawDir : HORIZONTAL_DIRECTIONS) {
                                    this.client.player.setYaw(this.getYawForDirection(yawDir));

                                    for (float pitch : TEST_PITCHES) {
                                        this.client.player.setPitch(pitch);
                                        ItemPlacementContext ctx = new ItemPlacementContext(this.client.world, this.client.player, Hand.MAIN_HAND, stack, hit) {};
                                        BlockState placed = invokeGetPlacementState(targetState.getBlock(), ctx);
                                        if (placed != null) {
                                            if (!this.orientationMatches(placed, targetState)) {
                                                mismatchFound = true;
                                                break label292;
                                            }

                                            anyMatch = true;
                                        }
                                    }
                                }

                                if (anyMatch && !mismatchFound) {
                                    return new Printer.PlacementStrategy(hitDir, hitFracY, null, null, false);
                                }
                            }
                        }

                        for (Direction hitDir : Direction.values()) {
                            double[] yFracs = hitDir.getAxis() == Axis.Y ? new double[]{hitDir == Direction.UP ? 1.0 : 0.0} : HIT_Y_FRACTIONS;

                            for (double hitFracY : yFracs) {
                                Vec3d hitVec = new Vec3d(
                                    simPos.getX() + 0.5 + hitDir.getOffsetX() * 0.5, simPos.getY() + hitFracY, simPos.getZ() + 0.5 + hitDir.getOffsetZ() * 0.5
                                );
                                BlockHitResult hit = new BlockHitResult(hitVec, hitDir, simPos, false);

                                for (Direction yawDir : HORIZONTAL_DIRECTIONS) {
                                    this.client.player.setYaw(this.getYawForDirection(yawDir));

                                    for (float pitchx : TEST_PITCHES) {
                                        this.client.player.setPitch(pitchx);
                                        ItemPlacementContext ctx = new ItemPlacementContext(this.client.world, this.client.player, Hand.MAIN_HAND, stack, hit) {};
                                        BlockState placed = invokeGetPlacementState(targetState.getBlock(), ctx);
                                        if (placed != null && this.orientationMatches(placed, targetState)) {
                                            boolean needsH = targetState.contains(Properties.HORIZONTAL_FACING)
                                                || targetState.contains(Properties.FACING) && targetState.get(Properties.FACING).getAxis().isHorizontal();
                                            boolean pitchMatters = false;

                                            for (float otherPitch : TEST_PITCHES) {
                                                if (otherPitch != pitchx) {
                                                    this.client.player.setPitch(otherPitch);
                                                    ItemPlacementContext ctx2 = new ItemPlacementContext(
                                                        this.client.world, this.client.player, Hand.MAIN_HAND, stack, hit
                                                    ) {};
                                                    BlockState placed2 = invokeGetPlacementState(targetState.getBlock(), ctx2);
                                                    if (placed2 == null || !this.orientationMatches(placed2, targetState)) {
                                                        pitchMatters = true;
                                                        break;
                                                    }
                                                }
                                            }

                                            this.client.player.setPitch(pitchx);
                                            Direction reqYaw = needsH ? yawDir : null;
                                            Float reqPitch = pitchMatters ? pitchx : null;
                                            return new Printer.PlacementStrategy(hitDir, hitFracY, reqYaw, reqPitch, false);
                                        }
                                    }
                                }
                            }
                        }

                        return new Printer.PlacementStrategy(Direction.DOWN, 0.5, null, null, false);
                    } finally {
                        this.client.player.setYaw(savedYaw);
                        this.client.player.setPitch(savedPitch);
                    }
                }
            }
        } else {
            return new Printer.PlacementStrategy(Direction.DOWN, 0.5, null, null, true);
        }
    }

    private boolean orientationMatches(BlockState placed, BlockState target) {
        if (placed.getBlock() != target.getBlock()) {
            return false;
        } else {
            for (Property<?> prop : ORIENTATION_PROPERTIES) {
                if (placed.contains(prop) && target.contains(prop) && !placed.get(prop).equals(target.get(prop))) {
                    return false;
                }
            }

            return true;
        }
    }

    private boolean isFacingCorrect(Printer.PlacementStrategy strat) {
        if (this.client.player == null) {
            return false;
        } else {
            float effYaw = RotationManager.yawOr(this.client.player.getYaw());
            float effPitch = RotationManager.pitchOr(this.client.player.getPitch());
            if (strat.yawRequired() != null && getDirectionFromYaw(effYaw) != strat.yawRequired()) {
                return false;
            } else {
                if (strat.pitchRequired() != null) {
                    float required = strat.pitchRequired();
                    boolean reqUp = required < -45.0F;
                    boolean reqDown = required > 45.0F;
                    boolean isUp = effPitch < -45.0F;
                    boolean isDown = effPitch > 45.0F;
                    if (reqUp && !isUp) {
                        return false;
                    }

                    if (reqDown && !isDown) {
                        return false;
                    }

                    if (!reqUp && !reqDown && (isUp || isDown)) {
                        return false;
                    }
                }

                return true;
            }
        }
    }

    private float getYawForDirection(Direction facing) {
        return switch (facing) {
            case SOUTH -> 0.0F;
            case WEST -> 90.0F;
            case NORTH -> 180.0F;
            case EAST -> -90.0F;
            default -> this.client.player.getYaw();
        };
    }

    private static Direction getDirectionFromYaw(float yaw) {
        float normalized = (yaw % 360.0F + 360.0F) % 360.0F;
        if (normalized >= 315.0F || normalized < 45.0F) {
            return Direction.SOUTH;
        } else if (normalized < 135.0F) {
            return Direction.WEST;
        } else {
            return normalized < 225.0F ? Direction.NORTH : Direction.EAST;
        }
    }

    private void applyRotation(Printer.PlacementStrategy strat) {
        if (strat.yawRequired() != null) {
            RotationManager.setYaw(this.getYawForDirection(strat.yawRequired()), 10);
        }

        if (strat.pitchRequired() != null) {
            RotationManager.setPitch(RotationManager.snapPitch(strat.pitchRequired()), 10);
        }
    }

    private void fixBlockStates() {
        if (this.setCorrectStates.get()) {
            if (this.selectionType.get() == Printer.SelectionType.Litematica && LitematicaHelper.isLoaded()) {
                if (this.client.player != null && this.client.world != null) {
                    if (!this.client.player.isSneaking() && this.client.currentScreen == null) {
                        if (this.tickCounter % 200 == 0) {
                            this.stateWaitUntil.entrySet().removeIf(e -> this.tickCounter >= e.getValue());
                            this.stateLastNeeded.keySet().removeIf(this::isOutOfTrackingRange);
                            this.stateStallRounds.keySet().removeIf(this::isOutOfTrackingRange);
                        }

                        if (this.stateDelayTimer > 0) {
                            this.stateDelayTimer--;
                        } else {
                            BlockPos playerPos = this.client.player.getBlockPos();
                            int range = (int)Math.ceil(this.getInteractRange()) + 1;
                            Map<BlockPos, BlockState> mismatched = LitematicaHelper.get()
                                .getMismatchedStatesInBox(
                                    playerPos.add(-range, -range, -range), playerPos.add(range, range, range), this.ignoredBlockList.get(), 512
                                );
                            if (!mismatched.isEmpty()) {
                                List<Entry<BlockPos, BlockState>> targets = new ArrayList<>(mismatched.entrySet());
                                targets.sort(
                                    Comparator.comparingDouble(
                                        e -> this.client.player.squaredDistanceTo(e.getKey().getX() + 0.5, e.getKey().getY() + 0.5, e.getKey().getZ() + 0.5)
                                    )
                                );
                                boolean batched = this.setStateDelay.get() == 0;

                                for (Entry<BlockPos, BlockState> entry : targets) {
                                    BlockPos pos = entry.getKey();
                                    BlockState target = entry.getValue();
                                    if (!this.stateGivenUp.contains(pos)
                                        && !this.ignoredBlockstatesList.get().contains(target.getBlock())
                                        && (this.layerType.get() != Printer.LayerType.OnlyBelowFeet || !(pos.getY() >= Math.floor(this.client.player.getY())))
                                        && (this.layerType.get() != Printer.LayerType.LitematicaLayer || LitematicaHelper.get().isPositionInRenderLayer(pos))) {
                                        Integer waitUntil = this.stateWaitUntil.get(pos);
                                        if (waitUntil != null) {
                                            if (this.tickCounter < waitUntil) {
                                                continue;
                                            }

                                            this.stateWaitUntil.remove(pos);
                                        }

                                        int needed = BlockStateCycler.getInteractionsNeeded(this.client.world.getBlockState(pos), target);
                                        if (needed != -1 && needed != 0) {
                                            if (this.isInInteractRange(pos)) {
                                                if (!PacketLimiter.canSendPlacePacket()) {
                                                    break;
                                                }

                                                Integer lastNeeded = this.stateLastNeeded.get(pos);
                                                if (lastNeeded != null && needed >= lastNeeded) {
                                                    if (this.stateStallRounds.merge(pos, 1, Integer::sum) >= 3) {
                                                        this.stateGivenUp.add(pos);
                                                        if (this.debugMode.get()) {
                                                            this.info(
                                                                "[DBG-STATE] %s @ %s — giving up, %d rounds changed nothing",
                                                                new Object[]{Registries.BLOCK.getId(target.getBlock()).getPath(), pos.toShortString(), 3}
                                                            );
                                                        }
                                                        continue;
                                                    }
                                                } else {
                                                    this.stateStallRounds.remove(pos);
                                                }

                                                this.stateLastNeeded.put(pos, needed);
                                                BlockHitResult hit = this.getInteractHit(pos);
                                                int sent = 0;
                                                if (!batched) {
                                                    BlockUtils.interact(hit, Hand.MAIN_HAND, true);
                                                    sent = 1;
                                                    this.stateDelayTimer = this.setStateDelay.get();
                                                    this.stateWaitUntil.put(pos, this.tickCounter + this.setStateDelay.get());
                                                } else {
                                                    while (sent < needed && PacketLimiter.canSendPlacePacket()) {
                                                        BlockUtils.interact(hit, Hand.MAIN_HAND, true);
                                                        sent++;
                                                    }

                                                    this.stateWaitUntil.put(pos, this.tickCounter + 5);
                                                }

                                                if (this.debugMode.get()) {
                                                    this.info(
                                                        "[DBG-STATE] %s @ %s — sent %d/%d interactions",
                                                        new Object[]{Registries.BLOCK.getId(target.getBlock()).getPath(), pos.toShortString(), sent, needed}
                                                    );
                                                }

                                                if (!batched || sent < needed) {
                                                    break;
                                                }
                                            }
                                        } else {
                                            this.stateLastNeeded.remove(pos);
                                            this.stateStallRounds.remove(pos);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isOutOfTrackingRange(BlockPos pos) {
        return this.client.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 1024.0;
    }

    private double getInteractRange() {
        return Math.min(MusheorSystem.Manager.placementRange.get(), this.client.player.getBlockInteractionRange());
    }

    private boolean isInInteractRange(BlockPos pos) {
        double range = this.getInteractRange();
        return this.client.player.getEyePos().squaredDistanceTo(this.getInteractHit(pos).getPos()) <= range * range;
    }

    private BlockHitResult getInteractHit(BlockPos pos) {
        Vec3d eye = this.client.player.getEyePos();
        BlockState state = this.client.world.getBlockState(pos);
        double minX = pos.getX();
        double minY = pos.getY();
        double minZ = pos.getZ();
        double maxX = pos.getX() + 1.0;
        double maxY = pos.getY() + 1.0;
        double maxZ = pos.getZ() + 1.0;
        VoxelShape shape = state.getOutlineShape(this.client.world, pos);
        if (!shape.isEmpty()) {
            Box box = shape.getBoundingBox();
            minX = pos.getX() + box.minX;
            maxX = pos.getX() + box.maxX;
            minY = pos.getY() + box.minY;
            maxY = pos.getY() + box.maxY;
            minZ = pos.getZ() + box.minZ;
            maxZ = pos.getZ() + box.maxZ;
        }

        Vec3d hit = new Vec3d(Math.max(minX, Math.min(eye.x, maxX)), Math.max(minY, Math.min(eye.y, maxY)), Math.max(minZ, Math.min(eye.z, maxZ)));
        return new BlockHitResult(hit, KekNuker.getClosestFace(eye, pos), pos, false);
    }

    private boolean canUseFreeHit(Printer.PlacementStrategy strat) {
        return !this.isForcedRotation() && strat.orientationFree();
    }

    private BlockHitResult getPlacementHit(BlockPos pos, Printer.PlacementStrategy strat, boolean freeHit) {
        Vec3d eye = this.client.player.getEyePos();
        if (freeHit) {
            double hx = Math.max((double)pos.getX(), Math.min(eye.x, pos.getX() + 1.0));
            double hy = Math.max((double)pos.getY(), Math.min(eye.y, pos.getY() + 1.0));
            double hz = Math.max((double)pos.getZ(), Math.min(eye.z, pos.getZ() + 1.0));
            return new BlockHitResult(new Vec3d(hx, hy, hz), KekNuker.getClosestFace(eye, pos), pos, false);
        } else {
            Direction dir = strat.hitDir();
            double hx = pos.getX() + 0.5 + dir.getOffsetX() * 0.5;
            double hy = pos.getY() + strat.hitFracY();
            double hz = pos.getZ() + 0.5 + dir.getOffsetZ() * 0.5;
            if (dir.getAxis() != Axis.X) {
                hx = Math.max(pos.getX() + 0.05, Math.min(eye.x, pos.getX() + 0.95));
            }

            if (dir.getAxis() != Axis.Z) {
                hz = Math.max(pos.getZ() + 0.05, Math.min(eye.z, pos.getZ() + 0.95));
            }

            return new BlockHitResult(new Vec3d(hx, hy, hz), dir, pos, false);
        }
    }

    private boolean isInPlacementRange(BlockPos pos, Printer.PlacementStrategy strat) {
        Vec3d eye = this.client.player.getEyePos();
        Vec3d hit = this.getPlacementHit(pos, strat, this.canUseFreeHit(strat)).getPos();
        double range = MusheorSystem.Manager.placementRange.get();
        return eye.squaredDistanceTo(hit) <= range * range;
    }

    private boolean canPlaceSafely(BlockPos pos, BlockState targetState) {
        if (targetState.getBlock() instanceof LanternBlock) {
            boolean hanging = targetState.get(Properties.HANGING);
            BlockPos attach = hanging ? pos.up() : pos.down();
            return !this.client.world.getBlockState(attach).isAir();
        } else if (targetState.getBlock() instanceof RedstoneWireBlock) {
            return !this.client.world.getBlockState(pos.down()).isAir();
        } else {
            return this.gravityCheck.get() && targetState.getBlock() instanceof FallingBlock ? !this.client.world.getBlockState(pos.down()).isAir() : true;
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

    private BlockPos findClosestInChunk(ChunkPos chunk) {
        if (this.client.world != null && this.client.player != null) {
            if (!this.client.world.isChunkLoaded(chunk.x, chunk.z)) {
                return null;
            } else {
                int minX = chunk.getStartX();
                int minZ = chunk.getStartZ();
                int maxX = chunk.getEndX();
                int maxZ = chunk.getEndZ();
                int minY = this.client.world.getBottomY();
                int maxY = this.client.world.getBottomY() + this.client.world.getHeight() - 1;
                Map<BlockPos, BlockState> candidates = LitematicaHelper.get()
                    .getBlocksInBox(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), this.onlyAir.get(), this.ignoredBlockList.get(), 512);
                if (candidates.isEmpty()) {
                    return null;
                } else {
                    BlockPos playerFeet = this.client.player.getBlockPos();
                    BlockPos closest = null;
                    double closestDist = Double.MAX_VALUE;

                    for (Entry<BlockPos, BlockState> entry : candidates.entrySet()) {
                        BlockPos pos = entry.getKey();
                        BlockState targetState = entry.getValue();
                        if (InventoryManager.countItem(targetState.getBlock().asItem()) > 0
                            && (this.layerType.get() != Printer.LayerType.OnlyBelowFeet || !(pos.getY() >= Math.floor(this.client.player.getY())))
                            && (this.layerType.get() != Printer.LayerType.LitematicaLayer || LitematicaHelper.get().isPositionInRenderLayer(pos))
                            && !pos.equals(playerFeet)
                            && !pos.equals(playerFeet.up())) {
                            double dist = this.client.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                            if (dist < closestDist) {
                                closestDist = dist;
                                closest = pos;
                            }
                        }
                    }

                    return closest;
                }
            }
        } else {
            return null;
        }
    }

    private BlockPos findClosestChunkTarget(Long2ObjectMap<Map<BlockPos, BlockState>> chunkPositions) {
        if (this.client.player != null && this.client.world != null) {
            double px = this.client.player.getX();
            double py = this.client.player.getY();
            double pz = this.client.player.getZ();
            List<Entry<Long, Map<BlockPos, BlockState>>> chunks = new ArrayList<>();
            ObjectIterator var9 = chunkPositions.long2ObjectEntrySet().iterator();

            while (var9.hasNext()) {
                it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry<Map<BlockPos, BlockState>> entry = (it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry<Map<BlockPos, BlockState>>)var9.next();
                chunks.add(Map.entry(entry.getLongKey(), (Map<BlockPos, BlockState>)entry.getValue()));
            }

            chunks.sort(Comparator.comparingDouble(e -> {
                int cx = ChunkPos.getPackedX(e.getKey());
                int cz = ChunkPos.getPackedZ(e.getKey());
                double centerX = (cx << 4) + 8.0;
                double centerZ = (cz << 4) + 8.0;
                return (centerX - px) * (centerX - px) + (centerZ - pz) * (centerZ - pz);
            }));

            for (Entry<Long, Map<BlockPos, BlockState>> chunk : chunks) {
                Map<BlockPos, BlockState> posMap = chunk.getValue();
                if (posMap != null && !posMap.isEmpty()) {
                    BlockPos bestPos = null;
                    double bestDist = Double.MAX_VALUE;

                    for (Entry<BlockPos, BlockState> entry : posMap.entrySet()) {
                        BlockPos pos = entry.getKey();
                        BlockState targetState = entry.getValue();
                        if (this.client.world.getBlockState(pos).getBlock() != targetState.getBlock()
                            && this.client.world.getBlockState(pos).getBlock() instanceof AirBlock
                            && BlockUtils.canPlaceBlock(pos, true, targetState.getBlock())
                            && InventoryManager.countItem(targetState.getBlock().asItem()) > 0) {
                            double dx = pos.getX() + 0.5 - px;
                            double dy = pos.getY() + 0.5 - py;
                            double dz = pos.getZ() + 0.5 - pz;
                            double distSq = dx * dx + dy * dy + dz * dz;
                            if (distSq < bestDist) {
                                bestDist = distSq;
                                bestPos = pos;
                            }
                        }
                    }

                    if (bestPos != null) {
                        return bestPos;
                    }
                }
            }

            return null;
        } else {
            return null;
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (this.client.player != null && this.client.world != null) {
            if (MusheorSystem.Manager.placeRender.get() && this.render.get()) {
                if (!this.renderTargets.isEmpty()) {
                    List<Pair<BlockPos, Block>> renderBlocks = new ArrayList<>();

                    for (Pair<BlockPos, BlockState> pair : this.renderTargets) {
                        if (!this.renderPlaceableOnly.get() || BlockUtils.canPlace((BlockPos)pair.first())) {
                            renderBlocks.add(Pair.of((BlockPos)pair.first(), ((BlockState)pair.second()).getBlock()));
                        }
                    }

                    BlockRenderUtils.renderBlocks(event, renderBlocks);
                }
            }
        }
    }

    public static enum LayerType {
        All,
        OnlyBelowFeet,
        LitematicaLayer;
    }

    private record PlacementStrategy(Direction hitDir, double hitFracY, Direction yawRequired, Float pitchRequired, boolean orientationFree) {
    }

    private static enum RestockType {
        Disabled,
        RestockConfig,
        Refill;
    }

    private static enum RotationType {
        Ignored,
        Forced;
    }

    private static enum SelectionType {
        Baritone,
        Litematica;
    }
}
