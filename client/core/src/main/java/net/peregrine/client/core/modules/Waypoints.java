package net.peregrine.client.core.modules;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.Theme;
import net.peregrine.client.core.WorldLabels;
import net.peregrine.client.core.settings.ActionSetting;
import net.peregrine.client.core.settings.BoolSetting;
import net.peregrine.client.core.settings.ChoiceSetting;
import net.peregrine.client.core.settings.KeySetting;
import net.peregrine.client.core.settings.SliderSetting;
import net.peregrine.client.core.settings.TextSetting;

/**
 * Waypoints: saved spots per server (and world and dimension), shown over the
 * world with their distance, and kept at the screen edge when they're off to
 * the side. Add one where you stand from the menu or with a key; a death point
 * is added for you.
 */
public final class Waypoints extends Module {

    static final String[] COLOR_NAMES = {"Cyan", "Green", "Yellow", "Orange", "Red", "Pink", "Purple", "White"};
    static final int[] COLORS = {0xFF4FE3E8, 0xFF3BE070, 0xFFFFD23C, 0xFFFF8A2E, 0xFFFF4B4B, 0xFFFF4FB4, 0xFFA25BFF, 0xFFF2F2F2};

    /** One saved spot. */
    static final class Point {
        String name;
        int x, y, z;
        String world;
        String dim;
        int color;
        boolean death;
    }

    private final TextSetting name = add(new TextSetting("name", "Name for the next one", "", 24, "Home"));
    private final ChoiceSetting color = add(new ChoiceSetting("color", "Its color", 0, COLOR_NAMES));
    private final ActionSetting addHere = add(new ActionSetting("add", "Add a waypoint where you stand", () -> "Add",
            this::addHere));
    private final KeySetting quickKey = add(new KeySetting("key", "Key to add one where you stand"));
    private final ActionSetting removeClosest = add(new ActionSetting("remove", "Remove the closest one", () -> "Remove",
            this::removeClosest));
    private final BoolSetting deaths = add(new BoolSetting("deaths", "Mark where you die", true));
    private final BoolSetting distance = add(new BoolSetting("distance", "Show the distance", true));
    private final BoolSetting edges = add(new BoolSetting("edges", "Keep them at the screen edge when off to the side", true));
    private final SliderSetting size = add(new SliderSetting("size", "Label size", 50f, 150f, 5f, 75f, "%.0f%%"));
    private final ActionSetting clear = add(new ActionSetting("clear", "Remove all in this world", () -> "Clear",
            this::clearWorld));

    private List<Point> points;
    private boolean wasAlive = true;
    private double lastX, lastY, lastZ;

    public Waypoints() {
        super("waypoints", "Waypoints", "Save spots and see them over the world with their distance",
                Category.UTILITY, false);
    }

    private Path file(Platform p) {
        return p.configFile().resolveSibling("peregrine-waypoints.json");
    }

    private List<Point> points(Platform p) {
        if (points == null) {
            points = new ArrayList<Point>();
            try {
                Path f = file(p);
                if (Files.isRegularFile(f)) {
                    List<Point> loaded = new Gson().fromJson(new String(Files.readAllBytes(f), StandardCharsets.UTF_8),
                            new TypeToken<List<Point>>() { }.getType());
                    if (loaded != null) {
                        points.addAll(loaded);
                    }
                }
            } catch (Exception e) {
                System.err.println("[Peregrine] couldn't read waypoints: " + e);
            }
        }
        return points;
    }

    private void save(Platform p) {
        try {
            Path f = file(p);
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            Files.write(tmp, new GsonBuilder().setPrettyPrinting().create().toJson(points).getBytes(StandardCharsets.UTF_8));
            Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            System.err.println("[Peregrine] couldn't save waypoints: " + e);
        }
    }

    private boolean here(Platform p, Point w) {
        return p.worldKey().equals(w.world) && p.dimension().equals(w.dim);
    }

    /** Adds a waypoint at your feet. */
    public Point add(Platform p, String label, int argb, boolean death) {
        if (!p.inWorld()) {
            return null;
        }
        Point w = new Point();
        w.name = label;
        w.x = (int) Math.floor(p.x());
        w.y = (int) Math.floor(p.y());
        w.z = (int) Math.floor(p.z());
        w.world = p.worldKey();
        w.dim = p.dimension();
        w.color = argb;
        w.death = death;
        points(p).add(w);
        save(p);
        return w;
    }

    /** Adds a waypoint at a spot (used by the self-test). */
    public void addAt(Platform p, String label, int x, int y, int z) {
        Point w = add(p, label, COLORS[0], false);
        if (w != null) {
            w.x = x;
            w.y = y;
            w.z = z;
            save(p);
        }
    }

    private void addHere() {
        Platform p = platform();
        int n = 1;
        for (Point w : points(p)) {
            if (here(p, w)) {
                n++;
            }
        }
        String label = name.value.trim().isEmpty() ? "Waypoint " + n : name.value.trim();
        add(p, label, COLORS[color.index], false);
        name.value = "";
    }

    private void removeClosest() {
        Platform p = platform();
        Point best = null;
        double bd = Double.MAX_VALUE;
        for (Point w : points(p)) {
            if (here(p, w)) {
                double d = dist(p, w);
                if (d < bd) {
                    bd = d;
                    best = w;
                }
            }
        }
        if (best != null) {
            points(p).remove(best);
            save(p);
        }
    }

    private void clearWorld() {
        Platform p = platform();
        points(p).removeIf(w -> p.worldKey().equals(w.world));
        save(p);
    }

    public int count(Platform p) {
        int n = 0;
        for (Point w : points(p)) {
            if (here(p, w)) {
                n++;
            }
        }
        return n;
    }

    private static double dist(Platform p, Point w) {
        double dx = w.x + 0.5 - p.x(), dy = w.y - p.y(), dz = w.z + 0.5 - p.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Override
    public void keyPressed(int key, Platform p) {
        if (key == quickKey.key && key >= 0) {
            addHere();
        }
    }

    @Override
    public void tick(Platform p) {
        float hp = p.health();
        boolean alive = hp != 0f;
        if (alive) {
            lastX = p.x();
            lastY = p.y();
            lastZ = p.z();
        }
        if (wasAlive && !alive && deaths.value) {
            points(p).removeIf(w -> w.death && p.worldKey().equals(w.world));
            Point w = add(p, "Death", 0xFFFF4B4B, true);
            if (w != null) {  // where you were standing, not where the camera fell to
                w.x = (int) Math.floor(lastX);
                w.y = (int) Math.floor(lastY);
                w.z = (int) Math.floor(lastZ);
                save(p);
            }
        }
        wasAlive = alive;
    }

    @Override
    public void renderWorld(Draw d, Platform p) {
        float scale = size.value / 100f;
        for (Point w : points(p)) {
            if (!here(p, w)) {
                continue;
            }
            double dd = dist(p, w);
            if (dd < 2.5) {
                continue;  // standing on it: no label in your face
            }
            String text = distance.value ? w.name + "  " + Math.round(dd) + "m" : w.name;
            WorldLabels.tag(d, p, w.x + 0.5, w.y + 1.6, w.z + 0.5, text, Theme.TEXT, w.color, scale, edges.value);
        }
    }
}
