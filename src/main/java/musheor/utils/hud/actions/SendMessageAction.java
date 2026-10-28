package musheor.utils.hud.actions;

import musheor.compat.VersionHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.ScreenHandler;

public class SendMessageAction implements ButtonAction {
    public String message;
    private static final MinecraftClient client = MinecraftClient.getInstance();

    public SendMessageAction(String message) {
        this.message = message;
    }

    @Override
    public void execute(ScreenHandler handler) {
        if (client.player != null && client.player.networkHandler != null) {
            client.player.networkHandler.sendChatMessage(this.message);
        }
    }

    @Override
    public String getType() {
        return "send_message";
    }

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();
        tag.putString("type", this.getType());
        tag.putString("message", this.message);
        return tag;
    }

    public static SendMessageAction fromTag(NbtCompound tag) {
        return new SendMessageAction(VersionHelper.get().getString(tag, "message"));
    }
}
