package musheor.modules.automation;

import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.ItemSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import musheor.musheor;
import musheor.utils.HighwayUtils;
import musheor.utils.manager.InventoryManager;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class Refill extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final MinecraftClient client = MinecraftClient.getInstance();
    public static Refill INSTANCE;
    public final Setting<Item> item = this.sgGeneral
        .add(new Builder().name("item").description("What block to steal / loot from shulker.").defaultValue(Items.OBSIDIAN).build());
    private final Setting<Boolean> fillAll = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("fill-all")
                .description("When this setting is enabled, the inventory will be completely filled up until there are no empty slots")
                .defaultValue(true)
                .build()
        );
    private final Setting<Integer> maxSlots = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("max-slots")
                .description("How many slots can maximumly be filled up")
                .defaultValue(10)
                .visible(() -> !this.fillAll.get())
                .build()
        );
    private final Setting<Boolean> placeUpsideDownBelow = this.sgGeneral
        .add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder().name("place-upside-down-below").defaultValue(true).build());
    boolean shulkerSelected;
    boolean shulkerPlaced;
    boolean shulkerOpened;
    int currentSlot;
    int filledSlots;
    int openDelay;
    BlockPos placePos;
    Direction placeSide;

    public Refill() {
        super(musheor.AUTOMATION, "refill", "Places a shulkerbox and refills the inventory with items.");
        INSTANCE = this;
    }

    @Override
    public void onActivate() {
        this.shulkerSelected = false;
        this.placePos = null;
        this.placeSide = Direction.DOWN;
        this.shulkerPlaced = false;
        this.shulkerOpened = false;
        this.currentSlot = 0;
        this.filledSlots = 0;
        this.openDelay = 5;
    }

    @EventHandler
    private void onTick(Post event) {
        if (this.client.player != null && this.client.world != null) {
            if (this.isActive()) {
                if (!this.shulkerSelected) {
                    ItemStack lookingFor = InventoryManager.findLeastFilledShulkerWith(this.item.get());
                    if (lookingFor != null) {
                        int slot = this.client.player.getInventory().getSlotWithStack(lookingFor);
                        if (this.client.player.getInventory().selectedSlot == slot) {
                            this.shulkerSelected = true;
                            return;
                        }

                        for (int i = 0; i < 9; i++) {
                            if (this.client.player.getInventory().getStack(i) == lookingFor) {
                                InventoryManager.selectSlot(i);
                                this.shulkerSelected = true;
                                return;
                            }
                        }
                    }

                    int emptySlot = InventoryManager.findFreeHotbarSlot();
                    if (InventoryManager.moveShulkerToSlot(this.item.get(), emptySlot)) {
                        InventoryManager.selectSlot(emptySlot);
                        this.shulkerSelected = true;
                        return;
                    }
                }

                if (this.placePos == null) {
                    if (this.placeUpsideDownBelow.get()) {
                        this.placePos = this.client.player.getBlockPos().withY(this.client.player.getBlockPos().getY() - 2);
                    } else if (this.client.crosshairTarget instanceof BlockHitResult bhr) {
                        BlockPos adjacent = bhr.getBlockPos();
                        this.placeSide = bhr.getSide();
                        this.placePos = adjacent.offset(this.placeSide);
                    }

                    return;
                }

                if (!this.shulkerPlaced) {
                    if (BlockUtils.canPlace(this.placePos, true)) {
                        this.shulkerPlaced = HighwayUtils.placeBlockOffhand(this.placePos, this.placeSide);
                    } else {
                        this.toggle();
                        this.info("Cannot place shulkerbox at desired position", new Object[0]);
                    }

                    return;
                }

                if (this.client.world.getBlockState(this.placePos).getBlock() instanceof ShulkerBoxBlock) {
                    if (!this.shulkerOpened) {
                        if (!(this.client.player.currentScreenHandler instanceof ShulkerBoxScreenHandler)) {
                            this.client
                                .player
                                .networkHandler
                                .sendPacket(
                                    new PlayerInteractBlockC2SPacket(
                                        Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofCenter(this.placePos), Direction.UP, this.placePos, false), 0
                                    )
                                );
                        } else {
                            this.shulkerOpened = true;
                        }
                    } else {
                        ScreenHandler handler = this.client.player.currentScreenHandler;
                        if (this.openDelay > 0) {
                            this.openDelay--;
                            return;
                        }

                        if (this.fillAll.get()) {
                            for (int ix = 0; ix < 27 && InventoryManager.countEmptySlots() > 0; ix++) {
                                if (handler.getSlot(this.currentSlot).getStack().getItem() == this.item.get()) {
                                    InvUtils.shiftClick().slotId(this.currentSlot);
                                }

                                this.currentSlot++;
                            }

                            this.client.player.closeHandledScreen();
                            this.toggle();
                        } else if (this.filledSlots >= this.maxSlots.get() && this.currentSlot >= 27) {
                            this.client.player.closeHandledScreen();
                            this.toggle();
                        } else {
                            if (handler.getSlot(this.currentSlot).getStack().getItem() == this.item.get()) {
                                InvUtils.shiftClick().slotId(this.currentSlot);
                                this.filledSlots++;
                            }

                            this.currentSlot++;
                        }
                    }
                }
            }
        }
    }
}
