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

    public static final String[] CAPES = {"None", "Falcon", "Violet", "Ember", "Starry night", "Pumpkin", "Frost", "Your own"};
    /** Texture names, under assets/peregrine-client/textures/cape/ ("custom" is your own, made in the launcher). */
    public static final String[] FILES = {null, "falcon", "violet", "ember", "starry", "pumpkin", "frost", "custom"};
    /** "Your own" cape: a picture, GIF or video made into a cape in the launcher's Locker. Only you see it. */
    public static final int CUSTOM = 7;
    /** Animated capes: frames (files name_0.png ...) and how long each shows. */
    static final java.util.Map<String, Integer> FRAMES = new java.util.HashMap<String, Integer>();
    static final long FRAME_MS = 125;

    static {
        FRAMES.put("starry", 24);
    }

    // ---- your own cape (<data>/capes/custom: frame_000.png ... and cape.json, written by the launcher)

    private static volatile long customCheckedAt;
    private static volatile long customStamp = -1;
    /** {version, frame count, total ms}, then each frame's ms; null when there is none. */
    private static volatile int[] custom;

    static java.nio.file.Path customDir() {
        String data = System.getProperty("peregrine.data");
        return data == null || data.isEmpty() ? null : java.nio.file.Paths.get(data, "capes", "custom");
    }

    /** Reads cape.json again when it changed (checked every couple of seconds). */
    static int[] custom() {
        long now = System.currentTimeMillis();
        if (now - customCheckedAt < 2000) {
            return custom;
        }
        customCheckedAt = now;
        java.nio.file.Path dir = customDir();
        java.nio.file.Path json = dir == null ? null : dir.resolve("cape.json");
        try {
            long stamp = json == null || !java.nio.file.Files.isRegularFile(json) ? -1
                    : java.nio.file.Files.getLastModifiedTime(json).toMillis();
            if (stamp == customStamp) {
                return custom;
            }
            customStamp = stamp;
            custom = stamp < 0 ? null : parseCustom(new String(java.nio.file.Files.readAllBytes(json), StandardCharsets.UTF_8));
        } catch (Exception e) {
            custom = null;
        }
        return custom;
    }

    /** cape.json as {version, frames, total ms, ms of each frame...}; null if it makes no sense. */
    public static int[] parseCustom(String text) {
        JsonObject o = JsonParser.parseString(text).getAsJsonObject();
        int n = o.get("frames").getAsInt();
        if (n < 1 || n > 1000) {
            return null;
        }
        int[] c = new int[3 + n];
        c[0] = Math.abs(o.get("v").getAsInt());
        c[1] = n;
        com.google.gson.JsonArray ms = o.has("ms") ? o.getAsJsonArray("ms") : null;
        for (int i = 0; i < n; i++) {
            int m = ms != null && i < ms.size() ? ms.get(i).getAsInt() : 100;
            c[3 + i] = Math.max(20, m);
            c[2] += c[3 + i];
        }
        return c;
    }

    /** Which frame of your own cape shows at a moment: "custom_<version>_<frame>". */
    public static String customFrame(int[] c, long nowMs) {
        int i = 0;
        if (c[1] > 1) {
            long t = nowMs % c[2];
            while (i < c[1] - 1 && t >= c[3 + i]) {
                t -= c[3 + i];
                i++;
            }
        }
        return "custom_" + c[0] + "_" + i;
    }

    /** Whether you have made your own cape in the launcher (and this version can show it). */
    public static boolean hasCustom() {
        net.peregrine.client.core.Peregrine pg = net.peregrine.client.core.Peregrine.get();
        return pg != null && pg.platform() != null && pg.platform().supportsCustomCape() && custom() != null;
    }

    /** How many capes the menus list: "Your own" only once you've made one. */
    public static int shown() {
        return hasCustom() ? CAPES.length : CUSTOM;
    }

    /** The PNG of a frame of your own cape ("custom_<version>_<frame>"). */
    public static java.nio.file.Path customFile(String name) {
        java.nio.file.Path dir = customDir();
        int us = name.lastIndexOf('_');
        if (dir == null || us < 0) {
            return null;
        }
        int i = Integer.parseInt(name.substring(us + 1));
        return dir.resolve(String.format("frame_%03d.png", i));
    }

    /** The texture id to draw a cape's current frame in the menus, or null (nothing to draw yet). */
    public static String textureId(String file) {
        if (file == null) {
            return null;
        }
        if (!file.equals("custom")) {
            return "peregrine-client:textures/cape/" + frame(file) + ".png";
        }
        int[] c = hasCustom() ? custom() : null;
        if (c == null) {
            return null;
        }
        String name = customFrame(c, System.currentTimeMillis());
        return net.peregrine.client.core.Peregrine.get().platform().customCapeTexture(name, customFile(name));
    }

    /** The texture file to show right now for a cape (moving capes change frame as time passes). */
    public static String frame(String file) {
        if ("custom".equals(file)) {
            int[] c = hasCustom() ? custom() : null;
            return c == null ? null : customFrame(c, System.currentTimeMillis());
        }
        Integer n = file == null ? null : FRAMES.get(file);
        if (n == null) {
            return file;
        }
        return file + "_" + (System.currentTimeMillis() / FRAME_MS) % n;
    }
    public static final String REGISTRY =
            "https://raw.githubusercontent.com/retakesrunsyou/peregrine-launcher/main/cosmetics.json";
    static final long REFRESH_MS = 30 * 60_000L;
    /** The Peregrine service (passed in by the launcher), or null. */
    public static final String SERVICE = System.getProperty("peregrine.service");
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
            return frame(FILES[m.cape.index]);
        }
        if (!m.others.value) {
            return null;
        }
        String[] p = players.get(key(uuid));
        if (p != null && p[0] != null && !p[0].startsWith("custom")) {
            return frame(p[0]);
        }
        String live = live(uuid);
        return live == null || live.isEmpty() || live.startsWith("custom") ? null : frame(live);
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

    /** The Peregrine cape you wear (index into CAPES, 0 = none). */
    public static int capeIndex() {
        Cosmetics m = instance;
        return m == null || !m.enabled() ? 0 : m.cape.index;
    }

    /** Puts on a Peregrine cape (from the Locker), saves it, and lets other Peregrine players know. */
    public static void setCape(int index) {
        Cosmetics m = instance;
        if (m == null || index < 0 || index >= CAPES.length) {
            return;
        }
        m.cape.index = index;
        if (!m.enabled()) {
            m.setEnabled(true);
        }
        net.peregrine.client.core.Peregrine.get().markDirty();
        announce(index);
    }

    /** Tells the Peregrine service your new cape. It's proven to be you with the chat-signing
     *  key Minecraft gives your account: Mojang signs that key, and this signs a one-time
     *  challenge with it. Your login never leaves the game. Quietly does nothing offline. */
    private static void announce(final int index) {
        final net.peregrine.client.core.Platform p = net.peregrine.client.core.Peregrine.get().platform();
        final String token = p.accessToken();
        final String uuid = key(p.playerUuid());
        if (SERVICE == null || SERVICE.isEmpty() || token == null || token.length() < 20 || uuid.length() != 32) {
            return;
        }
        Thread t = new Thread(() -> {
            try {
                String base = SERVICE.replaceAll("/+$", "");
                String file = FILES[index] == null || index == CUSTOM ? "" : FILES[index];   // your own stays private
                JsonObject body = prove(base, token, uuid);
                body.addProperty("cape", file);
                http("PUT", base + "/v1/presence", body.toString(), null);
                heard(uuid, file);
            } catch (Exception e) {
                System.out.println("[Peregrine] cape not shared: " + e.getMessage());
            }
        }, "Peregrine cape");
        t.setDaemon(true);
        t.start();
    }

    /** {uuid, nonce, cert, sig}: what the service checks to know a request is from this account. */
    public static JsonObject prove(String base, String token, String uuid) throws Exception {
        JsonObject cert = JsonParser.parseString(http("POST", "https://api.minecraftservices.com/player/certificates",
                "", "Bearer " + token)).getAsJsonObject();
        JsonObject pair = cert.getAsJsonObject("keyPair");
        String nonce = JsonParser.parseString(http("POST", base + "/v1/challenge", "{}", null))
                .getAsJsonObject().get("nonce").getAsString();
        return proof(uuid, nonce, pair.get("privateKey").getAsString(), pair.get("publicKey").getAsString(),
                cert.get("expiresAt").getAsString(), cert.get("publicKeySignatureV2").getAsString());
    }

    /** The signed answer to a challenge, from the parts of Mojang's certificate. */
    public static JsonObject proof(String uuid, String nonce, String privatePem, String publicPem, String expiresAt,
                                   String keySig) throws Exception {
        java.security.PrivateKey priv = java.security.KeyFactory.getInstance("RSA")
                .generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(pem(privatePem)));
        java.security.Signature s = java.security.Signature.getInstance("SHA256withRSA");
        s.initSign(priv);
        s.update(("peregrine:" + nonce + ":" + uuid).getBytes(StandardCharsets.UTF_8));
        JsonObject c = new JsonObject();
        c.addProperty("publicKey", java.util.Base64.getEncoder().encodeToString(pem(publicPem)));
        c.addProperty("expiresAt", java.time.Instant.parse(expiresAt).toEpochMilli());
        c.addProperty("keySig", keySig);
        JsonObject o = new JsonObject();
        o.addProperty("uuid", uuid);
        o.addProperty("nonce", nonce);
        o.add("cert", c);
        o.addProperty("sig", java.util.Base64.getEncoder().encodeToString(s.sign()));
        return o;
    }

    private static byte[] pem(String text) {
        return java.util.Base64.getMimeDecoder().decode(text.replaceAll("-----[A-Z ]+-----", "").trim());
    }

    private static String http(String method, String url, String body, String auth) throws java.io.IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        c.setRequestMethod(method);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("User-Agent", "PeregrineClient");
        if (auth != null) {
            c.setRequestProperty("Authorization", auth);
        }
        c.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        int code = c.getResponseCode();
        if (code >= 300) {
            throw new java.io.IOException(method + " " + url.replaceAll("\\?.*", "") + " answered " + code);
        }
        if (code == 204) {
            return "";
        }
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0 && b.size() < 1 << 16) {
                b.write(buf, 0, n);
            }
            return new String(b.toByteArray(), StandardCharsets.UTF_8);
        }
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
