package net.peregrine.client.core.knife;

/**
 * How polished metal catches the light: it mirrors its surroundings, so the colour seen depends
 * on where the surface faces. Peregrine uses a soft "studio" around the viewer (bright overhead,
 * a light band at eye level, darker ground, two soft lamps) tinted by the sky so knives look
 * right by day and at night. Directions are in view space (x right, y up, -z ahead).
 */
public final class Shine {

    private Shine() {
    }

    /** Scene brightness and sky tint (set each frame from the world: 0..1, RGB). */
    public static volatile float daylight = 1f;
    public static volatile float tintR = 1f, tintG = 1f, tintB = 1f;
    /** How far the view is tilted up (radians): reflections stay level with the world. */
    private static float cp = 1f, sp = 0f;

    public static void setPitch(float radians) {
        cp = (float) Math.cos(radians);
        sp = (float) Math.sin(radians);
    }

    /** The light reflected toward the eye from direction r (unit, view space): RGB 0..~1.6. */
    public static float[] env(float rx, float ry, float rz) {
        // turn the view-space direction back by the camera's pitch so "up" is the world's up
        float c = cp, s = sp;
        float wy = ry * c + rz * s;
        float wz = -ry * s + rz * c;
        float wx = rx;
        float up = wy;
        // the sky dome, a bright band near the horizon, dark ground
        float sky = up > 0 ? 0.55f + 0.45f * (float) Math.pow(up, 0.7) : 0.10f + 0.08f * (1 + up);
        float band = (float) Math.exp(-Math.pow((up - 0.08f) / 0.10f, 2)) * 0.55f;
        // two soft lamps: high front-left, and right
        float l1 = lamp(wx, wy, wz, -0.45f, 0.70f, -0.55f, 0.88f) * 1.25f;
        float l2 = lamp(wx, wy, wz, 0.85f, 0.25f, 0.45f, 0.93f) * 0.8f;
        float k = sky + band + l1 + l2;
        float d = 0.35f + 0.65f * daylight;
        return new float[] {k * d * tintR, k * d * tintG, k * d * (tintB * 1.04f)};
    }

    private static float lamp(float x, float y, float z, float lx, float ly, float lz, float edge) {
        float l = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
        float d = (x * lx + y * ly + z * lz) / l;
        if (d <= edge) {
            return 0f;
        }
        float t = (d - edge) / (1 - edge);
        return t * t * (3 - 2 * t);
    }

    /**
     * The mirror term at a surface point: the reflection of the view direction, looked up in env.
     * pos: view-space position, n: unit normal. Returns RGB.
     */
    public static float[] reflect(float px, float py, float pz, float nx, float ny, float nz) {
        float l = (float) Math.sqrt(px * px + py * py + pz * pz);
        float vx = px / l, vy = py / l, vz = pz / l;    // from the eye to the point
        float d = vx * nx + vy * ny + vz * nz;
        float rx = vx - 2 * d * nx, ry = vy - 2 * d * ny, rz = vz - 2 * d * nz;
        float[] e = env(rx, ry, rz);
        // grazing angles reflect more (Fresnel)
        float f = 1 - Math.abs(d);
        float fres = 0.7f + 0.3f * f * f * f;
        e[0] *= fres;
        e[1] *= fres;
        e[2] *= fres;
        return e;
    }

    /** Soft diffuse light from above-left (what the base colour is multiplied by). */
    public static float diffuse(float nx, float ny, float nz) {
        float wy = ny * cp + nz * sp;
        float wz = -ny * sp + nz * cp;
        float key = Math.max(0f, -0.35f * nx + 0.8f * wy + 0.45f * wz);
        float d = 0.35f + 0.65f * daylight;
        return (0.38f + 0.62f * Math.min(1f, key)) * d;
    }
}
