package musheor.modules.features;

import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import musheor.musheor;
import net.minecraft.network.packet.s2c.play.CloseScreenS2CPacket;

public class AntiCheat extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    public final Setting<Boolean> airPlaceBypass = this.sgGeneral
        .add(
            new Builder()
                .name("air-place-bypass")
                .description("Bypasses default meteor place methods with an airplace method across all modules")
                .defaultValue(true)
                .visible(() -> false)
                .build()
        );
    public final Setting<Boolean> noScreenClose = this.sgGeneral
        .add(new Builder().name("no-screen-close").description("Prevents the server from closing your inventory / GUI screens").defaultValue(true).build());

    public AntiCheat() {
        super(musheor.MAIN, "anti-cheat", "Anti-cheat bypasses for 2b2t.");
    }

    @EventHandler
    private void onPacketReceive(Receive event) {
        if (this.noScreenClose.get() && this.isActive()) {
            if (event.packet instanceof CloseScreenS2CPacket packet) {
                event.cancel();
            }
        }
    }
}
