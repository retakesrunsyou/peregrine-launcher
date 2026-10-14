package net.peregrine.client.core.modules;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.BoolSetting;
import net.peregrine.client.core.settings.ChoiceSetting;

/**
 * Peregrine capes and badges. Your own cape is always shown to you. Other players'
 * capes and badges come from the Peregrine cosmetics list (cosmetics.json in the
 * project), read every half hour, so friends who are on the list see each other's.
 *
 * Everyone else who plays with Peregrine shows up by themselves: the launcher tells the
 * Peregrine service which cape you picked when you press Play, and the game asks the
 * service about the players it can see (a few at a time, remembered for ten minutes), then
 * shows the falcon by their name and their cape.
 */
public final class Cosmetics extends Module {

    public static final String[] CAPES = {"None", "Falcon", "Violet", "Ember", "Starry night", "Pumpkin", "Frost"};
    /** Texture names, under assets/peregrine-client/textures/cape/. */
    public static final String[] FILES = {null, "falcon", "violet", "ember", "starry", "pumpkin", "frost"};
    public static final String REGISTRY =
            "https://raw.githubusercontent.com/retakesrunsyou/peregrine-launcher/main/cosmetics.json";
    static final long REFRESH_MS = 30 * 60_000L;
    /** The Peregrine service (passed in by the launcher), or null. */
    static final String SERVICE = System.getProperty("peregrine.service");
    static final long LIVE_MS = 10 * 60_000L;
    /** uuid -> {cape file or "" (a Peregrine player), or null (not one), time asked}. */
    private static final Map<String, Object[]> LIVE = new ConcurrentHashMap<String, Object[]>();
    private static final Set<String> WANTED = ConcurrentHashMap.newKeySet();
    private static volatile boolean asking;
    private static volatile long askedAt;

    /** Badge names in the list, and their glyph in the Peregrine font. */
    public static final String[][] BADGES = {
        {"peregrine", Hooks.LOGO}, {"dev", ""}, {"staff", ""}, {"supporter", ""}, {"early", ""},
    };

    private final ChoiceSetting cape = add(new ChoiceSetting("cape", "Your cape", 1, CAPES));
    private final BoolSetting others = add(new BoolSetting("others", "Other players' capes", true));
    private final BoolSetting badges = add(new BoolSetting("badges", "Badges next to names", true));

    private static volatile Cosmetics instance;
    /** uuid (no dashes, lower case) -> {cape file or null, badge glyph}. */
    private static volatile Map<String, String[]> players = Collections.emptyMap();
    private static volatile long fetchedAt;
    private static volatile boolean fetching;

    public Cosmetics() {
        super("cosmetics", "Cosmetics", "Peregrine capes and badges", Category.VISUALS, true);
        instance = this;
    }

    static String key(String uuid) {
        return uuid == null ? "" : uuid.replace("-", "").toLowerCase();
    }

    /** The cape texture name for a player, or null to leave their cape alone. */
    public static String cape(String uuid, boolean self) {
        Cosmetics m = instance;
        if (m == null || !m.enabled()) {
            return null;
        }
        if (self) {
            return FILES[m.cape.index];
        }
        if (!m.others.value) {
            return null;
        }
        String[] p = players.get(key(uuid));
        if (p != null && p[0] != null) {
            return p[0];
        }
        String live = live(uuid);
        return live == null || live.isEmpty() ? null : live;
    }

    /** What the service said about this player: their cape file, "" (Peregrine, no cape) or null. */
    static String live(String uuid) {
        if (SERVICE == null || SERVICE.isEmpty()) {
            return null;
        }
        String k = key(uuid);
        if (k.length() != 32) {
            return null;
        }
        Object[] e = LIVE.get(k);
        if (e == null || System.currentTimeMillis() - (Long) e[1] > LIVE_MS) {
            if (WANTED.size() < 500) {
                WANTED.add(k);
            }
        }
        return e == null ? null : (String) e[0];
    }

    /** For tests: what the service answered. */
    public static void heard(String uuid, String cape) {
        LIVE.put(key(uuid), new Object[] {cape, System.currentTimeMillis()});
        WANTED.remove(key(uuid));
    }

    public static int wanted() {
        return WANTED.size();
    }

    /** Asks the service about the players seen lately (at most every few seconds). */
    private static void ask() {
        if (asking || WANTED.isEmpty() || SERVICE == null || SERVICE.isEmpty()
                || System.currentTimeMillis() - askedAt < 4000) {
            return;
        }
        final List<String> batch = new ArrayList<String>();
        for (String k : WANTED) {
            batch.add(k);
            if (batch.size() >= 100) {
                break;
            }
        }
        asking = true;
        askedAt = System.currentTimeMillis();
        Thread t = new Thread(() -> {
            long now = System.currentTimeMillis();
            try {
                StringBuilder body = new StringBuilder("{\"uuids\":[");
                for (int i = 0; i < batch.size(); i++) {
                    body.append(i == 0 ? "\"" : ",\"").append(batch.get(i)).append('"');
                }
                body.append("]}");
                HttpURLConnection c = (HttpURLConnection) new URL(SERVICE.replaceAll("/+$", "") + "/v1/players").openConnection();
                c.setConnectTimeout(5000);
                c.setReadTimeout(8000);
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                c.setRequestProperty("User-Agent", "PeregrineClient");
                c.getOutputStream().write(body.toString().getBytes(StandardCharsets.UTF_8));
                if (c.getResponseCode() == 200) {
                    String json;
                    try (InputStream in = c.getInputStream()) {
                        ByteArrayOutputStream b = new ByteArrayOutputStream();
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0 && b.size() < 1 << 20) {
                            b.write(buf, 0, n);
                        }
                        json = new String(b.toByteArray(), StandardCharsets.UTF_8);
                    }
                    JsonObject found = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("players");
                    for (String k : batch) {
                        String cape = null;
                        if (found != null && found.has(k) && found.get(k).isJsonObject()) {
                            JsonObject o = found.getAsJsonObject(k);
                            String c2 = o.has("cape") && o.get("cape").isJsonPrimitive() ? capeFile(o.get("cape").getAsString()) : null;
                            cape = c2 == null ? "" : c2;
                        }
                        LIVE.put(k, new Object[] {cape, now});
                        WANTED.remove(k);
                    }
                }
            } catch (Exception e) {
                // offline or the service is unreachable: ask again later
            } finally {
                asking = false;
            }
        }, "Peregrine players");
        t.setDaemon(true);
        t.start();
    }

    /** The badge glyph to put before a player's name, or null. */
    public static String badge(String uuid, boolean self) {
        Cosmetics m = instance;
        if (m == null || !m.enabled() || !m.badges.value) {
            return null;
        }
        String[] p = players.get(key(uuid));
        if (p != null) {
            return p[1];
        }
        if (self) {
            return Hooks.LOGO;
        }
        return live(uuid) != null ? Hooks.LOGO : null;
    }

    static String glyph(String name) {
        for (String[] b : BADGES) {
            if (b[0].equalsIgnoreCase(name)) {
                return b[1];
            }
        }
        return Hooks.LOGO;
    }

    static String capeFile(String name) {
        if (name == null) {
            return null;
        }
        String n = name.trim().toLowerCase().replace(" ", "");
        for (int i = 1; i < FILES.length; i++) {
            if (FILES[i].equals(n) || CAPES[i].toLowerCase().replace(" ", "").equals(n)) {
                return FILES[i];
            }
        }
        return null;
    }

    /** Reads the list: {"players": {"uuid": {"cape": "falcon", "badge": "dev"}}}. Bad entries are skipped. */
    public static int load(String json) {
        Map<String, String[]> map = new HashMap<String, String[]>();
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject list = root.has("players") && root.get("players").isJsonObject()
                    ? root.getAsJsonObject("players") : new JsonObject();
            for (Map.Entry<String, JsonElement> e : list.entrySet()) {
                String id = key(e.getKey());
                if (id.length() != 32 || !e.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject o = e.getValue().getAsJsonObject();
                String c = o.has("cape") && o.get("cape").isJsonPrimitive() ? capeFile(o.get("cape").getAsString()) : null;
                String b = glyph(o.has("badge") && o.get("badge").isJsonPrimitive() ? o.get("badge").getAsString() : "peregrine");
                map.put(id, new String[] {c, b});
            }
        } catch (Exception e) {
            return -1;
        }
        players = map;
        return map.size();
    }

    private static void refresh() {
        if (fetching) {
            return;
        }
        fetching = true;
        fetchedAt = System.currentTimeMillis();
        Thread t = new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(REGISTRY).openConnection();
                c.setConnectTimeout(5000);
                c.setReadTimeout(8000);
                c.setRequestProperty("User-Agent", "PeregrineClient");
                if (c.getResponseCode() == 200) {
                    try (InputStream in = c.getInputStream()) {
                        ByteArrayOutputStream b = new ByteArrayOutputStream();
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0 && b.size() < 4 << 20) {
                            b.write(buf, 0, n);
                        }
                        load(new String(b.toByteArray(), StandardCharsets.UTF_8));
                    }
                }
            } catch (Exception e) {
                // offline or the list is unreachable: try again next time
            } finally {
                fetching = false;
            }
        }, "Peregrine cosmetics");
        t.setDaemon(true);
        t.start();
    }

    /** For tests: stop the list being fetched. */
    public static void holdList() {
        fetchedAt = Long.MAX_VALUE / 2;
    }

    @Override
    public void tick(Platform p) {
        if ((others.value || badges.value) && System.currentTimeMillis() - fetchedAt > REFRESH_MS) {
            refresh();
        }
        if (others.value || badges.value) {
            ask();
        }
    }
}
