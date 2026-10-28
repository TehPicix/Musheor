package musheor.utils.network;

import meteordevelopment.meteorclient.systems.modules.Modules;
import musheor.modules.features.CreativeFly;
import musheor.modules.hud.HudInfoPlus;
import musheor.utils.system.MusheorSystem;
import net.minecraft.client.MinecraftClient;

public class PacketLimiter {
    public static boolean canSendPlacePacket() {
        if (MinecraftClient.getInstance().player.isCreative()) {
            return true;
        } else {
            return Modules.get().get(CreativeFly.class).isActive() ? HudInfoPlus.getInteractPacketCount() < 8 : HudInfoPlus.getInteractPacketCount() < 9;
        }
    }

    public static boolean canSendInventoryPacket() {
        return MinecraftClient.getInstance().player.isCreative() ? true : HudInfoPlus.getInventoryPacketCount() < MusheorSystem.Manager.invPacketLimit.get();
    }

    public static int getInventoryPacketCount() {
        return HudInfoPlus.getInventoryPacketCount();
    }

    public static int getRemainingInventoryPackets() {
        return MusheorSystem.Manager.invPacketLimit.get() - getInventoryPacketCount();
    }
}
