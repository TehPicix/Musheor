package musheor.utils.hud;

import java.util.function.Supplier;
import meteordevelopment.meteorclient.settings.Setting;
import musheor.compat.VersionHelper;
import net.minecraft.client.font.DrawnTextConsumer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.DrawContext.HoverType;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ButtonWidget.PressAction;
import net.minecraft.text.Style;
import net.minecraft.text.TextColor;

public class DraggableButtonWidget extends ButtonWidget {
    private boolean dragging = false;
    private int dragOffsetX;
    private int dragOffsetY;
    private final net.minecraft.text.Text labelText;
    private final Supplier<net.minecraft.text.Text> labelSupplier;
    private final int textColor;
    private final DraggableButtonWidget.PositionSaveCallback positionSaveCallback;

    public DraggableButtonWidget(
        int x, int y, int width, int height, net.minecraft.text.Text label, PressAction action, Setting<Integer> settingX, Setting<Integer> settingY
    ) {
        super(x, y, width, height, net.minecraft.text.Text.empty(), action, DEFAULT_NARRATION_SUPPLIER);
        this.labelText = label;
        this.labelSupplier = null;
        this.textColor = 16777215;
        this.positionSaveCallback = (nx, ny) -> {
            settingX.set(nx);
            settingY.set(ny);
        };
    }

    public DraggableButtonWidget(
        int x,
        int y,
        int width,
        int height,
        net.minecraft.text.Text label,
        int textColor,
        PressAction action,
        DraggableButtonWidget.PositionSaveCallback onPositionSaved
    ) {
        super(x, y, width, height, net.minecraft.text.Text.empty(), action, DEFAULT_NARRATION_SUPPLIER);
        this.labelText = label;
        this.labelSupplier = null;
        this.textColor = textColor;
        this.positionSaveCallback = onPositionSaved;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (VersionHelper.get().hasControlDown() && this.isMouseOver(click.x(), click.y())) {
            this.dragging = true;
            this.dragOffsetX = (int)click.x() - this.getX();
            this.dragOffsetY = (int)click.y() - this.getY();
            return true;
        } else {
            return super.mouseClicked(click, doubled);
        }
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (this.dragging && VersionHelper.get().hasControlDown()) {
            this.setX((int)click.x() - this.dragOffsetX);
            this.setY((int)click.y() - this.dragOffsetY);
            return true;
        } else {
            if (this.dragging) {
                this.dragging = false;
            }

            return super.mouseDragged(click, offsetX, offsetY);
        }
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (this.dragging) {
            this.dragging = false;
            this.positionSaveCallback.save(this.getX(), this.getY());
            return true;
        } else {
            return super.mouseReleased(click);
        }
    }

    @Override
    protected void drawLabel(DrawnTextConsumer drawer) {
        net.minecraft.text.Text text;
        if (VersionHelper.get().hasControlDown()) {
            text = net.minecraft.text.Text.literal("✥");
        } else {
            net.minecraft.text.Text label = this.labelSupplier != null ? this.labelSupplier.get() : this.labelText;
            text = net.minecraft.text.Text.literal(label.getString())
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFF000000 | this.textColor & 16777215)));
        }

        this.drawTextWithMargin(drawer, text, 2);
    }

    @Override
    protected void drawIcon(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        this.drawButton(context);
        this.drawLabel(context.getHoverListener(this, HoverType.NONE));
        if (VersionHelper.get().hasControlDown()) {
            context.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), -2011907099);
        }
    }

    public interface PositionSaveCallback {
        void save(int var1, int var2);
    }
}
