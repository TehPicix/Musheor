package musheor.utils;

import meteordevelopment.discordipc.DiscordIPC;
import meteordevelopment.discordipc.RichPresence;
import musheor.utils.highway.HighwayState;
import musheor.utils.system.MusheorSystem;

public class DiscordRpc {
    private static final long APPLICATION_ID = 1346947991059300434L;
    private static final RichPresence presence = new RichPresence();

    public static void start() {
        DiscordIPC.start(1346947991059300434L, null);
        presence.setStart(System.currentTimeMillis() / 1000L);
        presence.setLargeImage("musheor_logo", "Musheor Highway Builder");
        update();
    }

    public static void stop() {
        DiscordIPC.stop();
    }

    public static void update() {
        if (HighwayState.getInstance().getRuntimeTicks() > 20) {
            presence.setDetails(buildDetails());
            presence.setState(buildState());
            DiscordIPC.setActivity(presence);
            MusheorSystem.debug("Updated RPC.");
        }
    }

    private static String buildDetails() {
        return String.format(
            "Obsidian placed | mined: %s | %s",
            HighwayStatsUtils.formatCompact(HighwayState.getInstance().getObsidianPlaced()),
            HighwayStatsUtils.formatCompact(HighwayState.getInstance().getObsidianMined())
        );
    }

    private static String buildState() {
        return String.format(
            "Lifetime: %s placed | %s mined",
            HighwayStatsUtils.formatCompact(HighwayState.getInstance().getLifetimeObsidianPlaced()),
            HighwayStatsUtils.formatCompact(HighwayState.getInstance().getLifetimeObsidianMined())
        );
    }
}
