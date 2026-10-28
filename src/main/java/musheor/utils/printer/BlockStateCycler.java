package musheor.utils.printer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ComparatorBlock;
import net.minecraft.block.DaylightDetectorBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.NoteBlock;
import net.minecraft.block.RepeaterBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;

public final class BlockStateCycler {
    private static final Set<String> IGNORED_PROPERTIES = Set.of(
        "powered",
        "locked",
        "lit",
        "waterlogged",
        "triggered",
        "extended",
        "north",
        "south",
        "east",
        "west",
        "up",
        "down",
        "distance",
        "persistent",
        "snowy",
        "signal_fire",
        "attached",
        "disarmed",
        "in_wall",
        "occupied",
        "bloom",
        "shrieking",
        "can_summon",
        "berries",
        "age",
        "stage",
        "level",
        "moisture",
        "hatch",
        "eggs",
        "power"
    );
    private static final Set<String> NON_TOGGLEABLE_BLOCKS = Set.of("iron_door", "iron_trapdoor");

    public static int getInteractionsNeeded(BlockState current, BlockState target) {
        if (current != null && target != null) {
            if (current.getBlock() != target.getBlock()) {
                return -1;
            } else {
                Property<?> cycled = getCycledProperty(current);
                if (cycled == null) {
                    return -1;
                } else {
                    for (Property<?> other : target.getProperties()) {
                        if (other != cycled
                            && !IGNORED_PROPERTIES.contains(other.getName())
                            && current.contains(other)
                            && !current.get(other).equals(target.get(other))) {
                            return -1;
                        }
                    }

                    return countCycleSteps(cycled, current, target);
                }
            }
        } else {
            return -1;
        }
    }

    private static Property<?> getCycledProperty(BlockState state) {
        Block block = state.getBlock();
        String name;
        if (block instanceof TrapdoorBlock || block instanceof DoorBlock || block instanceof FenceGateBlock) {
            if (NON_TOGGLEABLE_BLOCKS.contains(Registries.BLOCK.getId(block).getPath())) {
                return null;
            }

            if (block instanceof DoorBlock) {
                Property<?> half = block.getStateManager().getProperty("half");
                if (half != null && "upper".equals(getValueName(state, half))) {
                    return null;
                }
            }

            name = "open";
        } else if (block instanceof RepeaterBlock) {
            name = "delay";
        } else if (block instanceof ComparatorBlock) {
            name = "mode";
        } else if (block instanceof NoteBlock) {
            name = "note";
        } else if (block instanceof DaylightDetectorBlock) {
            name = "inverted";
        } else {
            if (!(block instanceof LeverBlock)) {
                return null;
            }

            name = "powered";
        }

        return block.getStateManager().getProperty(name);
    }

    private static <T extends Comparable<T>> int countCycleSteps(Property<T> prop, BlockState current, BlockState target) {
        if (current.contains(prop) && target.contains(prop)) {
            List<T> values = new ArrayList<>(prop.getValues());
            int from = values.indexOf(current.get(prop));
            int to = values.indexOf(target.get(prop));
            if (from >= 0 && to >= 0) {
                int size = values.size();
                return ((to - from) % size + size) % size;
            } else {
                return -1;
            }
        } else {
            return -1;
        }
    }

    private static <T extends Comparable<T>> String getValueName(BlockState state, Property<T> prop) {
        return prop.name(state.get(prop));
    }
}
