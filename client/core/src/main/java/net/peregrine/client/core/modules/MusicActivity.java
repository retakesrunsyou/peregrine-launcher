package net.peregrine.client.core.modules;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.HudModule;
import net.peregrine.client.core.NowPlaying;
import net.peregrine.client.core.Peregrine;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.Theme;
import net.peregrine.client.core.settings.BoolSetting;
import net.peregrine.client.core.settings.ChoiceSetting;

/**
 * Music activity: what you're listening to (Spotify, YouTube, any music app), with
 * the cover, the song, the artist and how long is left.
 */
public final class MusicActivity extends HudModule {

    static final int W = 172, H = 38, ART = 30;

    private final BoolSetting art = add(new BoolSetting("art", "Show the cover", true));
    private final ChoiceSetting time = add(new ChoiceSetting("time", "Time", 0, "Time left", "Played / length"));
    private final BoolSetting app = add(new BoolSetting("app", "Show where it's playing", true));
    private final BoolSetting paused = add(new BoolSetting("paused", "Keep showing when paused", true));

    public MusicActivity() {
        super("music", "Music activity", "The song you're playing on Spotify, YouTube or any music app", false, 0.25f, 0.74f);
    }

    private NowPlaying.Track track() {
        NowPlaying.Track t = NowPlaying.get();
        return t == null || (!t.playing && !paused.value) ? null : t;
    }

    @Override
    public boolean hasContent(Platform p) {
        return track() != null;
    }

    @Override
    public int width(Draw d, Platform p) {
        return W;
    }

    @Override
    public int height(Draw d, Platform p) {
        return H;
    }

    @Override
    public void render(Draw d, Platform p, int x, int y) {
        NowPlaying.Track t = track();
        if (t == null) {
            return;
        }
        int accent = Peregrine.get().accent();
        panel(d, x, y, W, H);
        int tx = x + 5;
        int[] cover = NowPlaying.cover();
        if (art.value) {
            int ax = x + 4, ay = y + 4;
            if (cover != null) {
                drawCover(d, cover, ax, ay, ART);
            } else {  // no cover: a note on the accent color
                d.roundRect(ax, ay, ART, ART, 3, Theme.withAlpha(accent, 0x70));
                d.text("♫", ax + (ART - d.width("♫")) / 2, ay + 11, Theme.TEXT, false);
            }
            if (!t.playing) {  // paused: two bars over the cover
                d.rect(ax, ay, ART, ART, 0x66000000);
                d.rect(ax + 10, ay + 9, 3, 12, 0xEEFFFFFF);
                d.rect(ax + 17, ay + 9, 3, 12, 0xEEFFFFFF);
            }
            tx = ax + ART + 6;
        }
        int right = x + W - 5;
        d.text(d.trim(t.title, right - tx), tx, y + 4, Theme.TEXT, true);
        String sub = t.artist;
        if (app.value && !t.app.isEmpty()) {
            sub = sub.isEmpty() ? t.app : sub + "  ·  " + t.app;
        }
        d.text(d.trim(sub, right - tx), tx, y + 14, Theme.MUTED, true);

        // progress: a thin bar and the time
        long pos = t.positionNow();
        String clock = t.length <= 0 ? (t.playing ? "LIVE" : "")
                : time.index == 0 ? "-" + NowPlaying.time(t.length - pos)
                : NowPlaying.time(pos) + " / " + NowPlaying.time(t.length);
        float s = 0.75f;
        int cw = Math.round(d.width(clock) * s);
        int barW = Math.max(10, right - tx - cw - 5);
        int by = y + H - 8;
        d.roundRect(tx, by, barW, 3, 1, 0x40FFFFFF);
        if (t.length > 0) {
            int fill = Math.max(1, Math.min(barW, Math.round(barW * (pos / (float) t.length))));
            d.roundRect(tx, by, fill, 3, 1, accent);
        }
        d.textScaled(clock, right - cw, by - 2, Theme.MUTED, s, true);
    }

    /** The cover, from its grid of colors (rows of the same color are drawn as one strip). */
    static void drawCover(Draw d, int[] c, int x, int y, int size) {
        int n = NowPlaying.ART;
        d.pushScale(x, y, size / (float) n);
        for (int row = 0; row < n; row++) {
            int start = 0;
            for (int col = 1; col <= n; col++) {
                if (col == n || c[row * n + col] != c[row * n + start]) {
                    d.rect(start, row, col - start, 1, c[row * n + start]);
                    start = col;
                }
            }
        }
        d.popScale();
    }
}
