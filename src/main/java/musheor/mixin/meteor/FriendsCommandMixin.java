package musheor.mixin.meteor;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.commands.arguments.FriendArgumentType;
import meteordevelopment.meteorclient.commands.commands.FriendsCommand;
import meteordevelopment.meteorclient.systems.friends.Friend;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.command.CommandSource;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
    value = {FriendsCommand.class},
    remap = false
)
public abstract class FriendsCommandMixin extends Command {
    protected FriendsCommandMixin(String name, String description, String... aliases) {
        super(name, description, aliases);
    }

    @Inject(
        method = {"build"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void build(LiteralArgumentBuilder<CommandSource> builder, CallbackInfo ci) {
        builder.then(literal("add").then(argument("player", StringArgumentType.word()).executes(context -> {
            String name = StringArgumentType.getString(context, "player");
            Friend friend = new Friend(name);
            if (Friends.get().add(friend)) {
                ChatUtils.sendMsg(friend.hashCode(), Formatting.GRAY, "Added (highlight)%s (default)to friends.".formatted(name));
            } else {
                this.error("Already friends with that player.", new Object[0]);
            }

            return 1;
        })));
        builder.then(literal("remove").then(argument("friend", FriendArgumentType.create()).executes(context -> {
            Friend friend = FriendArgumentType.get(context);
            if (friend == null) {
                this.error("Not friends with that player.", new Object[0]);
                return 1;
            } else {
                if (Friends.get().remove(friend)) {
                    ChatUtils.sendMsg(friend.hashCode(), Formatting.GRAY, "Removed (highlight)%s (default)from friends.".formatted(friend.getName()));
                } else {
                    this.error("Failed to remove that friend.", new Object[0]);
                }

                return 1;
            }
        })));
        builder.then(literal("list").executes(context -> {
            this.info("--- Friends ((highlight)%s(default)) ---", new Object[]{Friends.get().count()});
            Friends.get().forEach(f -> ChatUtils.info("(highlight)%s".formatted(f.getName())));
            return 1;
        }));
        ci.cancel();
    }
}
