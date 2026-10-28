package musheor.modules.features;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.Deque;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import musheor.musheor;
import musheor.compat.VersionHelper;
import musheor.modules.automation.HighwayBuilder;
import musheor.modules.automation.KekNuker;
import musheor.utils.BlockRenderUtils;
import musheor.utils.manager.InventoryManager;
import musheor.utils.system.MusheorSystem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

public class KekMine extends Module {
    private static final MinecraftClient client = MinecraftClient.getInstance();
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgGrim = this.settings.createGroup("Grim");
    private final SettingGroup sgRender = this.settings.createGroup("Render");
    public final Setting<Boolean> preventToolBreaking = this.sgGeneral
        .add(new Builder().name("prevent-tool-breaking").description("Prevents you from breaking your tool items").defaultValue(true).build());
    public final Setting<Integer> minToolDurability = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("min-tool-durability")
                .description("The minimum durability threshold of a usable tool")
                .defaultValue(25)
                .sliderRange(0, 500)
                .visible(this.preventToolBreaking::get)
                .build()
        );
    private final Setting<Boolean> silentSwap = this.sgGeneral
        .add(new Builder().name("silent-swap").description("Breaks the block without holding the pickaxe").defaultValue(false).build());
    public final Setting<Integer> holdTicks = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("hold-ticks")
                .description(
                    "Ticks to keep the tool selected after a silent break before restoring. Prevents wrong-tool drops when sequenced block packets and slot packets race (0 = restore immediately)"
                )
                .defaultValue(3)
                .min(0)
                .max(10)
                .visible(this.silentSwap::get)
                .build()
        );
    private final Setting<Boolean> debug = this.sgGeneral
        .add(
            new Builder()
                .name("debug")
                .description("Logs the break packet sequence (start / burst / park / stop / confirm) to chat")
                .defaultValue(false)
                .visible(() -> false)
                .build()
        );
    public final Setting<Boolean> grimBypass = this.sgGrim
        .add(new Builder().name("grim-bypass").description("Old Grim V3 bypass, no longer works on 2b2t or other Grim V2 servers.").defaultValue(false).build());
    public final Setting<Double> breakThreshold = this.sgGrim
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("break-threshold")
                .defaultValue(0.7)
                .min(0.1)
                .max(1.0)
                .decimalPlaces(2)
                .build()
        );
    public final Setting<Integer> breakTimeout = this.sgGrim
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("break-timeout")
                .description("Time in ticks to wait before attempting to break the same block again (higher = safer for high ping)")
                .defaultValue(5)
                .sliderRange(0, 20)
                .build()
        );
    public final Setting<Boolean> doubleBreak = this.sgGrim.add(new Builder().name("double-break").defaultValue(true).build());
    public final Setting<Boolean> alternate = this.sgGrim
        .add(
            new Builder()
                .name("alternate")
                .description("Alternate between a single and a double break to trick grim's break timeout (prevents you from mine-failing).")
                .defaultValue(true)
                .visible(this.doubleBreak::get)
                .build()
        );
    public final Setting<Boolean> validateBreak = this.sgGrim
        .add(
            new Builder()
                .name("validate-break")
                .description("Waits for the server to validate the break. Disable when you have high ping (may cause rubberbanding)")
                .defaultValue(true)
                .build()
        );
    public final Setting<Boolean> autoRebreak = this.sgGrim
        .add(new Builder().name("auto-rebreak").description("Automatically rebreak the last block in case it gets replaced").defaultValue(false).build());
    private final Setting<Boolean> globalRendering = this.sgRender
        .add(new Builder().name("global-rendering").defaultValue(true).description("Synchronize rendering with Musheor-Tab").build());
    private final Setting<SettingColor> renderColor = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("color")
                .defaultValue(new SettingColor(Color.cyan))
                .description("Custom color for rendering (lines / wireframe)")
                .visible(() -> !this.globalRendering.get())
                .build()
        );
    public static KekMine INSTANCE;
    private KekMine.MineContext primaryContext;
    private KekMine.MineContext secondaryContext;
    public BlockPos lastBreakPos;
    public final Deque<BlockPos> breakQueue = new ArrayDeque<>();
    private int restoreSlot = -1;
    private int restoreTick = -1;
    private int lastSentSlot = -1;
    private int tickCounter = 0;
    private long lastBreakPacketTime = 0L;
    private boolean lastFinishWasBurst = false;
    private long secondaryCooldownEnd = 0L;
    private boolean lastDoubleWasShort = false;
    private boolean doubleBreakAllowed = true;

    public KekMine() {
        super(musheor.MAIN, "KekMine", "Grim-safe packet miner with START-offset fast break.");
        INSTANCE = this;
    }

    static float getBreakDelta(BlockState state, BlockPos pos, ItemStack tool) {
        if (client.player != null && client.world != null) {
            float hardness = state.getHardness(client.world, pos);
            int h = state.isToolRequired() && (tool == null || !tool.isSuitableFor(state)) ? 100 : 30;
            float speed = tool != null && !tool.isEmpty() ? tool.getMiningSpeedMultiplier(state) : 1.0F;
            if (speed > 1.0F) {
                int efficiency = Utils.getEnchantmentLevel(tool, Enchantments.EFFICIENCY);
                if (efficiency > 0) {
                    speed += efficiency * efficiency + 1;
                }
            }

            if (StatusEffectUtil.hasHaste(client.player)) {
                speed *= 1.0F + (StatusEffectUtil.getHasteAmplifier(client.player) + 1) * 0.2F;
            }

            if (client.player.hasStatusEffect(StatusEffects.MINING_FATIGUE)) {
                float i = switch (client.player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier()) {
                    case 0 -> 0.3F;
                    case 1 -> 0.09F;
                    case 2 -> 0.0027F;
                    default -> 8.1E-4F;
                };
                speed *= i;
            }

            speed *= (float)client.player.getAttributeValue(EntityAttributes.BLOCK_BREAK_SPEED);
            if (client.player.isSubmergedIn(FluidTags.WATER)) {
                speed *= (float)client.player.getAttributeValue(EntityAttributes.SUBMERGED_MINING_SPEED);
            }

            if (!client.player.isOnGround()) {
                speed /= 5.0F;
            }

            return speed / hardness / h;
        } else {
            return 0.0F;
        }
    }

    public static boolean canInstaMine(BlockPos pos) {
        if (client.player != null && client.world != null) {
            BlockState state = client.world.getBlockState(pos);
            return state.isAir() ? false : getBreakDelta(state, pos, findBestTool(state)) >= 1.0F;
        } else {
            return false;
        }
    }

    private static boolean canHarvest(ItemStack stack, BlockState state) {
        return !state.isToolRequired() || stack.isSuitableFor(state);
    }

    private static ItemStack findBestTool(BlockState state) {
        if (client.player == null) {
            return ItemStack.EMPTY;
        } else {
            ItemStack hand = client.player.getMainHandStack();
            ItemStack best = hand;
            float bestSpeed = canHarvest(hand, state) ? hand.getMiningSpeedMultiplier(state) : 0.0F;

            for (int i = 0; i < 36; i++) {
                ItemStack stack = client.player.getInventory().getStack(i);
                if (!stack.isEmpty() && (!INSTANCE.preventToolBreaking.get() || InventoryManager.isDurable(stack)) && canHarvest(stack, state)) {
                    float speed = stack.getMiningSpeedMultiplier(state);
                    if (speed > bestSpeed) {
                        bestSpeed = speed;
                        best = stack;
                    }
                }
            }

            return best;
        }
    }

    public static boolean isHoldingEffectiveTool(BlockState state) {
        if (client.player == null) {
            return false;
        } else {
            ItemStack hand = client.player.getMainHandStack();
            return canHarvest(hand, state) && hand.getMiningSpeedMultiplier(state) > 1.0F;
        }
    }

    private boolean isHoldingBestTool(BlockState state) {
        if (client.player == null) {
            return true;
        } else {
            ItemStack hand = client.player.getMainHandStack();
            return hand.getMiningSpeedMultiplier(state) >= findBestTool(state).getMiningSpeedMultiplier(state);
        }
    }

    public void swapToBestTool(BlockPos pos) {
        if (client.player != null && client.world != null) {
            int slot = this.findBestHotbarSlot(client.world.getBlockState(pos));
            InventoryManager.selectSlot(slot);
        }
    }

    private int getServerSideSlot() {
        if (client.player == null) {
            return 0;
        } else {
            return this.restoreSlot >= 0 && this.lastSentSlot >= 0 ? this.lastSentSlot : client.player.getInventory().selectedSlot;
        }
    }

    private boolean hasBestToolInSlot(int slot, BlockState state) {
        if (client.player != null && slot >= 0 && slot <= 8) {
            ItemStack stack = client.player.getInventory().getStack(slot);
            return stack.getMiningSpeedMultiplier(state) >= findBestTool(state).getMiningSpeedMultiplier(state);
        } else {
            return false;
        }
    }

    private void ensureBestToolSelected(KekMine.MineContext ctx) {
        if (client.player != null && ctx != null) {
            int selected = this.getServerSideSlot();
            if (this.hasBestToolInSlot(selected, ctx.state)) {
                if (this.restoreSlot >= 0) {
                    this.restoreTick = Math.max(this.restoreTick, this.tickCounter + 3);
                }
            } else {
                int realSlot = this.restoreSlot >= 0 ? this.restoreSlot : client.player.getInventory().selectedSlot;
                int bestSlot = this.findBestHotbarSlot(ctx.state);
                if (bestSlot != selected) {
                    this.sendSlot(bestSlot);
                    this.restoreSlot = realSlot;
                    this.restoreTick = this.tickCounter + 3;
                }
            }
        }
    }

    public void mineBlock(BlockPos pos) {
        if (client.world != null) {
            if (BlockUtils.canBreak(pos, client.world.getBlockState(pos))) {
                if (!this.isOutOfRange(pos)) {
                    if (!this.isMiningOrQueued(pos)) {
                        this.startMining(pos, client.world.getBlockState(pos), this.shouldSilentSwap());
                    }
                }
            }
        }
    }

    public boolean isMiningOrQueued(BlockPos pos) {
        if (this.primaryContext != null && this.primaryContext.pos.equals(pos)) {
            return true;
        } else {
            return this.secondaryContext != null && this.secondaryContext.pos.equals(pos) ? true : this.breakQueue.contains(pos);
        }
    }

    public boolean hasOnlyQueued() {
        return this.primaryContext == null && this.secondaryContext == null && !this.breakQueue.isEmpty();
    }

    public boolean isIdle() {
        return this.primaryContext == null && this.secondaryContext == null && this.breakQueue.isEmpty();
    }

    private boolean isAutomationActive() {
        return Modules.get().get(KekNuker.class).isActive() || HighwayBuilder.isEchestFarmerActive() || InventoryManager.inventoryBusy;
    }

    public boolean shouldSilentSwap() {
        return this.silentSwap.get() && !this.isAutomationActive();
    }

    private boolean canDoubleBreak() {
        return this.alternate.get() && !this.doubleBreakAllowed
            ? false
            : this.secondaryContext == null
                && System.currentTimeMillis() >= this.secondaryCooldownEnd
                && this.primaryContext != null
                && this.primaryContext.awaitingFinish
                && this.primaryContext.getProgress() < 1.0;
    }

    private void updateAlternation() {
        this.doubleBreakAllowed = !this.lastDoubleWasShort;
        this.lastDoubleWasShort = false;
    }

    private void clearSecondary(boolean confirmedBroken) {
        this.secondaryContext = null;
        this.secondaryCooldownEnd = System.currentTimeMillis() + (confirmedBroken ? 50L : 290L);
    }

    private boolean sameBestTool(BlockState a, BlockState b) {
        ItemStack ta = findBestTool(a);
        ItemStack tb = findBestTool(b);
        return ta.isEmpty() && tb.isEmpty() ? true : ta.getItem() == tb.getItem();
    }

    public static void instaMine(BlockPos pos) {
        if (pos != null && client.player != null) {
            instaMine(pos, INSTANCE.getBreakFace(pos), INSTANCE.shouldSilentSwap());
        }
    }

    public static void instaMine(BlockPos pos, Direction dir, boolean silent) {
        if (pos != null) {
            INSTANCE.doInstaMine(pos, dir, silent);
        }
    }

    private void doInstaMine(BlockPos pos, Direction dir, boolean silent) {
        if (client.world != null && client.player != null && client.interactionManager != null) {
            if (!this.isMiningOrQueued(pos)) {
                BlockState state = client.world.getBlockState(pos);
                if (!state.isAir()) {
                    if (!silent && !this.isHoldingBestTool(state)) {
                        this.swapToBestTool(pos);
                        if (!this.breakQueue.contains(pos)) {
                            this.breakQueue.addFirst(pos);
                        }
                    } else {
                        int prevSlot = this.restoreSlot >= 0 ? this.restoreSlot : client.player.getInventory().selectedSlot;
                        int bestSlot = silent ? this.findBestHotbarSlot(state) : prevSlot;
                        boolean needSwap = silent && bestSlot != prevSlot;
                        if (needSwap) {
                            this.sendSlot(bestSlot);
                        }

                        client.interactionManager.sendSequencedPacket(client.world, sequence -> {
                            this.breakClientSide(pos, state);
                            return new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, pos, dir, sequence);
                        });
                        if (needSwap) {
                            int hold = this.holdTicks.get();
                            if (hold > 0) {
                                this.restoreSlot = prevSlot;
                                this.restoreTick = this.tickCounter + hold;
                            } else {
                                this.sendSlot(prevSlot);
                                this.restoreSlot = -1;
                            }
                        }

                        HighwayBuilder.onBlockMined(state);
                        this.lastBreakPos = pos;
                        this.lastBreakPacketTime = System.currentTimeMillis();
                        this.lastFinishWasBurst = false;
                    }
                }
            }
        }
    }

    private void breakClientSide(BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        state.onBlockBreakStart(client.world, pos, client.player);
        block.onBreak(client.world, pos, state, client.player);
        FluidState fluid = client.world.getFluidState(pos);
        boolean removed = client.world.setBlockState(pos, fluid.getBlockState(), 3);
        if (removed) {
            block.onBroken(client.world, pos, state);
        }

        client.world.syncWorldEvent(2001, pos, Block.getRawIdFromState(state));
    }

    @Override
    public void onDeactivate() {
        if (this.restoreSlot >= 0) {
            this.sendSlot(this.restoreSlot);
        }

        this.primaryContext = null;
        this.secondaryContext = null;
        this.breakQueue.clear();
        this.lastBreakPos = null;
        this.restoreSlot = -1;
        this.restoreTick = -1;
        this.lastSentSlot = -1;
        this.tickCounter = 0;
        this.lastBreakPacketTime = 0L;
        this.lastFinishWasBurst = false;
        this.secondaryCooldownEnd = 0L;
        this.lastDoubleWasShort = false;
        this.doubleBreakAllowed = true;
    }

    private int findBestHotbarSlot(BlockState state) {
        if (client.player == null) {
            return 0;
        } else {
            float bestSpeed = 1.0F;
            int bestSlot = client.player.getInventory().selectedSlot;

            for (int i = 0; i < 9; i++) {
                ItemStack stack = client.player.getInventory().getStack(i);
                if ((!this.preventToolBreaking.get() || InventoryManager.isDurable(stack)) && canHarvest(stack, state)) {
                    float speed = stack.getMiningSpeedMultiplier(state);
                    if (speed > bestSpeed) {
                        bestSpeed = speed;
                        bestSlot = i;
                    }
                }
            }

            int bestInvSlot = -1;

            for (int ix = 9; ix < client.player.getInventory().main.size(); ix++) {
                ItemStack stack = client.player.getInventory().getStack(ix);
                if (!stack.isEmpty() && (!this.preventToolBreaking.get() || InventoryManager.isDurable(stack)) && canHarvest(stack, state)) {
                    float speed = stack.getMiningSpeedMultiplier(state);
                    if (speed > bestSpeed) {
                        bestSpeed = speed;
                        bestInvSlot = ix;
                    }
                }
            }

            if (bestInvSlot != -1) {
                int hotbarSlot = InventoryManager.moveStackToHotbar(client.player.getInventory().getStack(bestInvSlot));
                bestSlot = Math.max(0, Math.min(8, hotbarSlot));
            }

            return bestSlot;
        }
    }

    public void startMining(BlockPos pos, BlockState state, boolean silent) {
        this.startMining(pos, state, this.getBreakFace(pos), silent);
    }

    public void startMining(BlockPos pos, BlockState state, Direction dir, boolean silent) {
        if (!this.isMiningOrQueued(pos)) {
            if (!HighwayBuilder.isEating()) {
                if (this.primaryContext == null || this.doubleBreak.get() && this.canDoubleBreak()) {
                    if (this.primaryContext == null) {
                        if (!this.isBreakCooldownOver()) {
                            if (!this.breakQueue.contains(pos)) {
                                this.breakQueue.addLast(pos);
                            }

                            return;
                        }

                        if (!silent && !this.isHoldingBestTool(state)) {
                            this.swapToBestTool(pos);
                            if (!this.breakQueue.contains(pos)) {
                                this.breakQueue.addFirst(pos);
                            }

                            return;
                        }

                        this.primaryContext = new KekMine.MineContext(pos, state, true, silent);
                        this.updateAlternation();
                        this.beginBreak(pos, dir, this.primaryContext);
                    } else {
                        this.debugLog("PARK", this.primaryContext.pos);
                        this.sendAction(Action.STOP_DESTROY_BLOCK, this.primaryContext.pos, this.getBreakFace(this.primaryContext.pos));
                        this.secondaryContext = this.primaryContext.toSecondary();
                        this.primaryContext = new KekMine.MineContext(pos, state, true, silent);
                        if (!this.sameBestTool(this.primaryContext.state, this.secondaryContext.state)) {
                            this.primaryContext.resetTool(this.secondaryContext.tool);
                        }

                        this.lastDoubleWasShort = !this.isLongBreak(this.primaryContext);
                        this.beginBreak(this.primaryContext.pos, dir, this.primaryContext);
                    }
                } else {
                    if (!this.breakQueue.contains(pos)) {
                        this.breakQueue.addLast(pos);
                    }
                }
            }
        }
    }

    @EventHandler
    private void onTickPre(Pre event) {
        if (client.player != null && client.world != null) {
            this.tickCounter++;
            if (this.restoreSlot >= 0 && this.tickCounter >= this.restoreTick) {
                this.sendSlot(this.restoreSlot);
                this.restoreSlot = -1;
            }

            if (!HighwayBuilder.isEating() && !client.player.isUsingItem()) {
                this.processQueue();
                this.tryBurst(this.primaryContext);
                boolean toolConflict = this.primaryContext != null
                    && this.secondaryContext != null
                    && !this.sameBestTool(this.primaryContext.state, this.secondaryContext.state);
                if (this.secondaryContext != null && this.secondaryContext.getProgress() >= 1.0) {
                    this.finishBreak(this.secondaryContext);
                }

                if (this.primaryContext != null) {
                    if (toolConflict && this.secondaryContext != null) {
                        this.primaryContext.switchTool(this.secondaryContext.tool);
                        if (this.primaryContext.getProgress() >= 1.0) {
                            this.finishBreak(this.primaryContext);
                        }
                    } else if (!this.primaryContext.silent && !this.isHoldingBestTool(this.primaryContext.state)) {
                        if (this.primaryContext.getProgress() >= 1.0) {
                            this.finishBreak(this.primaryContext);
                        } else {
                            this.swapToBestTool(this.primaryContext.pos);
                        }
                    } else {
                        this.primaryContext.switchTool(findBestTool(this.primaryContext.state));
                        if (this.primaryContext.getProgress() >= 1.0) {
                            this.finishBreak(this.primaryContext);
                        }
                    }
                }

                this.pruneFinished();
                if (this.autoRebreak.get()
                    && this.lastBreakPos != null
                    && this.primaryContext == null
                    && this.secondaryContext == null
                    && this.breakQueue.isEmpty()
                    && !this.isOutOfRange(this.lastBreakPos)) {
                    this.sendRebreak(new KekMine.MineContext(this.lastBreakPos, client.world.getBlockState(this.lastBreakPos), true), this.shouldSilentSwap());
                }
            }
        }
    }

    private void pruneFinished() {
        if (this.primaryContext != null && this.isBrokenOrOutOfRange(this.primaryContext.pos)) {
            this.primaryContext = null;
        }

        if (this.secondaryContext != null && this.isBrokenOrOutOfRange(this.secondaryContext.pos)) {
            this.clearSecondary(true);
        }

        this.breakQueue.removeIf(this::isBrokenOrOutOfRange);
    }

    private boolean isBrokenOrOutOfRange(BlockPos pos) {
        BlockState state = client.world.getBlockState(pos);
        return state.isAir() || this.isOutOfRange(pos);
    }

    private void processQueue() {
        if (!this.breakQueue.isEmpty()) {
            if (this.primaryContext == null) {
                if (!this.isBreakCooldownOver()) {
                    return;
                }

                BlockPos pos = this.breakQueue.pollFirst();
                BlockState state = client.world.getBlockState(pos);
                boolean silent = this.shouldSilentSwap();
                if (!silent && !this.isHoldingBestTool(state)) {
                    this.swapToBestTool(pos);
                    this.breakQueue.addFirst(pos);
                    return;
                }

                this.primaryContext = new KekMine.MineContext(pos, state, true, silent);
                this.updateAlternation();
                this.beginBreak(this.primaryContext.pos, this.getBreakFace(this.primaryContext.pos), this.primaryContext);
            }

            if (!this.breakQueue.isEmpty() && this.doubleBreak.get() && this.canDoubleBreak()) {
                this.debugLog("PARK", this.primaryContext.pos);
                this.sendAction(Action.STOP_DESTROY_BLOCK, this.primaryContext.pos, this.getBreakFace(this.primaryContext.pos));
                BlockPos nextPos = this.breakQueue.pollFirst();
                BlockState nextState = client.world.getBlockState(nextPos);
                this.secondaryContext = this.primaryContext.toSecondary();
                this.primaryContext = new KekMine.MineContext(nextPos, nextState, true, this.shouldSilentSwap());
                if (!this.sameBestTool(this.primaryContext.state, this.secondaryContext.state)) {
                    this.primaryContext.resetTool(this.secondaryContext.tool);
                }

                this.lastDoubleWasShort = !this.isLongBreak(this.primaryContext);
                this.beginBreak(this.primaryContext.pos, this.getBreakFace(this.primaryContext.pos), this.primaryContext);
            }
        }
    }

    private void sendStart(BlockPos pos, Direction dir) {
        if (this.grimBypass.get()) {
            this.sendAction(Action.STOP_DESTROY_BLOCK, pos, dir);
        }

        this.sendAction(Action.START_DESTROY_BLOCK, pos, dir);
    }

    private void sendOffsetStart(BlockPos pos, Direction dir) {
        this.sendAction(Action.START_DESTROY_BLOCK, new BlockPos(pos.getX(), 1337, pos.getZ()), dir);
    }

    private void sendStop(BlockPos pos, Direction dir) {
        this.sendAction(Action.STOP_DESTROY_BLOCK, pos, dir);
        this.lastBreakPacketTime = System.currentTimeMillis();
    }

    private void tryBurst(KekMine.MineContext ctx) {
        if (this.shouldBurst(ctx) && System.currentTimeMillis() - ctx.startTime > 290L) {
            this.sendBurst(ctx);
        }
    }

    private void debugLog(String what, BlockPos pos) {
        if (this.debug.get()) {
            this.info(
                "Tick: %d %s %s [PRIM=%s SEC=%s Q=%d]"
                    .formatted(
                        this.tickCounter,
                        what,
                        pos == null ? "-" : pos.toShortString(),
                        this.primaryContext == null ? "-" : this.primaryContext.pos.toShortString() + (this.primaryContext.burstSent ? "*" : ""),
                        this.secondaryContext == null ? "-" : this.secondaryContext.pos.toShortString() + (this.secondaryContext.burstSent ? "*" : ""),
                        this.breakQueue.size()
                    ),
                new Object[0]
            );
        }
    }

    private boolean shouldBurst(KekMine.MineContext ctx) {
        return ctx != null && !ctx.burstSent && this.isLongBreak(ctx);
    }

    private boolean isLongBreak(KekMine.MineContext ctx) {
        return ctx != null && !ctx.instant ? ctx.getBreakTimeMs() >= 290L : false;
    }

    private void sendBurst(KekMine.MineContext ctx) {
        if (ctx != null && client.player != null) {
            Direction dir = KekNuker.getClosestFace(client.player.getEyePos(), ctx.pos);

            for (int i = 0; i < 22; i++) {
                this.sendOffsetStart(ctx.pos, dir);
            }

            ctx.burstSent = true;
            this.debugLog("BURST", ctx.pos);
        }
    }

    private boolean isBreakCooldownOver() {
        return System.currentTimeMillis() - this.lastBreakPacketTime > 275L;
    }

    private void beginBreak(BlockPos pos, Direction dir, KekMine.MineContext ctx) {
        this.debugLog("START", pos);
        if (ctx.silent && ctx.instant && !ctx.instantWithMainHand) {
            int prevSlot = this.restoreSlot >= 0 ? this.restoreSlot : client.player.getInventory().selectedSlot;
            int bestSlot = this.findBestHotbarSlot(ctx.state);
            boolean needSwap = bestSlot != prevSlot;
            if (needSwap) {
                this.sendSlot(bestSlot);
            }

            this.sendStart(pos, dir);
            if (needSwap) {
                int hold = this.holdTicks.get();
                if (hold > 0) {
                    this.restoreSlot = prevSlot;
                    this.restoreTick = this.tickCounter + hold;
                } else {
                    this.sendSlot(prevSlot);
                    this.restoreSlot = -1;
                }
            }
        } else {
            this.sendStart(pos, dir);
            if (!ctx.instant && !ctx.offsetStartSent) {
                this.sendOffsetStart(pos, dir);
                ctx.offsetStartSent = true;
            }
        }
    }

    private void sendRebreak(KekMine.MineContext ctx, boolean silent) {
        if (client.world != null && client.player != null) {
            Direction dir = this.getBreakFace(ctx.pos);
            int prevSlot = this.restoreSlot >= 0 ? this.restoreSlot : client.player.getInventory().selectedSlot;
            int bestSlot = silent ? this.findBestHotbarSlot(ctx.state) : prevSlot;
            boolean needSwap = silent && bestSlot != prevSlot;
            if (needSwap) {
                this.sendSlot(bestSlot);
            }

            this.sendAction(Action.STOP_DESTROY_BLOCK, ctx.pos, dir);
            if (!ctx.state.isAir()) {
                this.lastBreakPacketTime = System.currentTimeMillis();
            }

            if (needSwap) {
                int hold = this.holdTicks.get();
                if (hold > 0) {
                    this.restoreSlot = prevSlot;
                    this.restoreTick = this.tickCounter + hold;
                } else {
                    this.sendSlot(prevSlot);
                    this.restoreSlot = -1;
                }
            }

            if (!this.validateBreak.get() && !ctx.state.isAir()) {
                client.world.syncWorldEvent(2001, ctx.pos, Block.getRawIdFromState(ctx.state));
                client.world.setBlockState(ctx.pos, Blocks.AIR.getDefaultState(), 3);
            }
        }
    }

    private void finishBreak(KekMine.MineContext ctx) {
        if (client.world != null && client.player != null) {
            if (ctx.awaitingFinish) {
                ctx.awaitingFinish = false;
                ctx.finishTick = this.tickCounter;
                this.lastFinishWasBurst = ctx.burstSent;
                this.debugLog(ctx.primary ? "FINISH-P" : "FINISH-S", ctx.pos);
                Direction face = this.getBreakFace(ctx.pos);
                if (!ctx.primary) {
                    if (ctx.silent) {
                        this.ensureBestToolSelected(ctx);
                    }

                    this.lastBreakPacketTime = System.currentTimeMillis();
                } else if (ctx.silent && ctx.instant) {
                    this.lastBreakPacketTime = System.currentTimeMillis();
                } else if (ctx.silent) {
                    int prevSlot = this.restoreSlot >= 0 ? this.restoreSlot : client.player.getInventory().selectedSlot;
                    int bestSlot = this.findBestHotbarSlot(ctx.state);
                    boolean needSwap = bestSlot != prevSlot;
                    if (needSwap) {
                        this.sendSlot(bestSlot);
                    }

                    this.sendStop(ctx.pos, face);
                    if (needSwap) {
                        int hold = this.holdTicks.get();
                        if (hold > 0) {
                            this.restoreSlot = prevSlot;
                            this.restoreTick = this.tickCounter + hold;
                        } else {
                            this.sendSlot(prevSlot);
                            this.restoreSlot = -1;
                        }
                    }
                } else {
                    if (client.player.getMainHandStack().getItem() != ctx.tool.getItem()) {
                        this.swapToBestTool(ctx.pos);
                    }

                    if (!ctx.instant) {
                        this.sendStop(ctx.pos, face);
                    } else {
                        this.lastBreakPacketTime = System.currentTimeMillis();
                    }
                }

                if (ctx.instant && !this.validateBreak.get()) {
                    client.world.syncWorldEvent(2001, ctx.pos, Block.getRawIdFromState(ctx.state));
                    client.world.setBlockState(ctx.pos, Blocks.AIR.getDefaultState(), 3);
                }

                this.lastBreakPos = ctx.pos;
            }

            boolean broke = this.isBrokenOrOutOfRange(ctx.pos);
            if (!broke && !ctx.primary && ctx.silent) {
                this.ensureBestToolSelected(ctx);
            }

            int timeout = ctx.primary ? 5 : 10;
            if (broke || this.tickCounter - ctx.finishTick >= timeout) {
                this.debugLog(broke ? "CONFIRMED" : "TIMEOUT", ctx.pos);
                if (ctx == this.primaryContext) {
                    this.primaryContext = null;
                } else if (ctx == this.secondaryContext) {
                    this.clearSecondary(broke);
                }

                if (broke) {
                    BlockSoundGroup sg = ctx.state.getSoundGroup();
                    VersionHelper.get()
                        .playSoundAtBlockCenter(ctx.pos, sg.getBreakSound(), SoundCategory.BLOCKS, (sg.getVolume() + 1.0F) / 2.0F, sg.getPitch() * 0.8F);
                    HighwayBuilder.onBlockMined(ctx.state);
                }
            }
        }
    }

    private void sendSlot(int slot) {
        if (client.getNetworkHandler() != null) {
            this.lastSentSlot = slot;
            client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }
    }

    public void sendAction(Action action, BlockPos pos) {
        this.sendAction(action, pos, this.getBreakFace(pos));
    }

    public Direction getBreakFace(BlockPos pos) {
        if (client.player != null && client.world != null) {
            Vec3d eye = client.player.getEyePos();
            Direction best = null;
            double bestDist = Double.MAX_VALUE;

            for (Direction d : Direction.values()) {
                Vec3d faceCenter = Vec3d.ofCenter(pos).add(d.getOffsetX() * 0.5, d.getOffsetY() * 0.5, d.getOffsetZ() * 0.5);
                double dist = eye.squaredDistanceTo(faceCenter);
                if (!(dist >= bestDist) && this.isFaceVisible(eye, pos, d, faceCenter)) {
                    bestDist = dist;
                    best = d;
                }
            }

            return best != null ? best : KekNuker.getClosestFace(eye, pos);
        } else {
            return Direction.UP;
        }
    }

    private boolean isFaceVisible(Vec3d eye, BlockPos pos, Direction d, Vec3d faceCenter) {
        BlockHitResult hit = client.world.raycast(new RaycastContext(eye, faceCenter, ShapeType.OUTLINE, FluidHandling.NONE, client.player));
        return hit != null && hit.getType() == Type.BLOCK && hit.getBlockPos().equals(pos) && hit.getSide() == d;
    }

    public void sendAction(Action action, BlockPos pos, Direction dir) {
        if (client.interactionManager != null && client.world != null) {
            client.interactionManager.sendSequencedPacket(client.world, sequence -> new PlayerActionC2SPacket(action, pos, dir, sequence));
        }
    }

    public boolean isOutOfRange(BlockPos pos) {
        return pos == null ? false : client.player.getEyePos().distanceTo(pos.toCenterPos()) > Modules.get().get(KekNuker.class).range.get() + 1.0;
    }

    public BlockPos getPrimaryPos() {
        return this.primaryContext != null ? this.primaryContext.pos : null;
    }

    public BlockPos getSecondaryPos() {
        return this.secondaryContext != null ? this.secondaryContext.pos : null;
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (client.player != null && client.world != null) {
            if (!Modules.get().get(KekNuker.class).isActive()) {
                if (this.globalRendering.get()) {
                    BlockRenderUtils.renderPositions(event, this.breakQueue.stream().toList());
                } else {
                    BlockRenderUtils.renderPositions(
                        event,
                        this.breakQueue.stream().toList(),
                        meteordevelopment.meteorclient.utils.render.color.Color.WHITE,
                        meteordevelopment.meteorclient.utils.render.color.Color.WHITE,
                        ShapeMode.Lines
                    );
                }
            }

            if (this.secondaryContext != null) {
                if (this.globalRendering.get()) {
                    this.renderProgress(
                        event,
                        this.secondaryContext,
                        MusheorSystem.Manager.renderSideColor.get(),
                        MusheorSystem.Manager.renderLineColor.get(),
                        MusheorSystem.Manager.renderShape.get()
                    );
                } else {
                    this.renderProgress(event, this.secondaryContext, this.renderColor.get(), this.renderColor.get(), ShapeMode.Lines);
                }
            }

            if (this.primaryContext != null) {
                if (this.globalRendering.get()) {
                    this.renderProgress(
                        event,
                        this.primaryContext,
                        MusheorSystem.Manager.renderSideColor.get(),
                        MusheorSystem.Manager.renderLineColor.get(),
                        MusheorSystem.Manager.renderShape.get()
                    );
                } else {
                    this.renderProgress(event, this.primaryContext, this.renderColor.get(), this.renderColor.get(), ShapeMode.Lines);
                }
            }

            if (this.autoRebreak.get() && this.lastBreakPos != null) {
                if (this.globalRendering.get()) {
                    BlockRenderUtils.renderPosition(event, this.lastBreakPos, client.world.getBlockState(this.lastBreakPos).getBlock());
                } else {
                    BlockRenderUtils.renderPosition(event, this.lastBreakPos, this.renderColor.get(), this.renderColor.get(), ShapeMode.Lines);
                }
            }
        }
    }

    private void renderProgress(
        Render3DEvent event,
        KekMine.MineContext ctx,
        meteordevelopment.meteorclient.utils.render.color.Color side,
        meteordevelopment.meteorclient.utils.render.color.Color line,
        ShapeMode shape
    ) {
        double offset = (1.0 - ctx.getProgress()) / 2.0;
        Box box = new Box(
            ctx.pos.getX() + offset,
            ctx.pos.getY() + offset,
            ctx.pos.getZ() + offset,
            ctx.pos.getX() + 1.0 - offset,
            ctx.pos.getY() + 1.0 - offset,
            ctx.pos.getZ() + 1.0 - offset
        );
        event.renderer.box(box, side, line, shape, 0);
    }

    public static class MineContext {
        public final BlockPos pos;
        public final BlockState state;
        public long startTime;
        public final float hardness;
        public boolean awaitingFinish = true;
        public int finishTick = -1;
        public final boolean primary;
        public final boolean silent;
        public boolean offsetStartSent = false;
        public boolean burstSent = false;
        public ItemStack tool;
        public boolean instant;
        public final boolean instantWithMainHand;
        public final MinecraftClient client = MinecraftClient.getInstance();

        public MineContext(BlockPos pos, BlockState state, boolean isPrimary) {
            this(pos, state, isPrimary, false);
        }

        public MineContext(BlockPos pos, BlockState state, boolean isPrimary, boolean silent) {
            this.pos = pos.toImmutable();
            this.state = state;
            this.hardness = state.getHardness(this.client.world, pos);
            this.primary = isPrimary;
            this.silent = silent;
            this.startTime = System.currentTimeMillis();
            this.tool = KekMine.findBestTool(state);
            this.updateInstant();
            this.instantWithMainHand = this.getBreakDelta(this.client.player.getMainHandStack()) >= 1.0F;
        }

        public KekMine.MineContext toSecondary() {
            KekMine.MineContext s = new KekMine.MineContext(this.pos, this.state, false, this.silent);
            s.startTime = System.currentTimeMillis();
            s.offsetStartSent = this.offsetStartSent;
            s.burstSent = this.burstSent;
            s.tool = this.tool;
            s.updateInstant();
            return s;
        }

        private void updateInstant() {
            this.instant = this.getBreakDelta(this.tool) >= 1.0F;
        }

        public void switchTool(ItemStack tool) {
            if (tool != null && (this.tool == null || this.tool.getItem() != tool.getItem())) {
                float oldPerTick = this.getBreakDelta(this.tool);
                float newPerTick = this.getBreakDelta(tool);
                if (Float.isFinite(oldPerTick) && oldPerTick > 0.0F && Float.isFinite(newPerTick) && newPerTick > 0.0F) {
                    float elapsedTicks = Math.max((float)(System.currentTimeMillis() - this.startTime) / 50.0F + 1.0F, 1.0F);
                    float rawProgress = oldPerTick * elapsedTicks;
                    float newElapsed = rawProgress / newPerTick;
                    this.startTime = System.currentTimeMillis() - (long)((newElapsed - 1.0F) * 50.0F);
                }

                this.tool = tool;
                this.updateInstant();
            }
        }

        public void resetTool(ItemStack tool) {
            if (tool != null) {
                this.tool = tool;
                this.startTime = System.currentTimeMillis();
                this.updateInstant();
            }
        }

        private float currentBreakDelta() {
            return this.getBreakDelta(this.tool);
        }

        private float getBreakDelta(ItemStack bestTool) {
            return KekMine.getBreakDelta(this.state, this.pos, bestTool);
        }

        private double getProgress() {
            if (this.client.player != null && this.client.world != null && !(this.hardness < 0.0F)) {
                float perTick = this.currentBreakDelta();
                if (perTick <= 0.0F) {
                    return 2.147483647E9;
                } else {
                    float elapsedTicks = Math.max((float)(System.currentTimeMillis() - this.startTime - 50L) / 50.0F + 1.0F, 1.0F);
                    float currentProgress = perTick * elapsedTicks;
                    float targetProgress = this.primary ? KekMine.INSTANCE.breakThreshold.get().floatValue() : 1.0F;
                    return Math.min((double)(currentProgress / targetProgress), 1.0);
                }
            } else {
                return 0.0;
            }
        }

        public float getBreakDelta() {
            return this.getBreakDelta(this.tool);
        }

        public long getBreakTimeMs() {
            if (this.client.player != null && this.client.world != null) {
                float perTick = this.currentBreakDelta();
                if (perTick <= 0.0F) {
                    return Long.MAX_VALUE;
                } else {
                    float target = this.primary ? KekMine.INSTANCE.breakThreshold.get().floatValue() : 1.0F;
                    float ticks = target / perTick;
                    return (long)(Math.max(ticks - 1.0F, 0.0F) * 50.0F);
                }
            } else {
                return Long.MAX_VALUE;
            }
        }
    }
}
