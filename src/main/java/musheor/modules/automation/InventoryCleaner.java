package musheor.modules.automation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import musheor.musheor;
import musheor.utils.HighwayUtils;
import musheor.utils.PlayerControlUtils;
import musheor.utils.manager.InventoryManager;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround;

public class InventoryCleaner extends Module {
    private static final MinecraftClient client = MinecraftClient.getInstance();
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<Boolean> dropEmptyShulker = this.sgGeneral
        .add(new Builder().name("drop-empty-shulker").description("Allow the paver to dispose of empty shulkerboxes.").defaultValue(true).build());
    private final Setting<Integer> shulkerGraceTicks = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("shulker-grace-ticks")
                .description(
                    "Ticks a shulker must appear empty before it is dropped. Prevents dropping freshly picked-up shulkers whose contents haven't synced from the server yet."
                )
                .defaultValue(5)
                .sliderRange(2, 20)
                .visible(this.dropEmptyShulker::get)
                .build()
        );
    private final Setting<Boolean> ignoreHotbar = this.sgGeneral
        .add(new Builder().name("ignore-hotbar").description("Ignore items in the player's hotbar.").defaultValue(true).build());
    private final Setting<Boolean> rotateDrop = this.sgGeneral
        .add(new Builder().name("rotate-drop").description("Drop items behind you").defaultValue(false).build());
    private final Setting<InventoryCleaner.ItemFilterList> itemFilter = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.EnumSetting.Builder<InventoryCleaner.ItemFilterList>()
                            .name("item-filter")
                        .description("Whitelist keeps selected items in the inventory, Blacklist throws selected items out.")
                    .defaultValue(InventoryCleaner.ItemFilterList.Blacklist)
                .build()
        );
    private final Setting<List<Item>> dropItemList = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.ItemListSetting.Builder()
                .name("drop-item-list")
                .description("List of items.")
                .defaultValue(Items.NETHERRACK)
                .build()
        );
    private final Setting<Integer> dropDelay = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("drop-delay")
                .description("Delay between dropping items.")
                .defaultValue(2)
                .sliderRange(1, 20)
                .build()
        );
    private int dropTimer = 0;
    private final Map<Integer, Integer> emptyShulkerTicks = new HashMap<>();

    public InventoryCleaner() {
        super(musheor.AUTOMATION, "inv-cleaner", "Automatically dispose of unwanted items.");
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();
        WButton scanInventoryAndSetItems = list.add(theme.button("Blacklist inventory")).widget();
        scanInventoryAndSetItems.action = this::blacklistInventory;
        return list;
    }

    @Override
    public void onDeactivate() {
        this.dropTimer = 0;
        this.emptyShulkerTicks.clear();
    }

    @EventHandler
    private void onTick(Pre event) {
        if (client.player != null && client.world != null) {
            int firstSlot = !this.ignoreHotbar.get() ? 0 : 9;

            for (int i = firstSlot; i < client.player.getInventory().main.size(); i++) {
                ItemStack stack = client.player.getInventory().getStack(i);
                if (stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock && InventoryManager.isShulkerEmpty(stack)) {
                    this.emptyShulkerTicks.merge(i, 1, Integer::sum);
                } else {
                    this.emptyShulkerTicks.remove(i);
                }
            }

            for (int ix = firstSlot; ix < client.player.getInventory().main.size(); ix++) {
                if (this.dropTimer < this.dropDelay.get()) {
                    this.dropTimer++;
                    return;
                }

                ItemStack itemStack = client.player.getInventory().getStack(ix);
                if (!(itemStack.getItem() instanceof BlockItem) || !(((BlockItem)itemStack.getItem()).getBlock() instanceof ShulkerBoxBlock)) {
                    if (itemStack.getItem() != Items.AIR
                        && (this.itemFilter.get() != InventoryCleaner.ItemFilterList.Blacklist || !this.dropItemList.get().contains(itemStack.getItem()))
                        && (this.itemFilter.get() != InventoryCleaner.ItemFilterList.Whitelist || this.dropItemList.get().contains(itemStack.getItem()))) {
                        if (this.rotateDrop.get()) {
                            this.dropBehind(ix);
                            this.dropTimer = 0;
                        } else {
                            InvUtils.drop().slot(ix);
                            this.dropTimer = 0;
                        }
                    }
                } else if (this.dropEmptyShulker.get()
                    && InventoryManager.isShulkerEmpty(itemStack)
                    && this.emptyShulkerTicks.getOrDefault(ix, 0) >= this.shulkerGraceTicks.get()) {
                    this.emptyShulkerTicks.remove(ix);
                    if (this.rotateDrop.get()) {
                        this.dropBehind(ix);
                    } else {
                        InvUtils.drop().slot(ix);
                    }

                    this.dropTimer = 0;
                }
            }
        }
    }

    private void blacklistInventory() {
        if (client.player != null && client.world != null) {
            this.dropItemList.get().clear();
            this.itemFilter.set(InventoryCleaner.ItemFilterList.Blacklist);

            for (int i = 0; i <= client.player.getInventory().size(); i++) {
                Item currentItem = client.player.getInventory().getStack(i).getItem();
                if (!this.dropItemList.get().contains(currentItem)) {
                    this.dropItemList.get().add(currentItem);
                }
            }

            ChatUtils.info("Inventory scanned and blacklisted!");
        }
    }

    private void dropBehind(int i) {
        assert client.player != null;

        float previousYaw = client.player.getYaw();
        PlayerControlUtils.setAutoWalk(false);
        client.player
            .networkHandler
            .sendPacket(new LookAndOnGround(HighwayUtils.HighwayDirection.getOppositeYaw(client.player.getYaw()), -20.0F, client.player.isOnGround(), false));
        InvUtils.drop().slot(i);
        client.player.setYaw(previousYaw);
    }

    public static enum ItemFilterList {
        Blacklist,
        Whitelist;
    }
}
