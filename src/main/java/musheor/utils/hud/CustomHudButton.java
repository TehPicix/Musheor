package musheor.utils.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import musheor.compat.VersionHelper;
import musheor.utils.hud.actions.ButtonAction;
import musheor.utils.hud.actions.CompositeAction;
import musheor.utils.system.MusheorSystem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;

public class CustomHudButton {
    public static final int[] TEXT_COLORS = new int[]{
        16777215, 16777045, 5636095, 5635925, 16733695, 16733525, 11184810, 5592405, 5592575, 43520, 43690, 11141120, 11141290, 16755200, 170, 0
    };
    public static final String[] TEXT_COLOR_NAMES = new String[]{
        "White",
        "Yellow",
        "Aqua",
        "Green",
        "Light Purple",
        "Red",
        "Gray",
        "Dark Gray",
        "Blue",
        "Dark Green",
        "Dark Aqua",
        "Dark Red",
        "Dark Purple",
        "Gold",
        "Dark Blue",
        "Black"
    };
    public String id;
    public String label;
    public int textColorIndex = 0;
    public int defaultX;
    public int defaultY;
    public int width = 40;
    public int height = 20;
    public CustomHudButton.ScreenTarget screenTarget = CustomHudButton.ScreenTarget.Inventory;
    public int savedX = Integer.MIN_VALUE;
    public int savedY = Integer.MIN_VALUE;
    public final List<ButtonAction> actions = new ArrayList<>();

    public CustomHudButton(String id, String label, int defaultX, int defaultY) {
        this.id = id;
        this.label = label;
        this.defaultX = defaultX;
        this.defaultY = defaultY;
    }

    public static CustomHudButton create(String label, int defaultX, int defaultY) {
        return new CustomHudButton(UUID.randomUUID().toString(), label, defaultX, defaultY);
    }

    public void cycleTextColor() {
        this.textColorIndex = (this.textColorIndex + 1) % TEXT_COLORS.length;
    }

    public int getTextColor() {
        return TEXT_COLORS[this.textColorIndex];
    }

    public int getX() {
        return this.savedX != Integer.MIN_VALUE ? this.savedX : this.defaultX;
    }

    public int getY() {
        return this.savedY != Integer.MIN_VALUE ? this.savedY : this.defaultY;
    }

    public DraggableButtonWidget createWidget(ScreenHandler handler) {
        CompositeAction composite = new CompositeAction(this.actions);
        return new DraggableButtonWidget(
            this.getX(),
            this.getY(),
            this.width,
            this.height,
            Text.literal(this.label),
            this.getTextColor(),
            btn -> composite.execute(handler),
            (newX, newY) -> {
                this.savedX = newX;
                this.savedY = newY;
                MusheorSystem.get().save();
            }
        );
    }

    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();
        tag.putString("id", this.id);
        tag.putString("label", this.label);
        tag.putInt("textColorIndex", this.textColorIndex);
        tag.putInt("defaultX", this.defaultX);
        tag.putInt("defaultY", this.defaultY);
        tag.putInt("width", this.width);
        tag.putInt("height", this.height);
        tag.putInt("savedX", this.savedX);
        tag.putInt("savedY", this.savedY);
        tag.putString("screenTarget", this.screenTarget.name());
        NbtList actionList = new NbtList();

        for (ButtonAction action : this.actions) {
            actionList.add(action.toTag());
        }

        tag.put("actions", actionList);
        return tag;
    }

    public static CustomHudButton fromTag(NbtCompound tag) {
        CustomHudButton btn = new CustomHudButton(
            VersionHelper.get().getString(tag, "id"),
            VersionHelper.get().getString(tag, "label"),
            VersionHelper.get().getInt(tag, "defaultX", 0),
            VersionHelper.get().getInt(tag, "defaultY", 0)
        );
        if (tag.contains("textColorIndex")) {
            btn.textColorIndex = VersionHelper.get().getInt(tag, "textColorIndex", 0);
        }

        if (tag.contains("width")) {
            btn.width = VersionHelper.get().getInt(tag, "width", 40);
        }

        if (tag.contains("height")) {
            btn.height = VersionHelper.get().getInt(tag, "height", 20);
        }

        if (tag.contains("savedX")) {
            btn.savedX = VersionHelper.get().getInt(tag, "savedX", Integer.MIN_VALUE);
        }

        if (tag.contains("savedY")) {
            btn.savedY = VersionHelper.get().getInt(tag, "savedY", Integer.MIN_VALUE);
        }

        if (tag.contains("screenTarget")) {
            try {
                btn.screenTarget = CustomHudButton.ScreenTarget.valueOf(VersionHelper.get().getString(tag, "screenTarget"));
            } catch (IllegalArgumentException var6) {
                btn.screenTarget = CustomHudButton.ScreenTarget.Inventory;
            }
        }

        for (NbtElement el : VersionHelper.get().getList(tag, "actions", 10)) {
            ButtonAction action = ButtonAction.fromTag((NbtCompound)el);
            if (action != null) {
                btn.actions.add(action);
            }
        }

        return btn;
    }

    public static enum ScreenTarget {
        Inventory,
        Chat,
        Both;

        public boolean appliesTo(CustomHudButton.ScreenTarget context) {
            return this == Both || this == context;
        }
    }
}
