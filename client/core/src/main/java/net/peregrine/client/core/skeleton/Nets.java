package net.peregrine.client.core.skeleton;

/**
 * Turns a distance field into quads ("surface nets"): one vertex inside each grid cell the
 * surface passes through, one quad across each grid edge it crosses. Smooth normals come from
 * the field itself, and every vertex gets a little ambient occlusion (darker deep in sockets,
 * between ribs, in the joints) baked into its colour.
 */
public final class Nets {

    private Nets() {
    }

    /** {cx, cy, cz} offsets of a cell's 8 corners, and its 12 edges as corner pairs. */
    private static final int[][] CORNER = {
        {0, 0, 0}, {1, 0, 0}, {0, 1, 0}, {1, 1, 0}, {0, 0, 1}, {1, 0, 1}, {0, 1, 1}, {1, 1, 1},
    };
    private static final int[][] EDGE = {
        {0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7},
    };

    /**
     * Meshes the field inside the box (min, max) with cells of the given size.
     */
    public static void mesh(Sdf.Node f, float[] min, float[] max, float cell, float aoStrength, Geo out) {
        int nx = (int) Math.ceil((max[0] - min[0]) / cell) + 3;
        int ny = (int) Math.ceil((max[1] - min[1]) / cell) + 3;
        int nz = (int) Math.ceil((max[2] - min[2]) / cell) + 3;
        float ox = min[0] - cell, oy = min[1] - cell, oz = min[2] - cell;
        float[] val = new float[nx * ny * nz];
        for (int k = 0; k < nz; k++) {
            for (int j = 0; j < ny; j++) {
                for (int i = 0; i < nx; i++) {
                    val[(k * ny + j) * nx + i] = f.d(ox + i * cell, oy + j * cell, oz + k * cell);
                }
            }
        }
        int cx = nx - 1, cy = ny - 1, cz = nz - 1;
        int[] index = new int[cx * cy * cz];
        java.util.Arrays.fill(index, -1);
        float[] g = new float[3];
        float span = Math.max(max[0] - min[0], Math.max(max[1] - min[1], max[2] - min[2])) + 2 * cell;
        for (int k = 0; k < cz; k++) {
            for (int j = 0; j < cy; j++) {
                for (int i = 0; i < cx; i++) {
                    int mask = 0;
                    float[] cv = new float[8];
                    for (int c = 0; c < 8; c++) {
                        cv[c] = val[((k + CORNER[c][2]) * ny + j + CORNER[c][1]) * nx + i + CORNER[c][0]];
                        if (cv[c] < 0) {
                            mask |= 1 << c;
                        }
                    }
                    if (mask == 0 || mask == 255) {
                        continue;
                    }
                    float sx = 0, sy = 0, sz = 0;
                    int n = 0;
                    for (int[] e : EDGE) {
                        float a = cv[e[0]], b = cv[e[1]];
                        if ((a < 0) == (b < 0)) {
                            continue;
                        }
                        float t = a / (a - b);
                        int[] p = CORNER[e[0]], q = CORNER[e[1]];
                        sx += p[0] + (q[0] - p[0]) * t;
                        sy += p[1] + (q[1] - p[1]) * t;
                        sz += p[2] + (q[2] - p[2]) * t;
                        n++;
                    }
                    float x = ox + (i + sx / n) * cell, y = oy + (j + sy / n) * cell, z = oz + (k + sz / n) * cell;
                    // pull the point on to the surface (a couple of steps along the slope), staying in its cell
                    for (int it = 0; it < 3; it++) {
                        gradient(f, x, y, z, cell * 0.25f, g);
                        float d = f.d(x, y, z);
                        float nx2 = x - g[0] * d, ny2 = y - g[1] * d, nz2 = z - g[2] * d;
                        float lim = cell * 0.75f;
                        float cx0 = ox + (i + 0.5f) * cell, cy0 = oy + (j + 0.5f) * cell, cz0 = oz + (k + 0.5f) * cell;
                        x = Math.max(cx0 - lim, Math.min(cx0 + lim, nx2));
                        y = Math.max(cy0 - lim, Math.min(cy0 + lim, ny2));
                        z = Math.max(cz0 - lim, Math.min(cz0 + lim, nz2));
                    }
                    gradient(f, x, y, z, cell * 0.25f, g);
                    float ao = occlusion(f, x, y, z, g, cell, aoStrength);
                    int col = shade(f.color(x - g[0] * cell, y - g[1] * cell, z - g[2] * cell), ao);
                    // the texture laid over the whole box from the side the surface faces (no repeats)
                    float u, v;
                    float ax = Math.abs(g[0]), ay = Math.abs(g[1]), az = Math.abs(g[2]);
                    float px = (x - min[0]) / span, py = (y - min[1]) / span, pz = (z - min[2]) / span;
                    if (ax >= ay && ax >= az) {
                        u = pz;
                        v = py;
                    } else if (ay >= az) {
                        u = px;
                        v = pz;
                    } else {
                        u = px;
                        v = py;
                    }
                    index[(k * cy + j) * cx + i] = out.vertex(x, y, z, g[0], g[1], g[2],
                            clamp(u), clamp(v), col);
                }
            }
        }
        // quads across every crossed grid edge
        for (int k = 1; k < cz; k++) {
            for (int j = 1; j < cy; j++) {
                for (int i = 1; i < cx; i++) {
                    float a = val[(k * ny + j) * nx + i];
                    boolean in = a < 0;
                    // along x
                    if (in != (val[(k * ny + j) * nx + i + 1] < 0)) {
                        face(out, index, cx, cy, in,
                                c(i, j - 1, k - 1), c(i, j, k - 1), c(i, j, k), c(i, j - 1, k));
                    }
                    // along y
                    if (in != (val[(k * ny + j + 1) * nx + i] < 0)) {
                        face(out, index, cx, cy, !in,
                                c(i - 1, j, k - 1), c(i, j, k - 1), c(i, j, k), c(i - 1, j, k));
                    }
                    // along z
                    if (in != (val[((k + 1) * ny + j) * nx + i] < 0)) {
                        face(out, index, cx, cy, in,
                                c(i - 1, j - 1, k), c(i, j - 1, k), c(i, j, k), c(i - 1, j, k));
                    }
                }
            }
        }
    }

    private static int[] c(int i, int j, int k) {
        return new int[] {i, j, k};
    }

    private static void face(Geo out, int[] index, int cx, int cy, boolean forward, int[] a, int[] b, int[] c, int[] d) {
        int ia = index[(a[2] * cy + a[1]) * cx + a[0]], ib = index[(b[2] * cy + b[1]) * cx + b[0]];
        int ic = index[(c[2] * cy + c[1]) * cx + c[0]], id = index[(d[2] * cy + d[1]) * cx + d[0]];
        if (ia < 0 || ib < 0 || ic < 0 || id < 0) {
            return;
        }
        if (forward) {
            out.quad(ia, ib, ic, id);
        } else {
            out.quad(id, ic, ib, ia);
        }
    }

    static void gradient(Sdf.Node f, float x, float y, float z, float h, float[] g) {
        g[0] = f.d(x + h, y, z) - f.d(x - h, y, z);
        g[1] = f.d(x, y + h, z) - f.d(x, y - h, z);
        g[2] = f.d(x, y, z + h) - f.d(x, y, z - h);
        float l = (float) Math.sqrt(g[0] * g[0] + g[1] * g[1] + g[2] * g[2]);
        if (l < 1e-9f) {
            g[0] = 0;
            g[1] = -1;
            g[2] = 0;
            return;
        }
        g[0] /= l;
        g[1] /= l;
        g[2] /= l;
    }

    /** Directions round a point (for looking for nearby bone). */
    private static final float[][] DIRS = new float[26][];

    static {
        int n = 0;
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x != 0 || y != 0 || z != 0) {
                        float l = (float) Math.sqrt(x * x + y * y + z * z);
                        DIRS[n++] = new float[] {x / l, y / l, z / l};
                    }
                }
            }
        }
    }

    /**
     * 1 open, toward 0 in hollows: of the points a little way out from the surface in every
     * direction it faces, how many are inside bone. (Only inside/outside is asked, so it works
     * even where the distances are rough.)
     */
    static float occlusion(Sdf.Node f, float x, float y, float z, float[] n, float cell, float strength) {
        if (strength <= 0) {
            return 1f;
        }
        int inside = 0, total = 0;
        float[] radii = {0.3f, 0.7f};
        for (float[] d : DIRS) {
            float k = d[0] * n[0] + d[1] * n[1] + d[2] * n[2];
            if (k < 0.15f) {
                continue;
            }
            for (float r : radii) {
                total++;
                if (f.d(x + (d[0] + n[0] * 0.35f) * r, y + (d[1] + n[1] * 0.35f) * r, z + (d[2] + n[2] * 0.35f) * r) < 0) {
                    inside++;
                }
            }
        }
        float occ = total == 0 ? 0 : inside / (float) total;
        return Math.max(0.25f, Math.min(1f, 1f - strength * occ * 1.5f));
    }

    static int shade(int argb, float k) {
        int r = Math.round(((argb >> 16) & 255) * k), g = Math.round(((argb >> 8) & 255) * k), b = Math.round((argb & 255) * k);
        return (argb & 0xFF000000) | r << 16 | g << 8 | b;
    }

    private static float clamp(float x) {
        return Math.max(0.002f, Math.min(0.998f, x));
    }
}
