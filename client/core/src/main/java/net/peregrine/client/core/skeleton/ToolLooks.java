package net.peregrine.client.core.skeleton;

import net.peregrine.client.core.knife.M4;

/**
 * Pickaxe looks: your pickaxe drawn as something else. Both are original designs:
 * <ul>
 *   <li>Star wand: a violet lacquered wand with gold bands, a pink ribbon wound round it, and a
 *       faceted crystal star that glows, with sparkles drifting round it.</li>
 *   <li>Peppermint axe: a candy-cane handle (red and white with a thin green stripe, a little
 *       hook at the end) wrapped in mint ribbon, with a round peppermint candy for the blade,
 *       sharpened to an edge, and a mint spike on the back.</li>
 * </ul>
 * Modelled in a Minecraft item's own space (pixels 0..16, the item lying in its plane, handle
 * from the bottom left corner to the top right like a pickaxe), so Minecraft's usual hold and
 * swing apply to them unchanged.
 */
public final class ToolLooks {

    public static final int NORMAL = 0, STAR_WAND = 1, PEPPERMINT_AXE = 2;
    public static final String[] NAMES = {"Normal", "Star wand", "Peppermint axe"};
    public static final String TEXTURE = "peregrine-client:textures/item/tool_looks.png";

    /** Where in a hand: Minecraft's display contexts. */
    public static final int FIRST_RIGHT = 0, FIRST_LEFT = 1, THIRD_RIGHT = 2, THIRD_LEFT = 3;

    // texture regions (u0, v0, u1, v1)
    static final float[] SWIRL = {0f, 0f, 0.5f, 0.5f}, CANDY = {0.5f, 0f, 0.75f, 1f}, WHITE = {0.75f, 0f, 1f, 0.25f},
            MINT = {0.75f, 0.25f, 1f, 0.5f}, GOLD = {0.75f, 0.5f, 1f, 0.75f}, STAR = {0.75f, 0.75f, 1f, 1f};

    private static final Geo[] LIT = new Geo[3], GLOW = new Geo[3];

    private ToolLooks() {
    }

    static synchronized Geo lit(int look) {
        if (LIT[look] == null) {
            build(look);
        }
        return LIT[look];
    }

    static synchronized Geo glow(int look) {
        if (GLOW[look] == null) {
            build(look);
        }
        return GLOW[look];
    }

    private static void build(int look) {
        Geo lit = new Geo(), glow = new Geo();
        if (look == STAR_WAND) {
            starWand(lit, glow);
        } else if (look == PEPPERMINT_AXE) {
            peppermintAxe(lit, glow);
        }
        LIT[look] = lit.pack();
        GLOW[look] = glow.pack();
    }

    // ------------------------------------------------------------------ drawing

    /**
     * The item transform Minecraft uses for a handheld item in that context, then the model's
     * pixels to blocks: base is the pose just before Minecraft would draw the item.
     */
    public static M4 hold(int context) {
        // item/handheld: rotation, translation (pixels), scale; left hands are flipped like Minecraft does
        float rx = 0, ry, rz, tx, ty, tz, s;
        boolean first = context == FIRST_RIGHT || context == FIRST_LEFT;
        boolean left = context == FIRST_LEFT || context == THIRD_LEFT;
        if (first) {
            ry = -90;
            rz = 25;
            tx = 1.13f;
            ty = 3.2f;
            tz = 1.13f;
            s = 0.68f;
        } else {
            ry = -90;
            rz = 55;
            tx = 0;
            ty = 4f;
            tz = 0.5f;
            s = 0.85f;
        }
        if (left) {   // the left-hand values are the right's mirrored, then Minecraft mirrors them back (x only)
            tx = -tx;
        }
        return new M4().translate(tx / 16f, ty / 16f, tz / 16f).rotX(rx).rotY(ry).rotZ(rz).scale(s)
                .translate(-0.5f, -0.5f, -0.5f).scale(1 / 16f);
    }

    /** The solid part of a look, placed by m. */
    public static void drawLit(int look, M4 m, Skeleton.Sink sink) {
        if (look <= 0 || look >= NAMES.length) {
            return;
        }
        Skeleton.emit(lit(look), m, sink);
    }

    /** The glowing part (added as light): halo and, for the wand, sparkles that drift with time (seconds). */
    public static void drawGlow(int look, M4 m, double t, Skeleton.Sink sink) {
        if (look <= 0 || look >= NAMES.length) {
            return;
        }
        Skeleton.emit(glow(look), m, sink);
        if (look == STAR_WAND) {
            sparkles(m, t, sink);
        }
    }

    private static final float SX = 12.7f, SY = 12.7f, SZ = 8f;   // the star's middle

    private static void sparkles(M4 m, double t, Skeleton.Sink sink) {
        float[] p = new float[3], n = new float[3];
        m.dir(0, 0, 1, n);
        for (int i = 0; i < 6; i++) {
            double ph = t * (0.6 + i * 0.13) + i * 1.7;
            float r = 3.6f + 0.8f * (float) Math.sin(ph * 1.3 + i);
            float a = (float) (ph + i * Math.PI / 3);
            float cx = SX + (float) Math.cos(a) * r, cy = SY + (float) Math.sin(a) * r;
            float cz = SZ + 1.2f * (float) Math.sin(ph * 0.9 + i * 2);
            float twinkle = 0.5f + 0.5f * (float) Math.sin(t * 7 + i * 2.3);
            float size = 0.35f + 0.35f * twinkle;
            int col = scale(0xFFFFE9A8, 0.35f + 0.65f * twinkle);
            // a four-pointed glint: two thin crossed quads in the item's plane
            for (int k = 0; k < 2; k++) {
                float lx = k == 0 ? size * 1.8f : size * 0.25f, ly = k == 0 ? size * 0.25f : size * 1.8f;
                float[][] c = {{cx - lx, cy - ly}, {cx + lx, cy - ly}, {cx + lx, cy + ly}, {cx - lx, cy + ly}};
                float[][] uv = {{STAR[0], STAR[1]}, {STAR[2], STAR[1]}, {STAR[2], STAR[3]}, {STAR[0], STAR[3]}};
                for (int j = 0; j < 4; j++) {
                    m.point(c[j][0], c[j][1], cz, p);
                    sink.vertex(p[0], p[1], p[2], n[0], n[1], n[2], uv[j][0], uv[j][1], col);
                }
            }
        }
    }

    // ------------------------------------------------------------------ the star wand

    private static void starWand(Geo g, Geo glow) {
        float[] d = {0.7071f, 0.7071f, 0};
        float x0 = 2.1f, x1 = 11.0f;
        // the shaft: violet lacquer, a little thicker toward the grip
        int start = g.verts;
        Tube.build(g, new float[][] {{x0, x0, 8, 0.4f}, {6, 6, 8, 0.36f}, {x1, x1, 8, 0.3f}}, null, 0xFF6A4FE0, 10, 1f,
                new float[] {0, 0, 1}, 2);
        region(g, start, WHITE);
        // gold bands and the pommel
        for (float at : new float[] {2.6f, 5.2f, 9.0f, 10.8f}) {
            float w = at > 10 ? 0.55f : 0.32f;
            start = g.verts;
            Tube.build(g, new float[][] {{at - w * d[0], at - w * d[1], 8, 0.47f}, {at, at, 8, 0.5f}, {at + w * d[0], at + w * d[1], 8, 0.47f}},
                    null, 0xFFFFFFFF, 12, 1f, new float[] {0, 0, 1}, 0);
            region(g, start, GOLD);
        }
        start = g.verts;
        ball(g, 1.75f, 1.75f, 8f, 0.62f, 12, 8, 0xFFFFFFFF);
        region(g, start, GOLD);
        // the ribbon, wound round the shaft
        int turns = 5, n = 70;
        float[][] rib = new float[n][];
        float[] a = {-0.7071f, 0.7071f, 0}, b = {0, 0, 1};
        for (int i = 0; i < n; i++) {
            float t = i / (n - 1f);
            float along = 3.0f + t * 5.6f;
            float ang = (float) (t * turns * 2 * Math.PI);
            float r = 0.43f;
            rib[i] = new float[] {along + (a[0] * (float) Math.cos(ang) + b[0] * (float) Math.sin(ang)) * r,
                along + (a[1] * (float) Math.cos(ang) + b[1] * (float) Math.sin(ang)) * r,
                8 + (a[2] * (float) Math.cos(ang) + b[2] * (float) Math.sin(ang)) * r, 0.085f};
        }
        start = g.verts;
        Tube.build(g, rib, null, 0xFFFF8AD6, 5, 1f, d, 0);
        region(g, start, WHITE);
        // the star: five points, faceted like cut crystal, one point leading out along the wand
        start = g.verts;
        star(g, SX, SY, SZ, 3.3f, 1.4f, 0.95f, 0.22f, 45f, 0xFFFFE27A);
        region(g, start, STAR);
        // its glow: the star again, bigger and softer, added as light
        start = glow.verts;
        star(glow, SX, SY, SZ, 4.3f, 2.0f, 1.3f, 0.6f, 45f, 0xFF6B5420);
        region(glow, start, STAR);
    }

    /** A faceted five-pointed star lying in the item's plane (normals per facet). */
    static void star(Geo g, float cx, float cy, float cz, float outer, float inner, float bulge, float edge, float rot, int col) {
        float[][] o = new float[10][];
        for (int i = 0; i < 10; i++) {
            double a = Math.toRadians(rot + i * 36);
            float r = i % 2 == 0 ? outer : inner;
            o[i] = new float[] {cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r};
        }
        for (int side = -1; side <= 1; side += 2) {
            float[] top = {cx, cy, cz + side * bulge};
            for (int i = 0; i < 10; i++) {
                float[] p = {o[i][0], o[i][1], cz + side * edge * (i % 2 == 0 ? 0.6f : 1f)};
                float[] q = {o[(i + 1) % 10][0], o[(i + 1) % 10][1], cz + side * edge * ((i + 1) % 2 == 0 ? 0.6f : 1f)};
                facet(g, top, side > 0 ? p : q, side > 0 ? q : p, col);
            }
        }
        // the thin rim round the outside
        for (int i = 0; i < 10; i++) {
            int j = (i + 1) % 10;
            float ei = edge * (i % 2 == 0 ? 0.6f : 1f), ej = edge * (j % 2 == 0 ? 0.6f : 1f);
            float[] a = {o[i][0], o[i][1], cz + ei}, b2 = {o[j][0], o[j][1], cz + ej};
            float[] c = {o[j][0], o[j][1], cz - ej}, d = {o[i][0], o[i][1], cz - ei};
            float[] e1 = Sdf.sub(b2, a), e2 = Sdf.sub(d, a);
            float[] nn = Sdf.cross(e2, e1);
            int ia = g.vertex(a[0], a[1], a[2], nn[0], nn[1], nn[2], 0.1f, 0.1f, scaleRgb(col, 0.85f));
            int ib = g.vertex(b2[0], b2[1], b2[2], nn[0], nn[1], nn[2], 0.9f, 0.1f, scaleRgb(col, 0.85f));
            int ic = g.vertex(c[0], c[1], c[2], nn[0], nn[1], nn[2], 0.9f, 0.9f, scaleRgb(col, 0.85f));
            int id = g.vertex(d[0], d[1], d[2], nn[0], nn[1], nn[2], 0.1f, 0.9f, scaleRgb(col, 0.85f));
            g.quad(ia, id, ic, ib);
        }
    }

    private static void facet(Geo g, float[] a, float[] b, float[] c, int col) {
        float[] nn = Sdf.cross(Sdf.sub(b, a), Sdf.sub(c, a));
        int ia = g.vertex(a[0], a[1], a[2], nn[0], nn[1], nn[2], 0.5f, 0.5f, col);
        int ib = g.vertex(b[0], b[1], b[2], nn[0], nn[1], nn[2], 0.1f, 0.9f, scaleRgb(col, 0.9f));
        int ic = g.vertex(c[0], c[1], c[2], nn[0], nn[1], nn[2], 0.9f, 0.9f, scaleRgb(col, 0.9f));
        g.tri(ia, ib, ic);
    }

    // ------------------------------------------------------------------ the peppermint axe

    private static void peppermintAxe(Geo g, Geo glow) {
        float[] d = {0.7071f, 0.7071f, 0}, perp = {-0.7071f, 0.7071f, 0};
        float top = 12.2f;
        // candy-cane handle with its little hook, one stripe pattern all the way along
        int start = g.verts;
        Tube.build(g, new float[][] {
            {0.95f, 2.75f, 8, 0.5f}, {0.2f, 2.0f, 8, 0.52f}, {0.25f, 0.9f, 8, 0.54f}, {1.2f, 0.6f, 8, 0.55f},
            {2.2f, 2.2f, 8, 0.56f}, {7f, 7f, 8, 0.55f}, {top, top, 8, 0.52f}},
                null, 0xFFFFFFFF, 10, 1f, new float[] {0, 0, 1}, 3);
        region(g, start, CANDY);
        // mint ribbon grip
        start = g.verts;
        Tube.build(g, new float[][] {{3.6f, 3.6f, 8, 0.6f}, {5.4f, 5.4f, 8, 0.64f}, {7.2f, 7.2f, 8, 0.6f}}, null, 0xFFFFFFFF, 10, 1f,
                new float[] {0, 0, 1}, 1);
        region(g, start, MINT);
        // the collar where the head sits
        start = g.verts;
        Tube.build(g, new float[][] {{9.9f, 9.9f, 8, 0.66f}, {10.4f, 10.4f, 8, 0.72f}, {10.9f, 10.9f, 8, 0.66f}}, null, 0xFFFFFFFF, 12, 1f,
                new float[] {0, 0, 1}, 0);
        region(g, start, MINT);
        // the blade: a peppermint candy, flat in the middle and ground to an edge round the rim
        float hx = 10.6f + perp[0] * 2.3f, hy = 10.6f + perp[1] * 2.3f;
        start = g.verts;
        disc(g, hx, hy, 8f, 3.4f, 0.62f, 2.5f, 0.07f, 40);
        // the mint spike on the back
        int s2 = g.verts;
        Tube.build(g, new float[][] {
            {10.6f - perp[0] * 0.2f, 10.6f - perp[1] * 0.2f, 8, 0.62f},
            {10.6f - perp[0] * 1.2f + d[0] * 0.25f, 10.6f - perp[1] * 1.2f + d[1] * 0.25f, 8, 0.4f},
            {10.6f - perp[0] * 2.3f + d[0] * 0.6f, 10.6f - perp[1] * 2.3f + d[1] * 0.6f, 8, 0.05f}},
                null, 0xFFB8F5D0, 8, 0.7f, new float[] {0, 0, 1}, 2);
        region(g, s2, MINT);
        // a soft sugary sheen round the candy
        start = glow.verts;
        disc(glow, hx, hy, 8f, 3.65f, 0.75f, 2.8f, 0.2f, 32);
        for (int i = start; i < glow.verts; i++) {
            glow.c[i] = 0xFF2A0C10;
        }
    }

    /**
     * A round candy blade: faces mapped to the peppermint picture, thickness t out to radius
     * flat, then tapering to edge at R.
     */
    static void disc(Geo g, float cx, float cy, float cz, float R, float t, float flat, float edge, int segs) {
        float[] radii = {0f, 0.5f, 1.2f, 1.9f, flat, (flat + R) / 2, R};
        for (int side = -1; side <= 1; side += 2) {
            int[][] ring = new int[radii.length][segs];
            for (int k = 0; k < radii.length; k++) {
                float r = radii[k];
                float th = r <= flat ? t : t + (edge - t) * (r - flat) / (R - flat);
                // slope of the face for the normal
                float slope = r <= flat ? 0 : (t - edge) / (R - flat);
                for (int i = 0; i < segs; i++) {
                    double a = 2 * Math.PI * i / segs;
                    float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
                    float u = SWIRL[0] + (SWIRL[2] - SWIRL[0]) * (0.5f + 0.47f * ca * r / R);
                    float v = SWIRL[1] + (SWIRL[3] - SWIRL[1]) * (0.5f + 0.47f * sa * r / R);
                    ring[k][i] = g.vertex(cx + ca * r, cy + sa * r, cz + side * th,
                            ca * slope, sa * slope, side, u, v, 0xFFFFFFFF);
                }
            }
            for (int k = 0; k + 1 < radii.length; k++) {
                for (int i = 0; i < segs; i++) {
                    int j = (i + 1) % segs;
                    if (side > 0) {
                        g.quad(ring[k][i], ring[k + 1][i], ring[k + 1][j], ring[k][j]);
                    } else {
                        g.quad(ring[k][j], ring[k + 1][j], ring[k + 1][i], ring[k][i]);
                    }
                }
            }
        }
        // the edge band
        int[] up = new int[segs], dn = new int[segs];
        for (int i = 0; i < segs; i++) {
            double a = 2 * Math.PI * i / segs;
            float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
            float u = SWIRL[0] + (SWIRL[2] - SWIRL[0]) * (0.5f + 0.47f * ca);
            float v = SWIRL[1] + (SWIRL[3] - SWIRL[1]) * (0.5f + 0.47f * sa);
            up[i] = g.vertex(cx + ca * R, cy + sa * R, cz + edge, ca, sa, 0, u, v, 0xFFFFFFFF);
            dn[i] = g.vertex(cx + ca * R, cy + sa * R, cz - edge, ca, sa, 0, u, v, 0xFFFFFFFF);
        }
        for (int i = 0; i < segs; i++) {
            int j = (i + 1) % segs;
            g.quad(up[i], dn[i], dn[j], up[j]);
        }
    }

    // ------------------------------------------------------------------ helpers

    /** A small round ball. */
    static void ball(Geo g, float cx, float cy, float cz, float r, int segs, int rings, int col) {
        int[][] idx = new int[rings + 1][segs];
        for (int k = 0; k <= rings; k++) {
            double ph = Math.PI * k / rings;
            for (int i = 0; i < segs; i++) {
                double th = 2 * Math.PI * i / segs;
                float nx = (float) (Math.sin(ph) * Math.cos(th)), ny = (float) (Math.sin(ph) * Math.sin(th)), nz = (float) Math.cos(ph);
                idx[k][i] = g.vertex(cx + nx * r, cy + ny * r, cz + nz * r, nx, ny, nz, i / (float) segs, k / (float) rings, col);
            }
        }
        for (int k = 0; k < rings; k++) {
            for (int i = 0; i < segs; i++) {
                int j = (i + 1) % segs;
                g.quad(idx[k][i], idx[k][j], idx[k + 1][j], idx[k + 1][i]);
            }
        }
    }

    /** Maps the texture positions of vertices from start on (0..1) into a region of the atlas. */
    static void region(Geo g, int start, float[] r) {
        for (int i = start; i < g.verts; i++) {
            int o = i * Geo.STRIDE;
            float u = Math.max(0, Math.min(1, g.v[o + 6])), v = Math.max(0, Math.min(1, g.v[o + 7]));
            float pu = (r[2] - r[0]) * 0.02f, pv = (r[3] - r[1]) * 0.02f;
            g.v[o + 6] = r[0] + pu + (r[2] - r[0] - 2 * pu) * u;
            g.v[o + 7] = r[1] + pv + (r[3] - r[1] - 2 * pv) * v;
        }
    }

    static int scale(int argb, float k) {
        return 0xFF000000 | (scaleRgb(argb, k) & 0xFFFFFF);
    }

    static int scaleRgb(int argb, float k) {
        int r = Math.min(255, Math.round(((argb >> 16) & 255) * k));
        int g = Math.min(255, Math.round(((argb >> 8) & 255) * k));
        int b = Math.min(255, Math.round((argb & 255) * k));
        return (argb & 0xFF000000) | r << 16 | g << 8 | b;
    }
}
