package musheor.modules.hud;

import java.util.ArrayDeque;
import java.util.Deque;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.DoubleSetting.Builder;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import musheor.musheor;
import musheor.utils.system.MusheorSystem;

public class HudInfoPlus extends HudElement {
    public static final HudElementInfo<HudInfoPlus> INFO = new HudElementInfo<>(
        musheor.MUSHEOR_HUD, "packet-limits", "A hud module that displays how many packets you are sending to the server", HudInfoPlus::new
    );
    private final Setting<Double> scale = this.settings.getDefaultGroup().add(new Builder().name("scale").defaultValue(1.0).build());
    private static final Deque<Long> inventoryPacketTimes = new ArrayDeque<>();
    private static final Deque<Long> globalPacketTimes = new ArrayDeque<>();
    private static final Deque<Long> interactPacketTimes = new ArrayDeque<>();

    public HudInfoPlus() {
        super(INFO);
    }

    public static void recordInventoryPacket() {
        synchronized (inventoryPacketTimes) {
            inventoryPacketTimes.addLast(System.currentTimeMillis());
        }
    }

    public static void recordGlobalPacket() {
        synchronized (globalPacketTimes) {
            globalPacketTimes.addLast(System.currentTimeMillis());
        }
    }

    public static void recordInteractPacket() {
        synchronized (interactPacketTimes) {
            interactPacketTimes.addLast(System.currentTimeMillis());
        }
    }

    public static void pruneExpiredPackets() {
        long now = System.currentTimeMillis();
        Long first;
        synchronized (inventoryPacketTimes) {
            while ((first = inventoryPacketTimes.peekFirst()) != null && now - first > 4000L) {
                inventoryPacketTimes.pollFirst();
            }
        }

        synchronized (globalPacketTimes) {
            while ((first = globalPacketTimes.peekFirst()) != null && now - first > 4000L) {
                globalPacketTimes.pollFirst();
            }
        }

        synchronized (interactPacketTimes) {
            first = interactPacketTimes.peekFirst();
            if (first != null && now - first > 310L) {
                interactPacketTimes.clear();
            }
        }
    }

    public static int getInventoryPacketCount() {
        synchronized (inventoryPacketTimes) {
            return inventoryPacketTimes.size();
        }
    }

    public static int getGlobalPacketCount() {
        synchronized (globalPacketTimes) {
            return globalPacketTimes.size();
        }
    }

    public static int getInteractPacketCount() {
        synchronized (interactPacketTimes) {
            return interactPacketTimes.size();
        }
    }

    @Override
    public void render(HudRenderer renderer) {
        int inventoryCount = getInventoryPacketCount();
        int generalCount = getGlobalPacketCount();
        int InteractionCount = getInteractPacketCount();
        String[][] lines = new String[][]{
            {"Inv", String.valueOf(inventoryCount)}, {"Global", String.valueOf(generalCount)}, {"Int", String.valueOf(InteractionCount)}
        };
        double lineHeight = renderer.textHeight(true, this.scale.get());
        double width = 0.0;

        for (String[] line : lines) {
            double lineWidth = renderer.textWidth(line[0], true) + renderer.textWidth(": ", true) + renderer.textWidth(line[1], true, this.scale.get());
            width = Math.max(width, lineWidth);
        }

        this.setSize(width, lineHeight * lines.length);
        double Y = this.y;
        double X = this.x;
        Color color;
        if (inventoryCount < MusheorSystem.Manager.invPacketLimit.get().intValue() * 0.5) {
            color = Color.GREEN;
        } else if (inventoryCount < MusheorSystem.Manager.invPacketLimit.get().intValue() * 0.8) {
            color = Color.YELLOW;
        } else {
            color = Color.RED;
        }

        Color color2;
        if (generalCount < MusheorSystem.Manager.globalPacketLimit.get().intValue() * 0.5) {
            color2 = Color.GREEN;
        } else if (generalCount < MusheorSystem.Manager.globalPacketLimit.get().intValue() * 0.8) {
            color2 = Color.YELLOW;
        } else {
            color2 = Color.RED;
        }

        Color color3;
        if (InteractionCount < 4.5) {
            color3 = Color.GREEN;
        } else if (InteractionCount < 7.2) {
            color3 = Color.YELLOW;
        } else {
            color3 = Color.RED;
        }

        String text2 = String.format("global: %d/%d", generalCount, MusheorSystem.Manager.globalPacketLimit.get());
        renderer.text(text2, X, Y, color2, true, this.scale.get());
        Y += lineHeight;
        String text = String.format("inv: %d/%d", inventoryCount, MusheorSystem.Manager.invPacketLimit.get());
        renderer.text(text, X, Y, color, true, this.scale.get());
        Y += lineHeight;
        String text3 = String.format("int: %d/%d", InteractionCount, 9);
        renderer.text(text3, X, Y, color3, true, this.scale.get());
    }
}
