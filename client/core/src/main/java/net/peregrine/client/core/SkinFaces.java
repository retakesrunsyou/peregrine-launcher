package net.peregrine.client.core;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * Player faces (the 8x8 front of the head, with the hat layer) from Mojang's skin
 * servers, fetched once in the background and kept as colors, so drawing them
 * needs no textures.
 */
public final class SkinFaces {

    private static final Map<String, int[]> FACES = new HashMap<String, int[]>();
    private static final Map<String, Boolean> ASKED = new HashMap<String, Boolean>();

    private SkinFaces() {
    }

    /** 64 colors (8x8, row by row), or null while it loads (or if there's no skin). */
    public static synchronized int[] get(final String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return null;
        }
        int[] f = FACES.get(uuid);
        if (f != null || ASKED.containsKey(uuid)) {
            return f;
        }
        ASKED.put(uuid, Boolean.TRUE);
        Thread t = new Thread(new Runnable() {
            public void run() {
                int[] face = fetch(uuid.replace("-", ""));
                if (face != null) {
                    synchronized (SkinFaces.class) {
                        FACES.put(uuid, face);
                    }
                }
            }
        }, "Peregrine skin");
        t.setDaemon(true);
        t.start();
        return null;
    }

    private static String read(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(5000);
        c.setReadTimeout(5000);
        try (InputStream in = c.getInputStream()) {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                b.write(buf, 0, n);
            }
            return new String(b.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    static int[] fetch(String uuid) {
        try {
            com.google.gson.JsonObject profile = com.google.gson.JsonParser.parseString(
                    read("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid)).getAsJsonObject();
            String value = null;
            for (com.google.gson.JsonElement e : profile.getAsJsonArray("properties")) {
                com.google.gson.JsonObject o = e.getAsJsonObject();
                if ("textures".equals(o.get("name").getAsString())) {
                    value = o.get("value").getAsString();
                }
            }
            if (value == null) {
                return null;
            }
            com.google.gson.JsonObject tex = com.google.gson.JsonParser.parseString(
                    new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8)).getAsJsonObject();
            String url = tex.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString()
                    .replace("http://", "https://");
            BufferedImage skin = ImageIO.read(new URL(url));
            return skin == null ? null : face(skin);
        } catch (Throwable e) {
            return null;
        }
    }

    static int[] face(BufferedImage skin) {
        int k = Math.max(1, skin.getWidth() / 64);
        int[] out = new int[64];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int base = skin.getRGB((8 + x) * k, (8 + y) * k) | 0xFF000000;
                int hat = skin.getRGB((40 + x) * k, (8 + y) * k);
                out[y * 8 + x] = (hat >>> 24) > 0x80 ? hat | 0xFF000000 : base;
            }
        }
        return out;
    }

    /** Draws a face as 8x8 squares, size pixels across. */
    public static void draw(Draw d, int[] face, int x, int y, int size) {
        d.pushScale(x, y, size / 8f);
        for (int i = 0; i < 64; i++) {
            d.rect(i % 8, i / 8, 1, 1, face[i]);
        }
        d.popScale();
    }
}
