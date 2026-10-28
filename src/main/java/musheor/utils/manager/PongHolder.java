package musheor.utils.manager;

import java.util.ArrayDeque;
import musheor.utils.HighwayBuilderLogic;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;

public class PongHolder {
    private static final ArrayDeque<Integer> heldPongs = new ArrayDeque<>();
    private static volatile boolean holding;

    public static synchronized void startHolding() {
        holding = true;
    }

    public static synchronized boolean isHolding() {
        return holding;
    }

    public static synchronized boolean holdPong(int parameter) {
        if (!holding) {
            return false;
        } else {
            heldPongs.addLast(parameter);
            return true;
        }
    }

    public static synchronized void releaseAll() {
        holding = false;
        if (!heldPongs.isEmpty()) {
            if (HighwayBuilderLogic.mc.getNetworkHandler() == null) {
                heldPongs.clear();
            } else {
                while (!heldPongs.isEmpty()) {
                    HighwayBuilderLogic.mc.getNetworkHandler().sendPacket(new CommonPongC2SPacket(heldPongs.pollFirst()));
                }
            }
        }
    }

    public static synchronized void discardAll() {
        holding = false;
        heldPongs.clear();
    }
}
