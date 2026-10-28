package musheor.utils.mapart;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import musheor.utils.HighwayUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;

public class MapArtGrid {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final MapArtGrid INSTANCE = new MapArtGrid();
    private MapArtGrid.Corner origin = MapArtGrid.Corner.TopLeft;
    private BlockPos anchorPos;
    private Direction facing;
    private Direction columnDirection;
    private Direction rowDirection;
    private int width;
    private int height;
    private int indexBase;
    private final Map<Long, MapArtGrid.Cell> cells = new LinkedHashMap<>();
    private final List<String> unmatchedMapNames = new ArrayList<>();
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d{1,4}");
    private static final Pattern BRACKET_GROUP_PATTERN = Pattern.compile("[(\\[{]([^)\\]}]*)[)\\]}]");

    public static MapArtGrid getInstance() {
        return INSTANCE;
    }

    public boolean isAnchored() {
        return this.anchorPos != null && this.facing != null;
    }

    public void setAnchor(ItemFrameEntity frame, MapArtGrid.Corner origin, float playerYaw) {
        this.origin = origin;
        this.anchorPos = frame.getAttachedBlockPos();
        this.facing = frame.getHorizontalFacing();
        if (this.facing.getAxis().isVertical()) {
            Direction away = HighwayUtils.getFacingDirection(playerYaw, 0.0F);
            this.rowDirection = origin == MapArtGrid.Corner.TopLeft ? away : away.getOpposite();
            this.columnDirection = this.rowDirection.rotateYClockwise();
            if (this.facing == Direction.DOWN) {
                this.columnDirection = this.columnDirection.getOpposite();
            }
        } else {
            this.columnDirection = this.facing.rotateYCounterclockwise();
            this.rowDirection = origin == MapArtGrid.Corner.TopLeft ? Direction.DOWN : Direction.UP;
        }

        this.cells.clear();
        this.unmatchedMapNames.clear();
    }

    public void clear() {
        this.anchorPos = null;
        this.facing = null;
        this.columnDirection = null;
        this.rowDirection = null;
        this.cells.clear();
        this.unmatchedMapNames.clear();
    }

    public MapArtGrid.Corner getOrigin() {
        return this.origin;
    }

    public BlockPos getAnchorPos() {
        return this.anchorPos;
    }

    public Direction getFacing() {
        return this.facing;
    }

    public Direction getColumnDirection() {
        return this.columnDirection;
    }

    public Direction getRowDirection() {
        return this.rowDirection;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public int getIndexBase() {
        return this.indexBase;
    }

    public void setSize(int width, int height) {
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
        this.cells.values().removeIf(cell -> this.width > 0 && cell.col >= this.width || this.height > 0 && cell.row >= this.height);
    }

    public void setIndexBase(int indexBase) {
        this.indexBase = indexBase;
    }

    public int scanFrames() {
        if (this.isAnchored() && mc.world != null) {
            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof ItemFrameEntity frame && frame.getHorizontalFacing() == this.facing) {
                    Vec3i delta = frame.getAttachedBlockPos().subtract(this.anchorPos);
                    if (projectOnto(delta, this.facing) == 0) {
                        int col = projectOnto(delta, this.columnDirection);
                        int row = projectOnto(delta, this.rowDirection);
                        if (col >= 0 && row >= 0 && (this.width <= 0 || col < this.width) && (this.height <= 0 || row < this.height)) {
                            MapArtGrid.Cell cell = this.cells
                                .computeIfAbsent(packKey(col, row), k -> new MapArtGrid.Cell(col, row, frame.getAttachedBlockPos()));
                            cell.frameId = frame.getId();
                            cell.filled = !frame.getHeldItemStack().isEmpty();
                        }
                    }
                }
            }

            return this.cells.size();
        } else {
            return this.cells.size();
        }
    }

    public void clearAttempts() {
        for (MapArtGrid.Cell cell : this.cells.values()) {
            cell.clearAttempt();
        }
    }

    public void refreshFilled() {
        for (MapArtGrid.Cell cell : this.cells.values()) {
            ItemFrameEntity frame = cell.getFrame();
            if (frame != null) {
                cell.filled = !frame.getHeldItemStack().isEmpty();
            }
        }
    }

    public Collection<MapArtGrid.Cell> getCells() {
        return this.cells.values();
    }

    public int getCellCount() {
        return this.cells.size();
    }

    public long countFilled() {
        return this.cells.values().stream().filter(c -> c.filled).count();
    }

    public boolean isComplete() {
        return !this.cells.isEmpty() && this.cells.values().stream().allMatch(c -> c.filled);
    }

    public List<MapArtGrid.Cell> getPendingCells() {
        List<MapArtGrid.Cell> pending = new ArrayList<>();

        for (MapArtGrid.Cell cell : this.cells.values()) {
            if (!cell.filled) {
                pending.add(cell);
            }
        }

        if (mc.player != null) {
            Vec3d eye = mc.player.getEyePos();
            pending.sort(Comparator.comparingDouble(cellx -> Vec3d.ofCenter(cellx.pos).squaredDistanceTo(eye)));
        }

        return pending;
    }

    public void matchInventoryMaps() {
        for (MapArtGrid.Cell cell : this.cells.values()) {
            cell.mapSlot = -1;
        }

        this.unmatchedMapNames.clear();
        if (mc.player != null) {
            for (int slot = 0; slot < 36; slot++) {
                ItemStack stack = mc.player.getInventory().getStack(slot);
                if (!stack.isEmpty() && stack.getItem() == Items.FILLED_MAP) {
                    String name = stack.getName().getString();
                    MapArtGrid.MapTarget target = parseMapName(name);
                    long key = target == null ? Long.MIN_VALUE : this.resolveCellKey(target);
                    MapArtGrid.Cell cell = key == Long.MIN_VALUE ? null : this.cells.get(key);
                    if (cell != null && !cell.filled && cell.mapSlot < 0) {
                        cell.mapSlot = slot;
                    } else if (cell == null) {
                        this.unmatchedMapNames.add(name);
                    }
                }
            }
        }
    }

    public List<String> getUnmatchedMapNames() {
        return this.unmatchedMapNames;
    }

    public static MapArtGrid.MapTarget parseMapName(String name) {
        if (name != null && !name.isBlank()) {
            Integer bracketedIndex = null;
            Matcher groups = BRACKET_GROUP_PATTERN.matcher(name);

            while (groups.find()) {
                List<Integer> nums = extractNumbers(groups.group(1));
                if (nums.size() >= 2) {
                    return new MapArtGrid.MapTarget.Grid(nums.get(0), nums.get(1));
                }

                if (nums.size() == 1 && bracketedIndex == null) {
                    bracketedIndex = nums.getFirst();
                }
            }

            if (bracketedIndex != null) {
                return new MapArtGrid.MapTarget.Index(bracketedIndex);
            } else {
                List<Integer> numsx = extractNumbers(name);
                if (numsx.size() >= 2) {
                    return new MapArtGrid.MapTarget.Grid(numsx.get(numsx.size() - 2), numsx.getLast());
                } else {
                    return numsx.size() == 1 ? new MapArtGrid.MapTarget.Index(numsx.getFirst()) : null;
                }
            }
        } else {
            return null;
        }
    }

    private static List<Integer> extractNumbers(String text) {
        List<Integer> nums = new ArrayList<>();
        Matcher matcher = NUMBER_PATTERN.matcher(text);

        while (matcher.find()) {
            nums.add(Integer.parseInt(matcher.group()));
        }

        return nums;
    }

    private long resolveCellKey(MapArtGrid.MapTarget target) {
        int col;
        int row;
        if (target instanceof MapArtGrid.MapTarget.Grid grid) {
            col = grid.x() - this.indexBase;
            row = grid.y() - this.indexBase;
        } else {
            if (this.width <= 0) {
                return Long.MIN_VALUE;
            }

            int index = ((MapArtGrid.MapTarget.Index)target).index() - this.indexBase;
            if (index < 0) {
                return Long.MIN_VALUE;
            }

            col = index % this.width;
            row = index / this.width;
        }

        if (col < 0 || row < 0) {
            return Long.MIN_VALUE;
        } else if (this.width > 0 && col >= this.width) {
            return Long.MIN_VALUE;
        } else {
            return this.height > 0 && row >= this.height ? Long.MIN_VALUE : packKey(col, row);
        }
    }

    public String describeMapName(String name) {
        MapArtGrid.MapTarget target = parseMapName(name);
        if (target == null) {
            return "no numbers in the name";
        } else if (target instanceof MapArtGrid.MapTarget.Grid grid) {
            return "grid " + grid.x() + "," + grid.y();
        } else {
            int index = ((MapArtGrid.MapTarget.Index)target).index();
            if (this.width <= 0) {
                return "index " + index + " (needs .mp size to resolve)";
            } else {
                int n = index - this.indexBase;
                return "index " + index + " -> grid " + (n % this.width + this.indexBase) + "," + (n / this.width + this.indexBase);
            }
        }
    }

    private static long packKey(int col, int row) {
        return (long)col << 32 | row & 4294967295L;
    }

    private static int projectOnto(Vec3i vec, Direction dir) {
        return vec.getX() * dir.getOffsetX() + vec.getY() * dir.getOffsetY() + vec.getZ() * dir.getOffsetZ();
    }

    public static Vec3d getFramePoint(BlockPos pos, Direction facing, double out) {
        double depth = 0.46875 - out;
        return Vec3d.ofCenter(pos).add(-facing.getOffsetX() * depth, -facing.getOffsetY() * depth, -facing.getOffsetZ() * depth);
    }

    public static double distanceToFrame(ItemFrameEntity frame) {
        if (mc.player == null) {
            return Double.MAX_VALUE;
        } else {
            Vec3d eye = mc.player.getEyePos();
            Box box = frame.getBoundingBox();
            double x = Math.max(box.minX, Math.min(eye.x, box.maxX));
            double y = Math.max(box.minY, Math.min(eye.y, box.maxY));
            double z = Math.max(box.minZ, Math.min(eye.z, box.maxZ));
            return eye.distanceTo(new Vec3d(x, y, z));
        }
    }

    public static final class Cell {
        public final int col;
        public final int row;
        public final BlockPos pos;
        private int frameId = -1;
        private boolean filled;
        private int mapSlot = -1;
        private int attemptTick = Integer.MIN_VALUE;

        private Cell(int col, int row, BlockPos pos) {
            this.col = col;
            this.row = row;
            this.pos = pos;
        }

        public boolean isFilled() {
            return this.filled;
        }

        public int getMapSlot() {
            return this.mapSlot;
        }

        public void markAttempt(int tick) {
            this.attemptTick = tick;
        }

        public void clearAttempt() {
            this.attemptTick = Integer.MIN_VALUE;
        }

        public boolean isAttemptPending(int tick, int timeout) {
            if (this.attemptTick == Integer.MIN_VALUE) {
                return false;
            } else {
                int elapsed = tick - this.attemptTick;
                return elapsed >= 0 && elapsed < timeout;
            }
        }

        public ItemFrameEntity getFrame() {
            if (MapArtGrid.mc.world != null && this.frameId >= 0) {
                return MapArtGrid.mc.world.getEntityById(this.frameId) instanceof ItemFrameEntity frame && frame.isAlive() ? frame : null;
            } else {
                return null;
            }
        }

        public String formatGridPos(int indexBase) {
            return this.col + indexBase + "," + (this.row + indexBase);
        }

        public int getLinearIndex(int width, int indexBase) {
            return width > 0 ? this.row * width + this.col + indexBase : -1;
        }
    }

    public static enum Corner {
        TopLeft,
        BottomLeft;
    }

    public sealed interface MapTarget permits MapArtGrid.MapTarget.Grid, MapArtGrid.MapTarget.Index {
        public record Grid(int x, int y) implements MapArtGrid.MapTarget {
        }

        public record Index(int index) implements MapArtGrid.MapTarget {
        }
    }
}
