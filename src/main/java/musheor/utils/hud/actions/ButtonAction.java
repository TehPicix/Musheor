package musheor.utils.hud.actions;

import musheor.compat.VersionHelper;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.ScreenHandler;

public interface ButtonAction {
    void execute(ScreenHandler var1);

    String getType();

    NbtCompound toTag();

    static ButtonAction fromTag(NbtCompound tag) {
        if (!tag.contains("type")) {
            return null;
        } else {
            String var1 = VersionHelper.get().getString(tag, "type");

            return (ButtonAction)(switch (var1) {
                case "toggle_module" -> ToggleModuleAction.fromTag(tag);
                case "send_message" -> SendMessageAction.fromTag(tag);
                case "run_command" -> RunCommandAction.fromTag(tag);
                case "change_setting" -> ChangeSettingAction.fromTag(tag);
                default -> null;
            });
        }
    }
}
