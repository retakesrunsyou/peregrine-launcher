package net.peregrine.client.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Peregrine's custom skies: the Milky Way, northern lights, a meteor shower, a black hole and
 * shooting stars. Each sky is built here, every frame, as a short list of textured four-sided
 * pieces on the sky dome (about a thousand at most, like Minecraft's own stars), already
 * animated: the stars turn with the time of day, the aurora's curtains ripple, meteors fly,
 * the black hole's disk swirls. A version adapter only has to draw the list, in order, with the
 * camera at the centre, after Minecraft's sky and before the world. No shaders, no per-pixel
 * work: it costs about what vanilla's stars do.
 */
public final class Skies {

    /** One textured piece: four corners (directions times RADIUS, camera at 0), their UVs, a tint. */
    public static final class Quad {
        public final String texture;
        public final float[] xyz = new float[12];
        public final float[] uv = new float[8];
        public int argb;

        Quad(String texture) {
            this.texture = texture;
        }
    }

    public static final String[] NAMES = {"Milky Way", "Northern lights", "Meteor shower", "Black hole", "Shooting stars"};
    public static final float RADIUS = 100f;
    static final String T = "peregrine-client:textures/sky/";

    private Skies() {
    }

    // ------------------------------------------------------------------ settings, set by the module

    public static volatile int sky = -1;            // index into NAMES, -1 = off (vanilla sky)
    public static volatile float brightness = 1f;
    public static volatile float speed = 1f;
    public static volatile boolean daytime;         // also show it during the day (faded)
    public static volatile boolean moon = true;     // a detailed moon, in its real phase
    public static volatile boolean clouds = true;   // thin night clouds drifting low in the sky
    public static volatile boolean turning;         // the stars turn with the time of day (off: they stay put)
    public static volatile int moonPhase;           // 0 full .. 4 new .. 7, from the world's day
    public static volatile long day;                // the world's day: the stars shift a little each night
    public static volatile float weather;           // 0 clear .. 1 full rain: clouds hide the sky

    /**
     * Turns a picture's name into a texture id. The module sets this to load the pictures
     * straight from Peregrine's own files, so no resource pack can replace them; without it,
     * the pictures' resource ids are used.
     */
    public static volatile java.util.function.Function<String, String> loader;
    private static final java.util.Map<String, String> ids = new java.util.HashMap<String, String>();

    static String tex(String name) {
        java.util.function.Function<String, String> l = loader;
        if (l == null) {
            return T + name;
        }
        String id = ids.get(name);
        if (id == null) {
            id = l.apply(name);
            if (id == null) {
                id = T + name;
            }
            ids.put(name, id);
        }
        return id;
    }

    /** Every picture a sky uses, to load ahead of time. */
    public static final String[] TEXTURES = {"white.png", "horizon.png", "stars.png", "star.png", "milkyway.png",
        "aurora.png", "meteor.png", "train.png", "blackhole.png", "clouds.png", "moonglow.png",
        "moon_0.png", "moon_1.png", "moon_2.png", "moon_3.png", "moon_4.png", "moon_5.png", "moon_6.png", "moon_7.png"};

    // ------------------------------------------------------------------ building a frame

    private static final List<Quad> pool = new ArrayList<Quad>();
    private static int used;

    private static Quad quad(String tex) {
        Quad q;
        if (used < pool.size()) {
            q = pool.get(used);
            if (!q.texture.equals(tex)) {
                q = new Quad(tex);
                pool.set(used, q);
            }
        } else {
            q = new Quad(tex);
            pool.add(q);
        }
        used++;
        return q;
    }

    /** How much of the custom sky shows at this time of day (0 by day unless "daytime"). */
    public static float visibility(long worldTime) {
        float t = (worldTime % 24000L + 24000L) % 24000L;
        // night is about 13000-23000: fade in over dusk, out over dawn
        float night;
        if (t >= 13500 && t <= 22500) {
            night = 1f;
        } else if (t > 12000 && t < 13500) {
            night = (t - 12000) / 1500f;
        } else if (t > 22500 && t < 24000) {
            night = (24000 - t) / 1500f;
        } else {
            night = 0f;
        }
        night = night * night * (3 - 2 * night);
        return daytime ? 0.35f + 0.65f * night : night;
    }

    /**
     * The pieces to draw this frame, back to front. ms: a clock in milliseconds (for animation);
     * worldTime: the world's time of day (the stars turn with it). Empty when no sky is chosen or
     * it's daytime. The list and its pieces are reused next frame: copy anything you keep.
     */
    public static List<Quad> build(long ms, long worldTime) {
        used = 0;
        int which = sky;
        float vis = which < 0 ? 0f : visibility(worldTime) * Math.max(0f, Math.min(2f, brightness));
        vis *= 1f - Math.max(0f, Math.min(1f, weather));
        List<Quad> out = new ArrayList<Quad>();
        if (vis <= 0.004f) {
            return out;
        }
        double secs = ms / 1000.0 * Math.max(0.05f, speed);
        // the real sky for someone at 40° north: it turns once a Minecraft day, and starts each
        // night two hours further on, so different constellations come round over the nights
        Sky f = new Sky(worldTime, day);
        now = f;

        // a dark, slightly blue dome over Minecraft's own sky
        int zen = which == 3 ? 0x020208 : which == 1 ? 0x020611 : 0x02040B;
        int hor = which == 3 ? 0x0C0A1A : which == 1 ? 0x07182A : 0x0A1226;
        dome(out, zen, hor, vis);
        // airglow: the faint green light of the upper air, low all round
        glowRing(tex("horizon.png"), Math.toRadians(-6), Math.toRadians(24), argb(vis * (which == 1 ? 0.5f : 0.38f), which == 1 ? 0x1E5A3A : 0x173322));
        // the Milky Way, then the stars over it, where they really are
        celestial(f, tex("milkyway.png"), vis * (which == 0 ? 1f : 0.8f));
        celestial(f, tex("stars.png"), vis);
        twinkles(f, vis, secs);
        if (moon && which != 3) {
            moon(vis, worldTime);
        }
        switch (which) {
            case 1:
                aurora(out, vis, secs);
                break;
            case 2:
                meteors(vis, ms, true);
                break;
            case 3:
                blackHole(vis, secs);
                break;
            case 4:
            case 0:
                meteors(vis, ms, false);
                break;
            default:
                break;
        }
        if (clouds) {
            clouds(vis, secs, which);
        }
        for (int i = 0; i < used; i++) {
            out.add(pool.get(i));
        }
        return out;
    }

    // ------------------------------------------------------------------ geometry helpers

    /**
     * The real sky's position: converts right ascension and declination to a direction in the
     * game (x east, y up, z south), for an observer at 40° north. Local sidereal time follows the
     * world's clock: at midnight on day 0 it's 18h30, Sagittarius (the Milky Way's core) due south.
     */
    private static Sky now;

    static final class Sky {
        static final double LAT = Math.toRadians(40);
        final double lst;

        Sky(long worldTime, long day) {
            if (!turning) {
                // still: the Milky Way arched high across the sky (Cygnus overhead), as on a summer night
                lst = Math.toRadians(20.3 * 15);
                return;
            }
            double t = ((worldTime % 24000L) + 24000L) % 24000L;
            lst = (t - 18000) / 24000.0 * Math.PI * 2 + Math.toRadians(18.5 * 15) + Math.floorMod(day, 12L) * Math.toRadians(2 * 15);
        }

        double[] local(double ra, double dec) {
            double h = lst - ra;
            double cd = Math.cos(dec), sd = Math.sin(dec), sl = Math.sin(LAT), cl = Math.cos(LAT);
            double up = sl * sd + cl * cd * Math.cos(h);
            double north = cl * sd - sl * cd * Math.cos(h);
            double east = -cd * Math.sin(h);
            return new double[] {east, up, -north};
        }
    }

    /** A direction from azimuth (0 = north, clockwise) and elevation, in radians. */
    static double[] dir(double az, double el) {
        double ce = Math.cos(el);
        return new double[] {Math.sin(az) * ce, Math.sin(el), -Math.cos(az) * ce};
    }

    private static void corner(Quad q, int i, double[] d, float u, float v) {
        q.xyz[i * 3] = (float) (d[0] * RADIUS);
        q.xyz[i * 3 + 1] = (float) (d[1] * RADIUS);
        q.xyz[i * 3 + 2] = (float) (d[2] * RADIUS);
        q.uv[i * 2] = u;
        q.uv[i * 2 + 1] = v;
    }

    private static int argb(float a, int rgb) {
        int al = Math.round(Math.max(0f, Math.min(1f, a)) * 255);
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    private static double[] norm(double[] v) {
        double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return new double[] {v[0] / l, v[1] / l, v[2] / l};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double[] add(double[] c, double[] u, double su, double[] v, double sv) {
        return new double[] {c[0] + u[0] * su + v[0] * sv, c[1] + u[1] * su + v[1] * sv, c[2] + u[2] * su + v[2] * sv};
    }

    /** Two directions at right angles to d, for building things round a point in the sky. */
    private static double[][] tangents(double[] d) {
        double[] up = Math.abs(d[1]) > 0.95 ? new double[] {1, 0, 0} : new double[] {0, 1, 0};
        double[] u = norm(cross(up, d));
        double[] v = norm(cross(d, u));
        return new double[][] {u, v};
    }

    // ------------------------------------------------------------------ layers

    /** The dark dome: one colour all over, then a soft glow of the horizon colour rising from below. */
    private static void dome(List<Quad> out, int zenith, int horizon, float vis) {
        int segs = 24;
        for (int r = 0; r < 6; r++) {
            double e0 = Math.toRadians(-90 + 30.0 * r), e1 = Math.toRadians(-90 + 30.0 * (r + 1));
            for (int s = 0; s < segs; s++) {
                double a0 = Math.PI * 2 * s / segs, a1 = Math.PI * 2 * (s + 1) / segs;
                Quad q = quad(tex("white.png"));
                corner(q, 0, dir(a0, e1), 0, 0);
                corner(q, 1, dir(a0, e0), 0, 1);
                corner(q, 2, dir(a1, e0), 1, 1);
                corner(q, 3, dir(a1, e1), 1, 0);
                q.argb = argb(vis, zenith);
            }
        }
        // the glow: from 10° below the horizon (solid) up to 55° (gone)
        double lo = Math.toRadians(-10), hi = Math.toRadians(55);
        for (int s = 0; s < segs; s++) {
            double a0 = Math.PI * 2 * s / segs, a1 = Math.PI * 2 * (s + 1) / segs;
            Quad q = quad(tex("horizon.png"));
            corner(q, 0, dir(a0, hi), 0, 0);
            corner(q, 1, dir(a0, lo), 0, 1);
            corner(q, 2, dir(a1, lo), 1, 1);
            corner(q, 3, dir(a1, hi), 1, 0);
            q.argb = argb(vis, horizon);
        }
    }

    /** A ring of a vertical gradient round the whole horizon, from elevation lo (solid) to hi (gone). */
    private static void glowRing(String tex, double lo, double hi, int argb) {
        int segs = 24;
        for (int s = 0; s < segs; s++) {
            double a0 = Math.PI * 2 * s / segs, a1 = Math.PI * 2 * (s + 1) / segs;
            Quad q = quad(tex);
            corner(q, 0, dir(a0, hi), 0, 0);
            corner(q, 1, dir(a0, lo), 0, 1);
            corner(q, 2, dir(a1, lo), 1, 1);
            corner(q, 3, dir(a1, hi), 1, 0);
            q.argb = argb;
        }
    }

    /**
     * A strip piece whose picture scrolls sideways (u can run past 1): split where the picture
     * wraps, so every piece's UVs stay within 0..1 (textures here don't repeat by themselves).
     * Corners: a0/a1 along the top (v = vTop), b0/b1 along the bottom.
     */
    private static void strip(String tex, double[] a0, double[] b0, double[] b1, double[] a1, double u0, double u1,
                              float vTop, float vBot, int argb) {
        double base = Math.floor(u0);
        u0 -= base;
        u1 -= base;
        if (u1 <= 1.0 + 1e-6) {
            Quad q = quad(tex);
            corner(q, 0, a0, (float) u0, vTop);
            corner(q, 1, b0, (float) u0, vBot);
            corner(q, 2, b1, (float) Math.min(1, u1), vBot);
            corner(q, 3, a1, (float) Math.min(1, u1), vTop);
            q.argb = argb;
            return;
        }
        double k = (1 - u0) / (u1 - u0);
        double[] am = norm(lerp(a0, a1, k)), bm = norm(lerp(b0, b1, k));
        strip(tex, a0, b0, bm, am, u0, 1.0, vTop, vBot, argb);
        strip(tex, am, bm, b1, a1, 0.0, u1 - 1.0, vTop, vBot, argb);
    }

    /** The Moon where Minecraft has it (rising in the east at dusk, high at midnight), in its phase. */
    private static void moon(float vis, long worldTime) {
        double t = (worldTime % 24000L + 24000L) % 24000L;
        double phi = (t - 12000) / 24000.0 * Math.PI * 2;      // 0 east at dusk, up at midnight
        double[] d = norm(new double[] {Math.cos(phi), Math.sin(phi), 0.18});
        if (d[1] < -0.08) {
            return;
        }
        int ph = Math.floorMod(moonPhase, 8);
        float lit = (float) (0.5 + 0.5 * Math.cos(ph / 8.0 * Math.PI * 2));   // 1 full .. 0 new
        double size = Math.toRadians(6.5);
        // a soft halo, brighter the fuller it is
        billboard(quad(tex("moonglow.png")), d, size * 4.5, 0, argb(vis * 0.35f * (0.15f + 0.85f * lit), 0xC8D6FF));
        billboard(quad(tex("moon_" + ph + ".png")), d, size, Math.PI, argb(vis, 0xFFFFFF));
    }

    /** Thin clouds low round the sky, drifting slowly; they pass in front of everything else. */
    private static void clouds(float vis, double secs, int which) {
        int segs = 48;
        double lo = Math.toRadians(-4), hi = Math.toRadians(20);
        double drift = secs * 0.0025;
        float a = vis * (which == 3 ? 0.35f : 0.5f);
        for (int s = 0; s < segs; s++) {
            double a0 = Math.PI * 2 * s / segs, a1 = Math.PI * 2 * (s + 1) / segs;
            double u0 = s / (double) segs * 2 + drift, u1 = (s + 1) / (double) segs * 2 + drift;
            strip(tex("clouds.png"), dir(a0, hi), dir(a0, lo), dir(a1, lo), dir(a1, hi), u0, u1, 0f, 1f, argb(a, 0xFFFFFF));
        }
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** A picture of the whole sky (right ascension across, declination down) on a sphere, where the real sky is. */
    private static void celestial(Sky f, String tex, float vis) {
        int segs = 48, rows = 24;
        for (int r = 0; r < rows; r++) {
            double d0 = Math.toRadians(90 - 180.0 * r / rows), d1 = Math.toRadians(90 - 180.0 * (r + 1) / rows);
            float v0 = r / (float) rows, v1 = (r + 1) / (float) rows;
            for (int s = 0; s < segs; s++) {
                double a0 = Math.PI * 2 * s / segs, a1 = Math.PI * 2 * (s + 1) / segs;
                double[] p00 = f.local(a0, d0), p01 = f.local(a0, d1), p11 = f.local(a1, d1), p10 = f.local(a1, d0);
                if (p00[1] < -0.2 && p01[1] < -0.2 && p11[1] < -0.2 && p10[1] < -0.2) {
                    continue;  // all below the ground
                }
                Quad q = quad(tex);
                float u0 = s / (float) segs, u1 = (s + 1) / (float) segs;
                corner(q, 0, p00, u0, v0);
                corner(q, 1, p01, u0, v1);
                corner(q, 2, p11, u1, v1);
                corner(q, 3, p10, u1, v0);
                q.argb = argb(vis, 0xFFFFFF);
            }
        }
    }

    /** The brightest real stars (RA hours, Dec degrees, magnitude, colour): they get a soft glow and twinkle. */
    private static final double[][] BRIGHT = {
        {6.752, -16.716, -1.46, 0xE1EAFF}, // Sirius
        {6.399, -52.696, -0.74, 0xE8EEFF}, // Canopus
        {14.660, -60.834, -0.27, 0xFFF6EE}, // Rigil Kentaurus
        {14.261, 19.182, -0.05, 0xFFE7D4}, // Arcturus
        {18.616, 38.784, 0.03, 0xE1EAFF}, // Vega
        {5.278, 45.998, 0.08, 0xFFF3E9}, // Capella
        {5.242, -8.202, 0.13, 0xDFE9FF}, // Rigel
        {7.655, 5.225, 0.34, 0xFCFAFF}, // Procyon
        {1.629, -57.237, 0.46, 0xDAE6FF}, // Achernar
        {5.919, 7.407, 0.50, 0xFFD9BA}, // Betelgeuse
        {14.064, -60.373, 0.61, 0xD7E4FF}, // Hadar
        {19.846, 8.868, 0.76, 0xECF1FF}, // Altair
        {12.443, -63.099, 0.76, 0xD6E3FF}, // Acrux
        {4.599, 16.509, 0.86, 0xFFE0C7}, // Aldebaran
        {16.490, -26.432, 0.96, 0xFFDABB}, // Antares
        {13.420, -11.161, 0.97, 0xD7E4FF}, // Spica
        {7.755, 28.026, 1.14, 0xFFEDDE}, // Pollux
        {22.961, -29.622, 1.16, 0xE5ECFF}, // Fomalhaut
        {20.690, 45.280, 1.25, 0xE5ECFF}, // Deneb
        {12.795, -59.689, 1.25, 0xD7E4FF}, // Mimosa
        {10.140, 11.967, 1.35, 0xDCE7FF}, // Regulus
        {6.977, -28.972, 1.50, 0xD8E4FF}, // Adhara
        {7.577, 31.888, 1.58, 0xE2EBFF}, // Castor
        {17.560, -37.104, 1.62, 0xD7E4FF}, // Shaula
        {12.519, -57.113, 1.63, 0xFFDFC4}, // Gacrux
        {5.419, 6.350, 1.64, 0xD7E4FF}, // Bellatrix
        {5.438, 28.608, 1.65, 0xDBE6FF}, // Elnath
        {9.220, -69.717, 1.68, 0xE4ECFF}, // Miaplacidus
        {5.604, -1.202, 1.69, 0xD9E5FF}, // Alnilam
        {22.137, -46.961, 1.73, 0xDBE6FF}, // Alnair
        {5.679, -1.943, 1.77, 0xD8E4FF}, // Alnitak
        {12.900, 55.960, 1.77, 0xE0E9FF}, // Alioth
        {11.062, 61.751, 1.79, 0xFFEBDB}, // Dubhe
        {3.405, 49.861, 1.79, 0xFFFEFB}, // Mirfak
        {7.140, -26.393, 1.84, 0xFFF7EF}, // Wezen
        {18.403, -34.385, 1.85, 0xDFE9FF}, // Kaus Australis
        {17.622, -42.998, 1.86, 0xFAF9FF}, // Sargas
        {8.375, -59.510, 1.86, 0xFFE6D1}, // Avior
        {13.792, 49.313, 1.86, 0xD9E5FF}, // Alkaid
        {5.992, 44.947, 1.90, 0xE4ECFF}, // Menkalinan
        {16.811, -69.028, 1.91, 0xFFE2CB}, // Atria
        {6.629, 16.399, 1.93, 0xE1EAFF}, // Alhena
        {20.427, -56.735, 1.94, 0xD8E5FF}, // Peacock
        {8.745, -54.708, 1.96, 0xE3EBFF}, // Alsephina
        {2.530, 89.264, 1.98, 0xFFF9F4}, // Polaris
        {6.378, -17.956, 1.98, 0xD7E4FF}, // Mirzam
        {9.460, -8.659, 1.98, 0xFFE2CB}, // Alphard
        {2.120, 23.462, 2.00, 0xFFE9D7}, // Hamal
        {10.333, 19.842, 2.01, 0xFFE9D8}, // Algieba
        {0.727, -17.987, 2.04, 0xFFECDD}, // Diphda
        {18.921, -26.297, 2.05, 0xDBE6FF}, // Nunki
        {1.162, 35.621, 2.05, 0xFFDFC5}, // Mirach
        {14.111, -36.370, 2.06, 0xFFECDE}, // Menkent
        {0.140, 29.091, 2.06, 0xDCE7FF}, // Alpheratz
        {22.711, -46.885, 2.07, 0xFFDEC4}, // Tiaki
        {17.582, 12.560, 2.08, 0xE8EEFF}, // Rasalhague
        {14.845, 74.156, 2.08, 0xFFE1C9}, // Kochab
        {5.796, -9.670, 2.09, 0xD9E5FF}, // Saiph
        {2.065, 42.330, 2.10, 0xFFE3CE}, // Almach
        {3.136, 40.956, 2.12, 0xDFE9FF}, // Algol
    };

    private static void twinkles(Sky f, float vis, double secs) {
        for (int i = 0; i < BRIGHT.length; i++) {
            double[] s = BRIGHT[i];
            double[] d = f.local(Math.toRadians(s[0] * 15), Math.toRadians(s[1]));
            if (d[1] < -0.02) {
                continue;  // set
            }
            double flux = Math.pow(10, -0.4 * (s[2] - 1.0));
            // stars twinkle more low down, where their light crosses more air
            double low = Math.pow(1 - Math.max(0, d[1]), 3);
            double amp = 0.06 + 0.40 * low;
            double ph = i * 12.9898;
            float tw = (float) (1 - amp * (0.5 + 0.5 * Math.sin(secs * (5 + i % 7) + ph)) * (0.6 + 0.4 * Math.sin(secs * 13.1 + ph * 2)));
            float a = (float) Math.min(1, 0.25 + 0.35 * Math.pow(flux, 0.5)) * tw;
            double size = Math.toRadians(0.6 + 0.9 * Math.pow(Math.min(flux, 12), 0.35));
            billboard(quad(tex("star.png")), d, size, 0, argb(vis * a, (int) s[3]));
        }
    }

    /** A square picture facing you at direction d, `size` radians across, turned by `rot`. */
    private static void billboard(Quad q, double[] d, double size, double rot, int argb) {
        double[][] t = tangents(d);
        double c = Math.cos(rot) * size / 2, s = Math.sin(rot) * size / 2;
        double[] u = {t[0][0] * c + t[1][0] * s, t[0][1] * c + t[1][1] * s, t[0][2] * c + t[1][2] * s};
        double[] v = {-t[0][0] * s + t[1][0] * c, -t[0][1] * s + t[1][1] * c, -t[0][2] * s + t[1][2] * c};
        corner(q, 0, norm(add(d, u, -1, v, 1)), 0, 0);
        corner(q, 1, norm(add(d, u, -1, v, -1)), 0, 1);
        corner(q, 2, norm(add(d, u, 1, v, -1)), 1, 1);
        corner(q, 3, norm(add(d, u, 1, v, 1)), 1, 0);
        q.argb = argb;
    }

    // ------------------------------------------------------------------ northern lights

    private static void aurora(List<Quad> out, float vis, double t) {
        // a faint green glow over the northern sky, under the curtains
        int segs0 = 16;
        for (int s = 0; s < segs0; s++) {
            double a0 = Math.toRadians(-110) + Math.toRadians(220) * s / segs0, a1 = Math.toRadians(-110) + Math.toRadians(220) * (s + 1) / segs0;
            double x = (s + 0.5) / segs0;
            float k = (float) Math.sin(Math.PI * x);
            Quad q = quad(tex("horizon.png"));
            corner(q, 0, dir(a0, Math.toRadians(50)), 0, 0);
            corner(q, 1, dir(a0, Math.toRadians(-2)), 0, 1);
            corner(q, 2, dir(a1, Math.toRadians(-2)), 1, 1);
            corner(q, 3, dir(a1, Math.toRadians(50)), 1, 0);
            q.argb = argb(vis * 0.30f * k * (float) (0.8 + 0.2 * Math.sin(t * 0.2)), 0x2FD787);
        }
        // three curtains low in the north, each rippling on its own
        double[][] curtains = {
            {Math.toRadians(-95), Math.toRadians(90), 8, 34, 4.4, 0.6},
            {Math.toRadians(-40), Math.toRadians(110), 16, 52, 2.1, 0.8},
            {Math.toRadians(-70), Math.toRadians(150), 10, 46, 0.0, 1.0},
        };
        int segs = 64;
        for (double[] c : curtains) {
            double start = c[0], span = c[1], low = Math.toRadians(c[2]), high = Math.toRadians(c[3]), ph = c[4];
            float pulse = (float) (0.7 + 0.3 * Math.sin(t * 0.35 + ph));
            for (int s = 0; s < segs; s++) {
                double x0 = s / (double) segs, x1 = (s + 1) / (double) segs;
                double[] p0b = curtainPoint(start, span, low, x0, t, ph, false);
                double[] p0t = curtainPoint(start, span, high, x0, t, ph, true);
                double[] p1b = curtainPoint(start, span, low, x1, t, ph, false);
                double[] p1t = curtainPoint(start, span, high, x1, t, ph, true);
                // fade at the curtain's two ends; rays drift sideways; bright patches race along it
                double xm = (x0 + x1) / 2;
                float edge = (float) Math.min(1, Math.min(x0, 1 - x1) / 0.12);
                float wave = (float) (0.55 + 0.30 * Math.sin(xm * 11 - t * 0.9 + ph) + 0.15 * Math.sin(xm * 29 + t * 1.7 + ph * 3));
                double u0 = x0 * 1.6 + t * 0.012 + ph, u1 = x1 * 1.6 + t * 0.012 + ph;
                strip(tex("aurora.png"), p0t, p0b, p1b, p1t, u0, u1, 0f, 1f, argb(vis * pulse * edge * wave * (float) c[5] * 1.25f, 0xFFFFFF));
            }
        }
    }

    private static double[] curtainPoint(double start, double span, double el, double x, double t, double ph, boolean top) {
        // the curtain folds: its path wanders in azimuth and height, slowly
        double az = start + span * x + 0.10 * Math.sin(x * 7 + t * 0.22 + ph) + 0.04 * Math.sin(x * 19 - t * 0.5 + ph * 2);
        double lift = Math.toRadians(7) * Math.sin(x * 4.3 + t * 0.16 + ph) + Math.toRadians(2.5) * Math.sin(x * 13 - t * 0.4 + ph)
                + (top ? Math.toRadians(8) * Math.sin(x * 2.7 - t * 0.11 + ph) : 0);
        return dir(az, el + lift);
    }

    // ------------------------------------------------------------------ meteors and shooting stars

    private static final class Meteor {
        double[] from, to;
        long start;
        long life;
        float bright;
        int color;
        boolean fireball;   // a bright one that leaves a glowing trail behind for a few seconds
    }

    private static final List<Meteor> trains = new ArrayList<Meteor>();
    /** Real meteor colours: mostly white, some green (magnesium), some orange (sodium), a few blue. */
    private static final int[] METEOR_COLORS = {0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xC8FFD8, 0xFFDDB0, 0xD8E4FF};

    private static final List<Meteor> meteors = new ArrayList<Meteor>();
    private static long nextMeteor;
    private static final Random rnd = new Random();

    private static void meteors(float vis, long ms, boolean shower) {
        if (ms >= nextMeteor) {
            Meteor m = new Meteor();
            // a shower all comes from one point: the Perseids' radiant in Perseus (RA 3h12m, Dec +58°)
            double[] radiant = now != null ? now.local(Math.toRadians(48), Math.toRadians(58)) : dir(Math.toRadians(40), Math.toRadians(62));
            if (radiant[1] < 0.15) {
                radiant = dir(Math.toRadians(40), Math.toRadians(35));
            }
            double[] start;
            if (shower) {
                start = norm(add(radiant, tangents(radiant)[0], (rnd.nextDouble() - 0.5) * 0.9, tangents(radiant)[1], (rnd.nextDouble() - 0.5) * 0.9));
            } else {
                start = dir(rnd.nextDouble() * Math.PI * 2, Math.toRadians(30 + rnd.nextDouble() * 45));
            }
            double[] away = shower ? norm(new double[] {start[0] - radiant[0], start[1] - radiant[1], start[2] - radiant[2]})
                    : norm(new double[] {rnd.nextDouble() - 0.5, -0.4 - rnd.nextDouble() * 0.4, rnd.nextDouble() - 0.5});
            double len = shower ? 0.25 + rnd.nextDouble() * 0.35 : 0.45 + rnd.nextDouble() * 0.35;
            m.from = start;
            m.to = norm(add(start, away, len, away, 0));
            m.start = ms;
            m.life = shower ? 500 + rnd.nextInt(700) : 900 + rnd.nextInt(700);
            m.bright = shower ? 0.6f + rnd.nextFloat() * 0.4f : 1f;
            m.color = METEOR_COLORS[rnd.nextInt(METEOR_COLORS.length)];
            m.fireball = rnd.nextFloat() < (shower ? 0.08f : 0.25f);
            if (m.fireball) {
                m.bright = 1f;
                m.life += 300;
            }
            meteors.add(m);
            long gap = shower ? 120 + rnd.nextInt(650) : 2500 + rnd.nextInt(6000);
            nextMeteor = ms + Math.round(gap / Math.max(0.2f, speed));
        }
        for (int i = meteors.size() - 1; i >= 0; i--) {
            Meteor m = meteors.get(i);
            float k = (ms - m.start) / (float) m.life;
            if (k >= 1f || k < 0f) {
                if (m.fireball && k >= 1f) {   // its trail lingers
                    m.start = ms;
                    m.life = 3500 + rnd.nextInt(2500);
                    trains.add(m);
                }
                meteors.remove(i);
                continue;
            }
            // head moves from `from` to `to`; the tail trails behind, longest mid-flight
            double head = k, tail = Math.max(0, k - 0.35 - 0.2 * Math.sin(Math.PI * k));
            double[] h = norm(lerp(m.from, m.to, head)), tl = norm(lerp(m.from, m.to, tail));
            float fade = (float) (Math.sin(Math.PI * Math.min(1, k * 1.15)));
            double width = Math.toRadians(m.fireball ? 1.5 : 0.9);
            streak(quad(tex("meteor.png")), tl, h, width, argb(vis * fade * m.bright, m.color));
            if (m.fireball) {   // a flare round its head
                billboard(quad(tex("star.png")), h, Math.toRadians(3.0 + 2.0 * Math.sin(Math.PI * k)), k * 3, argb(vis * fade * 0.9f, m.color));
            }
        }
        for (int i = trains.size() - 1; i >= 0; i--) {
            Meteor m = trains.get(i);
            float k = (ms - m.start) / (float) m.life;
            if (k >= 1f || k < 0f) {
                trains.remove(i);
                continue;
            }
            float a = (float) Math.pow(1 - k, 1.6) * 0.55f;
            streak(quad(tex("train.png")), m.from, m.to, Math.toRadians(0.7 + 2.2 * k), argb(vis * a, 0xB8FFD0));
        }
        if (trains.size() > 12) {
            trains.subList(0, trains.size() - 12).clear();
        }
        if (meteors.size() > 40) {
            meteors.subList(0, meteors.size() - 40).clear();
        }
    }

    private static double[] lerp(double[] a, double[] b, double t) {
        return new double[] {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    /** A streak from tail to head, `width` radians wide, facing you. */
    private static void streak(Quad q, double[] tail, double[] head, double width, int argb) {
        double[] mid = norm(lerp(tail, head, 0.5));
        double[] along = norm(new double[] {head[0] - tail[0], head[1] - tail[1], head[2] - tail[2]});
        double[] side = norm(cross(mid, along));
        double w = width / 2;
        corner(q, 0, norm(add(tail, side, w, side, 0)), 0, 0);
        corner(q, 1, norm(add(tail, side, -w, side, 0)), 0, 1);
        corner(q, 2, norm(add(head, side, -w, side, 0)), 1, 1);
        corner(q, 3, norm(add(head, side, w, side, 0)), 1, 0);
        q.argb = argb;
    }

    // ------------------------------------------------------------------ the black hole
    // blackhole.png is a 4 x 3 grid of frames from a ray-traced simulation (tools/blackhole_raytrace.py):
    // one third of a turn of the swirling disk, looping. Frames are cross-faded for smooth motion.

    static final int BH_FRAMES = 12, BH_COLS = 4, BH_ROWS = 3;

    private static void blackHole(float vis, double t) {
        double[] c = dir(Math.toRadians(200), Math.toRadians(30));
        double size = Math.toRadians(46);          // the whole frame, edge to edge
        double pos = (t * 1.25) % BH_FRAMES;       // about 10 seconds a loop
        int f0 = (int) Math.floor(pos), f1 = (f0 + 1) % BH_FRAMES;
        float w = (float) (pos - f0);
        // a slight tilt, so the disk isn't perfectly level with the horizon
        double rot = Math.toRadians(-8);
        frame(c, size, rot, f0, vis);
        if (w > 0.01f) {
            frame(c, size, rot, f1, vis * w);
        }
    }

    private static void frame(double[] c, double size, double rot, int f, float a) {
        Quad q = quad(tex("blackhole.png"));
        billboard(q, c, size, rot, argb(a, 0xFFFFFF));
        float u0 = (f % BH_COLS) / (float) BH_COLS, v0 = (f / BH_COLS) / (float) BH_ROWS;
        float du = 1f / BH_COLS, dv = 1f / BH_ROWS;
        for (int i = 0; i < 4; i++) {
            q.uv[i * 2] = u0 + q.uv[i * 2] * du;
            q.uv[i * 2 + 1] = v0 + q.uv[i * 2 + 1] * dv;
        }
    }
}
