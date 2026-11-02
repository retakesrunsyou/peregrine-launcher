package net.peregrine.client.core.clips;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Picks how to encode clips: the graphics card's own video encoder when ffmpeg can use it
 * (VAAPI on Linux, Quick Sync on Intel, NVENC on NVIDIA, AMF on AMD, Media Foundation on
 * Windows), which costs the game almost nothing; otherwise the x264 software encoder on its
 * fastest setting. Each one is tried on a tiny test video first.
 */
public final class Encoders {

    /** An encoder and the ffmpeg arguments it needs. */
    public static final class Choice {
        public final String name, codec;
        final List<String> before, filter, options;

        Choice(String name, String codec, List<String> before, List<String> filter, List<String> options) {
            this.name = name;
            this.codec = codec;
            this.before = before;
            this.filter = filter;
            this.options = options;
        }
    }

    private Encoders() {
    }

    static List<String> l(String... s) {
        return new ArrayList<String>(Arrays.asList(s));
    }

    /** Candidates for this system, best first. */
    public static List<Choice> candidates() {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        List<Choice> out = new ArrayList<Choice>();
        Choice x264 = new Choice("x264 (processor)", "libx264", l(), l("-pix_fmt", "yuv420p"),
                l("-preset", "veryfast", "-tune", "film"));
        Choice nvenc = new Choice("NVENC (NVIDIA)", "h264_nvenc", l(), l("-pix_fmt", "yuv420p"), l("-preset", "p3"));
        Choice qsv = new Choice("Quick Sync (Intel)", "h264_qsv", l(), l("-pix_fmt", "nv12"), l("-preset", "veryfast"));
        if (windows) {
            out.add(nvenc);
            out.add(qsv);
            out.add(new Choice("AMF (AMD)", "h264_amf", l(), l("-pix_fmt", "yuv420p"), l()));
            out.add(new Choice("Media Foundation", "h264_mf", l(), l("-pix_fmt", "nv12"), l()));
        } else {
            String dev = "/dev/dri/renderD128";
            if (new File(dev).exists()) {
                out.add(new Choice("VAAPI (graphics card)", "h264_vaapi", l("-vaapi_device", dev),
                        l("-vf", "format=nv12,hwupload"), l()));
            }
            out.add(nvenc);
            out.add(qsv);
        }
        out.add(x264);
        return out;
    }

    /** The first candidate that works with this ffmpeg (null if none, which means ffmpeg itself is broken). */
    public static Choice pick(String ffmpeg) {
        for (Choice c : candidates()) {
            if (works(ffmpeg, c)) {
                System.out.println("[Peregrine] clips: encoding with " + c.name);
                return c;
            }
            System.out.println("[Peregrine] clips: " + c.name + " doesn't work here");
        }
        return null;
    }

    public static Choice byCodec(String codec) {
        for (Choice c : candidates()) {
            if (c.codec.equals(codec)) {
                return c;
            }
        }
        return null;
    }

    static boolean works(String ffmpeg, Choice c) {
        List<String> cmd = new ArrayList<String>();
        Collections.addAll(cmd, ffmpeg, "-hide_banner", "-loglevel", "error");
        cmd.addAll(c.before);
        Collections.addAll(cmd, "-f", "lavfi", "-i", "color=c=black:s=320x240:r=30", "-frames:v", "12");
        cmd.addAll(c.filter);
        Collections.addAll(cmd, "-c:v", c.codec);
        cmd.addAll(c.options);
        Collections.addAll(cmd, "-b:v", "1000k", "-f", "null", "-");
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!p.waitFor(15, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Windows sound devices ffmpeg can record (DirectShow): {what the computer plays, your
     * microphone}, either null when there isn't one. What the computer plays needs a "Stereo Mix"
     * style device (Windows has one on most sound cards; it may need switching on in the sound
     * settings).
     */
    public static String[] windowsAudio(String ffmpeg) {
        String[] out = new String[2];
        try {
            Process p = new ProcessBuilder(ffmpeg, "-hide_banner", "-list_devices", "true", "-f", "dshow", "-i", "dummy")
                    .redirectErrorStream(true).start();
            List<String> audio = new ArrayList<String>();
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream(),
                    java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                boolean audioSection = false;
                while ((line = r.readLine()) != null) {
                    if (line.contains("DirectShow audio devices")) {
                        audioSection = true;
                    } else if (line.contains("DirectShow video devices")) {
                        audioSection = false;
                    }
                    int a = line.indexOf('"'), b = line.lastIndexOf('"');
                    if (a < 0 || b <= a || line.contains("Alternative name")) {
                        continue;
                    }
                    if (line.contains("(audio)") || (audioSection && !line.contains("(video)"))) {
                        audio.add(line.substring(a + 1, b));
                    }
                }
            }
            p.waitFor(10, TimeUnit.SECONDS);
            for (String name : audio) {
                String n = name.toLowerCase();
                boolean loop = n.contains("stereo mix") || n.contains("what u hear") || n.contains("wave out") || n.contains("loopback")
                        || n.contains("stereomix") || n.contains("cable output");
                if (loop && out[0] == null) {
                    out[0] = "audio=" + name;
                } else if (!loop && out[1] == null) {
                    out[1] = "audio=" + name;
                }
            }
        } catch (Exception e) {
            System.out.println("[Peregrine] clips: couldn't list sound devices: " + e.getMessage());
        }
        return out;
    }

    /** Finds ffmpeg: the one the launcher set up, else one on the PATH. */
    public static String findFfmpeg() {
        String set = System.getProperty("peregrine.ffmpeg");
        if (set != null && new File(set).canExecute()) {
            return set;
        }
        String data = System.getProperty("peregrine.data");
        if (data != null) {
            for (String n : new String[] {"ffmpeg", "ffmpeg.exe"}) {
                File f = new File(new File(new File(data, "tools"), "ffmpeg"), n);
                if (f.canExecute()) {
                    return f.getPath();
                }
            }
        }
        String path = System.getenv("PATH");
        if (path != null) {
            for (String d : path.split(File.pathSeparator)) {
                for (String n : new String[] {"ffmpeg", "ffmpeg.exe"}) {
                    File f = new File(d, n);
                    if (f.isFile() && f.canExecute()) {
                        return f.getPath();
                    }
                }
            }
        }
        return null;
    }
}
