package net.peregrine.client.core;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * Server icons for the HUD. The game hands over the icon it already has (servers
 * in your list); for anything else (direct connect) the icon is asked from the
 * server once, in the background, the same way the server list does it.
 * Icons are kept as a small grid of colors, so drawing needs no textures.
 */
public final class ServerIcons {

    /** Grid size: icons are drawn from this many cells a side. */
    public static final int CELLS = 22;

    private static final Map<String, int[]> ICONS = new HashMap<String, int[]>();
    private static final Map<String, Boolean> ASKED = new HashMap<String, Boolean>();

    private ServerIcons() {
    }

    /** The icon for this server as CELLS x CELLS ARGB colors, or null (none yet). */
    public static synchronized int[] get(String address, byte[] gameIcon) {
        if (address == null) {
            return null;
        }
        int[] icon = ICONS.get(address);
        if (icon != null) {
            return icon;
        }
        if (gameIcon != null) {
            icon = grid(gameIcon);
            if (icon != null) {
                ICONS.put(address, icon);
                return icon;
            }
        }
        if (!ASKED.containsKey(address)) {
            ASKED.put(address, Boolean.TRUE);
            final String a = address;
            Thread t = new Thread(new Runnable() {
                public void run() {
                    byte[] png = ping(a);
                    int[] g = png == null ? null : grid(png);
                    if (g != null) {
                        synchronized (ServerIcons.class) {
                            ICONS.put(a, g);
                        }
                    }
                }
            }, "Peregrine server icon");
            t.setDaemon(true);
            t.start();
        }
        return null;
    }

    /** A PNG shrunk to CELLS x CELLS (each cell the average of its pixels). */
    static int[] grid(byte[] png) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
            if (img == null) {
                return null;
            }
            int w = img.getWidth(), h = img.getHeight();
            int[] out = new int[CELLS * CELLS];
            for (int cy = 0; cy < CELLS; cy++) {
                for (int cx = 0; cx < CELLS; cx++) {
                    int x0 = cx * w / CELLS, x1 = Math.max(x0 + 1, (cx + 1) * w / CELLS);
                    int y0 = cy * h / CELLS, y1 = Math.max(y0 + 1, (cy + 1) * h / CELLS);
                    long a = 0, r = 0, g = 0, b = 0, n = 0;
                    for (int y = y0; y < y1; y++) {
                        for (int x = x0; x < x1; x++) {
                            int c = img.getRGB(x, y);
                            int ca = c >>> 24;
                            a += ca;
                            r += ((c >> 16) & 0xFF) * ca;
                            g += ((c >> 8) & 0xFF) * ca;
                            b += (c & 0xFF) * ca;
                            n++;
                        }
                    }
                    int ca = (int) (a / n);
                    out[cy * CELLS + cx] = a == 0 ? 0 : (ca << 24) | (int) (r / a) << 16 | (int) (g / a) << 8 | (int) (b / a);
                }
            }
            return out;
        } catch (Throwable e) {  // no image support in this Java, or a broken icon
            return null;
        }
    }

    // ---------------------------------------------------------------- ping

    /** host[:port] -> where to connect, following the server's SRV record like the game does. */
    static InetSocketAddress resolve(String address) {
        String host = address.trim();
        int port = 25565;
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(':') == colon) {
            try {
                port = Integer.parseInt(host.substring(colon + 1));
                return new InetSocketAddress(host.substring(0, colon), port);
            } catch (NumberFormatException ignored) {
                // not a port after all
            }
        }
        try {
            Hashtable<String, String> env = new Hashtable<String, String>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            javax.naming.directory.DirContext ctx = new javax.naming.directory.InitialDirContext(env);
            javax.naming.directory.Attribute srv = ctx.getAttributes("_minecraft._tcp." + host, new String[] {"SRV"}).get("SRV");
            if (srv != null) {
                String[] parts = srv.get().toString().split(" ");  // priority weight port target
                String target = parts[3].endsWith(".") ? parts[3].substring(0, parts[3].length() - 1) : parts[3];
                return new InetSocketAddress(target, Integer.parseInt(parts[2]));
            }
        } catch (Throwable ignored) {
            // no SRV record: the plain address
        }
        return new InetSocketAddress(host, port);
    }

    private static void varInt(DataOutputStream out, int v) throws IOException {
        while ((v & ~0x7F) != 0) {
            out.writeByte((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.writeByte(v);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int v = 0, shift = 0, b;
        do {
            b = in.readUnsignedByte();
            v |= (b & 0x7F) << shift;
            shift += 7;
            if (shift > 35) {
                throw new IOException("bad varint");
            }
        } while ((b & 0x80) != 0);
        return v;
    }

    private static void string(DataOutputStream out, String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        varInt(out, b.length);
        out.write(b);
    }

    /** The server's status JSON (as the multiplayer list asks it), or null. */
    static String status(String address) {
        InetSocketAddress to = resolve(address);
        Socket s = new Socket();
        try {
            s.connect(to, 4000);
            s.setSoTimeout(4000);
            ByteArrayOutputStream hs = new ByteArrayOutputStream();
            DataOutputStream h = new DataOutputStream(hs);
            h.writeByte(0);                    // handshake
            varInt(h, -1);                     // "any version": just asking for status
            string(h, to.getHostString());
            h.writeShort(to.getPort());
            varInt(h, 1);                      // next: status
            DataOutputStream out = new DataOutputStream(s.getOutputStream());
            varInt(out, hs.size());
            out.write(hs.toByteArray());
            out.writeByte(1);                  // status request: length 1, id 0
            out.writeByte(0);
            out.flush();
            DataInputStream in = new DataInputStream(s.getInputStream());
            readVarInt(in);                    // packet length
            if (readVarInt(in) != 0) {
                return null;
            }
            byte[] json = new byte[readVarInt(in)];
            in.readFully(json);
            return new String(json, StandardCharsets.UTF_8);
        } catch (Throwable e) {
            return null;
        } finally {
            try {
                s.close();
            } catch (IOException ignored) {
                // closed anyway
            }
        }
    }

    static byte[] ping(String address) {
        String json = status(address);
        if (json == null) {
            return null;
        }
        int i = json.indexOf("data:image/png;base64,");
        if (i < 0) {
            return null;
        }
        int start = i + "data:image/png;base64,".length();
        int end = json.indexOf('"', start);
        if (end < 0) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(json.substring(start, end).replace("\\n", "").replace("\\/", "/").replace("\n", ""));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
