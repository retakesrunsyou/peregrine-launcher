package net.peregrine.client.core.modules;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.clips.Encoders;
import net.peregrine.client.core.clips.Recorder;
import net.peregrine.client.core.settings.ActionSetting;
import net.peregrine.client.core.settings.BoolSetting;
import net.peregrine.client.core.settings.ChoiceSetting;
import net.peregrine.client.core.settings.KeySetting;

/**
 * Clips, like Medal: the game keeps recording into a replay buffer, and the clip key saves the
 * last moments as a video (the HUD and chat included, the game's sound too on Linux). Save the
 * last 30 seconds up to an hour, the whole day, or everything since the game started. Clips can
 * also save themselves when you die or get a kill. Find, watch, trim and rename them in the
 * launcher's Clips page.
 */
public final class Clips extends Module {

    public static final String[] KEEP_NAMES = {"30 seconds", "5 minutes", "10 minutes", "30 minutes", "1 hour", "24 hours",
        "Everything since the game started"};
    public static final long[] KEEP_SECONDS = {30, 300, 600, 1800, 3600, 86400, -1};
    public static final String[] CLIP_NAMES = {"30 seconds", "1 minute", "2 minutes", "5 minutes", "10 minutes", "30 minutes",
        "1 hour", "Everything kept"};
    public static final long[] CLIP_SECONDS = {30, 60, 120, 300, 600, 1800, 3600, -1};

    public final ChoiceSetting keep = add(new ChoiceSetting("keep", "Keep recording the last", 1, KEEP_NAMES));
    public final KeySetting clipKey = add(new KeySetting("clip_key", "Clip key"));
    public final ChoiceSetting clipLength = add(new ChoiceSetting("clip_length", "Clip key saves", 0, CLIP_NAMES));
    public final KeySetting longKey = add(new KeySetting("long_key", "Second clip key"));
    public final ChoiceSetting longLength = add(new ChoiceSetting("long_length", "Second key saves", 3, CLIP_NAMES));
    public final ActionSetting save30 = add(new ActionSetting("save_30s", "Save the last 30 seconds", () -> "Save", () -> saveClip(30)));
    public final ActionSetting save5m = add(new ActionSetting("save_5m", "Save the last 5 minutes", () -> "Save", () -> saveClip(300)));
    public final ActionSetting save10m = add(new ActionSetting("save_10m", "Save the last 10 minutes", () -> "Save", () -> saveClip(600)));
    public final ActionSetting save30m = add(new ActionSetting("save_30m", "Save the last 30 minutes", () -> "Save", () -> saveClip(1800)));
    public final ActionSetting save1h = add(new ActionSetting("save_1h", "Save the last hour", () -> "Save", () -> saveClip(3600)));
    public final ActionSetting saveDay = add(new ActionSetting("save_day", "Save the whole day (24 hours)", () -> "Save", () -> saveClip(86400)));
    public final ActionSetting saveAll = add(new ActionSetting("save_all", "Save everything since the game started", () -> "Save", () -> saveClip(-1)));
    public final ChoiceSetting resolution = add(new ChoiceSetting("resolution", "Resolution", 0, "720p", "1080p", "Your window size"));
    public final ChoiceSetting frameRate = add(new ChoiceSetting("fps", "Frame rate", 0, "30 FPS", "60 FPS"));
    public final ChoiceSetting quality = add(new ChoiceSetting("quality", "Quality", 1, "Smaller files", "Balanced", "Best"));
    public final BoolSetting sound = add(new BoolSetting("sound", "Record the game's sound (Linux)", true));
    public final BoolSetting onDeath = add(new BoolSetting("on_death", "Clip by itself when you die", true));
    public final BoolSetting onKill = add(new BoolSetting("on_kill", "Clip by itself when you get a kill", false));
    public final ChoiceSetting autoLength = add(new ChoiceSetting("auto_length", "Clips made by themselves are", 0,
            "30 seconds", "1 minute", "2 minutes"));
    public final BoolSetting notify = add(new BoolSetting("notify", "Show when a clip is saved", true));
    public final BoolSetting dot = add(new BoolSetting("dot", "Show a recording dot", false));

    private static volatile Clips instance;
    private Recorder recorder;
    private Encoders.Choice encoder;
    private volatile boolean probing;
    private boolean noSound;
    private long startTries, lastStart;
    private volatile String note;
    private volatile long noteAt;
    private float lastHealth = -1;
    private long pendingAt;
    private long pendingSeconds;
    private boolean sessionCleared;

    public Clips() {
        super("clips", "Clips", "Always-on replay buffer like Medal: the clip key saves the last 30 seconds up to an hour, the "
                + "whole day or the whole session, with your HUD and chat. Auto clips on deaths and kills", Category.UTILITY, false);
        clipKey.key = 297;   // F8
        instance = this;
    }

    // ------------------------------------------------------------------ for the game adapter

    /** The size frames should be (0,0 when not recording): the adapter scales and sends them. */
    public static int[] wantedSize(int windowW, int windowH) {
        Clips m = instance;
        if (m == null || !m.enabled() || m.recorder == null || !m.recorder.running()) {
            return null;
        }
        return new int[] {m.recorder.width(), m.recorder.height()};
    }

    /** Whether to capture this frame. */
    public static boolean wantFrame() {
        Clips m = instance;
        return m != null && m.enabled() && m.recorder != null && m.recorder.wantFrame();
    }

    /** A captured frame: BGRA, top row first. */
    public static void frame(ByteBuffer pixels, int w, int h) {
        Clips m = instance;
        if (m != null && m.recorder != null) {
            m.recorder.offer(pixels, w, h);
        }
    }

    // ------------------------------------------------------------------ running

    static Path dataDir() {
        String data = System.getProperty("peregrine.data");
        return data == null ? Paths.get(System.getProperty("user.home"), ".peregrine") : Paths.get(data);
    }

    public static Path clipsDir() {
        return dataDir().resolve("clips");
    }

    @Override
    public void tick(Platform p) {
        if (!enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        ensureRecording(p, now);
        if (recorder != null) {
            recorder.keep(KEEP_SECONDS[keep.index]);
        }
        // auto clips: a moment after it happens, so the clip shows it
        float h = p.health();
        if (onDeath.value && lastHealth > 0 && h <= 0 && h > -1) {
            schedule(now);
        }
        lastHealth = h;
        if (onKill.value && p.killedSomething()) {
            schedule(now);
        }
        if (pendingAt != 0 && now >= pendingAt) {
            pendingAt = 0;
            saveClip(pendingSeconds);
        }
    }

    private void schedule(long now) {
        if (pendingAt == 0) {
            pendingAt = now + 2500;
            pendingSeconds = new long[] {30, 60, 120}[autoLength.index];
        }
    }

    private void ensureRecording(Platform p, long now) {
        if (recorder != null && recorder.running()) {
            int[] want = size(p);
            if (want[0] != recorder.width() || want[1] != recorder.height()) {
                start(p);   // the window changed size
            }
            return;
        }
        if (probing || now - lastStart < Math.min(60_000, 3000L << Math.min(5, startTries))) {
            return;
        }
        lastStart = now;
        String ff = Encoders.findFfmpeg();
        if (ff == null) {
            setNote("Clips need ffmpeg: set it up in the launcher's Clips page");
            return;
        }
        if (recorder == null) {
            recorder = new Recorder(ff, dataDir().resolve("clips").resolve(".buffer"));
            if (!sessionCleared) {
                recorder.clearPieces();   // a new game session
                sessionCleared = true;
            }
        } else if (recorder.lastError() != null) {
            if (!noSound && sound.value) {
                noSound = true;   // most likely the sound input: try without it
            }
            setNote("Clips: " + recorder.lastError());
        }
        if (encoder == null) {
            probing = true;
            Thread t = new Thread(() -> {
                encoder = Encoders.pick(ff);
                probing = false;
                if (encoder == null) {
                    setNote("Clips: ffmpeg can't encode video here");
                }
            }, "Peregrine clips (testing encoders)");
            t.setDaemon(true);
            t.start();
            return;
        }
        startTries++;
        start(p);
    }

    private int[] size(Platform p) {
        int ww = Math.max(16, p.windowWidth()), wh = Math.max(16, p.windowHeight());
        int target = resolution.index == 0 ? 720 : resolution.index == 1 ? 1080 : wh;
        int h = Math.min(wh, target);
        int w = Math.round(h * (float) ww / wh);
        return new int[] {w & ~1, h & ~1};
    }

    private void start(Platform p) {
        if (encoder == null) {
            return;
        }
        int[] s = size(p);
        int fps = frameRate.index == 0 ? 30 : 60;
        // bitrate: about 5 Mbit/s for 720p30 on Balanced, scaled by pixels and frame rate
        double px = s[0] * (double) s[1] / (1280.0 * 720.0);
        int kbps = (int) Math.round(new int[] {3000, 5000, 9000}[quality.index] * px * (fps == 60 ? 1.5 : 1.0));
        boolean linux = System.getProperty("os.name", "").toLowerCase().contains("linux");
        String audio = sound.value && linux && !noSound ? "@DEFAULT_MONITOR@" : null;
        try {
            recorder.keep(KEEP_SECONDS[keep.index]);
            recorder.start(encoder, s[0], s[1], fps, Math.max(800, kbps), audio);
        } catch (Exception e) {
            setNote("Clips couldn't start ffmpeg: " + e.getMessage());
        }
    }

    @Override
    protected void onDisable() {
        if (recorder != null) {
            recorder.stop();
        }
    }

    @Override
    public void shutdown(Platform p) {
        if (recorder != null) {
            recorder.stop();
        }
    }

    @Override
    public void keyPressed(int key, Platform p) {
        if (key < 0) {
            return;
        }
        if (key == clipKey.key) {
            saveClip(CLIP_SECONDS[clipLength.index]);
        } else if (key == longKey.key) {
            saveClip(CLIP_SECONDS[longLength.index]);
        }
    }

    /** Saves the last seconds (-1: everything kept). */
    public void saveClip(long seconds) {
        if (recorder == null || recorder.pieces().isEmpty()) {
            setNote(enabled() ? "Nothing recorded yet" : "Turn Clips on first");
            return;
        }
        long have = recorder.recordedSeconds();
        final long secs = seconds > 0 && have > 0 && seconds > have ? -1 : seconds;
        setNote("Saving clip…");
        recorder.clip(secs, clipsDir(), r -> {
            if (r[0] != null) {
                setNote("Clip saved · " + (secs < 0 ? "everything recorded" : label(secs)));
            } else {
                setNote("Couldn't save the clip: " + r[1]);
            }
        });
    }

    static String label(long s) {
        return s >= 3600 ? (s / 3600) + (s >= 7200 ? " hours" : " hour") : s >= 60 ? (s / 60) + (s >= 120 ? " minutes" : " minute") : s + " seconds";
    }

    private void setNote(String text) {
        note = text;
        noteAt = System.currentTimeMillis();
    }

    @Override
    public void renderWorld(Draw d, Platform p) {
        long age = System.currentTimeMillis() - noteAt;
        String n = note;
        if (n != null && age < 3500 && (notify.value || n.startsWith("Clips") || n.startsWith("Couldn't") || n.startsWith("Nothing"))) {
            int w = d.width(n) + 16, x = p.screenWidth() - w - 8, y = p.screenHeight() - 38;
            int a = age > 3000 ? (int) (255 * (3500 - age) / 500) : 255;
            d.roundRect(x, y, w, 18, 5, (Math.min(200, a * 200 / 255) << 24) | 0x15171C);
            d.roundRect(x, y, 3, 18, 1, (a << 24) | 0xE5484D);
            d.text(n, x + 9, y + 5, (a << 24) | 0xFFFFFF, false);
        }
        if (dot.value && recorder != null && recorder.running()) {
            boolean on = (System.currentTimeMillis() / 600) % 2 == 0;
            d.roundRect(p.screenWidth() - 12, 6, 6, 6, 3, on ? 0xFFE5484D : 0x90E5484D);
        }
    }
}
