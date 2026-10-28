package net.peregrine.client.core.modules;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.Skies;
import net.peregrine.client.core.settings.BoolSetting;
import net.peregrine.client.core.settings.ChoiceSetting;
import net.peregrine.client.core.settings.SliderSetting;

/**
 * Custom sky: replaces the night sky with one of Peregrine's animated skies. The pictures are
 * loaded straight from Peregrine's own files (copied once to Peregrine's folder), not through
 * Minecraft's resources, so no resource pack can replace or break them.
 */
public final class CustomSky extends Module {

    public final ChoiceSetting sky = add(new ChoiceSetting("sky", "Sky", 0, Skies.NAMES));
    public final SliderSetting brightness = add(new SliderSetting("brightness", "Brightness", 30f, 150f, 5f, 100f, "%.0f%%"));
    public final SliderSetting speed = add(new SliderSetting("speed", "Animation speed", 10f, 300f, 5f, 50f, "%.0f%%"));
    public final BoolSetting turning = add(new BoolSetting("turning", "Stars turn with the time of day (off: the sky stays still)", false));
    public final BoolSetting moon = add(new BoolSetting("moon", "Realistic moon (in its real phase)", true));
    public final BoolSetting clouds = add(new BoolSetting("clouds", "Night clouds drifting low in the sky", true));
    public final BoolSetting daytime = add(new BoolSetting("daytime", "Show it faintly during the day too", false));

    private final Map<String, String> loaded = new HashMap<String, String>();
    private int preloaded;

    public CustomSky() {
        super("custom_sky", "Custom sky", "Animated skies: Milky Way, northern lights, meteor shower, black hole, shooting stars",
                Category.VISUALS, false);
    }

    @Override
    public void tick(Platform p) {
        apply(p);
        // load the pictures a couple per tick, so turning it on never freezes the game
        if (preloaded < Skies.TEXTURES.length) {
            for (int i = 0; i < 2 && preloaded < Skies.TEXTURES.length; i++) {
                load(p, Skies.TEXTURES[preloaded++]);
            }
        }
    }

    private void apply(final Platform p) {
        Skies.brightness = brightness.value / 100f;
        Skies.speed = speed.value / 100f;
        Skies.moon = moon.value;
        Skies.clouds = clouds.value;
        Skies.daytime = daytime.value;
        Skies.turning = turning.value;
        Skies.moonPhase = (int) Math.floorMod(p.worldDay() - 1, 8L);
        Skies.day = p.worldDay();
        Skies.loader = name -> load(p, name);
        Skies.sky = sky.index;
    }

    @Override
    protected void onEnable() {
        preloaded = 0;
    }

    @Override
    protected void onDisable() {
        Skies.sky = -1;
    }

    /** A sky picture as a texture, loaded from Peregrine's own files (never a resource pack's). */
    private String load(Platform p, String name) {
        if (loaded.containsKey(name)) {
            return loaded.get(name);
        }
        String id = null;
        try (InputStream in = CustomSky.class.getResourceAsStream("/assets/peregrine-client/textures/sky/" + name)) {
            if (in != null) {
                Path dir = p.configFile().resolveSibling("peregrine-sky");
                Files.createDirectories(dir);
                Path f = dir.resolve(name);
                byte[] data = readAll(in);
                if (!Files.exists(f) || Files.size(f) != data.length) {
                    Path tmp = dir.resolve(name + ".tmp");
                    Files.write(tmp, data);
                    Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
                }
                id = p.loadImage(f);
            }
        } catch (Exception e) {
            id = null;  // fall back to the picture's resource id
        }
        loaded.put(name, id);
        return id;
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
