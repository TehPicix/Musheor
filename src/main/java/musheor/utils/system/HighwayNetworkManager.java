package musheor.utils.system;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

public class HighwayNetworkManager {
    private static final String HIGHWAY_URL = "https://highways.musheck.dev";
    private static final HighwayNetworkManager.HighwayServerConfig FALLBACK_2B2T = new HighwayNetworkManager.HighwayServerConfig(
        new double[]{
            500.0,
            1000.0,
            1500.0,
            2000.0,
            2500.0,
            7500.0,
            55000.0,
            62500.0,
            100000.0,
            125000.0,
            250000.0,
            500000.0,
            750000.0,
            1000000.0,
            1250000.0,
            1875000.0,
            2500000.0,
            3750000.0
        },
        new double[]{2500.0, 5000.0, 25000.0, 50000.0, 125000.0, 250000.0, 500000.0, 3750000.0}
    );
    private static final Map<String, HighwayNetworkManager.HighwayServerConfig> FALLBACK_CONFIGS;
    public static final HighwayNetworkManager INSTANCE = new HighwayNetworkManager();
    private final AtomicReference<Map<String, HighwayNetworkManager.HighwayServerConfig>> configs;
    private final CopyOnWriteArrayList<Runnable> onLoadedCallbacks;
    private volatile boolean loaded;

    private HighwayNetworkManager() {
        this.configs = new AtomicReference<>(FALLBACK_CONFIGS);
        this.onLoadedCallbacks = new CopyOnWriteArrayList<>();
        this.loaded = false;
        this.fetchRemoteHighways();
    }

    public void fetchRemoteHighways() {
        CompletableFuture.runAsync(
            () -> {
                try {
                    HttpURLConnection conn = (HttpURLConnection)URI.create("https://highways.musheck.dev").toURL().openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    conn.setRequestProperty("User-Agent", "MusheorAddon");
                    if (conn.getResponseCode() != 200) {
                        System.err.println("[HighwayNetworkManager] HTTP " + conn.getResponseCode() + " — keeping fallback configs.");
                        return;
                    }

                    InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8);
                    JsonObject root = (JsonObject)new Gson().fromJson(reader, JsonObject.class);
                    reader.close();
                    Map<String, HighwayNetworkManager.HighwayServerConfig> parsed = new LinkedHashMap<>();

                    for (Entry<String, JsonElement> entry : root.entrySet()) {
                        String serverKey = entry.getKey();
                        JsonObject serverData = entry.getValue().getAsJsonObject();
                        double[] ring = this.parseDoubleArray(serverData, "ring_distances");
                        double[] diamond = this.parseDoubleArray(serverData, "diamond_distances");
                        HighwayNetworkManager.HighwayServerConfig existing = this.configs
                            .get()
                            .getOrDefault(serverKey, HighwayNetworkManager.HighwayServerConfig.EMPTY);
                        if (ring == null || ring.length == 0) {
                            ring = existing.ringDistances();
                        }

                        if (diamond == null || diamond.length == 0) {
                            diamond = existing.diamondDistances();
                        }

                        parsed.put(serverKey, new HighwayNetworkManager.HighwayServerConfig(ring, diamond));
                    }

                    Map<String, HighwayNetworkManager.HighwayServerConfig> merged = new LinkedHashMap<>(this.configs.get());
                    merged.putAll(parsed);
                    this.configs.set(Collections.unmodifiableMap(merged));
                    this.loaded = true;
                    System.out.println("[HighwayNetworkManager] Loaded configs for servers: " + this.configs.get().keySet());

                    for (Runnable cb : this.onLoadedCallbacks) {
                        try {
                            cb.run();
                        } catch (Exception var12) {
                            System.err.println("[HighwayNetworkManager] Callback error: " + var12.getMessage());
                        }
                    }
                } catch (Exception var13) {
                    System.err.println("[HighwayNetworkManager] Error fetching remote data, using fallback.");
                    var13.printStackTrace();
                }
            }
        );
    }

    private double[] parseDoubleArray(JsonObject root, String key) {
        if (!root.has(key)) {
            return null;
        } else {
            JsonArray array = root.getAsJsonArray(key);
            double[] result = new double[array.size()];

            for (int i = 0; i < array.size(); i++) {
                JsonElement el = array.get(i);

                try {
                    result[i] = el.getAsDouble();
                } catch (NumberFormatException var8) {
                    System.err.println("[HighwayNetworkManager] Skipping invalid number in '" + key + "': " + el);
                }
            }

            return result;
        }
    }

    public static HighwayNetworkManager getInstance() {
        return INSTANCE;
    }

    public boolean isLoaded() {
        return this.loaded;
    }

    public HighwayNetworkManager.HighwayServerConfig getConfig(String serverKey) {
        Map<String, HighwayNetworkManager.HighwayServerConfig> map = this.configs.get();
        return map == null ? HighwayNetworkManager.HighwayServerConfig.EMPTY : map.getOrDefault(serverKey, HighwayNetworkManager.HighwayServerConfig.EMPTY);
    }

    public List<String> getServerNames() {
        List<String> names = new ArrayList<>(this.configs.get().keySet());
        Collections.sort(names);
        return names.isEmpty() ? Collections.emptyList() : names;
    }

    public void onLoaded(Runnable callback) {
        if (this.loaded) {
            callback.run();
        } else {
            this.onLoadedCallbacks.add(callback);
        }
    }

    public double[] getRingDistances(String serverKey) {
        return this.getConfig(serverKey).ringDistances();
    }

    public double[] getDiamondDistances(String serverKey) {
        return this.getConfig(serverKey).diamondDistances();
    }

    static {
        Map<String, HighwayNetworkManager.HighwayServerConfig> m = new LinkedHashMap<>();
        m.put("2b2t", FALLBACK_2B2T);
        FALLBACK_CONFIGS = Collections.unmodifiableMap(m);
    }

    public record HighwayServerConfig(double[] ringDistances, double[] diamondDistances) {
        public static final HighwayNetworkManager.HighwayServerConfig EMPTY = new HighwayNetworkManager.HighwayServerConfig(new double[0], new double[0]);
    }
}
