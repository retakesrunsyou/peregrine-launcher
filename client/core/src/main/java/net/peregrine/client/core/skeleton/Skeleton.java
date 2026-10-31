package net.peregrine.client.core.skeleton;

import net.peregrine.client.core.knife.M4;

/**
 * The Skeleton look: players drawn as their bones. The pieces are built once, in the
 * background (a fraction of a second), at three levels of detail, and each frame are only moved
 * to where the player model's parts are, so it costs about what a normal player does.
 */
public final class Skeleton {

    public static final String TEXTURE = "peregrine-client:textures/skeleton/bone.png";

    /** Where vertices go (the game's vertex buffer, or a preview). */
    public interface Sink {
        void vertex(float x, float y, float z, float nx, float ny, float nz, float u, float v, int argb);
    }

    /** Parts in pose arrays, 9 numbers each: x, y, z (pixels), xRot, yRot, zRot (radians), x/y/z scale. */
    public static final int HEAD = 0, BODY = 1, RIGHT_ARM = 2, LEFT_ARM = 3, RIGHT_LEG = 4, LEFT_LEG = 5, PARTS = 6;

    /** [lod] -> head, body, right arm, left arm, right slim arm, left slim arm, right leg, left leg. */
    private static final Geo[][] MESH = new Geo[3][];
    private static volatile boolean started;

    private Skeleton() {
    }

    /** Starts building the bones (does nothing if already started). */
    public static synchronized void prepare() {
        if (started) {
            return;
        }
        started = true;
        Thread t = new Thread(() -> {
            try {
                for (int lod = 2; lod >= 0; lod--) {   // the simple one first, so something shows quickly
                    MESH[lod] = build(lod);
                }
            } catch (Throwable e) {
                System.err.println("[Peregrine] couldn't build the skeleton: " + e);
            }
        }, "Peregrine skeleton");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    /** Builds one level of detail now (tests and previews). */
    public static Geo[] build(int lod) {
        Geo ra = Bones.rightArm(lod, false), rs = Bones.rightArm(lod, true), rl = Bones.rightLeg(lod);
        return new Geo[] {Bones.head(lod), Bones.body(lod), ra, ra.mirrorX(), rs, rs.mirrorX(), rl, rl.mirrorX()};
    }

    /** For tests and previews: use these meshes. */
    public static void use(int lod, Geo[] m) {
        MESH[lod] = m;
        started = true;
    }

    public static boolean ready() {
        return MESH[0] != null || MESH[1] != null || MESH[2] != null;
    }

    /** The detail to use at a distance (squared, blocks): the nearest built one to what's wanted. */
    public static int lod(double distSq) {
        int want = distSq < 14 * 14 ? 0 : distSq < 36 * 36 ? 1 : 2;
        for (int d = 0; d < 3; d++) {
            if (MESH[Math.min(2, want + d)] != null) {
                return Math.min(2, want + d);
            }
            if (want - d >= 0 && MESH[want - d] != null) {
                return want - d;
            }
        }
        return -1;
    }

    /** Draws the whole skeleton for a player model pose (see the part constants), in model space (blocks). */
    public static void draw(float[] pose, boolean slim, int lod, Sink sink) {
        Geo[] m = lod < 0 ? null : MESH[lod];
        if (m == null) {
            return;
        }
        part(m[0], pose, HEAD, sink);
        part(m[1], pose, BODY, sink);
        part(m[slim ? 4 : 2], pose, RIGHT_ARM, sink);
        part(m[slim ? 5 : 3], pose, LEFT_ARM, sink);
        part(m[6], pose, RIGHT_LEG, sink);
        part(m[7], pose, LEFT_LEG, sink);
    }

    /** Just one arm, already placed (first person): vertices in the arm part's pixels. */
    public static void arm(boolean right, boolean slim, int lod, M4 m, Sink sink) {
        Geo[] g = lod < 0 ? null : MESH[lod];
        if (g == null) {
            return;
        }
        emit(g[right ? (slim ? 4 : 2) : (slim ? 5 : 3)], m, sink);
    }

    private static void part(Geo g, float[] pose, int part, Sink sink) {
        int o = part * 9;
        M4 m = new M4().translate(pose[o] / 16f, pose[o + 1] / 16f, pose[o + 2] / 16f)
                .rotZ((float) Math.toDegrees(pose[o + 5]))
                .rotY((float) Math.toDegrees(pose[o + 4]))
                .rotX((float) Math.toDegrees(pose[o + 3]))
                .scale(pose[o + 6] / 16f, pose[o + 7] / 16f, pose[o + 8] / 16f);
        emit(g, m, sink);
    }

    static void emit(Geo g, M4 m, Sink sink) {
        float[] p = new float[3], n = new float[3];
        float[] v = g.v;
        int[] q = g.q, c = g.c;
        for (int i = 0; i < g.quads; i++) {
            for (int j = 0; j < 4; j++) {
                int k = q[i * 4 + j], b = k * Geo.STRIDE;
                m.point(v[b], v[b + 1], v[b + 2], p);
                m.dir(v[b + 3], v[b + 4], v[b + 5], n);
                sink.vertex(p[0], p[1], p[2], n[0], n[1], n[2], v[b + 6], v[b + 7], c[k]);
            }
        }
    }

    /** Quads in a detail level (for tests). */
    public static int quads(Geo[] m) {
        int n = 0;
        for (Geo g : m) {
            n += g.quads;
        }
        return n;
    }

    /**
     * The standing pose of a player model (as Minecraft sets it up before animating): handy for
     * previews and tests.
     */
    public static float[] standing(boolean slim) {
        float[] p = new float[PARTS * 9];
        set(p, HEAD, 0, 0, 0);
        set(p, BODY, 0, 0, 0);
        set(p, RIGHT_ARM, -5, slim ? 2.5f : 2, 0);
        set(p, LEFT_ARM, 5, slim ? 2.5f : 2, 0);
        set(p, RIGHT_LEG, -1.9f, 12, 0);
        set(p, LEFT_LEG, 1.9f, 12, 0);
        return p;
    }

    public static void set(float[] p, int part, float x, float y, float z) {
        int o = part * 9;
        p[o] = x;
        p[o + 1] = y;
        p[o + 2] = z;
        p[o + 6] = p[o + 7] = p[o + 8] = 1;
    }

    public static void rot(float[] p, int part, float xRot, float yRot, float zRot) {
        int o = part * 9;
        p[o + 3] = xRot;
        p[o + 4] = yRot;
        p[o + 5] = zRot;
    }
}
