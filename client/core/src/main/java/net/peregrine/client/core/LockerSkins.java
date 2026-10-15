package net.peregrine.client.core;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Your skins in the game's Locker: the same collection as the launcher's Locker (skins you
 * added there), and wearing one on your Minecraft account (Mojang's own skin upload, done
 * with the game's sign-in). Everyone sees the new skin once you rejoin.
 */
final class LockerSkins {

    static final class Skin {
        final String id, name, file;
        final boolean slim;
        String texture;      // loaded for drawing
        boolean legacy;

        Skin(String id, String name, String file, boolean slim) {
            this.id = id;
            this.name = name;
            this.file = file;
            this.slim = slim;
        }
    }

    private List<Skin> skins = new ArrayList<Skin>();
    private long readAt;
    volatile String status = "";
    volatile boolean busy;

    private static Path library() {
        String data = System.getProperty("peregrine.data");
        return data == null || data.isEmpty() ? null : Paths.get(data, "skins", "library");
    }

    List<Skin> list(Platform p) {
        long now = System.currentTimeMillis();
        if (now - readAt < 3000) {
            return skins;
        }
        readAt = now;
        Path lib = library();
        List<Skin> out = new ArrayList<Skin>();
        if (lib != null) {
            try {
                Path index = lib.resolve("skins.json");
                if (Files.isRegularFile(index)) {
                    JsonArray arr = JsonParser.parseString(new String(Files.readAllBytes(index), StandardCharsets.UTF_8))
                            .getAsJsonArray();
                    for (JsonElement e : arr) {
                        JsonObject o = e.getAsJsonObject();
                        String file = o.get("file").getAsString();
                        if (!Files.isRegularFile(lib.resolve(file))) {
                            continue;
                        }
                        Skin s = old(o.get("id").getAsString());
                        if (s == null) {
                            s = new Skin(o.get("id").getAsString(), o.get("name").getAsString(), file,
                                    "slim".equals(o.has("variant") ? o.get("variant").getAsString() : ""));
                        }
                        out.add(s);
                    }
                }
            } catch (Exception e) {
                // a half-written list: keep what we had
                return skins;
            }
        }
        skins = out;
        return skins;
    }

    private Skin old(String id) {
        for (Skin s : skins) {
            if (s.id.equals(id)) {
                return s;
            }
        }
        return null;
    }

    /** The skin's texture for drawing (loaded the first time). */
    String texture(Platform p, Skin s) {
        if (s.texture == null) {
            Path f = library().resolve(s.file);
            int[] size = TitleMenu.pngSize(f);
            if (size != null) {
                s.legacy = size[1] * 2 == size[0];
                s.texture = p.loadImage(f);
            }
        }
        return s.texture;
    }

    /** Puts the skin on your account, in the background. done runs when it worked. */
    void wear(final Platform p, final Skin s, final Runnable done) {
        final String token = p.accessToken();
        if (token == null) {
            status = "Sign in with Microsoft to change your skin";
            return;
        }
        if (busy) {
            return;
        }
        busy = true;
        status = "Putting it on…";
        final Path file = library().resolve(s.file);
        final String uuid = p.playerUuid();
        Thread t = new Thread(() -> {
            try {
                byte[] png = Files.readAllBytes(file);
                String boundary = "----peregrine" + Long.toHexString(System.nanoTime());
                HttpURLConnection c = (HttpURLConnection) new URL("https://api.minecraftservices.com/minecraft/profile/skins")
                        .openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(30000);
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setRequestProperty("Authorization", "Bearer " + token);
                c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                c.setRequestProperty("User-Agent", "PeregrineClient");
                try (OutputStream out = c.getOutputStream()) {
                    String head = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"variant\"\r\n\r\n"
                            + (s.slim ? "slim" : "classic") + "\r\n--" + boundary
                            + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\nContent-Type: image/png\r\n\r\n";
                    out.write(head.getBytes(StandardCharsets.UTF_8));
                    out.write(png);
                    out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                }
                int code = c.getResponseCode();
                if (code == 200 || code == 204) {
                    String data = System.getProperty("peregrine.data");
                    if (data != null && uuid != null) {  // your player in the menus wears it straight away
                        Path own = Paths.get(data, "skins", uuid.replace("-", "") + ".png");
                        Files.createDirectories(own.getParent());
                        Files.write(own, png);
                    }
                    status = "Wearing " + s.name + ". Others see it when you rejoin.";
                    done.run();
                } else if (code == 401) {
                    status = "Minecraft wants you to sign in again (in the launcher)";
                } else if (code == 429) {
                    status = "Too many changes at once. Try again in a minute";
                } else {
                    status = "Minecraft didn't accept that skin (" + code + ")";
                    InputStream err = c.getErrorStream();
                    if (err != null) {
                        ByteArrayOutputStream b = new ByteArrayOutputStream();
                        byte[] buf = new byte[2048];
                        int n;
                        while ((n = err.read(buf)) > 0 && b.size() < 4096) {
                            b.write(buf, 0, n);
                        }
                        System.out.println("[Peregrine] skin upload: " + b);
                    }
                }
            } catch (Exception e) {
                status = "Couldn't reach Minecraft: " + e.getMessage();
            } finally {
                busy = false;
            }
        }, "Peregrine skin");
        t.setDaemon(true);
        t.start();
    }
}
