package net.peregrine.client.core;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

/**
 * What's playing on the computer right now (Spotify, YouTube in a browser, any
 * music app), read from the system's media controls: MPRIS on Linux, the media
 * session on Windows, Spotify or Music on macOS. Polled in the background while
 * the Music activity card is on; the position moves smoothly between polls.
 */
public final class NowPlaying {

    /** One snapshot. Times in milliseconds; length 0 means unknown (a live stream). */
    public static final class Track {
        public final String title, artist, app, art;
        public final long length, position;
        public final boolean playing;
        final long at = System.currentTimeMillis();

        public Track(String title, String artist, String app, String art, long length, long position, boolean playing) {
            this.title = title == null ? "" : title;
            this.artist = artist == null ? "" : artist;
            this.app = app == null ? "" : app;
            this.art = art == null ? "" : art;
            this.length = Math.max(0, length);
            this.position = Math.max(0, position);
            this.playing = playing;
        }

        /** Where the song is now, moving on from the last poll while it plays. */
        public long positionNow() {
            long p = position + (playing ? System.currentTimeMillis() - at : 0);
            return length > 0 ? Math.min(p, length) : p;
        }

        String key() {
            return title + "\u0000" + artist + "\u0000" + art;
        }
    }

    public static final int ART = 24;  // cover art is kept as ART x ART colors

    private static volatile Track current;
    private static volatile int[] cover;
    private static volatile String coverKey = "";
    private static volatile long wantedUntil;
    private static volatile boolean fixed;  // a track shown by the self-test stays put
    private static Thread worker;
    private static Process windows;

    private NowPlaying() {
    }

    /** The track playing (or paused) now, or null. Starts watching if it wasn't. */
    public static Track get() {
        wantedUntil = System.currentTimeMillis() + 5000;
        start();
        return current;
    }

    /** Cover art as ART*ART ARGB colors, or null. */
    public static int[] cover() {
        return cover;
    }

    /** For tests and the editor: show this track. */
    public static void show(Track t, int[] art) {
        fixed = t != null;
        current = t;
        cover = art;
    }

    private static synchronized void start() {
        if (worker != null && worker.isAlive()) {
            return;
        }
        worker = new Thread(NowPlaying::loop, "Peregrine now playing");
        worker.setDaemon(true);
        worker.start();
    }

    private static void loop() {
        String os = System.getProperty("os.name", "").toLowerCase();
        while (System.currentTimeMillis() < wantedUntil) {
            try {
                if (fixed) {
                    Thread.sleep(500);
                    continue;
                }
                Track t = os.contains("win") ? windows() : os.contains("mac") ? mac() : linux();
                current = t;
                if (t != null && !t.art.isEmpty() && !t.key().equals(coverKey)) {
                    coverKey = t.key();
                    cover = loadArt(t.art);
                } else if (t == null || t.art.isEmpty()) {
                    coverKey = "";
                    cover = null;
                }
            } catch (Exception e) {
                current = null;
            }
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                return;
            }
        }
        stopWindows();
    }

    // ------------------------------------------------------------ helpers

    static String run(long timeoutMs, String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            InputStream in = p.getInputStream();
            byte[] buf = new byte[8192];
            long end = System.currentTimeMillis() + timeoutMs;
            while (true) {
                int n = in.available() > 0 ? in.read(buf) : 0;
                if (n > 0) {
                    out.write(buf, 0, n);
                } else if (!p.isAlive()) {
                    int r;
                    while ((r = in.read(buf)) > 0) {
                        out.write(buf, 0, r);
                    }
                    break;
                } else if (System.currentTimeMillis() > end) {
                    p.destroyForcibly();
                    return null;
                } else {
                    Thread.sleep(15);
                }
            }
            return p.exitValue() == 0 ? new String(out.toByteArray(), StandardCharsets.UTF_8) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** "spotify" -> "Spotify", "chromium.instance1234" -> "Chromium", a YouTube page -> "YouTube". */
    static String appName(String bus, String url) {
        if (url != null && (url.contains("youtube.com") || url.contains("youtu.be"))) {
            return "YouTube";
        }
        if (url != null && url.contains("soundcloud.com")) {
            return "SoundCloud";
        }
        String n = bus.replace("org.mpris.MediaPlayer2.", "");
        int dot = n.indexOf('.');
        if (dot > 0) {
            n = n.substring(0, dot);
        }
        if (n.equalsIgnoreCase("firefox")) {
            return "Firefox";
        }
        if (n.equalsIgnoreCase("chromium") || n.equalsIgnoreCase("chrome")) {
            return "Chrome";
        }
        return n.isEmpty() ? "" : Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    // ------------------------------------------------------------ Linux (MPRIS over D-Bus)

    private static Track linux() {
        String names = run(2000, "dbus-send", "--session", "--dest=org.freedesktop.DBus", "--type=method_call",
                "--print-reply", "/org/freedesktop/DBus", "org.freedesktop.DBus.ListNames");
        if (names == null) {
            return null;
        }
        Track best = null;
        int bestScore = -1;
        for (String bus : mprisNames(names)) {
            String reply = run(2000, "dbus-send", "--session", "--print-reply", "--dest=" + bus, "/org/mpris/MediaPlayer2",
                    "org.freedesktop.DBus.Properties.GetAll", "string:org.mpris.MediaPlayer2.Player");
            Track t = reply == null ? null : parseMpris(bus, reply);
            if (t == null || t.title.isEmpty()) {
                continue;
            }
            int score = (t.playing ? 2 : 0) + (t.app.equals("Spotify") ? 1 : 0);
            if (score > bestScore) {
                best = t;
                bestScore = score;
            }
        }
        return best;
    }

    static List<String> mprisNames(String listNamesReply) {
        List<String> out = new ArrayList<String>();
        Matcher m = Pattern.compile("string \"(org\\.mpris\\.MediaPlayer2\\.[^\"]+)\"").matcher(listNamesReply);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    private static final Pattern STR = Pattern.compile("string \"(.*)\"\\s*$");
    private static final Pattern NUM = Pattern.compile("u?int(?:64|32) (-?\\d+)");
    private static final Pattern DBL = Pattern.compile("double (-?[\\d.]+)");

    /** Reads `dbus-send --print-reply ... GetAll org.mpris.MediaPlayer2.Player`. */
    static Track parseMpris(String bus, String reply) {
        String[] lines = reply.split("\n");
        String title = null, artist = null, art = null, url = null, status = null;
        long length = 0, position = 0;
        for (int i = 0; i < lines.length - 1; i++) {
            Matcher k = STR.matcher(lines[i].trim());
            if (!k.find() || lines[i].contains("variant")) {
                continue;
            }
            String key = k.group(1);
            String next = lines[i + 1];
            if (!next.contains("variant")) {
                continue;
            }
            if (next.contains("array [")) {  // a list of strings (artists)
                StringBuilder sb = new StringBuilder();
                for (int j = i + 2; j < lines.length && !lines[j].trim().equals("]"); j++) {
                    Matcher s = STR.matcher(lines[j].trim());
                    if (s.find()) {
                        sb.append(sb.length() > 0 ? ", " : "").append(s.group(1));
                    }
                }
                if (key.equals("xesam:artist")) {
                    artist = sb.toString();
                }
                continue;
            }
            Matcher s = STR.matcher(next.trim());
            Matcher n = NUM.matcher(next);
            Matcher d = DBL.matcher(next);
            if (key.equals("xesam:title") && s.find()) {
                title = s.group(1);
            } else if (key.equals("xesam:artist") && s.find()) {
                artist = s.group(1);
            } else if (key.equals("mpris:artUrl") && s.find()) {
                art = s.group(1);
            } else if (key.equals("xesam:url") && s.find()) {
                url = s.group(1);
            } else if (key.equals("PlaybackStatus") && s.find()) {
                status = s.group(1);
            } else if (key.equals("mpris:length")) {
                if (n.find()) {
                    length = Long.parseLong(n.group(1)) / 1000;
                } else if (d.find()) {
                    length = (long) (Double.parseDouble(d.group(1)) / 1000);
                }
            } else if (key.equals("Position") && n.find()) {
                position = Long.parseLong(n.group(1)) / 1000;
            }
        }
        if (title == null || "Stopped".equals(status)) {
            return null;
        }
        return new Track(title, artist, appName(bus, url), art, length, position, "Playing".equals(status));
    }

    // ------------------------------------------------------------ Windows (media session, via PowerShell)

    private static final String PS =
            "$ErrorActionPreference='SilentlyContinue';"
            + "Add-Type -AssemblyName System.Runtime.WindowsRuntime;"
            + "$at=([System.WindowsRuntimeSystemExtensions].GetMethods()|?{$_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'})[0];"
            + "function A($o,$t){$k=$at.MakeGenericMethod($t).Invoke($null,@($o));$k.Wait(-1)|Out-Null;$k.Result};"
            + "$M=[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime];"
            + "$P=[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties,Windows.Media.Control,ContentType=WindowsRuntime];"
            + "$S=[Windows.Storage.Streams.IRandomAccessStreamWithContentType,Windows.Storage.Streams,ContentType=WindowsRuntime];"
            + "$m=A ($M::RequestAsync()) $M;$last='';$art=[IO.Path]::Combine($env:TEMP,'peregrine-cover.img');"
            + "while($true){$s=$m.GetCurrentSession();"
            + "if($s){$p=A ($s.TryGetMediaPropertiesAsync()) $P;$tl=$s.GetTimelineProperties();$pb=$s.GetPlaybackInfo();"
            + "$key=$p.Title+'|'+$p.Artist;"
            + "if($key -ne $last -and $p.Thumbnail){$last=$key;try{$st=A ($p.Thumbnail.OpenReadAsync()) $S;"
            + "$r=[IO.WindowsRuntimeStreamExtensions]::AsStreamForRead($st);$f=[IO.File]::Create($art);$r.CopyTo($f);$f.Close();$r.Close()}catch{}};"
            + "$o=@{t=$p.Title;a=$p.Artist;app=$s.SourceAppUserModelId;len=[long]$tl.EndTime.TotalMilliseconds;"
            + "pos=[long]$tl.Position.TotalMilliseconds;at=[long]([DateTimeOffset]::Now-$tl.LastUpdatedTime).TotalMilliseconds;"
            + "playing=($pb.PlaybackStatus -eq 4);art=$art;key=$last};"
            + "[Console]::Out.WriteLine(($o|ConvertTo-Json -Compress))}else{[Console]::Out.WriteLine('{}')};"
            + "[Console]::Out.Flush();Start-Sleep -Milliseconds 1200}";
    private static BufferedReader windowsOut;

    private static Track windows() throws Exception {
        if (windows == null || !windows.isAlive()) {
            windows = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                    "-Command", PS).redirectErrorStream(true).start();
            windowsOut = new BufferedReader(new InputStreamReader(windows.getInputStream(), StandardCharsets.UTF_8));
        }
        String line = null;
        long end = System.currentTimeMillis() + 4000;
        while (System.currentTimeMillis() < end) {  // the newest line
            if (windowsOut.ready()) {
                String l = windowsOut.readLine();
                if (l != null && l.startsWith("{")) {
                    line = l;
                }
                if (!windowsOut.ready() && line != null) {
                    break;
                }
            } else if (line != null) {
                break;
            } else {
                Thread.sleep(50);
            }
        }
        return line == null ? current : parseWindows(line);
    }

    static Track parseWindows(String json) {
        com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        if (!o.has("t") || o.get("t").isJsonNull() || o.get("t").getAsString().isEmpty()) {
            return null;
        }
        String app = o.has("app") && !o.get("app").isJsonNull() ? o.get("app").getAsString().toLowerCase() : "";
        String name = app.contains("spotify") ? "Spotify" : app.contains("chrome") ? "Chrome" : app.contains("firefox")
                ? "Firefox" : app.contains("msedge") ? "Edge" : app.contains("zune") || app.contains("music") ? "Music" : "";
        boolean playing = o.has("playing") && o.get("playing").getAsBoolean();
        long pos = o.has("pos") ? o.get("pos").getAsLong() : 0;
        if (playing && o.has("at")) {  // Windows reports where it was when it last updated
            pos += Math.max(0, Math.min(o.get("at").getAsLong(), 10 * 60_000L));
        }
        String art = o.has("art") && o.has("key") && !o.get("key").getAsString().isEmpty()
                ? new File(o.get("art").getAsString()).toURI().toString() + "#" + o.get("key").getAsString().hashCode() : "";
        return new Track(o.get("t").getAsString(), o.has("a") ? o.get("a").getAsString() : "", name, art,
                o.has("len") ? o.get("len").getAsLong() : 0, pos, playing);
    }

    private static void stopWindows() {
        if (windows != null) {
            windows.destroy();
            windows = null;
        }
    }

    // ------------------------------------------------------------ macOS (Spotify or Music)

    private static Track mac() {
        for (String app : new String[] {"Spotify", "Music"}) {
            String script = "if application \"" + app + "\" is running then tell application \"" + app + "\" to "
                    + "return (name of current track) & \"\\n\" & (artist of current track) & \"\\n\" & "
                    + "(duration of current track) & \"\\n\" & (player position) & \"\\n\" & (player state as string)"
                    + (app.equals("Spotify") ? " & \"\\n\" & (artwork url of current track)" : "");
            String out = run(2500, "osascript", "-e", script);
            if (out == null || out.trim().isEmpty()) {
                continue;
            }
            String[] f = out.trim().split("\n");
            if (f.length < 5) {
                continue;
            }
            double len = parse(f[2]);
            long length = (long) (app.equals("Spotify") ? len : len * 1000);
            return new Track(f[0], f[1], app, f.length > 5 ? f[5] : "", length, (long) (parse(f[3]) * 1000),
                    f[4].trim().equals("playing"));
        }
        return null;
    }

    private static double parse(String s) {
        try {
            return Double.parseDouble(s.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ------------------------------------------------------------ cover art

    static int[] loadArt(String where) {
        try {
            BufferedImage img;
            String u = where.contains("#") ? where.substring(0, where.indexOf('#')) : where;
            if (u.startsWith("file:")) {
                img = ImageIO.read(new File(URLDecoder.decode(u.substring(u.startsWith("file://") ? 7 : 5), "UTF-8")));
            } else if (u.startsWith("http")) {
                HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
                c.setConnectTimeout(4000);
                c.setReadTimeout(6000);
                c.setRequestProperty("User-Agent", "PeregrineClient");
                try (InputStream in = c.getInputStream()) {
                    img = ImageIO.read(in);
                }
            } else {
                return null;
            }
            return img == null ? null : downscale(img, ART);
        } catch (Exception e) {
            return null;
        }
    }

    /** The middle square of an image, averaged down to n x n colors. */
    static int[] downscale(BufferedImage img, int n) {
        int w = img.getWidth(), h = img.getHeight(), side = Math.min(w, h);
        int ox = (w - side) / 2, oy = (h - side) / 2;
        int[] out = new int[n * n];
        for (int cy = 0; cy < n; cy++) {
            for (int cx = 0; cx < n; cx++) {
                int x0 = ox + cx * side / n, x1 = Math.max(x0 + 1, ox + (cx + 1) * side / n);
                int y0 = oy + cy * side / n, y1 = Math.max(y0 + 1, oy + (cy + 1) * side / n);
                long r = 0, g = 0, b = 0, cnt = 0;
                int step = Math.max(1, (x1 - x0) / 4);
                for (int y = y0; y < y1; y += step) {
                    for (int x = x0; x < x1; x += step) {
                        int c = img.getRGB(x, y);
                        r += (c >> 16) & 255;
                        g += (c >> 8) & 255;
                        b += c & 255;
                        cnt++;
                    }
                }
                out[cy * n + cx] = 0xFF000000 | (int) (r / cnt) << 16 | (int) (g / cnt) << 8 | (int) (b / cnt);
            }
        }
        return out;
    }

    /** "3:07" or "1:02:45". */
    public static String time(long ms) {
        long s = Math.max(0, ms / 1000);
        return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                : String.format("%d:%02d", s / 60, s % 60);
    }
}
