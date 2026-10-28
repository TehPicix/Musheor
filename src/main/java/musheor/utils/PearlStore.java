package musheor.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.math.BlockPos;

public class PearlStore {
    private static final Path PEARLS_FILE = Path.of("meteor-client/pearls.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<PearlStore.PearlEntry> pearls = new ArrayList<>();

    public static List<PearlStore.PearlEntry> getPearls() {
        return pearls;
    }

    public static void addPearl(String ownerName, BlockPos trapdoorPos, int entityId) {
        for (PearlStore.PearlEntry r : pearls) {
            if (r.trapdoorPos.equals(trapdoorPos)) {
                r.owner = ownerName;
                r.entityId = entityId;
                save();
                return;
            }
        }

        pearls.add(new PearlStore.PearlEntry(ownerName, trapdoorPos, entityId));
        save();
    }

    public static void updateEntityId(BlockPos trapdoorPos, int entityId) {
        for (PearlStore.PearlEntry r : pearls) {
            if (r.trapdoorPos.equals(trapdoorPos)) {
                r.entityId = entityId;
                return;
            }
        }
    }

    public static PearlStore.PearlEntry findByTrapdoor(BlockPos trapdoorPos) {
        for (PearlStore.PearlEntry r : pearls) {
            if (r.trapdoorPos.equals(trapdoorPos)) {
                return r;
            }
        }

        return null;
    }

    public static void removeOwner(String playerName) {
        pearls.removeIf(r -> r.owner.equalsIgnoreCase(playerName));
        save();
    }

    public static void clear() {
        pearls.clear();
        save();
    }

    public static List<PearlStore.PearlEntry> getByOwner(String ownerName) {
        List<PearlStore.PearlEntry> result = new ArrayList<>();

        for (PearlStore.PearlEntry r : pearls) {
            if (r.owner.equalsIgnoreCase(ownerName)) {
                result.add(r);
            }
        }

        return result;
    }

    public static void save() {
        try {
            Files.createDirectories(PEARLS_FILE.getParent());
            JsonArray arr = new JsonArray();

            for (PearlStore.PearlEntry r : pearls) {
                JsonObject obj = new JsonObject();
                obj.addProperty("owner", r.owner);
                obj.addProperty("entityId", r.entityId);
                obj.addProperty("x", r.trapdoorPos.getX());
                obj.addProperty("y", r.trapdoorPos.getY());
                obj.addProperty("z", r.trapdoorPos.getZ());
                arr.add(obj);
            }

            Files.writeString(PEARLS_FILE, GSON.toJson(arr));
        } catch (IOException var4) {
            var4.printStackTrace();
        }
    }

    public static void load() {
        pearls.clear();
        if (Files.exists(PEARLS_FILE)) {
            try {
                String json = Files.readString(PEARLS_FILE);

                for (JsonElement el : JsonParser.parseString(json).getAsJsonArray()) {
                    JsonObject obj = el.getAsJsonObject();
                    String owner = obj.get("owner").getAsString();
                    int entityId = obj.get("entityId").getAsInt();
                    int x = obj.get("x").getAsInt();
                    int y = obj.get("y").getAsInt();
                    int z = obj.get("z").getAsInt();
                    pearls.add(new PearlStore.PearlEntry(owner, new BlockPos(x, y, z), entityId));
                }
            } catch (Exception var10) {
                var10.printStackTrace();
            }
        }
    }

    public static class PearlEntry {
        public String owner;
        public BlockPos trapdoorPos;
        public int entityId;

        public PearlEntry(String ownerName, BlockPos trapdoorPos, int entityId) {
            this.owner = ownerName;
            this.trapdoorPos = trapdoorPos;
            this.entityId = entityId;
        }
    }
}
