package net.peregrine.client.core;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * A small reader for Minecraft's NBT files (schematics are NBT, usually gzipped). Compounds
 * become Maps, lists become Lists, arrays stay arrays, numbers become boxed numbers.
 */
public final class Nbt {

    private Nbt() {
    }

    /** Reads a (possibly gzipped) NBT file: the root compound. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> read(InputStream raw) throws IOException {
        BufferedInputStream in = new BufferedInputStream(raw);
        in.mark(2);
        int b1 = in.read(), b2 = in.read();
        in.reset();
        InputStream src = b1 == 0x1F && b2 == 0x8B ? new GZIPInputStream(in, 65536) : in;
        DataInputStream d = new DataInputStream(new BufferedInputStream(src, 65536));
        int type = d.readUnsignedByte();
        if (type != 10) {
            throw new IOException("not an NBT file");
        }
        d.readUTF();  // the root's name
        return (Map<String, Object>) payload(d, 10, 0);
    }

    private static Object payload(DataInputStream d, int type, int depth) throws IOException {
        if (depth > 512) {
            throw new IOException("NBT nested too deep");
        }
        switch (type) {
            case 1:
                return d.readByte();
            case 2:
                return d.readShort();
            case 3:
                return d.readInt();
            case 4:
                return d.readLong();
            case 5:
                return d.readFloat();
            case 6:
                return d.readDouble();
            case 7: {
                int n = len(d);
                byte[] a = new byte[n];
                d.readFully(a);
                return a;
            }
            case 8:
                return readString(d);
            case 9: {
                int t = d.readUnsignedByte();
                int n = len(d);
                List<Object> l = new ArrayList<Object>(Math.min(n, 4096));
                for (int i = 0; i < n; i++) {
                    l.add(payload(d, t, depth + 1));
                }
                return l;
            }
            case 10: {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                while (true) {
                    int t = d.readUnsignedByte();
                    if (t == 0) {
                        return m;
                    }
                    String name = readString(d);
                    m.put(name, payload(d, t, depth + 1));
                }
            }
            case 11: {
                int n = len(d);
                int[] a = new int[n];
                for (int i = 0; i < n; i++) {
                    a[i] = d.readInt();
                }
                return a;
            }
            case 12: {
                int n = len(d);
                long[] a = new long[n];
                for (int i = 0; i < n; i++) {
                    a[i] = d.readLong();
                }
                return a;
            }
            default:
                throw new IOException("unknown NBT tag " + type);
        }
    }

    private static int len(DataInputStream d) throws IOException {
        int n = d.readInt();
        if (n < 0 || n > 64 * 1024 * 1024) {
            throw new IOException("NBT array too big");
        }
        return n;
    }

    private static String readString(DataInputStream d) throws IOException {
        int n = d.readUnsignedShort();
        byte[] b = new byte[n];
        d.readFully(b);
        return new String(b, StandardCharsets.UTF_8);  // (modified UTF-8; fine for block names)
    }

    // ---- helpers for reading what came out

    @SuppressWarnings("unchecked")
    public static Map<String, Object> compound(Map<String, Object> m, String key) {
        Object o = m == null ? null : m.get(key);
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Map<String, Object> m, String key) {
        Object o = m == null ? null : m.get(key);
        return o instanceof List ? (List<Object>) o : new ArrayList<Object>();
    }

    public static int num(Map<String, Object> m, String key, int def) {
        Object o = m == null ? null : m.get(key);
        return o instanceof Number ? ((Number) o).intValue() : def;
    }

    public static String str(Map<String, Object> m, String key, String def) {
        Object o = m == null ? null : m.get(key);
        return o instanceof String ? (String) o : def;
    }

    /** {x, y, z} from a compound with x, y, z (or a list of 3 numbers). */
    public static int[] xyz(Object o) {
        if (o instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) o;
            return new int[] {num(m, "x", 0), num(m, "y", 0), num(m, "z", 0)};
        }
        if (o instanceof List && ((List<?>) o).size() >= 3) {
            List<?> l = (List<?>) o;
            return new int[] {((Number) l.get(0)).intValue(), ((Number) l.get(1)).intValue(), ((Number) l.get(2)).intValue()};
        }
        if (o instanceof int[] && ((int[]) o).length >= 3) {
            return (int[]) o;
        }
        return new int[3];
    }

    /** A block state as Minecraft writes it in text: "minecraft:oak_stairs[facing=north,half=top]". */
    public static String blockState(Map<String, Object> entry) {
        String name = str(entry, "Name", "minecraft:air");
        Map<String, Object> props = compound(entry, "Properties");
        if (props == null || props.isEmpty()) {
            return name;
        }
        StringBuilder b = new StringBuilder(name).append('[');
        boolean first = true;
        for (Map.Entry<String, Object> e : new java.util.TreeMap<String, Object>(props).entrySet()) {
            if (!first) {
                b.append(',');
            }
            first = false;
            b.append(e.getKey()).append('=').append(e.getValue());
        }
        return b.append(']').toString();
    }
}
