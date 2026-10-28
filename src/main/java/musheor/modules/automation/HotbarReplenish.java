package musheor.modules.automation;

import java.util.List;
import java.util.function.Predicate;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import musheor.musheor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class HotbarReplenish extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private static final MinecraftClient client = MinecraftClient.getInstance();
    private final Setting<Integer> threshold = this.sgGeneral
        .add(new Builder().name("threshold").description("The threshold of items left to trigger replenishment.").defaultValue(16).sliderRange(1, 63).build());
    private final Setting<Integer> delay = this.sgGeneral
        .add(new Builder().name("delay").description("Delay between slot movements in ticks").defaultValue(2).build());
    private final Setting<List<Item>> offhandItem = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.ItemListSetting.Builder()
                .name("offhand-item")
                .description("Item to maintain offhand slot..")
                .defaultValue()
                .build()
        );
    private final Setting<List<Item>> slot1 = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.ItemListSetting.Builder()
                .name("slot-1")
                .description("Item to maintain in the first hotbar slot.")
                .defaultValue()
                .build()
        );
    private final Setting<List<Item>> slot2 = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.ItemListSetting.Builder()
                .name("slot-2")
                .description("Item to maintain in the second hotbar slot.")
                .defaultValue()
                .build()
        );
    private final Setting<List<Item>> slot3 = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.ItemListSetting.Builder()
                .name("slot-3")
                .description("Item to maintain in the third hotbar slot.")
                .defaultValue()
                .build()
        );
    private final Setting<List<Item>> slot4 = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.ItemListSetting.Builder()
                .name("slot-4")
                .description("Item to maintain in the fourth hotbar slot.")
                .defaultValue()
                .build()
        );
    private final Setting<List<Item>> slot5 = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.ItemListSetting.Builder()
                .name("slot-5")
                .description("Item to maintain in the fifth hotbar slot.")
                .defaultValue()
                .build()
        );
    private final Setting<List<Item>> slot6 = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.ItemListSetting.Builder()
                .name("slot-6")
                .description("Item to maintain in the sixth hotbar slot.")
                .defaultValue()
                .build()
        );
    private final Setting<List<Item>> slot7 = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.ItemListSetting.Builder()
                .name("slot-7")
                .description("Item to maintain in the seventh hotbar slot.")
                .defaultValue()
                .build()
        );
    private final Setting<List<Item>> slot8 = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.ItemListSetting.Builder()
                .name("slot-8")
                .description("Item to maintain in the eighth hotbar slot.")
                .defaultValue()
                .build()
        );
    private final Setting<List<Item>> slot9 = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.ItemListSetting.Builder()
                .name("slot-9")
                .description("Item to maintain in the ninth hotbar slot.")
                .defaultValue()
                .build()
        );
    private static final int[] MANAGED_SLOTS = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 40};
    private int moveCooldown;

    public HotbarReplenish() {
        super(musheor.AUTOMATION, "hotbar-replenish", "Automatically refills specific items in each hotbar slot. Each slot independently configurable.");
    }

    @Override
    public void onActivate() {
        this.moveCooldown = 0;
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.isActive()) {
            if (client.player != null && client.world != null) {
                if (Modules.get().get(HighwayBuilder.class).isActive() && !this.slot9.get().isEmpty()) {
                    this.slot9.reset();
                    ChatUtils.warning("Warning: Reset last slot in Hotbar-Replenish because HighwayBuilder restocking reserves this for restocking!");
                }

                if (this.moveCooldown > 0) {
                    this.moveCooldown--;
                } else {
                    for (int slot : MANAGED_SLOTS) {
                        if (this.replenishSlot(slot)) {
                            this.moveCooldown = this.delay.get();
                            return;
                        }
                    }
                }
            }
        }
    }

    private boolean replenishSlot(int slot) {
        List<Item> allowed = this.getAllowedItems(slot);
        if (allowed.isEmpty()) {
            return false;
        } else {
            ItemStack current = this.getSlotStack(slot);
            if (!current.isEmpty() && allowed.contains(current.getItem())) {
                if (current.isStackable() && current.getCount() <= this.threshold.get()) {
                    int source = this.findSourceSlot(stack -> ItemStack.areItemsAndComponentsEqual(stack, current), slot);
                    if (source == -1) {
                        return false;
                    } else {
                        InvUtils.move().from(source).to(slot);
                        return true;
                    }
                } else {
                    return false;
                }
            } else {
                int source = this.findSourceSlot(stack -> allowed.contains(stack.getItem()), slot);
                if (source == -1) {
                    return false;
                } else {
                    InvUtils.move().from(source).to(slot);
                    return true;
                }
            }
        }
    }

    private int findSourceSlot(Predicate<ItemStack> wanted, int excludedSlot) {
        assert client.player != null;

        int best = -1;
        int bestCount = Integer.MAX_VALUE;

        for (int i = 0; i <= 35; i++) {
            if (i != excludedSlot && this.getAllowedItems(i).isEmpty()) {
                ItemStack stack = client.player.getInventory().getStack(i);
                if (!stack.isEmpty() && wanted.test(stack) && stack.getCount() < bestCount) {
                    best = i;
                    bestCount = stack.getCount();
                }
            }
        }

        return best;
    }

    private ItemStack getSlotStack(int slot) {
        assert client.player != null;

        return slot == 40 ? client.player.getOffHandStack() : client.player.getInventory().getStack(slot);
    }

    private List<Item> getAllowedItems(int slot) {
        if (slot == 40) {
            return this.offhandItem.get();
        } else {
            return switch (slot) {
                case 0 -> (List)this.slot1.get();
                case 1 -> (List)this.slot2.get();
                case 2 -> (List)this.slot3.get();
                case 3 -> (List)this.slot4.get();
                case 4 -> (List)this.slot5.get();
                case 5 -> (List)this.slot6.get();
                case 6 -> (List)this.slot7.get();
                case 7 -> (List)this.slot8.get();
                case 8 -> (List)this.slot9.get();
                default -> List.of();
            };
        }
    }
}
