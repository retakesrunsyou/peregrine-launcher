package net.peregrine.client.core.skeleton;

/**
 * Thin bones built directly as tubes (ribs, fingers, toes, collarbones): a smooth path with a
 * radius at every point, an oval cross-section that stays upright, and rounded ends.
 */
public final class Tube {

    private Tube() {
    }

    /**
     * pts: {x, y, z, r} along the bone; cols: a colour per point (or null for one colour);
     * flat: the second radius as a share of the first (1 round, under 1 a flattened bone);
     * up: which way the first radius points (kept square to the path).
     */
    public static void build(Geo g, float[][] pts, int[] cols, int col, int around, float flat, float[] up, int smooth) {
        float[][] p = smooth > 0 ? subdivide(pts, smooth) : pts;
        int[] cl = cols == null ? null : spread(cols, pts.length, p.length);
        int n = p.length;
        float len = 0;
        float[] along = new float[n];
        for (int i = 1; i < n; i++) {
            len += dist(p[i - 1], p[i]);
            along[i] = len;
        }
        int[][] ring = new int[n][around];
        float[] t = new float[3], a = new float[3], b = new float[3];
        for (int i = 0; i < n; i++) {
            tangent(p, i, t);
            // the first radius points as near "up" as the path allows
            float k = up[0] * t[0] + up[1] * t[1] + up[2] * t[2];
            a[0] = up[0] - k * t[0];
            a[1] = up[1] - k * t[1];
            a[2] = up[2] - k * t[2];
            normalize(a);
            b[0] = t[1] * a[2] - t[2] * a[1];
            b[1] = t[2] * a[0] - t[0] * a[2];
            b[2] = t[0] * a[1] - t[1] * a[0];
            float r = p[i][3];
            int c = cl == null ? col : cl[i];
            for (int j = 0; j < around; j++) {
                double ang = 2 * Math.PI * j / around;
                float ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
                float nx = a[0] * ca + b[0] * sa * flat, ny = a[1] * ca + b[1] * sa * flat, nz = a[2] * ca + b[2] * sa * flat;
                // normal of an oval: scale the other way
                float mx = a[0] * ca * flat + b[0] * sa, my = a[1] * ca * flat + b[1] * sa, mz = a[2] * ca * flat + b[2] * sa;
                ring[i][j] = g.vertex(p[i][0] + nx * r, p[i][1] + ny * r, p[i][2] + nz * r, mx, my, mz,
                        0.05f + 0.9f * j / around, 0.05f + 0.9f * along[i] / Math.max(1e-6f, len), c);
            }
        }
        for (int i = 0; i + 1 < n; i++) {
            for (int j = 0; j < around; j++) {
                int j2 = (j + 1) % around;
                g.quad(ring[i][j], ring[i][j2], ring[i + 1][j2], ring[i + 1][j]);
            }
        }
        cap(g, p, ring, 0, -1, around, cl == null ? col : cl[0]);
        cap(g, p, ring, n - 1, 1, around, cl == null ? col : cl[n - 1]);
    }

    /** A rounded end: the last ring closed with a little dome. */
    private static void cap(Geo g, float[][] p, int[][] ring, int i, int dir, int around, int col) {
        float[] t = new float[3];
        tangent(p, i, t);
        float r = p[i][3];
        float tx = p[i][0] + t[0] * dir * r * 0.75f, ty = p[i][1] + t[1] * dir * r * 0.75f, tz = p[i][2] + t[2] * dir * r * 0.75f;
        int tip = g.vertex(tx, ty, tz, t[0] * dir, t[1] * dir, t[2] * dir, 0.5f, dir < 0 ? 0.02f : 0.98f, col);
        for (int j = 0; j < around; j++) {
            int j2 = (j + 1) % around;
            if (dir > 0) {
                g.tri(ring[i][j], ring[i][j2], tip);
            } else {
                g.tri(ring[i][j2], ring[i][j], tip);
            }
        }
    }

    /** Smooths a path: Catmull-Rom points between the given ones (radius too). */
    static float[][] subdivide(float[][] p, int per) {
        if (p.length < 3) {
            per = Math.max(1, per);
        }
        int n = (p.length - 1) * per + 1;
        float[][] out = new float[n][4];
        for (int s = 0; s < p.length - 1; s++) {
            float[] p0 = p[Math.max(0, s - 1)], p1 = p[s], p2 = p[s + 1], p3 = p[Math.min(p.length - 1, s + 2)];
            for (int k = 0; k < per; k++) {
                float u = k / (float) per;
                for (int c = 0; c < 4; c++) {
                    out[s * per + k][c] = catmull(p0[c], p1[c], p2[c], p3[c], u);
                }
            }
        }
        out[n - 1] = p[p.length - 1].clone();
        return out;
    }

    private static int[] spread(int[] cols, int from, int to) {
        int[] out = new int[to];
        for (int i = 0; i < to; i++) {
            float f = i * (from - 1) / (float) Math.max(1, to - 1);
            int a = (int) Math.floor(f), b = Math.min(from - 1, a + 1);
            out[i] = lerp(cols[a], cols[b], f - a);
        }
        return out;
    }

    static int lerp(int a, int b, float t) {
        int r = 0;
        for (int s = 0; s <= 24; s += 8) {
            int ca = (a >>> s) & 255, cb = (b >>> s) & 255;
            r |= Math.round(ca + (cb - ca) * t) << s;
        }
        return r;
    }

    private static float catmull(float p0, float p1, float p2, float p3, float t) {
        return 0.5f * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t + (-p0 + 3 * p1 - 3 * p2 + p3) * t * t * t);
    }

    private static void tangent(float[][] p, int i, float[] t) {
        float[] a = p[Math.max(0, i - 1)], b = p[Math.min(p.length - 1, i + 1)];
        t[0] = b[0] - a[0];
        t[1] = b[1] - a[1];
        t[2] = b[2] - a[2];
        normalize(t);
    }

    private static void normalize(float[] v) {
        float l = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (l < 1e-9f) {
            v[0] = 1;
            return;
        }
        v[0] /= l;
        v[1] /= l;
        v[2] /= l;
    }

    private static float dist(float[] a, float[] b) {
        float dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
