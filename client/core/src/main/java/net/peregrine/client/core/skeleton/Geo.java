package net.peregrine.client.core.skeleton;

import java.util.Arrays;

/**
 * A mesh of quads with a colour on every vertex (bone shade, cavities darker, cartilage a
 * little grey): position, normal, texture position, ARGB. Built once, then only transformed.
 */
public final class Geo {

    public static final int STRIDE = 8;   // x y z nx ny nz u v

    public float[] v = new float[STRIDE * 512];
    public int[] c = new int[512];
    public int verts;
    public int[] q = new int[4 * 512];
    public int quads;

    public int vertex(float x, float y, float z, float nx, float ny, float nz, float u, float vv, int argb) {
        if (verts + 1 > c.length) {
            v = Arrays.copyOf(v, v.length * 2);
            c = Arrays.copyOf(c, c.length * 2);
        }
        float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1e-9f) {
            nx = 0;
            ny = -1;
            nz = 0;
            l = 1;
        }
        int o = verts * STRIDE;
        v[o] = x;
        v[o + 1] = y;
        v[o + 2] = z;
        v[o + 3] = nx / l;
        v[o + 4] = ny / l;
        v[o + 5] = nz / l;
        v[o + 6] = u;
        v[o + 7] = vv;
        c[verts] = argb;
        return verts++;
    }

    public void quad(int a, int b, int cc, int d) {
        if ((quads + 1) * 4 > q.length) {
            q = Arrays.copyOf(q, q.length * 2);
        }
        int o = quads * 4;
        q[o] = a;
        q[o + 1] = b;
        q[o + 2] = cc;
        q[o + 3] = d;
        quads++;
    }

    public void tri(int a, int b, int cc) {
        quad(a, b, cc, cc);
    }

    /** Adds another mesh's quads (copying its vertices). */
    public void add(Geo o) {
        int base = verts;
        for (int i = 0; i < o.verts; i++) {
            int k = i * STRIDE;
            vertex(o.v[k], o.v[k + 1], o.v[k + 2], o.v[k + 3], o.v[k + 4], o.v[k + 5], o.v[k + 6], o.v[k + 7], o.c[i]);
        }
        for (int i = 0; i < o.quads; i++) {
            int k = i * 4;
            quad(base + o.q[k], base + o.q[k + 1], base + o.q[k + 2], base + o.q[k + 3]);
        }
    }

    /** The same mesh flipped left to right (x to -x), its faces turned to still face outward. */
    public Geo mirrorX() {
        Geo g = new Geo();
        for (int i = 0; i < verts; i++) {
            int k = i * STRIDE;
            g.vertex(-v[k], v[k + 1], v[k + 2], -v[k + 3], v[k + 4], v[k + 5], v[k + 6], v[k + 7], c[i]);
        }
        for (int i = 0; i < quads; i++) {
            int k = i * 4;
            g.quad(q[k + 3], q[k + 2], q[k + 1], q[k]);
        }
        return g;
    }

    /** Moves every vertex. */
    public Geo shift(float dx, float dy, float dz) {
        for (int i = 0; i < verts; i++) {
            int k = i * STRIDE;
            v[k] += dx;
            v[k + 1] += dy;
            v[k + 2] += dz;
        }
        return this;
    }

    /** Trims the arrays to what's used. */
    public Geo pack() {
        v = Arrays.copyOf(v, verts * STRIDE);
        c = Arrays.copyOf(c, verts);
        q = Arrays.copyOf(q, quads * 4);
        return this;
    }
}
