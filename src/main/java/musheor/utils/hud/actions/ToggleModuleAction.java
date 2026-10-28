package musheor.utils.hud.actions;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import musheor.compat.VersionHelper;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.ScreenHandler;

public class ToggleModuleAction implements ButtonAction {
    public String moduleName;

    public ToggleModuleAction(String moduleName) {
        this.moduleName = moduleName;
    }

    @Override
    public void execute(ScreenHandler handler) {
        Module mod = Modules.get().get(this.moduleName);
        if (mod != null) {
            mod.toggle();
        }
    }

    @Override
    public String getType() {
        return "toggle_module";
    }

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();
        tag.putString("type", this.getType());
        tag.putString("module", this.moduleName);
        return tag;
    }

    public static ToggleModuleAction fromTag(NbtCompound tag) {
        return new ToggleModuleAction(VersionHelper.get().getString(tag, "module"));
    }
}
