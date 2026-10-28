package musheor.utils.hud.actions;

import meteordevelopment.meteorclient.utils.player.ChatUtils;
import musheor.compat.VersionHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.ScreenHandler;

public class RunCommandAction implements ButtonAction {
    public String command;
    private static final MinecraftClient client = MinecraftClient.getInstance();

    public RunCommandAction(String command) {
        this.command = command;
    }

    @Override
    public void execute(ScreenHandler handler) {
        if (client.player != null && client.getNetworkHandler() != null) {
            if (this.command.startsWith(".")) {
                ChatUtils.sendPlayerMsg(this.command);
            } else if (this.command.startsWith("/")) {
                VersionHelper.get().sendCommand(this.command.substring(1));
            } else {
                VersionHelper.get().sendCommand(this.command);
            }
        }
    }

    @Override
    public String getType() {
        return "run_command";
    }

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();
        tag.putString("type", this.getType());
        tag.putString("command", this.command);
        return tag;
    }

    public static RunCommandAction fromTag(NbtCompound tag) {
        return new RunCommandAction(VersionHelper.get().getString(tag, "command"));
    }
}
