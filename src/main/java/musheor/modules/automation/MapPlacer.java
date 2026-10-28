package musheor.modules.automation;

import java.util.List;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.DoubleSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import musheor.musheor;
import musheor.compat.VersionHelper;
import musheor.utils.manager.InventoryManager;
import musheor.utils.manager.RotationManager;
import musheor.utils.mapart.MapArtGrid;
import musheor.utils.system.MusheorSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction.Axis;
import org.joml.Vector3d;

public class MapPlacer extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgTiming = this.settings.createGroup("Timing");
    private final SettingGroup sgRender = this.settings.createGroup("Render");
    private final Setting<Double> placementRange = this.sgGeneral
        .add(
            new Builder()
                .name("placement-range")
                .description("How close a frame's hitbox has to be to your eyes before we try to fill it. The server's own limit is 3.")
                .defaultValue(3.0)
                .min(1.0)
                .sliderRange(1.0, 6.0)
                .decimalPlaces(1)
                .build()
        );
    private final Setting<MapPlacer.RotateMode> rotate = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.EnumSetting.Builder<MapPlacer.RotateMode>()
                            .name("rotate")
                        .description(
                            "How to face the frame being filled. Packet keeps the camera still, Client turns it for real, None sends the interact as-is."
                        )
                    .defaultValue(MapPlacer.RotateMode.Packet)
                .build()
        );
    private final Setting<Boolean> restoreSlot = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("restore-slot")
                .description(
                    "Selects the hotbar slot you were on again once placing stops. The swap itself can never be hidden — the server reads the map out of the slot we select."
                )
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> autoScan = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("auto-scan")
                .description("Keeps picking up more frames of the canvas as you walk, so .mp scan only has to be run once.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> pauseOnScreen = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("pause-on-screen")
                .description("Stops placing while any screen or inventory is open.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> disableWhenDone = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("disable-when-done")
                .description("Turns the module off once every frame of the canvas holds a map.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> chatFeedbackSetting = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("chat-feedback")
                .description("Reports each placement in chat.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Integer> placementDelay = this.sgTiming
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("placement-delay")
                .description("Ticks to wait between placements.")
                .defaultValue(1)
                .sliderRange(0, 20)
                .build()
        );
    private final Setting<Integer> swapDelay = this.sgTiming
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("swap-delay")
                .description("Ticks to wait after selecting a map before interacting. Raise this on high ping.")
                .defaultValue(1)
                .sliderRange(0, 10)
                .build()
        );
    private final Setting<Integer> scanInterval = this.sgTiming
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("scan-interval")
                .description("Ticks between rescans while auto-scan is on.")
                .defaultValue(50)
                .sliderRange(1, 100)
                .visible(this.autoScan::get)
                .build()
        );
    private final Setting<Boolean> render = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("render")
                .description("Draws the canvas so you can check every coordinate before you start placing.")
                .defaultValue(true)
                .build()
        );
    private final Setting<ShapeMode> shapeMode = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.EnumSetting.Builder<ShapeMode>()
                                .name("shape-mode")
                            .description("How the frame outlines are drawn.")
                        .defaultValue(ShapeMode.Lines)
                    .visible(this.render::get)
                .build()
        );
    private final Setting<Double> renderDistance = this.sgRender
        .add(
            new Builder()
                .name("render-distance")
                .description("Only draw cells this close to you.")
                .defaultValue(32.0)
                .sliderRange(8.0, 128.0)
                .visible(this.render::get)
                .build()
        );
    private final Setting<Boolean> showPlaced = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("show-placed")
                .description("Keep drawing frames that already hold a map. Off by default, so the render empties out as the mapart fills in.")
                .defaultValue(false)
                .visible(this.render::get)
                .build()
        );
    private final Setting<MapPlacer.LabelMode> labels = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.EnumSetting.Builder<MapPlacer.LabelMode>()
                                .name("labels")
                            .description("What to write on each frame: its grid coordinate, its row-major index, both, or nothing.")
                        .defaultValue(MapPlacer.LabelMode.Both)
                    .visible(this.render::get)
                .build()
        );
    private final Setting<Double> labelScale = this.sgRender
        .add(
            new Builder()
                .name("label-scale")
                .description("Size of the labels.")
                .defaultValue(2.0)
                .sliderRange(0.25, 3.0)
                .decimalPlaces(2)
                .visible(() -> this.render.get() && this.labels.get() != MapPlacer.LabelMode.None)
                .build()
        );
    private final Setting<Boolean> labelDistanceScaling = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("label-distance-scaling")
                .description("Shrink labels with distance instead of keeping them one size on screen.")
                .defaultValue(true)
                .visible(() -> this.render.get() && this.labels.get() != MapPlacer.LabelMode.None)
                .build()
        );
    private final Setting<SettingColor> readyColor = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("ready-color")
                .description("A frame whose map is in your inventory.")
                .defaultValue(new SettingColor(25, 225, 125, 180))
                .visible(this.render::get)
                .build()
        );
    private final Setting<SettingColor> missingColor = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("missing-color")
                .description("A frame whose map is NOT in your inventory.")
                .defaultValue(new SettingColor(225, 75, 50, 180))
                .visible(this.render::get)
                .build()
        );
    private final Setting<SettingColor> targetColor = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("target-color")
                .description("The frame being filled right now.")
                .defaultValue(new SettingColor(255, 210, 40, 220))
                .visible(this.render::get)
                .build()
        );
    private final Setting<SettingColor> placedColor = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("placed-color")
                .description("A frame that already holds a map.")
                .defaultValue(new SettingColor(120, 120, 120, 110))
                .visible(() -> this.render.get() && this.showPlaced.get())
                .build()
        );
    private static final MapArtGrid canvas = MapArtGrid.getInstance();
    private static boolean paused;
    private int tickCounter;
    private int placeDelayTimer;
    private int swapDelayTimer;
    private int scanTimer;
    private int prevSlot = -1;
    private float lastYaw;
    private float lastPitch;
    private int lastRotationTick = Integer.MIN_VALUE;
    private MapArtGrid.Cell targetCell;
    private MapPlacer.Status status = MapPlacer.Status.NoCanvas;

    public MapPlacer() {
        super(musheor.AUTOMATION, "map-placer", "Places a mapart into a wall of item frames, reading each map's position out of its name. Set it up with .mp.");
    }

    public static MapPlacer getInstance() {
        return Modules.get().get(MapPlacer.class);
    }

    public static void setPaused(boolean value) {
        paused = value;
    }

    @Override
    public void onActivate() {
        this.tickCounter = 0;
        this.placeDelayTimer = 0;
        this.swapDelayTimer = 0;
        this.scanTimer = 0;
        this.prevSlot = -1;
        this.lastRotationTick = Integer.MIN_VALUE;
        this.targetCell = null;
        if (!canvas.isAnchored()) {
            this.error("No canvas — look at the mapart's corner frame and run §b.mp origin topleft§r first.", new Object[0]);
            this.toggle();
        } else {
            canvas.scanFrames();
            canvas.clearAttempts();
            canvas.matchInventoryMaps();
            if (canvas.getCellCount() == 0) {
                this.warning("No item frames on the canvas yet. Walk closer so they load — auto-scan will pick them up.", new Object[0]);
            }
        }
    }

    @Override
    public void onDeactivate() {
        RotationManager.release(10);
        this.restorePrevSlot();
        this.targetCell = null;
        this.status = canvas.isAnchored() ? MapPlacer.Status.Paused : MapPlacer.Status.NoCanvas;
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player != null && this.mc.world != null && this.mc.interactionManager != null) {
            this.tickCounter++;
            RotationManager.release(10);
            if (!canvas.isAnchored()) {
                this.status = MapPlacer.Status.NoCanvas;
            } else if (!paused && (!this.pauseOnScreen.get() || this.mc.currentScreen == null)) {
                if (this.autoScan.get() && --this.scanTimer <= 0) {
                    this.scanTimer = this.scanInterval.get();
                    canvas.scanFrames();
                }

                canvas.refreshFilled();
                canvas.matchInventoryMaps();
                if (canvas.getCellCount() == 0) {
                    this.status = MapPlacer.Status.NoFrames;
                } else if (canvas.isComplete()) {
                    boolean announce = this.status != MapPlacer.Status.Done;
                    this.status = MapPlacer.Status.Done;
                    this.targetCell = null;
                    this.restorePrevSlot();
                    if (announce) {
                        if (this.chatFeedbackSetting.get()) {
                            this.info("Mapart complete — §b%d§r frames filled.", new Object[]{canvas.getCellCount()});
                        }

                        if (this.disableWhenDone.get()) {
                            this.toggle();
                        }
                    }
                } else if (this.placeDelayTimer > 0) {
                    this.placeDelayTimer--;
                } else {
                    this.targetCell = this.findTarget();
                    if (this.targetCell == null) {
                        this.restorePrevSlot();
                    } else {
                        ItemFrameEntity frame = this.targetCell.getFrame();
                        if (frame != null) {
                            if (!this.aimAt(frame)) {
                                this.status = MapPlacer.Status.Aiming;
                            } else {
                                int hotbarSlot = this.selectMapSlot(this.targetCell.getMapSlot());
                                if (hotbarSlot >= 0) {
                                    if (this.swapDelayTimer > 0) {
                                        this.swapDelayTimer--;
                                        this.status = MapPlacer.Status.Aiming;
                                    } else {
                                        this.placeMap(frame, this.targetCell);
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                this.status = MapPlacer.Status.Paused;
                this.targetCell = null;
                this.restorePrevSlot();
            }
        }
    }

    private MapArtGrid.Cell findTarget() {
        List<MapArtGrid.Cell> pending = canvas.getPendingCells();
        boolean anyInRange = false;
        boolean anyCarried = false;

        for (MapArtGrid.Cell cell : pending) {
            ItemFrameEntity frame = cell.getFrame();
            if (frame != null && !(MapArtGrid.distanceToFrame(frame) > this.placementRange.get())) {
                anyInRange = true;
                if (cell.getMapSlot() >= 0) {
                    anyCarried = true;
                    if (!cell.isAttemptPending(this.tickCounter, MusheorSystem.Manager.placementTimeout.get())) {
                        this.status = MapPlacer.Status.Placing;
                        return cell;
                    }
                }
            }
        }

        this.status = !anyInRange ? MapPlacer.Status.OutOfRange : (anyCarried ? MapPlacer.Status.Placing : MapPlacer.Status.NoMaps);
        return null;
    }

    private boolean aimAt(ItemFrameEntity frame) {
        if (this.rotate.get() == MapPlacer.RotateMode.None) {
            return true;
        } else {
            Vec3d aimPoint = frame.getBoundingBox().getCenter();
            Vec3d eye = this.mc.player.getEyePos();
            double dx = aimPoint.x - eye.x;
            double dy = aimPoint.y - eye.y;
            double dz = aimPoint.z - eye.z;
            float yaw = (float)Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
            float pitch = (float)(-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
            if (this.rotate.get() == MapPlacer.RotateMode.Client) {
                this.mc.player.setYaw(yaw);
                this.mc.player.setPitch(pitch);
                return true;
            } else {
                boolean onTheWire = this.lastRotationTick == this.tickCounter - 1
                    && Math.abs(MathHelper.wrapDegrees(this.lastYaw - yaw)) <= 8.0F
                    && Math.abs(this.lastPitch - pitch) <= 8.0F;
                if (RotationManager.setYaw(yaw, 10) && RotationManager.setPitch(pitch, 10)) {
                    this.lastYaw = yaw;
                    this.lastPitch = pitch;
                    this.lastRotationTick = this.tickCounter;
                }

                return onTheWire;
            }
        }
    }

    private int selectMapSlot(int slot) {
        int selected = this.mc.player.getInventory().selectedSlot;
        if (slot > 8) {
            ItemStack stack = this.mc.player.getInventory().getStack(slot);
            InvUtils.move().from(slot).to(this.findHotbarSlot());
            if (this.chatFeedbackSetting.get()) {
                this.info("Moved §b%s§r into the hotbar.", new Object[]{stack.getName().getString()});
            }

            this.swapDelayTimer = Math.max(1, this.swapDelay.get());
            this.status = MapPlacer.Status.Aiming;
            return -1;
        } else {
            if (selected != slot) {
                if (this.prevSlot < 0) {
                    this.prevSlot = selected;
                }

                InventoryManager.selectSlot(slot);
                this.swapDelayTimer = this.swapDelay.get();
                if (this.swapDelayTimer > 0) {
                    this.status = MapPlacer.Status.Aiming;
                    return -1;
                }
            }

            return slot;
        }
    }

    private void placeMap(ItemFrameEntity frame, MapArtGrid.Cell cell) {
        String name = this.mc.player.getMainHandStack().getName().getString();
        VersionHelper.get().interactEntityAt(frame, Hand.MAIN_HAND);
        this.mc
            .interactionManager
            .sendSequencedPacket(this.mc.world, sequence -> PlayerInteractEntityC2SPacket.interact(frame, this.mc.player.isSneaking(), Hand.MAIN_HAND));
        cell.markAttempt(this.tickCounter);
        this.placeDelayTimer = this.placementDelay.get();
        if (this.chatFeedbackSetting.get()) {
            this.info("Placed §b%s§r at §b%s§r.", new Object[]{name, cell.formatGridPos(canvas.getIndexBase())});
        }
    }

    private int findHotbarSlot() {
        for (int slot = 0; slot < 9; slot++) {
            if (this.mc.player.getInventory().getStack(slot).isEmpty()) {
                return slot;
            }
        }

        for (int slotx = 0; slotx < 9; slotx++) {
            if (this.mc.player.getInventory().getStack(slotx).getItem() == Items.FILLED_MAP) {
                return slotx;
            }
        }

        return InventoryManager.findFreeHotbarSlot();
    }

    private void restorePrevSlot() {
        if (this.prevSlot >= 0) {
            if (this.restoreSlot.get()) {
                InventoryManager.selectSlot(this.prevSlot);
            }

            this.prevSlot = -1;
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (this.render.get() && canvas.isAnchored() && this.mc.player != null && !this.mc.options.hudHidden) {
            Direction facing = canvas.getFacing();
            double maxDistSq = this.renderDistance.get() * this.renderDistance.get();
            Vec3d eye = this.mc.player.getEyePos();

            for (MapArtGrid.Cell cell : canvas.getCells()) {
                if ((!cell.isFilled() || this.showPlaced.get()) && !(Vec3d.ofCenter(cell.pos).squaredDistanceTo(eye) > maxDistSq)) {
                    Color lines = this.getCellColor(cell);
                    Color sides = new Color(lines.r, lines.g, lines.b, lines.a / 3);
                    BlockPos pos = cell.pos;
                    Vec3d front = MapArtGrid.getFramePoint(pos, facing, 0.0625);
                    Vec3d back = MapArtGrid.getFramePoint(pos, facing, 0.0);
                    double inset = 0.0625;
                    boolean onX = facing.getAxis() == Axis.X;
                    boolean onY = facing.getAxis() == Axis.Y;
                    boolean onZ = facing.getAxis() == Axis.Z;
                    event.renderer
                        .box(
                            onX ? Math.min(front.x, back.x) : pos.getX() + inset,
                            onY ? Math.min(front.y, back.y) : pos.getY() + inset,
                            onZ ? Math.min(front.z, back.z) : pos.getZ() + inset,
                            onX ? Math.max(front.x, back.x) : pos.getX() + 1 - inset,
                            onY ? Math.max(front.y, back.y) : pos.getY() + 1 - inset,
                            onZ ? Math.max(front.z, back.z) : pos.getZ() + 1 - inset,
                            lines,
                            sides,
                            this.shapeMode.get(),
                            0
                        );
                }
            }
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (this.render.get() && this.labels.get() != MapPlacer.LabelMode.None) {
            if (canvas.isAnchored() && this.mc.player != null && !this.mc.options.hudHidden) {
                Direction facing = canvas.getFacing();
                double maxDistSq = this.renderDistance.get() * this.renderDistance.get();
                Vec3d eye = this.mc.player.getEyePos();
                TextRenderer textRenderer = TextRenderer.get();

                for (MapArtGrid.Cell cell : canvas.getCells()) {
                    if ((!cell.isFilled() || this.showPlaced.get()) && !(Vec3d.ofCenter(cell.pos).squaredDistanceTo(eye) > maxDistSq)) {
                        String text = this.getCellLabel(cell);
                        if (!text.isEmpty()) {
                            Vec3d world = MapArtGrid.getFramePoint(cell.pos, facing, 0.1);
                            Vector3d pos = new Vector3d(world.x, world.y, world.z);
                            if (NametagUtils.to2D(pos, this.labelScale.get(), this.labelDistanceScaling.get())) {
                                NametagUtils.begin(pos);
                                textRenderer.begin();
                                double width = textRenderer.getWidth(text);
                                textRenderer.render(text, -width / 2.0, -textRenderer.getHeight() / 2.0, this.getCellColor(cell));
                                textRenderer.end();
                                NametagUtils.end();
                            }
                        }
                    }
                }
            }
        }
    }

    private Color getCellColor(MapArtGrid.Cell cell) {
        if (cell.isFilled()) {
            return this.placedColor.get();
        } else if (cell == this.targetCell) {
            return this.targetColor.get();
        } else {
            return cell.getMapSlot() >= 0 ? this.readyColor.get() : this.missingColor.get();
        }
    }

    private String getCellLabel(MapArtGrid.Cell cell) {
        int base = canvas.getIndexBase();
        int index = cell.getLinearIndex(canvas.getWidth(), base);

        return switch ((MapPlacer.LabelMode)this.labels.get()) {
            case Coords -> cell.formatGridPos(base);
            case Index -> index < 0 ? cell.formatGridPos(base) : String.valueOf(index);
            case Both -> index < 0 ? cell.formatGridPos(base) : cell.formatGridPos(base) + " #" + index;
            case None -> "";
        };
    }

    public String getStatusText() {
        if (!canvas.isAnchored()) {
            return "§7no canvas";
        } else {
            String state = switch (this.status) {
                case NoCanvas -> "§7no canvas";
                case NoFrames -> "§eno frames scanned";
                case Placing -> "§aplacing";
                case Aiming -> "§aaiming";
                case OutOfRange -> "§ewalk closer";
                case NoMaps -> "§cmaps missing";
                case Paused -> "§epaused";
                case Done -> "§adone";
            };
            return String.format("%s §7(%d/%d frames)", state, canvas.countFilled(), canvas.getCellCount());
        }
    }

    @Override
    public String getInfoString() {
        return canvas.isAnchored() ? canvas.countFilled() + "/" + canvas.getCellCount() : "no canvas";
    }

    public static int countMapsInInventory() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return 0;
        } else {
            int count = 0;

            for (int slot = 0; slot < 36; slot++) {
                if (player.getInventory().getStack(slot).getItem() == Items.FILLED_MAP) {
                    count++;
                }
            }

            return count;
        }
    }

    public static enum LabelMode {
        Coords,
        Index,
        Both,
        None;
    }

    public static enum RotateMode {
        Packet,
        Client,
        None;
    }

    public static enum Status {
        NoCanvas,
        NoFrames,
        Placing,
        Aiming,
        OutOfRange,
        NoMaps,
        Paused,
        Done;
    }
}
