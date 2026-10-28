package musheor.modules.features;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.BlockPosSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import musheor.musheor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.world.World;
import net.minecraft.world.Heightmap.Type;

public class PortalSpawnESP extends Module {
    private final AtomicBoolean scanning = new AtomicBoolean(false);
    private final List<BlockPos> portalPositions = new CopyOnWriteArrayList<>();
    public final Setting<BlockPos> targetPosition = this.settings
        .getDefaultGroup()
        .add(
            new Builder()
                .name("Target Position")
                .description("Block position to look around for possible / valid portal spawn locations.")
                .defaultValue(new BlockPos(0, 0, 0))
                .build()
        );
    private final Setting<Integer> radius = this.settings
        .getDefaultGroup()
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("radius")
                .description("The range at which portal locations are scanned and rendered.")
                .sliderRange(8, 128)
                .defaultValue(32)
                .build()
        );
    private final Setting<ShapeMode> shape = this.settings
        .getDefaultGroup()
        .add(
            new meteordevelopment.meteorclient.settings.EnumSetting.Builder<ShapeMode>()
                            .name("shape")
                        .description("How the blocks scheduled for placement are rendered.")
                    .defaultValue(ShapeMode.Both)
                .build()
        );
    private final Setting<SettingColor> lineColor = this.settings
        .getDefaultGroup()
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("line-color")
                .description("Color of blocks scheduled for placement.")
                .defaultValue(new SettingColor(25, 25, 225, 255))
                .build()
        );
    private final Setting<SettingColor> renderPlacementsSideColor = this.settings
        .getDefaultGroup()
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("render-placements-side-color")
                .description("Side color of blocks scheduled for placement.")
                .defaultValue(new SettingColor(25, 25, 225, 25))
                .build()
        );

    public PortalSpawnESP() {
        super(musheor.AUTOMATION, "portal-spawn-esp", "Client-side scan for where a Nether portal could spawn.");
    }

    @Override
    public void onDeactivate() {
        this.portalPositions.clear();
        this.scanning.set(false);
    }

    @EventHandler
    private void onTickPost(Post event) {
        if (this.mc.player != null && this.mc.world != null) {
            if (this.scanning.compareAndSet(false, true)) {
                new Thread(this::scanPortalSpawns, "PortalScanThread").start();
            }
        } else {
            this.portalPositions.clear();
            this.scanning.set(false);
        }
    }

    private void scanPortalSpawns() {
        World world = this.mc.world;

        assert this.mc.player != null && world != null;

        BlockPos center = this.targetPosition.get();
        int horizontalRadius = this.radius.get();
        int yMin = world.getBottomY();
        List<BlockPos> newPortals = new ArrayList<>();

        for (Mutable candidate : BlockPos.iterateInSquare(center, horizontalRadius, Direction.EAST, Direction.SOUTH)) {
            int x = candidate.getX();
            int z = candidate.getZ();
            int surfaceY = world.getTopY(Type.MOTION_BLOCKING, x, z);
            int yMax = Math.min(surfaceY, center.getY() + horizontalRadius);

            for (int y = yMax; y >= yMin; y--) {
                candidate.set(x, y, z);
                if (this.isValidPortalSpawn(candidate)) {
                    newPortals.add(candidate.toImmutable());
                    break;
                }
            }
        }

        this.portalPositions.clear();
        this.portalPositions.addAll(newPortals);
        this.scanning.set(false);
    }

    private boolean isValidPortalSpawn(Mutable basePos) {
        List<BlockPos> requiredSolidBlocks = new ArrayList<>();
        requiredSolidBlocks.add(basePos);
        requiredSolidBlocks.add(basePos.offset(Direction.NORTH, 1));
        requiredSolidBlocks.add(basePos.offset(Direction.NORTH, 2));
        requiredSolidBlocks.add(basePos.offset(Direction.NORTH, 3));

        for (BlockPos pos : requiredSolidBlocks) {
            assert this.mc.world != null;

            if (!this.mc.world.getBlockState(pos).isSideSolidFullSquare(this.mc.world, pos, Direction.UP)) {
                return false;
            }
        }

        for (BlockPos pos : requiredSolidBlocks) {
            for (int i = 1; i < 5; i++) {
                if (!this.mc.world.getBlockState(pos.up(i)).isAir()) {
                    return false;
                }
            }
        }

        return true;
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        for (BlockPos portalBase : this.portalPositions) {
            for (int i = 0; i < 5; i++) {
                event.renderer.box(portalBase.offset(Direction.NORTH, i), this.renderPlacementsSideColor.get(), this.lineColor.get(), this.shape.get(), 0);
            }
        }
    }
}
