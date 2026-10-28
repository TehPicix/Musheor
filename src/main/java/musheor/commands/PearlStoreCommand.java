package musheor.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.commands.arguments.PlayerListEntryArgumentType;
import musheor.compat.VersionHelper;
import musheor.utils.PearlStore;
import net.minecraft.command.CommandSource;

public class PearlStoreCommand extends Command {
    public PearlStoreCommand() {
        super("pearlstore", "Util command for Pearl Store");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("clear").executes(context -> {
            PearlStore.clear();
            return 1;
        }));
        builder.then(literal("remove").then(argument("player", PlayerListEntryArgumentType.create()).executes(context -> {
            PearlStore.removeOwner(VersionHelper.get().getName(context));
            return 1;
        })));
    }
}
