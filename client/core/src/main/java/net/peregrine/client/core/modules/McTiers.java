package net.peregrine.client.core.modules;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.BoolSetting;
import net.peregrine.client.core.settings.ChoiceSetting;

/**
 * MC Tiers: each player's PvP tier from mctiers.com (like "HT3" or "LT1") next to
 * their name above their head and in the tab list. Looked up once per player,
 * one at a time in the background, and remembered.
 */
public final class McTiers extends Module {

    static final String[] MODES = {"Best tier", "Sword", "Crystal (Vanilla)", "Axe", "UHC", "Pot", "NethOP", "SMP", "Mace"};
    static final String[][] KEYS = {{}, {"sword"}, {"vanilla"}, {"axe"}, {"uhc"}, {"pot"}, {"nethop", "neth_pot", "neth_op"},
        {"smp"}, {"mace"}};
    static final int[] TIER_COLORS = {0, 0xFFD24A, 0xC7D1DF, 0xD9935B, 0x9AA8D8, 0x9AA6B2};

    private final ChoiceSetting mode = add(new ChoiceSetting("mode", "Which tier", 0, MODES));
    private final BoolSetting tab = add(new BoolSetting("tab", "In the tab list", true));
    private final BoolSetting head = add(new BoolSetting("head", "Above heads", true));

    private static volatile McTiers instance;

    /** uuid -> rankings (null value: looked up, not ranked). */
    private static final Map<String, JsonObject> RANKS = new ConcurrentHashMap<String, JsonObject>();
    private static final Map<String, Long> ASKED = new ConcurrentHashMap<String, Long>();
    private static final ArrayDeque<String> QUEUE = new ArrayDeque<String>();
    private static final JsonObject NONE = new JsonObject();
    private static Thread worker;

    public McTiers() {
        super("mc_tiers", "MC Tiers", "Players' PvP tiers from mctiers.com next to their names", Category.UTILITY, false);
        instance = this;
    }

    public static boolean inTab() {
        McTiers m = instance;
        return m != null && m.enabled() && m.tab.value;
    }

    public static boolean overhead() {
        McTiers m = instance;
        return m != null && m.enabled() && m.head.value;
    }

    /** {tier, pos (0 high, 1 low), retired 0/1} for the chosen mode, or null. */
    private static int[] pick(JsonObject ranks, int modeIndex) {
        if (ranks == null || ranks == NONE) {
            return null;
        }
        int[] best = null;
        for (Map.Entry<String, JsonElement> e : ranks.entrySet()) {
            if (modeIndex > 0) {
                boolean match = false;
                for (String k : KEYS[modeIndex]) {
                    match |= e.getKey().equalsIgnoreCase(k);
                }
                if (!match) {
                    continue;
                }
            }
            if (!e.getValue().isJsonObject()) {
                continue;
            }
            JsonObject r = e.getValue().getAsJsonObject();
            if (!r.has("tier")) {
                continue;
            }
            int tier = r.get("tier").getAsInt();
            int pos = r.has("pos") ? r.get("pos").getAsInt() : 1;
            int retired = r.has("retired") && r.get("retired").getAsBoolean() ? 1 : 0;
            int score = tier * 2 + pos;
            if (best == null || score < best[0] * 2 + best[1]) {
                best = new int[] {tier, pos, retired};
            }
        }
        return best;
    }

    /** "HT3" style text for a player, or null (not ranked, still loading, or the feature is off). */
    public static String text(String uuid) {
        McTiers m = instance;
        if (m == null || !m.enabled() || uuid == null || uuid.isEmpty()) {
            return null;
        }
        String id = uuid.replace("-", "").toLowerCase();
        JsonObject r = RANKS.get(id);
        if (r == null) {
            request(id);
            return null;
        }
        int[] t = pick(r, m.mode.index);
        if (t == null || t[0] < 1 || t[0] > 5) {
            return null;
        }
        if (net.peregrine.client.core.Hooks.tierHits < 1000) {
            net.peregrine.client.core.Hooks.tierHits++;
        }
        return (t[2] == 1 ? "R" : "") + (t[1] == 0 ? "HT" : "LT") + t[0];
    }

    public static int color(String uuid) {
        McTiers m = instance;
        JsonObject r = uuid == null ? null : RANKS.get(uuid.replace("-", "").toLowerCase());
        int[] t = m == null ? null : pick(r, m.mode.index);
        return t == null || t[0] < 1 || t[0] > 5 ? 0xAAAAAA : TIER_COLORS[t[0]];
    }

    /** For tests: give a player's rankings directly. */
    public static void put(String uuid, JsonObject rankings) {
        RANKS.put(uuid.replace("-", "").toLowerCase(), rankings == null ? NONE : rankings);
    }

    private static void request(String id) {
        Long at = ASKED.get(id);
        long now = System.currentTimeMillis();
        if (at != null && now - at < 5 * 60_000L) {
            return;  // asked recently (a failed lookup is tried again after 5 minutes)
        }
        ASKED.put(id, now);
        synchronized (QUEUE) {
            if (QUEUE.size() < 200) {
                QUEUE.add(id);
            }
            if (worker == null || !worker.isAlive()) {
                worker = new Thread(McTiers::work, "Peregrine tiers");
                worker.setDaemon(true);
                worker.start();
            }
        }
    }

    private static void work() {
        while (true) {
            String id;
            synchronized (QUEUE) {
                id = QUEUE.poll();
                if (id == null) {
                    worker = null;
                    return;
                }
            }
            JsonObject r = fetch(id);
            if (r != null) {
                RANKS.put(id, r);
            }
            try {
                Thread.sleep(300);  // gentle on the site
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    /** The player's rankings object, NONE if they aren't ranked, null if the lookup failed. */
    static JsonObject fetch(String id) {
        String dashed = id.length() == 32 ? id.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5") : id;
        for (String url : new String[] {"https://mctiers.com/api/v2/profile/" + dashed,
                "https://mctiers.com/api/profile/" + id}) {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(5000);
                c.setReadTimeout(5000);
                c.setRequestProperty("User-Agent", "PeregrineClient");
                int code = c.getResponseCode();
                if (code == 404) {
                    return NONE;
                }
                if (code != 200) {
                    continue;
                }
                try (InputStream in = c.getInputStream()) {
                    java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        b.write(buf, 0, n);
                    }
                    JsonObject o = JsonParser.parseString(new String(b.toByteArray(), StandardCharsets.UTF_8)).getAsJsonObject();
                    return o.has("rankings") && o.get("rankings").isJsonObject() ? o.getAsJsonObject("rankings") : NONE;
                }
            } catch (Exception e) {
                // try the next address, or again later
            }
        }
        return null;
    }

    @Override
    public void tick(Platform p) {
    }
}
