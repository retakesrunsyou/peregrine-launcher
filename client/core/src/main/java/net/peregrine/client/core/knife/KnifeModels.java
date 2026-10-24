package net.peregrine.client.core.knife;

import java.util.ArrayList;
import java.util.List;

/**
 * Peregrine's knives, modelled here in centimetres (real sizes). Knife space: the blade points
 * up (+y) from y = 0, the handle hangs below it, the sharp edge faces -x, z goes through the
 * blade's thickness. Each knife says where the hand grips it and how.
 */
public final class KnifeModels {

    public static final String[] NAMES = {"Karambit", "Butterfly knife", "Bayonet", "Combat knife", "Nomad",
        "Skeleton knife", "Push dagger"};

    /** How the hand holds it: blade up out of the fist, blade down (ring on top), blade between the fingers. */
    public static final int HAMMER = 0, REVERSE = 1, PUSH = 2;

    /** A piece of a knife. group: 0 moves with the handle you hold; 1 the butterfly blade; 2 its free handle. */
    public static final class Part {
        public final Mesh mesh;
        public final int group;

        Part(Mesh mesh, int group) {
            this.mesh = mesh;
            this.group = group;
        }
    }

    public static final class Knife {
        public final String name;
        public final List<Part> parts = new ArrayList<Part>();
        public float[] grip = {0, -5};          // where the fist holds it (x, y)
        public int hold = HAMMER;
        public float[] spin = {0, -5};          // what it turns round in spins (x, y)
        public float[] tip = {0, 15};
        public float[] pinA, pinB;              // butterfly pivots
        public int quads;

        Knife(String name) {
            this.name = name;
        }

        Mesh add(int group) {
            Mesh m = new Mesh();
            parts.add(new Part(m, group));
            return m;
        }

        Knife done() {
            for (Part p : parts) {
                quads += p.mesh.quads;
            }
            // the tip: the blade's point farthest from the grip
            Mesh b = parts.get(0).mesh;
            float best = -1;
            for (int i = 0; i < b.verts; i++) {
                float dx = b.v[i * Mesh.STRIDE] - grip[0], dy = b.v[i * Mesh.STRIDE + 1] - grip[1];
                float d = dx * dx + dy * dy;
                if (d > best) {
                    best = d;
                    tip = new float[] {b.v[i * Mesh.STRIDE], b.v[i * Mesh.STRIDE + 1]};
                }
            }
            return this;
        }
    }

    private static Knife[] cache, lowCache;

    private KnifeModels() {
    }

    /** All seven knives (built once). */
    public static synchronized Knife[] all() {
        if (cache == null) {
            cache = new Knife[] {karambit(), butterfly(), bayonet(), combat(), nomad(), skeleton(), pushDagger()};
        }
        return cache;
    }

    /** The same knives with far fewer faces (for glows and afterimages). */
    public static synchronized Knife[] low() {
        if (lowCache == null) {
            all();
            Shapes.detail = 0.34f;
            try {
                lowCache = new Knife[] {karambit(), butterfly(), bayonet(), combat(), nomad(), skeleton(), pushDagger()};
            } finally {
                Shapes.detail = 1f;
            }
        }
        return lowCache;
    }

    public static Knife getLow(int i) {
        Knife[] a = low();
        return a[Math.max(0, Math.min(a.length - 1, i))];
    }

    public static Knife get(int i) {
        Knife[] a = all();
        return a[Math.max(0, Math.min(a.length - 1, i))];
    }

    // ------------------------------------------------------------------ helpers

    /** A straight-ish handle from (x0,y0) to (x1,y1) through a middle control, with a size profile. */
    private static float[][] bar(float[] line, int n, float[][] sizes) {
        float[][] p = Shapes.path(line, n, 4);
        n = p.length;
        for (int i = 0; i < n; i++) {
            float s = i / (float) (n - 1);
            p[i][2] = Shapes.key(sizes, 1, s);
            p[i][3] = Shapes.key(sizes, 2, s);
        }
        return p;
    }

    /** Rounds off the last few points of a bar to a dome (pommel or end of a guard). */
    private static float[][] domeEnds(float[][] p, int each, boolean start, boolean end) {
        int n = p.length;
        each = Math.min(each, (start && end ? n / 2 : n) - 1);
        for (int k = 0; k < each; k++) {
            float f = (float) Math.sqrt(1 - Math.pow((each - k) / (float) (each + 0.35f), 2));
            if (start) {
                p[k][2] *= f;
                p[k][3] *= f;
            }
            if (end) {
                p[n - 1 - k][2] *= f;
                p[n - 1 - k][3] *= f;
            }
        }
        return p;
    }

    private static float[] line(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3) {
        return new float[] {x0, y0, x1, y1, x2, y2, x3, y3};
    }

    private static float[] straight(float x0, float y0, float x1, float y1) {
        return line(x0, y0, x0 + (x1 - x0) / 3, y0 + (y1 - y0) / 3, x0 + 2 * (x1 - x0) / 3, y0 + 2 * (y1 - y0) / 3, x1, y1);
    }

    // ------------------------------------------------------------------ the knives
    // blade keys: {s, spine offset, edge offset, half thickness, swedge}; offsets are along the
    // blade line's left normal, which is -x for a blade pointing up: so the edge (on -x) is
    // positive and the spine (+x) negative.

    static Knife bayonet() {
        Knife k = new Knife("Bayonet");
        Shapes.blade(k.add(0), straight(0, 0, 0, 17.5f), new float[][] {
            {0.00f, -1.25f, 1.85f, 0.30f, 0},
            {0.05f, -1.25f, 1.85f, 0.30f, 0},
            {0.62f, -1.25f, 1.72f, 0.27f, 0},
            {0.71f, -1.12f, 1.62f, 0.25f, 0.2f},
            {0.80f, -0.80f, 1.42f, 0.22f, 0.65f},
            {0.90f, -0.30f, 0.95f, 0.16f, 0.9f},
            {0.97f, 0.12f, 0.38f, 0.07f, 1},
            {1.00f, 0.25f, 0.25f, 0.00f, 1},
        }, 0.48f, 0.12f, 100, new float[] {0.14f, 0.52f, 11, 0.28f});
        // cross guard
        float[][] guard = domeEnds(bar(straight(-2.7f, -0.28f, 2.5f, -0.28f), 22, new float[][] {
            {0, 0.30f, 0.62f}, {0.5f, 0.32f, 0.70f}, {1, 0.30f, 0.62f}}), 4, true, true);
        Shapes.sweep(k.add(0), guard, false, 16, 3.2f, 0, Shapes.METAL_V0, Shapes.METAL_V1);
        // handle with a light swell and a metal pommel
        float[][] h = bar(straight(-0.2f, -0.55f, -0.2f, -12.0f), 30, new float[][] {
            {0, 1.25f, 1.05f}, {0.25f, 1.42f, 1.15f}, {0.55f, 1.38f, 1.12f}, {0.85f, 1.48f, 1.18f}, {1, 1.45f, 1.15f}});
        Shapes.sweep(k.add(0), h, false, 24, 3.6f, 0, Shapes.HANDLE_V0, Shapes.HANDLE_V1);
        float[][] pom = domeEnds(bar(straight(-0.2f, -11.9f, -0.2f, -13.0f), 8, new float[][] {
            {0, 1.5f, 1.2f}, {1, 1.5f, 1.2f}}), 4, false, true);
        Shapes.sweep(k.add(0), pom, false, 24, 3.6f, 0, Shapes.METAL_V0, Shapes.METAL_V1);
        k.grip = new float[] {-0.2f, -5.6f};
        k.spin = new float[] {-0.2f, -5.0f};
        return k.done();
    }

    static Knife combat() {
        Knife k = new Knife("Combat knife");
        Shapes.blade(k.add(0), straight(0, 0, 0, 16.5f), new float[][] {
            {0.00f, -1.10f, 1.55f, 0.30f, 0},
            {0.06f, -1.10f, 1.62f, 0.30f, 0},
            {0.55f, -1.10f, 1.58f, 0.28f, 0},
            {0.68f, -0.98f, 1.50f, 0.26f, 0.35f},
            {0.84f, -0.50f, 1.18f, 0.21f, 0.8f},
            {0.95f, 0.02f, 0.58f, 0.10f, 1},
            {1.00f, 0.22f, 0.22f, 0.00f, 1},
        }, 0.40f, 0.10f, 70, null);
        float[][] guard = domeEnds(bar(line(-2.2f, -0.3f, -0.8f, -0.25f, 0.8f, -0.25f, 1.7f, -0.45f), 20, new float[][] {
            {0, 0.28f, 0.55f}, {0.5f, 0.30f, 0.62f}, {1, 0.28f, 0.5f}}), 4, true, true);
        Shapes.sweep(k.add(0), guard, false, 16, 3f, 0, Shapes.METAL_V0, Shapes.METAL_V1);
        // handle with three finger grooves
        float[][] sizes = new float[13][3];
        for (int i = 0; i < 13; i++) {
            float s = i / 12f;
            float groove = s < 0.62f ? (float) Math.pow(Math.sin(s / 0.62 * Math.PI * 3), 2) : 0;
            sizes[i][0] = s;
            sizes[i][1] = 1.32f - 0.14f * groove + 0.12f * Math.max(0, (s - 0.75f) / 0.25f);
            sizes[i][2] = 1.05f + 0.06f * Math.max(0, (s - 0.75f) / 0.25f);
        }
        float[][] h = domeEnds(bar(straight(-0.15f, -0.5f, -0.15f, -12.3f), 40, sizes), 4, false, true);
        Shapes.sweep(k.add(0), h, false, 24, 3.4f, 0, Shapes.HANDLE_V0, Shapes.HANDLE_V1);
        k.grip = new float[] {-0.15f, -5.4f};
        k.spin = new float[] {-0.15f, -4.8f};
        return k.done();
    }

    static Knife karambit() {
        Knife k = new Knife("Karambit");
        // the claw: curves toward the edge side (the edge is on the inside of the curve)
        Shapes.blade(k.add(0), line(0, 0, 0, 3.2f, -1.4f, 6.6f, -4.6f, 7.6f), new float[][] {
            {0.00f, -0.95f, 0.85f, 0.26f, 0},
            {0.25f, -1.12f, 0.95f, 0.26f, 0},
            {0.60f, -1.05f, 0.80f, 0.23f, 0.15f},
            {0.85f, -0.62f, 0.45f, 0.15f, 0.6f},
            {0.96f, -0.18f, 0.10f, 0.06f, 0.9f},
            {1.00f, 0.00f, 0.00f, 0.00f, 1},
        }, 0.42f, 0.12f, 70, null);
        // bolster
        float[][] bol = domeEnds(bar(straight(-1.2f, -0.25f, 1.15f, -0.25f), 12, new float[][] {
            {0, 0.32f, 0.5f}, {1, 0.32f, 0.5f}}), 3, true, true);
        Shapes.sweep(k.add(0), bol, false, 14, 3f, 0, Shapes.METAL_V0, Shapes.METAL_V1);
        // the handle curves the same way, ending in the finger ring
        float[] hl = line(0, -0.45f, 0.1f, -3.8f, -0.1f, -7.0f, -1.1f, -9.2f);
        float[][] h = bar(hl, 30, new float[][] {
            {0, 1.05f, 0.82f}, {0.4f, 1.18f, 0.88f}, {0.8f, 1.10f, 0.84f}, {1, 0.95f, 0.78f}});
        Shapes.sweep(k.add(0), h, false, 22, 3.4f, 0, Shapes.HANDLE_V0, Shapes.HANDLE_V1);
        float rx = -2.05f, ry = -10.75f;
        Shapes.ring(k.add(0), rx, ry, 1.55f, 0.42f, 40, 14, Shapes.METAL_V0, Shapes.METAL_V1);
        k.hold = REVERSE;
        k.grip = new float[] {-0.15f, -4.6f};
        k.spin = new float[] {rx, ry};
        return k.done();
    }

    static Knife butterfly() {
        Knife k = new Knife("Butterfly knife");
        Shapes.blade(k.add(1), straight(0, -1.3f, 0, 9.8f), new float[][] {
            {0.00f, -1.15f, 1.15f, 0.22f, 0},
            {0.14f, -1.15f, 1.15f, 0.22f, 0},
            {0.18f, -1.12f, 1.10f, 0.22f, 0},
            {0.68f, -1.12f, 1.02f, 0.21f, 0.1f},
            {0.84f, -0.80f, 0.70f, 0.17f, 0.6f},
            {0.95f, -0.25f, 0.20f, 0.08f, 1},
            {1.00f, -0.05f, -0.05f, 0.00f, 1},
        }, 0.45f, 0.10f, 56, null);
        // a tang pin on the blade for each handle
        float[] pa = {-0.62f, -0.75f}, pb = {0.62f, -0.75f};
        // two channel handles, side by side when open
        for (int side = 0; side < 2; side++) {
            float x = side == 0 ? pa[0] : pb[0];
            Mesh hm = k.add(side == 0 ? 0 : 2);
            float[][] h = domeEnds(bar(straight(x, -0.15f, x, -13.2f), 34, new float[][] {
                {0, 0.56f, 0.72f}, {0.5f, 0.6f, 0.75f}, {1, 0.56f, 0.72f}}), 3, false, true);
            Shapes.sweep(hm, h, false, 20, 4.2f, 0, Shapes.HANDLE_V0, Shapes.HANDLE_V1);
            // pivot screws on both faces
            Shapes.pin(hm, x, pa[1], 0.32f, 0.78f, 12);
            Shapes.pin(hm, x, -12.3f, 0.26f, 0.78f, 10);
        }
        // the latch on the held handle's end
        Mesh latch = k.add(0);
        float[][] lb = domeEnds(bar(straight(pa[0] - 0.1f, -12.9f, pb[0] + 0.25f, -12.9f), 8, new float[][] {
            {0, 0.18f, 0.35f}, {1, 0.18f, 0.35f}}), 2, true, true);
        Shapes.sweep(latch, lb, false, 10, 3f, 0, Shapes.METAL_V0, Shapes.METAL_V1);
        k.pinA = pa;
        k.pinB = pb;
        k.grip = new float[] {0, -6.6f};
        k.spin = new float[] {0, -6.0f};
        return k.done();
    }

    static Knife nomad() {
        Knife k = new Knife("Nomad");
        Shapes.blade(k.add(0), straight(0, 0, 0, 11.0f), new float[][] {
            {0.00f, -1.20f, 1.30f, 0.30f, 0},
            {0.10f, -1.22f, 1.48f, 0.30f, 0},
            {0.50f, -1.25f, 1.60f, 0.29f, 0},
            {0.72f, -1.12f, 1.42f, 0.26f, 0.1f},
            {0.88f, -0.62f, 0.98f, 0.19f, 0.5f},
            {0.97f, 0.05f, 0.42f, 0.08f, 0.8f},
            {1.00f, 0.25f, 0.25f, 0.00f, 1},
        }, 0.36f, 0.10f, 60, null);
        float[][] bol = domeEnds(bar(straight(-1.35f, -0.3f, 1.55f, -0.3f), 12, new float[][] {
            {0, 0.34f, 0.55f}, {1, 0.34f, 0.55f}}), 3, true, true);
        Shapes.sweep(k.add(0), bol, false, 14, 3f, 0, Shapes.METAL_V0, Shapes.METAL_V1);
        float[][] h = domeEnds(bar(line(0, -0.55f, 0, -4.2f, 0.2f, -8.4f, 0.85f, -11.2f), 34, new float[][] {
            {0, 1.25f, 0.95f}, {0.15f, 1.15f, 0.95f}, {0.55f, 1.32f, 1.0f}, {0.9f, 1.55f, 1.05f}, {1, 1.5f, 1.0f}}),
            4, false, true);
        Shapes.sweep(k.add(0), h, false, 24, 3.4f, 0, Shapes.HANDLE_V0, Shapes.HANDLE_V1);
        Shapes.ring(k.add(0), 1.25f, -11.85f, 0.42f, 0.13f, 18, 8, Shapes.METAL_V0, Shapes.METAL_V1);
        for (float y : new float[] {-2.4f, -8.2f}) {
            Shapes.pin(k.add(0), 0.05f, y, 0.22f, 1.0f, 10);
        }
        k.grip = new float[] {0.15f, -5.4f};
        k.spin = new float[] {0.1f, -4.6f};
        return k.done();
    }

    static Knife skeleton() {
        Knife k = new Knife("Skeleton knife");
        Shapes.blade(k.add(0), straight(0, 0, 0, 10.0f), new float[][] {
            {0.00f, -1.05f, 1.15f, 0.27f, 0},
            {0.45f, -1.08f, 1.32f, 0.26f, 0},
            {0.72f, -0.98f, 1.15f, 0.23f, 0.2f},
            {0.88f, -0.55f, 0.78f, 0.16f, 0.6f},
            {0.97f, 0.00f, 0.32f, 0.07f, 0.9f},
            {1.00f, 0.16f, 0.16f, 0.00f, 1},
        }, 0.40f, 0.10f, 56, null);
        // the open frame handle: a loop of round bar
        int n = 64;
        float[][] loop = new float[n][4];
        float top = -0.6f, bottom = -10.6f, half = 1.15f, r = 1.15f;
        // a stadium: two straight sides joined by half circles at both ends
        float straightLen = (top - bottom) - 2 * r;
        float per = 2 * straightLen + 2 * (float) Math.PI * r;
        for (int i = 0; i < n; i++) {
            float d = per * i / n;
            float x, y;
            if (d < straightLen) {
                x = half;
                y = top - r - d;
            } else if (d < straightLen + Math.PI * r) {
                double a = (d - straightLen) / r;
                x = (float) Math.cos(a) * half;
                y = bottom + r - (float) Math.sin(a) * r;
            } else if (d < 2 * straightLen + Math.PI * r) {
                x = -half;
                y = bottom + r + (d - straightLen - (float) Math.PI * r);
            } else {
                double a = (d - 2 * straightLen - Math.PI * r) / r;
                x = -(float) Math.cos(a) * half;
                y = top - r + (float) Math.sin(a) * r;
            }
            loop[i][0] = x;
            loop[i][1] = y;
            loop[i][2] = 0.36f;
            loop[i][3] = 0.36f;
        }
        Shapes.sweep(k.add(0), loop, true, 12, 2.2f, 0, Shapes.METAL_V0, Shapes.METAL_V1);
        // cord wrapped round the frame's sides
        Mesh cord = k.add(0);
        for (int i = 0; i < 9; i++) {
            float y = -3.0f - i * 0.62f;
            for (float x : new float[] {-half, half}) {
                float[][] wrap = new float[12][4];
                for (int j = 0; j < 12; j++) {
                    double a = 2 * Math.PI * j / 12;
                    wrap[j][0] = x + (float) Math.cos(a) * 0.5f;
                    wrap[j][1] = y + (float) Math.sin(a) * 0.12f;
                    wrap[j][2] = 0.15f;
                    wrap[j][3] = 0.5f;
                }
                Shapes.sweep(cord, wrap, true, 6, 2, 0, Shapes.HANDLE_V0, Shapes.HANDLE_V1);
            }
        }
        k.grip = new float[] {0, -5.4f};
        k.spin = new float[] {0, -4.6f};
        return k.done();
    }

    static Knife pushDagger() {
        Knife k = new Knife("Push dagger");
        Shapes.blade(k.add(0), straight(0, 0, 0, 8.2f), new float[][] {
            {0.00f, -1.25f, 1.25f, 0.32f, 1},
            {0.55f, -1.18f, 1.18f, 0.30f, 1},
            {0.85f, -0.62f, 0.62f, 0.2f, 1},
            {0.97f, -0.12f, 0.12f, 0.06f, 1},
            {1.00f, 0.00f, 0.00f, 0.00f, 1},
        }, 0.5f, 0.5f, 40, null);
        float[][] shank = bar(straight(0, -1.6f, 0, 0.2f), 6, new float[][] {{0, 0.5f, 0.3f}, {1, 0.42f, 0.28f}});
        Shapes.sweep(k.add(0), shank, false, 12, 3f, 0, Shapes.METAL_V0, Shapes.METAL_V1);
        // the T handle, across the palm
        float[][] t = domeEnds(bar(line(-4.4f, -2.75f, -1.6f, -2.05f, 1.6f, -2.05f, 4.4f, -2.75f), 30, new float[][] {
            {0, 1.0f, 1.2f}, {0.5f, 1.12f, 1.3f}, {1, 1.0f, 1.2f}}), 4, true, true);
        Shapes.sweep(k.add(0), t, false, 22, 3.2f, 0, Shapes.HANDLE_V0, Shapes.HANDLE_V1);
        k.hold = PUSH;
        k.grip = new float[] {0, -2.3f};
        k.spin = new float[] {0, -2.3f};
        return k.done();
    }
}
