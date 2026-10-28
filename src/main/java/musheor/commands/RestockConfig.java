package musheor.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.systems.Systems;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import musheor.compat.VersionHelper;
import musheor.utils.HighwayUtils;
import musheor.utils.system.MusheorSystem;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLong;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class RestockConfig extends Command {
    private static final Map<Item, List<BlockPos>> restockContainers = new HashMap<>();
    private static boolean restockingConfigMode = false;
    private static Item restockingConfigItem = null;
    private static boolean autoConfigMode = false;
    private static BlockPos pendingContainerPos = null;
    private static int scanDelayTicks = -1;

    public RestockConfig() {
        super("rc", "Configure restocking container locations for items.", "rc");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(this::executeDefault);
        builder.then(literal("auto").executes(this::executeAuto));
        builder.then(literal("add").then(argument("item", ItemStackArgumentType.itemStack(REGISTRY_ACCESS)).executes(this::executeAdd)));
        builder.then(literal("clear").executes(this::executeClear));
        builder.then(literal("list").executes(this::executeList));
        builder.then(literal("remove").then(argument("item", ItemStackArgumentType.itemStack(REGISTRY_ACCESS)).executes(this::executeRemove)));
    }

    private int executeDefault(CommandContext<CommandSource> context) {
        if (!autoConfigMode) {
            this.info("Usage: .rc auto | add <item> | remove <item> | list | clear", new Object[0]);
            return 1;
        } else {
            autoConfigMode = false;
            pendingContainerPos = null;
            scanDelayTicks = -1;
            if (MeteorClient.EVENT_BUS.isListening(RestockConfig.AutoConfigListener.class)) {
                MeteorClient.EVENT_BUS.unsubscribe(RestockConfig.AutoConfigListener.INSTANCE);
            }

            this.info("Exited auto config mode", new Object[0]);
            return 1;
        }
    }

    private int executeAuto(CommandContext<CommandSource> context) {
        autoConfigMode = true;
        pendingContainerPos = null;
        scanDelayTicks = -1;
        this.info("Auto config mode enabled", new Object[0]);
        this.info("§7Open containers to automatically register their contents", new Object[0]);
        this.info("§7Type .rc to exit", new Object[0]);
        if (!MeteorClient.EVENT_BUS.isListening(RestockConfig.AutoConfigListener.class)) {
            MeteorClient.EVENT_BUS.subscribe(RestockConfig.AutoConfigListener.INSTANCE);
        }

        return 1;
    }

    private int executeAdd(CommandContext<CommandSource> context) {
        Item item = ItemStackArgumentType.getItemStackArgument(context, "item").getItem();
        if (item != null && item != Items.AIR) {
            restockingConfigMode = true;
            restockingConfigItem = item;
            this.info("Restocking config mode enabled for §b%s", new Object[]{getItemId(item)});
            this.info("§7Right-click containers to add them", new Object[0]);
            this.info("§7Esc to exit config mode", new Object[0]);
            if (!MeteorClient.EVENT_BUS.isListening(RestockConfig.RestockingConfigListener.class)) {
                MeteorClient.EVENT_BUS.subscribe(RestockConfig.RestockingConfigListener.INSTANCE);
            }

            return 1;
        } else {
            this.error("Invalid item: %s", new Object[]{item});
            return 1;
        }
    }

    private int executeClear(CommandContext<CommandSource> context) {
        int totalContainers = restockContainers.values().stream().mapToInt(List::size).sum();
        restockContainers.clear();
        saveConfig();
        this.info("Cleared all restocking containers (%d total)", new Object[]{totalContainers});
        return 1;
    }

    private int executeList(CommandContext<CommandSource> context) {
        if (restockContainers.isEmpty()) {
            this.info("No restocking containers configured", new Object[0]);
            return 1;
        } else {
            this.info("Restocking containers:", new Object[0]);

            for (Entry<Item, List<BlockPos>> entry : restockContainers.entrySet()) {
                Item item = entry.getKey();
                List<BlockPos> positions = entry.getValue();
                this.info("§b%s§r (%d containers):", new Object[]{getItemId(item), positions.size()});

                for (BlockPos pos : positions) {
                    this.info("  - %s, blocks away", new Object[]{Math.ceil(HighwayUtils.horizontalDistance(mc.player.getBlockPos(), pos))});
                }
            }

            return 1;
        }
    }

    private int executeRemove(CommandContext<CommandSource> context) {
        Item item = ItemStackArgumentType.getItemStackArgument(context, "item").getItem();
        if (item != null && item != Items.AIR) {
            List<BlockPos> removed = restockContainers.remove(item);
            if (removed != null && !removed.isEmpty()) {
                this.info("Removed %d containers for %s", new Object[]{removed.size(), getItemId(item)});
                saveConfig();
            } else {
                this.warning("No containers configured for %s", new Object[]{getItemId(item)});
            }

            return 1;
        } else {
            this.error("Invalid item: %s", new Object[]{item});
            return 1;
        }
    }

    public static String getItemId(Item item) {
        return Registries.ITEM.getId(item).toString().replace("minecraft:", "");
    }

    public static List<BlockPos> getContainers(Item item) {
        return restockContainers.getOrDefault(item, new ArrayList<>());
    }

    public static boolean hasContainers(Item item) {
        return restockContainers.containsKey(item) && !restockContainers.get(item).isEmpty();
    }

    private static void saveConfig() {
        Systems.get(MusheorSystem.class).save();
    }

    public static NbtCompound toNbt() {
        NbtCompound tag = new NbtCompound();

        for (Entry<Item, List<BlockPos>> entry : restockContainers.entrySet()) {
            String key = Registries.ITEM.getId(entry.getKey()).toString();
            NbtList posList = new NbtList();

            for (BlockPos pos : entry.getValue()) {
                posList.add(NbtLong.of(pos.asLong()));
            }

            tag.put(key, posList);
        }

        return tag;
    }

    public static void fromNbt(NbtCompound tag) {
        restockContainers.clear();

        for (String key : tag.getKeys()) {
            Identifier id = Identifier.tryParse(key);
            if (id != null && Registries.ITEM.containsId(id)) {
                Item item = Registries.ITEM.get(id);
                NbtList posList = VersionHelper.get().getList(tag, key, 4);
                List<BlockPos> positions = new ArrayList<>();

                for (int i = 0; i < posList.size(); i++) {
                    positions.add(BlockPos.fromLong(((NbtLong)posList.get(i)).longValue()));
                }

                restockContainers.put(item, positions);
            }
        }
    }

    private static class AutoConfigListener {
        private static final RestockConfig.AutoConfigListener INSTANCE = new RestockConfig.AutoConfigListener();

        @EventHandler(
            priority = 200
        )
        private void onInteractBlock(InteractBlockEvent event) {
            if (RestockConfig.autoConfigMode) {
                RestockConfig.pendingContainerPos = event.result.getBlockPos();
                RestockConfig.scanDelayTicks = -1;
            }
        }

        @EventHandler(
            priority = 200
        )
        private void onOpenScreen(OpenScreenEvent event) {
            if (RestockConfig.autoConfigMode && RestockConfig.pendingContainerPos != null) {
                if (event.screen != null) {
                    RestockConfig.scanDelayTicks = 4;
                }
            }
        }

        @EventHandler
        private void onTick(Post event) {
            if (RestockConfig.autoConfigMode && RestockConfig.scanDelayTicks >= 0) {
                if (RestockConfig.scanDelayTicks > 0) {
                    RestockConfig.scanDelayTicks--;
                } else {
                    RestockConfig.scanDelayTicks = -1;
                    if (RestockConfig.mc.player != null && RestockConfig.mc.player.currentScreenHandler != null && RestockConfig.pendingContainerPos != null) {
                        ScreenHandler handler = RestockConfig.mc.player.currentScreenHandler;
                        Set<Item> foundItems = new HashSet<>();
                        int containerSlotCount = handler.slots.size() - 36;
                        if (containerSlotCount > 0) {
                            for (int i = 0; i < containerSlotCount; i++) {
                                Slot slot = handler.slots.get(i);
                                ItemStack stack = slot.getStack();
                                if (!stack.isEmpty() && stack.getItem() != Items.AIR) {
                                    foundItems.add(stack.getItem());
                                }
                            }

                            if (foundItems.isEmpty()) {
                                ChatUtils.info(
                                    "Container at §b%d %d %d§r is empty, skipping",
                                    RestockConfig.pendingContainerPos.getX(),
                                    RestockConfig.pendingContainerPos.getY(),
                                    RestockConfig.pendingContainerPos.getZ()
                                );
                            } else {
                                BlockPos pos = RestockConfig.pendingContainerPos;

                                for (Item item : foundItems) {
                                    List<BlockPos> positions = RestockConfig.restockContainers.computeIfAbsent(item, k -> new ArrayList<>());
                                    if (!positions.contains(pos)) {
                                        positions.add(pos);
                                    }
                                }

                                RestockConfig.saveConfig();
                                ChatUtils.info("Registered §b%d§r item(s) from container", foundItems.size(), pos.getX(), pos.getY(), pos.getZ());

                                for (Item itemx : foundItems) {
                                    ChatUtils.info("  - §b%s", RestockConfig.getItemId(itemx));
                                }
                            }

                            RestockConfig.pendingContainerPos = null;
                        }
                    }
                }
            }
        }
    }

    private static class RestockingConfigListener {
        private static final RestockConfig.RestockingConfigListener INSTANCE = new RestockConfig.RestockingConfigListener();

        @EventHandler(
            priority = 200
        )
        private void onInteractBlock(InteractBlockEvent event) {
            if (RestockConfig.restockingConfigMode && RestockConfig.restockingConfigItem != null) {
                BlockPos pos = event.result.getBlockPos();
                RestockConfig.restockContainers.computeIfAbsent(RestockConfig.restockingConfigItem, k -> new ArrayList<>()).add(pos);
                RestockConfig.saveConfig();
                ChatUtils.info("Added container for §b%s", Registries.ITEM.getId(RestockConfig.restockingConfigItem).toString());
                event.cancel();
            }
        }

        @EventHandler(
            priority = 200
        )
        private void onOpenScreen(OpenScreenEvent event) {
            if (RestockConfig.restockingConfigMode) {
                if (event.screen != null) {
                    this.exitConfigMode();
                    event.cancel();
                }
            }
        }

        private void exitConfigMode() {
            RestockConfig.restockingConfigMode = false;
            Item item = RestockConfig.restockingConfigItem;
            RestockConfig.restockingConfigItem = null;
            int count = RestockConfig.restockContainers.getOrDefault(item, new ArrayList<>()).size();
            ChatUtils.info("Exited restocking config mode. Added §b%d§r containers for §b%s", count, RestockConfig.getItemId(item));
        }
    }
}
