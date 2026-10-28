package musheor.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import musheor.modules.automation.ReKit;
import net.minecraft.command.CommandSource;

public class KitCommand extends Command {
    public KitCommand() {
        super("kit", "Manage ReKit loadouts. Usage: .kit create|load|delete|list [name]");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("create").then(argument("name", StringArgumentType.word()).executes(ctx -> {
            String name = StringArgumentType.getString(ctx, "name");
            Modules.get().get(ReKit.class).saveKit(name);
            return 1;
        })));
        builder.then(literal("load").then(argument("name", StringArgumentType.word()).executes(ctx -> {
            String name = StringArgumentType.getString(ctx, "name");
            ReKit rekit = Modules.get().get(ReKit.class);
            if (!rekit.kitExists(name)) {
                ChatUtils.error("Kit '§b" + name + "§c' not found. Use §b.kit create§c to save one.");
                return 1;
            } else {
                rekit.kitName.set(name);
                rekit.loadKit(name);
                ChatUtils.info("Active kit set to '§b" + name + "§r'.");
                return 1;
            }
        })));
        builder.then(literal("delete").then(argument("name", StringArgumentType.word()).executes(ctx -> {
            String name = StringArgumentType.getString(ctx, "name");
            Modules.get().get(ReKit.class).deleteKit(name);
            return 1;
        })));
        builder.then(literal("list").executes(ctx -> {
            List<String> kits = Modules.get().get(ReKit.class).listKits();
            if (kits.isEmpty()) {
                ChatUtils.info("No kits saved.");
            } else {
                ChatUtils.info("Saved kits: §b" + String.join("§r, §b", kits) + "§r");
            }

            return 1;
        }));
    }
}
