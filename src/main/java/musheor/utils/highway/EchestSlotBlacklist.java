package musheor.utils.highway;

import java.util.HashSet;
import java.util.Set;
import meteordevelopment.meteorclient.systems.modules.Modules;

public final class EchestSlotBlacklist {
    private static final Set<Integer> blacklistedSlots = new HashSet<>();

    public static boolean isAllowed(int slot) {
        return slot >= 0 && slot < 27 && !blacklistedSlots.contains(slot);
    }

    public static boolean isBlacklisted(int slot) {
        return blacklistedSlots.contains(slot);
    }

    public static void allowSlot(int slot) {
        if (slot >= 0 && slot < 27) {
            blacklistedSlots.remove(slot);
        }
    }

    public static void blacklistSlot(int slot) {
        if (slot >= 0 && slot < 27) {
            blacklistedSlots.add(slot);
        }
    }

    public static int toMask() {
        int mask = 0;

        for (int slot : blacklistedSlots) {
            if (slot >= 0 && slot < 27) {
                mask |= 1 << slot;
            }
        }

        return mask;
    }

    public static void loadMask(int mask) {
        blacklistedSlots.clear();

        for (int slot = 0; slot < 27; slot++) {
            if ((mask & 1 << slot) != 0) {
                blacklistedSlots.add(slot);
            }
        }
    }

    public static void save() {
        Modules.get().save();
    }
}
