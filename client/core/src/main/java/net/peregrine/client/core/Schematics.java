package net.peregrine.client.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Peregrine's schematics: load a build from the instance's schematics folder, place it in the
 * world as an outline you build into, move it, turn it, mirror it, show one layer at a time,
 * and see what's missing or wrong (and what blocks you still need).
 */
public final class Schematics {

    // outline colors
    public static final int MISSING = 0xFF4FC3F7, WRONG = 0xFFFF5252, WRONG_STATE = 0xFFFFA726, EXTRA = 0xFFE040FB, FRAME = 0xFFFFFFFF;

    /** A schematic placed in the world. */
    public static final class Placement {
        public final Schematic s;
        public final Path file;
        public int ox, oy, oz;          // where its corner is
        public int rotation;            // quarter turns clockwise (seen from above)
        public boolean mirror;          // flipped left to right (before turning)
        public boolean visible = true;
        public int layerMode;           // 0 every layer, 1 one layer, 2 every layer up to it
        public int layer;               // counted from its bottom
        public String world = "";
        private final Map<Integer, String> stateCache = new HashMap<Integer, String>();

        Placement(Schematic s, Path file) {
            this.s = s;
            this.file = file;
        }

        /** Its size in the world (turned): x, y, z. */
        public int sizeX() {
            return rotation % 2 == 0 ? s.sx : s.sz;
        }

        public int sizeZ() {
            return rotation % 2 == 0 ? s.sz : s.sx;
        }

        /** The palette index expected at a world position (0 = air or outside). */
        public int expectedAt(int wx, int wy, int wz) {
            int rx = wx - ox, ry = wy - oy, rz = wz - oz;
            if (ry < 0 || ry >= s.sy || rx < 0 || rz < 0 || rx >= sizeX() || rz >= sizeZ()) {
                return 0;
            }
            int x, z;
            switch (rotation & 3) {
                case 1:  // 90° clockwise: (x, z) -> (sz-1-z, x)
                    x = rz;
                    z = s.sz - 1 - rx;
                    break;
                case 2:
                    x = s.sx - 1 - rx;
                    z = s.sz - 1 - rz;
                    break;
                case 3:
                    x = s.sx - 1 - rz;
                    z = rx;
                    break;
                default:
                    x = rx;
                    z = rz;
            }
            if (mirror) {
                x = s.sx - 1 - x;
            }
            return s.at(x, ry, z);
        }

        /** The block state at a palette index, turned and mirrored like the placement. */
        public String state(int index) {
            int key = index * 8 + (rotation & 3) * 2 + (mirror ? 1 : 0);
            String st = stateCache.get(key);
            if (st == null) {
                st = transform(s.palette[index], rotation & 3, mirror);
                stateCache.put(key, st);
            }
            return st;
        }

        public boolean showsLayer(int ry) {
            return layerMode == 0 || (layerMode == 1 ? ry == layer : ry <= layer);
        }
    }

    private static volatile Placement current;
    private static volatile String status = "";
    // progress, from the last full check
    private static volatile int done, wrong, needed;
    private static volatile List<Object[]> missingMaterials = Collections.emptyList();
    private static long checkedAt;

    private Schematics() {
    }

    public static Placement current() {
        return current;
    }

    public static String status() {
        return status;
    }

    public static int done() {
        return done;
    }

    public static int wrong() {
        return wrong;
    }

    public static int needed() {
        return needed;
    }

    /** What's still missing, by block: {name, missing}. */
    public static List<Object[]> missing() {
        return missingMaterials;
    }

    public static Path folder() {
        return Library.gameDir().resolve("schematics");
    }

    /** Schematic files in the folder (and one folder down), newest first. */
    public static List<Path> files() {
        List<Path> out = new ArrayList<Path>();
        collect(folder(), out, 0);
        out.sort((a, b) -> {
            try {
                return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
            } catch (IOException e) {
                return 0;
            }
        });
        return out;
    }

    private static void collect(Path dir, List<Path> out, int depth) {
        if (!Files.isDirectory(dir) || depth > 1) {
            return;
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                if (Files.isDirectory(p)) {
                    collect(p, out, depth + 1);
                } else if (n.endsWith(".litematic") || n.endsWith(".schem") || n.endsWith(".schematic") || n.endsWith(".nbt")) {
                    out.add(p);
                }
            }
        } catch (IOException e) {
            // an unreadable folder: skip it
        }
    }

    /** Loads a file in the background and places it at your feet. */
    public static void load(final Path file, final Platform p) {
        status = "Loading " + file.getFileName() + "...";
        final int px = (int) Math.floor(p.x()), py = (int) Math.floor(p.y()), pz = (int) Math.floor(p.z());
        final String world = p.worldKey();
        Thread t = new Thread(() -> {
            try {
                Schematic s = Schematic.load(file);
                Placement pl = new Placement(s, file);
                pl.ox = px;
                pl.oy = py;
                pl.oz = pz;
                pl.world = world == null ? "" : world;
                current = pl;
                checkedAt = 0;
                status = "";
                save();
            } catch (Throwable e) {
                status = "Couldn't load it: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
        }, "Peregrine schematic");
        t.setDaemon(true);
        t.start();
    }

    public static void unload() {
        current = null;
        done = wrong = needed = 0;
        missingMaterials = Collections.emptyList();
        save();
    }

    public static void changed() {
        checkedAt = 0;
        save();
    }

    // ------------------------------------------------------------------ checking the build

    /**
     * What to outline near you: {x, y, z, color} for every block that's missing, wrong or in
     * the wrong state, within range (adapters call this a couple of times a second).
     */
    public static List<int[]> overlay(Platform p, int range, int limit) {
        Placement pl = current;
        List<int[]> out = new ArrayList<int[]>();
        if (pl == null || !pl.visible || !p.inWorld() || !sameWorld(pl, p)) {
            return out;
        }
        int cx = (int) Math.floor(p.x()), cy = (int) Math.floor(p.y()), cz = (int) Math.floor(p.z());
        int x0 = Math.max(pl.ox, cx - range), x1 = Math.min(pl.ox + pl.sizeX() - 1, cx + range);
        int y0 = Math.max(pl.oy, cy - range), y1 = Math.min(pl.oy + pl.s.sy - 1, cy + range);
        int z0 = Math.max(pl.oz, cz - range), z1 = Math.min(pl.oz + pl.sizeZ() - 1, cz + range);
        for (int y = y0; y <= y1 && out.size() < limit; y++) {
            if (!pl.showsLayer(y - pl.oy)) {
                continue;
            }
            for (int z = z0; z <= z1 && out.size() < limit; z++) {
                for (int x = x0; x <= x1 && out.size() < limit; x++) {
                    int want = pl.expectedAt(x, y, z);
                    if (want == 0) {
                        continue;  // air in the schematic: nothing to check (and no need to ask the world)
                    }
                    String have = p.blockStateAt(x, y, z);
                    if (have == null) {
                        continue;  // not loaded
                    }
                    int c = compare(pl, want, have);
                    if (c != OK) {
                        out.add(new int[] {x, y, z, c});
                    }
                }
            }
        }
        long now = System.currentTimeMillis();
        if (now - checkedAt > 2000) {
            checkedAt = now;
            progress(pl, p);
        }
        return out;
    }

    /** Returned by compare when the block is right. */
    public static final int OK = 0;

    /** The outline color for a position, or OK (0) when it's right. */
    public static int compare(Placement pl, int want, String have) {
        boolean haveAir = Schematic.isAir(have) || Schematic.nameOf(have).equals("minecraft:water")
                || Schematic.nameOf(have).equals("minecraft:lava") || Schematic.nameOf(have).equals("minecraft:short_grass")
                || Schematic.nameOf(have).equals("minecraft:grass") || Schematic.nameOf(have).equals("minecraft:snow");
        if (want == 0) {
            return OK;  // air in the schematic: whatever's there is fine (extra blocks aren't marked)
        }
        String expected = pl.state(want);
        if (haveAir && !Schematic.nameOf(expected).equals(Schematic.nameOf(have))) {
            return MISSING;
        }
        if (!Schematic.nameOf(expected).equals(Schematic.nameOf(have))) {
            return WRONG;
        }
        return sameState(expected, have) ? OK : WRONG_STATE;
    }

    private static final java.util.Set<String> IGNORED = new java.util.HashSet<String>(java.util.Arrays.asList(
            "waterlogged", "distance", "persistent", "power", "powered", "age", "moisture", "snowy", "triggered",
            "enabled", "lit", "open", "occupied", "has_book", "note", "instrument", "berries", "stage", "honey_level",
            "north", "east", "south", "west", "up", "down"));

    /** Do the properties that matter (facing, half, axis, type...) match? */
    static boolean sameState(String expected, String have) {
        Map<String, String> want = props(expected), got = props(have);
        for (Map.Entry<String, String> e : want.entrySet()) {
            if (IGNORED.contains(e.getKey())) {
                continue;
            }
            String g = got.get(e.getKey());
            if (g != null && !g.equals(e.getValue())) {
                return false;
            }
        }
        return true;
    }

    static Map<String, String> props(String state) {
        Map<String, String> m = new HashMap<String, String>();
        int a = state.indexOf('['), b = state.lastIndexOf(']');
        if (a < 0 || b < a) {
            return m;
        }
        for (String kv : state.substring(a + 1, b).split(",")) {
            int eq = kv.indexOf('=');
            if (eq > 0) {
                m.put(kv.substring(0, eq).trim(), kv.substring(eq + 1).trim());
            }
        }
        return m;
    }

    /** Counts what's done, wrong and still needed across the whole build (big builds: skipped). */
    private static void progress(Placement pl, Platform p) {
        long vol = (long) pl.sizeX() * pl.s.sy * pl.sizeZ();
        if (vol > 4_000_000 || pl.s.total > 60_000) {  // too big to check twice a second without a hitch
            needed = pl.s.total;
            done = -1;
            return;
        }
        int d = 0, w = 0, n = 0;
        Map<String, Integer> miss = new HashMap<String, Integer>();
        for (int y = 0; y < pl.s.sy; y++) {
            for (int z = 0; z < pl.sizeZ(); z++) {
                for (int x = 0; x < pl.sizeX(); x++) {
                    int want = pl.expectedAt(pl.ox + x, pl.oy + y, pl.oz + z);
                    if (want == 0) {
                        continue;
                    }
                    n++;
                    String have = p.blockStateAt(pl.ox + x, pl.oy + y, pl.oz + z);
                    if (have == null) {
                        continue;
                    }
                    int c = compare(pl, want, have);
                    if (c == OK) {
                        d++;
                    } else if (c == MISSING) {
                        miss.merge(Schematic.nameOf(pl.state(want)), 1, Integer::sum);
                    } else {
                        w++;
                    }
                }
            }
        }
        done = d;
        wrong = w;
        needed = n;
        List<Object[]> list = new ArrayList<Object[]>();
        for (Map.Entry<String, Integer> e : miss.entrySet()) {
            list.add(new Object[] {e.getKey(), e.getValue()});
        }
        list.sort((a, b) -> (Integer) b[1] - (Integer) a[1]);
        missingMaterials = list;
    }

    static boolean sameWorld(Placement pl, Platform p) {
        String w = p.worldKey();
        return pl.world.isEmpty() || w == null || w.equals(pl.world);
    }

    // ------------------------------------------------------------------ what you're looking at

    /**
     * The schematic block you're looking at, if it still needs doing: {name, "place"/"wrong"/"state"}
     * (walks along your view up to 6 blocks).
     */
    public static String[] lookingAt(Platform p) {
        Placement pl = current;
        if (pl == null || !pl.visible || !p.inWorld()) {
            return null;
        }
        double yaw = Math.toRadians(p.yaw()), pitch = Math.toRadians(p.pitch());
        double dx = -Math.sin(yaw) * Math.cos(pitch), dy = -Math.sin(pitch), dz = Math.cos(yaw) * Math.cos(pitch);
        double x = p.x(), y = p.y() + 1.62, z = p.z();
        for (double t = 0; t < 6; t += 0.1) {
            int bx = (int) Math.floor(x + dx * t), by = (int) Math.floor(y + dy * t), bz = (int) Math.floor(z + dz * t);
            int want = pl.expectedAt(bx, by, bz);
            String have = p.blockStateAt(bx, by, bz);
            if (have == null) {
                return null;
            }
            if (want != 0 && pl.showsLayer(by - pl.oy)) {
                int c = compare(pl, want, have);
                String name = pretty(Schematic.nameOf(pl.state(want)));
                if (c == MISSING) {
                    return new String[] {name, "place"};
                }
                if (c == WRONG) {
                    return new String[] {name, "wrong"};
                }
                if (c == WRONG_STATE) {
                    return new String[] {name, "state"};
                }
            }
            if (!Schematic.isAir(have) && !Schematic.nameOf(have).equals("minecraft:water")) {
                return null;  // a real block in the way
            }
        }
        return null;
    }

    /** "minecraft:oak_planks" -> "Oak Planks" */
    public static String pretty(String id) {
        String n = id.substring(id.indexOf(':') + 1).replace('_', ' ');
        StringBuilder b = new StringBuilder();
        for (String w : n.split(" ")) {
            if (!w.isEmpty()) {
                b.append(b.length() == 0 ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            }
        }
        return b.toString();
    }

    /** The item you place a block with ("minecraft:wall_torch" -> "minecraft:torch"). */
    public static String itemFor(String block) {
        String n = block.replace("minecraft:", "");
        if (n.startsWith("wall_") || n.contains("_wall_")) {
            n = n.replace("wall_", "");
        }
        switch (n) {
            case "redstone_wire":
                return "minecraft:redstone";
            case "tripwire":
                return "minecraft:string";
            case "water":
                return "minecraft:water_bucket";
            case "lava":
                return "minecraft:lava_bucket";
            case "fire":
                return "minecraft:flint_and_steel";
            default:
                return "minecraft:" + n;
        }
    }

    // ------------------------------------------------------------------ turning block states

    private static final String[] FACES = {"north", "east", "south", "west"};

    /** Turns a block state by quarter turns clockwise, mirrored left to right first. */
    public static String transform(String state, int rot, boolean mirror) {
        int a = state.indexOf('[');
        if (a < 0 || (rot == 0 && !mirror)) {
            return state;
        }
        String name = state.substring(0, a);
        Map<String, String> p = props(state);
        Map<String, String> out = new java.util.TreeMap<String, String>();
        for (Map.Entry<String, String> e : p.entrySet()) {
            String k = e.getKey(), v = e.getValue();
            // connections named by side (fences, walls, panes, redstone): the key moves
            int side = java.util.Arrays.asList(FACES).indexOf(k);
            if (side >= 0) {
                out.put(FACES[turn(side, rot, mirror)], v);
                continue;
            }
            int face = java.util.Arrays.asList(FACES).indexOf(v);
            if ((k.equals("facing") || k.equals("horizontal_facing")) && face >= 0) {
                out.put(k, FACES[turn(face, rot, mirror)]);
            } else if (k.equals("axis") && rot % 2 == 1 && (v.equals("x") || v.equals("z"))) {
                out.put(k, v.equals("x") ? "z" : "x");
            } else if (k.equals("rotation")) {
                try {
                    int r = Integer.parseInt(v);
                    if (mirror) {
                        r = (16 - r) % 16;
                    }
                    out.put(k, String.valueOf((r + rot * 4) % 16));
                } catch (NumberFormatException ex) {
                    out.put(k, v);
                }
            } else if (mirror && (k.equals("hinge") || k.equals("shape") || k.equals("type") && (v.equals("left") || v.equals("right")))) {
                out.put(k, v.replace("left", "\u0001").replace("right", "left").replace("\u0001", "right"));
            } else {
                out.put(k, v);
            }
        }
        StringBuilder b = new StringBuilder(name).append('[');
        boolean first = true;
        for (Map.Entry<String, String> e : out.entrySet()) {
            if (!first) {
                b.append(',');
            }
            first = false;
            b.append(e.getKey()).append('=').append(e.getValue());
        }
        return b.append(']').toString();
    }

    private static int turn(int face, int rot, boolean mirror) {
        if (mirror && (face == 1 || face == 3)) {
            face = 4 - face;  // east <-> west
        }
        return (face + rot) & 3;
    }

    // ------------------------------------------------------------------ remembering

    static Path saveFile() {
        String data = System.getProperty("peregrine.data");
        Path base = data != null && !data.isEmpty() ? Paths.get(data, "client") : Library.gameDir();
        return base.resolve("schematic.json");
    }

    static void save() {
        Placement pl = current;
        try {
            Path f = saveFile();
            if (pl == null) {
                Files.deleteIfExists(f);
                return;
            }
            JsonObject o = new JsonObject();
            o.addProperty("file", pl.file.toString());
            o.addProperty("x", pl.ox);
            o.addProperty("y", pl.oy);
            o.addProperty("z", pl.oz);
            o.addProperty("rotation", pl.rotation);
            o.addProperty("mirror", pl.mirror);
            o.addProperty("layerMode", pl.layerMode);
            o.addProperty("layer", pl.layer);
            o.addProperty("visible", pl.visible);
            o.addProperty("world", pl.world);
            Files.createDirectories(f.getParent());
            Files.write(f, o.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            // not remembered then
        }
    }

    private static String restoredFor;

    /** Puts back what was placed last time you played this world (once per world). */
    public static void restore(Platform p) {
        String w = p.worldKey();
        if (w == null || w.equals(restoredFor) || current != null) {
            return;
        }
        restoredFor = w;
        try {
            Path f = saveFile();
            if (!Files.isRegularFile(f)) {
                return;
            }
            JsonObject o = JsonParser.parseString(new String(Files.readAllBytes(f), StandardCharsets.UTF_8)).getAsJsonObject();
            if (!w.equals(Library.str(o, "world"))) {
                return;
            }
            Path file = Paths.get(Library.str(o, "file"));
            if (!Files.isRegularFile(file)) {
                return;
            }
            Placement pl = new Placement(Schematic.load(file), file);
            pl.ox = o.get("x").getAsInt();
            pl.oy = o.get("y").getAsInt();
            pl.oz = o.get("z").getAsInt();
            pl.rotation = o.get("rotation").getAsInt() & 3;
            pl.mirror = o.get("mirror").getAsBoolean();
            pl.layerMode = o.get("layerMode").getAsInt();
            pl.layer = o.get("layer").getAsInt();
            pl.visible = !o.has("visible") || o.get("visible").getAsBoolean();
            pl.world = w;
            current = pl;
        } catch (Exception e) {
            // a broken save: start fresh
        }
    }

    /** For tests: place a schematic without a file. */
    public static Placement place(Schematic s, int x, int y, int z) {
        Placement pl = new Placement(s, Paths.get(s.name));
        pl.ox = x;
        pl.oy = y;
        pl.oz = z;
        current = pl;
        return pl;
    }

    static final Map<Path, String> INFO = new ConcurrentHashMap<Path, String>();
}
