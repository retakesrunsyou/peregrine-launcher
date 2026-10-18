package net.peregrine.client.core;

/**
 * Picks Peregrine's interface art (made by tools/make_gui_art.py) at a sharpness that suits the
 * GUI scale, so pictures are drawn close to one texture pixel per screen pixel: smooth, never
 * blocky when blown up or shimmery when shrunk a lot.
 */
final class Art {

    private Art() {
    }

    /** Screen pixels per GUI pixel right now (2 if unknown). */
    static int scale() {
        try {
            Peregrine pc = Peregrine.get();
            if (pc != null && pc.platform() != null) {
                return Math.max(1, Math.min(8, (int) Math.round(pc.platform().guiScale())));
            }
        } catch (RuntimeException e) {
            // not set up yet
        }
        return 2;
    }

    private static int bucket() {
        int s = scale();
        return s <= 2 ? 2 : s <= 4 ? 4 : 6;
    }

    /** A filled quarter-circle set for corners of radius r (2..16). */
    static String round(int r) {
        return Draw.GUI + "round/" + r + "_" + bucket() + ".png";
    }

    /** A 1 px ring for outlines of radius r (2..16). */
    static String ring(int r) {
        return Draw.GUI + "ring/" + r + "_" + bucket() + ".png";
    }

    /** The smallest of sizes that is at least about as big as px screen pixels. */
    private static int fit(float px, int... sizes) {
        for (int s : sizes) {
            if (s >= px * 0.8f) {
                return s;
            }
        }
        return sizes[sizes.length - 1];
    }

    /** An icon to be drawn size GUI pixels square. */
    static String icon(String name, int size) {
        return Draw.GUI + "icon/" + fit(size * scale(), 32, 64, 128) + "/" + name + ".png";
    }

    /** The falcon to be drawn size GUI pixels square. */
    static String falcon(int size) {
        return Draw.GUI + "falcon/" + fit(size * scale(), 64, 128, 256, 512) + ".png";
    }

    /** The PEREGRINE wordmark to be drawn w GUI pixels wide (it's 8.8 times as wide as tall). */
    static String wordmark(int w) {
        return Draw.GUI + "wordmark/" + fit(w * scale(), 256, 512, 1024) + ".png";
    }

    static final float WORDMARK_ASPECT = 8.797f;
}
