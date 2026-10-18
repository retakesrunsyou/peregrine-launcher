package net.peregrine.client.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A build loaded from a file: Litematica (.litematic), Sponge/WorldEdit (.schem) or a vanilla
 * structure (.nbt). Kept as a box of blocks: a palette of block states and one palette index per
 * block (0 is always air). Rotating and mirroring happen when it's placed (see Placement).
 */
public final class Schematic {

    public final String name;
    public final int sx, sy, sz;
    public final String[] palette;   // block states, palette[0] = "minecraft:air"
    public final int[] blocks;       // index (y * sz + z) * sx + x -> palette index
    public final int total;          // blocks that aren't air

    Schematic(String name, int sx, int sy, int sz, String[] palette, int[] blocks) {
        this.name = name;
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.palette = palette;
        this.blocks = blocks;
        int n = 0;
        for (int b : blocks) {
            if (b != 0) {
                n++;
            }
        }
        this.total = n;
    }

    public int at(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) {
            return 0;
        }
        return blocks[(y * sz + z) * sx + x];
    }

    /** How many of each block (by name, without its state), most first. */
    public List<Object[]> materials() {
        Map<String, Integer> counts = new HashMap<String, Integer>();
        for (int b : blocks) {
            if (b != 0) {
                counts.merge(nameOf(palette[b]), 1, Integer::sum);
            }
        }
        List<Object[]> out = new ArrayList<Object[]>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            out.add(new Object[] {e.getKey(), e.getValue()});
        }
        out.sort((a, b) -> (Integer) b[1] - (Integer) a[1]);
        return out;
    }

    /** "minecraft:oak_stairs[facing=north]" -> "minecraft:oak_stairs" */
    public static String nameOf(String state) {
        int i = state.indexOf('[');
        return i < 0 ? state : state.substring(0, i);
    }

    static boolean isAir(String state) {
        String n = nameOf(state);
        return n.equals("minecraft:air") || n.equals("minecraft:cave_air") || n.equals("minecraft:void_air")
                || n.equals("minecraft:structure_void");
    }

    // ------------------------------------------------------------------ loading

    public static Schematic load(Path file) throws IOException {
        String fn = file.getFileName().toString();
        String lower = fn.toLowerCase(Locale.ROOT);
        Map<String, Object> root;
        try (InputStream in = Files.newInputStream(file)) {
            root = Nbt.read(in);
        }
        String name = fn.replaceAll("\\.[^.]+$", "");
        if (lower.endsWith(".litematic")) {
            return litematic(name, root);
        }
        if (lower.endsWith(".schem") || lower.endsWith(".schematic")) {
            return sponge(name, root);
        }
        return structure(name, root);
    }

    /** Litematica: one or more regions, each with a palette and tightly packed bits. */
    static Schematic litematic(String name, Map<String, Object> root) throws IOException {
        Map<String, Object> meta = Nbt.compound(root, "Metadata");
        if (meta != null && Nbt.str(meta, "Name", "").length() > 0) {
            name = Nbt.str(meta, "Name", name);
        }
        Map<String, Object> regions = Nbt.compound(root, "Regions");
        if (regions == null || regions.isEmpty()) {
            throw new IOException("This litematic has no regions");
        }
        // the box around every region
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        List<int[]> boxes = new ArrayList<int[]>();
        List<Map<String, Object>> regs = new ArrayList<Map<String, Object>>();
        for (Object o : regions.values()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> r = (Map<String, Object>) o;
            int[] pos = Nbt.xyz(r.get("Position"));
            int[] size = Nbt.xyz(r.get("Size"));
            int[] lo = new int[3], dim = new int[3];
            for (int i = 0; i < 3; i++) {
                dim[i] = Math.abs(size[i]);
                lo[i] = size[i] < 0 ? pos[i] + size[i] + 1 : pos[i];
            }
            boxes.add(new int[] {lo[0], lo[1], lo[2], dim[0], dim[1], dim[2]});
            regs.add(r);
            minX = Math.min(minX, lo[0]);
            minY = Math.min(minY, lo[1]);
            minZ = Math.min(minZ, lo[2]);
            maxX = Math.max(maxX, lo[0] + dim[0]);
            maxY = Math.max(maxY, lo[1] + dim[1]);
            maxZ = Math.max(maxZ, lo[2] + dim[2]);
        }
        int sx = maxX - minX, sy = maxY - minY, sz = maxZ - minZ;
        check(sx, sy, sz);
        Palette pal = new Palette();
        int[] blocks = new int[sx * sy * sz];
        for (int ri = 0; ri < regs.size(); ri++) {
            Map<String, Object> r = regs.get(ri);
            int[] box = boxes.get(ri);
            List<Object> plist = Nbt.list(r, "BlockStatePalette");
            int[] map = new int[plist.size()];
            for (int i = 0; i < plist.size(); i++) {
                @SuppressWarnings("unchecked")
                Map<String, Object> e = (Map<String, Object>) plist.get(i);
                map[i] = pal.id(Nbt.blockState(e));
            }
            Object bs = r.get("BlockStates");
            if (!(bs instanceof long[]) || map.length == 0) {
                continue;
            }
            long[] arr = (long[]) bs;
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(map.length - 1));
            long mask = (1L << bits) - 1;
            int w = box[3], h = box[4], l = box[5];
            long vol = (long) w * h * l;
            for (long i = 0; i < vol; i++) {
                long start = i * bits;
                int a = (int) (start >>> 6);
                int off = (int) (start & 63);
                if (a >= arr.length) {
                    break;
                }
                long v = arr[a] >>> off;
                if (off + bits > 64 && a + 1 < arr.length) {
                    v |= arr[a + 1] << (64 - off);
                }
                int pi = (int) (v & mask);
                if (pi <= 0 || pi >= map.length) {
                    continue;
                }
                int x = (int) (i % w), z = (int) ((i / w) % l), y = (int) (i / ((long) w * l));
                int gx = box[0] - minX + x, gy = box[1] - minY + y, gz = box[2] - minZ + z;
                blocks[(gy * sz + gz) * sx + gx] = map[pi];
            }
        }
        return new Schematic(name, sx, sy, sz, pal.array(), blocks);
    }

    /** Sponge (.schem, WorldEdit): version 2 at the top, version 3 inside "Schematic". */
    static Schematic sponge(String name, Map<String, Object> root) throws IOException {
        Map<String, Object> s = Nbt.compound(root, "Schematic") != null ? Nbt.compound(root, "Schematic") : root;
        int w = Nbt.num(s, "Width", 0) & 0xFFFF, h = Nbt.num(s, "Height", 0) & 0xFFFF, l = Nbt.num(s, "Length", 0) & 0xFFFF;
        check(w, h, l);
        Map<String, Object> holder = Nbt.compound(s, "Blocks") != null ? Nbt.compound(s, "Blocks") : s;
        Map<String, Object> palette = Nbt.compound(holder, "Palette");
        Object data = holder.containsKey("Data") ? holder.get("Data") : holder.get("BlockData");
        if (palette == null || !(data instanceof byte[])) {
            throw new IOException("Old (pre-1.13) schematics aren't supported, save it again with WorldEdit");
        }
        Palette pal = new Palette();
        int max = 0;
        for (Object v : palette.values()) {
            max = Math.max(max, ((Number) v).intValue());
        }
        int[] map = new int[max + 1];
        for (Map.Entry<String, Object> e : palette.entrySet()) {
            map[((Number) e.getValue()).intValue()] = pal.id(e.getKey());
        }
        byte[] bytes = (byte[]) data;
        int[] blocks = new int[w * h * l];
        int i = 0, p = 0;
        while (p < bytes.length && i < blocks.length) {  // varints, one per block
            int value = 0, shift = 0, b;
            do {
                b = bytes[p++];
                value |= (b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0 && p < bytes.length && shift < 35);
            // Sponge order: x + z * width + y * width * length (the same as ours)
            blocks[i++] = value >= 0 && value < map.length ? map[value] : 0;
        }
        return new Schematic(name, w, h, l, pal.array(), blocks);
    }

    /** A vanilla structure (.nbt, from structure blocks). */
    static Schematic structure(String name, Map<String, Object> root) throws IOException {
        int[] size = Nbt.xyz(root.get("size"));
        check(size[0], size[1], size[2]);
        List<Object> plist = Nbt.list(root, "palette");
        if (plist.isEmpty() && !Nbt.list(root, "palettes").isEmpty()) {
            Object first = Nbt.list(root, "palettes").get(0);
            plist = first instanceof List ? castList(first) : plist;
        }
        Palette pal = new Palette();
        int[] map = new int[plist.size()];
        for (int i = 0; i < plist.size(); i++) {
            @SuppressWarnings("unchecked")
            Map<String, Object> e = (Map<String, Object>) plist.get(i);
            map[i] = pal.id(Nbt.blockState(e));
        }
        int[] blocks = new int[size[0] * size[1] * size[2]];
        for (Object o : Nbt.list(root, "blocks")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> b = (Map<String, Object>) o;
            int[] pos = Nbt.xyz(b.get("pos"));
            int st = Nbt.num(b, "state", -1);
            if (st < 0 || st >= map.length || pos[0] < 0 || pos[1] < 0 || pos[2] < 0
                    || pos[0] >= size[0] || pos[1] >= size[1] || pos[2] >= size[2]) {
                continue;
            }
            blocks[(pos[1] * size[2] + pos[2]) * size[0] + pos[0]] = map[st];
        }
        return new Schematic(name, size[0], size[1], size[2], pal.array(), blocks);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> castList(Object o) {
        return (List<Object>) o;
    }

    private static void check(int x, int y, int z) throws IOException {
        if (x <= 0 || y <= 0 || z <= 0) {
            throw new IOException("This schematic is empty");
        }
        if ((long) x * y * z > 32L * 1024 * 1024) {
            throw new IOException("This schematic is too big (over 32 million blocks)");
        }
    }

    /** Block states to small numbers; air is always 0. */
    static final class Palette {
        private final Map<String, Integer> ids = new HashMap<String, Integer>();
        private final List<String> states = new ArrayList<String>();

        Palette() {
            states.add("minecraft:air");
        }

        int id(String state) {
            if (isAir(state)) {
                return 0;
            }
            Integer i = ids.get(state);
            if (i == null) {
                i = states.size();
                states.add(state);
                ids.put(state, i);
            }
            return i;
        }

        String[] array() {
            return states.toArray(new String[0]);
        }
    }
}
