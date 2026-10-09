package net.peregrine.client.core;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Saves which modules are on and where HUD items sit, in
 * config/peregrine-client.json. Uses only old Gson APIs so it works on every
 * Minecraft version's bundled Gson.
 */
final class Config {

    private Config() {
    }

    @SuppressWarnings("deprecation")
    static void load(Peregrine p) {
        Path file = p.platform().configFile();
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = new JsonParser().parse(r).getAsJsonObject();
            if (root.has("accent")) {
                p.setAccent((int) Long.parseLong(root.get("accent").getAsString(), 16));
            }
            JsonObject mods = root.has("modules") ? root.getAsJsonObject("modules") : new JsonObject();
            for (Map.Entry<String, JsonElement> e : mods.entrySet()) {
                Module m = p.module(e.getKey());
                if (m == null || !e.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject o = e.getValue().getAsJsonObject();
                if (o.has("x") && m instanceof HudModule) {
                    ((HudModule) m).fx = o.get("x").getAsFloat();
                    ((HudModule) m).fy = o.get("y").getAsFloat();
                }
                if (m instanceof HudModule) {
                    HudModule h = (HudModule) m;
                    if (o.has("scale")) {
                        h.setScaleKeepingCorner(o.get("scale").getAsFloat());
                    }
                    if (o.has("text")) {
                        h.textColor = (int) Long.parseLong(o.get("text").getAsString(), 16);
                    }
                    if (o.has("label")) {
                        h.labelColor = (int) Long.parseLong(o.get("label").getAsString(), 16);
                    }
                    if (o.has("background")) {
                        h.background = o.get("background").getAsBoolean();
                    }
                    if (o.has("backgroundAlpha")) {
                        h.backgroundAlpha = Math.max(0, Math.min(255, o.get("backgroundAlpha").getAsInt()));
                    }
                    if (o.has("shadow")) {
                        h.shadow = o.get("shadow").getAsBoolean();
                    }
                }
                if (o.has("enabled")) {
                    m.setEnabledQuietly(o.get("enabled").getAsBoolean());
                }
            }
        } catch (Exception e) {
            // A broken file shouldn't stop the game; start from defaults instead.
            System.err.println("[Peregrine] Couldn't read " + file + ", using defaults: " + e);
        }
    }

    static void save(Peregrine p) {
        JsonObject root = new JsonObject();
        root.addProperty("accent", Integer.toHexString(p.accent()));
        JsonObject mods = new JsonObject();
        for (Module m : p.modules()) {
            JsonObject o = new JsonObject();
            o.addProperty("enabled", m.enabled());
            if (m instanceof HudModule) {
                HudModule h = (HudModule) m;
                o.addProperty("x", h.fx);
                o.addProperty("y", h.fy);
                if (h.scale != 1f) {
                    o.addProperty("scale", h.scale);
                }
                if (h.textColor != 0) {
                    o.addProperty("text", Integer.toHexString(h.textColor));
                }
                if (h.labelColor != 0) {
                    o.addProperty("label", Integer.toHexString(h.labelColor));
                }
                if (!h.background) {
                    o.addProperty("background", false);
                }
                if (h.backgroundAlpha != HudModule.DEFAULT_BG_ALPHA) {
                    o.addProperty("backgroundAlpha", h.backgroundAlpha);
                }
                if (!h.shadow) {
                    o.addProperty("shadow", false);
                }
            }
            mods.add(m.id, o);
        }
        root.add("modules", mods);

        Path file = p.platform().configFile();
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(root, w);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            System.err.println("[Peregrine] Couldn't save settings: " + e);
        }
    }
}
