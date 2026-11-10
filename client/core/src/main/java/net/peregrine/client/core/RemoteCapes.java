package net.peregrine.client.core;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Other players' own capes: downloaded once from the Peregrine service (as checked when they were
 * shared) and kept in <data>/capes/remote/<hash>/, at most 60 of them.
 */
public final class RemoteCapes {

    private static final int KEEP = 60;
    /** The most a shared cape can be: 160 frames, 24 MB (a still is up to 1024 x 512, a moving one 512 x 256). */
    public static final int MAX_FRAMES = 160;
    static final int MAX_BYTES = 24 * 1024 * 1024;
    private static final Set<String> FETCHING = ConcurrentHashMap.newKeySet();
    private static final Map<String, Long> FAILED = new ConcurrentHashMap<String, Long>();

    private RemoteCapes() {
    }

    static Path root() {
        String data = System.getProperty("peregrine.data");
        return data == null || data.isEmpty() ? null : Paths.get(data, "capes", "remote");
    }

    /** A frame's file, or null while it's still being fetched (asks for it the first time). */
    public static Path frame(String hash, int i) {
        Path dir = root();
        if (dir == null || !hash.matches("[0-9a-f]{24}")) {
            return null;
        }
        Path f = dir.resolve(hash).resolve(String.format("frame_%03d.png", i));
        if (Files.isRegularFile(dir.resolve(hash).resolve("done"))) {
            return Files.isRegularFile(f) ? f : null;
        }
        fetch(hash, dir);
        return null;
    }

    private static void fetch(final String hash, final Path dir) {
        String service = net.peregrine.client.core.modules.Cosmetics.SERVICE;
        Long failedAt = FAILED.get(hash);
        if (service == null || service.isEmpty() || (failedAt != null && System.currentTimeMillis() - failedAt < 10 * 60_000L)
                || !FETCHING.add(hash)) {
            return;
        }
        Thread t = new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(service.replaceAll("/+$", "") + "/v1/cape/" + hash).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(60000);
                c.setRequestProperty("User-Agent", "PeregrineClient");
                if (c.getResponseCode() != 200) {
                    throw new java.io.IOException("answered " + c.getResponseCode());
                }
                byte[] all;
                try (InputStream in = c.getInputStream()) {
                    java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        b.write(buf, 0, n);
                        if (b.size() > MAX_BYTES) {
                            throw new java.io.IOException("too big");
                        }
                    }
                    all = b.toByteArray();
                }
                List<byte[]> frames = split(all);
                if (frames == null) {
                    throw new java.io.IOException("not a cape");
                }
                Path d = dir.resolve(hash);
                Files.createDirectories(d);
                for (int i = 0; i < frames.size(); i++) {
                    Files.write(d.resolve(String.format("frame_%03d.png", i)), frames.get(i));
                }
                Files.write(d.resolve("done"), new byte[0]);
                tidy(dir);
            } catch (Exception e) {
                FAILED.put(hash, System.currentTimeMillis());
            } finally {
                FETCHING.remove(hash);
            }
        }, "Peregrine cape download");
        t.setDaemon(true);
        t.start();
    }

    /** The service's block ("PCAP", count, then length + ms + PNG for each frame) as PNGs. */
    public static List<byte[]> split(byte[] all) {
        if (all.length < 8 || all[0] != 'P' || all[1] != 'C' || all[2] != 'A' || all[3] != 'P') {
            return null;
        }
        ByteBuffer b = ByteBuffer.wrap(all);
        b.position(4);
        int n = b.getInt();
        if (n < 1 || n > MAX_FRAMES) {
            return null;
        }
        List<byte[]> out = new ArrayList<byte[]>();
        for (int i = 0; i < n; i++) {
            if (b.remaining() < 8) {
                return null;
            }
            int len = b.getInt();
            b.getInt();   // its time (the list of players carries it too)
            if (len < 0 || len > b.remaining()) {
                return null;
            }
            byte[] png = new byte[len];
            b.get(png);
            out.add(png);
        }
        return out;
    }

    /** Builds the block from PNGs and their times. */
    public static byte[] join(List<byte[]> pngs, List<Integer> ms) {
        int size = 8;
        for (byte[] p : pngs) {
            size += 8 + p.length;
        }
        ByteBuffer b = ByteBuffer.allocate(size);
        b.put(new byte[] {'P', 'C', 'A', 'P'});
        b.putInt(pngs.size());
        for (int i = 0; i < pngs.size(); i++) {
            b.putInt(pngs.get(i).length);
            b.putInt(i < ms.size() ? ms.get(i) : 100);
            b.put(pngs.get(i));
        }
        return b.array();
    }

    /** Keeps the newest KEEP capes. */
    private static void tidy(Path dir) {
        List<Path> all = new ArrayList<Path>();
        try (DirectoryStream<Path> s = Files.newDirectoryStream(dir)) {
            for (Path p : s) {
                all.add(p);
            }
        } catch (Exception e) {
            return;
        }
        if (all.size() <= KEEP) {
            return;
        }
        all.sort((a, b) -> Long.compare(modified(a), modified(b)));
        for (int i = 0; i < all.size() - KEEP; i++) {
            try (DirectoryStream<Path> s = Files.newDirectoryStream(all.get(i))) {
                for (Path f : s) {
                    Files.deleteIfExists(f);
                }
                Files.deleteIfExists(all.get(i));
            } catch (Exception ignored) {
                // next time
            }
        }
    }

    private static long modified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (Exception e) {
            return 0;
        }
    }
}
