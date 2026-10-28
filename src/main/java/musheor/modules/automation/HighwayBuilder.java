package musheor.modules.automation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.systems.modules.movement.AutoWalk;
import meteordevelopment.meteorclient.systems.modules.movement.AutoWalk.Direction;
import meteordevelopment.meteorclient.systems.modules.player.AutoEat;
import meteordevelopment.meteorclient.systems.modules.player.AutoGap;
import meteordevelopment.meteorclient.systems.modules.render.FreeLook;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import musheor.musheor;
import musheor.compat.VersionHelper;
import musheor.utils.BlockRenderUtils;
import musheor.utils.DiscordRpc;
import musheor.utils.HighwayBuilderLogic;
import musheor.utils.HighwayPositions;
import musheor.utils.HighwayUtils;
import musheor.utils.PlayerControlUtils;
import musheor.utils.baritone.BaritoneHelper;
import musheor.utils.highway.EchestSlotBlacklist;
import musheor.utils.highway.HighwayDetector;
import musheor.utils.highway.HighwayState;
import musheor.utils.hud.screen.EchestConfigScreen;
import musheor.utils.manager.InventoryManager;
import musheor.utils.system.MusheorSystem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

public class HighwayBuilder extends Module {
    private final MinecraftClient client = MinecraftClient.getInstance();
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgPlacement = this.settings.createGroup("Placement");
    private final SettingGroup sgRestocking = this.settings.createGroup("Restocking");
    private final SettingGroup sgAutoEat = this.settings.createGroup("Auto Eat");
    private final SettingGroup sgNuker = this.settings.createGroup("Nuker");
    private final SettingGroup sgKillAura = this.settings.createGroup("Kill Aura");
    private final SettingGroup sgSafety = this.settings.createGroup("Safety");
    private final SettingGroup sgInventory = this.settings.createGroup("Inventory");
    private final SettingGroup sgMiscellaneous = this.settings.createGroup("Miscellaneous");
    private final Setting<HighwayBuilder.BuildMode> buildMode = this.sgGeneral
        .add(
            new Builder<HighwayBuilder.BuildMode>().name("build-mode").description("Pave mode places blocks - Dig mode digs tunnels.")
                    .defaultValue(HighwayBuilder.BuildMode.Pave)
                .build()
        );
    public final Setting<HighwayBuilder.Mode> mode = this.sgGeneral
        .add(
            new Builder<HighwayBuilder.Mode>().name("mode")
                        .description(
                            "Which mode to run the highwaybuilder in; Auto = fully automatic alignment, width, etc... - Semi = Chosen highway type and other parameters - Manual = full manual control"
                        )
                    .defaultValue(HighwayBuilder.Mode.Auto)
                .build()
        );
    private final Setting<HighwayBuilder.HighwayType> highwayType = this.sgGeneral
        .add(
            new Builder<HighwayBuilder.HighwayType>().name("highway-type").description("Cardinal or diagonal highway type.")
                        .defaultValue(HighwayBuilder.HighwayType.Cardinal)
                    .visible(() -> this.mode.get() != HighwayBuilder.Mode.Auto)
                .build()
        );
    private final Setting<Integer> pavementWidth = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("pavement-width")
                .description("Width of the pavement below the player's feet.")
                .defaultValue(4)
                .sliderRange(3, 9)
                .visible(() -> this.mode.get() != HighwayBuilder.Mode.Auto)
                .build()
        );
    private final Setting<Boolean> autoBounce = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("auto-bounce")
                .description("Automatically start bouncing when no blockages or missing blocks can be found ahead")
                .defaultValue(true)
                .visible(() -> this.mode.get() != HighwayBuilder.Mode.Manual)
                .build()
        );
    private final Setting<Integer> bounceDistanceCheck = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("bounce-distance-check")
                .description("How many blocks to scan ahead and check before allowing the paver to start bouncing")
                .defaultValue(24)
                .sliderRange(1, 64)
                .visible(() -> this.mode.get() != HighwayBuilder.Mode.Manual && this.autoBounce.get())
                .build()
        );
    private final Setting<Block> pavementBlock = this.sgPlacement
        .add(
            new meteordevelopment.meteorclient.settings.BlockSetting.Builder()
                .name("pavement-block")
                .description("Block that is used to build highway pavement with.")
                .defaultValue(Blocks.OBSIDIAN)
                .visible(() -> this.buildMode.get() == HighwayBuilder.BuildMode.Pave)
                .build()
        );
    private final Setting<HighwayBuilder.ScaffoldMode> scaffoldMode = this.sgPlacement
        .add(
            new Builder<HighwayBuilder.ScaffoldMode>().name("scaffold-mode")
                            .description("What type of scaffolding to use when going over caves and open area's.")
                        .defaultValue(HighwayBuilder.ScaffoldMode.Simple)
                    .visible(() -> this.buildMode.get() == HighwayBuilder.BuildMode.Tunnel)
                .build()
        );
    private final Setting<Block> scaffoldBlock = this.sgPlacement
        .add(
            new meteordevelopment.meteorclient.settings.BlockSetting.Builder()
                .name("scaffold-block")
                .description("Block that is used to fix the flooring with.")
                .defaultValue(Blocks.NETHERRACK)
                .visible(() -> this.buildMode.get() == HighwayBuilder.BuildMode.Tunnel && this.scaffoldMode.get() != HighwayBuilder.ScaffoldMode.Disabled)
                .build()
        );
    private final Setting<Boolean> scaffoldLeftRail = this.sgPlacement
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("scaffold-left-rail")
                .description("Places the railing on the left side of the player.")
                .defaultValue(true)
                .visible(() -> this.buildMode.get() == HighwayBuilder.BuildMode.Tunnel)
                .build()
        );
    private final Setting<Boolean> scaffoldRightRail = this.sgPlacement
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("scaffold-right-rail")
                .description("Places the railing on the right side of the player.")
                .defaultValue(true)
                .visible(() -> this.buildMode.get() == HighwayBuilder.BuildMode.Tunnel)
                .build()
        );
    private final Setting<Boolean> placeRails = this.sgPlacement
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("place-rails")
                .description("Places railings on both sides of the highway 1 block above the pavement.")
                .defaultValue(true)
                .visible(() -> this.buildMode.get() == HighwayBuilder.BuildMode.Pave && this.mode.get() != HighwayBuilder.Mode.Auto)
                .build()
        );
    private final Setting<Boolean> placeLeftRail = this.sgPlacement
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("place-left-rail")
                .description("Places railings on the left side sides of the highway 1 block above the pavement.")
                .defaultValue(true)
                .visible(() -> this.buildMode.get() == HighwayBuilder.BuildMode.Pave && this.placeRails.get() && this.mode.get() != HighwayBuilder.Mode.Auto)
                .build()
        );
    private final Setting<Boolean> placeRightRail = this.sgPlacement
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("place-right-rail")
                .description("Places railings on the right sides of the highway 1 block above the pavement.")
                .defaultValue(true)
                .visible(() -> this.buildMode.get() == HighwayBuilder.BuildMode.Pave && this.placeRails.get() && this.mode.get() != HighwayBuilder.Mode.Auto)
                .build()
        );
    private final Setting<Boolean> replaceCryingObsidian = this.sgPlacement
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("replace-crying-obsidian")
                .description("Mines and replaces crying obsidian if part of the pavement positions.")
                .defaultValue(false)
                .visible(() -> this.buildMode.get() == HighwayBuilder.BuildMode.Pave)
                .build()
        );
    private final Setting<Boolean> fillCeiling = this.sgPlacement
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("fill-ceiling")
                .description("Fills the ceiling at Y level 123 with blocks.")
                .defaultValue(false)
                .visible(() -> this.buildMode.get() == HighwayBuilder.BuildMode.Pave)
                .build()
        );
    private final Setting<Boolean> echestFarming = this.sgRestocking
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("echest-farming")
                .description("Allow the player to farm obsidian by mining enderchests.")
                .defaultValue(true)
                .visible(() -> this.buildMode.get() == HighwayBuilder.BuildMode.Pave && this.pavementBlock.get() == Blocks.OBSIDIAN)
                .build()
        );
    private final Setting<Boolean> enableItemRestocking = this.sgRestocking
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("enable-item-restocking")
                .description("Allow the restocking process to grab items from shulkers.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> swapBrokenPickaxes = this.sgRestocking
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("swap-broken-pickaxes")
                .description("Swaps broken pickaxes with new ones when restocking these, allowing you to repair them later.")
                .defaultValue(true)
                .visible(this.enableItemRestocking::get)
                .build()
        );
    private final Setting<Boolean> shulkerRestocking = this.sgRestocking
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("shulker-restocking")
                .description("Allow the restocking process to grab shulkers from the player's enderchest.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> materialShulkerRestocking = this.sgRestocking
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("material-shulker-restocking")
                .description("Allow the restocking process to grab shulkers with pavement material from the player's enderchest.")
                .defaultValue(false)
                .visible(this.shulkerRestocking::get)
                .build()
        );
    private final Setting<Boolean> toolShulkerRestocking = this.sgRestocking
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("tool-shulker-restocking")
                .description("Allow the restocking process to grab tool shulkers from the player's enderchest.")
                .defaultValue(false)
                .visible(this.shulkerRestocking::get)
                .build()
        );
    private final Setting<Boolean> toggleAutoEat = this.sgAutoEat
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("toggle-auto-eat")
                .description("Pauses the current task and automatically eats.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> toggleAutoGap = this.sgAutoEat
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("toggle-auto-gap")
                .description("Allow the player to eat golden apples when required")
                .defaultValue(true)
                .build()
        );
    public final Setting<Boolean> toggleKekNuker = this.sgNuker
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("toggle-kek-nuker")
                .description("Allows the paver to use the nuker module.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> mineAboveRails = this.sgNuker
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("mine-above-rails")
                .description("Cleans and removes blocks above rails")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> toggleKillAura = this.sgKillAura
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("toggle-kill-aura")
                .description("Automatically attack nearby hostile entities.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> disconnectIfNoMaterials = this.sgSafety
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("disconnect-if-no-materials")
                .description("Automatically disconnect when materials are low (less than 8 Obsidian/E-Chests).")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> toggleSourceFiller = this.sgSafety
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("toggle-source-filler")
                .description("Automatically remove lava sources when you can reach them.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> advancedSourceFiller = this.sgSafety
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("advanced-source-filler")
                .description("Uses baritone to path to lava sources ahead of the player. Not recommended.")
                .defaultValue(false)
                .visible(null)
                .build()
        );
    private final Setting<Boolean> toggleAutoReplenish = this.sgInventory
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("toggle-auto-replenish")
                .description("Automatically move items from inventory to hotbar when slots are empty.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> toggleInventoryCleaner = this.sgInventory
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("toggle-inventory-cleaner")
                .description("Allow the paver to dispose of unwanted items.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> discordRpc = this.sgMiscellaneous
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("discord-rpc")
                .description("Send paver stats to discord activity.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> enableFreelook = this.sgMiscellaneous
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("enable-freelook")
                .description("Freelook when using the paver.")
                .defaultValue(false)
                .build()
        );
    public static HighwayBuilder INSTANCE;
    private boolean needsInit = false;
    private int restockAttempts = 0;

    public HighwayBuilder() {
        super(musheor.AUTOMATION, "HighwayBuilder", "Automated highway builder for 2b2t.");
        INSTANCE = this;
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();
        list.add(theme.button("Configure E-Chest slots")).widget().action = () -> this.client.setScreen(new EchestConfigScreen(this.client.currentScreen));
        return list;
    }

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = super.toTag();
        tag.putInt("echestSlotBlacklist", EchestSlotBlacklist.toMask());
        return tag;
    }

    @Override
    public Module fromTag(NbtCompound tag) {
        Module module = super.fromTag(tag);
        EchestSlotBlacklist.loadMask(VersionHelper.get().getInt(tag, "echestSlotBlacklist", 0));
        return module;
    }

    @Override
    public void onActivate() {
        this.needsInit = true;
        this.restockAttempts = 0;
        HighwayState.getInstance().setRuntimeTicks(0);
    }

    private void initializeRun() {
        HighwayState state = HighwayState.getInstance();
        state.reset();
        boolean noPavementBlockPresent = this.pavementBlock.get() == Blocks.OBSIDIAN && InventoryManager.countItem(getPavementBlock().asItem()) < 1;
        boolean noPickaxePresent = InventoryManager.countPickaxes(false) < 1;
        if (this.buildMode.get() != HighwayBuilder.BuildMode.Pave || !noPavementBlockPresent && !noPickaxePresent) {
            boolean noNonSilkPickaxePresent = InventoryManager.countPickaxes(true) < 1;
            if (this.buildMode.get() == HighwayBuilder.BuildMode.Tunnel) {
                if (noNonSilkPickaxePresent) {
                    this.error("No pickaxe found in inventory...!", new Object[0]);
                }

                PlayerControlUtils.disableHighwayBuilder();
            } else {
                this.needsInit = false;
                state.setStartPos(this.client.player.getBlockPos());
                state.setRuntimeTicks(0);
                InventoryManager.resetRestock(false);
                if (!this.initHighwayState()) {
                    PlayerControlUtils.disableHighwayBuilder();
                } else {
                    PlayerControlUtils.setModuleSetting(KekNuker.class, "nuker-mode", KekNuker.NukerMode.Highway);
                    PlayerControlUtils.setModuleSetting(KekNuker.class, "range", 5.5);
                    this.enableHelperModules();
                    state.getStartPositions().clear();
                    state.getStartPositions().add(this.client.player.getBlockPos());
                    state.getPendingPlacements().clear();
                    PlayerControlUtils.setModuleSetting(AutoWalk.class, "mode", AutoWalk.Mode.Simple);
                    PlayerControlUtils.setModuleSetting(AutoWalk.class, "simple-direction", Direction.Forwards);
                }
            }
        } else {
            if (noPavementBlockPresent) {
                this.error("No pavement material found in inventory...", new Object[0]);
            }

            if (noPickaxePresent) {
                this.error("No non-silk touch pickaxe found in inventory...!", new Object[0]);
            }

            PlayerControlUtils.disableHighwayBuilder();
        }
    }

    @Override
    public void onDeactivate() {
        this.needsInit = false;
        HighwayBuilderLogic.reset();
        PlayerControlUtils.setAutoWalk(false);
        BaritoneHelper.cancelAll();
        disableHelperModules();
        DiscordRpc.stop();
        HighwayState.getInstance().reset();
        this.info("Deactivated.", new Object[0]);
    }

    @EventHandler
    public void onTick(Pre event) {
        HighwayState state = HighwayState.getInstance();
        if (this.client.player == null || this.client.world == null || this.client.interactionManager == null) {
            state.setRuntimeTicks(0);
        } else if (this.client.player.isSpectator()) {
            this.info("Player is likely in queue, waiting to proceed with highwaybuilder state checks...", new Object[0]);
            state.setRuntimeTicks(0);
        } else if (this.needsInit) {
            this.initializeRun();
        } else {
            state.incrementRuntimeTicks();
            HighwayUtils.clearExpiredPlacements(state.getRuntimeTicks(), getPavementBlock());
            if (state.getLavaPos() != null) {
                PlayerControlUtils.setAutoWalk(false);
            }

            state.incrementRpcUpdateTicks();
            if (state.getRpcUpdateTicks() >= 400) {
                state.setRpcUpdateTicks(0);
                if (this.discordRpc.get()) {
                    DiscordRpc.update();
                } else {
                    DiscordRpc.stop();
                }
            }

            Module BetterEChestFarmer = Modules.get().get(EchestFarmer.class);
            boolean isGapEating = Modules.get().get(AutoGap.class).isEating();
            if (!isEating()
                && !isGapEating
                && !isEchestFarmerActive()
                && !isKillAuraAttacking()
                && !PlayerControlUtils.isGatheringItem()
                && !state.isEchestFarming()) {
                int enderChestCount = this.pavementBlock.get() == Blocks.OBSIDIAN ? InventoryManager.countItem(Items.ENDER_CHEST) : 64;
                int obsidianCount = InventoryManager.countItem(this.pavementBlock.get().asItem());
                state.setNonSilkPickaxeCount(InventoryManager.countPickaxes(false));
                state.setPickaxeCount(InventoryManager.countPickaxes(true));
                if (this.buildMode.get() == HighwayBuilder.BuildMode.Pave) {
                    state.setOutOfMaterials(enderChestCount <= 8 && obsidianCount <= 8 || state.getNonSilkPickaxeCount() < 1 || !hasFood());
                }

                if (this.buildMode.get() == HighwayBuilder.BuildMode.Tunnel) {
                    state.setOutOfMaterials(state.getPickaxeCount() < 1 || !hasFood());
                }

                if (state.isPostRestocking()) {
                    InventoryManager.postRestock();
                } else if (state.isEchestRestocking()) {
                    InventoryManager.restockFromEchest(state.getRestockType(), InventoryManager.restockAmount);
                    MusheorSystem.debug("Restocking shulker from echest %s; Amount: %s", state.getRestockType(), InventoryManager.restockAmount);
                } else if (state.isShulkerRestocking()) {
                    InventoryManager.restockFromShulker(state.getRestockType(), InventoryManager.restockAmount);
                    MusheorSystem.debug("Restocking %s; Amount: %s", state.getRestockType(), InventoryManager.restockAmount);
                } else if (state.isPostEchestFarming() && !BetterEChestFarmer.isActive() && this.echestFarming.get()) {
                    HighwayBuilderLogic.finishEchestFarming();
                    MusheorSystem.debug("Running post-echest farmer...");
                } else if (this.shouldFarmEchests(obsidianCount, enderChestCount)
                    && this.echestFarming.get()
                    && this.buildMode.get() == HighwayBuilder.BuildMode.Pave) {
                    HighwayBuilderLogic.startEchestFarming();
                    MusheorSystem.debug("Running echest farmer...");
                } else if (!state.isOutOfMaterials()) {
                    this.restockAttempts = 0;
                    if (this.buildMode.get() == HighwayBuilder.BuildMode.Pave) {
                        if (this.mode.get() == HighwayBuilder.Mode.Auto) {
                            HighwayBuilderLogic.tickAutoPave();
                        } else {
                            switch ((HighwayBuilder.HighwayType)this.highwayType.get()) {
                                case Cardinal:
                                    HighwayBuilderLogic.tickCardinalPave();
                                    break;
                                case Diagonal:
                                    HighwayBuilderLogic.tickDiagonalPave();
                            }
                        }
                    }

                    if (this.buildMode.get() == HighwayBuilder.BuildMode.Tunnel) {
                        switch ((HighwayBuilder.HighwayType)this.highwayType.get()) {
                            case Cardinal:
                                HighwayBuilderLogic.tickCardinalDig();
                                break;
                            case Diagonal:
                                HighwayBuilderLogic.tickDiagonalDig();
                        }
                    }
                } else if ((this.enableItemRestocking.get() || this.shulkerRestocking.get()) && this.mode.get() != HighwayBuilder.Mode.Manual) {
                    if (!InventoryManager.restocking && !InventoryManager.postRestocking) {
                        if (this.restockAttempts >= 6) {
                            this.stopForLowMaterials(enderChestCount, obsidianCount);
                        } else {
                            InventoryManager.restockPos = this.client.player.getBlockPos();
                            PlayerControlUtils.setAutoWalk(false);
                            InventoryManager.evaluateRestock();
                            if (!InventoryManager.restocking) {
                                this.stopForLowMaterials(enderChestCount, obsidianCount);
                            } else {
                                this.restockAttempts++;
                            }
                        }
                    }
                } else {
                    this.stopForLowMaterials(enderChestCount, obsidianCount);
                }
            } else {
                PlayerControlUtils.setAutoWalk(false);
            }
        }
    }

    private void stopForLowMaterials(int enderChestCount, int obsidianCount) {
        HighwayState state = HighwayState.getInstance();
        String reason = "Low on materials and restocking failed or is unavailable. Missing: ";
        if (enderChestCount < 8) {
            reason = reason + "Enderchest count: " + enderChestCount + "/8 ";
        }

        if (obsidianCount < 1) {
            reason = reason + "Obsidian count: " + obsidianCount + "/8 ";
        }

        if (!hasFood()) {
            reason = reason + "Food - golden apples blacklisted in auto-eat module? ";
        }

        if (getBuildMode() == HighwayBuilder.BuildMode.Pave && state.getPickaxeCount() < 1) {
            reason = reason + "Non SilkTouch pickaxe.";
        }

        if (getBuildMode() == HighwayBuilder.BuildMode.Tunnel && state.getNonSilkPickaxeCount() < 1) {
            reason = reason + "Pickaxe.";
        }

        this.restockAttempts = 0;
        PlayerControlUtils.disableHighwayBuilder();
        if (this.disconnectIfNoMaterials.get()) {
            PlayerControlUtils.disconnect(reason);
        } else {
            this.error(reason, new Object[0]);
        }
    }

    private boolean shouldFarmEchests(int obsidianCount, int echestCount) {
        HighwayState state = HighwayState.getInstance();
        if (!state.isShulkerRestocking() && !state.isEchestRestocking() && !state.isPostRestocking() && !PlayerControlUtils.isGatheringItem()) {
            return this.pavementBlock.get() != Blocks.OBSIDIAN ? false : echestCount > 8 && InventoryManager.countEmptySlots() > 0 && obsidianCount <= 8;
        } else {
            return false;
        }
    }

    private boolean initHighwayState() {
        assert this.client.player != null;

        HighwayState state = HighwayState.getInstance();
        state.setDirection(HighwayUtils.getPlayerDirection());
        state.setPlacementType(MusheorSystem.Manager.placementType.get());
        state.setStartX(this.client.player.getBlockX());
        state.setStartZ(this.client.player.getBlockZ());
        state.setAlignX(VersionHelper.get().getPlayerPos().getX());
        state.setAlignZ(VersionHelper.get().getPlayerPos().getZ());
        state.setAnchorX(this.client.player.getBlockX());
        state.setAnchorY(this.client.player.getBlockY());
        state.setAnchorZ(this.client.player.getBlockZ());
        if (this.mode.get() == HighwayBuilder.Mode.Auto) {
            HighwayDetector.DetectedHighway detected = HighwayDetector.detect(
                this.client.player.getBlockX(), this.client.player.getBlockZ(), this.client.player.getBlockY(), state.getDirection()
            );
            if (detected == null) {
                this.error("Auto mode could not detect a highway. Stand on a highway or use Semi mode.", new Object[0]);
                return false;
            }

            state.setDetectedHighway(detected);
            state.setStartX(detected.startX);
            state.setStartZ(detected.startZ);
            state.setAlignX(detected.alignX);
            state.setAlignZ(detected.alignZ);
            state.setAnchorX(detected.startX);
            state.setAnchorZ(detected.startZ);
            this.info("Detected: §b" + detected.label + "§r | Width: §e" + detected.width + "§r | Dir: §a" + detected.direction, new Object[0]);
        }

        return true;
    }

    private void enableHelperModules() {
        Module KekNuker = Modules.get().get(KekNuker.class);
        Module AutoEat = Modules.get().get(AutoEat.class);
        Module AutoGap = Modules.get().get(AutoGap.class);
        Module KillAura = Modules.get().get(KillAura.class);
        Module SourceRemover = Modules.get().get(SourceRemover.class);
        Module InventoryCleaner = Modules.get().get(InventoryCleaner.class);
        Module HotbarReplenish = Modules.get().get(HotbarReplenish.class);
        Module FreeLook = Modules.get().get(FreeLook.class);
        if (this.toggleKekNuker.get() && !KekNuker.isActive()) {
            KekNuker.toggle();
        }

        if (this.toggleAutoEat.get() && !AutoEat.isActive()) {
            AutoEat.toggle();
        }

        if (this.toggleAutoGap.get() && !AutoGap.isActive()) {
            AutoGap.toggle();
        }

        if (this.toggleKillAura.get() && !KillAura.isActive()) {
            KillAura.toggle();
        }

        if (this.toggleInventoryCleaner.get() && !InventoryCleaner.isActive()) {
            InventoryCleaner.toggle();
        }

        if (this.enableFreelook.get() && !FreeLook.isActive()) {
            PlayerControlUtils.enableFreeLook();
        }

        if (this.toggleAutoReplenish.get() && !HotbarReplenish.isActive()) {
            HotbarReplenish.toggle();
        }

        if (this.discordRpc.get()) {
            DiscordRpc.start();
        }

        if (this.toggleSourceFiller.get() && !SourceRemover.isActive()) {
            SourceRemover.toggle();
        }
    }

    public static void disableHelperModules() {
        Module[] alwaysDisableModules = new Module[]{
            Modules.get().get(EchestFarmer.class),
            Modules.get().get(KekNuker.class),
            Modules.get().get(AutoWalk.class),
            Modules.get().get(HotbarReplenish.class),
            Modules.get().get(FreeLook.class),
            Modules.get().get(SourceRemover.class),
            Modules.get().get(InventoryCleaner.class),
            Modules.get().get(KekBounce.class)
        };

        for (Module module : alwaysDisableModules) {
            if (module.isActive()) {
                module.toggle();
            }
        }

        Module KillAura = Modules.get().get(KillAura.class);
        Module AutoEat = Modules.get().get(AutoEat.class);
        Module AutoGap = Modules.get().get(AutoGap.class);
        if (INSTANCE.toggleKillAura.get() && KillAura.isActive()) {
            KillAura.toggle();
        }

        if (INSTANCE.toggleAutoEat.get() && AutoEat.isActive()) {
            AutoEat.toggle();
        }

        if (INSTANCE.toggleAutoGap.get() && AutoGap.isActive()) {
            AutoGap.toggle();
        }
    }

    public static boolean hasFood() {
        List<Item> blacklistedFood = Modules.get().get(AutoEat.class).blacklist.get();

        for (int i = 0; i < Objects.requireNonNull(MinecraftClient.getInstance().player).getInventory().main.size(); i++) {
            ItemStack stack = MinecraftClient.getInstance().player.getInventory().getStack(i);
            if (stack.getComponents().contains(DataComponentTypes.FOOD)
                && !(stack.getComponents() instanceof ShulkerBoxBlock)
                && !blacklistedFood.contains(stack.getItem())) {
                return true;
            }

            if (stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE && !blacklistedFood.contains(stack.getItem())) {
                return true;
            }
        }

        return false;
    }

    public static void onBlockMined(BlockState s) {
        HighwayState state = HighwayState.getInstance();
        if (s.getBlock() == Blocks.OBSIDIAN) {
            state.incrementObsidianMined();
        }

        if (s.getBlock() == Blocks.NETHERRACK) {
            state.incrementNetherrackMined();
        }

        if (s.getBlock() == Blocks.ENDER_CHEST) {
            state.incrementEchestsMined();
        }
    }

    public static boolean isEating() {
        return Modules.get().get(AutoEat.class).eating || Modules.get().get(AutoGap.class).isEating();
    }

    public static boolean isKillAuraAttacking() {
        return Modules.get().get(KillAura.class).attacking;
    }

    public static HighwayUtils.HighwayDirection getDirection() {
        return HighwayState.getInstance().getDirection();
    }

    public static boolean isEchestFarmerActive() {
        return EchestFarmer.INSTANCE.isActive();
    }

    public static HighwayBuilder.BuildMode getBuildMode() {
        return INSTANCE.buildMode.get();
    }

    public static HighwayBuilder.Mode getMode() {
        return INSTANCE.mode.get();
    }

    public static HighwayBuilder.HighwayType getHighwayType() {
        if (INSTANCE.mode.get() == HighwayBuilder.Mode.Auto) {
            HighwayDetector.DetectedHighway d = HighwayState.getInstance().getDetectedHighway();
            if (d != null) {
                return d.type;
            }
        }

        return INSTANCE.highwayType.get();
    }

    public static int getPavementWidth() {
        if (INSTANCE.mode.get() == HighwayBuilder.Mode.Auto) {
            HighwayDetector.DetectedHighway d = HighwayState.getInstance().getDetectedHighway();
            if (d != null) {
                return d.width;
            }
        }

        return INSTANCE.pavementWidth.get();
    }

    public static boolean shouldMineAboveRails() {
        return INSTANCE.mineAboveRails.get();
    }

    public static boolean shouldReplaceCryingObsidian() {
        return INSTANCE.replaceCryingObsidian.get();
    }

    public static Block getPavementBlock() {
        return INSTANCE.pavementBlock.get();
    }

    public static boolean shouldPlaceRails() {
        return INSTANCE.placeRails.get();
    }

    public static boolean shouldPlaceLeftRail() {
        return INSTANCE.placeLeftRail.get();
    }

    public static boolean shouldPlaceRightRail() {
        return INSTANCE.placeRightRail.get();
    }

    public static Block getScaffoldBlock() {
        return INSTANCE.scaffoldBlock.get();
    }

    public static HighwayBuilder.ScaffoldMode getScaffoldMode() {
        return INSTANCE.scaffoldMode.get();
    }

    public static boolean shouldScaffoldLeftRail() {
        return INSTANCE.scaffoldLeftRail.get();
    }

    public static boolean shouldScaffoldRightRail() {
        return INSTANCE.scaffoldRightRail.get();
    }

    public static boolean useAdvancedSourceFiller() {
        return INSTANCE.advancedSourceFiller.get();
    }

    public static boolean shouldFillCeiling() {
        return INSTANCE.fillCeiling.get();
    }

    public static boolean isItemRestockingEnabled() {
        return INSTANCE.enableItemRestocking.get();
    }

    public static boolean shouldSwapBrokenPickaxes() {
        return INSTANCE.swapBrokenPickaxes.get();
    }

    public static boolean isShulkerRestockingEnabled() {
        return INSTANCE.shulkerRestocking.get();
    }

    public static boolean isMaterialShulkerRestockingEnabled() {
        return INSTANCE.materialShulkerRestocking.get();
    }

    public static boolean isToolShulkerRestockingEnabled() {
        return INSTANCE.toolShulkerRestocking.get();
    }

    public static boolean isAutoBounceEnabled() {
        return INSTANCE.autoBounce.get() && INSTANCE.buildMode.get() == HighwayBuilder.BuildMode.Pave;
    }

    public static int getBounceDistanceCheck() {
        return INSTANCE.bounceDistanceCheck.get();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        HighwayState state = HighwayState.getInstance();

        assert this.client.player != null;

        if (state.getDirection() != null) {
            if (state.getAnchorX() != null && state.getAnchorY() != null && state.getAnchorZ() != null) {
                if (state.getAlignX() != null && state.getAlignZ() != null) {
                    if (MusheorSystem.Manager.placeRender.get()) {
                        if (this.buildMode.get() == HighwayBuilder.BuildMode.Pave) {
                            HighwayDetector.DetectedHighway detected = HighwayDetector.detect(
                                this.client.player.getBlockX(), this.client.player.getBlockZ(), this.client.player.getBlockY(), getDirection()
                            );
                            int railY = state.getAnchorY();
                            List<BlockPos> cardinalPositions = new ArrayList<>();

                            for (BlockPos pos : HighwayPositions.getCardinalFloor(3, 2, shouldPlaceLeftRail(), shouldPlaceRightRail())) {
                                if ((detected == null || pos.getY() != railY || !HighwayDetector.isOnCrossingHighway(pos, detected))
                                    && BlockUtils.canPlace(pos, true)) {
                                    cardinalPositions.add(pos);
                                }
                            }

                            List<BlockPos> diagonalPositions = new ArrayList<>();

                            for (BlockPos posx : HighwayPositions.getDiagonalFloor(2, 2, shouldPlaceLeftRail(), shouldPlaceRightRail())) {
                                if ((detected == null || posx.getY() != railY || !HighwayDetector.isOnCrossingHighway(posx, detected))
                                    && BlockUtils.canPlace(posx, true)) {
                                    diagonalPositions.add(posx);
                                }
                            }

                            if (getHighwayType() == HighwayBuilder.HighwayType.Cardinal) {
                                BlockRenderUtils.renderPositions(event, cardinalPositions, getPavementBlock());
                            }

                            if (getHighwayType() == HighwayBuilder.HighwayType.Diagonal) {
                                BlockRenderUtils.renderPositions(event, diagonalPositions, getPavementBlock());
                            }
                        }
                    }
                }
            }
        }
    }

    public static enum BuildMode {
        Pave,
        Tunnel;
    }

    public static enum HighwayType {
        Cardinal,
        Diagonal;
    }

    public static enum Mode {
        Auto,
        Semi,
        Manual;
    }

    public static enum ScaffoldMode {
        Disabled,
        Simple,
        Full;
    }
}
