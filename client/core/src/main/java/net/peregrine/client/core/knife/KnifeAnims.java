package net.peregrine.client.core.knife;

/**
 * The knife's moves, as keyframes added on top of the resting hold. Channels:
 *   FX, FY, FZ          the fist moves (blocks; x right, y up, z toward you)
 *   PITCH, YAW, ROLL    the arm turns (degrees)
 *   WP, WY, WR          the wrist bends (degrees)
 *   SPIN                the knife twirls in the hand round its spin point (degrees)
 *   TWIST               the knife turns round its own length (degrees)
 *   TOSS                the knife leaves the hand, straight up (blocks)
 *   FOLD, LOOP          butterfly knife: how closed the blade is (0 open .. 1 closed), and
 *                       the extra turn of the swinging handle (degrees)
 * Every channel starts and ends at 0 unless a key says otherwise, so moves blend into the hold.
 */
public final class KnifeAnims {

    public static final int FX = 0, FY = 1, FZ = 2, PITCH = 3, YAW = 4, ROLL = 5, WP = 6, WY = 7, WR = 8,
            SPIN = 9, TWIST = 10, TOSS = 11, FOLD = 12, LOOP = 13, CHANNELS = 14;

    public static final class Anim {
        public final String name;
        public final float seconds;
        final float[][] t = new float[CHANNELS][];
        final float[][] v = new float[CHANNELS][];

        /** keys: rows of {time 0..1, channel, value, channel, value, ...}. */
        Anim(String name, float seconds, float[]... keys) {
            this.name = name;
            this.seconds = seconds;
            for (int ch = 0; ch < CHANNELS; ch++) {
                int count = 0;
                boolean start = false, end = false;
                for (float[] k : keys) {
                    for (int i = 1; i + 1 < k.length; i += 2) {
                        if ((int) k[i] == ch) {
                            count++;
                            start |= k[0] <= 0f;
                            end |= k[0] >= 1f;
                        }
                    }
                }
                if (count == 0) {
                    continue;
                }
                int n = count + (start ? 0 : 1) + (end ? 0 : 1);
                float[] tt = new float[n], vv = new float[n];
                int j = 0;
                if (!start) {
                    tt[j] = 0;
                    vv[j++] = 0;
                }
                for (float[] k : keys) {
                    for (int i = 1; i + 1 < k.length; i += 2) {
                        if ((int) k[i] == ch) {
                            tt[j] = k[0];
                            vv[j++] = k[i + 1];
                        }
                    }
                }
                if (!end) {
                    tt[j] = 1;
                    vv[j] = 0;
                }
                t[ch] = tt;
                v[ch] = vv;
            }
        }

        /** Adds this move's values at fraction f (0..1) of its time into out, times weight. */
        public void add(float f, float weight, float[] out) {
            for (int ch = 0; ch < CHANNELS; ch++) {
                if (t[ch] != null) {
                    out[ch] += sample(t[ch], v[ch], f) * weight;
                }
            }
        }

        public boolean has(int ch) {
            return t[ch] != null;
        }
    }

    /** Smooth through the keys (Catmull-Rom, flat at the ends). */
    static float sample(float[] t, float[] v, float f) {
        int n = t.length;
        if (f <= t[0]) {
            return v[0];
        }
        if (f >= t[n - 1]) {
            return v[n - 1];
        }
        int i = 0;
        while (i < n - 2 && f >= t[i + 1]) {
            i++;
        }
        float h = t[i + 1] - t[i];
        float u = (f - t[i]) / h;
        float m0 = i == 0 ? 0 : (v[i + 1] - v[i - 1]) / (t[i + 1] - t[i - 1]) * h;
        float m1 = i + 2 >= n ? 0 : (v[i + 2] - v[i]) / (t[i + 2] - t[i]) * h;
        float u2 = u * u, u3 = u2 * u;
        return (2 * u3 - 3 * u2 + 1) * v[i] + (u3 - 2 * u2 + u) * m0 + (-2 * u3 + 3 * u2) * v[i + 1] + (u3 - u2) * m1;
    }

    private static float[] k(float t, float... cv) {
        float[] r = new float[cv.length + 1];
        r[0] = t;
        System.arraycopy(cv, 0, r, 1, cv.length);
        return r;
    }

    private KnifeAnims() {
    }

    // ------------------------------------------------------------------ pulling it out

    public static final Anim DRAW = new Anim("draw", 0.95f,
            k(0, FY, -0.45f, FX, 0.12f, PITCH, -35, WP, -40, SPIN, -360),
            k(0.35f, FY, -0.06f, FX, 0.03f, PITCH, -6, WP, 8, SPIN, -40),
            k(0.62f, FY, 0.02f, WP, -4, WR, 10, SPIN, 0),
            k(0.82f, FY, -0.008f, WR, -3));

    /** The butterfly knife flips open as it comes out. */
    public static final Anim DRAW_BUTTERFLY = new Anim("draw", 1.15f,
            k(0, FY, -0.45f, FX, 0.12f, PITCH, -35, FOLD, 1, LOOP, 0),
            k(0.25f, FY, -0.05f, PITCH, -5, FOLD, 1, LOOP, 0, WY, 0),
            k(0.45f, FOLD, 0.55f, LOOP, -120, WY, -25, WR, 15),
            k(0.68f, FOLD, 0, LOOP, -360, WY, 10, WR, -6),
            k(0.85f, FOLD, 0, LOOP, -360, WY, -2),
            k(1, FOLD, 0, LOOP, -360));

    // ------------------------------------------------------------------ hits

    /** A forehand slash: the whole arm sweeps right to left across the screen, blade flat. */
    public static final Anim SLASH_FORE = new Anim("forehand", 0.34f,
            k(0.12f, FX, 0.10f, FY, 0.03f, YAW, -22, WR, -60, WY, -25, WP, 25),
            k(0.45f, FX, -0.26f, FY, -0.02f, FZ, -0.08f, YAW, 30, WR, -75, WY, 35, WP, 30),
            k(0.70f, FX, -0.18f, FY, -0.04f, YAW, 18, WR, -40, WY, 20, WP, 15));

    /** A backhand slash: left to right. */
    public static final Anim SLASH_BACK = new Anim("backhand", 0.34f,
            k(0.12f, FX, -0.14f, FY, 0.04f, YAW, 22, WR, 70, WY, 30, WP, 20),
            k(0.45f, FX, 0.18f, FY, -0.03f, FZ, -0.06f, YAW, -28, WR, 85, WY, -40, WP, 30),
            k(0.70f, FX, 0.10f, FY, -0.04f, YAW, -14, WR, 45, WY, -20, WP, 12));

    /** An uppercut: low to high, blade leading upwards. */
    public static final Anim UPPERCUT = new Anim("uppercut", 0.36f,
            k(0.15f, FY, -0.16f, FX, 0.03f, PITCH, 18, WP, -30),
            k(0.48f, FY, 0.14f, FZ, -0.10f, FX, -0.06f, PITCH, -22, WP, 40, WR, -20),
            k(0.72f, FY, 0.06f, FZ, -0.05f, PITCH, -10, WP, 20));

    /** An overhead chop (every third hit of a combo): the blade comes down from above. */
    public static final Anim CHOP = new Anim("chop", 0.40f,
            k(0.18f, FY, 0.16f, FZ, 0.04f, FX, -0.04f, PITCH, 20, WP, -55, WR, -15),
            k(0.50f, FY, -0.16f, FZ, -0.10f, FX, -0.08f, PITCH, -30, WP, 75, WR, -10),
            k(0.74f, FY, -0.08f, FZ, -0.05f, PITCH, -14, WP, 35));

    /** A critical hit: the stab drives straight forward, point first. */
    public static final Anim STAB = new Anim("stab", 0.38f,
            k(0.16f, FZ, 0.08f, FY, -0.02f, WP, 62, FX, 0.02f),
            k(0.40f, FZ, -0.34f, FX, -0.12f, FY, 0.03f, WP, 80, PITCH, -8),
            k(0.66f, FZ, -0.16f, FX, -0.06f, WP, 45));

    /** A critical hit, the other way: a lunge that twists the blade in. */
    public static final Anim LUNGE = new Anim("lunge", 0.46f,
            k(0.14f, FZ, 0.07f, WP, 55, TWIST, 0),
            k(0.38f, FZ, -0.38f, FX, -0.14f, FY, 0.02f, WP, 82, TWIST, 0),
            k(0.55f, FZ, -0.33f, FX, -0.12f, WP, 80, TWIST, 160),
            k(0.80f, FZ, -0.10f, WP, 30, TWIST, 180),
            k(1, TWIST, 180));

    // ------------------------------------------------------------------ inspecting (F)

    /** Show-off: bring it in, turn it to show both sides, then a spin. */
    public static final Anim SHOWOFF = new Anim("show-off", 2.7f,
            k(0.12f, FX, -0.14f, FY, 0.06f, FZ, 0.05f, WY, -30, WR, -20, TWIST, 0),
            k(0.35f, FX, -0.16f, FY, 0.07f, FZ, 0.06f, WY, -36, WR, -25, TWIST, 170),
            k(0.55f, FX, -0.14f, FY, 0.06f, FZ, 0.06f, WY, -30, WR, -20, TWIST, 180),
            k(0.66f, FX, -0.08f, FY, 0.03f, WY, -10, TWIST, 360, SPIN, 0),
            k(0.85f, SPIN, 360, TWIST, 360),
            k(1, SPIN, 360, TWIST, 360));

    /** Looking over the blade: held flat in front, turned slowly along. */
    public static final Anim LOOK_OVER = new Anim("look over", 2.9f,
            k(0.15f, FX, -0.18f, FY, 0.08f, FZ, 0.08f, WR, -80, WP, 15, YAW, 8, TWIST, 0),
            k(0.45f, FX, -0.20f, FY, 0.10f, FZ, 0.10f, WR, -85, WP, 25, YAW, 12, TWIST, -40),
            k(0.70f, FX, -0.18f, FY, 0.08f, FZ, 0.08f, WR, -70, WP, -10, YAW, 6, TWIST, -150),
            k(0.85f, FX, -0.05f, WR, -20, TWIST, -180),
            k(1, TWIST, -180));

    /** Toss and catch: up it goes, turning over twice, back into the fist. */
    public static final Anim TOSS_CATCH = new Anim("toss", 2.0f,
            k(0.10f, FY, -0.06f, WP, -15, TOSS, 0, SPIN, 0),
            k(0.22f, FY, 0.05f, WP, 10, TOSS, 0.05f, SPIN, 60),
            k(0.45f, FY, 0.02f, TOSS, 0.42f, SPIN, 400),
            k(0.66f, FY, -0.02f, TOSS, 0.04f, SPIN, 700),
            k(0.72f, FY, -0.07f, TOSS, 0, SPIN, 720, WP, -12),
            k(0.86f, FY, 0.01f, SPIN, 720, WP, 4),
            k(1, SPIN, 720));

    /** Spin frenzy: fast twirls while the wrist rolls round. */
    public static final Anim FRENZY = new Anim("frenzy", 2.3f,
            k(0.10f, FX, -0.08f, FY, 0.04f, WY, -20, SPIN, 0),
            k(0.30f, WY, 20, WR, 30, SPIN, 540),
            k(0.50f, WY, -25, WR, -30, SPIN, 1080, FX, -0.12f),
            k(0.70f, WY, 15, WR, 20, SPIN, 1620),
            k(0.85f, FX, -0.02f, WY, 0, WR, 0, SPIN, 1800),
            k(1, SPIN, 1800));

    /** The butterfly knife's inspect: closes and flips open again, twice. */
    public static final Anim FLIPS = new Anim("flips", 2.6f,
            k(0.08f, FX, -0.08f, FY, 0.04f, FOLD, 0, LOOP, 0, WY, -10),
            k(0.22f, FOLD, 1, LOOP, 0, WY, 20, WR, -20),
            k(0.30f, FOLD, 1, LOOP, 0),
            k(0.45f, FOLD, 0, LOOP, -360, WY, -25, WR, 15),
            k(0.55f, FOLD, 0, LOOP, -360),
            k(0.68f, FOLD, 1, LOOP, -360, WY, 20),
            k(0.76f, FOLD, 1, LOOP, -360),
            k(0.90f, FOLD, 0, LOOP, -720, WY, -5, FX, -0.03f),
            k(1, FOLD, 0, LOOP, -720));

    // ------------------------------------------------------------------ fidgets while standing still

    public static final Anim IDLE_SPIN = new Anim("idle spin", 1.1f,
            k(0.15f, WP, -10, SPIN, 0),
            k(0.65f, SPIN, 360, WP, 6),
            k(1, SPIN, 360));

    public static final Anim IDLE_FLICK = new Anim("wrist flick", 0.9f,
            k(0.20f, WY, 28, WR, 10),
            k(0.38f, WY, -22, WR, -8),
            k(0.60f, WY, 6));

    public static final Anim IDLE_DOUBLE = new Anim("double spin", 1.5f,
            k(0.12f, WP, -10, SPIN, 0),
            k(0.45f, SPIN, 360),
            k(0.75f, SPIN, 720, WP, 6),
            k(1, SPIN, 720));

    public static final Anim IDLE_TAP = new Anim("tap", 1.4f,
            k(0.15f, WP, 30, FY, -0.02f),
            k(0.28f, WP, 42, FY, -0.04f),
            k(0.38f, WP, 30, FY, -0.02f),
            k(0.50f, WP, 42, FY, -0.04f),
            k(0.62f, WP, 30, FY, -0.02f),
            k(0.80f, WP, 5));

    public static final Anim[] INSPECTS = {SHOWOFF, LOOK_OVER, TOSS_CATCH, FRENZY};
    public static final Anim[] IDLES = {IDLE_SPIN, IDLE_FLICK, IDLE_DOUBLE, IDLE_TAP};
}
