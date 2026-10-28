package musheor.modules.automation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.world.PlaySoundEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.AutoEat;
import meteordevelopment.meteorclient.systems.modules.player.ChestSwap;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import musheor.musheor;
import musheor.compat.VersionHelper;
import musheor.compat.XearoHelper;
import musheor.modules.automation.highway.HighwayNavigator;
import musheor.modules.automation.highway.HighwayRouter;
import musheor.utils.BouncePitchPolicy;
import musheor.utils.HighwayUtils;
import musheor.utils.PlayerControlUtils;
import musheor.utils.baritone.BaritoneHelper;
import musheor.utils.manager.ElytraManager;
import musheor.utils.manager.InventoryManager;
import musheor.utils.manager.PongHolder;
import musheor.utils.manager.RotationManager;
import musheor.utils.system.HighwayNetworkManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.data.TrackedDataHandler;
import net.minecraft.entity.data.DataTracker.SerializedEntry;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class KekBounce extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgGrim = this.settings.createGroup("Grim");
    private final SettingGroup sgRotation = this.settings.createGroup("Rotation");
    private final SettingGroup sgObstaclePasser = this.settings.createGroup("Obstacle-Passer");
    private final SettingGroup sgAutoRepair = this.settings.createGroup("Auto-Repair");
    private final SettingGroup sg2bNav = this.settings.createGroup("2bNav");
    private final Setting<KekBounce.Mode> mode = this.sgGeneral
        .add(
            new Builder<KekBounce.Mode>().name("mode")
                        .description("Simple: freeform bounce. Navigate: Set and follows temporary waypoints created using Xearo")
                    .defaultValue(KekBounce.Mode.Simple)
                .build()
        );
    private final Setting<Boolean> muteSwapSounds = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("mute-swap-sounds")
                .description("Mutes the armour equip and elytra wind sounds that fake-fly's swaps would play every hop.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> fakeFly = this.sgGrim
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("fake-fly")
                .description(
                    "Bounce while wearing a chestplate. Keeps the elytra in your hotbar and only puts it on for the instant the glide is started, so it takes no durability."
                )
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> toggleElytra = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("toggle-elytra")
                .description("Equips an elytra on activate, and a chestplate on deactivate.")
                .defaultValue(true)
                .visible(() -> !this.fakeFly.get())
                .build()
        );
    private final Setting<Integer> fakeFlyHold = this.sgGrim
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("fake-fly-hold")
                .description("Max ticks to keep gliding after the server ends the glide, holding back pongs meanwhile. 0 re-deploys mid-air instead.")
                .defaultValue(12)
                .sliderRange(0, 20)
                .min(0)
                .visible(() -> false)
                .build()
        );
    private final Setting<Boolean> lockPitch = this.sgRotation
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("lock-pitch")
                .description("Whether to lock your pitch when bouncing.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> variablePitch = this.sgRotation
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("variable-pitch")
                .description(
                    "Changes the pitch every tick to the one that builds the most speed (about 18% faster than a fixed 72.4). Only the server-side rotation moves, your camera stays free."
                )
                .defaultValue(false)
                .visible(this.lockPitch::get)
                .onChanged(value -> {
                    if (value) {
                        getPitchPolicy();
                    }
                })
                .build()
        );
    private final Setting<Double> pitch = this.sgRotation
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("pitch")
                .description("Degrees of pitch to lock to")
                .defaultValue(72.4)
                .sliderRange(-90.0, 90.0)
                .decimalPlaces(2)
                .visible(() -> this.lockPitch.get() && !this.variablePitch.get())
                .build()
        );
    private final Setting<KekBounce.YawLock> yawLock = this.sgRotation
        .add(
            new Builder<KekBounce.YawLock>().name("yaw-lock")
                        .description("Snap on activation, Snap to nearest axis, configure custom value or disable.")
                    .defaultValue(KekBounce.YawLock.Onactivate)
                .build()
        );
    private final Setting<Double> yaw = this.sgRotation
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("yaw")
                .description("Degrees of yaw to lock to")
                .defaultValue(0.0)
                .sliderRange(-180.0, 180.0)
                .decimalPlaces(2)
                .visible(() -> this.yawLock.get() == KekBounce.YawLock.Custom)
                .build()
        );
    private final Setting<Boolean> simpleObstaclePasser = this.sgObstaclePasser
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("simple-obstacle-passer")
                .description("Uses baritone to pass obstacles and aligns you to your starting position when enabling the module")
                .defaultValue(false)
                .build()
        );
    private final Setting<KekBounce.StartPos> startPosType = this.sgObstaclePasser
        .add(
            new Builder<KekBounce.StartPos>().name("start-pos-type")
                            .description("Automatic or custom starting position for aligning with obstacle-passer")
                        .defaultValue(KekBounce.StartPos.OnActivate)
                    .visible(() -> this.mode.get() == KekBounce.Mode.Simple && this.simpleObstaclePasser.get())
                .build()
        );
    private final Setting<KekBounce.Height> targetY = this.sgObstaclePasser
        .add(
            new Builder<KekBounce.Height>().name("target-y").description("The Y level to bounce at.")
                        .defaultValue(KekBounce.Height.Auto)
                    .visible(this.simpleObstaclePasser::get)
                .build()
        );
    private final Setting<Integer> yLevel = this.sgObstaclePasser
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("y-level")
                .description("The Y level to bounce at.")
                .defaultValue(120)
                .sliderRange(-64, 120)
                .visible(() -> this.simpleObstaclePasser.get() && this.targetY.get() == KekBounce.Height.Manual)
                .build()
        );
    private final Setting<BlockPos> customStartPos = this.sgObstaclePasser
        .add(
            new meteordevelopment.meteorclient.settings.BlockPosSetting.Builder()
                .name("custom-start-pos")
                .description("The position to align with when using the custom starting position option.")
                .defaultValue(new BlockPos(0, 0, 0))
                .visible(
                    () -> this.mode.get() == KekBounce.Mode.Simple && this.simpleObstaclePasser.get() && this.startPosType.get() == KekBounce.StartPos.Custom
                )
                .onChanged(value -> this.startPos = value)
                .build()
        );
    private final Setting<Integer> distance = this.sgObstaclePasser
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("distance")
                .description("The distance to set the baritone goal for path realignment.")
                .defaultValue(8)
                .visible(this.simpleObstaclePasser::get)
                .build()
        );
    private final Setting<Integer> stuckWindow = this.sgObstaclePasser
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("stuck-window")
                .description("Ticks over which your progress is measured to decide whether you are stuck.")
                .defaultValue(30)
                .sliderRange(10, 100)
                .min(5)
                .visible(this.simpleObstaclePasser::get)
                .build()
        );
    private final Setting<Double> stuckDistance = this.sgObstaclePasser
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("stuck-distance")
                .description("You count as stuck when you moved less than this many blocks over the stuck window.")
                .defaultValue(4.0)
                .sliderRange(0.5, 20.0)
                .decimalPlaces(1)
                .min(0.0)
                .visible(this.simpleObstaclePasser::get)
                .build()
        );
    private final Setting<Integer> setbackLimit = this.sgObstaclePasser
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("setback-limit")
                .description("You count as stuck after this many server setbacks (rubberbands) within the stuck window. 0 disables.")
                .defaultValue(3)
                .sliderRange(0, 10)
                .min(0)
                .visible(this.simpleObstaclePasser::get)
                .build()
        );
    private final Setting<Boolean> showEta = this.sg2bNav
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("show-eta")
                .description("Displays and calculates arrival time above the player's hotbar.")
                .defaultValue(true)
                .visible(() -> XearoHelper.isLoaded() && FabricLoader.getInstance().isModLoaded("xaeroplus") && this.mode.get() == KekBounce.Mode.Advanced)
                .build()
        );
    private final Setting<Boolean> renderPathOnMap = this.sg2bNav
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("render-path-on-map")
                .description("Displays your current navigation path on your minimap / worldmap.")
                .defaultValue(true)
                .visible(() -> XearoHelper.isLoaded() && FabricLoader.getInstance().isModLoaded("xaeroplus") && this.mode.get() == KekBounce.Mode.Advanced)
                .build()
        );
    private final Setting<Double> approachRadius = this.sg2bNav
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("approach-radius")
                .description("Start slowing down when within this many blocks of a waypoint.")
                .defaultValue(64.0)
                .sliderRange(16.0, 256.0)
                .decimalPlaces(0)
                .visible(() -> XearoHelper.isLoaded() && this.mode.get() == KekBounce.Mode.Advanced)
                .build()
        );
    private final Setting<Double> arriveRadius = this.sg2bNav
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("arrive-radius")
                .description("Consider a waypoint reached when within this many blocks.")
                .defaultValue(32.0)
                .sliderRange(8.0, 128.0)
                .decimalPlaces(0)
                .visible(() -> XearoHelper.isLoaded() && this.mode.get() == KekBounce.Mode.Advanced)
                .build()
        );
    private final Setting<Integer> alignTimeout = this.sg2bNav
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("align-timeout")
                .description("Ticks to spend aligning yaw before bouncing anyway.")
                .defaultValue(40)
                .sliderRange(10, 100)
                .visible(() -> XearoHelper.isLoaded() && this.mode.get() == KekBounce.Mode.Advanced)
                .build()
        );
    private final Setting<Double> yawTolerance = this.sg2bNav
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("yaw-tolerance")
                .description("Degrees of yaw error considered close enough to start bouncing.")
                .defaultValue(4.0)
                .sliderRange(1.0, 20.0)
                .decimalPlaces(1)
                .visible(() -> XearoHelper.isLoaded() && this.mode.get() == KekBounce.Mode.Advanced)
                .build()
        );
    private final Setting<Integer> transitionDistance = this.sg2bNav
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("transition-distance")
                .description("Baritone goal distance when walking an intersection.")
                .defaultValue(8)
                .sliderRange(1, 32)
                .visible(() -> XearoHelper.isLoaded() && this.mode.get() == KekBounce.Mode.Advanced)
                .build()
        );
    private final Setting<Integer> settleTicks = this.sg2bNav
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("settle-ticks")
                .description("Ticks to wait at an intersection after arriving before resuming bounce.")
                .defaultValue(10)
                .sliderRange(0, 40)
                .visible(() -> XearoHelper.isLoaded() && this.mode.get() == KekBounce.Mode.Advanced)
                .build()
        );
    private final Setting<Boolean> repairElytra = this.sgAutoRepair
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("repair-elytra")
                .description("Uses experience bottles to repair your elytra.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Integer> damageThreshold = this.sgAutoRepair
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("damage-threshold-%")
                .description("At what durability percentage should it start repairing the elytra.")
                .defaultValue(5)
                .sliderRange(0, 100)
                .visible(this.repairElytra::get)
                .build()
        );
    private final Setting<Integer> repairThreshold = this.sgAutoRepair
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("repair-threshold-%")
                .description("At what durability percentage should it stop repairing the elytra.")
                .defaultValue(90)
                .sliderRange(0, 100)
                .visible(this.repairElytra::get)
                .build()
        );
    private final Setting<String> server = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("server")
                .description("Which server's highway network to use. Must match a key in the remote config (e.g. \"2b2t\").")
                .defaultValue("2b2t")
                .onChanged(
                    value -> {
                        HighwayNetworkManager mgr = HighwayNetworkManager.getInstance();
                        if (!mgr.isLoaded()) {
                            this.warning("Highway configs are still loading — server key will be validated once loaded.", new Object[0]);
                        } else {
                            if (!mgr.getServerNames().contains(value)) {
                                this.error(
                                    "Unknown server \"(highlight)%s(default)\". Known servers: (highlight)%s(default).",
                                    new Object[]{value, String.join(", ", mgr.getServerNames())}
                                );
                            }
                        }
                    }
                )
                .visible(() -> false)
                .build()
        );
    private HighwayUtils.HighwayDirection bounceDirection;
    private int obstaclePassTicks;
    private int obstacleCooldown;
    private boolean bouncePaused;
    private boolean repairing;
    private Float activationYaw;
    private HighwayNavigator navigator = null;
    private XearoHelper.WaypointData currentWaypoint = null;
    private int lastWaypointCount = -1;
    private int unusedResetCounter;
    private BlockPos startPos;
    private final ArrayDeque<Vec3d> positionHistory = new ArrayDeque<>();
    private final ArrayDeque<Integer> setbackTicks = new ArrayDeque<>();
    private final AtomicInteger pendingSetbacks = new AtomicInteger();
    private int lastSampleAge = Integer.MIN_VALUE;
    private static volatile BouncePitchPolicy pitchPolicy;
    private static boolean pitchSolverStarted;
    private double groundY;
    private boolean rotationApplied;
    private int fakeFlyTicks;
    private int lastDeployTick;
    private int lastChestplateSwapTick;
    private int airTicks;
    private boolean landedSinceDeploy;
    private volatile boolean fakeFlyActive;
    private volatile boolean holdingGlide;
    private int holdTicks;
    private final Object holdLock = new Object();
    private int elytraSlot = -1;
    private int previousSlot = -1;
    private boolean pausedForEating;

    public KekBounce() {
        super(musheor.AUTOMATION, "kek-bounce", "Elytra bounce module with some extra juice.");
    }

    @Override
    public void onActivate() {
        if (this.mc.player != null && this.mc.world != null) {
            this.bounceDirection = HighwayUtils.getPlayerDirection();
            this.resetStuckTracking();
            this.obstaclePassTicks = 0;
            this.obstacleCooldown = 0;
            this.bouncePaused = false;
            this.repairing = false;
            this.unusedResetCounter = 0;
            this.groundY = this.mc.player.getY();
            this.rotationApplied = false;
            if (this.lockPitch.get() && this.variablePitch.get()) {
                getPitchPolicy();
            }

            this.startPos = this.startPosType.get() == KekBounce.StartPos.OnActivate
                ? BlockPos.ofFloored(VersionHelper.get().getPlayerPos())
                : this.customStartPos.get();
            if (this.targetY.get() == KekBounce.Height.Auto) {
                this.yLevel.set(this.mc.player.getBlockY());
            }

            this.fakeFlyTicks = 0;
            this.lastDeployTick = -2;
            this.lastChestplateSwapTick = -2;
            this.airTicks = 0;
            this.landedSinceDeploy = true;
            this.fakeFlyActive = false;
            this.releaseGlideHold();
            this.elytraSlot = -1;
            this.previousSlot = -1;
            this.pausedForEating = false;
            if (this.fakeFly.get()) {
                this.previousSlot = this.mc.player.getInventory().selectedSlot;
                ElytraManager.stowElytra();
                this.elytraSlot = ElytraManager.findHotbarElytra();
                if (this.elytraSlot == -1) {
                    this.warning("Fake-fly needs an elytra in your hotbar — bouncing on whatever is on your chest instead.", new Object[0]);
                }
            } else if (this.toggleElytra.get() && !this.mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem().equals(Items.ELYTRA)) {
                Modules.get().get(ChestSwap.class).swap();
            }

            if (this.yawLock.get() == KekBounce.YawLock.Onactivate) {
                this.activationYaw = this.mc.player.getYaw();
            }

            if (this.mode.get() == KekBounce.Mode.Advanced) {
                if (this.renderPathOnMap.get() && !FabricLoader.getInstance().isModLoaded("xaeroplus")) {
                    this.warning("Cannot render path on map, %sXaeroPlus %srequired but not installed!", new Object[]{Formatting.GOLD, Formatting.YELLOW});
                    this.renderPathOnMap.set(false);
                }

                if (this.showEta.get() && !FabricLoader.getInstance().isModLoaded("xaeroplus")) {
                    this.warning("Cannot show ETA, %sXaeroPlus %srequired but not installed!", new Object[]{Formatting.GOLD, Formatting.YELLOW});
                    this.showEta.set(false);
                }

                if (!XearoHelper.isLoaded()) {
                    this.warning("AdvancedNavigation requires %sXaero%s. Install it or switch to Simple mode.", new Object[]{Formatting.RED, Formatting.YELLOW});
                    this.toggle();
                    return;
                }

                this.startNavigation();
            } else {
                this.resetSimpleState();
            }
        }
    }

    @Override
    public void onDeactivate() {
        if (this.navigator != null) {
            this.navigator.stop();
            this.navigator = null;
        }

        this.currentWaypoint = null;
        this.lastWaypointCount = -1;
        if (XearoHelper.isLoaded()) {
            XearoHelper.get().clearLinesOnMap();
        }

        this.fakeFlyActive = false;
        this.releaseGlideHold();
        if (this.fakeFly.get()) {
            if (this.previousSlot >= 0) {
                InventoryManager.selectSlot(this.previousSlot);
            }

            this.previousSlot = -1;
            this.pausedForEating = false;
        } else if (this.toggleElytra.get()) {
            Modules.get().get(ChestSwap.class).swap();
        }

        if (BaritoneHelper.isPathing()) {
            BaritoneHelper.cancelAll();
        }

        PlayerControlUtils.releaseMovementKeys();
        this.activationYaw = null;
        if (this.rotationApplied) {
            RotationManager.clear();
        }

        this.rotationApplied = false;
    }

    private void resetSimpleState() {
        this.bounceDirection = HighwayUtils.getPlayerDirection();
        this.resetStuckTracking();
        this.obstaclePassTicks = 0;
        this.obstacleCooldown = 0;
        this.bouncePaused = false;
        this.repairing = false;
    }

    private void startNavigation() {
        XearoHelper.WaypointData wp = XearoHelper.get().getOldestWaypoint(true);
        if (wp == null) {
            ChatUtils.info(Formatting.AQUA + "[2bNav] " + Formatting.WHITE + "No temp waypoints found!");
            this.toggle();
        } else {
            this.navigateTo(wp);
        }
    }

    private void navigateTo(XearoHelper.WaypointData wp) {
        this.currentWaypoint = wp;
        double px = this.mc.player.getX();
        double pz = this.mc.player.getZ();
        double gx = wp.x();
        double gz = wp.z();
        ChatUtils.info(
            Formatting.AQUA
                + "[2bNav] "
                + Formatting.WHITE
                + "Navigating to waypoint: "
                + Formatting.YELLOW
                + wp.name()
                + Formatting.WHITE
                + " ("
                + (int)gx
                + ", "
                + (int)gz
                + ")"
        );
        HighwayRouter.Route route = HighwayRouter.findRoute(px, pz, gx, gz);
        if (route == null) {
            ChatUtils.info(Formatting.AQUA + "[2bNav] " + Formatting.WHITE + "No highway route found to waypoint: " + wp.name());
            this.toggle();
        } else {
            this.drawRouteOnMap(route);
            HighwayNavigator.Config navConfig = new HighwayNavigator.Config(
                this.approachRadius.get(),
                this.arriveRadius.get(),
                this.alignTimeout.get(),
                this.yawTolerance.get().floatValue(),
                this.transitionDistance.get(),
                this.settleTicks.get()
            );
            this.navigator = new HighwayNavigator(route, navConfig, new HighwayNavigator.BounceController() {
                @Override
                public void setBouncing(boolean active) {
                    KekBounce.this.bouncePaused = !active;
                }
            }, new HighwayNavigator.Listener() {
                @Override
                public void onStateChanged(HighwayNavigator.State newState, String description) {
                    ChatUtils.info(Formatting.AQUA + "[2bNav] " + Formatting.WHITE + description);
                }

                @Override
                public void onRouteComplete() {
                    XearoHelper.get().deleteCurrentWaypoint(KekBounce.this.currentWaypoint);
                    KekBounce.this.currentWaypoint = null;
                    XearoHelper.WaypointData next = XearoHelper.get().getOldestWaypoint(true);
                    if (next == null) {
                        ChatUtils.info(Formatting.AQUA + "[2bNav] " + Formatting.WHITE + "No waypoints found, assuming destination was reached!");
                        KekBounce.this.toggle();
                    } else {
                        KekBounce.this.navigateTo(next);
                    }
                }
            });
        }
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.isActive()) {
            if (this.mc.player != null && this.mc.world != null) {
                this.fakeFlyActive = false;
                if (this.rotationApplied) {
                    RotationManager.release(0);
                    this.rotationApplied = false;
                }

                if (this.mc.player.isOnGround()) {
                    this.groundY = this.mc.player.getY();
                }

                if (this.mode.get() == KekBounce.Mode.Advanced) {
                    this.tickNavigate();
                } else {
                    this.tickSimple();
                }

                if (!this.fakeFlyActive && (this.holdingGlide || PongHolder.isHolding())) {
                    this.releaseGlideHold();
                }
            }
        }
    }

    @EventHandler
    private void onPacketReceive(Receive event) {
        if (event.packet instanceof PlayerPositionLookS2CPacket) {
            this.pendingSetbacks.incrementAndGet();
        }

        if (this.fakeFlyActive) {
            if (!(event.packet instanceof PlayerPositionLookS2CPacket)) {
                if (event.packet instanceof EntityTrackerUpdateS2CPacket packet && this.fakeFlyHold.get() > 0) {
                    ClientPlayerEntity player = this.mc.player;
                    if (player != null && packet.id() == player.getId() && player.isGliding()) {
                        List<SerializedEntry<?>> values = packet.trackedValues();

                        for (int i = 0; i < values.size(); i++) {
                            SerializedEntry<?> entry = values.get(i);
                            if (entry.id() == 0 && entry.value() instanceof Byte flags) {
                                if ((flags & -128) != 0) {
                                    return;
                                }

                                TrackedDataHandler<Byte> handler = (TrackedDataHandler<Byte>)entry.handler();
                                synchronized (this.holdLock) {
                                    try {
                                        values.set(i, new SerializedEntry<>(entry.id(), handler, (byte)(flags | -128)));
                                    } catch (UnsupportedOperationException var12) {
                                        return;
                                    }

                                    this.holdTicks = 0;
                                    this.holdingGlide = true;
                                    PongHolder.startHolding();
                                    return;
                                }
                            }
                        }
                    }
                }
            } else {
                if (this.holdingGlide || PongHolder.isHolding()) {
                    PongHolder.releaseAll();
                    this.mc.execute(this::releaseGlideHold);
                }
            }
        }
    }

    @EventHandler
    private void onPlaySound(PlaySoundEvent event) {
        if (this.fakeFlyActive && this.muteSwapSounds.get()) {
            String path = event.sound.getId().getPath();
            if (path.startsWith("item.armor.equip_") || path.equals("item.elytra.flying")) {
                event.cancel();
            }
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        this.fakeFlyActive = false;
        this.holdingGlide = false;
        this.holdTicks = 0;
        PongHolder.discardAll();
    }

    private void tickNavigate() {
        if (this.repairing) {
            this.tickRepair();
        } else if (this.needsRepair()) {
            this.repairing = true;
        } else {
            if (this.mode.get() == KekBounce.Mode.Advanced && this.renderPathOnMap.get()) {
                this.drawWaypointRoutesOnMap();
            }

            if (this.showEta.get()) {
                this.displayEta();
            }

            if (this.navigator != null) {
                if (this.simpleObstaclePasser.get() && this.navigator.getState() == HighwayNavigator.State.BOUNCING) {
                    this.tickObstaclePasser();
                }

                this.navigator.tick();
                if (this.isBouncing()) {
                    this.mc.player.setSprinting(true);
                    if (!BaritoneHelper.isBusy()) {
                        this.applyPitch();
                    }

                    PlayerControlUtils.setKeyPressed(this.mc.options.jumpKey, true);
                    PlayerControlUtils.setKeyPressed(this.mc.options.forwardKey, true);
                    this.tryStartGlide();
                }
            }
        }
    }

    private void tickSimple() {
        if (this.simpleObstaclePasser.get()) {
            this.tickObstaclePasser();
        }

        if (!this.bouncePaused && !BaritoneHelper.isBusy()) {
            this.mc.player.setSprinting(true);
            if (this.repairing) {
                this.tickRepair();
            } else if (this.needsRepair()) {
                this.repairing = true;
            } else {
                this.applyYawLock();
                this.applyPitch();
                PlayerControlUtils.setKeyPressed(this.mc.options.jumpKey, true);
                PlayerControlUtils.setKeyPressed(this.mc.options.forwardKey, true);
                this.tryStartGlide();
            }
        } else {
            this.stopGlide(false);
        }
    }

    private void applyPitch() {
        if (this.lockPitch.get()) {
            BouncePitchPolicy policy = this.variablePitch.get() ? getPitchPolicy() : null;
            if (policy != null
                && !this.mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING)
                && !this.mc.player.hasStatusEffect(StatusEffects.LEVITATION)
                && !this.mc.player.hasStatusEffect(StatusEffects.JUMP_BOOST)) {
                float p = policy.getBestPitch(this.mc.player.getY() - this.groundY, this.mc.player.getVelocity().y, this.mc.player.isOnGround());
                if (RotationManager.setPitch(p, 0)) {
                    this.rotationApplied = true;
                }
            } else {
                this.mc.player.setPitch(this.pitch.get().floatValue());
            }
        }
    }

    private static BouncePitchPolicy getPitchPolicy() {
        if (pitchPolicy == null && !pitchSolverStarted) {
            pitchSolverStarted = true;
            Thread solver = new Thread(() -> pitchPolicy = BouncePitchPolicy.solve(0.08, 0.42), "musheor-bounce-pitch");
            solver.setDaemon(true);
            solver.start();
        }

        return pitchPolicy;
    }

    private void tryStartGlide() {
        if (!this.fakeFly.get() || !this.tickFakeFly()) {
            if (!this.mc.player.isGliding() && !this.mc.player.isOnGround()) {
                this.mc.player.startGliding();
                this.mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(this.mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            }
        }
    }

    private boolean tickFakeFly() {
        if (Modules.get().get(AutoEat.class).eating) {
            if (!this.pausedForEating) {
                this.pausedForEating = true;
                if (!this.isWearingElytra() && this.isHoldingElytra()) {
                    this.interactMainHand();
                }

                if (this.previousSlot >= 0) {
                    InventoryManager.selectSlot(this.previousSlot);
                }
            }

            return false;
        } else {
            if (this.pausedForEating) {
                this.pausedForEating = false;
                if (this.resolveElytraSlot() >= 0) {
                    InventoryManager.selectSlot(this.elytraSlot);
                }

                if (this.isWearingElytra() && this.isHoldingChestplate()) {
                    this.interactMainHand();
                }
            }

            if (this.resolveElytraSlot() < 0) {
                return false;
            } else {
                InventoryManager.selectSlot(this.elytraSlot);
                this.fakeFlyActive = true;
                this.fakeFlyTicks++;
                boolean ground = this.mc.player.isOnGround();
                if (ground) {
                    this.airTicks = 0;
                    this.landedSinceDeploy = true;
                } else {
                    this.airTicks++;
                }

                if (this.isWearingElytra() && this.isHoldingChestplate() && this.fakeFlyTicks - this.lastChestplateSwapTick >= 2) {
                    this.lastChestplateSwapTick = this.fakeFlyTicks;
                    this.interactMainHand();
                }

                if (this.holdingGlide) {
                    this.holdTicks++;
                    boolean nextHop = this.landedSinceDeploy && this.airTicks >= 2;
                    if (nextHop || this.holdTicks > this.fakeFlyHold.get()) {
                        this.releaseGlideHold();
                    }
                }

                if (!ground && this.airTicks >= 2 && !this.mc.player.isGliding() && this.fakeFlyTicks - this.lastDeployTick >= 2) {
                    this.redeployGlide();
                }

                return true;
            }
        }
    }

    private void redeployGlide() {
        if (this.isHoldingElytra() && !this.isWearingElytra()) {
            this.interactMainHand();
            if (this.isWearingElytra()) {
                this.mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(this.mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                this.mc.player.startGliding();
                this.interactMainHand();
                this.lastDeployTick = this.fakeFlyTicks;
                this.landedSinceDeploy = false;
            }
        }
    }

    private void releaseGlideHold() {
        boolean held;
        synchronized (this.holdLock) {
            held = this.holdingGlide;
            this.holdingGlide = false;
            this.holdTicks = 0;
            PongHolder.releaseAll();
        }

        if (held && this.mc.player != null && this.mc.player.isGliding()) {
            this.mc.player.stopGliding();
        }
    }

    private void interactMainHand() {
        if (this.mc.interactionManager != null) {
            float savedYaw = this.mc.player.getYaw();
            float savedPitch = this.mc.player.getPitch();
            this.mc.player.setYaw(RotationManager.yawOr(savedYaw));
            this.mc.player.setPitch(RotationManager.pitchOr(savedPitch));

            try {
                this.mc.interactionManager.interactItem(this.mc.player, Hand.MAIN_HAND);
            } finally {
                this.mc.player.setYaw(savedYaw);
                this.mc.player.setPitch(savedPitch);
            }
        }
    }

    private int resolveElytraSlot() {
        if (this.elytraSlot >= 0 && this.elytraSlot <= 8) {
            ItemStack held = this.mc.player.getInventory().getStack(this.elytraSlot);
            if (held.isOf(Items.ELYTRA) || isChestplate(held)) {
                return this.elytraSlot;
            }
        }

        return this.elytraSlot = ElytraManager.findHotbarElytra();
    }

    private boolean isWearingElytra() {
        return this.mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
    }

    private boolean isHoldingElytra() {
        return this.mc.player.getMainHandStack().isOf(Items.ELYTRA);
    }

    private boolean isHoldingChestplate() {
        return isChestplate(this.mc.player.getMainHandStack());
    }

    private static boolean isChestplate(ItemStack stack) {
        if (stack.isOf(Items.ELYTRA)) {
            return false;
        } else {
            EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
            return equippable != null && equippable.slot() == EquipmentSlot.CHEST;
        }
    }

    private void tickObstaclePasser() {
        if (this.bouncePaused) {
            if (BaritoneHelper.isBusy() && ++this.obstaclePassTicks < 200) {
                this.stopGlide(false);
            } else {
                this.finishObstaclePass();
            }
        } else if (this.obstacleCooldown > 0) {
            this.obstacleCooldown--;
        } else {
            boolean yLevelWrong = this.mode.get() != KekBounce.Mode.Advanced
                && (
                    VersionHelper.get().getPlayerPos().getY() < this.yLevel.get().intValue()
                        || VersionHelper.get().getPlayerPos().getY() > this.yLevel.get() + 2
                );
            if (!yLevelWrong && (!this.mc.player.horizontalCollision || this.mc.player.collidedSoftly)) {
                if (!this.repairing && this.isStuck()) {
                    this.startObstaclePass();
                }
            } else {
                this.startObstaclePass();
            }
        }
    }

    private void startObstaclePass() {
        this.bouncePaused = true;
        this.resetStuckTracking();
        this.obstaclePassTicks = 0;
        this.unusedResetCounter = 0;
        this.stopGlide(false);
        BaritoneHelper.pathTo(this.getRealignGoal(this.distance.get()));
    }

    private void finishObstaclePass() {
        if (BaritoneHelper.isBusy()) {
            BaritoneHelper.cancelAll();
        }

        this.bouncePaused = false;
        this.resetStuckTracking();
        this.obstaclePassTicks = 0;
        this.obstacleCooldown = 20;
        this.unusedResetCounter = 0;
        if (this.mode.get() != KekBounce.Mode.Advanced && this.bounceDirection != null) {
            this.mc.player.setYaw(HighwayUtils.HighwayDirection.toYaw(this.bounceDirection));
        }
    }

    private void applyYawLock() {
        KekBounce.YawLock yawMode = this.yawLock.get();
        switch (yawMode) {
            case Onactivate:
                if (this.activationYaw != null) {
                    this.mc.player.setYaw(HighwayUtils.HighwayDirection.toYaw(HighwayUtils.HighwayDirection.fromYaw(this.activationYaw)));
                }
                break;
            case Snap:
                HighwayUtils.HighwayDirection direction = HighwayUtils.getPlayerDirection();
                this.mc.player.setYaw(HighwayUtils.HighwayDirection.toYaw(direction));
                break;
            case Custom:
                this.mc.player.setYaw(this.yaw.get().floatValue());
        }
    }

    private void stopGlide(boolean notifyServer) {
        PlayerControlUtils.releaseMovementKeys();
        if (this.mc.player != null && this.mc.player.isGliding()) {
            if (notifyServer && this.mc.getNetworkHandler() != null) {
                this.mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(this.mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            }

            this.mc.player.stopGliding();
        }
    }

    private boolean isStuck() {
        if (this.mc.player == null) {
            return false;
        } else {
            int age = this.mc.player.age;
            if (age != this.lastSampleAge + 1) {
                this.resetStuckTracking();
            }

            this.lastSampleAge = age;
            int window = this.stuckWindow.get();

            for (int i = this.pendingSetbacks.getAndSet(0); i > 0; i--) {
                this.setbackTicks.addLast(age);
            }

            while (!this.setbackTicks.isEmpty() && age - this.setbackTicks.peekFirst() >= window) {
                this.setbackTicks.removeFirst();
            }

            if (this.setbackLimit.get() > 0 && this.setbackTicks.size() >= this.setbackLimit.get()) {
                return true;
            } else {
                this.positionHistory.addLast(VersionHelper.get().getPlayerPos());
                if (this.positionHistory.size() <= window) {
                    return false;
                } else {
                    Vec3d then = this.positionHistory.removeFirst();
                    Vec3d now = this.positionHistory.peekLast();
                    double dx = now.x - then.x;
                    double dz = now.z - then.z;
                    double min = this.stuckDistance.get();
                    return dx * dx + dz * dz < min * min;
                }
            }
        }
    }

    private void resetStuckTracking() {
        this.positionHistory.clear();
        this.setbackTicks.clear();
        this.pendingSetbacks.set(0);
        this.lastSampleAge = Integer.MIN_VALUE;
    }

    private float getElytraDurabilityPercent() {
        ItemStack stack = this.mc.player.getEquippedStack(EquipmentSlot.CHEST);
        return (1.0F - (float)stack.getDamage() / stack.getMaxDamage()) * 100.0F;
    }

    private boolean needsRepair() {
        if (!this.repairElytra.get()) {
            return false;
        } else if (!this.mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem().equals(Items.ELYTRA)) {
            return false;
        } else {
            return Modules.get().get(AutoEat.class).eating ? false : this.getElytraDurabilityPercent() <= this.damageThreshold.get().intValue();
        }
    }

    private void tickRepair() {
        if (!Modules.get().get(AutoEat.class).eating) {
            if (this.getElytraDurabilityPercent() >= this.repairThreshold.get().intValue()) {
                this.repairing = false;
            } else {
                int xpSlot = InventoryManager.findInInventory(Items.EXPERIENCE_BOTTLE);
                if (xpSlot == -1) {
                    ChatUtils.info("%sNo experience bottles found, waiting ...", Formatting.GOLD);
                } else if (xpSlot > 8) {
                    InventoryManager.moveToHotbar(Items.EXPERIENCE_BOTTLE);
                } else {
                    PlayerControlUtils.releaseMovementKeys();
                    this.mc.player.getInventory().selectedSlot = xpSlot;
                    this.mc.player.setPitch(90.0F);
                    this.mc.interactionManager.interactItem(this.mc.player, Hand.MAIN_HAND);
                }
            }
        }
    }

    private BlockPos getRealignGoal(int distance) {
        int px = this.mc.player.getBlockX();
        int py = this.yLevel.get();
        int pz = this.mc.player.getBlockZ();
        if (this.mode.get() == KekBounce.Mode.Advanced) {
            return switch (this.bounceDirection) {
                case NORTH -> new BlockPos(px, py, pz - distance);
                case SOUTH -> new BlockPos(px, py, pz + distance);
                case EAST -> new BlockPos(px + distance, py, pz);
                case WEST -> new BlockPos(px - distance, py, pz);
                case NORTH_EAST -> new BlockPos(px + distance, py, pz - distance);
                case NORTH_WEST -> new BlockPos(px - distance, py, pz - distance);
                case SOUTH_EAST -> new BlockPos(px + distance, py, pz + distance);
                case SOUTH_WEST -> new BlockPos(px - distance, py, pz + distance);
            };
        } else {
            int sx = this.startPos.getX();
            int sz = this.startPos.getZ();

            return switch (this.bounceDirection) {
                case NORTH -> new BlockPos(sx, py, pz - distance);
                case SOUTH -> new BlockPos(sx, py, pz + distance);
                case EAST -> new BlockPos(px + distance, py, sz);
                case WEST -> new BlockPos(px - distance, py, sz);
                case NORTH_EAST -> {
                    int gx = px + distance;
                    yield new BlockPos(gx, py, sx + sz - gx);
                }
                case NORTH_WEST -> {
                    int gx = px - distance;
                    yield new BlockPos(gx, py, gx - (sx - sz));
                }
                case SOUTH_EAST -> {
                    int gx = px + distance;
                    yield new BlockPos(gx, py, gx - (sx - sz));
                }
                case SOUTH_WEST -> {
                    int gx = px - distance;
                    yield new BlockPos(gx, py, sx + sz - gx);
                }
            };
        }
    }

    private void drawRouteOnMap(HighwayRouter.Route route) {
        if (XearoHelper.isLoaded()) {
            List<XearoHelper.LineData> lines = new ArrayList<>();
            List<HighwayUtils.HorizontalPos> wps = route.waypoints;

            for (int i = 0; i + 1 < wps.size(); i++) {
                lines.add(new XearoHelper.LineData((int)wps.get(i).x(), (int)wps.get(i).z(), (int)wps.get(i + 1).x(), (int)wps.get(i + 1).z()));
            }

            XearoHelper.get().drawLinesOnMap(lines, -16733441);
        }
    }

    private void drawWaypointRoutesOnMap() {
        if (XearoHelper.isLoaded()) {
            List<XearoHelper.WaypointData> wps = XearoHelper.get().getWaypoints(true);
            if (wps != null) {
                if (wps.size() != this.lastWaypointCount) {
                    this.lastWaypointCount = wps.size();
                    List<XearoHelper.WaypointData> sorted = new ArrayList<>(wps);
                    sorted.sort(Comparator.comparingLong(XearoHelper.WaypointData::createdAt));
                    List<XearoHelper.LineData> allLines = new ArrayList<>();
                    double fromX = this.mc.player.getX();
                    double fromZ = this.mc.player.getZ();

                    for (XearoHelper.WaypointData wp : sorted) {
                        HighwayRouter.Route route = HighwayRouter.findRoute(fromX, fromZ, wp.x(), wp.z());
                        if (route != null) {
                            List<HighwayUtils.HorizontalPos> pts = route.waypoints;

                            for (int i = 0; i + 1 < pts.size(); i++) {
                                allLines.add(
                                    new XearoHelper.LineData((int)pts.get(i).x(), (int)pts.get(i).z(), (int)pts.get(i + 1).x(), (int)pts.get(i + 1).z())
                                );
                            }
                        }

                        fromX = wp.x();
                        fromZ = wp.z();
                    }

                    XearoHelper.get().drawLinesOnMap(allLines, -16733441);
                }
            }
        }
    }

    private void displayEta() {
        if (this.currentWaypoint != null) {
            String etaStr = XearoHelper.get().getEtaSuffix(this.currentWaypoint);
            if (etaStr == null || etaStr.isBlank()) {
                etaStr = "...";
            }

            this.mc
                .player
                .sendMessage(
                    Text.literal("[2bNav] ")
                        .setStyle(Style.EMPTY.withColor(Formatting.AQUA))
                        .append(Text.literal("ETA ").setStyle(Style.EMPTY.withColor(Formatting.GRAY)))
                        .append(Text.literal(etaStr).setStyle(Style.EMPTY.withColor(Formatting.WHITE).withBold(true))),
                    true
                );
        }
    }

    public boolean isBouncing() {
        return this.isActive() && !this.bouncePaused && !this.repairing && !BaritoneHelper.isBusy();
    }

    private static enum Height {
        Auto,
        Manual;
    }

    public static enum Mode {
        Simple,
        Advanced;
    }

    private static enum StartPos {
        OnActivate,
        Custom;
    }

    public static enum YawLock {
        Onactivate,
        Snap,
        Custom,
        Disable;
    }
}
