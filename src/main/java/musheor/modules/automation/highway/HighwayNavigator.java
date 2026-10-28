package musheor.modules.automation.highway;

import musheor.compat.VersionHelper;
import musheor.utils.HighwayUtils;
import musheor.utils.baritone.BaritoneHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

public class HighwayNavigator {
    private final MinecraftClient client = MinecraftClient.getInstance();
    private final HighwayRouter.Route route;
    private final HighwayNavigator.BounceController bounceController;
    private final HighwayNavigator.Listener listener;
    private final HighwayNavigator.Config config;
    private HighwayNavigator.State state = HighwayNavigator.State.ALIGNING;
    private int legIndex = 0;
    private int alignTicks = 0;
    private int stationaryTicks = 0;
    private int settleTicksLeft = 0;
    private HighwayUtils.HorizontalPos lastPos = null;
    private double startDistance = Double.MAX_VALUE;

    public HighwayNavigator(
        HighwayRouter.Route route, HighwayNavigator.Config config, HighwayNavigator.BounceController bounceController, HighwayNavigator.Listener listener
    ) {
        this.route = route;
        this.config = config;
        this.bounceController = bounceController;
        this.listener = listener;
    }

    public HighwayNavigator.State getState() {
        return this.state;
    }

    public void tick() {
        if (this.client.player != null && this.client.world != null) {
            if (this.state != HighwayNavigator.State.DONE && this.state != HighwayNavigator.State.FAILED) {
                if (this.route.legs.isEmpty()) {
                    this.finish();
                } else {
                    switch (this.state) {
                        case ALIGNING:
                            this.tickAligning();
                            break;
                        case BOUNCING:
                            this.tickBouncing();
                            break;
                        case APPROACHING_WAYPOINT:
                            this.tickApproaching();
                            break;
                        case TRANSITIONING:
                            this.tickTransitioning();
                            break;
                        case SETTLING:
                            this.tickSettling();
                    }
                }
            }
        }
    }

    public void stop() {
        this.pauseBounce();
        if (BaritoneHelper.isPathing()) {
            BaritoneHelper.cancelAll();
        }
    }

    private void tickAligning() {
        HighwayUtils.HorizontalPos target = this.currentTarget();
        float desiredYaw = this.yawTo(target);
        this.client.player.setYaw(desiredYaw);
        float yawDiff = Math.abs(this.yawDifference(this.client.player.getYaw(), desiredYaw));
        this.alignTicks++;
        if (yawDiff <= this.config.yawToleranceDeg() || this.alignTicks >= this.config.alignTimeoutTicks()) {
            this.alignTicks = 0;
            this.startDistance = this.distanceTo(this.currentTarget());
            this.resumeBounce();
            this.setState(HighwayNavigator.State.BOUNCING, "bouncing toward " + this.formatTarget());
        }
    }

    private void tickBouncing() {
        HighwayUtils.HorizontalPos target = this.currentTarget();
        double dist = this.distanceTo(target);
        if (!BaritoneHelper.isBusy()) {
            this.client.player.setYaw(this.yawTo(target));
        }

        this.trackMovement();
        if (dist < this.config.approachRadius() && dist < this.startDistance) {
            this.setState(HighwayNavigator.State.APPROACHING_WAYPOINT, "approaching " + this.formatTarget());
        }
    }

    private void tickApproaching() {
        HighwayUtils.HorizontalPos target = this.currentTarget();
        double dist = this.distanceTo(target);
        this.client.player.setYaw(this.yawTo(target));
        if (dist < this.config.arriveRadius()) {
            this.pauseBounce();
            BlockPos goal = this.toBlockPos(target);
            BaritoneHelper.pathTo(goal);
            this.setState(HighwayNavigator.State.TRANSITIONING, "transitioning at " + this.formatTarget());
        }
    }

    private void tickTransitioning() {
        if (!BaritoneHelper.isBusy()) {
            this.settleTicksLeft = this.config.settleTicks();
            this.setState(HighwayNavigator.State.SETTLING, "settling at " + this.formatTarget());
        }
    }

    private void tickSettling() {
        int nextWpIdx = this.legIndex + 2;
        HighwayUtils.HorizontalPos faceTarget = nextWpIdx < this.route.waypoints.size() ? this.route.waypoints.get(nextWpIdx) : this.currentTarget();
        this.client.player.setYaw(this.yawTo(faceTarget));
        if (--this.settleTicksLeft <= 0) {
            this.advanceLeg();
        }
    }

    private void advanceLeg() {
        this.legIndex++;
        if (this.legIndex >= this.route.legs.size()) {
            this.finish();
        } else {
            this.resumeBounce();
            this.setState(HighwayNavigator.State.ALIGNING, "aligning for leg " + this.legIndex + ": " + this.currentLeg().highway().name());
        }
    }

    private void resumeBounce() {
        this.bounceController.setBouncing(true);
    }

    private void pauseBounce() {
        this.bounceController.setBouncing(false);
    }

    private void trackMovement() {
        HighwayUtils.HorizontalPos pos = this.playerPos();
        if (this.lastPos != null) {
            double moved = HighwayNetwork.distance(pos, this.lastPos);
            if (moved < 0.5) {
                this.stationaryTicks++;
            } else {
                this.stationaryTicks = 0;
            }
        }

        this.lastPos = pos;
    }

    private HighwayRouter.Leg currentLeg() {
        return this.route.legs.get(this.legIndex);
    }

    private HighwayUtils.HorizontalPos currentTarget() {
        return this.route.waypoints.get(this.legIndex + 1);
    }

    private String formatTarget() {
        HighwayUtils.HorizontalPos wp = this.currentTarget();
        return String.format("(%.0f, %.0f)", wp.x(), wp.z());
    }

    private double distanceTo(HighwayUtils.HorizontalPos target) {
        HighwayUtils.HorizontalPos pos = this.playerPos();
        return HighwayNetwork.distance(pos, target);
    }

    private HighwayUtils.HorizontalPos playerPos() {
        return new HighwayUtils.HorizontalPos(this.client.player.getX(), this.client.player.getZ());
    }

    private BlockPos toBlockPos(HighwayUtils.HorizontalPos p) {
        return new BlockPos((int)p.x(), (int)VersionHelper.get().getPlayerPos().getY(), (int)p.z());
    }

    private float yawTo(HighwayUtils.HorizontalPos target) {
        double dx = target.x() - this.client.player.getX();
        double dz = target.z() - this.client.player.getZ();
        return (float)Math.toDegrees(Math.atan2(-dx, dz));
    }

    private float yawDifference(float a, float b) {
        float diff = (a - b) % 360.0F;
        if (diff > 180.0F) {
            diff -= 360.0F;
        }

        if (diff < -180.0F) {
            diff += 360.0F;
        }

        return diff;
    }

    private void setState(HighwayNavigator.State newState, String description) {
        this.state = newState;
        this.listener.onStateChanged(newState, description);
    }

    private void finish() {
        this.pauseBounce();
        this.state = HighwayNavigator.State.DONE;
        this.listener.onRouteComplete();
    }

    public interface BounceController {
        void setBouncing(boolean var1);
    }

    public record Config(double approachRadius, double arriveRadius, int alignTimeoutTicks, float yawToleranceDeg, int transitionGoalDist, int settleTicks) {
    }

    public interface Listener {
        void onStateChanged(HighwayNavigator.State var1, String var2);

        void onRouteComplete();
    }

    public static enum State {
        ALIGNING,
        BOUNCING,
        APPROACHING_WAYPOINT,
        TRANSITIONING,
        SETTLING,
        DONE,
        FAILED;
    }
}
