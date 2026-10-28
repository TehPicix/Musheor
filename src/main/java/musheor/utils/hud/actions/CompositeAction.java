package musheor.utils.hud.actions;

import java.util.List;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.ScreenHandler;

public class CompositeAction implements ButtonAction {
    private final List<ButtonAction> actions;

    public CompositeAction(List<ButtonAction> actions) {
        this.actions = actions;
    }

    @Override
    public void execute(ScreenHandler handler) {
        for (ButtonAction action : this.actions) {
            action.execute(handler);
        }
    }

    @Override
    public String getType() {
        return "composite";
    }

    @Override
    public NbtCompound toTag() {
        return new NbtCompound();
    }
}
