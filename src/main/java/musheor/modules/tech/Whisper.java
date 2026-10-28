package musheor.modules.tech;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import musheor.musheor;
import musheor.utils.PlayerControlUtils;
import net.minecraft.client.MinecraftClient;

public class Whisper extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private static final MinecraftClient client = MinecraftClient.getInstance();
    public final Setting<Integer> antiSpamLength = this.sgGeneral
        .add(
            new Builder()
                .name("anti-spam-length")
                .description("The length of the randomly generated message")
                .defaultValue(12)
                .min(1)
                .sliderRange(1, 31)
                .build()
        );
    private final Setting<String> username = this.settings
        .getDefaultGroup()
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("username")
                .description("IGN of the player you want to send a message to.")
                .defaultValue("")
                .build()
        );

    public Whisper() {
        super(musheor.MAIN, "TPA", "Used for sending TP requests to stasis-bots");
    }

    @EventHandler
    public void onTick(Pre event) {
        if (this.isActive()) {
            PlayerControlUtils.sendTeleportRequest(this.username.get(), this.antiSpamLength.get());
            this.toggle();
        }
    }
}
