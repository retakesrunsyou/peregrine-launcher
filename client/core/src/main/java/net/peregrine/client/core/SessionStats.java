package net.peregrine.client.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

/**
 * How well the game ran this session, for the launcher's Library page: the average FPS while in a
 * world and the "1% low" (how low it dips). Written to peregrine-stats.json in the game folder
 * every minute and when the game closes. The first half minute in a world is left out, so the
 * moment of joining doesn't drag the average down.
 */
final class SessionStats {

    private static final long SKIP_MS = 30_000;
    private static final int MAX = 4 * 3600;   // samples kept (one a second): four hours

    private final float[] samples = new float[MAX];
    private int count, next;
    private long worldSince, lastSample, lastWrite;

    void tick(Platform p) {
        long now = System.currentTimeMillis();
        if (!p.inWorld()) {
            worldSince = 0;
            return;
        }
        if (worldSince == 0) {
            worldSince = now;
        }
        if (now - worldSince < SKIP_MS || now - lastSample < 1000) {
            return;
        }
        lastSample = now;
        int fps = p.fps();
        if (fps <= 0) {
            return;
        }
        samples[next] = fps;
        next = (next + 1) % MAX;
        count = Math.min(MAX, count + 1);
        if (now - lastWrite > 60_000 && count >= 20) {
            lastWrite = now;
            write(p);
        }
    }

    void write(Platform p) {
        if (count < 20) {
            return;
        }
        float[] s = Arrays.copyOf(samples, count);
        double sum = 0;
        for (float f : s) {
            sum += f;
        }
        Arrays.sort(s);
        int avg = (int) Math.round(sum / s.length);
        int low = Math.round(s[Math.max(0, (int) Math.floor(s.length * 0.01))]);
        String json = "{\"avg_fps\": " + avg + ", \"low_fps\": " + low + ", \"minutes\": " + (count / 60)
                + ", \"mc\": \"" + p.minecraftVersion().replace("\"", "") + "\", \"at\": " + (System.currentTimeMillis() / 1000) + "}\n";
        try {
            Path dir = Paths.get(System.getProperty("user.dir", "."));
            Path tmp = dir.resolve("peregrine-stats.json.tmp");
            Files.write(tmp, json.getBytes(StandardCharsets.UTF_8));
            Files.move(tmp, dir.resolve("peregrine-stats.json"), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            // only a nice-to-have
        }
    }
}
