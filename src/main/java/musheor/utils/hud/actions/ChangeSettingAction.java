package musheor.utils.hud.actions;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import musheor.compat.VersionHelper;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.ScreenHandler;

public class ChangeSettingAction implements ButtonAction {
    public String moduleName;
    public String settingName;
    public String value;

    public ChangeSettingAction(String moduleName, String settingName, String value) {
        this.moduleName = moduleName;
        this.settingName = settingName;
        this.value = value;
    }

    @Override
    public void execute(ScreenHandler handler) {
        Module mod = Modules.get().get(this.moduleName);
        if (mod != null) {
            mod.settings.forEach(group -> group.forEach(setting -> {
                if (setting.name.equalsIgnoreCase(this.settingName)) {
                    setting.parse(this.value);
                }
            }));
        }
    }

    @Override
    public String getType() {
        return "change_setting";
    }

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();
        tag.putString("type", this.getType());
        tag.putString("module", this.moduleName);
        tag.putString("setting", this.settingName);
        tag.putString("value", this.value);
        return tag;
    }

    public static ChangeSettingAction fromTag(NbtCompound tag) {
        return new ChangeSettingAction(
            VersionHelper.get().getString(tag, "module"), VersionHelper.get().getString(tag, "setting"), VersionHelper.get().getString(tag, "value")
        );
    }
}
