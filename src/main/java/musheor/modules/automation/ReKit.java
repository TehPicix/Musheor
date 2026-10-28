package musheor.modules.automation;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;
import musheor.musheor;
import musheor.utils.network.PacketLimiter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;

public class ReKit extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final MinecraftClient client = MinecraftClient.getInstance();
    public final Setting<ReKit.RekitMode> mode = this.sgGeneral
        .add(
            new Builder<ReKit.RekitMode>().name("mode").description("Auto = triggers on container open. OnKey = triggers on keybind.")
                    .defaultValue(ReKit.RekitMode.Auto)
                .build()
        );
    public final Setting<Keybind> key = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.KeybindSetting.Builder()
                .name("key")
                .description("Keybind to trigger rekit.")
                .defaultValue(Keybind.none())
                .visible(() -> this.mode.get() == ReKit.RekitMode.OnKey)
                .build()
        );
    public final Setting<String> kitName = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("kit-name")
                .description("Name of the active kit. Leave blank to disable.")
                .defaultValue("")
                .build()
        );
    private final HashMap<String, HashMap<Integer, Item>> kitCache = new HashMap<>();
    private boolean keyWasPressed = false;
    private int pendingTicks = -1;
    private ScreenHandler pendingHandler = null;

    public ReKit() {
        super(musheor.MAIN, "rekit", "Sorts inventory to match a saved kit loadout when opening a container or pressing a keybind.");
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();
        WButton saveBtn = list.add(theme.button("Save current loadout")).widget();
        WButton clearBtn = list.add(theme.button("Clear kit cache")).widget();
        saveBtn.action = () -> this.saveKit(this.kitName.get());
        clearBtn.action = this::clearCache;
        return list;
    }

    @Override
    public void onActivate() {
        String name = this.kitName.get();
        if (name.isEmpty()) {
            this.info("No kit selected.", new Object[0]);
        } else {
            HashMap<Integer, Item> kit = this.loadKit(name);
            if (kit == null) {
                this.info("Kit '§b%s§r' not found. Use §b.kit create§r to save one.", new Object[]{name});
            } else {
                this.kitCache.put(name, kit);
                this.info("Loaded kit '§b%s§r' (§b%d§r slots).", new Object[]{name, kit.size()});
            }
        }
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (this.mode.get() == ReKit.RekitMode.Auto) {
            if (this.client.player != null) {
                if (event.screen instanceof HandledScreen) {
                    if (!(event.screen instanceof InventoryScreen)) {
                        ScreenHandler handler = this.client.player.currentScreenHandler;
                        if (handler != null && handler != this.client.player.playerScreenHandler) {
                            this.pendingHandler = handler;
                            this.pendingTicks = 1;
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.client.player != null) {
            if (this.pendingTicks > 0 && --this.pendingTicks == 0) {
                ScreenHandler current = this.client.player.currentScreenHandler;
                if (current != null && current == this.pendingHandler) {
                    this.applyKit(this.pendingHandler);
                }

                this.pendingHandler = null;
            }

            if (this.mode.get() == ReKit.RekitMode.OnKey) {
                boolean pressed = this.key.get().isPressed();
                if (pressed && !this.keyWasPressed) {
                    this.keyWasPressed = true;
                    ScreenHandler handler = null;
                    if (this.client.currentScreen instanceof HandledScreen) {
                        ScreenHandler current = this.client.player.currentScreenHandler;
                        if (current != null && current != this.client.player.playerScreenHandler) {
                            handler = current;
                        }
                    }

                    this.applyKit(handler);
                } else if (!pressed) {
                    this.keyWasPressed = false;
                }
            }
        }
    }

    public void applyKit(ScreenHandler containerHandler) {
        if (this.client.player != null) {
            String name = this.kitName.get();
            if (!name.isEmpty()) {
                record MoveOp(int from, int to, int returnTo) {
                }

                HashMap<Integer, Item> kit = this.kitCache.computeIfAbsent(name, this::loadKit);
                if (kit == null) {
                    this.info("Kit '§b%s§r' not found.", new Object[]{name});
                } else {
                    Set<Integer> kitSlots = kit.keySet();
                    int syncId = this.getSyncId(containerHandler);
                    int containerSize = containerHandler != null && containerHandler != this.client.player.playerScreenHandler
                        ? containerHandler.slots.size() - 36
                        : 0;
                    Item[] invItem = new Item[36];
                    int[] invCount = new int[36];

                    for (int i = 0; i < 36; i++) {
                        ItemStack s = this.client.player.getInventory().getStack(i);
                        invItem[i] = s.getItem();
                        invCount[i] = s.getCount();
                    }

                    Item[] conItem = new Item[containerSize];
                    int[] conCount = new int[containerSize];

                    for (int i = 0; i < containerSize; i++) {
                        ItemStack s = containerHandler.getSlot(i).getStack();
                        conItem[i] = s.getItem();
                        conCount[i] = s.getCount();
                    }

                    List<MoveOp> plan = new ArrayList<>();
                    Iterator var27 = kit.entrySet().iterator();

                    while (true) {
                        int targetInv;
                        Item targetItem;
                        int maxCount;
                        int targetScreen;
                        int current;
                        while (true) {
                            if (!var27.hasNext()) {
                                for (MoveOp op : plan) {
                                    if (!PacketLimiter.canSendInventoryPacket()) {
                                        return;
                                    }

                                    if (op.to() == -1) {
                                        this.client.interactionManager.clickSlot(syncId, op.from(), 0, SlotActionType.QUICK_MOVE, this.client.player);
                                    } else {
                                        this.client.interactionManager.clickSlot(syncId, op.from(), 0, SlotActionType.PICKUP, this.client.player);
                                        this.client.interactionManager.clickSlot(syncId, op.to(), 0, SlotActionType.PICKUP, this.client.player);
                                        if (op.returnTo() != -1) {
                                            this.client.interactionManager.clickSlot(syncId, op.returnTo(), 0, SlotActionType.PICKUP, this.client.player);
                                        }
                                    }
                                }

                                return;
                            }

                            Entry<Integer, Item> entry = (Entry<Integer, Item>)var27.next();
                            targetInv = entry.getKey();
                            targetItem = entry.getValue();
                            maxCount = targetItem.getMaxCount();
                            targetScreen = this.toScreenSlot(targetInv, containerHandler);
                            current = invItem[targetInv] == targetItem ? invCount[targetInv] : 0;
                            if (current < maxCount) {
                                if (invCount[targetInv] <= 0 || invItem[targetInv] == targetItem) {
                                    break;
                                }

                                int freeSlot = this.findFreeSlot(invCount, kitSlots, targetInv);
                                if (freeSlot != -1) {
                                    plan.add(new MoveOp(targetScreen, this.toScreenSlot(freeSlot, containerHandler), -1));
                                    invItem[freeSlot] = invItem[targetInv];
                                    invCount[freeSlot] = invCount[targetInv];
                                } else {
                                    if (containerSize <= 0) {
                                        continue;
                                    }

                                    plan.add(new MoveOp(targetScreen, -1, -1));
                                }

                                invItem[targetInv] = Items.AIR;
                                invCount[targetInv] = 0;
                                current = 0;
                                break;
                            }
                        }

                        int remaining = maxCount - current;

                        for (int i = 0; i < 36 && remaining > 0; i++) {
                            if (invItem[i] == targetItem && i != targetInv && invCount[i] != 0) {
                                Item kitAtI = kit.get(i);
                                if (kitAtI == null || kitAtI != targetItem) {
                                    int fromScreen = this.toScreenSlot(i, containerHandler);
                                    int moved = Math.min(invCount[i], remaining);
                                    plan.add(new MoveOp(fromScreen, targetScreen, invCount[i] > remaining ? fromScreen : -1));
                                    invCount[i] -= moved;
                                    if (invCount[i] == 0) {
                                        invItem[i] = Items.AIR;
                                    }

                                    invCount[targetInv] += moved;
                                    invItem[targetInv] = targetItem;
                                    remaining -= moved;
                                }
                            }
                        }

                        for (int ix = 0; ix < containerSize && remaining > 0; ix++) {
                            if (conItem[ix] == targetItem && conCount[ix] != 0) {
                                int moved = Math.min(conCount[ix], remaining);
                                plan.add(new MoveOp(ix, targetScreen, conCount[ix] > remaining ? ix : -1));
                                conCount[ix] -= moved;
                                if (conCount[ix] == 0) {
                                    conItem[ix] = Items.AIR;
                                }

                                invCount[targetInv] += moved;
                                invItem[targetInv] = targetItem;
                                remaining -= moved;
                            }
                        }
                    }
                }
            }
        }
    }

    private int toScreenSlot(int invSlot, ScreenHandler containerHandler) {
        if (containerHandler != null && containerHandler != this.client.player.playerScreenHandler) {
            int containerSize = containerHandler.slots.size() - 36;
            return invSlot <= 8 ? containerSize + 27 + invSlot : containerSize + (invSlot - 9);
        } else {
            return invSlot <= 8 ? 36 + invSlot : invSlot;
        }
    }

    private int getSyncId(ScreenHandler containerHandler) {
        return containerHandler != null && containerHandler != this.client.player.playerScreenHandler
            ? containerHandler.syncId
            : this.client.player.playerScreenHandler.syncId;
    }

    private int findFreeSlot(int[] invCount, Set<Integer> kitSlots, int exclude) {
        for (int i = 35; i >= 9; i--) {
            if (i != exclude && !kitSlots.contains(i) && invCount[i] == 0) {
                return i;
            }
        }

        for (int ix = 8; ix >= 0; ix--) {
            if (ix != exclude && !kitSlots.contains(ix) && invCount[ix] == 0) {
                return ix;
            }
        }

        return -1;
    }

    public void saveKit(String kitName) {
        if (kitName != null && !kitName.isEmpty()) {
            if (this.client.player != null) {
                Map<String, String> kitData = new LinkedHashMap<>();

                for (int i = 0; i < 36; i++) {
                    ItemStack stack = this.client.player.getInventory().getStack(i);
                    if (!stack.isEmpty()) {
                        kitData.put(String.valueOf(i), Registries.ITEM.getId(stack.getItem()).toString());
                    }
                }

                File file = this.getKitFile(kitName);
                if (file != null) {
                    file.getParentFile().mkdirs();

                    try (Writer writer = new FileWriter(file)) {
                        new Gson().toJson(kitData, writer);
                    } catch (IOException var9) {
                        this.error("Failed to save kit: %s", new Object[]{var9.getMessage()});
                        return;
                    }

                    HashMap<Integer, Item> itemMap = this.parseKit(kitData);
                    this.kitCache.put(kitName, itemMap);
                    this.info("Saved kit '§b%s§r' (§b%d§r slots).", new Object[]{kitName, itemMap.size()});
                }
            }
        } else {
            this.info("Set a kit name first.", new Object[0]);
        }
    }

    public HashMap<Integer, Item> loadKit(String kitName) {
        File file = this.getKitFile(kitName);
        if (file != null && file.exists()) {
            try {
                HashMap var6;
                try (Reader reader = new FileReader(file)) {
                    Type type = (new TypeToken<HashMap<String, String>>() {}).getType();
                    HashMap<String, String> raw = (HashMap<String, String>)new Gson().fromJson(reader, type);
                    if (raw == null) {
                        return null;
                    }

                    var6 = this.parseKit(raw);
                }

                return var6;
            } catch (Exception var9) {
                this.error("Failed to load kit '§b%s§r': %s", new Object[]{kitName, var9.getMessage()});
                return null;
            }
        } else {
            return null;
        }
    }

    private HashMap<Integer, Item> parseKit(Map<String, String> raw) {
        HashMap<Integer, Item> map = new HashMap<>();

        for (Entry<String, String> e : raw.entrySet()) {
            try {
                int slot = Integer.parseInt(e.getKey());
                if (slot >= 0 && slot <= 35) {
                    Item item = Registries.ITEM.get(Identifier.of(e.getValue()));
                    if (item != Items.AIR) {
                        map.put(slot, item);
                    }
                }
            } catch (NumberFormatException var7) {
            }
        }

        return map;
    }

    public File getKitFile(String kitName) {
        if (kitName != null && !kitName.isEmpty()) {
            try {
                return new File(new File(new File(MeteorClient.FOLDER, "musheor"), "kits"), kitName + ".json");
            } catch (NullPointerException var3) {
                return null;
            }
        } else {
            return null;
        }
    }

    public void clearCache() {
        this.kitCache.clear();
        this.info("Cleared kit cache.", new Object[0]);
    }

    public void deleteKit(String name) {
        this.kitCache.remove(name);
        File file = this.getKitFile(name);
        if (file != null && file.exists() && file.delete()) {
            this.info("Deleted kit '§b%s§r'.", new Object[]{name});
        }
    }

    public boolean kitExists(String name) {
        if (this.kitCache.containsKey(name)) {
            return true;
        } else {
            File file = this.getKitFile(name);
            return file != null && file.exists();
        }
    }

    public List<String> listKits() {
        File kitsDir = new File(new File(MeteorClient.FOLDER, "musheor"), "kits");
        if (!kitsDir.exists()) {
            return Collections.emptyList();
        } else {
            File[] files = kitsDir.listFiles((d, n) -> n.endsWith(".json"));
            if (files == null) {
                return Collections.emptyList();
            } else {
                List<String> names = new ArrayList<>();

                for (File f : files) {
                    names.add(f.getName().replace(".json", ""));
                }

                return names;
            }
        }
    }

    public static enum RekitMode {
        Auto,
        OnKey;
    }
}
