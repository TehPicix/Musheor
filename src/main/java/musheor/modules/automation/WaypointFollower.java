package musheor.modules.automation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.world.Dimension;
import meteordevelopment.orbit.EventHandler;
import musheor.musheor;
import musheor.compat.XearoHelper;
import musheor.modules.features.KekFly;
import musheor.utils.PlayerControlUtils;
import musheor.utils.baritone.BaritoneHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Formatting;

public class WaypointFollower extends Module {
    private final MinecraftClient client = MinecraftClient.getInstance();
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<WaypointFollower.FlightMode> flightMode = this.sgGeneral
        .add(
            new Builder<WaypointFollower.FlightMode>().name("flight-mode").description("Which type of flight mode to use")
                    .defaultValue(WaypointFollower.FlightMode.RocketFly)
                .build()
        );
    private final Setting<Boolean> drawPath = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("draw-path")
                .description("Draws a path between waypoints on Xearo's map")
                .defaultValue(true)
                .visible(() -> XearoHelper.isLoaded() && this.flightMode.get() != WaypointFollower.FlightMode.Baritone)
                .build()
        );
    private final Setting<Integer> arrivalRadius = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("arrival-radius")
                .description("XZ distance to a waypoint at which it is considered reached.")
                .defaultValue(32)
                .sliderRange(8, 256)
                .build()
        );
    private final Setting<Double> degreesPerTick = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("degrees-per-tick")
                .description("Max yaw rotation per tick — limits how sharply the player turns toward a waypoint.")
                .defaultValue(3.0)
                .sliderRange(0.5, 20.0)
                .decimalPlaces(1)
                .visible(() -> this.flightMode.get() == WaypointFollower.FlightMode.RocketFly || this.flightMode.get() == WaypointFollower.FlightMode.Custom)
                .build()
        );
    private XearoHelper.WaypointData currentWaypoint = null;
    private int lastWaypointCount = -1;

    public WaypointFollower() {
        super(musheor.AUTOMATION, "waypoint-follower", "Automatically follow waypoints in all dimensions using different flight modes.");
    }

    @Override
    public void onActivate() {
        if (this.client.player != null && this.client.world != null) {
            Dimension currentDim = PlayerUtils.getDimension();
            if (this.flightMode.get() == WaypointFollower.FlightMode.Baritone && (currentDim == Dimension.End || currentDim == Dimension.Overworld)) {
                Formatting format = currentDim == Dimension.End ? Formatting.DARK_PURPLE : Formatting.RED;
                PlayerControlUtils.setModuleSetting(WaypointFollower.class, "flight-mode", WaypointFollower.FlightMode.RocketFly);
                ChatUtils.info(
                    Formatting.GOLD
                        + "[WaypointFollower] "
                        + Formatting.WHITE
                        + "Automatically switching to RocketFly: "
                        + format
                        + currentDim.name()
                        + Formatting.WHITE
                        + " does not support Baritone Elytra Flight."
                );
                this.toggle();
            }
        }
    }

    @Override
    public void onDeactivate() {
        if (this.currentWaypoint != null) {
            BaritoneHelper.cancelAll();
            this.currentWaypoint = null;
        }

        if (XearoHelper.isLoaded()) {
            XearoHelper.get().clearLinesOnMap();
        }

        this.lastWaypointCount = -1;
        KekFly fly = Modules.get().get(KekFly.class);
        if (fly.isActive() && this.flightMode.get() == WaypointFollower.FlightMode.RocketFly) {
            fly.toggle();
        }
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.client.player != null && this.client.world != null) {
            switch ((WaypointFollower.FlightMode)this.flightMode.get()) {
                case Baritone:
                    this.tickBaritone();
                    break;
                case RocketFly:
                    this.tickRocketFly();
                    break;
                case Custom:
                    this.tickYawOnly();
            }

            if (this.drawPath.get() && this.flightMode.get() != WaypointFollower.FlightMode.Baritone) {
                this.drawPathOnMap();
            }
        }
    }

    private void tickBaritone() {
        if (this.currentWaypoint == null) {
            this.currentWaypoint = XearoHelper.get().getOldestWaypoint(true);
            if (this.currentWaypoint != null) {
                this.pathToWaypoint(this.currentWaypoint);
            }
        } else {
            if (XearoHelper.get().distanceToWaypoint(this.currentWaypoint) < this.arrivalRadius.get().intValue()) {
                BaritoneHelper.cancelAll();
                ChatUtils.info(Formatting.GREEN + "Arrived at " + Formatting.YELLOW + this.currentWaypoint.name() + Formatting.GREEN + ", moving to next...");
                XearoHelper.get().deleteCurrentWaypoint(this.currentWaypoint);
                this.currentWaypoint = null;
            }
        }
    }

    private void tickRocketFly() {
        if (Modules.get().get(KekFly.class).isActive()) {
            if (this.currentWaypoint == null) {
                this.currentWaypoint = XearoHelper.get().getOldestWaypoint(true);
                if (this.currentWaypoint == null) {
                    return;
                }

                ChatUtils.info(Formatting.GOLD + "[WaypointFollower] " + Formatting.WHITE + "Flying to: " + Formatting.YELLOW + this.currentWaypoint.name());
            }

            float desiredYaw = this.yawToWaypoint(this.currentWaypoint);
            float diff = this.yawDifference(this.client.player.getYaw(), desiredYaw);
            float maxRot = (float)this.degreesPerTick.get().doubleValue();
            if (Math.abs(diff) <= maxRot) {
                this.client.player.setYaw(desiredYaw);
            } else {
                this.client.player.setYaw(this.client.player.getYaw() - Math.signum(diff) * maxRot);
            }

            if (XearoHelper.get().distanceToWaypoint(this.currentWaypoint) < this.arrivalRadius.get().intValue()) {
                ChatUtils.info(
                    Formatting.GOLD
                        + "[WaypointFollower] "
                        + Formatting.WHITE
                        + "Arrived at "
                        + Formatting.YELLOW
                        + this.currentWaypoint.name()
                        + Formatting.WHITE
                        + ", moving to next..."
                );
                XearoHelper.get().deleteCurrentWaypoint(this.currentWaypoint);
                this.currentWaypoint = null;
            }
        }
    }

    private void tickYawOnly() {
        if (this.currentWaypoint == null) {
            this.currentWaypoint = XearoHelper.get().getOldestWaypoint(true);
            if (this.currentWaypoint == null) {
                return;
            }

            ChatUtils.info(Formatting.GOLD + "[WaypointFollower] " + Formatting.WHITE + "Flying to: " + Formatting.YELLOW + this.currentWaypoint.name());
        }

        float desiredYaw = this.yawToWaypoint(this.currentWaypoint);
        float diff = this.yawDifference(this.client.player.getYaw(), desiredYaw);
        float maxRot = (float)this.degreesPerTick.get().doubleValue();
        if (Math.abs(diff) <= maxRot) {
            this.client.player.setYaw(desiredYaw);
        } else {
            this.client.player.setYaw(this.client.player.getYaw() - Math.signum(diff) * maxRot);
        }

        if (XearoHelper.get().distanceToWaypoint(this.currentWaypoint) < this.arrivalRadius.get().intValue()) {
            ChatUtils.info(
                Formatting.GOLD
                    + "[WaypointFollower] "
                    + Formatting.WHITE
                    + "Arrived at "
                    + Formatting.YELLOW
                    + this.currentWaypoint.name()
                    + Formatting.WHITE
                    + ", moving to next..."
            );
            XearoHelper.get().deleteCurrentWaypoint(this.currentWaypoint);
            this.currentWaypoint = null;
        }
    }

    private void drawPathOnMap() {
        if (XearoHelper.isLoaded()) {
            List<XearoHelper.WaypointData> wps = XearoHelper.get().getWaypoints(true);
            if (wps != null) {
                if (wps.size() != this.lastWaypointCount) {
                    this.lastWaypointCount = wps.size();
                    if (wps.isEmpty()) {
                        XearoHelper.get().clearLinesOnMap();
                    } else {
                        List<XearoHelper.WaypointData> sorted = new ArrayList<>(wps);
                        sorted.sort(Comparator.comparingLong(XearoHelper.WaypointData::createdAt));
                        List<XearoHelper.LineData> lines = new ArrayList<>();
                        double fromX = this.client.player.getX();
                        double fromZ = this.client.player.getZ();

                        for (XearoHelper.WaypointData wp : sorted) {
                            lines.add(new XearoHelper.LineData((int)fromX, (int)fromZ, wp.x(), wp.z()));
                            fromX = wp.x();
                            fromZ = wp.z();
                        }

                        XearoHelper.get().drawLinesOnMap(lines, -16733441);
                    }
                }
            }
        }
    }

    private void pathToWaypoint(XearoHelper.WaypointData wp) {
        ChatUtils.info(Formatting.GOLD + "[WaypointFollower] " + Formatting.WHITE + "Flying to next waypoint: " + Formatting.GRAY + wp.name());
        BaritoneHelper.elytraTo(wp.x(), wp.z());
    }

    private float yawToWaypoint(XearoHelper.WaypointData wp) {
        double dx = wp.x() - this.client.player.getX();
        double dz = wp.z() - this.client.player.getZ();
        return (float)Math.toDegrees(Math.atan2(-dx, dz));
    }

    private float yawDifference(float a, float b) {
        float d = (a - b) % 360.0F;
        if (d > 180.0F) {
            d -= 360.0F;
        }

        if (d < -180.0F) {
            d += 360.0F;
        }

        return d;
    }

    public static enum FlightMode {
        Baritone,
        RocketFly,
        Custom;
    }
}
