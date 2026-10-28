package musheor.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.commands.arguments.PlayerListEntryArgumentType;
import meteordevelopment.meteorclient.systems.modules.Modules;
import musheor.compat.VersionHelper;
import musheor.modules.tech.Whisper;
import musheor.utils.PlayerControlUtils;
import net.minecraft.command.CommandSource;

public class WhisperCommand extends Command {
    public static String lastPlayer;
    static int antiSpamLength = Modules.get().get(Whisper.class).antiSpamLength.get();

    public WhisperCommand() {
        super("tp", "Sends a random message to another player.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(argument("player", PlayerListEntryArgumentType.create()).executes(context -> {
            if (VersionHelper.get().getName(context) != null) {
                lastPlayer = VersionHelper.get().getName(context);
            }

            antiSpamLength = Modules.get().get(Whisper.class).antiSpamLength.get();
            PlayerControlUtils.sendTeleportRequest(lastPlayer, antiSpamLength);
            return 1;
        }));
        builder.executes(context -> {
            antiSpamLength = Modules.get().get(Whisper.class).antiSpamLength.get();
            PlayerControlUtils.sendTeleportRequest(lastPlayer, antiSpamLength);
            return 1;
        });
    }
}
