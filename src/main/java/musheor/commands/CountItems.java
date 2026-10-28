package musheor.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import musheor.utils.manager.InventoryManager;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class CountItems extends Command {
    public CountItems() {
        super("countItems", "Counts how many items a player has in the inventory (including shulker boxes)");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(
            argument("item", ItemStackArgumentType.itemStack(REGISTRY_ACCESS))
                .executes(
                    context -> {
                        ItemStack stack = ItemStackArgumentType.getItemStackArgument(context, "item").createStack(1, false);
                        if (stack != null && stack.getItem() != Items.AIR) {
                            InventoryManager.countItemIncludingShulkers(stack.getItem());
                            this.info(
                                "%s %s items found!",
                                new Object[]{InventoryManager.countItemIncludingShulkers(stack.getItem()), stack.getItem().getName().getString()}
                            );
                        }

                        return 1;
                    }
                )
        );
    }
}
