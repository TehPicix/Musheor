package musheor.modules.automation;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import musheor.musheor;
import musheor.utils.network.PacketLimiter;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.BundleItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.CartographyTableScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

public class MapDuper extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgBundles = this.settings.createGroup("Bundles");
    private final Setting<Integer> copies = this.sgGeneral
        .add(
            new Builder()
                .name("copies")
                .description("How many duplicates to make of each map. Each one costs one empty map.")
                .defaultValue(1)
                .sliderRange(1, 16)
                .build()
        );
    private final Setting<Integer> delay = this.sgGeneral
        .add(
            new Builder()
                .name("delay")
                .description("Extra ticks to wait between slot actions. 0 acts once every tick.")
                .defaultValue(3)
                .sliderRange(0, 20)
                .build()
        );
    private final Setting<Boolean> disableWhenDone = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("disable-when-done")
                .description("Turn the module off once a run finishes.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> closeTableWhenDone = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("close-table-when-done")
                .description("Close the cartography table once a run finishes.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> chatFeedbackSetting = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("chat-feedback")
                .description("Reports what was duplicated, and why a run stopped early.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> useBundles = this.sgBundles
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("use-bundles")
                .description("Take the maps to copy out of a bundle first. Off, the loose maps in your inventory are the job.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> dropOverflow = this.sgBundles
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("drop-overflow")
                .description(
                    "A bundle holds more maps than the inventory has slots, so once it is full the rest are thrown on the floor — looking straight down, so they land at your feet."
                )
                .defaultValue(false)
                .visible(this.useBundles::get)
                .build()
        );
    private final Setting<Boolean> originalsToSource = this.sgBundles
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("originals-to-source")
                .description("Put one of each map back into the bundle it came out of.")
                .defaultValue(true)
                .visible(this.useBundles::get)
                .build()
        );
    private final Setting<Boolean> copiesToBundles = this.sgBundles
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("copies-to-bundles")
                .description(
                    "Put the new copies into the other bundles in your inventory, filling them in slot order. Off, or with no bundle free, copies are left loose."
                )
                .defaultValue(true)
                .visible(this.useBundles::get)
                .build()
        );
    private final Setting<Integer> mapsPerBundle = this.sgBundles
        .add(
            new Builder()
                .name("maps-per-bundle")
                .description("How many maps to put in a copy bundle before moving on to the next one. A bundle holds 64.")
                .defaultValue(64)
                .min(1)
                .sliderRange(1, 64)
                .visible(() -> this.useBundles.get() && this.copiesToBundles.get())
                .build()
        );
    private MapDuper.Stage stage = MapDuper.Stage.Done;
    private final Set<MapIdComponent> jobMaps = new LinkedHashSet<>();
    private final Deque<MapIdComponent> copyQueue = new ArrayDeque<>();
    private int copiesLeft;
    private boolean awaitingOutput;
    private final Set<MapIdComponent> returnedOriginals = new LinkedHashSet<>();
    private final Set<MapIdComponent> completedMaps = new LinkedHashSet<>();
    private int sourceBundleSlot = -1;
    private int pendingDestinationSlot = -1;
    private int actionTimer;
    private int jobSize;
    private boolean lookingDown;
    private int finishedSyncId = -1;

    public MapDuper() {
        super(musheor.AUTOMATION, "map-duper", "Duplicates maparts at a cartography table, optionally straight out of and back into bundles.");
    }

    @Override
    public void onActivate() {
        this.reset();
    }

    @Override
    public void onDeactivate() {
        this.reset();
    }

    private void resetJob() {
        this.stage = MapDuper.Stage.Done;
        this.jobMaps.clear();
        this.copyQueue.clear();
        this.copiesLeft = 0;
        this.awaitingOutput = false;
        this.returnedOriginals.clear();
        this.completedMaps.clear();
        this.sourceBundleSlot = -1;
        this.pendingDestinationSlot = -1;
        this.actionTimer = 0;
        this.jobSize = 0;
        this.lookingDown = false;
    }

    private void reset() {
        this.resetJob();
        this.finishedSyncId = -1;
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player != null && this.mc.world != null && this.mc.interactionManager != null) {
            CartographyTableScreenHandler handler = this.getTable();
            if (handler == null) {
                if (this.stage != MapDuper.Stage.Done) {
                    this.finish("Cartography table closed");
                }
            } else {
                if (this.stage == MapDuper.Stage.Done) {
                    if (handler.syncId == this.finishedSyncId) {
                        return;
                    }

                    this.startJob(handler);
                    if (this.stage == MapDuper.Stage.Done) {
                        return;
                    }
                }

                if (--this.actionTimer <= 0) {
                    this.actionTimer = this.delay.get() + 1;
                    if (PacketLimiter.canSendInventoryPacket()) {
                        this.runStage(handler);
                    }
                }
            }
        }
    }

    private CartographyTableScreenHandler getTable() {
        if (this.mc.player == null) {
            return null;
        } else {
            return this.mc.player.currentScreenHandler instanceof CartographyTableScreenHandler table ? table : null;
        }
    }

    private void startJob(CartographyTableScreenHandler handler) {
        this.jobMaps.clear();
        this.copyQueue.clear();
        this.copiesLeft = 0;
        this.awaitingOutput = false;
        this.returnedOriginals.clear();
        this.completedMaps.clear();
        this.pendingDestinationSlot = -1;
        this.lookingDown = false;
        this.sourceBundleSlot = this.useBundles.get() ? this.findSourceBundle(handler) : -1;
        boolean bundled = this.sourceBundleSlot >= 0;
        this.jobMaps.addAll(bundled ? this.collectBundleMaps(handler) : this.collectInventoryMaps(handler));
        if (this.jobMaps.isEmpty()) {
            this.sourceBundleSlot = -1;
        } else {
            this.jobSize = this.jobMaps.size();
            if (bundled) {
                this.stage = MapDuper.Stage.Extract;
            } else {
                this.beginCopy(handler);
            }

            if (this.chatFeedbackSetting.get()) {
                this.info(
                    "Duplicating §b%d§r map(s) §b%d§r time(s) each%s.", new Object[]{this.jobMaps.size(), this.copies.get(), bundled ? " from a bundle" : ""}
                );
            }
        }
    }

    private void beginCopy(CartographyTableScreenHandler handler) {
        this.copyQueue.clear();
        this.awaitingOutput = false;

        for (MapIdComponent id : this.jobMaps) {
            if (!this.completedMaps.contains(id) && this.countMapCopies(handler, id) > 0) {
                this.copyQueue.addLast(id);
            }
        }

        this.copiesLeft = this.copies.get();
        this.stage = MapDuper.Stage.Copy;
    }

    private boolean runStage(CartographyTableScreenHandler handler) {
        return switch (this.stage) {
            case Extract -> this.runExtract(handler);
            case Copy -> this.copyStep(handler);
            case Store -> this.runStore(handler);
            case Done -> false;
        };
    }

    private boolean runExtract(CartographyTableScreenHandler handler) {
        boolean acted = false;

        for (int i = 0; i < 160 && PacketLimiter.canSendInventoryPacket(); i++) {
            boolean hadPitch = this.lookingDown;
            if (!this.extractStep(handler)) {
                break;
            }

            acted = true;
            if (this.getTable() == null || this.stage != MapDuper.Stage.Extract || !hadPitch && this.lookingDown) {
                break;
            }
        }

        return acted;
    }

    private boolean extractStep(CartographyTableScreenHandler handler) {
        if (getMapId(handler.getCursorStack()) != null) {
            int free = this.countEmptySlots(handler) > 4 ? this.findEmptySlot(handler) : -1;
            if (free >= 0) {
                this.clickSlot(handler, free, 0, SlotActionType.PICKUP);
                return true;
            } else if (!this.dropOverflow.get()) {
                return this.fail("Inventory full and drop-overflow is off");
            } else if (!this.lookingDown) {
                this.mc.player.setPitch(90.0F);
                this.lookingDown = true;
                return true;
            } else {
                this.clickSlot(handler, -999, 0, SlotActionType.PICKUP);
                return true;
            }
        } else if (this.stashCursor(handler)) {
            return true;
        } else if (!this.isBundleWithMaps(handler, this.sourceBundleSlot)) {
            this.beginCopy(handler);
            return true;
        } else {
            this.clickSlot(handler, this.sourceBundleSlot, 1, SlotActionType.PICKUP);
            return true;
        }
    }

    private boolean copyStep(CartographyTableScreenHandler handler) {
        if (this.stashCursor(handler)) {
            return true;
        } else if (this.countEmptySlots(handler) < 2) {
            return this.storeMaps(handler, true) ? true : this.fail("Inventory full and nothing left that can be put away");
        } else {
            MapIdComponent id = this.copyQueue.peekFirst();
            if (id == null) {
                this.stage = MapDuper.Stage.Store;
                return true;
            } else if (handler.getSlot(2).hasStack()) {
                this.clickSlot(handler, 2, 0, SlotActionType.QUICK_MOVE);
                if (this.awaitingOutput) {
                    this.awaitingOutput = false;
                    if (--this.copiesLeft <= 0) {
                        this.markCompleted(id);
                    }
                }

                return true;
            } else if (getMapId(handler.getSlot(0).getStack()) != null) {
                if (this.loadEmptyMap(handler)) {
                    return true;
                } else {
                    this.awaitingOutput = true;
                    return false;
                }
            } else if (this.loadEmptyMap(handler)) {
                return true;
            } else {
                int slot = this.findSlot(handler, stack -> id.equals(getMapId(stack)));
                if (slot < 0) {
                    this.markCompleted(id);
                    return true;
                } else {
                    this.clickSlot(handler, slot, 0, SlotActionType.QUICK_MOVE);
                    this.awaitingOutput = true;
                    return true;
                }
            }
        }
    }

    private void markCompleted(MapIdComponent id) {
        this.completedMaps.add(id);
        this.copyQueue.removeFirstOccurrence(id);
        this.copiesLeft = this.copies.get();
        this.awaitingOutput = false;
    }

    private boolean loadEmptyMap(CartographyTableScreenHandler handler) {
        if (handler.getSlot(1).hasStack()) {
            return false;
        } else {
            int empty = this.findSlot(handler, stack -> stack.isOf(Items.MAP));
            if (empty < 0) {
                this.fail("Out of empty maps");
                return true;
            } else {
                this.clickSlot(handler, empty, 0, SlotActionType.QUICK_MOVE);
                return true;
            }
        }
    }

    private boolean runStore(CartographyTableScreenHandler handler) {
        if (this.storeMaps(handler, false)) {
            return true;
        } else if (this.sourceHasPendingMaps(handler) && this.countEmptySlots(handler) > 4) {
            this.stage = MapDuper.Stage.Extract;
            return true;
        } else {
            this.finish(null);
            return false;
        }
    }

    private boolean storeMaps(CartographyTableScreenHandler handler, boolean onlyCompleted) {
        boolean acted = false;

        for (int i = 0; i < 160 && PacketLimiter.canSendInventoryPacket() && this.storeStep(handler, onlyCompleted); i++) {
            acted = true;
            if (this.getTable() == null) {
                break;
            }
        }

        return acted;
    }

    private boolean storeStep(CartographyTableScreenHandler handler, boolean onlyCompleted) {
        ItemStack cursor = handler.getCursorStack();
        if (getMapId(cursor) != null) {
            if (cursor.getCount() > 1) {
                int free = this.findEmptySlot(handler);
                if (free >= 0) {
                    this.clickSlot(handler, free, 1, SlotActionType.PICKUP);
                    return true;
                }
            }

            if (this.pendingDestinationSlot >= 0 && this.canStoreInBundle(handler, this.pendingDestinationSlot)) {
                this.clickSlot(handler, this.pendingDestinationSlot, 0, SlotActionType.PICKUP);
                this.pendingDestinationSlot = -1;
                return true;
            } else {
                this.pendingDestinationSlot = -1;
                return this.stashCursor(handler);
            }
        } else if (this.stashCursor(handler)) {
            return true;
        } else {
            for (int slot = 3; slot < handler.slots.size(); slot++) {
                MapIdComponent id = getMapId(handler.getSlot(slot).getStack());
                if (id != null && this.jobMaps.contains(id) && (!onlyCompleted || this.completedMaps.contains(id))) {
                    int destination = this.pickStoreDestination(handler, id);
                    if (destination >= 0) {
                        this.pendingDestinationSlot = destination;
                        this.clickSlot(handler, slot, 0, SlotActionType.PICKUP);
                        return true;
                    }
                }
            }

            return false;
        }
    }

    private int pickStoreDestination(CartographyTableScreenHandler handler, MapIdComponent id) {
        if (this.originalsToSource.get()
            && this.sourceBundleSlot >= 0
            && !this.returnedOriginals.contains(id)
            && this.isBundle(handler, this.sourceBundleSlot)
            && this.countMapsInBundle(handler, this.sourceBundleSlot) < 64) {
            this.returnedOriginals.add(id);
            return this.sourceBundleSlot;
        } else {
            return this.copiesToBundles.get() ? this.findCopyBundle(handler) : -1;
        }
    }

    private int findCopyBundle(CartographyTableScreenHandler handler) {
        for (int slot = 3; slot < handler.slots.size(); slot++) {
            if (slot != this.sourceBundleSlot
                && this.isBundle(handler, slot)
                && this.holdsOnlyMaps(handler, slot)
                && this.countMapsInBundle(handler, slot) < this.mapsPerBundle.get()) {
                return slot;
            }
        }

        return -1;
    }

    private boolean canStoreInBundle(CartographyTableScreenHandler handler, int slot) {
        if (!this.isBundle(handler, slot)) {
            return false;
        } else {
            int limit = slot == this.sourceBundleSlot ? 64 : this.mapsPerBundle.get();
            return this.countMapsInBundle(handler, slot) < limit;
        }
    }

    private boolean stashCursor(CartographyTableScreenHandler handler) {
        if (handler.getCursorStack().isEmpty()) {
            return false;
        } else {
            int free = this.findEmptySlot(handler);
            if (free < 0) {
                return false;
            } else {
                this.clickSlot(handler, free, 0, SlotActionType.PICKUP);
                return true;
            }
        }
    }

    private void clickSlot(ScreenHandler handler, int slot, int button, SlotActionType action) {
        this.mc.interactionManager.clickSlot(handler.syncId, slot, button, action, this.mc.player);
    }

    private boolean fail(String reason) {
        this.finish(reason);
        return false;
    }

    private void finish(String reason) {
        if (this.chatFeedbackSetting.get()) {
            if (reason == null) {
                this.info("Done — §b%d§r mapart(s) duplicated.", new Object[]{this.jobSize});
            } else {
                this.warning("Stopped: %s.", new Object[]{reason});
            }
        }

        CartographyTableScreenHandler handler = this.getTable();
        if (handler != null) {
            this.finishedSyncId = handler.syncId;
        }

        this.resetJob();
        if (this.closeTableWhenDone.get() && this.mc.player != null) {
            this.mc.player.closeHandledScreen();
        }

        if (this.disableWhenDone.get()) {
            this.toggle();
        }
    }

    private static MapIdComponent getMapId(ItemStack stack) {
        return !stack.isEmpty() && stack.isOf(Items.FILLED_MAP) ? stack.get(DataComponentTypes.MAP_ID) : null;
    }

    private int countMapCopies(ScreenHandler handler, MapIdComponent id) {
        int count = 0;

        for (int slot = 3; slot < handler.slots.size(); slot++) {
            if (id.equals(getMapId(handler.getSlot(slot).getStack()))) {
                count++;
            }
        }

        return count;
    }

    private int findSlot(ScreenHandler handler, Predicate<ItemStack> test) {
        for (int slot = 3; slot < handler.slots.size(); slot++) {
            ItemStack stack = handler.getSlot(slot).getStack();
            if (!stack.isEmpty() && test.test(stack)) {
                return slot;
            }
        }

        return -1;
    }

    private int countEmptySlots(ScreenHandler handler) {
        int count = 0;

        for (int slot = 3; slot < handler.slots.size(); slot++) {
            if (!handler.getSlot(slot).hasStack()) {
                count++;
            }
        }

        return count;
    }

    private int findEmptySlot(ScreenHandler handler) {
        for (int slot = 3; slot < handler.slots.size(); slot++) {
            if (!handler.getSlot(slot).hasStack()) {
                return slot;
            }
        }

        return -1;
    }

    private boolean isBundle(ScreenHandler handler, int slot) {
        return slot >= 3 && slot < handler.slots.size() ? handler.getSlot(slot).getStack().getItem() instanceof BundleItem : false;
    }

    private static BundleContentsComponent getBundleContents(ScreenHandler handler, int slot) {
        return handler.getSlot(slot).getStack().get(DataComponentTypes.BUNDLE_CONTENTS);
    }

    private int countMapsInBundle(ScreenHandler handler, int slot) {
        BundleContentsComponent contents = getBundleContents(handler, slot);
        if (contents == null) {
            return 0;
        } else {
            int count = 0;

            for (ItemStack inner : contents.iterate()) {
                if (getMapId(inner) != null) {
                    count += inner.getCount();
                }
            }

            return count;
        }
    }

    private boolean holdsOnlyMaps(ScreenHandler handler, int slot) {
        BundleContentsComponent contents = getBundleContents(handler, slot);
        if (contents != null && !contents.isEmpty()) {
            for (ItemStack inner : contents.iterate()) {
                if (getMapId(inner) == null) {
                    return false;
                }
            }

            return true;
        } else {
            return true;
        }
    }

    private boolean isBundleWithMaps(ScreenHandler handler, int slot) {
        return this.isBundle(handler, slot) && this.countMapsInBundle(handler, slot) > 0;
    }

    private boolean sourceHasPendingMaps(ScreenHandler handler) {
        if (!this.isBundle(handler, this.sourceBundleSlot)) {
            return false;
        } else {
            BundleContentsComponent contents = getBundleContents(handler, this.sourceBundleSlot);
            if (contents == null) {
                return false;
            } else {
                for (ItemStack inner : contents.iterate()) {
                    MapIdComponent id = getMapId(inner);
                    if (id != null && !this.completedMaps.contains(id)) {
                        return true;
                    }
                }

                return false;
            }
        }
    }

    private int findSourceBundle(CartographyTableScreenHandler handler) {
        for (int slot = 3; slot < handler.slots.size(); slot++) {
            if (this.isBundleWithMaps(handler, slot)) {
                return slot;
            }
        }

        return -1;
    }

    private Set<MapIdComponent> collectBundleMaps(CartographyTableScreenHandler handler) {
        Set<MapIdComponent> ids = new LinkedHashSet<>();
        if (this.sourceBundleSlot < 0) {
            return ids;
        } else {
            BundleContentsComponent contents = getBundleContents(handler, this.sourceBundleSlot);
            if (contents == null) {
                return ids;
            } else {
                for (ItemStack inner : contents.iterate()) {
                    MapIdComponent id = getMapId(inner);
                    if (id != null) {
                        ids.add(id);
                    }
                }

                return ids;
            }
        }
    }

    private Set<MapIdComponent> collectInventoryMaps(CartographyTableScreenHandler handler) {
        Set<MapIdComponent> ids = new LinkedHashSet<>();

        for (int slot = 3; slot < handler.slots.size(); slot++) {
            MapIdComponent id = getMapId(handler.getSlot(slot).getStack());
            if (id != null) {
                ids.add(id);
            }
        }

        return ids;
    }

    @Override
    public String getInfoString() {
        return this.stage == MapDuper.Stage.Done ? null : this.stage.name().toLowerCase();
    }

    private static enum Stage {
        Extract,
        Copy,
        Store,
        Done;
    }
}
