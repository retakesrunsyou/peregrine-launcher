package net.peregrine.client.core.knife;

/**
 * Builds the pieces knives are made of, in centimetres, in the knife's plane (x across, y along,
 * z through the thickness):
 *  - blade(): a ground blade swept along a curve: flat sides, a bevel to a sharp edge, an
 *    optional false edge (swedge) on the spine near the tip, saw teeth on the spine;
 *  - sweep(): a rounded bar (handles, guards, rings) along a path, with a superellipse
 *    cross-section that can grow and shrink along the way.
 * Texture positions: the blade uses the top of the skin picture (v 0..0.625), handles the band
 * below it, metal fittings the bottom strip.
 */
public final class Shapes {

    public static final float BLADE_V0 = 0f, BLADE_V1 = 0.625f;
    public static final float HANDLE_V0 = 0.625f, HANDLE_V1 = 0.875f;
    public static final float METAL_V0 = 0.875f, METAL_V1 = 1f;

    private Shapes() {
    }

    // ------------------------------------------------------------------ curves

    /** A point on a cubic Bézier given as {x0,y0, x1,y1, x2,y2, x3,y3}. */
    static float[] bez(float[] b, float t) {
        float u = 1 - t;
        float w0 = u * u * u, w1 = 3 * u * u * t, w2 = 3 * u * t * t, w3 = t * t * t;
        return new float[] {w0 * b[0] + w1 * b[2] + w2 * b[4] + w3 * b[6], w0 * b[1] + w1 * b[3] + w2 * b[5] + w3 * b[7]};
    }

    /** n points along a Bézier, each followed by the given extra columns (filled by the caller). */
    static float[][] path(float[] b, int n, int cols) {
        float[][] out = new float[n][cols];
        for (int i = 0; i < n; i++) {
            float[] p = bez(b, i / (float) (n - 1));
            out[i][0] = p[0];
            out[i][1] = p[1];
        }
        return out;
    }

    /**
     * A smooth curve through keys (rows of {s, value...}) at s, column col: monotone cubic, so it
     * never overshoots between keys (a blade's outline stays where it's drawn).
     */
    static float key(float[][] k, int col, float s) {
        int n = k.length;
        if (s <= k[0][0]) {
            return k[0][col];
        }
        if (s >= k[n - 1][0]) {
            return k[n - 1][col];
        }
        int i = 0;
        while (i < n - 2 && s >= k[i + 1][0]) {
            i++;
        }
        float s0 = k[i][0], s1 = k[i + 1][0];
        float y0 = k[i][col], y1 = k[i + 1][col];
        float h = s1 - s0;
        float d = (y1 - y0) / h;
        float m0 = tangent(k, col, i), m1 = tangent(k, col, i + 1);
        // keep it monotone on this piece
        if (d == 0) {
            m0 = 0;
            m1 = 0;
        } else {
            if (Math.signum(m0) != Math.signum(d)) {
                m0 = 0;
            }
            if (Math.signum(m1) != Math.signum(d)) {
                m1 = 0;
            }
            float a = m0 / d, bb = m1 / d;
            float r = a * a + bb * bb;
            if (r > 9) {
                float tau = 3 / (float) Math.sqrt(r);
                m0 = tau * a * d;
                m1 = tau * bb * d;
            }
        }
        float t = (s - s0) / h;
        float t2 = t * t, t3 = t2 * t;
        return (2 * t3 - 3 * t2 + 1) * y0 + (t3 - 2 * t2 + t) * h * m0 + (-2 * t3 + 3 * t2) * y1 + (t3 - t2) * h * m1;
    }

    private static float tangent(float[][] k, int col, int i) {
        int n = k.length;
        if (i == 0) {
            return (k[1][col] - k[0][col]) / (k[1][0] - k[0][0]);
        }
        if (i == n - 1) {
            return (k[n - 1][col] - k[n - 2][col]) / (k[n - 1][0] - k[n - 2][0]);
        }
        float d0 = (k[i][col] - k[i - 1][col]) / (k[i][0] - k[i - 1][0]);
        float d1 = (k[i + 1][col] - k[i][col]) / (k[i + 1][0] - k[i][0]);
        if (d0 * d1 <= 0) {
            return 0;
        }
        return (d0 + d1) / 2;
    }

    // ------------------------------------------------------------------ blade

    /**
     * A blade along the Bézier centre line {@code line}. keys rows: {s, spineOffset,
     * edgeOffset, halfThickness, swedge} (offsets along the line's left normal; swedge 0 = a
     * flat spine, 1 = the spine ground to an edge too). grind: how far across (0 spine, 1 edge)
     * the flat ends and the edge bevel starts; spineBevel: width of the spine's own bevel.
     * teeth: null or {from s, to s, count, depth} for a saw back.
     */
    public static void blade(Mesh m, float[] line, float[][] keys, float grind, float spineBevel, int n, float[] teeth) {
        // the cross-section: spine+, spine bevel+, grind line+, edge, grind line-, spine bevel-, spine-
        float[] f = {0, spineBevel, grind, 1, grind, spineBevel, 0};
        int[] side = {1, 1, 1, 0, -1, -1, -1};
        float[][][] pts = new float[n][7][3];
        float[][] centre = new float[n][3];
        float[] ss = new float[n];
        float len = 0;
        float[] prev = null;
        float[] arc = new float[n];
        for (int i = 0; i < n; i++) {
            float s = i / (float) (n - 1);
            ss[i] = s;
            float[] c = bez(line, s);
            float[] c2 = bez(line, Math.min(1, s + 0.002f));
            float[] c1 = bez(line, Math.max(0, s - 0.002f));
            float tx = c2[0] - c1[0], ty = c2[1] - c1[1];
            float tl = (float) Math.sqrt(tx * tx + ty * ty);
            tx /= tl;
            ty /= tl;
            float nx = -ty, ny = tx;
            float sp = key(keys, 1, s), ed = key(keys, 2, s), t = Math.max(0, key(keys, 3, s));
            float sw = Math.max(0, Math.min(1, key(keys, 4, s)));
            if (teeth != null && s > teeth[0] && s < teeth[1]) {
                float ph = (s - teeth[0]) / (teeth[1] - teeth[0]) * teeth[2];
                float saw = ph - (float) Math.floor(ph);           // 0..1 ramp per tooth
                float shape = saw < 0.7f ? saw / 0.7f : (1 - saw) / 0.3f;
                sp += -Math.signum(ed - sp) * teeth[3] * shape;      // teeth stand out from the spine
            }
            float h0 = t * (1 - sw);
            float[] hs = {h0, t, t, 0, t, t, h0};
            for (int j = 0; j < 7; j++) {
                float o = sp + (ed - sp) * f[j];
                pts[i][j][0] = c[0] + nx * o;
                pts[i][j][1] = c[1] + ny * o;
                pts[i][j][2] = side[j] * hs[j];
            }
            float mo = (sp + ed) / 2;
            centre[i][0] = c[0] + nx * mo;
            centre[i][1] = c[1] + ny * mo;
            if (prev != null) {
                len += (float) Math.hypot(c[0] - prev[0], c[1] - prev[1]);
            }
            arc[i] = len;
            prev = c;
        }
        int[][] segs = {{0, 1}, {1, 2}, {2, 3}, {3, 4}, {4, 5}, {5, 6}, {6, 0}};
        for (int[] sg : segs) {
            int a = sg[0], b = sg[1];
            int[] va = new int[n], vb = new int[n];
            float[] last = {0, 0, 1};
            for (int i = 0; i < n; i++) {
                float[] pa = pts[i][a], pb = pts[i][b];
                int i0 = Math.max(0, i - 1), i1 = Math.min(n - 1, i + 1);
                float[] ma0 = mid(pts[i0][a], pts[i0][b]), ma1 = mid(pts[i1][a], pts[i1][b]);
                float[] along = sub(ma1, ma0);
                float[] across = sub(pb, pa);
                float[] nn = cross(across, along);
                float nl = len(nn);
                if (nl < 1e-7f || len(across) < 1e-5f) {
                    nn = last;
                } else {
                    nn = scale(nn, 1 / nl);
                    float[] out = sub(mid(pa, pb), centre[i]);
                    if (dot(nn, out) < 0) {
                        nn = scale(nn, -1);
                    }
                    last = nn;
                }
                float u = len > 0 ? arc[i] / len : ss[i];
                va[i] = m.vertex(pa[0], pa[1], pa[2], nn[0], nn[1], nn[2], u, BLADE_V0 + (BLADE_V1 - BLADE_V0) * f[a]);
                vb[i] = m.vertex(pb[0], pb[1], pb[2], nn[0], nn[1], nn[2], u, BLADE_V0 + (BLADE_V1 - BLADE_V0) * f[b]);
            }
            for (int i = 0; i < n - 1; i++) {
                oriented(m, va[i], vb[i], vb[i + 1], va[i + 1]);
            }
        }
        // close the end at the hilt
        float[] p0 = pts[0][0];
        float[] p3 = pts[0][3];
        float[] dir = sub(mid(pts[1][0], pts[1][3]), mid(p0, p3));
        float[] nn = scale(dir, -1 / Math.max(1e-6f, len(dir)));
        int cc = m.vertex(centre[0][0], centre[0][1], 0, nn[0], nn[1], nn[2], 0, BLADE_V1 * 0.5f);
        int[] ring = new int[7];
        for (int j = 0; j < 7; j++) {
            ring[j] = m.vertex(pts[0][j][0], pts[0][j][1], pts[0][j][2], nn[0], nn[1], nn[2], 0, BLADE_V1 * f[j]);
        }
        for (int j = 0; j < 7; j++) {
            orientedTri(m, cc, ring[j], ring[(j + 1) % 7]);
        }
    }

    // ------------------------------------------------------------------ bars

    /**
     * A bar along path rows {x, y, a, b} (a: half size in the path's plane, b: half size through
     * it), cross-section a superellipse with exponent p (2 = round, 4 = rounded square).
     * closed: the path loops (rings). Ends with a size are closed with flat caps.
     */
    public static void sweep(Mesh m, float[][] path, boolean closed, int around, float p, float zc, float v0, float v1) {
        int n = path.length;
        int[][] ring = new int[n][around + 1];
        float total = 0;
        float[] arc = new float[n];
        for (int i = 1; i < n; i++) {
            total += (float) Math.hypot(path[i][0] - path[i - 1][0], path[i][1] - path[i - 1][1]);
            arc[i] = total;
        }
        if (closed) {
            total += (float) Math.hypot(path[0][0] - path[n - 1][0], path[0][1] - path[n - 1][1]);
        }
        float e = 2f / p;
        for (int i = 0; i < n; i++) {
            int ip = closed ? (i - 1 + n) % n : Math.max(0, i - 1);
            int in = closed ? (i + 1) % n : Math.min(n - 1, i + 1);
            float tx = path[in][0] - path[ip][0], ty = path[in][1] - path[ip][1];
            float tl = (float) Math.sqrt(tx * tx + ty * ty);
            tx /= tl;
            ty /= tl;
            float nx = -ty, ny = tx;
            float a = path[i][2], b = path[i][3];
            // how fast the bar grows here: tilts the normals toward the narrow end
            float da = (path[in][2] - path[ip][2]) / Math.max(1e-4f, distance(path[in], path[ip]));
            float db = (path[in][3] - path[ip][3]) / Math.max(1e-4f, distance(path[in], path[ip]));
            for (int j = 0; j <= around; j++) {
                double th = 2 * Math.PI * j / around;
                float c = (float) Math.cos(th), s = (float) Math.sin(th);
                float ex = Math.signum(c) * (float) Math.pow(Math.abs(c), e);
                float ez = Math.signum(s) * (float) Math.pow(Math.abs(s), e);
                float x = path[i][0] + nx * ex * a, y = path[i][1] + ny * ex * a, z = zc + ez * b;
                float lx = Math.signum(c) * (float) Math.pow(Math.abs(c), 2 - e) / Math.max(a, 1e-3f);
                float lz = Math.signum(s) * (float) Math.pow(Math.abs(s), 2 - e) / Math.max(b, 1e-3f);
                float ll = (float) Math.sqrt(lx * lx + lz * lz);
                lx /= ll;
                lz /= ll;
                float slope = -(lx * da + lz * db);   // a growing bar's surface leans backwards
                float wx = nx * lx + tx * slope, wy = ny * lx + ty * slope, wz = lz;
                float u = total > 0 ? arc[i] / total : 0;
                ring[i][j] = m.vertex(x, y, z, wx, wy, wz, u, v0 + (v1 - v0) * j / around);
            }
        }
        int last = closed ? n : n - 1;
        for (int i = 0; i < last; i++) {
            int i2 = (i + 1) % n;
            for (int j = 0; j < around; j++) {
                oriented(m, ring[i][j], ring[i][j + 1], ring[i2][j + 1], ring[i2][j]);
            }
        }
        if (!closed) {
            cap(m, path, 0, 1, around, p, zc, v0, v1);
            cap(m, path, n - 1, n - 2, around, p, zc, v0, v1);
        }
    }

    private static void cap(Mesh m, float[][] path, int i, int other, int around, float p, float zc, float v0, float v1) {
        float a = path[i][2], b = path[i][3];
        if (a < 1e-3f || b < 1e-3f) {
            return;
        }
        float tx = path[i][0] - path[other][0], ty = path[i][1] - path[other][1];
        float tl = (float) Math.sqrt(tx * tx + ty * ty);
        tx /= tl;
        ty /= tl;
        float nx = -ty, ny = tx;
        float e = 2f / p;
        int c = m.vertex(path[i][0], path[i][1], zc, tx, ty, 0, path[i] == path[0] ? 0 : 1, (v0 + v1) / 2);
        int[] r = new int[around];
        for (int j = 0; j < around; j++) {
            double th = 2 * Math.PI * j / around;
            float cs = (float) Math.cos(th), sn = (float) Math.sin(th);
            float ex = Math.signum(cs) * (float) Math.pow(Math.abs(cs), e);
            float ez = Math.signum(sn) * (float) Math.pow(Math.abs(sn), e);
            r[j] = m.vertex(path[i][0] + nx * ex * a, path[i][1] + ny * ex * a, zc + ez * b, tx, ty, 0,
                    path[i] == path[0] ? 0 : 1, v0 + (v1 - v0) * j / around);
        }
        for (int j = 0; j < around; j++) {
            orientedTri(m, c, r[j], r[(j + 1) % around]);
        }
    }

    /** A ring (in the knife's plane) of centre radius R and bar radius r. */
    public static void ring(Mesh m, float cx, float cy, float R, float r, int segs, int around, float v0, float v1) {
        float[][] path = new float[segs][4];
        for (int i = 0; i < segs; i++) {
            double th = 2 * Math.PI * i / segs;
            path[i][0] = cx + R * (float) Math.cos(th);
            path[i][1] = cy + R * (float) Math.sin(th);
            path[i][2] = r;
            path[i][3] = r;
        }
        sweep(m, path, true, around, 2, 0, v0, v1);
    }

    /** A short round pin through the knife's thickness (z), like a rivet or pivot. */
    public static void pin(Mesh m, float cx, float cy, float r, float half, int around) {
        int[] top = new int[around + 1], bot = new int[around + 1], st = new int[around + 1], sb = new int[around + 1];
        for (int j = 0; j <= around; j++) {
            double th = 2 * Math.PI * j / around;
            float c = (float) Math.cos(th), s = (float) Math.sin(th);
            float x = cx + r * c, y = cy + r * s;
            float u = j / (float) around;
            top[j] = m.vertex(x, y, half, 0, 0, 1, u, METAL_V0 + 0.02f);
            bot[j] = m.vertex(x, y, -half, 0, 0, -1, u, METAL_V0 + 0.02f);
            st[j] = m.vertex(x, y, half, c, s, 0, u, METAL_V0 + 0.04f);
            sb[j] = m.vertex(x, y, -half, c, s, 0, u, METAL_V1 - 0.02f);
        }
        int ct = m.vertex(cx, cy, half, 0, 0, 1, 0.5f, METAL_V0 + 0.02f);
        int cb = m.vertex(cx, cy, -half, 0, 0, -1, 0.5f, METAL_V0 + 0.02f);
        for (int j = 0; j < around; j++) {
            orientedTri(m, ct, top[j], top[j + 1]);
            orientedTri(m, cb, bot[j + 1], bot[j]);
            oriented(m, st[j], sb[j], sb[j + 1], st[j + 1]);
        }
    }

    // ------------------------------------------------------------------ winding

    /** Adds a quad whose corners turn so its face points the way its vertex normals do. */
    static void oriented(Mesh m, int a, int b, int c, int d) {
        float[] pa = p(m, a), pb = p(m, b), pd = p(m, d), pc = p(m, c);
        float[] fn = cross(sub(pc, pa), sub(pd, pb));
        float[] vn = {m.v[a * 8 + 3] + m.v[c * 8 + 3], m.v[a * 8 + 4] + m.v[c * 8 + 4], m.v[a * 8 + 5] + m.v[c * 8 + 5]};
        if (dot(fn, vn) < 0) {
            m.quad(a, d, c, b);
        } else {
            m.quad(a, b, c, d);
        }
    }

    static void orientedTri(Mesh m, int a, int b, int c) {
        float[] pa = p(m, a), pb = p(m, b), pc = p(m, c);
        float[] fn = cross(sub(pb, pa), sub(pc, pa));
        float[] vn = {m.v[a * 8 + 3], m.v[a * 8 + 4], m.v[a * 8 + 5]};
        if (dot(fn, vn) < 0) {
            m.tri(a, c, b);
        } else {
            m.tri(a, b, c);
        }
    }

    // ------------------------------------------------------------------ tiny vector helpers

    private static float[] p(Mesh m, int i) {
        return new float[] {m.v[i * 8], m.v[i * 8 + 1], m.v[i * 8 + 2]};
    }

    private static float distance(float[] a, float[] b) {
        return (float) Math.hypot(a[0] - b[0], a[1] - b[1]);
    }

    static float[] sub(float[] a, float[] b) {
        return new float[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    static float[] mid(float[] a, float[] b) {
        return new float[] {(a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2};
    }

    static float[] cross(float[] a, float[] b) {
        return new float[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    static float len(float[] a) {
        return (float) Math.sqrt(dot(a, a));
    }

    static float[] scale(float[] a, float k) {
        return new float[] {a[0] * k, a[1] * k, a[2] * k};
    }
}
