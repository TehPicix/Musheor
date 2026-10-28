package musheor.utils.hud.screen;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;

public class EchestConfigScreen extends EchestConfigScreenBase {
    public EchestConfigScreen(Screen parent) {
        super(parent);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        return this.handleSlotClick(click.x(), click.y(), click.button()) ? true : super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        return this.handleSlotClick(click.x(), click.y(), click.button()) ? true : super.mouseDragged(click, offsetX, offsetY);
    }
}
