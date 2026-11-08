package net.peregrine.client.core.clips;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

/**
 * The replay buffer, like Medal or ShadowPlay: the game's frames go to ffmpeg, which encodes
 * them (on the graphics card where it can) into short pieces on disk. Only as many pieces as the
 * buffer length are kept, so it can run all day; a clip joins the last pieces into one MP4
 * without encoding again, so saving even an hour takes seconds.
 * <p>
 * The game never waits for any of this: frames are handed over by copying into a buffer that a
 * writer thread sends to ffmpeg at a steady frame rate (repeating the last frame when the game
 * is slower, dropping when it's faster).
 */
public final class Recorder {

    /** Seconds per piece on disk. */
    public static final int PIECE = 4;

    private final String ffmpeg;
    private final Path dir;
    private Process process;
    private Thread writer, errors;
    private volatile boolean running;
    private volatile String lastError;
    private final List<String> errorLines = Collections.synchronizedList(new ArrayList<String>());

    // the latest frame from the game
    private final Object lock = new Object();
    private byte[] latest;
    private long latestSeq;
    private int width, height, fps;
    private long startedAt;
    private long lastCapture;
    private volatile long keepSeconds = 300;
    private volatile long maxBytes = -1;

    public Recorder(String ffmpeg, Path dir) {
        this.ffmpeg = ffmpeg;
        this.dir = dir;
    }

    public boolean running() {
        Process p = process;
        return running && p != null && p.isAlive();
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public String lastError() {
        return lastError;
    }

    /** The most disk the buffer may use (bytes, -1 for no limit): the oldest pieces go first. */
    public void maxBytes(long bytes) {
        maxBytes = bytes;
    }

    /** How long to keep: seconds, or -1 for everything since the game started. */
    public void keep(long seconds) {
        keepSeconds = seconds;
    }

    /** Seconds recorded so far (what a clip can cover at most). */
    public long recordedSeconds() {
        return startedAt == 0 ? 0 : (System.currentTimeMillis() - startedAt) / 1000;
    }

    /** Starts ffmpeg for frames of this size (the pieces already on disk are kept). */
    public synchronized void start(Encoders.Choice enc, int w, int h, int frameRate, int kbps, String audioInput) throws IOException {
        start(enc, w, h, frameRate, kbps, audioInput, null);
    }

    /** Also records a microphone (mixed with the game's sound) when mic isn't null (PulseAudio names). */
    public synchronized void start(Encoders.Choice enc, int w, int h, int frameRate, int kbps, String audioInput, String mic) throws IOException {
        start(enc, w, h, frameRate, kbps, audioInput == null ? null : new String[] {"pulse", audioInput},
                mic == null ? null : new String[] {"pulse", mic});
    }

    /** Sound inputs as {ffmpeg input format, device}: "pulse" on Linux, "dshow" ("audio=Name") on Windows. */
    public synchronized void start(Encoders.Choice enc, int w, int h, int frameRate, int kbps, String[] game, String[] voice) throws IOException {
        String audioInput = game == null ? null : game[1];
        String mic = voice == null ? null : voice[1];
        stop();
        Files.createDirectories(dir);
        width = w;
        height = h;
        fps = frameRate;
        List<String> cmd = new ArrayList<String>();
        cmd.add(ffmpeg);
        cmd.add("-hide_banner");
        cmd.add("-loglevel");
        cmd.add("error");
        cmd.add("-y");
        cmd.addAll(enc.before);
        cmd.add("-f");
        cmd.add("rawvideo");
        cmd.add("-pix_fmt");
        cmd.add("bgra");
        cmd.add("-video_size");
        cmd.add(w + "x" + h);
        cmd.add("-framerate");
        cmd.add(String.valueOf(frameRate));
        cmd.add("-thread_queue_size");
        cmd.add("64");
        cmd.add("-i");
        cmd.add("pipe:0");
        if (audioInput != null) {
            addAudio(cmd, game);
        }
        int audios = (audioInput != null ? 1 : 0) + (mic != null ? 1 : 0);
        if (mic != null) {
            addAudio(cmd, voice);
        }
        if (audios == 2) {   // the game and your voice in one track
            cmd.add("-filter_complex");
            cmd.add("[1:a][2:a]amix=inputs=2:duration=longest:dropout_transition=0:normalize=0[a]");
            cmd.add("-map");
            cmd.add("0:v");
            cmd.add("-map");
            cmd.add("[a]");
        }
        cmd.addAll(enc.filter);
        cmd.add("-c:v");
        cmd.add(enc.codec);
        cmd.addAll(enc.options);
        cmd.add("-b:v");
        cmd.add(kbps + "k");
        cmd.add("-maxrate");
        cmd.add((kbps * 3 / 2) + "k");
        cmd.add("-bufsize");
        cmd.add((kbps * 2) + "k");
        cmd.add("-g");
        cmd.add(String.valueOf(frameRate * 2));
        if (audios > 0) {
            cmd.add("-c:a");
            cmd.add("aac");
            cmd.add("-b:a");
            cmd.add("128k");
        }
        cmd.add("-f");
        cmd.add("segment");
        cmd.add("-segment_time");
        cmd.add(String.valueOf(PIECE));
        cmd.add("-segment_format");
        cmd.add("mpegts");
        cmd.add("-reset_timestamps");
        cmd.add("1");
        cmd.add("-segment_start_number");
        cmd.add(String.valueOf(nextNumber()));
        cmd.add(dir.resolve("piece_%08d.ts").toString());
        cmd.removeIf(String::isEmpty);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        process = pb.start();
        running = true;
        lastError = null;
        errorLines.clear();
        if (startedAt == 0) {
            startedAt = System.currentTimeMillis();
        }
        final Process p = process;
        errors = new Thread(() -> drain(p), "Peregrine clips (ffmpeg)");
        errors.setDaemon(true);
        errors.start();
        writer = new Thread(this::writeLoop, "Peregrine clips");
        writer.setDaemon(true);
        writer.start();
    }

    private static void addAudio(List<String> cmd, String[] in) {
        cmd.add("-f");
        cmd.add(in[0]);
        cmd.add("-thread_queue_size");
        cmd.add("2048");
        if (in[0].equals("dshow")) {
            cmd.add("-audio_buffer_size");
            cmd.add("50");
        }
        cmd.add("-i");
        cmd.add(in[1]);
    }

    /** Stops recording (the pieces stay, so you can still save a clip). */
    public synchronized void stop() {
        running = false;
        Process p = process;
        process = null;
        if (writer != null) {
            writer.interrupt();
            try {
                writer.join(1500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            writer = null;
        }
        if (p != null) {
            try {
                p.getOutputStream().close();   // ffmpeg finishes the last piece
            } catch (IOException ignored) {
                // gone already
            }
            try {
                if (!p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                }
            } catch (InterruptedException e) {
                p.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Should the game send a frame now (at most the recording frame rate)? */
    public boolean wantFrame() {
        if (!running) {
            return false;
        }
        long now = System.nanoTime();
        if (now - lastCapture < 1_000_000_000L / Math.max(1, fps) - 2_000_000L) {
            return false;
        }
        lastCapture = now;
        return true;
    }

    /** A frame from the game: BGRA, top row first. Copied right away; the buffer can be reused. */
    public void offer(ByteBuffer pixels, int w, int h) {
        if (!running || w != width || h != height) {
            return;
        }
        int n = w * h * 4;
        synchronized (lock) {
            if (latest == null || latest.length != n) {
                latest = new byte[n];
            }
            int pos = pixels.position();
            pixels.get(latest, 0, Math.min(n, pixels.remaining()));
            pixels.position(pos);
            latestSeq++;
        }
    }

    private void writeLoop() {
        Process p = process;
        if (p == null) {
            return;
        }
        byte[] frame = null;
        long seq = -1;
        long period = 1_000_000_000L / Math.max(1, fps);
        long next = System.nanoTime();
        long lastTidy = 0;
        try (OutputStream out = new BufferedOutputStream(p.getOutputStream(), 1 << 20)) {
            while (running && !Thread.currentThread().isInterrupted()) {
                long now = System.nanoTime();
                if (now < next) {
                    long wait = next - now;
                    Thread.sleep(wait / 1_000_000L, (int) (wait % 1_000_000L));
                    continue;
                }
                synchronized (lock) {
                    if (latest != null && latestSeq != seq) {
                        if (frame == null || frame.length != latest.length) {
                            frame = new byte[latest.length];
                        }
                        System.arraycopy(latest, 0, frame, 0, latest.length);
                        seq = latestSeq;
                    }
                }
                if (frame != null) {
                    out.write(frame);
                    out.flush();
                }
                next += period;
                if (System.nanoTime() - next > 1_000_000_000L) {
                    next = System.nanoTime();   // fell far behind (ffmpeg was busy): carry on from now
                }
                if (now - lastTidy > 5_000_000_000L) {
                    lastTidy = now;
                    tidy();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            lastError = errorText("ffmpeg stopped");
            running = false;
        }
    }

    private void drain(Process p) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            int logged = 0;
            while ((line = r.readLine()) != null) {
                if (logged++ < 30) {   // into the game log, so problems can be found
                    System.out.println("[Peregrine] clips (ffmpeg): " + line);
                }
                errorLines.add(line);
                if (errorLines.size() > 20) {
                    errorLines.remove(0);
                }
            }
        } catch (IOException ignored) {
            // ffmpeg ended
        }
        if (running) {
            lastError = errorText("ffmpeg stopped");
            running = false;
        }
    }

    /** What went wrong: the most telling line ffmpeg printed (an error, not its final summary). */
    private String errorText(String what) {
        synchronized (errorLines) {
            if (errorLines.isEmpty()) {
                return what;
            }
            String best = errorLines.get(errorLines.size() - 1);
            for (String line : errorLines) {
                String l = line.toLowerCase();
                if (l.contains("error") || l.contains("failed") || l.contains("invalid") || l.contains("not found")
                        || l.contains("no such") || l.contains("unknown") || l.contains("cannot") || l.contains("unable")
                        || l.contains("refused") || l.contains("denied")) {
                    if (!l.contains("conversion failed") && !l.contains("error while filtering")) {
                        best = line;
                        break;
                    }
                }
            }
            best = best.replaceAll("^\\[[^\\]]*\\]\\s*", "");   // drop "[h264_vaapi @ 0x...] "
            return what + ": " + best;
        }
    }

    /** The pieces on disk, oldest first. */
    public List<Path> pieces() {
        List<Path> out = new ArrayList<Path>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "piece_*.ts")) {
            for (Path p : ds) {
                out.add(p);
            }
        } catch (IOException ignored) {
            // nothing to list
        }
        Collections.sort(out);
        return out;
    }

    private int nextNumber() {
        List<Path> ps = pieces();
        if (ps.isEmpty()) {
            return 0;
        }
        String n = ps.get(ps.size() - 1).getFileName().toString().replaceAll("\\D", "");
        try {
            return Integer.parseInt(n) + 1;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Deletes pieces older than the buffer length. */
    void tidy() {
        long keep = keepSeconds;
        List<Path> ps = pieces();
        int first = 0;
        if (keep >= 0) {
            int allowed = (int) (keep / PIECE) + 2;
            first = Math.max(0, ps.size() - allowed);
        }
        // and within the disk limit, newest first (the piece being written is always kept)
        long cap = maxBytes;
        if (cap > 0) {
            long total = 0;
            for (int i = ps.size() - 1; i >= first; i--) {
                try {
                    total += Files.size(ps.get(i));
                } catch (IOException e) {
                    continue;
                }
                if (total > cap && i < ps.size() - 1) {
                    first = i + 1;
                    break;
                }
            }
        }
        for (int i = 0; i < first; i++) {
            try {
                Files.deleteIfExists(ps.get(i));
            } catch (IOException ignored) {
                // try again next time
            }
        }
    }

    /** Deletes every piece (a new session). */
    public void clearPieces() {
        for (Path p : pieces()) {
            try {
                Files.deleteIfExists(p);
            } catch (IOException ignored) {
                // leave it
            }
        }
    }

    /**
     * Saves the last seconds (or everything kept, with -1) as an MP4 in the clips folder; runs in
     * the background and reports the file (or null and why) when done.
     */
    public void clip(final long seconds, final Path clipsDir, final Consumer<String[]> done) {
        Thread t = new Thread(() -> {
            try {
                done.accept(new String[] {saveClip(seconds, clipsDir).toString(), null});
            } catch (Exception e) {
                done.accept(new String[] {null, e.getMessage()});
            }
        }, "Peregrine clip");
        t.setDaemon(true);
        t.start();
    }

    Path saveClip(long seconds, Path clipsDir) throws IOException, InterruptedException {
        List<Path> ps = pieces();
        if (ps.isEmpty()) {
            throw new IOException("nothing recorded yet");
        }
        // the newest piece is still being written: it's included up to where it is now
        int need = seconds < 0 ? ps.size() : (int) Math.min(ps.size(), seconds / PIECE + 2);
        List<Path> use = ps.subList(ps.size() - need, ps.size());
        Files.createDirectories(clipsDir);
        Path work = Files.createTempDirectory(dir, "clip");
        try {
            Path list = work.resolve("list.txt");
            StringBuilder sb = new StringBuilder();
            for (Path p : use) {
                sb.append("file '").append(p.toAbsolutePath().toString().replace("'", "'\\''")).append("'\n");
            }
            Files.write(list, sb.toString().getBytes(StandardCharsets.UTF_8));
            Path joined = work.resolve("joined.ts");
            run(ffmpeg, "-hide_banner", "-loglevel", "error", "-y", "-f", "concat", "-safe", "0", "-i", list.toString(),
                    "-c", "copy", joined.toString());
            String stamp = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(new Date());
            String label = seconds < 0 ? "session" : seconds >= 3600 ? (seconds / 3600) + "h" : seconds >= 60 ? (seconds / 60) + "m" : seconds + "s";
            Path out = clipsDir.resolve("Minecraft " + stamp + " (" + label + ").mp4");
            List<String> cmd = new ArrayList<String>();
            Collections.addAll(cmd, ffmpeg, "-hide_banner", "-loglevel", "error", "-y");
            if (seconds > 0) {   // without re-encoding a clip starts on a keyframe (every 2 s): start a little early
                Collections.addAll(cmd, "-sseof", "-" + (seconds + 2));
            }
            Collections.addAll(cmd, "-i", joined.toString(), "-c", "copy", "-bsf:a", "aac_adtstoasc", "-movflags", "+faststart",
                    out.toString());
            run(cmd.toArray(new String[0]));
            return out;
        } finally {
            deleteTree(work.toFile());
        }
    }

    private static void run(String... cmd) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        StringBuilder log = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (log.length() < 4000) {
                    log.append(line).append('\n');
                }
            }
        }
        if (p.waitFor() != 0) {
            String[] lines = log.toString().trim().split("\n");
            throw new IOException("ffmpeg: " + lines[lines.length - 1]);
        }
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) {
                deleteTree(k);
            }
        }
        f.delete();
    }
}
