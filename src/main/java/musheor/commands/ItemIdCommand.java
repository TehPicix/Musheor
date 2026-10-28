package musheor.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;

public class ItemIdCommand extends Command {
    public ItemIdCommand() {
        super("itemid", "Lists the entity ids of all entities within a range of the player");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            this.listEntities(16);
            return 1;
        });
        builder.then(argument("range", IntegerArgumentType.integer(1, 512)).executes(context -> {
            this.listEntities(IntegerArgumentType.getInteger(context, "range"));
            return 1;
        }));
    }

    private void listEntities(int range) {
        if (mc.world != null && mc.player != null) {
            List<Entity> found = new ArrayList<>();

            for (Entity entity : mc.world.getEntities()) {
                if (entity != mc.player && !(mc.player.distanceTo(entity) > range)) {
                    found.add(entity);
                }
            }

            if (found.isEmpty()) {
                this.info("No entities found within §b%d§r blocks.", new Object[]{range});
            } else {
                found.sort(Comparator.comparingDouble(mc.player::distanceTo));
                this.info("Found §b%d§r entities within §b%d§r blocks:", new Object[]{found.size(), range});
                int listed = 0;

                for (Entity entityx : found) {
                    if (listed++ >= 100) {
                        this.info("§7... and %d more (increase chat length or lower the range).", new Object[]{found.size() - 100});
                        break;
                    }

                    this.info("§b%d§r - %s §7(%.1fm)", new Object[]{entityx.getId(), this.describeEntity(entityx), mc.player.distanceTo(entityx)});
                }
            }
        } else {
            this.error("You must be in a world.", new Object[0]);
        }
    }

    private String describeEntity(Entity entity) {
        String type = Registries.ENTITY_TYPE.getId(entity.getType()).toString().replace("minecraft:", "");
        if (entity instanceof PlayerEntity player) {
            return type + " §a" + player.getName().getString();
        } else if (entity instanceof ItemEntity item) {
            return type + " §a" + item.getStack().getCount() + "x " + Registries.ITEM.getId(item.getStack().getItem()).toString().replace("minecraft:", "");
        } else {
            String name = entity.getName().getString();
            return !entity.hasCustomName() && name.equalsIgnoreCase(type.replace('_', ' ')) ? type : type + " §a" + name;
        }
    }
}
