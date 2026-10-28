package musheor.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.orbit.EventHandler;
import musheor.utils.hud.screen.EchestConfigScreen;
import net.minecraft.command.CommandSource;

public class EchestConfigCommand extends Command {
    private boolean pendingOpen = false;
    private int openDelayTicks = 0;

    public EchestConfigCommand() {
        super("erc", "Opens the echest-configuration screen");
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            this.scheduleOpen();
            return 1;
        });
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.pendingOpen && --this.openDelayTicks == 0) {
            mc.execute(() -> mc.setScreen(new EchestConfigScreen(null)));
            this.pendingOpen = false;
        }
    }

    private void scheduleOpen() {
        this.pendingOpen = true;
        this.openDelayTicks = 2;
    }
}
