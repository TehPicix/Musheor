package musheor.utils.manager;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import java.util.Objects;
import meteordevelopment.meteorclient.mixin.ContainerComponentAccessor;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.AutoEat;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import musheor.musheor;
import musheor.compat.VersionHelper;
import musheor.modules.automation.ElytraTweakz;
import musheor.modules.automation.HighwayBuilder;
import musheor.modules.automation.KekNuker;
import musheor.modules.features.KekMine;
import musheor.utils.HighwayUtils;
import musheor.utils.PlayerControlUtils;
import musheor.utils.RestockType;
import musheor.utils.highway.EchestSlotBlacklist;
import musheor.utils.highway.HighwayState;
import musheor.utils.placement.BlockPlacer;
import musheor.utils.system.MusheorSystem;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.DropperBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class InventoryManager extends Module {
    private final SettingGroup sgItems = this.settings.createGroup("Items");
    private final SettingGroup sgShulkers = this.settings.createGroup("Shulkers");
    private final SettingGroup sgDelays = this.settings.createGroup("Delays");
    private static final MinecraftClient client = MinecraftClient.getInstance();
    private static int delayTicks;
    private static int postOpenDelayTicks;
    private static int openDelayTicks;
    private static int postRestockDelayTicks;
    private static int swapDelayTicks;
    public static BlockPos restockPos;
    private static BlockPos containerPos;
    private static int placeDelayTicks;
    private static boolean restockInitialized;
    private static int containerSlotIndex = 0;
    public static boolean restocking;
    public static boolean inventoryBusy;
    private static boolean breakingContainer;
    private static boolean containerPlaced;
    private static boolean containerItemReady;
    public static boolean postRestocking;
    private static int takenCount = 0;
    private static int stealDelayCounter = 0;
    private static boolean waitingStealDelay;
    public static int restockAmount;
    static int toolRestockCount;
    static int foodRestockCount;
    static int materialShulkerCount;
    static int toolShulkerCount;
    public static InventoryManager INSTANCE;
    public final Setting<Integer> toolRestockAmount = this.sgItems
        .add(
            new Builder()
                .name("tool-restock-amount")
                .description("How many pickaxes should be taken on restock when the player runs out.")
                .defaultValue(1)
                .sliderRange(1, 20)
                .build()
        );
    public final Setting<Integer> foodRestockAmount = this.sgItems
        .add(
            new Builder()
                .name("food-restock-amount")
                .description("How many stacks of food should be taken on restock when the player runs out.")
                .defaultValue(1)
                .sliderRange(1, 10)
                .build()
        );
    public final Setting<Integer> materialShulkerRestockAmount = this.sgShulkers
        .add(
            new Builder()
                .name("material-shulker-restock-amount")
                .description("How many shulkers of pavement materials should be taken when performing an enderchest restock.")
                .defaultValue(1)
                .sliderRange(1, 20)
                .build()
        );
    public final Setting<Integer> toolShulkerRestockAmount = this.sgShulkers
        .add(
            new Builder()
                .name("tool-shulker-restock-amount")
                .description("How many shulkers of pickaxes should be taken when performing an enderchest restock.")
                .defaultValue(1)
                .sliderRange(1, 10)
                .build()
        );
    public final Setting<Integer> stealingDelay = this.sgDelays
        .add(
            new Builder()
                .name("stealing-delay")
                .description("Ticks to wait between stealing slots from a container. 0 steals every matching slot in a single tick.")
                .defaultValue(1)
                .min(0)
                .sliderRange(0, 20)
                .build()
        );
    public final Setting<Integer> delayAfterPlacingContainer = this.sgDelays
        .add(
            new Builder()
                .name("delay-after-placing-container")
                .description("Time the player should wait before placing a container in ticks")
                .defaultValue(5)
                .sliderRange(1, 20)
                .build()
        );
    public final Setting<Integer> delayBeforeOpeningContainer = this.sgDelays
        .add(
            new Builder()
                .name("delay-before-opening-container")
                .description("Time the player should wait before opening a container in ticks")
                .defaultValue(5)
                .sliderRange(1, 20)
                .build()
        );
    public final Setting<Integer> delayAfterOpeningContainer = this.sgDelays
        .add(
            new Builder()
                .name("delay-after-opening-container")
                .description("Time the player should wait after opening a container in ticks")
                .defaultValue(5)
                .sliderRange(1, 20)
                .build()
        );
    public final Setting<Integer> delayAfterSwappingItem = this.sgDelays
        .add(
            new Builder()
                .name("delay-after-swapping-item")
                .description("Time the player should wait before it attempts to swap items")
                .defaultValue(5)
                .sliderRange(1, 20)
                .build()
        );
    public final Setting<Integer> delayAfterRestocking = this.sgDelays
        .add(
            new Builder()
                .name("delay-after-restocking")
                .description("Time the player should wait completing the restocking process")
                .defaultValue(10)
                .sliderRange(1, 20)
                .build()
        );

    public InventoryManager() {
        super(musheor.AUTOMATION, "inventory-manager", "Manager module for inventory related stuff");
        INSTANCE = this;
    }

    public static void cacheSettings() {
        placeDelayTicks = INSTANCE.delayAfterPlacingContainer.get();
        openDelayTicks = INSTANCE.delayBeforeOpeningContainer.get();
        postOpenDelayTicks = INSTANCE.delayAfterOpeningContainer.get();
        swapDelayTicks = INSTANCE.delayAfterSwappingItem.get();
        postRestockDelayTicks = INSTANCE.delayAfterRestocking.get();
        materialShulkerCount = INSTANCE.materialShulkerRestockAmount.get();
        toolShulkerCount = INSTANCE.toolShulkerRestockAmount.get();
        toolRestockCount = INSTANCE.toolRestockAmount.get();
        foodRestockCount = INSTANCE.foodRestockAmount.get();
    }

    public static void resetRestock(boolean handlePostRestocking) {
        HighwayState state = HighwayState.getInstance();
        cacheSettings();
        postRestocking = false;
        state.setShulkerRestocking(false);
        state.setEchestRestocking(false);
        containerItemReady = false;
        containerPlaced = false;
        takenCount = 0;
        containerSlotIndex = 0;
        restockInitialized = false;
        stealDelayCounter = 0;
        restockPos = null;
        restocking = false;
        state.setRestocking(false);
        state.setRestockType(null);
        restockAmount = 0;
        state.setPostRestocking(false);
        delayTicks = 0;
        if (handlePostRestocking) {
            postRestocking = true;
            state.setPostRestocking(true);
        }
    }

    public static void evaluateRestock() {
        HighwayState state = HighwayState.getInstance();
        if (HighwayBuilder.INSTANCE.mode.get() != HighwayBuilder.Mode.Manual) {
            if (HighwayBuilder.getBuildMode() == HighwayBuilder.BuildMode.Pave) {
                if (HighwayBuilder.isShulkerRestockingEnabled()
                    && HighwayBuilder.isToolShulkerRestockingEnabled()
                    && findShulkerWith(DataComponentTypes.TOOL) == null
                    && countPickaxes(false) == 0) {
                    restocking = true;
                    state.setRestockType(RestockType.PICKAXE_NON_SILK);
                    restockAmount = toolShulkerCount;
                    state.setEchestRestocking(true);
                    MusheorSystem.debug("Tool shulker restocking process started");
                    return;
                }

                if (HighwayBuilder.isShulkerRestockingEnabled()
                    && HighwayBuilder.isMaterialShulkerRestockingEnabled()
                    && findShulkerWith(Items.ENDER_CHEST) == null
                    && findShulkerWith(HighwayBuilder.getPavementBlock().asItem()) == null) {
                    restocking = true;
                    state.setRestockType(RestockType.PAVEMENT);
                    restockAmount = materialShulkerCount;
                    state.setEchestRestocking(true);
                    MusheorSystem.debug("Echest shulker restocking process started");
                    return;
                }

                if (HighwayBuilder.isItemRestockingEnabled()
                    && countItem(HighwayBuilder.getPavementBlock().asItem()) <= 8
                    && countItem(Items.ENDER_CHEST) <= 8
                    && (findShulkerWith(Items.ENDER_CHEST) != null || findShulkerWith(HighwayBuilder.getPavementBlock().asItem()) != null)) {
                    restocking = true;
                    state.setRestockType(RestockType.PAVEMENT);
                    restockAmount = countEmptySlots() / 2;
                    state.setShulkerRestocking(true);
                    MusheorSystem.debug("Material restocking process started");
                    return;
                }

                if (HighwayBuilder.isItemRestockingEnabled() && findShulkerWith(DataComponentTypes.TOOL) != null && countPickaxes(false) < 1) {
                    restocking = true;
                    state.setRestockType(RestockType.PICKAXE_NON_SILK);
                    restockAmount = toolRestockCount;
                    state.setShulkerRestocking(true);
                    MusheorSystem.debug("Pickaxe restocking process started");
                    return;
                }

                if (HighwayBuilder.isItemRestockingEnabled() && !HighwayBuilder.hasFood() && findShulkerWith(DataComponentTypes.FOOD) != null) {
                    restocking = true;
                    state.setRestockType(RestockType.FOOD);
                    restockAmount = foodRestockCount;
                    state.setShulkerRestocking(true);
                    MusheorSystem.debug("Food restocking process started");
                    return;
                }
            }

            if (HighwayBuilder.getBuildMode() == HighwayBuilder.BuildMode.Tunnel) {
                if (HighwayBuilder.isShulkerRestockingEnabled()
                    && HighwayBuilder.isToolShulkerRestockingEnabled()
                    && findShulkerWith(DataComponentTypes.TOOL) == null
                    && countPickaxes(true) == 0) {
                    restocking = true;
                    state.setRestockType(RestockType.PICKAXE_SILK);
                    restockAmount = toolShulkerCount;
                    state.setEchestRestocking(true);
                    MusheorSystem.debug("Tool shulker restocking process started");
                    return;
                }

                if (HighwayBuilder.isItemRestockingEnabled() && findShulkerWith(DataComponentTypes.TOOL) != null && countPickaxes(true) == 0) {
                    restocking = true;
                    state.setRestockType(RestockType.PICKAXE_SILK);
                    restockAmount = toolShulkerCount;
                    state.setShulkerRestocking(true);
                    MusheorSystem.debug("Tool restocking process started");
                    return;
                }

                if (HighwayBuilder.isItemRestockingEnabled() && !HighwayBuilder.hasFood()) {
                    restocking = true;
                    state.setRestockType(RestockType.FOOD);
                    restockAmount = foodRestockCount;
                    state.setShulkerRestocking(true);
                    MusheorSystem.debug("Food restocking process started");
                    return;
                }
            }
        }
    }

    private static boolean isContainerBlock(BlockPos pos) {
        Block block = client.world.getBlockState(pos).getBlock();
        return block instanceof ChestBlock
            || block instanceof ShulkerBoxBlock
            || block instanceof BarrelBlock
            || block instanceof HopperBlock
            || block instanceof DispenserBlock
            || block instanceof DropperBlock;
    }

    public static boolean isContainerScreenOpen() {
        if (client.player == null) {
            return false;
        } else {
            ScreenHandler handler = client.player.currentScreenHandler;
            return handler instanceof GenericContainerScreenHandler || handler instanceof ShulkerBoxScreenHandler;
        }
    }

    public static boolean openContainer(BlockPos pos) {
        if (client.player == null || client.world == null) {
            return false;
        } else if (!isContainerBlock(pos)) {
            return false;
        } else if (isContainerScreenOpen()) {
            return true;
        } else {
            Vec3d hitVec = Vec3d.ofCenter(pos);
            HighwayUtils.rotateToPlace(pos);
            BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.UP, pos, false);
            interactBlock(hitResult);
            return false;
        }
    }

    public static void interactBlock(BlockHitResult bhr) {
        if (client.player != null && client.world != null && client.interactionManager != null) {
            client.interactionManager.sendSequencedPacket(client.world, sequence -> new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, bhr, sequence));
        }
    }

    public static boolean isDurable(ItemStack stack) {
        return stack.getMaxDamage() - stack.getDamage() > KekMine.INSTANCE.minToolDurability.get();
    }

    public static void moveToHotbar(Item item) {
        int slot = 0;

        for (int i = 9; i < client.player.getInventory().main.size(); i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (stack.getItem() == item) {
                slot = i;
                break;
            }
        }

        if (slot != 0) {
            InvUtils.move().from(slot).to(findFreeHotbarSlot());
        }
    }

    public static int moveStackToHotbar(ItemStack stack) {
        int dest = findFreeHotbarSlot();
        InvUtils.move().from(client.player.getInventory().getSlotWithStack(stack)).to(dest);
        return dest;
    }

    public static void selectSlot(int slot) {
        if (client.player != null && client.getNetworkHandler() != null) {
            slot = Math.max(0, Math.min(8, slot));
            if (client.player.getInventory().selectedSlot != slot) {
                client.player.getInventory().selectedSlot = slot;
                client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            }
        }
    }

    public static int findFreeHotbarSlot() {
        if (client.player == null) {
            return 8;
        } else {
            for (int i = 0; i <= 8; i++) {
                ItemStack current = client.player.getInventory().getStack(i);
                if (current.isEmpty()) {
                    return i;
                }
            }

            return Utils.random(1, 7);
        }
    }

    public static int findFreeInventorySlot() {
        if (client.player == null) {
            return 35;
        } else {
            for (int i = 9; i < 36; i++) {
                ItemStack current = client.player.getInventory().getStack(i);
                if (current.isEmpty()) {
                    return i;
                }
            }

            return Utils.random(9, 35);
        }
    }

    public static boolean useSlot(int slot, Runnable action) {
        if (client.player == null || action == null) {
            return false;
        } else if (slot >= 0 && slot <= 35) {
            boolean swapped = swapToSelected(slot);

            boolean var3;
            try {
                action.run();
                var3 = true;
            } finally {
                if (swapped) {
                    swapToSelected(slot);
                }
            }

            return var3;
        } else {
            return false;
        }
    }

    public static boolean swapToSelected(int slot) {
        int currentSlot = client.player.getInventory().selectedSlot;
        if (slot == currentSlot) {
            return false;
        } else {
            int screenSlot = toScreenSlot(slot);
            client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, screenSlot, currentSlot, SlotActionType.SWAP, client.player);
            MusheorSystem.debug(
                "Swapped slot %d to selected in screen %s with ID: %d",
                slot,
                client.player.currentScreenHandler.toString(),
                client.player.currentScreenHandler.syncId
            );
            return true;
        }
    }

    public static int toScreenSlot(int slot) {
        return slot >= 0 && slot <= 8 ? 36 + slot : slot;
    }

    public static int findInHotbar(Item item) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (stack.getItem() == item) {
                return i;
            }
        }

        return -1;
    }

    public static int findInInventory(Item item) {
        for (int i = 0; i < Objects.requireNonNull(client.player).getInventory().main.size(); i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (stack.getItem() == item) {
                return i;
            }
        }

        return -1;
    }

    public static int findUsableElytra() {
        for (int i = 0; i < Objects.requireNonNull(client.player).getInventory().main.size(); i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (stack.getItem() == Items.ELYTRA && stack.getMaxDamage() - stack.getDamage() > ElytraTweakz.INSTANCE.durabilityThreshold.get()) {
                return i;
            }
        }

        return -1;
    }

    public static int findChestplate() {
        if (client.player == null) {
            return -1;
        } else {
            PlayerInventory inventory = client.player.getInventory();

            for (int i = 0; i < inventory.main.size(); i++) {
                ItemStack stack = inventory.getStack(i);
                EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
                if (equippable != null && equippable.slot() == EquipmentSlot.CHEST && !stack.isOf(Items.ELYTRA)) {
                    return i;
                }
            }

            return -1;
        }
    }

    public static void dropSlot(int slot, boolean entireStack) {
        if (client.player != null && client.getNetworkHandler() != null) {
            client.interactionManager
                .clickSlot(client.player.currentScreenHandler.syncId, toScreenSlot(slot), entireStack ? 1 : 0, SlotActionType.THROW, client.player);
        }
    }

    public static void swapChestArmor() {
        ItemStack currentItem = client.player.getEquippedStack(EquipmentSlot.CHEST);
        if (currentItem.contains(DataComponentTypes.GLIDER)) {
            int chest = findChestplate();
            InvUtils.move().from(chest).toArmor(2);
        } else if (currentItem.contains(DataComponentTypes.EQUIPPABLE)
            && currentItem.get(DataComponentTypes.EQUIPPABLE).slot().getEntitySlotId() == EquipmentSlot.CHEST.getEntitySlotId()) {
            int elytra = findUsableElytra();
            InvUtils.move().from(elytra).toArmor(2);
        } else {
            int elytra = findUsableElytra();
            InvUtils.move().from(elytra).toArmor(2);
        }
    }

    public static ItemStack findStack(Item item) {
        for (int i = 0; i < Objects.requireNonNull(client.player).getInventory().main.size(); i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (stack.getItem() == item) {
                return stack;
            }
        }

        return null;
    }

    public static int countInShulker(ItemStack shulker, Item item) {
        ItemStack[] containerItems = new ItemStack[27];
        Utils.getItemsInContainerItem(shulker, containerItems);
        int count = 0;

        for (ItemStack stack : containerItems) {
            if (!stack.isEmpty() && stack.getItem() == item) {
                count += stack.getCount();
            }
        }

        return count;
    }

    public static int countInShulker(ItemStack shulker, ComponentType<?> component) {
        ItemStack[] containerItems = new ItemStack[27];
        Utils.getItemsInContainerItem(shulker, containerItems);
        int count = 0;

        for (ItemStack stack : containerItems) {
            if (!stack.isEmpty() && stack.getComponents().contains(component)) {
                count += stack.getCount();
            }
        }

        return count;
    }

    public static int countItem(Item item) {
        int count = 0;

        for (int i = 0; i < client.player.getInventory().main.size(); i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                count += stack.getCount();
            }
        }

        return count;
    }

    public static int countItemIncludingShulkers(Item item) {
        int count = 0;

        for (int i = 0; i < client.player.getInventory().main.size(); i++) {
            ItemStack itemStack = client.player.getInventory().getStack(i);
            if (!itemStack.isEmpty()) {
                if (itemStack.getItem() instanceof BlockItem && ((BlockItem)itemStack.getItem()).getBlock() instanceof ShulkerBoxBlock) {
                    count += countInShulker(itemStack, item);
                }

                if (itemStack.getItem() == item) {
                    count += itemStack.getCount();
                }
            }
        }

        return count;
    }

    public static ItemStack findShulkerWith(Item item) {
        for (int i = 0; i < Objects.requireNonNull(client.player).getInventory().main.size(); i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (stack.getItem() instanceof BlockItem && ((BlockItem)stack.getItem()).getBlock() instanceof ShulkerBoxBlock && shulkerContains(stack, item)) {
                return stack;
            }
        }

        return null;
    }

    public static ItemStack findLeastFilledShulkerWith(Item item) {
        int count = 0;
        int least = 1729;
        ItemStack stack = null;

        for (int i = 0; i < Objects.requireNonNull(client.player).getInventory().size(); i++) {
            ItemStack itemStack = client.player.getInventory().getStack(i);
            if (itemStack.getItem() instanceof BlockItem && ((BlockItem)itemStack.getItem()).getBlock() instanceof ShulkerBoxBlock) {
                count = countInShulker(itemStack, item);
                if (least > count && count > 0) {
                    least = count;
                    stack = itemStack;
                }
            }
        }

        return stack;
    }

    public static ItemStack findLeastFilledShulkerWith(ComponentType<?> component) {
        int count = 0;
        int least = 99;
        ItemStack stack = null;

        for (int i = 0; i < Objects.requireNonNull(client.player).getInventory().size(); i++) {
            ItemStack itemStack = client.player.getInventory().getStack(i);
            if (itemStack.getItem() instanceof BlockItem && ((BlockItem)itemStack.getItem()).getBlock() instanceof ShulkerBoxBlock) {
                count = countInShulker(itemStack, component);
                if (least > count && count > 0) {
                    least = count;
                    stack = itemStack;
                }
            }
        }

        return stack;
    }

    public static ItemStack findShulkerWith(ComponentType<?> component) {
        for (int i = 0; i < Objects.requireNonNull(client.player).getInventory().main.size(); i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (stack.getItem() instanceof BlockItem && ((BlockItem)stack.getItem()).getBlock() instanceof ShulkerBoxBlock && shulkerContains(stack, component)
                )
             {
                return stack;
            }
        }

        return null;
    }

    public static boolean shulkerContains(ItemStack shulker, ComponentType<?> component) {
        ItemStack[] containerItems = new ItemStack[27];
        Utils.getItemsInContainerItem(shulker, containerItems);

        for (ItemStack stack : containerItems) {
            if (!stack.isEmpty() && stack.getComponents().contains(component)) {
                if (component != DataComponentTypes.TOOL) {
                    return true;
                }

                if (stack.getMaxDamage() - stack.getDamage() > 50) {
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean shulkerContains(ItemStack shulker, Item targetItem) {
        ItemStack[] containerItems = new ItemStack[27];
        Utils.getItemsInContainerItem(shulker, containerItems);

        for (ItemStack stack : containerItems) {
            if (!stack.isEmpty() && stack.getItem() == targetItem) {
                if (!VersionHelper.get().isPickaxe(stack)) {
                    return true;
                }

                if (stack.getMaxDamage() - stack.getDamage() > 50) {
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean isShulkerEmpty(ItemStack shulker) {
        ComponentMap components = shulker.getComponents();
        if (!components.contains(DataComponentTypes.CONTAINER)) {
            return false;
        } else {
            ContainerComponentAccessor container = (ContainerComponentAccessor)(Object)components.get(DataComponentTypes.CONTAINER);
            if (container != null) {
                for (ItemStack stack : VersionHelper.get().getStacks(container)) {
                    if (!stack.isEmpty()) {
                        return false;
                    }
                }
            }

            return true;
        }
    }

    public static void swapToItem(Item item) {
        swapToItem(item, false);
    }

    public static void swapToItem(Item item, boolean silent) {
        assert client.player != null;

        FindItemResult result = InvUtils.findInHotbar(itemStack -> itemStack.getItem() == item);
        if (result.found()) {
            InvUtils.swap(result.slot(), silent);
        } else {
            moveToHotbar(item);
        }

        MusheorSystem.debug("Swapping to %s", Registries.ITEM.getId(item.asItem()).toString());
    }

    public static boolean selectItem(Item item) {
        assert client.player != null;

        if (client.player.getMainHandStack().getItem() == item) {
            return true;
        } else {
            boolean silent = MusheorSystem.Manager.silentPlace.get();
            swapToItem(item, silent);
            return silent && client.player.getMainHandStack().getItem() == item;
        }
    }

    public static void selectEchestTool(boolean silkTouch) {
        assert client.player != null;

        double bestScore = -1.0;
        int bestSlot = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack itemStack = client.player.getInventory().getStack(i);
            if (!Utils.hasEnchantments(itemStack, Enchantments.SILK_TOUCH) || !silkTouch) {
                double score = itemStack.getMiningSpeedMultiplier(Blocks.ENDER_CHEST.getDefaultState());
                if (score > bestScore) {
                    bestScore = score;
                    bestSlot = i;
                }
            }
        }

        if (bestSlot != -1) {
            InvUtils.swap(bestSlot, false);
        }
    }

    public static ItemStack findBestTool(BlockState state) {
        if (client.player == null) {
            return ItemStack.EMPTY;
        } else {
            ItemStack bestStack = client.player.getMainHandStack();
            float bestSpeed = 1.0F;

            for (int i = 0; i < client.player.getInventory().size(); i++) {
                ItemStack stack = client.player.getInventory().getStack(i);
                if (!KekMine.INSTANCE.preventToolBreaking.get() || isDurable(stack)) {
                    float speed = stack.getMiningSpeedMultiplier(state);
                    if (speed > bestSpeed) {
                        bestSpeed = speed;
                        bestStack = stack;
                    }
                }
            }

            return bestStack;
        }
    }

    public static void selectBestTool(BlockPos blockPos) {
        assert client.world != null && client.player != null && blockPos != null;

        BlockState state = client.world.getBlockState(blockPos);
        float bestSpeed = 1.0F;
        int bestSlot = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack itemStack = client.player.getInventory().getStack(i);
            if (!itemStack.isEmpty() && (!KekMine.INSTANCE.preventToolBreaking.get() || isDurable(itemStack))) {
                float speed = itemStack.getMiningSpeedMultiplier(state);
                if (speed > bestSpeed && itemStack.isSuitableFor(state)) {
                    bestSpeed = speed;
                    bestSlot = i;
                }
            }
        }

        if (bestSlot != -1) {
            client.player.getInventory().selectedSlot = bestSlot;
        } else if (VersionHelper.get().isTool(client.player.getMainHandStack()) && !isDurable(client.player.getMainHandStack())) {
            InvUtils.move().from(client.player.getInventory().selectedSlot).to(findFreeInventorySlot());
        } else {
            for (int ix = 9; ix < client.player.getInventory().main.size(); ix++) {
                ItemStack itemStack = client.player.getInventory().getStack(ix);
                if (!itemStack.isEmpty() && (!KekMine.INSTANCE.preventToolBreaking.get() || isDurable(itemStack))) {
                    float speed = itemStack.getMiningSpeedMultiplier(state);
                    if (speed > bestSpeed && itemStack.isSuitableFor(state)) {
                        bestSpeed = speed;
                        bestSlot = ix;
                    }
                }
            }

            if (bestSlot != -1) {
                int hotbarSlot = moveStackToHotbar(client.player.getInventory().getStack(bestSlot));
                client.player.getInventory().selectedSlot = Math.max(0, Math.min(8, hotbarSlot));
            }
        }
    }

    public static int countPickaxes(boolean allowSilkTouch) {
        assert client.player != null;

        int count = 0;

        for (int i = 0; i < client.player.getInventory().size(); i++) {
            ItemStack itemStack = client.player.getInventory().getStack(i);
            if (VersionHelper.get().isPickaxe(itemStack)) {
                if (KekMine.INSTANCE.preventToolBreaking.get()) {
                    if (!allowSilkTouch
                        && !Utils.hasEnchantments(itemStack, Enchantments.SILK_TOUCH)
                        && itemStack.getMaxDamage() - itemStack.getDamage() > KekMine.INSTANCE.minToolDurability.get()) {
                        count++;
                    }

                    if (allowSilkTouch && itemStack.getMaxDamage() - itemStack.getDamage() > KekMine.INSTANCE.minToolDurability.get()) {
                        count++;
                    }
                } else if (!allowSilkTouch && !Utils.hasEnchantments(itemStack, Enchantments.SILK_TOUCH)) {
                    count++;
                } else if (allowSilkTouch) {
                    count++;
                }
            }
        }

        return count;
    }

    public static int countWornPickaxes() {
        assert client.player != null;

        int count = 0;

        for (int i = 0; i < client.player.getInventory().main.size(); i++) {
            ItemStack itemStack = client.player.getInventory().getStack(i);
            if (VersionHelper.get().isPickaxe(itemStack) && itemStack.getMaxDamage() - itemStack.getDamage() <= KekMine.INSTANCE.minToolDurability.get()) {
                count++;
            }
        }

        return count;
    }

    public static int findWornPickaxeScreenSlot() {
        assert client.player != null;

        for (int i = 0; i < client.player.currentScreenHandler.slots.size(); i++) {
            ItemStack itemStack = client.player.currentScreenHandler.getSlot(i).getStack();
            if (VersionHelper.get().isPickaxe(itemStack)
                && itemStack.getMaxDamage() - itemStack.getDamage() <= KekMine.INSTANCE.minToolDurability.get()
                && i > 26) {
                return i;
            }
        }

        return -1;
    }

    public static int countEmptySlots() {
        int emptyCount = 0;

        assert client.player != null;

        for (ItemStack itemStack : client.player.getInventory().main) {
            if (itemStack.isEmpty()) {
                emptyCount++;
            }
        }

        return emptyCount;
    }

    public static int countEmptyContainerSlots(ScreenHandler handler) {
        int emptyCount = 0;
        int playerInvOffset = SlotUtils.indexToId(9);

        for (int i = 0; i < playerInvOffset; i++) {
            if (handler.getSlot(i).getStack().isEmpty()) {
                emptyCount++;
            }
        }

        return emptyCount;
    }

    public static boolean moveShulkerToSlot(Item itemInShulker, int hotbarSlot) {
        ItemStack stack = findLeastFilledShulkerWith(itemInShulker);
        if (stack == null) {
            MusheorSystem.debug("No shulker with " + itemInShulker.toString() + " found in the inventory");
            return false;
        } else {
            int slot = client.player.getInventory().getSlotWithStack(stack);
            if (slot == hotbarSlot) {
                return true;
            } else if (slot == -1) {
                return false;
            } else {
                InvUtils.move().from(slot).to(hotbarSlot);
                MusheorSystem.debug("Moving shulker with " + itemInShulker.toString() + " to slot " + hotbarSlot);
                return isShulkerBox(client.player.getInventory().getStack(hotbarSlot));
            }
        }
    }

    public static boolean moveShulkerToSlot(ComponentType<?> type, int hotbarSlot) {
        ItemStack stack = findLeastFilledShulkerWith(type);
        if (stack == null) {
            MusheorSystem.debug("No shulker with " + type.toString() + " found in the inventory");
            return false;
        } else {
            int slot = client.player.getInventory().getSlotWithStack(stack);
            if (slot == hotbarSlot) {
                return true;
            } else if (slot == -1) {
                return false;
            } else {
                InvUtils.move().from(slot).to(hotbarSlot);
                MusheorSystem.debug("Moving shulker with " + type.toString() + " to slot " + hotbarSlot);
                return isShulkerBox(client.player.getInventory().getStack(hotbarSlot));
            }
        }
    }

    public static Item findNearbyShulkerDrop(double radius) {
        if (client.world != null && client.player != null) {
            Box area = client.player.getBoundingBox().expand(radius);

            for (ItemEntity itemEntity : client.world.getEntitiesByClass(ItemEntity.class, area, e -> true)) {
                ItemStack stack = itemEntity.getStack();
                if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock) {
                    return itemEntity.getStack().getItem();
                }
            }

            return null;
        } else {
            return null;
        }
    }

    public static void restockFromShulker(RestockType type, int stacks) {
        if (client.world != null && client.player != null && type != null) {
            HighwayState state = HighwayState.getInstance();
            if (!PlayerControlUtils.isGatheringItem() && !state.isEchestFarming()) {
                PlayerControlUtils.setAutoWalk(false);
                Module nuker = Modules.get().get(KekNuker.class);
                if (nuker.isActive()) {
                    nuker.toggle();
                }

                if (delayTicks > 0) {
                    delayTicks--;
                } else {
                    if (!restockInitialized) {
                        selectSlot(8);
                        VersionHelper.get().syncInventory();
                        restockInitialized = true;
                        MusheorSystem.debug("Restocking has been initialized, proceeding...");
                    }

                    if (client.player.getBlockPos().getSquaredDistance(restockPos) > 1.5) {
                        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(restockPos));
                        MusheorSystem.debug("Player is not at restocking position, calling baritone...");
                    } else if (!containerItemReady) {
                        boolean moved = false;
                        switch (type) {
                            case PICKAXE_SILK:
                            case PICKAXE_NON_SILK:
                                moved = moveShulkerToSlot(DataComponentTypes.TOOL, 8);
                                break;
                            case FOOD:
                                moved = moveShulkerToSlot(DataComponentTypes.FOOD, 8);
                                break;
                            case PAVEMENT:
                                Item item = null;
                                if (findShulkerWith(HighwayBuilder.getPavementBlock().asItem()) != null) {
                                    item = HighwayBuilder.getPavementBlock().asItem();
                                } else if (HighwayBuilder.getPavementBlock() == Blocks.OBSIDIAN && findShulkerWith(Items.ENDER_CHEST) != null) {
                                    item = Items.ENDER_CHEST;
                                }

                                if (item != null) {
                                    moved = moveShulkerToSlot(item, 8);
                                }
                        }

                        if (moved) {
                            containerItemReady = true;
                        }

                        delayTicks = swapDelayTicks;
                    } else {
                        if (!containerPlaced) {
                            containerPos = HighwayUtils.getPosBehind(HighwayBuilder.getDirection());
                            if (placeDelayTicks > 0) {
                                PlayerControlUtils.setKeyPressed(client.options.sneakKey, false);
                                placeDelayTicks--;
                                HighwayUtils.rotateToFace(containerPos, Direction.DOWN);
                                return;
                            }

                            if (client.world.getBlockState(containerPos).getBlock() instanceof ShulkerBoxBlock) {
                                MusheorSystem.debug("Shulker box placed successfully!");
                                containerPlaced = true;
                                delayTicks = placeDelayTicks;
                            } else {
                                if (BlockUtils.canPlace(containerPos, true) || client.world.getBlockState(containerPos).isReplaceable()) {
                                    if (!isShulkerBox(client.player.getInventory().getStack(8))) {
                                        MusheorSystem.debug("Slot 8 is not holding a shulker, redoing the swap");
                                        containerItemReady = false;
                                        delayTicks = swapDelayTicks;
                                        return;
                                    } else {
                                        MusheorSystem.debug(
                                            "Placing shulkerbox at x: %s y: %s z: %s", containerPos.getX(), containerPos.getY(), containerPos.getZ()
                                        );
                                        selectSlot(8);
                                        BlockPlacer.place(containerPos, Direction.UP, state.getPlacementType(), MusheorSystem.Manager.silentPlace.get());
                                        return;
                                    }
                                }

                                if (KekMine.INSTANCE.isActive()) {
                                    KekMine.INSTANCE.startMining(containerPos, client.world.getBlockState(containerPos), false);
                                    MusheorSystem.debug("Breaking obstructing block at restocking position");
                                    return;
                                }

                                BlockUtils.breakBlock(containerPos, true);
                                MusheorSystem.debug("Breaking obstructing block at restocking position");
                            }
                        }

                        if (!(client.player.currentScreenHandler instanceof ShulkerBoxScreenHandler)) {
                            HighwayUtils.rotateToPlace(containerPos);
                            client.interactionManager
                                .sendSequencedPacket(
                                    client.world,
                                    sequence -> new PlayerInteractBlockC2SPacket(
                                        Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofCenter(containerPos), Direction.UP, containerPos, false), sequence
                                    )
                                );
                            MusheorSystem.debug("Opening shulkerbox at x: %s y: %s z: %s", containerPos.getX(), containerPos.getY(), containerPos.getZ());
                            delayTicks = openDelayTicks;
                        } else if (postOpenDelayTicks > 0) {
                            postOpenDelayTicks--;
                        } else {
                            ScreenHandler handler = client.player.currentScreenHandler;
                            int containerSlots = getContainerSlotCount(handler);
                            if (takenCount < stacks && containerSlotIndex < containerSlots) {
                                int stealDelay = INSTANCE.stealingDelay.get();
                                if (stealDelay <= 0) {
                                    for (; containerSlotIndex < containerSlots && takenCount < stacks; containerSlotIndex++) {
                                        if (takeFromSlot(type, handler, containerSlotIndex)) {
                                            takenCount++;
                                        }
                                    }
                                } else {
                                    if (waitingStealDelay) {
                                        if (stealDelayCounter < stealDelay) {
                                            stealDelayCounter++;
                                            return;
                                        }

                                        waitingStealDelay = false;
                                    }

                                    if (takeFromSlot(type, handler, containerSlotIndex)) {
                                        takenCount++;
                                        waitingStealDelay = true;
                                        stealDelayCounter = 0;
                                    }

                                    containerSlotIndex++;
                                }
                            } else {
                                MusheorSystem.debug("Completed item restocking process");
                                resetRestock(true);
                            }
                        }
                    }
                }
            } else {
                restocking = false;
            }
        }
    }

    private static int getContainerSlotCount(ScreenHandler handler) {
        return Math.max(0, handler.slots.size() - client.player.getInventory().main.size());
    }

    private static boolean takeFromSlot(RestockType type, ScreenHandler handler, int slot) {
        ItemStack stack = handler.getSlot(slot).getStack();
        if (!matchesRestockType(type, stack)) {
            return false;
        } else {
            String stolen = stack.getItem().getName().getString();
            InvUtils.shiftClick().slotId(slot);
            if ((type == RestockType.PICKAXE_NON_SILK || type == RestockType.PICKAXE_SILK) && HighwayBuilder.shouldSwapBrokenPickaxes()) {
                int brokenPickaxeSlot = findWornPickaxeScreenSlot();
                if (brokenPickaxeSlot != -1) {
                    InvUtils.shiftClick().slotId(brokenPickaxeSlot);
                }
            }

            MusheorSystem.debug("Stealing {%s} from slot [%s]", stolen, slot);
            return true;
        }
    }

    private static boolean matchesRestockType(RestockType type, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        } else {
            return switch (type) {
                case PICKAXE_SILK -> VersionHelper.get().isPickaxe(stack) && Utils.hasEnchantments(stack, Enchantments.SILK_TOUCH) && isDurable(stack);
                case PICKAXE_NON_SILK -> VersionHelper.get().isPickaxe(stack) && !Utils.hasEnchantments(stack, Enchantments.SILK_TOUCH) && isDurable(stack);
                case FOOD -> stack.getComponents().contains(DataComponentTypes.FOOD)
                    && !Modules.get().get(AutoEat.class).blacklist.get().contains(stack.getItem());
                case PAVEMENT -> stack.getItem() == HighwayBuilder.getPavementBlock().asItem()
                    || HighwayBuilder.getPavementBlock() == Blocks.OBSIDIAN && stack.getItem() == Items.ENDER_CHEST;
            };
        }
    }

    public static void restockFromEchest(RestockType type, int shulkers) {
        if (client.world != null && client.player != null && type != null) {
            HighwayState state = HighwayState.getInstance();
            if (!PlayerControlUtils.isGatheringItem() && !state.isEchestFarming()) {
                PlayerControlUtils.setAutoWalk(false);
                Module nuker = Modules.get().get(KekNuker.class);
                if (nuker.isActive()) {
                    nuker.toggle();
                }

                if (delayTicks > 0) {
                    delayTicks--;
                } else {
                    if (!restockInitialized) {
                        selectSlot(findInInventory(Items.ENDER_CHEST));
                        VersionHelper.get().syncInventory();
                        restockInitialized = true;
                        MusheorSystem.debug("Echest shulker restocking has been initialized, proceeding...");
                    }

                    if (client.player.getBlockPos().getSquaredDistance(restockPos) > 1.5) {
                        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(restockPos));
                        MusheorSystem.debug("Player is not at restocking position, calling baritone...");
                    } else if (!containerItemReady) {
                        int echestSlot = findInInventory(Items.ENDER_CHEST);
                        if (echestSlot == -1) {
                            MusheorSystem.debug("No enderchest in inventory to place for echest restock — aborting.");
                            resetRestock(false);
                        } else {
                            selectSlot(echestSlot);
                            containerItemReady = true;
                            delayTicks = swapDelayTicks;
                        }
                    } else {
                        if (!containerPlaced) {
                            containerPos = HighwayUtils.getPosBehind(HighwayBuilder.getDirection());
                            if (placeDelayTicks > 0) {
                                PlayerControlUtils.setKeyPressed(client.options.sneakKey, false);
                                placeDelayTicks--;
                                HighwayUtils.rotateToFace(containerPos, Direction.DOWN);
                                return;
                            }

                            if (client.world.getBlockState(containerPos).getBlock() instanceof EnderChestBlock) {
                                MusheorSystem.debug("Enderchest placed successfully!");
                                containerPlaced = true;
                                delayTicks = placeDelayTicks;
                            } else {
                                if (state.getPendingPlacements().containsKey(containerPos)) {
                                    return;
                                }

                                if (BlockUtils.canPlace(containerPos, true) || client.world.getBlockState(containerPos).isReplaceable()) {
                                    MusheorSystem.debug(
                                        "Placing enderchest at x: %s y: %s z: %s", containerPos.getX(), containerPos.getY(), containerPos.getZ()
                                    );
                                    swapToItem(Items.ENDER_CHEST, MusheorSystem.Manager.silentPlace.get());
                                    BlockPlacer.place(containerPos, Direction.DOWN, state.getPlacementType(), MusheorSystem.Manager.silentPlace.get());
                                    state.getPendingPlacements().put(containerPos, state.getRuntimeTicks());
                                    return;
                                }

                                if (KekMine.INSTANCE.isActive()) {
                                    KekMine.INSTANCE.startMining(containerPos, client.world.getBlockState(containerPos), false);
                                    MusheorSystem.debug("Breaking obstructing block at restocking position");
                                    return;
                                }

                                BlockUtils.breakBlock(containerPos, true);
                                MusheorSystem.debug("Breaking obstructing block at restocking position");
                            }
                        }

                        if (!(client.player.currentScreenHandler instanceof GenericContainerScreenHandler)) {
                            HighwayUtils.rotateToPlace(containerPos);
                            client.interactionManager
                                .sendSequencedPacket(
                                    client.world,
                                    sequence -> new PlayerInteractBlockC2SPacket(
                                        Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofCenter(containerPos), Direction.UP, containerPos, false), sequence
                                    )
                                );
                            MusheorSystem.debug("Opening enderchest at x: %s y: %s z: %s", containerPos.getX(), containerPos.getY(), containerPos.getZ());
                            delayTicks = openDelayTicks;
                        } else if (postOpenDelayTicks > 0) {
                            postOpenDelayTicks--;
                        } else {
                            ScreenHandler handler = client.player.currentScreenHandler;
                            int containerSlots = getContainerSlotCount(handler);
                            if (takenCount < shulkers && containerSlotIndex < containerSlots) {
                                int stealDelay = INSTANCE.stealingDelay.get();
                                if (stealDelay <= 0) {
                                    for (; containerSlotIndex < containerSlots && takenCount < shulkers; containerSlotIndex++) {
                                        if (takeShulkerFromSlot(type, handler, containerSlotIndex)) {
                                            takenCount++;
                                        }
                                    }
                                } else {
                                    if (waitingStealDelay) {
                                        if (stealDelayCounter < stealDelay) {
                                            stealDelayCounter++;
                                            return;
                                        }

                                        waitingStealDelay = false;
                                    }

                                    if (takeShulkerFromSlot(type, handler, containerSlotIndex)) {
                                        takenCount++;
                                        waitingStealDelay = true;
                                        stealDelayCounter = 0;
                                    }

                                    containerSlotIndex++;
                                }
                            } else {
                                MusheorSystem.debug("Completed echest shulker restocking process");
                                resetRestock(true);
                            }
                        }
                    }
                }
            } else {
                restocking = false;
            }
        }
    }

    private static boolean isShulkerBox(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    private static boolean shulkerHasRestockItem(RestockType type, ItemStack shulker) {
        if (!isShulkerBox(shulker)) {
            return false;
        } else {
            ItemStack[] contents = new ItemStack[27];
            Utils.getItemsInContainerItem(shulker, contents);

            for (ItemStack stack : contents) {
                if (stack != null && matchesRestockType(type, stack)) {
                    return true;
                }
            }

            return false;
        }
    }

    private static boolean takeShulkerFromSlot(RestockType type, ScreenHandler handler, int slot) {
        if (!EchestSlotBlacklist.isAllowed(slot)) {
            return false;
        } else {
            ItemStack stack = handler.getSlot(slot).getStack();
            if (!shulkerHasRestockItem(type, stack)) {
                return false;
            } else {
                String stolen = stack.getItem().getName().getString();
                InvUtils.shiftClick().slotId(slot);
                MusheorSystem.debug("Grabbing shulker {%s} from echest slot [%s]", stolen, slot);
                return true;
            }
        }
    }

    public static boolean selectEchestPickaxe() {
        if (client.player == null) {
            return false;
        } else {
            BlockState echest = Blocks.ENDER_CHEST.getDefaultState();
            int bestSlot = -1;
            float bestSpeed = -1.0F;

            for (int i = 0; i < client.player.getInventory().main.size(); i++) {
                ItemStack stack = client.player.getInventory().getStack(i);
                if (VersionHelper.get().isPickaxe(stack)) {
                    float speed = stack.getMiningSpeedMultiplier(echest);
                    if (speed > bestSpeed) {
                        bestSpeed = speed;
                        bestSlot = i;
                    }
                }
            }

            if (bestSlot == -1) {
                return false;
            } else {
                if (bestSlot < 9) {
                    InvUtils.swap(bestSlot, false);
                } else {
                    int hotbarSlot = moveStackToHotbar(client.player.getInventory().getStack(bestSlot));
                    selectSlot(hotbarSlot);
                }

                return true;
            }
        }
    }

    public static void postRestock() {
        if (client.player != null && client.world != null && containerPos != null) {
            if (!inventoryBusy && !HighwayBuilder.isEchestFarmerActive() && !restocking) {
                HighwayState state = HighwayState.getInstance();
                if (client.currentScreen instanceof HandledScreen && client.player.currentScreenHandler != client.player.playerScreenHandler) {
                    client.player.closeHandledScreen();
                    client.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(client.player.playerScreenHandler.syncId));
                } else {
                    Module nuker = Modules.get().get(KekNuker.class);
                    if (!nuker.isActive()) {
                        nuker.toggle();
                    }

                    if (postRestocking) {
                        MusheorSystem.debug("Post-restocking task running...");
                        PlayerControlUtils.setAutoWalk(false);
                        Block restockBlock = client.world.getBlockState(containerPos).getBlock();
                        if (restockBlock instanceof ShulkerBoxBlock || restockBlock instanceof EnderChestBlock) {
                            breakingContainer = true;
                            if (restockBlock instanceof EnderChestBlock) {
                                if (!selectEchestPickaxe()) {
                                    selectBestTool(containerPos);
                                }
                            } else {
                                selectBestTool(containerPos);
                            }

                            if (PlayerUtils.isWithinReach(containerPos)) {
                                PlayerControlUtils.cancelBaritone();
                                if (KekMine.INSTANCE.isActive()) {
                                    KekMine.INSTANCE.startMining(containerPos, client.world.getBlockState(containerPos), false);
                                }

                                if (BlockUtils.breakBlock(containerPos, true)) {
                                    return;
                                }
                            } else if (!BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) {
                                BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(containerPos));
                            }
                        }

                        if (!client.world.getBlockState(containerPos).isAir()) {
                            return;
                        }

                        breakingContainer = false;
                        if (postRestockDelayTicks > 0) {
                            postRestockDelayTicks--;
                            return;
                        }

                        Item item = findNearbyShulkerDrop(12.0);
                        if (item != null) {
                            PlayerControlUtils.startGatheringItem(item, false);
                        }

                        if (client.world.getBlockState(containerPos).getBlock().equals(Blocks.AIR)
                            && !PlayerControlUtils.isGatheringItem()
                            && !state.isShulkerRestocking()
                            && !state.isEchestRestocking()
                            && !breakingContainer
                            && !state.isEchestFarming()) {
                            resetRestock(false);
                        }
                    }
                }
            }
        }
    }
}
