package musheor.utils.manager;

import java.lang.reflect.Field;
import musheor.utils.HighwayBuilderLogic;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;

public class RotationManager {
    public static final int MOVEMENT = 0;
    public static final int INTERACT = 10;
    private static Float yaw = null;
    private static Float pitch = null;
    private static int priority;
    private static int stampAge = Integer.MIN_VALUE;
    private static RotationManager.MoveMode moveMode = RotationManager.MoveMode.Preserve;
    private static boolean inputManaged;
    private static final Vec2f[] INPUTS = new Vec2f[]{
        new Vec2f(0.0F, 1.0F),
        new Vec2f(-1.0F, 1.0F),
        new Vec2f(-1.0F, 0.0F),
        new Vec2f(-1.0F, -1.0F),
        new Vec2f(0.0F, -1.0F),
        new Vec2f(1.0F, -1.0F),
        new Vec2f(1.0F, 0.0F),
        new Vec2f(1.0F, 1.0F)
    };
    private static Field headYawField;
    private static boolean headYawResolved;

    public static void setServerYaw(float y) {
        setYaw(y, 0);
    }

    public static void setServerPitch(float p) {
        setPitch(p, 0);
    }

    public static boolean setYaw(float y, int prio) {
        if (!claim(prio)) {
            return false;
        } else {
            yaw = y;
            return true;
        }
    }

    public static boolean setPitch(float p, int prio) {
        if (!claim(prio)) {
            return false;
        } else {
            pitch = p;
            return true;
        }
    }

    public static void setMoveMode(RotationManager.MoveMode mode, int prio) {
        if (claim(prio)) {
            moveMode = mode;
        }
    }

    public static void clear() {
        yaw = null;
        pitch = null;
        priority = 0;
        moveMode = RotationManager.MoveMode.Preserve;
        stampAge = Integer.MIN_VALUE;
    }

    public static void release(int prio) {
        if (stale() || priority == prio) {
            clear();
        }
    }

    private static boolean stale() {
        return HighwayBuilderLogic.mc.player == null || HighwayBuilderLogic.mc.player.age != stampAge;
    }

    private static boolean claim(int prio) {
        boolean fresh = stale();
        if (!fresh && prio < priority) {
            return false;
        } else {
            if (fresh || prio > priority) {
                moveMode = RotationManager.MoveMode.Preserve;
            }

            priority = prio;
            stampAge = HighwayBuilderLogic.mc.player != null ? HighwayBuilderLogic.mc.player.age : Integer.MIN_VALUE;
            return true;
        }
    }

    public static boolean isActive() {
        return yaw != null || pitch != null;
    }

    public static int ownerPriority() {
        return priority;
    }

    public static boolean outranksMovement() {
        return isActive() && priority > 0;
    }

    public static Float getPendingYaw() {
        return yaw;
    }

    public static Float getPendingPitch() {
        return pitch;
    }

    public static float yawOr(float fallback) {
        return yaw != null ? yaw : fallback;
    }

    public static float pitchOr(float fallback) {
        return pitch != null ? pitch : fallback;
    }

    public static boolean isInputManaged() {
        return inputManaged;
    }

    public static Vec2f rewriteInput(Vec2f current) {
        inputManaged = false;
        if (yaw != null && HighwayBuilderLogic.mc.player != null) {
            if (Math.abs(current.x) <= 1.0E-4F && Math.abs(current.y) <= 1.0E-4F) {
                return null;
            } else {
                int step;
                if (moveMode == RotationManager.MoveMode.Forward) {
                    step = 0;
                } else {
                    float intent = HighwayBuilderLogic.mc.player.getYaw() + (float)Math.toDegrees(Math.atan2(-current.x, current.y));
                    step = Math.round(MathHelper.wrapDegrees(intent - yaw) / 45.0F) & 7;
                }

                inputManaged = true;
                return INPUTS[step];
            }
        } else {
            return null;
        }
    }

    public static float snapYaw(float y) {
        int quadrant = Math.round(MathHelper.wrapDegrees(y) / 90.0F);
        return MathHelper.wrapDegrees(quadrant * 90.0F);
    }

    public static float snapPitch(float p) {
        if (p < -45.0F) {
            return -90.0F;
        } else {
            return p > 45.0F ? 90.0F : 0.0F;
        }
    }

    public static void setModelHeadYaw(Object renderState, float value) {
        if (!headYawResolved) {
            headYawResolved = true;

            for (String name : new String[]{"relativeHeadYaw", "yawDegrees"}) {
                try {
                    headYawField = renderState.getClass().getField(name);
                    break;
                } catch (NoSuchFieldException var8) {
                }
            }
        }

        if (headYawField != null) {
            try {
                headYawField.setFloat(renderState, value);
            } catch (IllegalAccessException var7) {
            }
        }
    }

    public static enum MoveMode {
        Preserve,
        Forward;
    }
}
