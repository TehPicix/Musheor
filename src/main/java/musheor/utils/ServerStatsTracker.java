package musheor.utils;

import com.google.gson.JsonObject;
import java.net.http.HttpResponse;
import java.util.concurrent.RejectedExecutionException;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.network.Http;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.orbit.EventHandler;
import musheor.compat.VersionHelper;
import musheor.modules.automation.HighwayBuilder;
import musheor.utils.highway.HighwayState;
import musheor.utils.system.MusheorSystem;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket.Mode;
import net.minecraft.stat.StatHandler;
import net.minecraft.stat.Stats;

public class ServerStatsTracker {
    int tick = 0;
    private static volatile String lastStamp = null;

    public static void requestUpdate() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && mc.player.networkHandler != null) {
            mc.player.networkHandler.sendPacket(new ClientStatusC2SPacket(Mode.REQUEST_STATS));
        }
    }

    public static void syncToState() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            StatHandler stats = mc.player.getStatHandler();
            HighwayState state = HighwayState.getInstance();
            int mined = stats.getStat(Stats.MINED, Blocks.OBSIDIAN);
            int placed = stats.getStat(Stats.USED, Items.OBSIDIAN);
            int echests = stats.getStat(Stats.MINED, Blocks.ENDER_CHEST);
            int nrack = stats.getStat(Stats.MINED, Blocks.NETHERRACK);
            state.setLifetimeObsidianMined(mined);
            state.setLifetimeObsidianPlaced(placed);
            state.setLifetimeEchestsMined(echests);
            state.setLifetimeNetherrackMined(nrack);
            state.setLifetimeBlocksMined(mined + echests + nrack);
        }
    }

    public static void refresh() {
        requestUpdate();
        syncToState();
        MusheorSystem.debug("Stats requested, updating...");
    }

    public static void upload(String apiKey) {
        if (apiKey != null && !apiKey.isEmpty()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null && mc.getSession() != null) {
                ServerInfo server = mc.getCurrentServerEntry();
                if (server != null) {
                    HighwayState state = HighwayState.getInstance();
                    JsonObject p = new JsonObject();
                    p.addProperty("uuid", mc.player.getUuidAsString());
                    p.addProperty("username", mc.getSession().getUsername());
                    p.addProperty("server", server.address);
                    p.addProperty("obsidianMined", state.getLifetimeObsidianMined());
                    p.addProperty("obsidianPlaced", state.getLifetimeObsidianPlaced());
                    p.addProperty("echestsMined", state.getLifetimeEchestsMined());
                    p.addProperty("netherrackMined", state.getLifetimeNetherrackMined());
                    p.addProperty("blocksMined", state.getLifetimeBlocksMined());
                    p.addProperty("timestamp", System.currentTimeMillis());
                    String body = VersionHelper.get().getGson().toJson(p);
                    String stamp = apiKey.hashCode() + ":" + body;
                    if (!stamp.equals(lastStamp)) {
                        lastStamp = stamp;

                        try {
                            MeteorExecutor.execute(() -> send(body, apiKey));
                        } catch (RejectedExecutionException var8) {
                        }
                    }
                }
            }
        }
    }

    private static void send(String body, String apiKey) {
        HttpResponse<String> res = Http.post("https://api.musheck.dev/stats")
            .bearer(apiKey)
            .bodyJson(body)
            .exceptionHandler(e -> MusheorSystem.debug("Stats upload failed: " + e.getMessage()))
            .sendStringResponse();
        int code = res.statusCode();
        if (code / 100 != 2) {
            lastStamp = null;
            if (code != 401 && code != 403) {
                MusheorSystem.debug("Stats upload rejected: HTTP " + code);
            } else {
                MusheorSystem.debug("Stats rejected: key invalid, blacklisted, or UUID not yours.");
            }
        }
    }

    @EventHandler
    private void onTick(Pre event) {
        if (HighwayBuilderLogic.mc.player != null && HighwayBuilderLogic.mc.world != null) {
            if (Modules.get().get(HighwayBuilder.class).isActive()) {
                if (++this.tick % (MusheorSystem.Manager.postIntervalSeconds.get() * 20) == 0) {
                    syncToState();
                    upload(MusheorSystem.Manager.apiKey.get());
                    requestUpdate();
                    this.tick = 0;
                }
            }
        }
    }
}
