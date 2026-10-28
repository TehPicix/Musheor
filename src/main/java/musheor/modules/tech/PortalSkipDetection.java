package musheor.modules.tech;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import musheor.musheor;
import musheor.utils.BlockRenderUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

public class PortalSkipDetection extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<Integer> chunkRadius = this.sgGeneral
        .add(new Builder().name("chunk-radius").description("Radius in chunks to scan (circular)").defaultValue(4).sliderMin(1).sliderMax(8).build());
    private final CopyOnWriteArrayList<BlockPos> portalBlocks = new CopyOnWriteArrayList<>();
    private final Set<Long> foundPortalKeys = ConcurrentHashMap.newKeySet();
    private final Set<Long> scannedChunks = ConcurrentHashMap.newKeySet();
    private ExecutorService scanExecutor;
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    public PortalSkipDetection() {
        super(musheor.AUTOMATION, "portal-skip-detection", "Detects portal skip patterns (AIR in CAVE_AIR regions)");
    }

    @Override
    public void onActivate() {
        this.portalBlocks.clear();
        this.foundPortalKeys.clear();
        this.scannedChunks.clear();
        this.stopRequested.set(false);
        this.scanExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "PortalSkipScanner");
            t.setDaemon(true);
            t.setPriority(1);
            return t;
        });
        this.scanExecutor.submit(this::scanLoop);
        this.info("Portal scanner started.", new Object[0]);
    }

    @Override
    public void onDeactivate() {
        this.stopRequested.set(true);
        if (this.scanExecutor != null) {
            this.scanExecutor.shutdownNow();
            this.scanExecutor = null;
        }

        this.portalBlocks.clear();
        this.foundPortalKeys.clear();
        this.scannedChunks.clear();
    }

    private void scanLoop() {
        while (!this.stopRequested.get()) {
            try {
                if (this.mc.player != null && this.mc.world != null) {
                    World world = this.mc.world;
                    ChunkPos center = this.mc.player.getChunkPos();
                    int r = this.chunkRadius.get();
                    List<ChunkPos> newChunks = new ArrayList<>();

                    for (int dx = -r; dx <= r; dx++) {
                        for (int dz = -r; dz <= r; dz++) {
                            if (dx * dx + dz * dz <= r * r) {
                                ChunkPos chunk = new ChunkPos(center.x + dx, center.z + dz);
                                long chunkKey = chunk.toLong();
                                if (!this.scannedChunks.contains(chunkKey) && world.isChunkLoaded(chunk.x, chunk.z)) {
                                    newChunks.add(chunk);
                                    this.scannedChunks.add(chunkKey);
                                }
                            }
                        }
                    }

                    if (!newChunks.isEmpty()) {
                        for (ChunkPos chunk : newChunks) {
                            if (this.stopRequested.get()) {
                                break;
                            }

                            this.scanChunk(world, chunk);
                        }
                    }

                    Thread.sleep(50L);
                } else {
                    Thread.sleep(500L);
                }
            } catch (InterruptedException var10) {
                break;
            } catch (Exception var11) {
            }
        }
    }

    private void scanChunk(World world, ChunkPos chunk) {
        int startX = chunk.getStartX();
        int startZ = chunk.getStartZ();
        int minY = world.getBottomY();
        int maxY = world.getBottomY() + world.getHeight();

        for (int y = minY; y < maxY && !this.stopRequested.get(); y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    BlockPos pos = new BlockPos(startX + x, y, startZ + z);
                    this.checkBlock(world, pos);
                }
            }
        }
    }

    private void checkBlock(World world, BlockPos pos) {
        try {
            if (world.getBlockState(pos).getBlock() != Blocks.AIR) {
                return;
            }
        } catch (Exception var4) {
            return;
        }

        this.checkPortalShape(world, pos, true);
        this.checkPortalShape(world, pos, false);
    }

    private void checkPortalShape(World world, BlockPos corner, boolean xAligned) {
        long key = corner.asLong() ^ (xAligned ? 1L : 0L);
        if (!this.foundPortalKeys.contains(key)) {
            List<BlockPos> portalBlocks = new ArrayList<>();
            boolean valid = true;

            try {
                for (int h = 0; h < 5 && valid; h++) {
                    for (int w = 0; w < 4 && valid; w++) {
                        BlockPos checkPos = xAligned ? corner.add(w, h, 0) : corner.add(0, h, w);
                        BlockState state = world.getBlockState(checkPos);
                        boolean isAir = state.getBlock() == Blocks.AIR;
                        boolean isCorner = (h == 0 || h == 4) && (w == 0 || w == 3);
                        if (isAir) {
                            portalBlocks.add(checkPos);
                        } else if (!isCorner) {
                            valid = false;
                        }
                    }
                }
            } catch (Exception var14) {
                return;
            }

            if (valid && portalBlocks.size() >= 14 && this.isSkipPattern(world, corner, xAligned)) {
                this.foundPortalKeys.add(key);
                this.portalBlocks.addAll(portalBlocks);
                int x = corner.getX();
                int y = corner.getY();
                int z = corner.getZ();
                this.mc.execute(() -> this.info("Portal found at %d, %d, %d!", new Object[]{x, y, z}));
            }
        }
    }

    private boolean isSkipPattern(World world, BlockPos corner, boolean xAligned) {
        try {
            int caveAirNearby = 0;
            int boundedSides = 0;
            boolean leftBounded = true;

            for (int h = 0; h < 5; h++) {
                BlockPos pos = xAligned ? corner.add(-1, h, 0) : corner.add(0, h, -1);
                if (world.getBlockState(pos).getBlock() == Blocks.AIR) {
                    leftBounded = false;
                }

                if (world.getBlockState(pos).getBlock() == Blocks.CAVE_AIR) {
                    caveAirNearby++;
                }
            }

            if (leftBounded) {
                boundedSides++;
            }

            boolean rightBounded = true;

            for (int h = 0; h < 5; h++) {
                BlockPos posx = xAligned ? corner.add(4, h, 0) : corner.add(0, h, 4);
                if (world.getBlockState(posx).getBlock() == Blocks.AIR) {
                    rightBounded = false;
                }

                if (world.getBlockState(posx).getBlock() == Blocks.CAVE_AIR) {
                    caveAirNearby++;
                }
            }

            if (rightBounded) {
                boundedSides++;
            }

            boolean bottomBounded = true;

            for (int w = 0; w < 4; w++) {
                BlockPos posxx = xAligned ? corner.add(w, -1, 0) : corner.add(0, -1, w);
                if (world.getBlockState(posxx).getBlock() == Blocks.AIR) {
                    bottomBounded = false;
                }

                if (world.getBlockState(posxx).getBlock() == Blocks.CAVE_AIR) {
                    caveAirNearby++;
                }
            }

            if (bottomBounded) {
                boundedSides++;
            }

            boolean topBounded = true;

            for (int w = 0; w < 4; w++) {
                BlockPos posxxx = xAligned ? corner.add(w, 5, 0) : corner.add(0, 5, w);
                if (world.getBlockState(posxxx).getBlock() == Blocks.AIR) {
                    topBounded = false;
                }

                if (world.getBlockState(posxxx).getBlock() == Blocks.CAVE_AIR) {
                    caveAirNearby++;
                }
            }

            if (topBounded) {
                boundedSides++;
            }

            return boundedSides >= 2 && caveAirNearby >= 10;
        } catch (Exception var12) {
            return false;
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (this.mc.player != null && this.mc.world != null) {
            if (!this.portalBlocks.isEmpty()) {
                BlockRenderUtils.renderPositions(event, new ArrayList<>(this.portalBlocks));
            }
        }
    }
}
