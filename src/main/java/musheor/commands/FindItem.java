package musheor.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import musheor.utils.HighwayUtils;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class FindItem extends Command {
    public FindItem() {
        super("find", "Find and pickup specific items using Baritone.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(argument("item", ItemStackArgumentType.itemStack(REGISTRY_ACCESS)).executes(context -> {
            ItemStack stack = ItemStackArgumentType.getItemStackArgument(context, "item").createStack(1, false);
            if (stack != null && stack.getItem() != Items.AIR) {
                HighwayUtils.pathToNearestItem(stack.getItem());
            }

            return 1;
        }));
    }
}
