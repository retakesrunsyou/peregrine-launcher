package net.peregrine.client.core;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The performance test: for a minute the camera flies the same path round where you stand
 * (two laps, looking out over the world, rising and dipping), while every frame's time is
 * recorded. Then: average FPS, 1% and 0.1% lows, stutters, garbage-collector pauses. Results
 * are kept in peregrine-benchmarks.txt beside the config so runs can be compared (vanilla,
 * Sodium, Peregrine's own boosts, different settings). Only the camera moves: you stay put, so
 * it's fine on servers.
 */
public final class Benchmark {

    public static final Benchmark INSTANCE = new Benchmark();

    static final double WARMUP = 5, LENGTH = 60;
    private static final int MAX = 400_000;

    private volatile boolean running;
    private long startNs, lastNs;
    private final long[] frames = new long[MAX];
    private int count;
    private long gcCount0, gcTime0;
    private double cx, cy, cz;
    private boolean centred;
    private float radius = 48;
    private String setup = "";
    private List<String> result;
    private long resultAt;

    private Benchmark() {
    }

    public boolean running() {
        return running;
    }

    /** Starts a run (renderDistance in chunks: the path stays well inside it). */
    public void start(Platform p) {
        count = 0;
        lastNs = 0;
        centred = false;
        radius = Math.max(16, Math.min(96, p.renderDistance() * 16 * 0.45f));
        setup = "Minecraft " + p.minecraftVersion() + ", render distance " + p.renderDistance() + ", " + collector() + ", " + mods(p);
        listen();
        result = null;
        startNs = System.nanoTime();
        running = true;
    }

    public void stop() {
        running = false;
    }

    private double elapsed(long now) {
        return (now - startNs) / 1e9;
    }

    /** Every frame (from the game loop). */
    public void frame(long now) {
        if (!running) {
            return;
        }
        double t = elapsed(now);
        if (t >= WARMUP) {
            if (lastNs != 0 && count < MAX) {
                frames[count++] = now - lastNs;
            } else if (lastNs == 0) {
                long[] gc = gc();
                gcCount0 = gc[0];
                gcTime0 = gc[1];
                longestPause = 0;
            }
            lastNs = now;
        }
        if (t >= WARMUP + LENGTH) {
            running = false;
            finish();
        }
    }

    /**
     * Where the camera is this frame: {x, y, z, yaw, pitch} (Minecraft's degrees), around the
     * eye position given; null when no test is running.
     */
    public double[] camera(double ex, double ey, double ez) {
        if (!running) {
            return null;
        }
        if (!centred) {
            cx = ex;
            cy = ey;
            cz = ez;
            centred = true;
        }
        double t = elapsed(System.nanoTime());
        double lap = (t / (WARMUP + LENGTH)) * 2 * Math.PI * 2;          // two laps
        double r = radius * (0.75 + 0.25 * Math.sin(t * 0.37));
        double x = cx + Math.cos(lap) * r;
        double z = cz + Math.sin(lap) * r;
        double y = cy + 14 + 10 * Math.sin(t * 0.21);
        // look outward and ahead along the path, a little down, sweeping
        double heading = lap + Math.PI * 0.30;
        double dx = Math.cos(heading), dz = Math.sin(heading);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) (12 + 10 * Math.sin(t * 0.29));
        return new double[] {x, y, z, yaw, pitch};
    }

    /** {pauses, paused ms} so far: collections that stop the game (not the background ones). */
    private static long[] gc() {
        long c = 0, ms = 0;
        for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (isPause(b.getName())) {
                c += Math.max(0, b.getCollectionCount());
                ms += Math.max(0, b.getCollectionTime());
            }
        }
        return new long[] {c, ms};
    }

    /** G1 "Young/Old Generation" and ZGC "Pauses" stop the game; "Concurrent"/"Cycles" run beside it. */
    static boolean isPause(String collector) {
        String n = collector.toLowerCase();
        return !n.contains("concurrent") && !n.contains("cycles");
    }

    private static volatile long longestPause;
    private static boolean listening;

    /** Listens for each collection to find the longest pause (where the JVM supports it). */
    private static void listen() {
        if (listening) {
            return;
        }
        listening = true;
        try {
            for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) {
                if (!(b instanceof javax.management.NotificationEmitter)) {
                    continue;
                }
                ((javax.management.NotificationEmitter) b).addNotificationListener((note, hb) -> {
                    try {
                        Object data = note.getUserData();
                        if (!(data instanceof javax.management.openmbean.CompositeData)) {
                            return;
                        }
                        javax.management.openmbean.CompositeData cd = (javax.management.openmbean.CompositeData) data;
                        String name = String.valueOf(cd.get("gcName"));
                        Object info = cd.get("gcInfo");
                        if (isPause(name) && info instanceof javax.management.openmbean.CompositeData) {
                            long ms = ((Number) ((javax.management.openmbean.CompositeData) info).get("duration")).longValue();
                            if (ms > longestPause) {
                                longestPause = ms;
                            }
                        }
                    } catch (RuntimeException ignored) {
                        // not this JVM's format
                    }
                }, null, null);
            }
        } catch (RuntimeException | LinkageError e) {
            // no notifications here: the longest pause stays unknown
        }
    }

    private static String collector() {
        StringBuilder sb = new StringBuilder();
        for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) {
            String n = b.getName();
            String kind = n.startsWith("ZGC") ? "ZGC" : n.startsWith("G1") ? "G1" : n.startsWith("Shenandoah") ? "Shenandoah" : n;
            if (sb.indexOf(kind) < 0) {
                sb.append(sb.length() == 0 ? "" : "/").append(kind);
            }
        }
        long max = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        return sb + " collector, " + max + " MB";
    }

    private static String mods(Platform p) {
        try {
            Path dir = p.gameDir().resolve("mods");
            if (!Files.isDirectory(dir)) {
                return "no mods";
            }
            List<String> names = new ArrayList<String>();
            try (java.util.stream.Stream<Path> s = Files.list(dir)) {
                s.forEach(f -> {
                    String n = f.getFileName().toString().toLowerCase();
                    if (n.endsWith(".jar")) {
                        names.add(n.replaceAll("[-_+]?(mc)?\\d.*$", ""));
                    }
                });
            }
            java.util.Collections.sort(names);
            return names.isEmpty() ? "no mods" : "mods: " + String.join(", ", names);
        } catch (Exception e) {
            return "mods unknown";
        }
    }

    private void finish() {
        int n = count;
        if (n < 10) {
            result = Arrays.asList("Benchmark", "Too few frames to measure");
            resultAt = System.currentTimeMillis();
            return;
        }
        long[] f = Arrays.copyOf(frames, n);
        long total = 0;
        for (long v : f) {
            total += v;
        }
        long[] sorted = f.clone();
        Arrays.sort(sorted);
        double avgFps = n / (total / 1e9);
        double low1 = lowFps(sorted, 0.01), low01 = lowFps(sorted, 0.001);
        long median = sorted[n / 2];
        long stutterAt = Math.max(median * 5 / 2, median + 20_000_000L);
        int stutters = 0;
        for (long v : f) {
            if (v > stutterAt) {
                stutters++;
            }
        }
        long[] gc = gc();
        long gcCount = gc[0] - gcCount0, gcMs = gc[1] - gcTime0;
        double worst = sorted[n - 1] / 1e6;
        List<String> r = new ArrayList<String>();
        r.add("Benchmark");
        r.add(String.format("%.0f FPS average", avgFps));
        r.add(String.format("%.0f FPS 1%% low   %.0f FPS 0.1%% low", low1, low01));
        r.add(String.format("%d stutters   worst frame %.0f ms", stutters, worst));
        r.add(longestPause > 0
                ? String.format("%d memory clean-up pauses, %d ms in all, longest %d ms", gcCount, gcMs, longestPause)
                : String.format("%d memory clean-up pauses, %d ms in all", gcCount, gcMs));
        result = r;
        resultAt = System.currentTimeMillis();
        save(r);
    }

    /** The average FPS of the slowest share of frames. */
    private static double lowFps(long[] sorted, double share) {
        int k = Math.max(1, (int) Math.round(sorted.length * share));
        long sum = 0;
        for (int i = sorted.length - k; i < sorted.length; i++) {
            sum += sorted[i];
        }
        return k / (sum / 1e9);
    }

    private void save(List<String> r) {
        try {
            Platform p = Peregrine.get().platform();
            Path file = p.configFile().resolveSibling("peregrine-benchmarks.txt");
            StringBuilder sb = new StringBuilder();
            sb.append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date()))
                    .append("  ").append(setup).append('\n');
            for (int i = 1; i < r.size(); i++) {
                sb.append("    ").append(r.get(i)).append('\n');
            }
            sb.append('\n');
            Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            // the numbers are still on screen
        }
    }

    /** The progress bar while running, and the results card for a while after. */
    public void render(Draw d, Platform p) {
        int sw = p.screenWidth();
        if (running) {
            double t = elapsed(System.nanoTime());
            String what = t < WARMUP ? "Benchmark: getting ready..." : String.format("Benchmark: %d s left", (int) Math.ceil(WARMUP + LENGTH - t));
            int w = Math.max(160, d.width(what) + 20), x = (sw - w) / 2, y = 8;
            d.roundRect(x, y, w, 24, 6, 0xB0101216);
            d.text(what, x + (w - d.width(what)) / 2, y + 5, Theme.TEXT, false);
            float pr = (float) Math.max(0, Math.min(1, (t - WARMUP) / LENGTH));
            d.roundRect(x + 8, y + 17, w - 16, 3, 1, 0x40FFFFFF);
            d.roundRect(x + 8, y + 17, Math.round((w - 16) * pr), 3, 1, 0xFF000000 | (Peregrine.get().accent() & 0xFFFFFF));
            return;
        }
        List<String> r = result;
        if (r == null || System.currentTimeMillis() - resultAt > 20000) {
            return;
        }
        int w = 10;
        for (String s : r) {
            w = Math.max(w, d.width(s));
        }
        w += 24;
        int h = 14 + r.size() * 11, x = (sw - w) / 2, y = 8;
        d.roundRect(x, y, w, h, 6, 0xC0101216);
        for (int i = 0; i < r.size(); i++) {
            d.text(r.get(i), x + 12, y + 7 + i * 11, i == 0 ? (0xFF000000 | Peregrine.get().accent()) : i == 1 ? Theme.TEXT : Theme.MUTED, false);
        }
    }

    /** The last run's lines (null if none). */
    public List<String> result() {
        return result;
    }
}
