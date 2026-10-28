package musheor.modules.features;

import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.ChestSwap;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.world.Dimension;
import meteordevelopment.orbit.EventHandler;
import musheor.musheor;
import musheor.utils.manager.ElytraManager;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;

public class KekFly extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgGrim = this.settings.createGroup("Grim");
    private final Setting<Boolean> autoSwap = this.sgGeneral
        .add(new Builder().name("auto-swap").description("Automatically swaps to your elytra when activating").defaultValue(true).build());
    private final Setting<Boolean> swapBack = this.sgGeneral
        .add(
            new Builder()
                .name("swap-back")
                .description("Automatically swaps back to your chestplate when deactivating")
                .defaultValue(true)
                .visible(this.autoSwap::get)
                .build()
        );
    public final Setting<Boolean> lockY = this.sgGeneral
        .add(
            new Builder()
                .name("lock-y")
                .description("Steer the player's pitch each tick to maintain a target Y level while gliding.")
                .defaultValue(false)
                .build()
        );
    public final Setting<Integer> yLevel = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("y-level")
                .description("The Y level to maintain when lock-y is enabled.")
                .defaultValue(350)
                .sliderRange(64, 512)
                .visible(this.lockY::get)
                .build()
        );
    private final Setting<Boolean> fakeFly = this.sgGrim
        .add(new Builder().name("fake-fly").description("Allows you to fly while wearing a chestplate").defaultValue(false).build());
    public static KekFly INSTANCE;

    public KekFly() {
        super(musheor.MAIN, "kek-fly", "ZOOM thru the air, or even on ground...");
        INSTANCE = this;
    }

    @Override
    public void onActivate() {
        ElytraManager.beginSession();
        if (ElytraManager.isElytraMissing()) {
            ChatUtils.info("§cNo elytra found, disabling...");
            this.toggle();
        } else {
            ElytraManager.attach(this, this.fakeFly::get);
            if (this.mc.player != null && this.mc.world != null) {
                if (this.autoSwap.get() && !this.fakeFly.get() && !this.mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
                    Modules.get().get(ChestSwap.class).swap();
                }

                if (this.mc.player.isOnGround()) {
                    this.mc.player.jump();
                }
            }
        }
    }

    @Override
    public void onDeactivate() {
        ElytraManager.endSession();
        if (this.mc.player != null && this.mc.world != null) {
            this.mc.player.setNoGravity(false);
            if (this.autoSwap.get() && this.swapBack.get() && !this.fakeFly.get()) {
                Modules.get().get(ChestSwap.class).swap();
            } else {
                ElytraManager.startGlide();
            }

            ElytraManager.detach(this);
        } else {
            ElytraManager.detach(this);
        }
    }

    @EventHandler
    private void onPacketReceive(Receive event) {
        ElytraManager.onPacketReceive(event);
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        ElytraManager.onGameLeft();
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player != null) {
            ElytraManager.tick();
            ElytraManager.startGlide();
            if (this.lockY.get()) {
                this.steerToYLevel();
            }

            ElytraManager.useRocketIfNeeded();
        }
    }

    private void steerToYLevel() {
        if (!this.mc.player.isOnGround()) {
            if (PlayerUtils.getDimension() != Dimension.Nether) {
                double deltaY = this.yLevel.get().intValue() - this.mc.player.getY();
                float desiredPitch = (float)Math.toDegrees(Math.atan2(-deltaY, 16.0));
                desiredPitch = Math.max(-45.0F, Math.min(45.0F, desiredPitch));
                this.mc.player.setPitch(desiredPitch);
            }
        }
    }
}
