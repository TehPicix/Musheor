package musheor.utils.hud.screen;

import java.util.List;
import musheor.utils.hud.CustomHudButton;
import musheor.utils.hud.actions.ButtonAction;
import musheor.utils.hud.actions.ChangeSettingAction;
import musheor.utils.hud.actions.RunCommandAction;
import musheor.utils.hud.actions.SendMessageAction;
import musheor.utils.hud.actions.ToggleModuleAction;
import musheor.utils.system.MusheorSystem;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class EditButtonScreen extends Screen {
    private final Screen parent;
    private final CustomHudButton button;
    private final List<CustomHudButton> allButtons;
    private TextFieldWidget labelField;
    private EditButtonScreen.ActionType selectedActionType = EditButtonScreen.ActionType.ToggleModule;
    private TextFieldWidget primaryField;
    private TextFieldWidget settingNameField;
    private TextFieldWidget valueField;

    public EditButtonScreen(Screen parent, CustomHudButton button, List<CustomHudButton> allButtons) {
        super(Text.literal("Edit Button: " + button.label));
        this.parent = parent;
        this.button = button;
        this.allButtons = allButtons;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        this.labelField = new TextFieldWidget(this.textRenderer, cx - 75, 30, 150, 18, Text.literal("Label"));
        this.labelField.setText(this.button.label);
        this.labelField.setMaxLength(32);
        this.addDrawableChild(this.labelField);
        this.addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> {
            this.button.cycleTextColor();
            MusheorSystem.get().save();
        }).position(cx - 75 + 152, 30).size(20, 20).build());

        for (CustomHudButton.ScreenTarget target : CustomHudButton.ScreenTarget.values()) {
            this.addDrawableChild(ButtonWidget.builder(Text.literal(target.name()), b -> {
                this.button.screenTarget = target;
                MusheorSystem.get().save();
            }).position(cx - 75 + target.ordinal() * 52, 62).size(50, 16).build());
        }

        this.addDrawableChild(ButtonWidget.builder(Text.literal("−"), b -> {
            this.button.width = Math.max(20, this.button.width - 2);
            MusheorSystem.get().save();
        }).position(cx - 75, 88).size(16, 16).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> {
            this.button.width = Math.min(200, this.button.width + 2);
            MusheorSystem.get().save();
        }).position(cx - 75 + 50, 88).size(16, 16).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("−"), b -> {
            this.button.height = Math.max(10, this.button.height - 2);
            MusheorSystem.get().save();
        }).position(cx + 10, 88).size(16, 16).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> {
            this.button.height = Math.min(40, this.button.height + 2);
            MusheorSystem.get().save();
        }).position(cx + 10 + 50, 88).size(16, 16).build());

        for (EditButtonScreen.ActionType type : EditButtonScreen.ActionType.values()) {
            this.addDrawableChild(ButtonWidget.builder(Text.literal(type.name()), b -> {
                this.selectedActionType = type;
                this.rebuildActionFields();
            }).position(cx - 150 + type.ordinal() * 78, 116).size(76, 16).build());
        }

        this.rebuildActionFields();
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Add Action"), b -> this.addAction()).position(cx - 80, this.height - 55).size(76, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Save & Back"), b -> {
            if (!this.labelField.getText().isBlank()) {
                this.button.label = this.labelField.getText();
            }

            MusheorSystem.get().save();
            this.client.setScreen(this.parent);
        }).position(cx + 4, this.height - 55).size(76, 20).build());
        this.addDrawableChild(
            ButtonWidget.builder(Text.literal("← Back"), b -> this.client.setScreen(this.parent)).position(cx - 25, this.height - 28).size(50, 16).build()
        );
    }

    private void rebuildActionFields() {
        if (this.primaryField != null) {
            this.remove(this.primaryField);
        }

        if (this.settingNameField != null) {
            this.remove(this.settingNameField);
        }

        if (this.valueField != null) {
            this.remove(this.valueField);
        }

        int cx = this.width / 2;
        int fy = 136;
        switch (this.selectedActionType) {
            case ToggleModule:
                this.primaryField = this.createTextField(cx - 75, fy, "Module name (e.g. KillAura)");
                this.settingNameField = null;
                this.valueField = null;
                break;
            case SendMessage:
                this.primaryField = this.createTextField(cx - 75, fy, "Message text");
                this.settingNameField = null;
                this.valueField = null;
                break;
            case RunCommand:
                this.primaryField = this.createTextField(cx - 75, fy, "Command (e.g. .tp or /gamemode)");
                this.settingNameField = null;
                this.valueField = null;
                break;
            case ChangeSetting:
                this.primaryField = this.createTextField(cx - 75, fy, "Module name");
                this.settingNameField = this.createTextField(cx - 75, fy + 24, "Setting name");
                this.valueField = this.createTextField(cx - 75, fy + 48, "Value");
        }

        if (this.primaryField != null) {
            this.addDrawableChild(this.primaryField);
        }

        if (this.settingNameField != null) {
            this.addDrawableChild(this.settingNameField);
        }

        if (this.valueField != null) {
            this.addDrawableChild(this.valueField);
        }
    }

    private TextFieldWidget createTextField(int x, int y, String placeholder) {
        TextFieldWidget f = new TextFieldWidget(this.textRenderer, x, y, 150, 20, Text.literal(placeholder));
        f.setPlaceholder(Text.literal(placeholder));
        f.setMaxLength(128);
        return f;
    }

    private void addAction() {
        if (this.primaryField != null && !this.primaryField.getText().isBlank()) {
            ButtonAction action = (ButtonAction)(switch (this.selectedActionType) {
                case ToggleModule -> new ToggleModuleAction(this.primaryField.getText().trim());
                case SendMessage -> new SendMessageAction(this.primaryField.getText().trim());
                case RunCommand -> new RunCommandAction(this.primaryField.getText().trim());
                case ChangeSetting -> this.settingNameField == null || this.valueField == null
                    ? null
                    : (
                        !this.settingNameField.getText().isBlank() && !this.valueField.getText().isBlank()
                            ? new ChangeSettingAction(
                                this.primaryField.getText().trim(), this.settingNameField.getText().trim(), this.valueField.getText().trim()
                            )
                            : null
                    );
            });
            if (action != null) {
                this.button.actions.add(action);
                this.primaryField.setText("");
                if (this.settingNameField != null) {
                    this.settingNameField.setText("");
                }

                if (this.valueField != null) {
                    this.valueField.setText("");
                }
            }
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        context.fill(0, 0, this.width, this.height, -1072689136);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        super.render(context, mouseX, mouseY, deltaTicks);
        int cx = this.width / 2;
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, cx, 10, -1);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Label:"), cx - 75, 20, -5592406);
        int swatchX = cx - 75 + 152;
        context.fill(swatchX + 2, 32, swatchX + 18, 48, 0xFF000000 | this.button.getTextColor());
        if (mouseX >= swatchX && mouseX <= swatchX + 20 && mouseY >= 30 && mouseY <= 50) {
            context.drawTextWithShadow(
                this.textRenderer,
                Text.literal(CustomHudButton.TEXT_COLOR_NAMES[this.button.textColorIndex]),
                swatchX + 22,
                37,
                0xFF000000 | this.button.getTextColor()
            );
        }

        context.drawTextWithShadow(this.textRenderer, Text.literal("Show on:"), cx - 75, 52, -5592406);

        for (CustomHudButton.ScreenTarget target : CustomHudButton.ScreenTarget.values()) {
            if (this.button.screenTarget == target) {
                int bx = cx - 75 + target.ordinal() * 52;
                context.fill(bx, 62, bx + 50, 78, -2011907099);
            }
        }

        context.drawTextWithShadow(this.textRenderer, Text.literal("Size:"), cx - 75, 80, -5592406);
        context.drawTextWithShadow(this.textRenderer, Text.literal("W:"), cx - 75 + 18, 90, -5592406);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(String.valueOf(this.button.width)), cx - 75 + 33, 90, -1);
        context.drawTextWithShadow(this.textRenderer, Text.literal("H:"), cx + 10 + 18, 90, -5592406);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(String.valueOf(this.button.height)), cx + 10 + 33, 90, -1);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Action type:"), cx - 75, 106, -5592406);

        for (EditButtonScreen.ActionType type : EditButtonScreen.ActionType.values()) {
            if (this.selectedActionType == type) {
                int bx = cx - 150 + type.ordinal() * 78;
                context.fill(bx, 116, bx + 76, 132, -2011907099);
            }
        }

        context.drawTextWithShadow(this.textRenderer, Text.literal("── Current actions ──"), cx - 75, 208, -5592406);
        int ay = 220;

        for (int i = 0; i < this.button.actions.size(); i++) {
            context.drawTextWithShadow(this.textRenderer, Text.literal(i + 1 + ". " + this.describeAction(this.button.actions.get(i))), cx - 75, ay, -1);
            int removeX = cx + 80;
            if (mouseX >= removeX && mouseX <= removeX + 30 && mouseY >= ay - 2 && mouseY <= ay + 10) {
                context.fill(removeX, ay - 2, removeX + 30, ay + 10, -1996541133);
            }

            context.drawTextWithShadow(this.textRenderer, Text.literal("[x]"), removeX, ay, -48060);
            ay += 14;
        }

        if (this.button.actions.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer, Text.literal("No actions yet."), cx - 75, ay, -7829368);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int cx = this.width / 2;
        int ay = 220;
        double mouseX = click.x();
        double mouseY = click.y();

        for (int i = 0; i < this.button.actions.size(); i++) {
            int removeX = cx + 80;
            if (mouseX >= removeX && mouseX <= removeX + 30 && mouseY >= ay - 2 && mouseY <= ay + 10) {
                this.button.actions.remove(i);
                return true;
            }

            ay += 14;
        }

        return super.mouseClicked(click, doubled);
    }

    private String describeAction(ButtonAction action) {
        return switch (action) {
            case ToggleModuleAction a -> "Toggle: " + a.moduleName;
            case SendMessageAction ax -> "Message: " + this.truncate(ax.message, 24);
            case RunCommandAction axx -> "Command: " + this.truncate(axx.command, 24);
            case ChangeSettingAction axxx -> "Setting: " + axxx.moduleName + "." + axxx.settingName + "=" + axxx.value;
            default -> action.getType();
        };
    }

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static enum ActionType {
        ToggleModule,
        SendMessage,
        RunCommand,
        ChangeSetting;
    }
}
