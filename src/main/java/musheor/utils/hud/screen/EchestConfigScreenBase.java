package musheor.utils.hud.screen;

import meteordevelopment.meteorclient.utils.player.EChestMemory;
import musheor.utils.highway.EchestSlotBlacklist;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;

public abstract class EchestConfigScreenBase extends Screen {
    private final Screen parent;
    private int gridX;
    private int gridY;

    protected EchestConfigScreenBase(Screen parent) {
        super(Text.literal("E-Chest Restock Config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int gridW = 162;
        int gridH = 54;
        this.gridX = (this.width - gridW) / 2;
        this.gridY = (this.height - gridH) / 2;
    }

    private DefaultedList<ItemStack> getEchestItems() {
        return EChestMemory.isKnown() ? EChestMemory.ITEMS : null;
    }

    private int getSlotAt(double mouseX, double mouseY) {
        int relX = (int)(mouseX - this.gridX);
        int relY = (int)(mouseY - this.gridY);
        if (relX >= 0 && relY >= 0) {
            int col = relX / 18;
            int row = relY / 18;
            return col < 9 && row < 3 ? row * 9 + col : -1;
        } else {
            return -1;
        }
    }

    protected boolean handleSlotClick(double mouseX, double mouseY, int button) {
        int slot = this.getSlotAt(mouseX, mouseY);
        if (slot == -1) {
            return false;
        } else {
            if (button == 0) {
                EchestSlotBlacklist.allowSlot(slot);
            } else if (button == 1) {
                EchestSlotBlacklist.blacklistSlot(slot);
            }

            return true;
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        context.fill(0, 0, this.width, this.height, -1072689136);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        super.render(context, mouseX, mouseY, deltaTicks);
        int gridW = 162;
        int gridH = 54;
        context.fill(this.gridX - 8, this.gridY - 8, this.gridX + gridW + 8, this.gridY + gridH + 8, -266856424);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.gridY - 8 - 28, -1);
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.literal("Left-click = whitelist (green)   |   Right-click = blacklist (red)"),
            this.width / 2,
            this.gridY - 8 - 14,
            -5592406
        );
        DefaultedList<ItemStack> items = this.getEchestItems();
        if (items == null) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("unknown inventory"), this.width / 2, this.gridY + gridH + 8 + 8, -7829368);
        }

        int hovered = this.getSlotAt(mouseX, mouseY);
        ItemStack hoveredStack = null;

        for (int slot = 0; slot < 27; slot++) {
            int col = slot % 9;
            int row = slot / 9;
            int x = this.gridX + col * 18;
            int y = this.gridY + row * 18;
            context.fill(x, y, x + 18, y + 18, -13948117);
            int bg = EchestSlotBlacklist.isBlacklisted(slot) ? -1228219 : -12868259;
            context.fill(x + 1, y + 1, x + 18 - 1, y + 18 - 1, bg);
            if (items != null) {
                ItemStack stack = items.get(slot);
                if (!stack.isEmpty()) {
                    context.drawItem(stack, x + 1, y + 1);
                    context.drawStackOverlay(this.textRenderer, stack, x + 1, y + 1);
                    if (slot == hovered) {
                        hoveredStack = stack;
                    }
                }
            }

            if (slot == hovered) {
                context.fill(x + 1, y + 1, x + 18 - 1, y + 18 - 1, 1090519039);
            }
        }

        if (hoveredStack != null && !hoveredStack.isEmpty()) {
            context.drawItemTooltip(this.textRenderer, hoveredStack, mouseX, mouseY);
        }
    }

    @Override
    public void close() {
        EchestSlotBlacklist.save();
        if (this.client != null) {
            this.client.setScreen(this.parent);
        } else {
            super.close();
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
