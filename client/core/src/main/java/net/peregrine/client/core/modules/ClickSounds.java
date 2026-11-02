package net.peregrine.client.core.modules;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;

import net.peregrine.client.core.Module;
import net.peregrine.client.core.Peregrine;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.BoolSetting;
import net.peregrine.client.core.settings.ChoiceSetting;
import net.peregrine.client.core.settings.SliderSetting;

/**
 * Click sounds: a soft, deep "creamy keyboard" sound for every key you press and a muted click
 * for every mouse button, on the way down and (quieter) on the way back up. Letters, numbers,
 * the spacebar, big keys (Enter, Backspace, Shift...) and each mouse button sound a little
 * different. Your own sounds, picked in the launcher, are used instead where you've set them.
 */
public final class ClickSounds extends Module {

    public final SliderSetting volume = add(new SliderSetting("volume", "Volume", 5f, 100f, 5f, 55f, "%.0f%%"));
    public final BoolSetting release = add(new BoolSetting("release", "Sound when keys come back up", true));
    public final BoolSetting mouse = add(new BoolSetting("mouse", "Mouse clicks", true));
    public final ChoiceSetting when = add(new ChoiceSetting("when", "When", 0,
            "Always", "Only while typing (chat and menus)", "Only while playing"));

    /** Sound kinds; files are click/<kind>_<down|up>_<n> in the game, <kind>.wav for your own. */
    public static final String[] KINDS = {"letter", "number", "space", "enter", "other",
        "mouse_left", "mouse_right", "mouse_middle", "mouse_side"};

    private static volatile ClickSounds instance;
    private final java.util.Random random = new java.util.Random();

    public ClickSounds() {
        super("click_sounds", "Click sounds", "A soft creamy-keyboard sound for every key and mouse click. Pick your own sounds "
                + "for letters, numbers, space, other keys and each mouse button in the launcher", Category.UTILITY, false);
        instance = this;
    }

    /** The kind of sound for a key (GLFW key codes). */
    public static String kindOf(int key) {
        if (key == 32) {
            return "space";
        }
        if ((key >= 48 && key <= 57) || (key >= 320 && key <= 329)) {
            return "number";
        }
        if (key >= 65 && key <= 90) {
            return "letter";
        }
        if (key == 257 || key == 335 || key == 259 || key == 340 || key == 344 || key == 258 || key == 280) {
            return "enter";   // the big keys: Enter, Backspace, Shift, Tab, Caps Lock
        }
        return "other";
    }

    public static String mouseKind(int button) {
        return button == 0 ? "mouse_left" : button == 1 ? "mouse_right" : button == 2 ? "mouse_middle" : "mouse_side";
    }

    /** A key went down (action 1), came up (0) or repeats (2). typing: a screen or chat is open. */
    public static void key(int key, int action, boolean typing) {
        ClickSounds m = instance;
        if (m == null || !m.enabled() || action == 2 || key < 0) {
            return;
        }
        m.play(kindOf(key), action == 1, typing);
    }

    public static void mouseButton(int button, int action, boolean typing) {
        ClickSounds m = instance;
        if (m == null || !m.enabled() || !m.mouse.value || action == 2) {
            return;
        }
        m.play(mouseKind(button), action == 1, typing);
    }

    private void play(String kind, boolean down, boolean typing) {
        if ((when.index == 1 && !typing) || (when.index == 2 && typing) || (!down && !release.value)) {
            return;
        }
        float vol = volume.value / 100f * (down ? 1f : 0.7f);
        float pitch = 0.96f + random.nextFloat() * 0.08f;
        Path own = ownSound(kind);
        if (own != null && OwnSounds.play(own, down ? vol : vol * 0.8f)) {
            return;
        }
        if (!down && own != null) {
            return;   // your own sound plays on the way down only
        }
        Peregrine pg = Peregrine.get();
        Platform p = pg == null ? null : pg.platform();
        if (p != null) {
            p.playUiSound("peregrine-client:click." + kind + "." + (down ? "down" : "up"), vol, pitch);
        }
    }

    // ---- your own sounds: <data>/click-sounds/<kind>.wav, picked in the launcher

    private static final Map<String, Object[]> OWN = new HashMap<String, Object[]>();   // kind -> {path or null, checked at}

    static Path ownSound(String kind) {
        long now = System.currentTimeMillis();
        Object[] e = OWN.get(kind);
        if (e == null || now - (Long) e[1] > 3000) {
            String data = System.getProperty("peregrine.data");
            Path p = data == null ? null : Paths.get(data, "click-sounds", kind + ".wav");
            e = new Object[] {p != null && Files.isRegularFile(p) ? p : null, now};
            OWN.put(kind, e);
        }
        return (Path) e[0];
    }

    /** Plays WAV files with Java's own sound output (a few copies of each, so fast typing overlaps). */
    static final class OwnSounds {
        private static final Map<Path, Clip[]> CLIPS = new HashMap<Path, Clip[]>();
        private static final Map<Path, Long> LOADED = new HashMap<Path, Long>();
        private static final Map<Path, Integer> NEXT = new HashMap<Path, Integer>();

        static synchronized boolean play(Path file, float vol) {
            try {
                long stamp = Files.getLastModifiedTime(file).toMillis();
                Clip[] pool = CLIPS.get(file);
                if (pool == null || !Long.valueOf(stamp).equals(LOADED.get(file))) {
                    if (pool != null) {
                        for (Clip c : pool) {
                            c.close();
                        }
                    }
                    pool = new Clip[4];
                    for (int i = 0; i < pool.length; i++) {
                        try (InputStream in = new BufferedInputStream(Files.newInputStream(file));
                             AudioInputStream a = AudioSystem.getAudioInputStream(in)) {
                            pool[i] = AudioSystem.getClip();
                            pool[i].open(a);
                        }
                    }
                    CLIPS.put(file, pool);
                    LOADED.put(file, stamp);
                }
                int i = NEXT.getOrDefault(file, 0);
                NEXT.put(file, (i + 1) % pool.length);
                Clip c = pool[i];
                c.stop();
                c.setFramePosition(0);
                if (c.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl g = (FloatControl) c.getControl(FloatControl.Type.MASTER_GAIN);
                    float db = (float) (20 * Math.log10(Math.max(0.0001, vol)));
                    g.setValue(Math.max(g.getMinimum(), Math.min(g.getMaximum(), db)));
                }
                c.start();
                return true;
            } catch (Throwable t) {
                return false;
            }
        }
    }
}
