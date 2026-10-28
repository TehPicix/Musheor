package musheor.utils.highway;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import musheor.utils.HighwayUtils;
import musheor.utils.RestockType;
import musheor.utils.system.MusheorSystem;
import net.minecraft.util.math.BlockPos;

public class HighwayState {
    private static final HighwayState INSTANCE = new HighwayState();
    private MusheorSystem.PlacementType placementType;
    private boolean airPlaceSwap;
    private HighwayUtils.HighwayDirection direction;
    private BlockPos startPos;
    private int startX;
    private int startZ;
    private Integer anchorX;
    private Integer anchorY;
    private Integer anchorZ;
    private Double alignX;
    private Double alignZ;
    private boolean restocking;
    private boolean outOfMaterials;
    private boolean inPlaceRange;
    private boolean placing;
    private boolean missingBlock;
    private boolean placementBlocked;
    private boolean unusedFlag;
    private boolean echestRestocking;
    private boolean shulkerRestocking;
    private boolean postRestocking;
    private boolean postEchestFarming;
    private boolean echestFarming;
    private RestockType restockType;
    private BlockPos echestFarmPos;
    private BlockPos lavaPos;
    private BlockPos lavaReturnPos;
    private BlockPos gatherReturnPos;
    private int runtimeTicks;
    private int rpcUpdateTicks;
    private int nonSilkPickaxeCount;
    private int pickaxeCount;
    private int obsidianPlaced;
    private int obsidianMined;
    private int unusedCounter;
    private int netherrackMined;
    private int echestsMined;
    private int lifetimeObsidianPlaced;
    private int lifetimeObsidianMined;
    private int lifetimeBlocksMined;
    private int lifetimeEchestsMined;
    private int lifetimeNetherrackMined;
    private final List<BlockPos> unusedPositionsA = new ArrayList<>();
    private final List<BlockPos> startPositions = new ArrayList<>();
    private final List<BlockPos> unusedPositionsB = new ArrayList<>();
    private final List<BlockPos> unusedPositionsC = new ArrayList<>();
    private final Map<BlockPos, Integer> pendingPlacements = new HashMap<>();
    private HighwayDetector.DetectedHighway detectedHighway;

    private HighwayState() {
    }

    public static HighwayState getInstance() {
        return INSTANCE;
    }

    public MusheorSystem.PlacementType getPlacementType() {
        return this.placementType != null ? this.placementType : MusheorSystem.Manager.placementType.get();
    }

    public void setPlacementType(MusheorSystem.PlacementType type) {
        this.placementType = type;
    }

    public void setAirPlaceSwap(boolean val) {
        this.airPlaceSwap = val;
    }

    public HighwayUtils.HighwayDirection getDirection() {
        return this.direction;
    }

    public void setDirection(HighwayUtils.HighwayDirection dir) {
        this.direction = dir;
    }

    public BlockPos getStartPos() {
        return this.startPos;
    }

    public void setStartPos(BlockPos pos) {
        this.startPos = pos;
    }

    public int getStartX() {
        return this.startX;
    }

    public void setStartX(int x) {
        this.startX = x;
    }

    public int getStartZ() {
        return this.startZ;
    }

    public void setStartZ(int z) {
        this.startZ = z;
    }

    public Integer getAnchorX() {
        return this.anchorX;
    }

    public void setAnchorX(Integer x) {
        this.anchorX = x;
    }

    public Integer getAnchorY() {
        return this.anchorY;
    }

    public void setAnchorY(Integer y) {
        this.anchorY = y;
    }

    public Integer getAnchorZ() {
        return this.anchorZ;
    }

    public void setAnchorZ(Integer z) {
        this.anchorZ = z;
    }

    public Double getAlignX() {
        return this.alignX;
    }

    public void setAlignX(Double x) {
        this.alignX = x;
    }

    public Double getAlignZ() {
        return this.alignZ;
    }

    public void setAlignZ(Double z) {
        this.alignZ = z;
    }

    public void setRestocking(boolean val) {
        this.restocking = val;
    }

    public boolean isOutOfMaterials() {
        return this.outOfMaterials;
    }

    public void setOutOfMaterials(boolean val) {
        this.outOfMaterials = val;
    }

    public boolean isInPlaceRange() {
        return this.inPlaceRange;
    }

    public void setInPlaceRange(boolean val) {
        this.inPlaceRange = val;
    }

    public boolean isPlacing() {
        return this.placing;
    }

    public void setPlacing(boolean val) {
        this.placing = val;
    }

    public boolean hasMissingBlock() {
        return this.missingBlock;
    }

    public void setMissingBlock(boolean val) {
        this.missingBlock = val;
    }

    public boolean isPlacementBlocked() {
        return this.placementBlocked;
    }

    public void setPlacementBlocked(boolean val) {
        this.placementBlocked = val;
    }

    public boolean isEchestRestocking() {
        return this.echestRestocking;
    }

    public void setEchestRestocking(boolean val) {
        this.echestRestocking = val;
    }

    public boolean isShulkerRestocking() {
        return this.shulkerRestocking;
    }

    public void setShulkerRestocking(boolean val) {
        this.shulkerRestocking = val;
    }

    public boolean isPostRestocking() {
        return this.postRestocking;
    }

    public void setPostRestocking(boolean val) {
        this.postRestocking = val;
    }

    public boolean isPostEchestFarming() {
        return this.postEchestFarming;
    }

    public void setPostEchestFarming(boolean val) {
        this.postEchestFarming = val;
    }

    public boolean isEchestFarming() {
        return this.echestFarming;
    }

    public void setEchestFarming(boolean val) {
        this.echestFarming = val;
    }

    public RestockType getRestockType() {
        return this.restockType;
    }

    public void setRestockType(RestockType type) {
        this.restockType = type;
    }

    public BlockPos getEchestFarmPos() {
        return this.echestFarmPos;
    }

    public void setEchestFarmPos(BlockPos pos) {
        this.echestFarmPos = pos;
    }

    public BlockPos getLavaPos() {
        return this.lavaPos;
    }

    public void setLavaPos(BlockPos pos) {
        this.lavaPos = pos;
    }

    public BlockPos getLavaReturnPos() {
        return this.lavaReturnPos;
    }

    public void setLavaReturnPos(BlockPos pos) {
        this.lavaReturnPos = pos;
    }

    public BlockPos getGatherReturnPos() {
        return this.gatherReturnPos;
    }

    public void setGatherReturnPos(BlockPos pos) {
        this.gatherReturnPos = pos;
    }

    public int getRuntimeTicks() {
        return this.runtimeTicks;
    }

    public void setRuntimeTicks(int val) {
        this.runtimeTicks = val;
    }

    public void incrementRuntimeTicks() {
        this.runtimeTicks++;
    }

    public int getRpcUpdateTicks() {
        return this.rpcUpdateTicks;
    }

    public void setRpcUpdateTicks(int val) {
        this.rpcUpdateTicks = val;
    }

    public void incrementRpcUpdateTicks() {
        this.rpcUpdateTicks++;
    }

    public int getNonSilkPickaxeCount() {
        return this.nonSilkPickaxeCount;
    }

    public void setNonSilkPickaxeCount(int val) {
        this.nonSilkPickaxeCount = val;
    }

    public int getPickaxeCount() {
        return this.pickaxeCount;
    }

    public void setPickaxeCount(int val) {
        this.pickaxeCount = val;
    }

    public int getObsidianPlaced() {
        return this.obsidianPlaced;
    }

    public void incrementObsidianPlaced() {
        this.obsidianPlaced++;
    }

    public int getObsidianMined() {
        return this.obsidianMined;
    }

    public void incrementObsidianMined() {
        this.obsidianMined++;
    }

    public int getNetherrackMined() {
        return this.netherrackMined;
    }

    public void incrementNetherrackMined() {
        this.netherrackMined++;
    }

    public int getEchestsMined() {
        return this.echestsMined;
    }

    public void incrementEchestsMined() {
        this.echestsMined++;
    }

    public int getLifetimeObsidianPlaced() {
        return this.lifetimeObsidianPlaced;
    }

    public void setLifetimeObsidianPlaced(int val) {
        this.lifetimeObsidianPlaced = val;
    }

    public int getLifetimeObsidianMined() {
        return this.lifetimeObsidianMined;
    }

    public void setLifetimeObsidianMined(int val) {
        this.lifetimeObsidianMined = val;
    }

    public int getLifetimeBlocksMined() {
        return this.lifetimeBlocksMined;
    }

    public void setLifetimeBlocksMined(int val) {
        this.lifetimeBlocksMined = val;
    }

    public int getLifetimeEchestsMined() {
        return this.lifetimeEchestsMined;
    }

    public void setLifetimeEchestsMined(int val) {
        this.lifetimeEchestsMined = val;
    }

    public int getLifetimeNetherrackMined() {
        return this.lifetimeNetherrackMined;
    }

    public void setLifetimeNetherrackMined(int val) {
        this.lifetimeNetherrackMined = val;
    }

    public List<BlockPos> getStartPositions() {
        return this.startPositions;
    }

    public Map<BlockPos, Integer> getPendingPlacements() {
        return this.pendingPlacements;
    }

    public HighwayDetector.DetectedHighway getDetectedHighway() {
        return this.detectedHighway;
    }

    public void setDetectedHighway(HighwayDetector.DetectedHighway h) {
        this.detectedHighway = h;
    }

    public void reset() {
        this.direction = null;
        this.startPos = null;
        this.startX = 0;
        this.startZ = 0;
        this.anchorX = null;
        this.anchorY = null;
        this.anchorZ = null;
        this.alignX = null;
        this.alignZ = null;
        this.restocking = false;
        this.outOfMaterials = false;
        this.inPlaceRange = false;
        this.placing = false;
        this.missingBlock = false;
        this.placementBlocked = false;
        this.unusedFlag = false;
        this.echestRestocking = false;
        this.shulkerRestocking = false;
        this.postRestocking = false;
        this.postEchestFarming = false;
        this.echestFarming = false;
        this.restockType = null;
        this.echestFarmPos = null;
        this.lavaPos = null;
        this.lavaReturnPos = null;
        this.gatherReturnPos = null;
        this.runtimeTicks = 0;
        this.rpcUpdateTicks = 0;
        this.nonSilkPickaxeCount = 0;
        this.pickaxeCount = 0;
        this.obsidianPlaced = 0;
        this.obsidianMined = 0;
        this.unusedCounter = 0;
        this.netherrackMined = 0;
        this.echestsMined = 0;
        this.unusedPositionsA.clear();
        this.startPositions.clear();
        this.unusedPositionsB.clear();
        this.unusedPositionsC.clear();
        this.pendingPlacements.clear();
        this.detectedHighway = null;
    }
}
