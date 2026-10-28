package musheor.modules.tech;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import musheor.musheor;
import musheor.utils.manager.InventoryManager;
import musheor.utils.network.PacketLimiter;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

public class ContainerTweaks extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final MinecraftClient client = MinecraftClient.getInstance();
    public static ContainerTweaks INSTANCE;
    private final Setting<Boolean> onlyShulkers = this.sgGeneral
        .add(new Builder().name("only-shulkers").description("Only moves shulkers.").defaultValue(true).build());
    public final Setting<Boolean> showButtons = this.sgGeneral
        .add(new Builder().name("show-buttons").description("Shows stealing and dumping buttons.").defaultValue(false).build());
    public final Setting<Keybind> moveMatching = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.KeybindSetting.Builder()
                .name("move-matching")
                .description("Moves all matching items between inventories.")
                .defaultValue(Keybind.none())
                .build()
        );
    public final Setting<Keybind> moveAll = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.KeybindSetting.Builder()
                .name("move-all")
                .description("Moves all items from one inventory to the other.")
                .defaultValue(Keybind.none())
                .build()
        );
    public final Setting<List<Item>> bundleItemWhitelist = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.ItemListSetting.Builder()
                .name("bundle-item-whitelist")
                .description("Items that are allowed to be bundled / unbundled")
                .defaultValue(new ArrayList<>())
                .build()
        );
    public final Setting<Integer> stealOffsetX = this.sgGeneral
        .add(new meteordevelopment.meteorclient.settings.IntSetting.Builder().name("steal-offset-x").defaultValue(0).visible(() -> false).build());
    public final Setting<Integer> stealOffsetY = this.sgGeneral
        .add(new meteordevelopment.meteorclient.settings.IntSetting.Builder().name("steal-offset-y").defaultValue(0).visible(() -> false).build());
    public final Setting<Integer> dumpOffsetX = this.sgGeneral
        .add(new meteordevelopment.meteorclient.settings.IntSetting.Builder().name("dump-offset-x").defaultValue(0).visible(() -> false).build());
    public final Setting<Integer> dumpOffsetY = this.sgGeneral
        .add(new meteordevelopment.meteorclient.settings.IntSetting.Builder().name("dump-offset-y").defaultValue(0).visible(() -> false).build());
    public final Setting<Integer> bundleOffsetX = this.sgGeneral
        .add(new meteordevelopment.meteorclient.settings.IntSetting.Builder().name("bundle-offset-x").defaultValue(0).visible(() -> false).build());
    public final Setting<Integer> bundleOffsetY = this.sgGeneral
        .add(new meteordevelopment.meteorclient.settings.IntSetting.Builder().name("bundle-offset-y").defaultValue(0).visible(() -> false).build());
    public final Setting<Integer> unbundleOffsetX = this.sgGeneral
        .add(new meteordevelopment.meteorclient.settings.IntSetting.Builder().name("unbundle-offset-x").defaultValue(0).visible(() -> false).build());
    public final Setting<Integer> unbundleOffsetY = this.sgGeneral
        .add(new meteordevelopment.meteorclient.settings.IntSetting.Builder().name("unbundle-offset-y").defaultValue(0).visible(() -> false).build());

    public ContainerTweaks() {
        super(musheor.MAIN, "container-tweaks", "A more advanced way to steal and store items in containers");
        INSTANCE = this;
    }

    private void quickMoveRange(ScreenHandler handler, int start, int end, boolean steal) {
        for (int i = start;
            i < end
                && !this.isPacketLimitReached()
                && (InventoryManager.countEmptySlots() != 0 || !steal)
                && (InventoryManager.countEmptyContainerSlots(handler) != 0 || steal);
            i++
        ) {
            if (handler.getSlot(i).hasStack()) {
                ItemStack stack = handler.getSlot(i).getStack();
                if (this.isShulkerBox(stack) || !this.onlyShulkers.get()) {
                    if (this.client.currentScreen == null || !Utils.canUpdate()) {
                        break;
                    }

                    this.client.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, this.client.player);
                }
            }
        }
    }

    public void moveMatchingItems(ScreenHandler handler, Slot clickedSlot) {
        ItemStack target = clickedSlot.getStack().copy();
        if (!target.isEmpty()) {
            boolean clickedInPlayerInv = clickedSlot.inventory == this.client.player.getInventory();
            MeteorExecutor.execute(
                () -> {
                    for (int i = 0;
                        i < handler.slots.size()
                            && (InventoryManager.countEmptySlots() != 0 || clickedInPlayerInv)
                            && (InventoryManager.countEmptyContainerSlots(handler) != 0 || !clickedInPlayerInv)
                            && !this.isPacketLimitReached();
                        i++
                    ) {
                        Slot slot = handler.getSlot(i);
                        if ((clickedInPlayerInv ? slot.inventory == this.client.player.getInventory() : slot.inventory != this.client.player.getInventory())
                            && slot.hasStack()
                            && (this.isShulkerBox(slot.getStack()) || !this.onlyShulkers.get())
                            && ItemStack.areItemsAndComponentsEqual(slot.getStack(), target)) {
                            if (this.client.currentScreen == null || !Utils.canUpdate()) {
                                break;
                            }

                            this.client.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, this.client.player);
                        }
                    }
                }
            );
        }
    }

    public void moveAllItems(ScreenHandler handler, Slot clickedSlot) {
        ItemStack target = clickedSlot.getStack();
        if (!target.isEmpty()) {
            boolean clickedInPlayerInv = clickedSlot.inventory == this.client.player.getInventory();
            MeteorExecutor.execute(
                () -> {
                    for (int i = 0;
                        i < handler.slots.size()
                            && (InventoryManager.countEmptySlots() != 0 || clickedInPlayerInv)
                            && (InventoryManager.countEmptyContainerSlots(handler) != 0 || !clickedInPlayerInv)
                            && !this.isPacketLimitReached();
                        i++
                    ) {
                        Slot slot = handler.getSlot(i);
                        if ((clickedInPlayerInv ? slot.inventory == this.client.player.getInventory() : slot.inventory != this.client.player.getInventory())
                            && slot.hasStack()
                            && (this.isShulkerBox(slot.getStack()) || !this.onlyShulkers.get())) {
                            if (this.client.currentScreen == null || !Utils.canUpdate()) {
                                break;
                            }

                            this.client.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, this.client.player);
                        }
                    }
                }
            );
        }
    }

    public void stealItems(ScreenHandler handler) {
        MeteorExecutor.execute(() -> this.quickMoveRange(handler, 0, SlotUtils.indexToId(9), true));
    }

    public void dumpItems(ScreenHandler handler) {
        int playerInvOffset = SlotUtils.indexToId(9);
        MeteorExecutor.execute(() -> this.quickMoveRange(handler, playerInvOffset, playerInvOffset + 36, false));
    }

    private boolean isPacketLimitReached() {
        if (!PacketLimiter.canSendInventoryPacket()) {
            this.warning("§cCancelled slot movement to prevent packet kick!", new Object[0]);
            return true;
        } else {
            return false;
        }
    }

    boolean isShulkerBox(ItemStack stack) {
        return stack.getItem() instanceof BlockItem && ((BlockItem)stack.getItem()).getBlock() instanceof ShulkerBoxBlock;
    }
}
