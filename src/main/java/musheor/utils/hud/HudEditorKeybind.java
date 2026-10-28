package musheor.utils.hud;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.orbit.EventHandler;
import musheor.utils.hud.screen.CustomButtonManagerScreen;
import musheor.utils.system.MusheorSystem;
import net.minecraft.client.MinecraftClient;

public class HudEditorKeybind {
    private final MinecraftClient client = MinecraftClient.getInstance();

    @EventHandler
    private void onTick(Pre event) {
        if (this.client.currentScreen == null) {
            if (MusheorSystem.get().openEditor.get().isPressed()) {
                this.client.execute(() -> this.client.setScreen(new CustomButtonManagerScreen(this.client.currentScreen)));
            }
        }
    }
}
