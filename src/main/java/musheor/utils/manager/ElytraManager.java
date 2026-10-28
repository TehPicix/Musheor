package musheor.utils.manager;

import java.util.List;
import java.util.function.BooleanSupplier;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.TickRate;
import musheor.modules.automation.ElytraTweakz;
import musheor.utils.HighwayBuilderLogic;
import musheor.utils.network.PacketLimiter;
import musheor.utils.system.MusheorSystem;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.data.TrackedDataHandler;
import net.minecraft.entity.data.DataTracker.SerializedEntry;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

public class ElytraManager {
    private static BooleanSupplier fakeFlySetting;
    private static Module owner;
    private static boolean pendingRocket;
    private static final Object LOCK = new Object();
    private static volatile boolean fakeGlideActive;
    private static volatile boolean trackerUpdateSeen;
    private static volatile boolean spoofingGlide;
    private static int ticksSinceAttempt;
    private static int spoofTicks;
    private static int launchTicks;
    private static int rocketCooldown;
    private static long lastRocketTime;
    private static boolean sessionActive;
    private static boolean glideStarted;

    public static void attach(Module module, BooleanSupplier setting) {
        owner = module;
        fakeFlySetting = setting;
        pendingRocket = false;
        resetFakeFlyState();
        if (isFakeFlyEnabled()) {
            stowElytra();
        }
    }

    public static void detach(Module module) {
        if (owner == module) {
            releaseSpoof(true);
            owner = null;
            fakeFlySetting = null;
            pendingRocket = false;
            resetFakeFlyState();
        }
    }

    public static boolean isFakeFlyEnabled() {
        return fakeFlySetting != null && fakeFlySetting.getAsBoolean();
    }

    private static boolean handleFakeFly() {
        if (!isFakeFlyEnabled()) {
            return false;
        } else if (HighwayBuilderLogic.mc.player.isOnGround()) {
            onLanded();
            return true;
        } else {
            ticksSinceAttempt++;
            if (spoofingGlide && ++spoofTicks > 8) {
                releaseSpoof(true);
            }

            if (shouldAttemptGlide() && PacketLimiter.getRemainingInventoryPackets() > 4) {
                boolean wearingElytra = HighwayBuilderLogic.mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
                int elytraSlot = wearingElytra ? -1 : findHotbarElytra();
                if (wearingElytra
                    || elytraSlot != -1 && HighwayBuilderLogic.mc.player.currentScreenHandler == HighwayBuilderLogic.mc.player.playerScreenHandler) {
                    boolean swapped = false;
                    releaseSpoof(false);
                    trackerUpdateSeen = false;
                    fakeGlideActive = true;
                    ticksSinceAttempt = 0;
                    if (!wearingElytra) {
                        swapChestWithHotbar(elytraSlot);
                        swapped = true;
                    }

                    sendStartFallFlying();
                    if (pendingRocket) {
                        pendingRocket = false;
                        if (InventoryManager.findInInventory(Items.FIREWORK_ROCKET) != -1) {
                            fireRocket();
                        }
                    }

                    if (swapped) {
                        swapChestWithHotbar(elytraSlot);
                    }

                    return true;
                } else {
                    return true;
                }
            } else {
                return true;
            }
        }
    }

    private static boolean shouldAttemptGlide() {
        if (ticksSinceAttempt < 2) {
            return false;
        } else if (TickRate.INSTANCE.getTimeSinceLastTick() > 1.5F) {
            return false;
        } else {
            return fakeGlideActive && !trackerUpdateSeen ? ticksSinceAttempt > getGlideTimeoutTicks() : true;
        }
    }

    private static int getGlideTimeoutTicks() {
        float tps = TickRate.INSTANCE.getTickRate();
        if (tps <= 0.0F) {
            tps = 20.0F;
        }

        int ping = 0;
        if (HighwayBuilderLogic.mc.getNetworkHandler() != null) {
            PlayerListEntry entry = HighwayBuilderLogic.mc.getNetworkHandler().getPlayerListEntry(HighwayBuilderLogic.mc.player.getUuid());
            if (entry != null) {
                ping = entry.getLatency();
            }
        }

        return 2 + (int)Math.ceil((ping + 2000.0 / tps) / 50.0);
    }

    public static void onPacketReceive(Receive event) {
        if (isFakeFlyEnabled()) {
            if (!(event.packet instanceof PlayerPositionLookS2CPacket)) {
                if (event.packet instanceof EntityTrackerUpdateS2CPacket packet && fakeGlideActive) {
                    ClientPlayerEntity player = HighwayBuilderLogic.mc.player;
                    if (player != null && packet.id() == player.getId()) {
                        List<SerializedEntry<?>> values = packet.trackedValues();

                        for (int i = 0; i < values.size(); i++) {
                            SerializedEntry<?> entry = values.get(i);
                            if (entry.id() == 0 && entry.value() instanceof Byte flags) {
                                if ((flags & -128) != 0) {
                                    return;
                                }

                                trackerUpdateSeen = true;
                                if (!player.isGliding()) {
                                    return;
                                }

                                TrackedDataHandler<Byte> handler = (TrackedDataHandler<Byte>)entry.handler();
                                synchronized (LOCK) {
                                    try {
                                        values.set(i, new SerializedEntry<>(entry.id(), handler, (byte)(flags | -128)));
                                    } catch (UnsupportedOperationException var11) {
                                        return;
                                    }

                                    spoofTicks = 0;
                                    spoofingGlide = true;
                                    PongHolder.startHolding();
                                    return;
                                }
                            }
                        }
                    }
                }
            } else {
                if (spoofingGlide || PongHolder.isHolding()) {
                    PongHolder.releaseAll();
                    HighwayBuilderLogic.mc.execute(() -> {
                        releaseSpoof(true);
                        fakeGlideActive = false;
                    });
                }
            }
        }
    }

    public static void onLanded() {
        releaseSpoof(true);
        fakeGlideActive = false;
    }

    public static void onGameLeft() {
        PongHolder.discardAll();
        resetFakeFlyState();
    }

    private static void releaseSpoof(boolean dropClientGlide) {
        boolean held;
        synchronized (LOCK) {
            held = spoofingGlide;
            spoofingGlide = false;
            spoofTicks = 0;
            PongHolder.releaseAll();
        }

        if (held && dropClientGlide && HighwayBuilderLogic.mc.player != null && HighwayBuilderLogic.mc.player.isGliding()) {
            HighwayBuilderLogic.mc.player.stopGliding();
        }
    }

    private static void resetFakeFlyState() {
        synchronized (LOCK) {
            spoofingGlide = false;
            spoofTicks = 0;
        }

        fakeGlideActive = false;
        trackerUpdateSeen = false;
        ticksSinceAttempt = 2;
    }

    private static void swapChestWithHotbar(int hotbarSlot) {
        HighwayBuilderLogic.mc
            .interactionManager
            .clickSlot(HighwayBuilderLogic.mc.player.playerScreenHandler.syncId, 6, hotbarSlot, SlotActionType.SWAP, HighwayBuilderLogic.mc.player);
    }

    public static void stowElytra() {
        if (HighwayBuilderLogic.mc.player != null && HighwayBuilderLogic.mc.interactionManager != null) {
            if (HighwayBuilderLogic.mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
                if (HighwayBuilderLogic.mc.player.currentScreenHandler == HighwayBuilderLogic.mc.player.playerScreenHandler) {
                    int chestplate = InventoryManager.findChestplate();
                    if (chestplate >= 0 && chestplate <= 8) {
                        swapChestWithHotbar(chestplate);
                    } else {
                        int free = findEmptyHotbarSlot();
                        if (free == -1) {
                            MusheorSystem.debug("%sFake-fly: no free hotbar slot to stow the equipped elytra.", Formatting.RED);
                        } else {
                            swapChestWithHotbar(free);
                            if (chestplate != -1) {
                                InvUtils.move().from(chestplate).toArmor(2);
                            }
                        }
                    }
                }
            }
        }
    }

    public static int findHotbarElytra() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = HighwayBuilderLogic.mc.player.getInventory().getStack(i);
            if (stack.isOf(Items.ELYTRA) && stack.getMaxDamage() - stack.getDamage() > 1) {
                return i;
            }
        }

        return -1;
    }

    private static int findEmptyHotbarSlot() {
        for (int i = 0; i < 9; i++) {
            if (HighwayBuilderLogic.mc.player.getInventory().getStack(i).isEmpty()) {
                return i;
            }
        }

        return -1;
    }

    public static void beginSession() {
        launchTicks = -1;
        rocketCooldown = 0;
        lastRocketTime = 0L;
        sessionActive = true;
        glideStarted = false;
    }

    public static boolean isElytraMissing() {
        if (HighwayBuilderLogic.mc.player == null) {
            return true;
        } else {
            ItemStack worn = HighwayBuilderLogic.mc.player.getEquippedStack(EquipmentSlot.CHEST);
            return worn.isOf(Items.ELYTRA) && worn.getMaxDamage() - worn.getDamage() > ElytraTweakz.INSTANCE.durabilityThreshold.get()
                ? false
                : InventoryManager.findUsableElytra() == -1;
        }
    }

    public static void endSession() {
        sessionActive = false;
        glideStarted = false;
    }

    public static boolean isSessionActive() {
        return sessionActive;
    }

    public static boolean isGlideActive() {
        return glideStarted && sessionActive;
    }

    public static void markGlideStarted() {
        glideStarted = true;
    }

    public static void tick() {
        launchTicks++;
        if (launchTicks >= MusheorSystem.Manager.launchDelay.get()) {
            if (HighwayBuilderLogic.mc.player != null) {
                boolean gliding = HighwayBuilderLogic.mc.player.isGliding();
                if (gliding) {
                    glideStarted = true;
                } else if (!sessionActive) {
                    glideStarted = false;
                }
            }

            if (rocketCooldown > 0) {
                rocketCooldown--;
            }
        }
    }

    public static boolean isRocketBoosting() {
        if (MusheorSystem.Manager.autoRocket.get()) {
            for (Entity entity : HighwayBuilderLogic.mc.world.getEntities()) {
                if (entity instanceof FireworkRocketEntity rocket && rocket.getOwner() == HighwayBuilderLogic.mc.player) {
                    return true;
                }
            }

            return false;
        } else {
            return System.currentTimeMillis() - lastRocketTime < MusheorSystem.Manager.rocketDelay.get() * 1000.0;
        }
    }

    public static void useRocketIfNeeded() {
        if (!RotationManager.outranksMovement()) {
            if (rocketCooldown <= 0 && !isRocketBoosting()) {
                if (InventoryManager.findInInventory(Items.FIREWORK_ROCKET) != -1) {
                    if (!HighwayBuilderLogic.mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
                        if (isFakeFlyEnabled()) {
                            pendingRocket = true;
                        }
                    } else {
                        fireRocket();
                    }
                }
            }
        }
    }

    public static void fireRocket() {
        if (!RotationManager.outranksMovement()) {
            int rocket = InventoryManager.findInInventory(Items.FIREWORK_ROCKET);
            if (rocket != -1) {
                float yaw = RotationManager.yawOr(HighwayBuilderLogic.mc.player.getYaw());
                float pitch = RotationManager.pitchOr(HighwayBuilderLogic.mc.player.getPitch());
                InventoryManager.useSlot(
                    rocket,
                    () -> HighwayBuilderLogic.mc
                        .interactionManager
                        .sendSequencedPacket(HighwayBuilderLogic.mc.world, sequence -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, sequence, yaw, pitch))
                );
                MusheorSystem.debug("%sAttempted to fire a rocket!", Formatting.AQUA);
                rocketCooldown = MusheorSystem.Manager.rocketTimeout.get();
                lastRocketTime = System.currentTimeMillis();
            }
        }
    }

    public static void startGlide() {
        if (HighwayBuilderLogic.mc.player != null && HighwayBuilderLogic.mc.getNetworkHandler() != null && HighwayBuilderLogic.mc.interactionManager != null) {
            if (!HighwayBuilderLogic.mc.player.isSubmergedInWater()) {
                if (!handleFakeFly()) {
                    if (!HighwayBuilderLogic.mc.player.isGliding() && !HighwayBuilderLogic.mc.player.isOnGround()) {
                        sendStartFallFlying();
                        markGlideStarted();
                    }
                }
            }
        }
    }

    public static void sendStartFallFlying() {
        HighwayBuilderLogic.mc
            .interactionManager
            .sendSequencedPacket(HighwayBuilderLogic.mc.world, sequence -> new ClientCommandC2SPacket(HighwayBuilderLogic.mc.player, Mode.START_FALL_FLYING));
        HighwayBuilderLogic.mc.player.startGliding();
        markGlideStarted();
    }
}
