package musheor.modules.automation;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.ChestSwap;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import musheor.musheor;
import musheor.utils.manager.ElytraManager;
import musheor.utils.manager.InventoryManager;
import musheor.utils.system.MusheorSystem;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class ElytraTweakz extends Module {
    private final Setting<Boolean> doubleJumpEquipElytra = this.settings
        .getDefaultGroup()
        .add(
            new Builder()
                .name("double-jump-equip-elytra")
                .description("Equip your elytra and start gliding when double-tapping spacebar while airborne")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> landEquipChestplate = this.settings
        .getDefaultGroup()
        .add(
            new Builder()
                .name("land-equip-chestplate")
                .description("Automatically equip your chestplate when landing after gliding")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> autoSwapBroken = this.settings
        .getDefaultGroup()
        .add(
            new Builder()
                .name("auto-swap-broken")
                .description("Automatically swap with a new elytra when your current one is broken / damaged")
                .defaultValue(true)
                .build()
        );
    public final Setting<Integer> durabilityThreshold = this.settings
        .getDefaultGroup()
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("durability-threshold")
                .defaultValue(10)
                .sliderRange(1, 100)
                .visible(this.autoSwapBroken::get)
                .build()
        );
    private final Setting<Keybind> fireRocketKeybind = this.settings
        .getDefaultGroup()
        .add(
            new meteordevelopment.meteorclient.settings.KeybindSetting.Builder()
                .name("fire-rocket-keybind")
                .description("Fires a rocket when pressed/held. Does nothing if there is already an active rocket")
                .defaultValue(Keybind.none())
                .build()
        );
    public static ElytraTweakz INSTANCE;
    private boolean wasJumpPressed;
    private boolean wasOnGround;
    private boolean wasGliding;
    private long lastJumpTime;
    private int pendingGlideTicks;
    private int rocketCooldown;

    public ElytraTweakz() {
        super(musheor.MAIN, "elytra-tweakz", "Useful elytra enhancements and tweaks");
        INSTANCE = this;
    }

    @Override
    public void onActivate() {
        this.wasJumpPressed = false;
        this.wasOnGround = false;
        this.wasGliding = false;
        this.lastJumpTime = 0L;
        this.pendingGlideTicks = 0;
        this.rocketCooldown = 0;
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player != null && this.mc.world != null) {
            boolean onGround = this.mc.player.isOnGround();
            boolean gliding = this.mc.player.isGliding();
            boolean jumpPressed = this.mc.options.jumpKey.isPressed();
            boolean jumpJustPressed = jumpPressed && !this.wasJumpPressed;
            if (this.autoSwapBroken.get()) {
                ItemStack chest = this.mc.player.getEquippedStack(EquipmentSlot.CHEST);
                if (chest.isOf(Items.ELYTRA)) {
                    int dura = chest.getMaxDamage() - chest.getDamage();
                    if (dura <= this.durabilityThreshold.get()) {
                        for (int i = 0; i < this.mc.player.getInventory().main.size(); i++) {
                            ItemStack stack = this.mc.player.getInventory().getStack(i);
                            if (stack.isOf(Items.ELYTRA) && stack.getMaxDamage() - stack.getDamage() > this.durabilityThreshold.get()) {
                                InvUtils.move().from(i).toArmor(2);
                                break;
                            }
                        }
                    }
                }
            }

            if (!ElytraManager.isSessionActive() && !Modules.get().get(KekBounce.class).isActive()) {
                ElytraManager.tick();
                if (this.doubleJumpEquipElytra.get()) {
                    if (jumpJustPressed) {
                        long now = System.currentTimeMillis();
                        if (!onGround && now - this.lastJumpTime < 300L) {
                            if (this.mc.player.isCreative() || this.mc.player.getAbilities().flying) {
                                return;
                            }

                            ItemStack chest = this.mc.player.getEquippedStack(EquipmentSlot.CHEST);
                            if (!chest.isOf(Items.ELYTRA)) {
                                boolean hasElytra = this.mc
                                    .player
                                    .getInventory()
                                    .main
                                    .stream()
                                    .anyMatch(s -> s.isOf(Items.ELYTRA) && s.getMaxDamage() - s.getDamage() > this.durabilityThreshold.get());
                                if (hasElytra) {
                                    InventoryManager.swapChestArmor();
                                    this.pendingGlideTicks = 1;
                                    return;
                                }
                            } else if (chest.getMaxDamage() - chest.getDamage() > this.durabilityThreshold.get()) {
                                ElytraManager.startGlide();
                            }
                        }

                        this.lastJumpTime = now;
                    }

                    if (this.pendingGlideTicks > 0) {
                        this.pendingGlideTicks--;
                        ElytraManager.startGlide();
                    }
                }

                if (this.landEquipChestplate.get()
                    && !this.wasOnGround
                    && onGround
                    && this.wasGliding
                    && this.mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
                    Modules.get().get(ChestSwap.class).swap();
                }

                if (this.mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA) && this.mc.player.isGliding()) {
                    if (this.rocketCooldown > 0) {
                        this.rocketCooldown--;
                    }

                    if (this.fireRocketKeybind.get().isPressed() && this.rocketCooldown == 0) {
                        ElytraManager.useRocketIfNeeded();
                        this.rocketCooldown = MusheorSystem.Manager.rocketTimeout.get();
                    }
                }

                this.wasJumpPressed = jumpPressed;
                this.wasOnGround = onGround;
                this.wasGliding = gliding;
            } else {
                this.wasJumpPressed = jumpPressed;
                this.wasOnGround = onGround;
                this.wasGliding = gliding;
            }
        }
    }
}
