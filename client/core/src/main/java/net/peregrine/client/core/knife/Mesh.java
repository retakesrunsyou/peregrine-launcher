package net.peregrine.client.core.knife;

/**
 * A small triangle/quad mesh: every vertex has a position, a normal and a texture position;
 * faces are quads (a triangle repeats its last corner), which is what Minecraft draws.
 */
public final class Mesh {

    public static final int STRIDE = 8;  // x y z nx ny nz u v

    public float[] v = new float[STRIDE * 256];
    public int verts;
    public int[] q = new int[4 * 256];
    public int quads;

    public int vertex(float x, float y, float z, float nx, float ny, float nz, float u, float vv) {
        if ((verts + 1) * STRIDE > v.length) {
            v = java.util.Arrays.copyOf(v, v.length * 2);
        }
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-8f) {
            nx = 0;
            ny = 1;
            nz = 0;
            len = 1;
        }
        int o = verts * STRIDE;
        v[o] = x;
        v[o + 1] = y;
        v[o + 2] = z;
        v[o + 3] = nx / len;
        v[o + 4] = ny / len;
        v[o + 5] = nz / len;
        v[o + 6] = u;
        v[o + 7] = vv;
        return verts++;
    }

    public void quad(int a, int b, int c, int d) {
        if ((quads + 1) * 4 > q.length) {
            q = java.util.Arrays.copyOf(q, q.length * 2);
        }
        int o = quads * 4;
        q[o] = a;
        q[o + 1] = b;
        q[o + 2] = c;
        q[o + 3] = d;
        quads++;
    }

    public void tri(int a, int b, int c) {
        quad(a, b, c, c);
    }

    /** Scales and moves every vertex (normals are unchanged by a uniform scale). */
    public void transform(float scale, float dx, float dy, float dz) {
        for (int i = 0; i < verts; i++) {
            int o = i * STRIDE;
            v[o] = v[o] * scale + dx;
            v[o + 1] = v[o + 1] * scale + dy;
            v[o + 2] = v[o + 2] * scale + dz;
        }
    }

    /** Lowest and highest corner: {minX, minY, minZ, maxX, maxY, maxZ}. */
    public float[] bounds() {
        float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int i = 0; i < verts; i++) {
            for (int k = 0; k < 3; k++) {
                float c = v[i * STRIDE + k];
                b[k] = Math.min(b[k], c);
                b[k + 3] = Math.max(b[k + 3], c);
            }
        }
        return b;
    }
}
