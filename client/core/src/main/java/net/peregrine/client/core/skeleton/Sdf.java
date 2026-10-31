package net.peregrine.client.core.skeleton;

import java.util.ArrayList;
import java.util.List;

/**
 * Shapes as distance fields (negative inside): spheres, egg shapes, tapered rods, thin plates,
 * blended together smoothly like real bone grows, with holes cut out (eye sockets, the nose).
 * Turned into a mesh once with {@link Nets}.
 */
public final class Sdf {

    private Sdf() {
    }

    /** A shape: its distance at a point, and its colour there. */
    public abstract static class Node {
        int color = 0xFFFFFFFF;
        /** Bounding sphere (for skipping far shapes quickly). */
        float bx, by, bz, br = Float.MAX_VALUE;

        public abstract float d(float x, float y, float z);

        public int color(float x, float y, float z) {
            return color;
        }

        public Node tint(int argb) {
            color = argb;
            return this;
        }

        float lower(float x, float y, float z) {
            if (br == Float.MAX_VALUE) {
                return -Float.MAX_VALUE;
            }
            float dx = x - bx, dy = y - by, dz = z - bz;
            return (float) Math.sqrt(dx * dx + dy * dy + dz * dz) - br;
        }
    }

    // ------------------------------------------------------------------ shapes

    /** An egg/ellipsoid with any orientation: centre, its three axes (unit) and radii along them. */
    public static Node ellipsoid(final float cx, final float cy, final float cz, float[] ax, float[] ay, float[] az,
                                 final float rx, final float ry, final float rz) {
        final float[] a = ax, b = ay, c = az;
        Node n = new Node() {
            @Override
            public float d(float x, float y, float z) {
                float px = x - cx, py = y - cy, pz = z - cz;
                float u = (px * a[0] + py * a[1] + pz * a[2]) / rx;
                float v = (px * b[0] + py * b[1] + pz * b[2]) / ry;
                float w = (px * c[0] + py * c[1] + pz * c[2]) / rz;
                float k0 = (float) Math.sqrt(u * u + v * v + w * w);
                float k1 = (float) Math.sqrt(u * u / (rx * rx) + v * v / (ry * ry) + w * w / (rz * rz));
                return k1 < 1e-6f ? -Math.min(rx, Math.min(ry, rz)) : k0 * (k0 - 1) / k1;
            }
        };
        n.bx = cx;
        n.by = cy;
        n.bz = cz;
        n.br = Math.max(rx, Math.max(ry, rz));
        return n;
    }

    public static final float[] X = {1, 0, 0}, Y = {0, 1, 0}, Z = {0, 0, 1};

    /** An axis-aligned egg shape. */
    public static Node egg(float cx, float cy, float cz, float rx, float ry, float rz) {
        return ellipsoid(cx, cy, cz, X, Y, Z, rx, ry, rz);
    }

    public static Node ball(float cx, float cy, float cz, float r) {
        return egg(cx, cy, cz, r, r, r);
    }

    /** A rod from a to b, radius ra at a and rb at b, rounded ends. */
    public static Node rod(final float ax, final float ay, final float az, final float bx, final float by, final float bz,
                           final float ra, final float rb) {
        final float ex = bx - ax, ey = by - ay, ez = bz - az;
        final float ee = Math.max(1e-9f, ex * ex + ey * ey + ez * ez);
        Node n = new Node() {
            @Override
            public float d(float x, float y, float z) {
                float px = x - ax, py = y - ay, pz = z - az;
                float h = Math.max(0, Math.min(1, (px * ex + py * ey + pz * ez) / ee));
                float dx = px - ex * h, dy = py - ey * h, dz = pz - ez * h;
                return (float) Math.sqrt(dx * dx + dy * dy + dz * dz) - (ra + (rb - ra) * h);
            }
        };
        n.bx = (ax + bx) / 2;
        n.by = (ay + by) / 2;
        n.bz = (az + bz) / 2;
        n.br = (float) Math.sqrt(ee) / 2 + Math.max(ra, rb);
        return n;
    }

    /** A rod through several points (each {x, y, z, r}). */
    public static Node rods(float[][] p) {
        List<Node> l = new ArrayList<Node>();
        for (int i = 0; i + 1 < p.length; i++) {
            l.add(rod(p[i][0], p[i][1], p[i][2], p[i + 1][0], p[i + 1][1], p[i + 1][2], p[i][3], p[i + 1][3]));
        }
        return union(0.05f, l.toArray(new Node[0]));
    }

    /** A thin rounded plate through a triangle (thickness t each side). */
    public static Node plate(final float[] a, final float[] b, final float[] c, final float t) {
        Node n = new Node() {
            @Override
            public float d(float x, float y, float z) {
                return triangle(a, b, c, x, y, z) - t;
            }
        };
        n.bx = (a[0] + b[0] + c[0]) / 3;
        n.by = (a[1] + b[1] + c[1]) / 3;
        n.bz = (a[2] + b[2] + c[2]) / 3;
        float r = 0;
        for (float[] p : new float[][] {a, b, c}) {
            float dx = p[0] - n.bx, dy = p[1] - n.by, dz = p[2] - n.bz;
            r = Math.max(r, (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
        }
        n.br = r + t;
        return n;
    }

    /** Exact distance to a triangle in 3D. */
    static float triangle(float[] a, float[] b, float[] c, float x, float y, float z) {
        float[] ba = sub(b, a), pa = {x - a[0], y - a[1], z - a[2]};
        float[] cb = sub(c, b), pb = {x - b[0], y - b[1], z - b[2]};
        float[] ac = sub(a, c), pc = {x - c[0], y - c[1], z - c[2]};
        float[] nor = cross(ba, ac);
        boolean outside = Math.signum(dot(cross(ba, nor), pa)) + Math.signum(dot(cross(cb, nor), pb))
                + Math.signum(dot(cross(ac, nor), pc)) < 2;
        float d2;
        if (outside) {
            d2 = Math.min(Math.min(seg2(ba, pa), seg2(cb, pb)), seg2(ac, pc));
        } else {
            float k = dot(nor, pa);
            d2 = k * k / dot(nor, nor);
        }
        return (float) Math.sqrt(d2);
    }

    private static float seg2(float[] e, float[] p) {
        float h = Math.max(0, Math.min(1, dot(e, p) / dot(e, e)));
        float dx = e[0] * h - p[0], dy = e[1] * h - p[1], dz = e[2] * h - p[2];
        return dx * dx + dy * dy + dz * dz;
    }

    // ------------------------------------------------------------------ combining

    /** Shapes blended together (k: how far the blend reaches). */
    public static Node union(final float k, final Node... kids) {
        Node n = new Node() {
            @Override
            public float d(float x, float y, float z) {
                float best = Float.MAX_VALUE;
                for (Node c : kids) {
                    if (best != Float.MAX_VALUE && c.lower(x, y, z) > best + k) {
                        continue;
                    }
                    float d = c.d(x, y, z);
                    best = best == Float.MAX_VALUE ? d : smin(best, d, k);
                }
                return best;
            }

            @Override
            public int color(float x, float y, float z) {
                float best = Float.MAX_VALUE;
                int col = color;
                for (Node c : kids) {
                    if (c.lower(x, y, z) > best) {
                        continue;
                    }
                    float d = c.d(x, y, z);
                    if (d < best) {
                        best = d;
                        col = c.color(x, y, z);
                    }
                }
                return col;
            }
        };
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        boolean bounded = true;
        for (Node c : kids) {
            if (c.br == Float.MAX_VALUE) {
                bounded = false;
                break;
            }
            minX = Math.min(minX, c.bx - c.br);
            minY = Math.min(minY, c.by - c.br);
            minZ = Math.min(minZ, c.bz - c.br);
            maxX = Math.max(maxX, c.bx + c.br);
            maxY = Math.max(maxY, c.by + c.br);
            maxZ = Math.max(maxZ, c.bz + c.br);
        }
        if (bounded && kids.length > 0) {
            n.bx = (minX + maxX) / 2;
            n.by = (minY + maxY) / 2;
            n.bz = (minZ + maxZ) / 2;
            float hx = (maxX - minX) / 2, hy = (maxY - minY) / 2, hz = (maxZ - minZ) / 2;
            n.br = (float) Math.sqrt(hx * hx + hy * hy + hz * hz) + k;
        }
        return n;
    }

    public static Node union(float k, List<Node> kids) {
        return union(k, kids.toArray(new Node[0]));
    }

    /** A shape with others carved out of it (k: how soft the edge of the cut is). */
    public static Node cut(final Node base, final float k, final Node... holes) {
        Node n = new Node() {
            @Override
            public float d(float x, float y, float z) {
                float d = base.d(x, y, z);
                for (Node h : holes) {
                    if (h.lower(x, y, z) > -d + k) {
                        continue;
                    }
                    d = smax(d, -h.d(x, y, z), k);
                }
                return d;
            }

            @Override
            public int color(float x, float y, float z) {
                // the inside of a cut (deep in an eye socket) takes the cut's colour
                float d = base.d(x, y, z);
                for (Node h : holes) {
                    if (h.color != 0xFFFFFFFF && -h.d(x, y, z) > d - 0.05f) {
                        return h.color;
                    }
                }
                return base.color(x, y, z);
            }
        };
        n.bx = base.bx;
        n.by = base.by;
        n.bz = base.bz;
        n.br = base.br;
        return n;
    }

    static float smin(float a, float b, float k) {
        if (k <= 0) {
            return Math.min(a, b);
        }
        float h = Math.max(k - Math.abs(a - b), 0) / k;
        return Math.min(a, b) - h * h * k * 0.25f;
    }

    static float smax(float a, float b, float k) {
        return -smin(-a, -b, k);
    }

    // ------------------------------------------------------------------ vectors

    static float[] sub(float[] a, float[] b) {
        return new float[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    static float[] cross(float[] a, float[] b) {
        return new float[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    public static float[] norm(float x, float y, float z) {
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        return new float[] {x / l, y / l, z / l};
    }
}
