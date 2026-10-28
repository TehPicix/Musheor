package musheor.compat;

import java.util.List;
import net.minecraft.util.math.Vec3d;

public interface XearoHelper {
    List<XearoHelper.WaypointData> getWaypoints(boolean var1);

    XearoHelper.WaypointData getOldestWaypoint(boolean var1);

    Object getCurrentWaypointSetHandle();

    void setWaypointSet(String var1);

    void restoreWaypointSet(Object var1);

    void addWaypointToCurrent(String var1, String var2, Vec3d var3, XearoHelper.WaypointColorHint var4);

    void addWaypointToCurrent(String var1, String var2, Vec3d var3, XearoHelper.WaypointColorHint var4, boolean var5);

    void deleteCurrentWaypoint(XearoHelper.WaypointData var1);

    void deleteAllTempWaypoints();

    void updateWaypointSettings();

    void drawLinesOnMap(List<XearoHelper.LineData> var1, int var2);

    void drawLinesOnMap(String var1, List<XearoHelper.LineData> var2, int var3);

    void clearLinesOnMap();

    void clearLinesOnMap(String var1);

    double distanceToWaypoint(XearoHelper.WaypointData var1);

    String getEtaSuffix(XearoHelper.WaypointData var1);

    static boolean isLoaded() {
        return XearoHelper.XearoHelperHolder.INSTANCE != null;
    }

    static XearoHelper get() {
        return XearoHelper.XearoHelperHolder.INSTANCE;
    }

    static void setInstance(XearoHelper instance) {
        XearoHelper.XearoHelperHolder.INSTANCE = instance;
    }

    public record LineData(int x1, int z1, int x2, int z2) {
    }

    public static enum WaypointColorHint {
        WHITE,
        RED,
        GOLD,
        BLUE;
    }

    public record WaypointData(int x, int y, int z, String name, boolean temporary, long createdAt) {
    }

    public static class XearoHelperHolder {
        static XearoHelper INSTANCE = null;
    }
}
