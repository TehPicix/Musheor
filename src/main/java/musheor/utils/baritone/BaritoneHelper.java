package musheor.utils.baritone;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.pathing.goals.GoalXZ;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

public class BaritoneHelper {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static boolean isPathing() {
        return BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing();
    }

    public static boolean hasPath() {
        return BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().hasPath();
    }

    public static boolean isBusy() {
        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        return baritone.getPathingBehavior().isPathing()
            || baritone.getPathingBehavior().getInProgress().isPresent()
            || baritone.getCustomGoalProcess().isActive();
    }

    public static void cancelAll() {
        BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
    }

    public static void setGoalAndPath(Goal goal) {
        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
    }

    public static void setGoalAndPath(GoalNear goal) {
        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
    }

    public static boolean elytraTo(int x, int z) {
        if (!BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().isActive()) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().pathTo(new GoalXZ(x, z));
            return true;
        } else {
            return false;
        }
    }

    public static void pathTo(BlockPos pos) {
        setGoalAndPath(new GoalBlock(pos));
    }

    public static void executeCommand(String command) {
        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute(command);
    }

    public static void gotoCommand(BlockPos itemPos) {
        executeCommand(String.format("goto %d %d %d", itemPos.getX(), itemPos.getY(), itemPos.getZ()));
    }
}
