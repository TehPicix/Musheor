package musheor.modules.tech;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import musheor.musheor;
import musheor.utils.BlockRenderUtils;
import musheor.utils.HighwayUtils;
import musheor.utils.highway.HighwayState;
import musheor.utils.manager.InventoryManager;
import musheor.utils.network.PacketLimiter;
import musheor.utils.placement.BlockPlacer;
import musheor.utils.system.MusheorSystem;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class AutoPortal extends Module {
    private static final MinecraftClient client = MinecraftClient.getInstance();
    private static final SettingColor BLOCKED_LINE_COLOR = new SettingColor(255, 45, 45, 220);
    private static final SettingColor BLOCKED_SIDE_COLOR = new SettingColor(255, 45, 45, 60);
    private final Setting<Boolean> autoDisable = this.settings
        .getDefaultGroup()
        .add(new Builder().name("auto-disable").description("Automatically disables the module when a portal is built").defaultValue(true).build());
    private final Setting<Boolean> pauseOnInput = this.settings
        .getDefaultGroup()
        .add(
            new Builder().name("pause-on-input").description("Pauses the building process when you are moving, sneaking or jumping").defaultValue(true).build()
        );
    private final Setting<Boolean> renderPortal = this.settings
        .getDefaultGroup()
        .add(new Builder().name("render-portal").description("Renders the portal's position").defaultValue(true).build());
    private final Setting<SettingColor> renderColor = this.settings
        .getDefaultGroup()
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("render-color")
                .defaultValue(new SettingColor(0, 225, 255, 200))
                .description("Custom color for rendering (lines / wireframe)")
                .visible(this.renderPortal::get)
                .build()
        );
    private boolean positionLocked;
    private AutoPortal.Phase phase;
    private final List<BlockPos> framePositions = new ArrayList<>();
    private final List<BlockPos> interiorPositions = new ArrayList<>();
    private BlockPos ignitePos;
    private int swapCooldown;
    private int tickCounter;

    public AutoPortal() {
        super(musheor.MAIN, "auto-portal", "Automatically builds and lights a nether portal");
    }

    @Override
    public void onActivate() {
        this.positionLocked = false;
        this.phase = AutoPortal.Phase.PLACING;
        this.framePositions.clear();
        this.interiorPositions.clear();
        this.ignitePos = null;
        this.swapCooldown = 0;
        this.tickCounter = 0;
        HighwayState.getInstance().getPendingPlacements().clear();
    }

    @Override
    public void onDeactivate() {
        this.framePositions.clear();
        this.interiorPositions.clear();
        this.ignitePos = null;
    }

    @EventHandler
    private void onTick(Pre event) {
        if (client.player != null && client.world != null && client.interactionManager != null) {
            if (!this.positionLocked) {
                this.computePortalLayout();
            }

            HighwayState.getInstance()
                .getPendingPlacements()
                .entrySet()
                .removeIf(
                    entry -> {
                        BlockState targetState = client.world.getBlockState(entry.getKey());
                        return targetState != null && client.world.getBlockState(entry.getKey()).getBlock() == Blocks.OBSIDIAN
                            ? true
                            : this.tickCounter - entry.getValue() > MusheorSystem.Manager.placementTimeout.get();
                    }
                );
            if (this.swapCooldown > 0) {
                this.swapCooldown--;
            }

            if (!this.pauseOnInput.get() || !this.isMovementKeyPressed()) {
                if (!this.positionLocked) {
                    int obsidianCount = InventoryManager.countItemIncludingShulkers(Items.OBSIDIAN);
                    if (obsidianCount < 10) {
                        this.error("Not enough obsidian (need %d, have %d), disabling.", new Object[]{10, obsidianCount});
                        this.toggle();
                        return;
                    }

                    if (!InvUtils.find(stack -> stack.getItem() == Items.FLINT_AND_STEEL).found()) {
                        this.error("No flint and steel found in inventory, disabling.", new Object[0]);
                        this.toggle();
                        return;
                    }

                    this.positionLocked = true;
                    this.phase = AutoPortal.Phase.PLACING;
                    this.info("Position locked, building portal...", new Object[0]);
                }

                switch (this.phase) {
                    case PLACING:
                        this.placeFrame(this.tickCounter++);
                        break;
                    case LIGHTING:
                        this.ignitePortal();
                        break;
                    case DONE:
                        this.verifyPortal();
                }
            }
        }
    }

    private void placeFrame(int currentTick) {
        boolean anyPlaceable = false;

        for (BlockPos pos : this.framePositions) {
            if (client.world.getBlockState(pos).getBlock() != Blocks.OBSIDIAN
                && client.world.getBlockState(pos).isReplaceable()
                && HighwayUtils.isInPlacementRange(pos)) {
                anyPlaceable = true;
                break;
            }
        }

        if (!anyPlaceable) {
            if (this.isFrameComplete()) {
                this.phase = AutoPortal.Phase.LIGHTING;
                this.swapCooldown = MusheorSystem.Manager.swapDelay.get();
            }
        } else if (this.swapCooldown <= 0) {
            FindItemResult inHotbar = InvUtils.findInHotbar(stack -> stack.getItem() == Items.OBSIDIAN);
            if (!inHotbar.found()) {
                InventoryManager.moveToHotbar(Items.OBSIDIAN);
                this.swapCooldown = MusheorSystem.Manager.swapDelay.get();
            } else {
                InventoryManager.swapToItem(Items.OBSIDIAN, MusheorSystem.Manager.silentPlace.get());

                for (BlockPos posx : this.framePositions) {
                    if (!PacketLimiter.canSendPlacePacket()) {
                        break;
                    }

                    if (!HighwayState.getInstance().getPendingPlacements().containsKey(posx)
                        && client.world.getBlockState(posx).getBlock() != Blocks.OBSIDIAN
                        && client.world.getBlockState(posx).isReplaceable()
                        && HighwayUtils.isInPlacementRange(posx)) {
                        HighwayState.getInstance().getPendingPlacements().put(posx, currentTick);
                        BlockPlacer.place(posx, Direction.DOWN, HighwayState.getInstance().getPlacementType(), MusheorSystem.Manager.silentPlace.get());
                    }
                }
            }
        }
    }

    private void ignitePortal() {
        if (this.ignitePos != null && HighwayUtils.isInPlacementRange(this.ignitePos)) {
            if (this.swapCooldown <= 0 && PacketLimiter.canSendPlacePacket()) {
                FindItemResult inHotbar = InvUtils.findInHotbar(stack -> stack.getItem() == Items.FLINT_AND_STEEL);
                if (inHotbar.found()) {
                    InvUtils.swap(inHotbar.slot(), false);
                    BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(this.ignitePos).add(0.0, 0.5, 0.0), Direction.UP, this.ignitePos, false);
                    client.interactionManager.sendSequencedPacket(client.world, sequence -> new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hit, sequence));
                    this.phase = AutoPortal.Phase.DONE;
                } else if (!InvUtils.find(stack -> stack.getItem() == Items.FLINT_AND_STEEL).found()) {
                    this.error("Lost flint and steel during build, disabling.", new Object[0]);
                    this.toggle();
                } else {
                    InventoryManager.moveToHotbar(Items.FLINT_AND_STEEL);
                    this.swapCooldown = MusheorSystem.Manager.swapDelay.get();
                }
            }
        }
    }

    private void verifyPortal() {
        if (this.isPortalLit()) {
            this.info("Portal built successfully!", new Object[0]);
            if (this.autoDisable.get()) {
                this.toggle();
            }
        } else {
            this.phase = AutoPortal.Phase.LIGHTING;
        }
    }

    private boolean isFrameComplete() {
        for (BlockPos pos : this.framePositions) {
            if (client.world.getBlockState(pos).getBlock() != Blocks.OBSIDIAN) {
                return false;
            }
        }

        return true;
    }

    private boolean isPortalLit() {
        for (BlockPos pos : this.interiorPositions) {
            if (client.world.getBlockState(pos).getBlock() == Blocks.NETHER_PORTAL) {
                return true;
            }
        }

        return false;
    }

    private boolean isMovementKeyPressed() {
        return client.options.forwardKey.isPressed()
            || client.options.backKey.isPressed()
            || client.options.leftKey.isPressed()
            || client.options.rightKey.isPressed()
            || client.options.jumpKey.isPressed()
            || client.options.sneakKey.isPressed();
    }

    private Direction getFacingFromYaw(float yaw) {
        float n = (yaw % 360.0F + 360.0F) % 360.0F;
        if (n >= 315.0F || n < 45.0F) {
            return Direction.SOUTH;
        } else if (n < 135.0F) {
            return Direction.WEST;
        } else {
            return n < 225.0F ? Direction.NORTH : Direction.EAST;
        }
    }

    private int getYOffsetFromPitch(float pitch) {
        float clamped = Math.max(-45.0F, Math.min(45.0F, pitch));
        int step = Math.round(-clamped / 9.0F);
        return Math.max(-5, Math.min(5, step));
    }

    private void computePortalLayout() {
        this.framePositions.clear();
        this.interiorPositions.clear();
        this.ignitePos = null;
        BlockPos foot = client.player.getBlockPos();
        Direction facing = this.getFacingFromYaw(client.player.getYaw());
        int yOffset = this.getYOffsetFromPitch(client.player.getPitch());
        int fx = facing.getOffsetX();
        int fz = facing.getOffsetZ();
        int rx = -fz;
        int rz = fx;
        int planeX = foot.getX() + fx * 2;
        int planeZ = foot.getZ() + fz * 2;
        int baseY = foot.getY() + yOffset;

        for (int h = 0; h < 5; h++) {
            for (int w = -1; w <= 2; w++) {
                BlockPos pos = new BlockPos(planeX + rx * w, baseY + h, planeZ + rz * w);
                boolean isCorner = (h == 0 || h == 4) && (w == -1 || w == 2);
                boolean isInterior = h >= 1 && h <= 3 && w >= 0 && w <= 1;
                if (!isCorner) {
                    if (isInterior) {
                        this.interiorPositions.add(pos);
                    } else {
                        this.framePositions.add(pos);
                    }
                }
            }
        }

        for (BlockPos pos : this.framePositions) {
            if (pos.getY() == baseY) {
                this.ignitePos = pos;
                break;
            }
        }
    }

    private boolean isLayoutObstructed() {
        for (BlockPos pos : this.framePositions) {
            BlockState state = client.world.getBlockState(pos);
            if (state.getBlock() != Blocks.OBSIDIAN && !state.isReplaceable()) {
                return true;
            }
        }

        for (BlockPos posx : this.interiorPositions) {
            BlockState state = client.world.getBlockState(posx);
            if (state.getBlock() != Blocks.NETHER_PORTAL && state.getBlock() != Blocks.FIRE && !state.isReplaceable()) {
                return true;
            }
        }

        return false;
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (client.player != null && client.world != null) {
            if (this.renderPortal.get()) {
                if (!this.framePositions.isEmpty() || !this.interiorPositions.isEmpty()) {
                    boolean blocked = this.isLayoutObstructed();
                    SettingColor lineColor = blocked ? BLOCKED_LINE_COLOR : this.renderColor.get();
                    SettingColor sideColor = blocked ? BLOCKED_SIDE_COLOR : this.renderColor.get();
                    List<BlockPos> all = new ArrayList<>(this.framePositions.size() + this.interiorPositions.size());
                    all.addAll(this.framePositions);
                    all.addAll(this.interiorPositions);
                    BlockRenderUtils.renderPositions(event, all, lineColor, sideColor, ShapeMode.Lines);
                }
            }
        }
    }

    private static enum Phase {
        PLACING,
        LIGHTING,
        DONE;
    }
}
