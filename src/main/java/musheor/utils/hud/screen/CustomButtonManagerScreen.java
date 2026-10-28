package musheor.utils.hud.screen;

import java.util.List;
import musheor.utils.hud.CustomHudButton;
import musheor.utils.system.MusheorSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class CustomButtonManagerScreen extends Screen {
    private final Screen parent;
    private List<CustomHudButton> customButtons;

    public CustomButtonManagerScreen(Screen parent) {
        super(Text.literal("Custom HUD Buttons"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.customButtons = MusheorSystem.get().customButtons;
        this.rebuildButtons();
    }

    private void rebuildButtons() {
        this.clearChildren();
        int cx = this.width / 2;
        int y = 40;

        for (CustomHudButton btn : this.customButtons) {
            this.addDrawableChild(
                ButtonWidget.builder(Text.literal("✎ Edit"), button -> this.client.setScreen(new EditButtonScreen(this, btn, this.customButtons)))
                    .position(cx + 188, y)
                    .size(50, 20)
                    .build()
            );
            this.addDrawableChild(ButtonWidget.builder(Text.literal("✗ Delete"), button -> {
                this.customButtons.remove(btn);
                MusheorSystem.get().save();
                this.init();
            }).position(cx + 240, y).size(50, 20).build());
            y += 26;
        }

        this.addDrawableChild(ButtonWidget.builder(Text.literal("+ New Button"), b -> {
            CustomHudButton newBtn = CustomHudButton.create("New", this.width / 2 - 20, 40);
            this.customButtons.add(newBtn);
            MusheorSystem.get().save();
            this.client.setScreen(new EditButtonScreen(this, newBtn, this.customButtons));
        }).position(cx - 50, this.height - 48).size(100, 20).build());
        this.addDrawableChild(
            ButtonWidget.builder(Text.literal("← Back"), b -> this.client.setScreen(this.parent)).position(cx - 25, this.height - 24).size(50, 16).build()
        );
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        context.fill(0, 0, this.width, this.height, -1072689136);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        super.render(context, mouseX, mouseY, deltaTicks);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 14, -1);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Label"), this.width / 2 - 150, 28, -5592406);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Actions"), this.width / 2 + 50, 28, -5592406);
        int y = 40;

        for (CustomHudButton btn : this.customButtons) {
            context.drawTextWithShadow(this.textRenderer, Text.literal(btn.label), this.width / 2 - 150, y + 5, -1);
            context.drawTextWithShadow(this.textRenderer, Text.literal(btn.actions.size() + " action(s)"), this.width / 2 + 50, y + 5, -7798904);
            y += 26;
        }

        if (this.customButtons.isEmpty()) {
            context.drawCenteredTextWithShadow(
                this.textRenderer, Text.literal("No buttons yet. Press '+ New Button' to create one."), this.width / 2, 50, -7829368
            );
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
