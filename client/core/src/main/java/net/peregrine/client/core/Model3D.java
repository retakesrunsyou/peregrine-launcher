package net.peregrine.client.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A player drawn in 3D from their skin (and Peregrine cape): the boxes of a Minecraft player,
 * turned and tilted, each face drawn as a textured parallelogram, farthest first. Seen
 * straight on (no perspective), which is how Minecraft draws players in menus too.
 *
 * Model space: x to the player's left (screen right when facing you), y down from the top of
 * the head (0) to the feet (32), z toward you. Units are skin pixels.
 */
public final class Model3D {

    private Model3D() {
    }

    private static final class Face {
        float depth;
        float[] p0, eu, ev;      // screen: corner, then the edges along u and v
        float u0, v0, u1, v1;    // texture fractions
        int shade;
        String tex;
    }

    /** One box: position, size, where its skin is (u, v origin in pixels), how much bigger it is drawn. */
    private static void box(List<Face> out, float x0, float y0, float z0, float w, float h, float d, int u, int v,
                            float grow, float tw, float th, String tex, float[] rot, boolean mirror, float[] limb) {
        x0 -= grow;
        y0 -= grow;
        z0 -= grow;
        float bw = w + 2 * grow, bh = h + 2 * grow, bd = d + 2 * grow;
        float x1 = x0 + bw, y1 = y0 + bh, z1 = z0 + bd;
        // texture rectangles (pixels) of each face: front, back, right side (x0), left side (x1), top, bottom
        float[][] uv = {
            {u + d, v + d, w, h},
            {u + 2 * d + w, v + d, w, h},
            {u, v + d, d, h},
            {u + d + w, v + d, d, h},
            {u + d, v, w, d},
            {u + d + w, v, w, d},
        };
        if (mirror) {  // old 64x32 skins: the left limbs are the right ones, flipped
            float[] t = uv[2];
            uv[2] = uv[3];
            uv[3] = t;
        }
        // corner, u edge, v edge (model space) and outward normal of each face
        float[][][] faces = {
            {{x0, y0, z1}, {bw, 0, 0}, {0, bh, 0}, {0, 0, 1}},
            {{x1, y0, z0}, {-bw, 0, 0}, {0, bh, 0}, {0, 0, -1}},
            {{x0, y0, z0}, {0, 0, bd}, {0, bh, 0}, {-1, 0, 0}},
            {{x1, y0, z1}, {0, 0, -bd}, {0, bh, 0}, {1, 0, 0}},
            {{x0, y0, z0}, {bw, 0, 0}, {0, 0, bd}, {0, -1, 0}},
            {{x0, y1, z1}, {bw, 0, 0}, {0, 0, -bd}, {0, 1, 0}},
        };
        int[] shades = {255, 235, 205, 205, 245, 170};
        for (int i = 0; i < 6; i++) {
            float[][] f = faces[i];
            float[] n = rotDir(rot, limbDir(limb, f[3]));
            if (n[2] <= 0.0001f) {
                continue;  // facing away
            }
            float[] p0 = f[0], eu = f[1], ev = f[2];
            float[] r = uv[i];
            float fu0 = r[0], fu1 = r[0] + r[2];
            if (mirror) {  // flip across the face
                p0 = new float[] {p0[0] + eu[0], p0[1] + eu[1], p0[2] + eu[2]};
                eu = new float[] {-eu[0], -eu[1], -eu[2]};
            }
            Face face = new Face();
            float[] a = rotPoint(rot, limbPoint(limb, p0));
            float[] b = rotPoint(rot, limbPoint(limb, add(p0, eu)));
            float[] c = rotPoint(rot, limbPoint(limb, add(p0, ev)));
            float[] centre = rotPoint(rot, limbPoint(limb, new float[] {p0[0] + eu[0] / 2 + ev[0] / 2,
                p0[1] + eu[1] / 2 + ev[1] / 2, p0[2] + eu[2] / 2 + ev[2] / 2}));
            face.p0 = a;
            face.eu = new float[] {b[0] - a[0], b[1] - a[1]};
            face.ev = new float[] {c[0] - a[0], c[1] - a[1]};
            face.depth = centre[2] + grow * 0.01f;
            face.u0 = fu0 / tw;
            face.u1 = fu1 / tw;
            face.v0 = r[1] / th;
            face.v1 = (r[1] + r[3]) / th;
            float light = 0.78f + 0.22f * Math.max(0f, n[2]);
            int sh = Math.round(shades[i] * light);
            face.shade = 0xFF000000 | (sh << 16) | (sh << 8) | sh;
            face.tex = tex;
            out.add(face);
        }
    }

    private static float[] add(float[] a, float[] b) {
        return new float[] {a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    // ---- turning: limb swing (around a pivot, about x), then the whole player (yaw, then pitch)

    /** limb: {pivotY, pivotZ, angle (radians, about x)} or null. */
    private static float[] limbPoint(float[] limb, float[] p) {
        if (limb == null) {
            return p;
        }
        float c = (float) Math.cos(limb[2]), s = (float) Math.sin(limb[2]);
        float y = p[1] - limb[0], z = p[2] - limb[1];
        return new float[] {p[0], limb[0] + y * c - z * s, limb[1] + y * s + z * c};
    }

    private static float[] limbDir(float[] limb, float[] n) {
        if (limb == null) {
            return n;
        }
        float c = (float) Math.cos(limb[2]), s = (float) Math.sin(limb[2]);
        return new float[] {n[0], n[1] * c - n[2] * s, n[1] * s + n[2] * c};
    }

    /** rot: {cos yaw, sin yaw, cos pitch, sin pitch, pivot y, screen x, screen y, scale} */
    private static float[] rotPoint(float[] r, float[] p) {
        float x = p[0], y = p[1] - r[4], z = p[2];
        float x1 = x * r[0] + z * r[1];
        float z1 = -x * r[1] + z * r[0];
        float y2 = y * r[2] - z1 * r[3];
        float z2 = y * r[3] + z1 * r[2];
        return new float[] {r[5] + x1 * r[7], r[6] + (y2 + r[4]) * r[7], z2};
    }

    private static float[] rotDir(float[] r, float[] n) {
        float x1 = n[0] * r[0] + n[2] * r[1];
        float z1 = -n[0] * r[1] + n[2] * r[0];
        float z2 = n[1] * r[3] + z1 * r[2];
        return new float[] {x1, 0, z2};
    }

    /**
     * Draws the player. cx, top: where the middle of the figure and the top of the head go;
     * scale: screen pixels per skin pixel. yaw 0 faces you; pitch tips the top toward you.
     * t is seconds, for the idle sway of arms and cape.
     */
    public static void draw(Draw d, String skin, boolean slim, boolean legacy, String cape, float cx, float top, float scale,
                            float yawDeg, float pitchDeg, float t) {
        float yaw = (float) Math.toRadians(yawDeg), pitch = (float) Math.toRadians(pitchDeg);
        float[] rot = {(float) Math.cos(yaw), (float) Math.sin(yaw), (float) Math.cos(pitch), (float) Math.sin(pitch), 16f,
            cx, top, scale};
        List<Face> faces = new ArrayList<Face>();
        float tw = 64f, th = legacy ? 32f : 64f;
        float aw = slim ? 3 : 4;
        float swing = (float) Math.sin(t * 1.6) * 0.06f;
        float[] rArm = {9f, 0f, swing}, lArm = {9f, 0f, -swing};
        // head, body, arms, legs, then the outer layer slightly bigger
        box(faces, -4, 0, -4, 8, 8, 8, 0, 0, 0, tw, th, skin, rot, false, null);
        box(faces, -4, 8, -2, 8, 12, 4, 16, 16, 0, tw, th, skin, rot, false, null);
        box(faces, -4 - aw, 8, -2, aw, 12, 4, 40, 16, 0, tw, th, skin, rot, false, rArm);
        box(faces, -4, 20, -2, 4, 12, 4, 0, 16, 0, tw, th, skin, rot, false, null);
        if (legacy) {
            box(faces, 4, 8, -2, aw, 12, 4, 40, 16, 0, tw, th, skin, rot, true, lArm);
            box(faces, 0, 20, -2, 4, 12, 4, 0, 16, 0, tw, th, skin, rot, true, null);
            box(faces, -4, 0, -4, 8, 8, 8, 32, 0, 0.5f, tw, th, skin, rot, false, null);
        } else {
            box(faces, 4, 8, -2, aw, 12, 4, 32, 48, 0, tw, th, skin, rot, false, lArm);
            box(faces, 0, 20, -2, 4, 12, 4, 16, 48, 0, tw, th, skin, rot, false, null);
            box(faces, -4, 0, -4, 8, 8, 8, 32, 0, 0.5f, tw, th, skin, rot, false, null);
            box(faces, -4, 8, -2, 8, 12, 4, 16, 32, 0.25f, tw, th, skin, rot, false, null);
            box(faces, -4 - aw, 8, -2, aw, 12, 4, 40, 32, 0.25f, tw, th, skin, rot, false, rArm);
            box(faces, 4, 8, -2, aw, 12, 4, 48, 48, 0.25f, tw, th, skin, rot, false, lArm);
            box(faces, -4, 20, -2, 4, 12, 4, 0, 32, 0.25f, tw, th, skin, rot, false, null);
            box(faces, 0, 20, -2, 4, 12, 4, 0, 48, 0.25f, tw, th, skin, rot, false, null);
        }
        if (cape != null) {
            // hangs from the shoulders behind the back, tipped out a little and swaying
            float tilt = (float) Math.toRadians(-8 - 3 * Math.sin(t * 1.3));
            float[] hang = {8f, -2.6f, tilt};
            capeBox(faces, cape, rot, hang);
        }
        Collections.sort(faces, new Comparator<Face>() {
            public int compare(Face a, Face b) {
                return Float.compare(a.depth, b.depth);
            }
        });
        for (Face f : faces) {
            // a hair bigger, so no seams show between faces
            float gx = 0.012f, gy = 0.012f;
            float px = f.p0[0] - (f.eu[0] + f.ev[0]) * gx, py = f.p0[1] - (f.eu[1] + f.ev[1]) * gy;
            d.textureQuad(f.tex, px, py, f.eu[0] * (1 + 2 * gx), f.eu[1] * (1 + 2 * gx), f.ev[0] * (1 + 2 * gy),
                    f.ev[1] * (1 + 2 * gy), f.u0, f.v0, f.u1, f.v1, f.shade);
        }
    }

    /** The cape: 10x16x1, its outside (1, 1 on the cape texture) facing away from the player. */
    private static void capeBox(List<Face> out, String tex, float[] rot, float[] hang) {
        float x0 = -5, y0 = 8, z0 = -3.1f, w = 10, h = 16, d = 1;
        float x1 = x0 + w, y1 = y0 + h, z1 = z0 + d;
        // face, texture rect (pixels on 64x32), corner, u, v, normal
        Object[][] faces = {
            {new float[] {1, 1, 10, 16}, new float[] {x1, y0, z0}, new float[] {-w, 0, 0}, new float[] {0, h, 0}, new float[] {0, 0, -1}},
            {new float[] {12, 1, 10, 16}, new float[] {x0, y0, z1}, new float[] {w, 0, 0}, new float[] {0, h, 0}, new float[] {0, 0, 1}},
            {new float[] {0, 1, 1, 16}, new float[] {x0, y0, z0}, new float[] {0, 0, d}, new float[] {0, h, 0}, new float[] {-1, 0, 0}},
            {new float[] {11, 1, 1, 16}, new float[] {x1, y0, z1}, new float[] {0, 0, -d}, new float[] {0, h, 0}, new float[] {1, 0, 0}},
            {new float[] {1, 0, 10, 1}, new float[] {x0, y0, z0}, new float[] {w, 0, 0}, new float[] {0, 0, d}, new float[] {0, -1, 0}},
            {new float[] {11, 0, 10, 1}, new float[] {x0, y1, z1}, new float[] {w, 0, 0}, new float[] {0, 0, -d}, new float[] {0, 1, 0}},
        };
        for (Object[] f : faces) {
            float[] r = (float[]) f[0], p0 = (float[]) f[1], eu = (float[]) f[2], ev = (float[]) f[3], n = (float[]) f[4];
            float[] rn = rotDir(rot, limbDir(hang, n));
            if (rn[2] <= 0.0001f) {
                continue;
            }
            float[] a = rotPoint(rot, limbPoint(hang, p0));
            float[] b = rotPoint(rot, limbPoint(hang, add(p0, eu)));
            float[] c = rotPoint(rot, limbPoint(hang, add(p0, ev)));
            float[] centre = rotPoint(rot, limbPoint(hang, new float[] {p0[0] + eu[0] / 2 + ev[0] / 2,
                p0[1] + eu[1] / 2 + ev[1] / 2, p0[2] + eu[2] / 2 + ev[2] / 2}));
            Face face = new Face();
            face.p0 = a;
            face.eu = new float[] {b[0] - a[0], b[1] - a[1]};
            face.ev = new float[] {c[0] - a[0], c[1] - a[1]};
            face.depth = centre[2];
            face.u0 = r[0] / 64f;
            face.u1 = (r[0] + r[2]) / 64f;
            face.v0 = r[1] / 32f;
            face.v1 = (r[1] + r[3]) / 32f;
            int sh = Math.round(255 * (0.75f + 0.25f * rn[2]));
            face.shade = 0xFF000000 | (sh << 16) | (sh << 8) | sh;
            face.tex = tex;
            out.add(face);
        }
    }
}
