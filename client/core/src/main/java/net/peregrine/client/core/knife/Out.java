package net.peregrine.client.core.knife;

import java.util.ArrayList;
import java.util.List;

/**
 * What to draw this frame for the first-person knife and arm: quads in view space, grouped into
 * batches by how they're drawn and with which picture. Reused every frame (no garbage).
 */
public final class Out {

    /** Lit, see-through when its colour's alpha says so (arm and knife). */
    public static final int LIT = 0;
    /** Added on top as light (polished reflections, glow, trails): no lighting, not hidden by fog. */
    public static final int ADD = 1;

    public static final class Batch {
        public int layer;
        public String texture;
        public int start, count;   // in vertices (4 per quad)
    }

    public float[] v = new float[8 * 4096];   // x y z nx ny nz u v
    public int[] c = new int[4096];           // ARGB per vertex
    public int verts;
    public final List<Batch> batches = new ArrayList<Batch>();
    private final List<Batch> pool = new ArrayList<Batch>();
    private Batch open;

    public void clear() {
        verts = 0;
        pool.addAll(batches);
        batches.clear();
        open = null;
    }

    public void begin(int layer, String texture) {
        if (open != null && open.layer == layer && open.texture.equals(texture)) {
            return;
        }
        Batch b = pool.isEmpty() ? new Batch() : pool.remove(pool.size() - 1);
        b.layer = layer;
        b.texture = texture;
        b.start = verts;
        b.count = 0;
        batches.add(b);
        open = b;
    }

    public void vertex(float x, float y, float z, float nx, float ny, float nz, float u, float vv, int argb) {
        if (verts + 1 > c.length) {
            v = java.util.Arrays.copyOf(v, v.length * 2);
            c = java.util.Arrays.copyOf(c, c.length * 2);
        }
        int o = verts * 8;
        v[o] = x;
        v[o + 1] = y;
        v[o + 2] = z;
        v[o + 3] = nx;
        v[o + 4] = ny;
        v[o + 5] = nz;
        v[o + 6] = u;
        v[o + 7] = vv;
        c[verts] = argb;
        verts++;
        open.count++;
    }

    public int quads() {
        return verts / 4;
    }
}
