package net.peregrine.client.core.skeleton;

import java.util.ArrayList;
import java.util.List;

import static net.peregrine.client.core.skeleton.Sdf.ball;
import static net.peregrine.client.core.skeleton.Sdf.egg;
import static net.peregrine.client.core.skeleton.Sdf.ellipsoid;
import static net.peregrine.client.core.skeleton.Sdf.norm;
import static net.peregrine.client.core.skeleton.Sdf.rod;

/**
 * A human skeleton made to fit a Minecraft player: one piece for each of the player model's
 * parts (head, body, arms, legs), in that part's own space (Minecraft pixels, y down, the front
 * toward -z, the player's right toward -x), so each piece turns exactly like the part it
 * replaces. Shapes follow real anatomy: skull with eye sockets, nose opening, cheekbones, teeth
 * and a jaw; a curved spine of separate vertebrae; twelve pairs of ribs with cartilage to the
 * breastbone; collarbones and shoulder blades; a pelvis with its openings; and long bones with
 * proper ends, kneecaps, hands and feet.
 */
public final class Bones {

    public static final int BONE = 0xFFF2EEE6, TOOTH = 0xFFFFFFFF, CARTILAGE = 0xFFC9CDD2, DARK = 0xFF8A8478;

    /** Detail levels: grid cell (pixels), sides of tubes, smoothing of tube paths. */
    static final float[] CELL = {0.15f, 0.27f, 0.45f};
    static final int[] AROUND = {6, 5, 4};
    static final int[] SMOOTH = {2, 1, 0};

    static Sdf.Node lastBody;

    private Bones() {
    }

    // ------------------------------------------------------------------ head

    /** The skull, jaw and neck vertebrae, around the neck pivot. */
    public static Geo head(int lod) {
        List<Sdf.Node> skull = new ArrayList<Sdf.Node>();
        // the braincase: an egg longer front to back, a little flat at the sides
        skull.add(egg(0f, -5.05f, 0.45f, 2.1f, 2.25f, 2.7f));
        skull.add(egg(0f, -4.55f, 1.3f, 1.95f, 1.75f, 1.9f));                 // the back of the head
        skull.add(egg(0f, -4.75f, -1.95f, 1.75f, 0.42f, 0.55f));              // brow ridge
        // the face: a flat upper jaw under the nose, cheekbones and their arches back to the ears
        skull.add(egg(0f, -2.6f, -1.45f, 0.98f, 0.55f, 0.72f));
        skull.add(rod(-0.75f, -3.35f, -1.95f, 0.75f, -3.35f, -1.95f, 0.32f, 0.32f));          // between the eyes and the teeth
        skull.add(rod(0f, -4.2f, -2.05f, 0f, -3.55f, -2.2f, 0.2f, 0.16f));                    // bridge of the nose
        for (int s = -1; s <= 1; s += 2) {
            skull.add(egg(s * 1.42f, -3.4f, -1.4f, 0.48f, 0.42f, 0.62f));
            skull.add(rod(s * 1.62f, -3.3f, -1.05f, s * 1.98f, -3.15f, 0.55f, 0.2f, 0.17f));
            skull.add(egg(s * 1.9f, -2.75f, 0.95f, 0.3f, 0.42f, 0.38f));      // mastoid behind the ear
            skull.add(rod(s * 0.55f, -2.6f, -1.95f, s * 1.3f, -3.15f, -1.55f, 0.28f, 0.3f));   // upper jaw up to the cheek
            skull.add(rod(s * 0.95f, -4.75f, -2.05f, s * 1.55f, -3.7f, -1.6f, 0.2f, 0.24f));   // outer rim of the eye
        }
        // the gum ridge the upper teeth grow from
        for (int i = 0; i < 6; i++) {
            float a0 = (float) Math.toRadians(-80 + i * 32), a1 = (float) Math.toRadians(-80 + (i + 1) * 32);
            skull.add(rod((float) Math.sin(a0) * 1.0f, -2.12f, -1.2f - (float) Math.cos(a0) * 0.9f,
                    (float) Math.sin(a1) * 1.0f, -2.12f, -1.2f - (float) Math.cos(a1) * 0.9f, 0.22f, 0.22f));
        }
        // the base of the skull, where it sits on the spine
        skull.add(egg(0f, -3.05f, 0.75f, 1.35f, 0.7f, 1.35f));
        List<Sdf.Node> holes = new ArrayList<Sdf.Node>();
        for (int s = -1; s <= 1; s += 2) {
            holes.add(egg(s * 0.85f, -4.02f, -2.3f, 0.7f, 0.64f, 0.8f).tint(0xFFB8AF9E));     // eye sockets, deep
            holes.add(rod(s * 0.82f, -4.0f, -2.1f, s * 0.6f, -4.0f, -0.6f, 0.55f, 0.25f).tint(0xFF6E665A));
            holes.add(ball(s * 2.12f, -3.25f, 0.45f, 0.16f));                 // ear openings
        }
        holes.add(egg(0f, -3.05f, -2.25f, 0.34f, 0.58f, 0.9f).tint(0xFF8C8374));               // the nose opening
        holes.add(egg(0f, -3.4f, -2.3f, 0.22f, 0.32f, 0.8f).tint(0xFF8C8374));
        Sdf.Node cranium = Sdf.cut(Sdf.union(0.45f, skull), 0.15f, holes.toArray(new Sdf.Node[0]));

        // the jaw: chin, the U of its body, then up to the joint in front of the ear
        List<Sdf.Node> jaw = new ArrayList<Sdf.Node>();
        jaw.add(egg(0f, -0.78f, -1.92f, 0.55f, 0.36f, 0.3f));
        for (int s = -1; s <= 1; s += 2) {
            jaw.add(rod(s * 0.35f, -0.88f, -1.9f, s * 1.12f, -1.0f, -1.12f, 0.33f, 0.3f));
            jaw.add(rod(s * 1.12f, -1.0f, -1.12f, s * 1.62f, -1.15f, 0.15f, 0.3f, 0.28f));
            jaw.add(ellipsoid(s * 1.68f, -2.15f, 0.25f, Sdf.X, norm(0, 1, 0.15f), norm(0, -0.15f, 1), 0.14f, 0.85f, 0.42f));
            jaw.add(egg(s * 1.75f, -2.95f, 0.5f, 0.2f, 0.18f, 0.24f));        // the joint
        }
        Sdf.Node mandible = Sdf.union(0.3f, jaw);

        // neck: seven vertebrae down to the shoulders
        List<Sdf.Node> neck = new ArrayList<Sdf.Node>();
        for (int i = 0; i < 6; i++) {
            float y = -2.25f + i * 0.4f, z = 0.75f + i * 0.02f;
            neck.add(egg(0f, y, z, 0.42f, 0.2f, 0.36f));
            neck.add(rod(0f, y, z + 0.2f, 0f, y + 0.12f, z + 0.75f, 0.11f, 0.08f));
            neck.add(rod(-0.7f, y + 0.02f, z + 0.05f, 0.7f, y + 0.02f, z + 0.05f, 0.08f, 0.08f));
        }
        Sdf.Node spine = Sdf.union(0.08f, neck);

        Sdf.Node all = Sdf.union(0.02f, cranium, mandible, spine);
        Geo g = new Geo();
        Nets.mesh(all, new float[] {-2.6f, -7.6f, -2.8f}, new float[] {2.6f, 0.2f, 3.5f}, CELL[lod] * 1.15f, 1f, g);
        // teeth: upper and lower rows round the front of the jaws, growing out of the gums
        int ta = Math.max(4, AROUND[lod]);
        for (int row = 0; row < 2; row++) {
            float rad = row == 0 ? 1.0f : 0.92f;
            float root = row == 0 ? -1.98f : -1.12f, tip = row == 0 ? -1.52f : -1.5f;
            int count = lod == 2 ? 8 : 12;
            for (int i = 0; i < count; i++) {
                float a = (float) Math.toRadians(-82 + i * (164f / (count - 1)));
                float x = (float) Math.sin(a) * rad, z = -1.2f - (float) Math.cos(a) * (rad - 0.08f);
                int edge = Math.min(i, count - 1 - i);
                boolean molar = edge < 3, canine = edge == 3;
                float w = molar ? 0.16f : canine ? 0.12f : 0.1f;
                float t2 = tip + (row == 0 ? 1 : -1) * (canine ? 0.06f : 0f);
                float mid = (root + t2) / 2;
                // the cross-section runs along the jaw: flatter front to back for the front teeth
                float[] along = {(float) Math.cos(a), 0, (float) Math.sin(a)};
                Tube.build(g, new float[][] {{x, root, z, w * 0.9f}, {x, mid, z, w}, {x, t2, z, w * 0.8f}},
                        null, TOOTH, ta, molar ? 0.85f : 0.6f, along, 0);
            }
        }
        return g.pack();
    }

    // ------------------------------------------------------------------ body

    /** Spine, ribs, breastbone, collarbones, shoulder blades and pelvis, from the neck pivot down. */
    public static Geo body(int lod) {
        List<Sdf.Node> parts = new ArrayList<Sdf.Node>();
        // the spine: twelve chest and five lower-back vertebrae on a gentle S curve
        for (int i = 0; i < 17; i++) {
            float y = 0.25f + i * (10.4f / 16f);
            float z = spineZ(y);
            boolean lumbar = i >= 12;
            float r = lumbar ? 0.62f : 0.42f + i * 0.012f;
            parts.add(egg(0f, y, z - 0.15f, r, lumbar ? 0.24f : 0.2f, r * 0.85f));
            // the spike at the back and the wings to the sides
            parts.add(rod(0f, y, z + 0.25f, 0f, y + (lumbar ? 0.15f : 0.45f), z + (lumbar ? 0.95f : 1.0f), 0.13f, 0.09f));
            float wing = lumbar ? 1.05f : 0.85f;
            parts.add(rod(-wing, y - 0.05f, z + 0.3f, wing, y - 0.05f, z + 0.3f, 0.1f, 0.1f));
        }
        // breastbone: the wide top, the long body, the little tip
        parts.add(ellipsoid(0f, 1.05f, -1.82f, Sdf.X, norm(0, 1, 0.12f), norm(0, -0.12f, 1), 0.72f, 0.6f, 0.17f));
        parts.add(ellipsoid(0f, 3.2f, -1.95f, Sdf.X, norm(0, 1, 0.1f), norm(0, -0.1f, 1), 0.48f, 1.75f, 0.16f));
        parts.add(rod(0f, 4.9f, -1.9f, 0f, 5.5f, -1.75f, 0.14f, 0.06f));
        for (int s = -1; s <= 1; s += 2) {
            // shoulder blade: a thin triangle on the back, its ridge, and the socket for the arm
            float[] top = {s * 1.35f, 0.55f, 1.7f}, low = {s * 1.9f, 4.65f, 1.55f}, out = {s * 3.65f, 1.05f, 1.1f};
            parts.add(Sdf.plate(top, low, out, 0.16f));
            parts.add(rod(s * 1.55f, 1.45f, 1.85f, s * 3.85f, 0.35f, 0.9f, 0.12f, 0.16f));
            parts.add(egg(s * 3.75f, 1.05f, 0.75f, 0.25f, 0.42f, 0.3f));
            // pelvis: the hip bone's wing, its rim, the socket, and the rings round the opening below
            parts.add(ellipsoid(s * 2.35f, 10.85f, 0.3f, norm(s * 0.5f, 0.35f, -0.8f), norm(0, 1, 0.25f),
                    norm(s * 0.85f, 0.05f, 0.5f), 1.5f, 1.1f, 0.26f));
            parts.add(Sdf.rods(new float[][] {
                {s * 0.95f, 10.0f, 1.2f, 0.16f}, {s * 2.15f, 9.75f, 1.0f, 0.2f}, {s * 3.15f, 10.0f, 0.25f, 0.2f},
                {s * 3.2f, 10.45f, -0.55f, 0.18f}, {s * 2.6f, 11.0f, -0.85f, 0.14f}}));
            parts.add(egg(s * 1.95f, 12.0f, 0.05f, 0.72f, 0.65f, 0.72f));
            parts.add(rod(s * 1.75f, 12.25f, -0.4f, s * 0.28f, 12.65f, -1.05f, 0.3f, 0.24f));      // pubic bone
            parts.add(rod(s * 1.85f, 12.55f, 0.25f, s * 1.45f, 13.5f, 0.5f, 0.33f, 0.3f));         // sit bone
            parts.add(rod(s * 1.45f, 13.5f, 0.5f, s * 0.35f, 13.15f, -0.85f, 0.22f, 0.18f));
        }
        // the sacrum: a curved wedge closing the pelvis at the back, and the tailbone
        parts.add(ellipsoid(0f, 11.4f, 1.1f, Sdf.X, norm(0, 1, 0.3f), norm(0, -0.3f, 1), 1.15f, 1.35f, 0.3f));
        parts.add(rod(0f, 12.6f, 1.4f, 0f, 13.35f, 1.15f, 0.2f, 0.07f));
        Sdf.Node solid = Sdf.union(0.22f, parts);

        // the shoulder sockets and hip sockets are cupped
        List<Sdf.Node> holes = new ArrayList<Sdf.Node>();
        for (int s = -1; s <= 1; s += 2) {
            holes.add(ball(s * 2.25f, 12.0f, 0.05f, 0.55f));
            holes.add(ball(s * 4.05f, 1.1f, 0.65f, 0.3f));
            holes.add(egg(s * 1.15f, 12.95f, -0.15f, 0.35f, 0.4f, 0.55f));    // the opening in each hip bone
        }
        Sdf.Node body = Sdf.cut(solid, 0.1f, holes.toArray(new Sdf.Node[0]));
        lastBody = body;
        Geo g = new Geo();
        Nets.mesh(body, new float[] {-4.3f, -0.4f, -2.3f}, new float[] {4.3f, 13.8f, 2.4f}, CELL[lod] * 1.35f, 1f, g);

        // ribs and collarbones are thin: tubes
        int around = AROUND[lod], smooth = SMOOTH[lod];
        float[] widths = {1.55f, 2.25f, 2.7f, 3.0f, 3.2f, 3.3f, 3.32f, 3.25f, 3.15f, 3.0f, 2.75f, 2.45f};
        for (int i = 0; i < 12; i++) {
            float y0 = 0.45f + i * 0.6f;
            float a = widths[i];
            float drop = 1.3f + i * 0.07f;
            boolean floating = i >= 10, falseRib = i >= 7 && i < 10;
            float end = floating ? 1.75f + (i - 10) * 0.15f : falseRib ? 2.55f - (i - 7) * 0.08f : 2.85f;
            int n = 9;
            for (int s = -1; s <= 1; s += 2) {
                float[][] pts = new float[n][4];
                int[] cols = new int[n];
                for (int k = 0; k < n; k++) {
                    float th = 0.22f + (end - 0.22f) * k / (n - 1f);
                    float x = s * (0.45f + (a - 0.45f) * (float) Math.sin(Math.min(Math.PI / 2, th * 1.05f))
                            * (th > Math.PI / 2 ? (float) Math.sin(th) : 1f));
                    float z = -0.3f + 1.45f * (float) Math.cos(th) + (k == 0 ? spineZ(y0) - 1.15f : 0);
                    float y = y0 + drop * (float) Math.pow(th / Math.PI, 1.4);
                    pts[k] = new float[] {x, y, z, k == n - 1 ? 0.12f : 0.16f};
                    cols[k] = !floating && k >= n - 3 ? CARTILAGE : BONE;
                }
                if (!floating) {   // cartilage on to the breastbone (true ribs) or the rib above (false ribs)
                    float[] last = pts[n - 1];
                    float ty = falseRib ? last[1] - 0.6f : Math.min(4.8f, 0.9f + i * 0.62f);
                    float tx = falseRib ? s * (float) Math.max(0.7, Math.abs(last[0]) - 0.6) : s * 0.42f;
                    pts = append(pts, new float[] {(last[0] + tx) / 2, (last[1] + ty) / 2 + 0.1f, -1.85f, 0.12f});
                    pts = append(pts, new float[] {tx, ty, -1.9f, 0.11f});
                    cols = append(cols, CARTILAGE);
                    cols = append(cols, CARTILAGE);
                }
                Tube.build(g, pts, cols, BONE, around, 0.5f, new float[] {0, 1, 0}, smooth);
            }
        }
        for (int s = -1; s <= 1; s += 2) {
            Tube.build(g, new float[][] {
                {s * 0.45f, 0.6f, -1.75f, 0.24f}, {s * 1.6f, 0.38f, -1.9f, 0.19f}, {s * 2.7f, 0.25f, -1.45f, 0.18f},
                {s * 3.5f, 0.12f, -0.6f, 0.2f}, {s * 3.9f, 0.08f, 0.1f, 0.24f}},
                    null, BONE, around, 0.8f, new float[] {0, 1, 0}, smooth);
        }
        return g.pack();
    }

    /** Where the spine runs (z) at a height: back curve in the chest, forward in the lower back. */
    static float spineZ(float y) {
        return 1.0f + 0.22f * (float) Math.sin((y + 0.5f) / 7.5f * Math.PI) - 0.25f * smooth(7.5f, 11f, y);
    }

    private static float smooth(float a, float b, float x) {
        float t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    // ------------------------------------------------------------------ arms

    /**
     * The right arm in its part's space (shoulder joint at the top): upper arm, the two forearm
     * bones, wrist and a hand with its fingers gently curled. c: the middle of the arm box
     * (-1 for a classic arm, -0.5 slim); the left arm is this mirrored.
     */
    public static Geo rightArm(int lod, boolean slim) {
        float c = slim ? -0.5f : -1f, k = slim ? 0.85f : 1f;   // slim arms: thinner bones
        float in = 1f;                                          // toward the body (+x for the right arm)
        List<Sdf.Node> p = new ArrayList<Sdf.Node>();
        p.add(ball(c + in * 0.2f, -1.25f, 0.1f, 0.55f * k));                                 // head of the upper arm
        p.add(egg(c - in * 0.25f, -0.95f, -0.15f, 0.38f * k, 0.4f, 0.35f * k));              // its knob
        p.add(rod(c, -0.8f, 0.05f, c + in * 0.05f, 3.8f, 0.1f, 0.34f * k, 0.3f * k));
        p.add(egg(c, 4.05f, 0.05f, 0.62f * k, 0.32f, 0.4f * k));                             // elbow end
        p.add(egg(c + in * 0.1f, 4.2f, 0.45f, 0.28f * k, 0.36f, 0.26f * k));                 // the elbow point
        p.add(rod(c + in * 0.2f, 4.4f, 0.25f, c + in * 0.25f, 8.55f, 0.12f, 0.22f * k, 0.15f * k));   // ulna
        p.add(rod(c - in * 0.2f, 4.5f, -0.05f, c - in * 0.3f, 8.55f, -0.18f, 0.17f * k, 0.27f * k));  // radius
        // wrist: two rows of small bones
        for (int i = 0; i < 4; i++) {
            float z = -0.36f + i * 0.24f;
            p.add(egg(c, 8.85f, z, 0.24f * k, 0.17f, 0.15f));
            p.add(egg(c + in * 0.03f, 9.2f, z * 1.1f, 0.22f * k, 0.17f, 0.15f));
        }
        Sdf.Node arm = Sdf.union(0.16f, p);
        Geo g = new Geo();
        Nets.mesh(arm, new float[] {c - 1.2f, -2f, -1.2f}, new float[] {c + 1.2f, 9.5f, 1.2f}, CELL[lod] * 1.2f, 1f, g);
        // the hand: palm bones fanning out from the wrist, fingers curled toward the palm (which
        // faces the body), the thumb forward
        int around = Math.max(4, AROUND[lod] - 1), smooth = SMOOTH[lod];
        float[] fz = {-0.46f, -0.16f, 0.14f, 0.42f};
        float[] len = {1.0f, 1.1f, 1.04f, 0.82f};
        for (int f = 0; f < 4; f++) {
            float z0 = fz[f] * 0.6f, z1 = fz[f];
            float L = len[f];
            float r = (f == 3 ? 0.095f : 0.11f) * k;
            float ky = 9.35f + 0.85f * L;                      // knuckle
            Tube.build(g, new float[][] {
                {c, 9.3f, z0, r * 1.15f}, {c, (9.3f + ky) / 2, (z0 + z1) / 2, r * 0.85f}, {c, ky, z1, r * 1.3f},     // palm bone and knuckle
                {c + in * 0.12f, ky + 0.42f * L, z1, r * 0.95f}, {c + in * 0.2f, ky + 0.48f * L, z1, r * 1.05f},  // first bone, middle joint
                {c + in * 0.45f, ky + 0.66f * L, z1, r * 0.85f}, {c + in * 0.52f, ky + 0.68f * L, z1, r * 0.9f},  // second bone
                {c + in * 0.72f, ky + 0.62f * L, z1, r * 0.75f}, {c + in * 0.82f, ky + 0.55f * L, z1, r * 0.55f}},
                    null, BONE, around, 1f, new float[] {0, 0, 1}, smooth);
        }
        Tube.build(g, new float[][] {
            {c - in * 0.05f, 9.15f, -0.45f, 0.14f * k}, {c, 9.5f, -0.72f, 0.11f * k}, {c + in * 0.08f, 9.85f, -0.9f, 0.13f * k},
            {c + in * 0.25f, 10.2f, -0.95f, 0.1f * k}, {c + in * 0.42f, 10.45f, -0.88f, 0.09f * k}, {c + in * 0.55f, 10.55f, -0.78f, 0.07f * k}},
                null, BONE, around, 1f, new float[] {1, 0, 0}, smooth);
        return g.pack();
    }

    // ------------------------------------------------------------------ legs

    /** The right leg from the hip joint down: thigh bone, kneecap, shin bones and the foot. */
    public static Geo rightLeg(int lod) {
        float in = 1f;   // toward the middle of the body
        List<Sdf.Node> p = new ArrayList<Sdf.Node>();
        p.add(ball(in * 0.2f, 0.02f, 0.05f, 0.52f));                                  // head of the thigh bone, in the hip socket
        p.add(rod(in * 0.15f, 0.1f, 0.05f, -in * 0.5f, 0.6f, 0.05f, 0.3f, 0.34f));   // its neck
        p.add(egg(-in * 0.8f, 0.4f, 0.15f, 0.42f, 0.55f, 0.45f));                    // the big knob on the outside
        p.add(rod(-in * 0.55f, 0.85f, 0.05f, 0f, 5.45f, -0.05f, 0.36f, 0.33f));      // the shaft
        p.add(egg(in * 0.38f, 5.75f, 0.1f, 0.42f, 0.42f, 0.6f));                     // knee ends
        p.add(egg(-in * 0.38f, 5.75f, 0.1f, 0.42f, 0.42f, 0.6f));
        p.add(ellipsoid(0f, 5.85f, -0.72f, Sdf.X, norm(0, 1, 0.2f), norm(0, -0.2f, 1), 0.4f, 0.48f, 0.17f).tint(BONE));   // kneecap
        p.add(egg(0f, 6.4f, 0.0f, 0.82f, 0.28f, 0.66f));                             // top of the shin bone
        p.add(rod(0.05f, 6.6f, -0.12f, in * 0.1f, 10.8f, 0f, 0.33f, 0.27f));
        p.add(egg(in * 0.35f, 11.05f, 0f, 0.3f, 0.3f, 0.32f));                       // inner ankle knob
        p.add(rod(-in * 0.58f, 6.55f, 0.25f, -in * 0.55f, 10.95f, 0.15f, 0.17f, 0.14f));   // fibula
        p.add(egg(-in * 0.58f, 11.15f, 0.12f, 0.21f, 0.32f, 0.24f));                 // outer ankle knob
        // foot: ankle bone, heel and the arch
        p.add(egg(0f, 11.45f, 0.05f, 0.44f, 0.3f, 0.5f));
        p.add(egg(-in * 0.05f, 11.72f, 0.6f, 0.38f, 0.3f, 0.55f));
        p.add(egg(in * 0.1f, 11.55f, -0.55f, 0.45f, 0.25f, 0.4f));
        Sdf.Node leg = Sdf.union(0.2f, p);
        Geo g = new Geo();
        Nets.mesh(leg, new float[] {-1.5f, -0.7f, -1.4f}, new float[] {1.0f, 12.1f, 1.3f}, CELL[lod] * 1.3f, 1f, g);
        int around = Math.max(4, AROUND[lod] - 2), smooth = SMOOTH[lod];
        for (int t = 0; t < 5; t++) {
            float x0 = in * (0.35f - t * 0.17f), x1 = in * (0.55f - t * 0.27f);
            float r = t == 0 ? 0.17f : 0.11f;
            float L = t == 0 ? 1f : 0.9f - t * 0.06f;
            Tube.build(g, new float[][] {
                {x0, 11.6f, -0.65f, r}, {x1 * 0.9f, 11.78f, -1.15f, r * 0.9f}, {x1, 11.88f, -1.4f - 0.25f * L, r * 1.05f},
                {x1, 11.9f, -1.6f - 0.4f * L, r * 0.85f}, {x1, 11.92f, -1.65f - 0.55f * L, r * 0.75f}},
                    null, BONE, around, 1f, new float[] {0, 1, 0}, smooth);
        }
        return g.pack();
    }

    private static float[][] append(float[][] a, float[] p) {
        float[][] b = java.util.Arrays.copyOf(a, a.length + 1);
        b[a.length] = p;
        return b;
    }

    private static int[] append(int[] a, int v) {
        int[] b = java.util.Arrays.copyOf(a, a.length + 1);
        b[a.length] = v;
        return b;
    }
}
