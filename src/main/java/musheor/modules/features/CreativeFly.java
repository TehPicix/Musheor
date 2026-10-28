package musheor.modules.features;

import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.ChestSwap;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import musheor.musheor;
import musheor.compat.VersionHelper;
import musheor.utils.manager.ElytraManager;
import musheor.utils.manager.RotationManager;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;

public class CreativeFly extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgGrim = this.settings.createGroup("Grim");
    private final Setting<CreativeFly.HoverMode> hover = this.sgGeneral
        .add(new Builder<CreativeFly.HoverMode>().name("hover").defaultValue(CreativeFly.HoverMode.FullStop).build());
    private final Setting<Boolean> autoSwap = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("auto-swap")
                .description("Automatically swaps to your elytra when activating")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> swapBack = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("swap-back")
                .description("Automatically swaps back to your chestplate when deactivating")
                .defaultValue(true)
                .visible(this.autoSwap::get)
                .build()
        );
    private final Setting<Boolean> pauseOnGround = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("pause-on-ground")
                .description("Pauses when the player is touching the ground")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> fakeFly = this.sgGrim
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("fake-fly")
                .description("Allows you to fly without having an elytra equipped (requires elytra in the hotbar)")
                .defaultValue(false)
                .build()
        );
    public static CreativeFly INSTANCE;
    private boolean hoverFlip;
    private Vec3d frozenPos;
    private boolean hasMovementInput;
    private int freezeTicks;

    public CreativeFly() {
        super(musheor.MAIN, "creative-fly", "Creative flight, but in survival, optimized for GrimAC");
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
                if (this.autoSwap.get() && !this.fakeFly.get() && !this.mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem().equals(Items.ELYTRA)) {
                    Modules.get().get(ChestSwap.class).swap();
                }

                if (this.mc.player.isOnGround()) {
                    this.mc.player.jump();
                }

                this.hoverFlip = false;
                this.frozenPos = null;
                this.hasMovementInput = false;
            }
        }
    }

    @Override
    public void onDeactivate() {
        RotationManager.clear();
        ElytraManager.endSession();
        if (this.mc.player != null && this.mc.world != null) {
            this.mc.player.setNoGravity(false);
            this.frozenPos = null;
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
            RotationManager.release(0);
            if (this.pauseOnGround.get() && this.mc.player.isOnGround()) {
                ElytraManager.onLanded();
            } else {
                ElytraManager.tick();
                boolean up = this.mc.options.jumpKey.isPressed();
                boolean down = this.mc.options.sneakKey.isPressed();
                boolean w = this.mc.options.forwardKey.isPressed();
                boolean a = this.mc.options.leftKey.isPressed();
                boolean s = this.mc.options.backKey.isPressed();
                boolean d = this.mc.options.rightKey.isPressed();
                boolean any = up || down || w || a || s || d;
                this.hasMovementInput = any;
                float cameraYaw = this.mc.player.getYaw();
                if (any) {
                    if (this.frozenPos != null) {
                        this.mc.player.setPosition(this.frozenPos.x, this.frozenPos.y, this.frozenPos.z);
                        this.mc.player.setVelocity(Vec3d.ZERO);
                        this.mc.player.setNoGravity(false);
                        this.mc.player.resetPosition();
                        this.frozenPos = null;
                    }

                    float flightYaw = cameraYaw;
                    if (w && a) {
                        flightYaw = cameraYaw - 45.0F;
                    } else if (w && d) {
                        flightYaw = cameraYaw + 45.0F;
                    } else if (s && a) {
                        flightYaw = cameraYaw - 135.0F;
                    } else if (s && d) {
                        flightYaw = cameraYaw + 135.0F;
                    } else if (a) {
                        flightYaw = cameraYaw - 90.0F;
                    } else if (d) {
                        flightYaw = cameraYaw + 90.0F;
                    } else if (s) {
                        flightYaw = cameraYaw + 180.0F;
                    }

                    float flightPitch = -1.0F;
                    if (!up || !w && !a && !s && !d) {
                        if (up) {
                            flightPitch = -90.0F;
                        } else if (!down || !w && !a && !s && !d) {
                            if (down) {
                                flightPitch = 90.0F;
                            }
                        } else {
                            flightPitch = 45.0F;
                        }
                    } else {
                        flightPitch = -45.0F;
                    }

                    RotationManager.setServerYaw(flightYaw);
                    RotationManager.setServerPitch(flightPitch);
                    RotationManager.setMoveMode(RotationManager.MoveMode.Forward, 0);
                }

                if (this.frozenPos == null || this.fakeFly.get()) {
                    ElytraManager.startGlide();
                }

                if (!any) {
                    if (this.hover.get() != CreativeFly.HoverMode.FullStop) {
                        if (ElytraManager.isRocketBoosting()) {
                            float flightYawx = cameraYaw;
                            float flightPitch = -1.0F;
                            if (this.hover.get() == CreativeFly.HoverMode.FlipHorizontal) {
                                if (this.hoverFlip) {
                                    flightYawx = cameraYaw + 180.0F;
                                }
                            } else if (this.hover.get() == CreativeFly.HoverMode.FlipVertical) {
                                flightPitch = this.hoverFlip ? -90.0F : 90.0F;
                            }

                            this.hoverFlip = !this.hoverFlip;
                            RotationManager.setServerYaw(flightYawx);
                            RotationManager.setServerPitch(flightPitch);
                        } else {
                            ElytraManager.useRocketIfNeeded();
                        }
                    } else {
                        this.holdPosition();
                    }
                } else {
                    ElytraManager.useRocketIfNeeded();
                }
            }
        }
    }

    private void holdPosition() {
        if (this.frozenPos == null) {
            this.frozenPos = VersionHelper.get().getPlayerPos();
            this.freezeTicks = 0;
        }

        if (++this.freezeTicks >= 60 && !ElytraManager.isRocketBoosting()) {
            this.freezeTicks = 0;
            this.frozenPos = this.frozenPos.subtract(0.0, 0.0313, 0.0);
        }

        this.mc.player.setVelocity(Vec3d.ZERO);
        this.mc.player.setNoGravity(true);
        this.mc.player.setPosition(this.frozenPos.x, this.frozenPos.y, this.frozenPos.z);
        ElytraManager.useRocketIfNeeded();
    }

    public static enum HoverMode {
        FullStop,
        FlipHorizontal,
        FlipVertical;
    }
}
