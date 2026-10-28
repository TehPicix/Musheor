package musheor.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import musheor.compat.XearoHelper;
import net.minecraft.command.CommandSource;

public class XaeroUtilsCommand extends Command {
    public XaeroUtilsCommand() {
        super("xaero", "Xearo Utils Command");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("deleteAllTempWaypoints").executes(context -> {
            XearoHelper.get().deleteAllTempWaypoints();
            ChatUtils.info("Deleted all temp waypoints.");
            return 1;
        }));
    }
}
