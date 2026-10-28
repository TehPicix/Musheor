package musheor.utils;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import musheor.modules.automation.HighwayBuilder;
import musheor.utils.highway.HighwayState;
import musheor.utils.manager.InventoryManager;
import net.minecraft.item.Items;

public class HighwayStatsUtils {
    public static int cachedDistanceTravelled = 0;

    public static String formatSectionProgress(int sectionSize) {
        double distanceLeft = getDistanceToNextSection(sectionSize);
        double percentage = (sectionSize - distanceLeft) / sectionSize * 100.0;
        return String.format("%.2f", percentage);
    }

    public static int computeDistanceTravelled() {
        HighwayUtils.HighwayDirection direction = HighwayBuilder.getDirection();

        assert MeteorClient.mc.player != null;

        int distanceTravelled = 0;
        if (direction == HighwayUtils.HighwayDirection.NORTH || direction == HighwayUtils.HighwayDirection.SOUTH) {
            distanceTravelled = Math.abs(MeteorClient.mc.player.getBlockZ()) - Math.abs(HighwayState.getInstance().getStartZ());
        } else if (direction == HighwayUtils.HighwayDirection.EAST || direction == HighwayUtils.HighwayDirection.WEST) {
            distanceTravelled = Math.abs(MeteorClient.mc.player.getBlockX()) - Math.abs(HighwayState.getInstance().getStartX());
        }

        if (direction == HighwayUtils.HighwayDirection.NORTH_EAST
            || direction == HighwayUtils.HighwayDirection.NORTH_WEST
            || direction == HighwayUtils.HighwayDirection.SOUTH_EAST
            || direction == HighwayUtils.HighwayDirection.SOUTH_WEST) {
            distanceTravelled = Math.abs(Math.abs(MeteorClient.mc.player.getBlockX()) - Math.abs(HighwayState.getInstance().getStartX()));
        }

        return Math.abs(distanceTravelled);
    }

    public static int getDistanceTravelled() {
        if (Modules.get().get(HighwayBuilder.class).isActive()) {
            cachedDistanceTravelled = computeDistanceTravelled();
        }

        return cachedDistanceTravelled;
    }

    public static double getPlacementsPerSecond() {
        if (!Modules.get().get(HighwayBuilder.class).isActive()) {
            return -1.0;
        } else {
            double obsidianPlaced = HighwayState.getInstance().getObsidianPlaced();
            double ticksPassed = HighwayState.getInstance().getRuntimeTicks();
            return obsidianPlaced != 0.0 && ticksPassed != 0.0 ? obsidianPlaced / (ticksPassed / 20.0) : 0.0;
        }
    }

    public static double getBreaksPerSecond() {
        if (!Modules.get().get(HighwayBuilder.class).isActive()) {
            return -1.0;
        } else {
            HighwayState state = HighwayState.getInstance();
            double blocksBroken = state.getObsidianMined() + state.getNetherrackMined() + state.getEchestsMined();
            double ticksPassed = state.getRuntimeTicks();
            return blocksBroken != 0.0 && ticksPassed != 0.0 ? blocksBroken / (ticksPassed / 20.0) : 0.0;
        }
    }

    public static int getMaterialCount() {
        return InventoryManager.countItemIncludingShulkers(Items.ENDER_CHEST) * 8 + InventoryManager.countItemIncludingShulkers(Items.OBSIDIAN);
    }

    public static double computeRatePerSecond(double distanceTravelled, double ticksPassed) {
        double secondsPassed = ticksPassed / 20.0;
        return secondsPassed <= 0.0 ? 0.0 : distanceTravelled / secondsPassed;
    }

    public static double estimateTimeToNextSection(int sectionSize) {
        HighwayState state = HighwayState.getInstance();
        if (getDistanceTravelled() >= 1 && state.getRuntimeTicks() >= 1) {
            long remainingDistance = getDistanceToNextSection(sectionSize);
            long distanceTravelled = getDistanceTravelled();
            long distancePerSecond = getDistanceTravelled() / (state.getRuntimeTicks() / 20);
            if (distanceTravelled <= 1L) {
                return -2.0;
            } else {
                return state.getRuntimeTicks() <= 200 ? -1.0 : remainingDistance / computeRatePerSecond(distanceTravelled, state.getRuntimeTicks());
            }
        } else {
            return 0.0;
        }
    }

    public static int getDistanceToNextSection(int sectionSize) {
        // Rebuilt by hand from bytecode: Vineflower 1.11.1 fails to decompile this enum pattern switch.
        assert MeteorClient.mc.player != null;

        HighwayUtils.HighwayDirection direction = HighwayBuilder.getDirection();
        int blockCoordinate = switch (direction) {
            case HighwayUtils.HighwayDirection.NORTH, HighwayUtils.HighwayDirection.SOUTH -> MeteorClient.mc.player.getBlockZ();
            case HighwayUtils.HighwayDirection.EAST,
                HighwayUtils.HighwayDirection.WEST,
                HighwayUtils.HighwayDirection.NORTH_EAST,
                HighwayUtils.HighwayDirection.NORTH_WEST,
                HighwayUtils.HighwayDirection.SOUTH_EAST,
                HighwayUtils.HighwayDirection.SOUTH_WEST -> MeteorClient.mc.player.getBlockX();
            case null, default -> 0;
        };
        int sectionStart = Math.floorDiv(blockCoordinate, sectionSize) * sectionSize;
        int sectionEnd = sectionStart + sectionSize;
        if (direction == HighwayUtils.HighwayDirection.SOUTH
            || direction == HighwayUtils.HighwayDirection.WEST
            || direction == HighwayUtils.HighwayDirection.NORTH_EAST
            || direction == HighwayUtils.HighwayDirection.SOUTH_EAST) {
            sectionStart = sectionEnd;
        }

        int blocksLeft = sectionStart - blockCoordinate;
        return Math.abs(blocksLeft);
    }

    public static String formatCompact(int value) {
        if (value >= 1000000) {
            return String.format("%.2fm", value / 1000000.0);
        } else {
            return value >= 1000 ? String.format("%.2fk", value / 1000.0) : String.valueOf(value);
        }
    }

    public static String formatPlacementsPerSecond(double placementsPerSecond) {
        return placementsPerSecond == -1.0 ? "Waiting..." : String.format("%.2f blocks / s", placementsPerSecond);
    }

    public static String formatPlacementsPerHour(double placementsPerSecond) {
        return placementsPerSecond == -1.0 ? "Waiting..." : String.format("%.2f blocks / h", placementsPerSecond * 3600.0);
    }

    public static String formatDistancePerSecond(int distanceTravelled) {
        return distanceTravelled < 1
            ? "Waiting..."
            : String.format("%.2f blocks / s", (float)computeRatePerSecond(distanceTravelled, HighwayState.getInstance().getRuntimeTicks()));
    }

    public static String formatDistancePerHour(int distanceTravelled) {
        return distanceTravelled < 1
            ? "Waiting..."
            : String.format("%.2f blocks / h", (float)computeRatePerSecond(distanceTravelled, HighwayState.getInstance().getRuntimeTicks()) * 3600.0F);
    }

    public static String formatBreaksPerSecond(double breakingPerSecond) {
        return breakingPerSecond == -1.0 ? "Waiting..." : String.format("%.2f blocks / s", breakingPerSecond);
    }

    public static String formatRefillEta() {
        if (getPlacementsPerSecond() == -1.0) {
            return "Waiting...";
        } else {
            long timeLeft = (long)(getMaterialCount() / getPlacementsPerSecond());
            long hours = timeLeft / 3600L;
            long minutes = timeLeft % 3600L / 60L;
            long seconds = timeLeft % 60L;
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
    }

    public static String formatSectionEta(int sectionSize) {
        if (getPlacementsPerSecond() == -1.0) {
            return "Waiting...";
        } else {
            long timeLeft = (long)(
                getDistanceToNextSection(sectionSize) / computeRatePerSecond(getDistanceTravelled(), HighwayState.getInstance().getRuntimeTicks())
            );
            long hours = timeLeft / 3600L;
            long minutes = timeLeft % 3600L / 60L;
            long seconds = timeLeft % 60L;
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
    }

    public static String formatTicksAsTime(long ticks) {
        if (ticks == -1L) {
            return "Calculating...";
        } else if (ticks == -2L) {
            return "Waiting...";
        } else {
            long totalSeconds = ticks / 20L;
            long hours = totalSeconds / 3600L;
            long minutes = totalSeconds % 3600L / 60L;
            long seconds = totalSeconds % 60L;
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
    }
}
