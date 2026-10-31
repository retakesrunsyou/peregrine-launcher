package net.peregrine.client.core.knife;

import java.util.Random;

/**
 * First-person knives: works out, every frame, where your arm, wrist and knife are (the resting
 * hold plus whatever move is playing) and fills an {@link Out} with the quads to draw. Version
 * adapters only feed it a {@link Frame} and draw what comes out.
 */
public final class KnifeView {

    public static final KnifeView INSTANCE = new KnifeView();

    // ------------------------------------------------------------------ settings (set by the modules)

    public volatile boolean enabled;
    public volatile int knife;                  // KnifeModels index
    public volatile int skin = 1;               // KnifeSkins index
    public volatile int armMode;                // ARM_SHOWN, ARM_HIDDEN, ARM_OFF
    public volatile float size = 1f;
    /** How big your arm looks (1 = Minecraft-sized; smaller sits further away, same spot on screen). */
    public volatile float armSize = 0.72f;
    public volatile float moveX, moveY, moveZ;  // the knife in your hand, pixels
    public volatile float tilt, turn, roll;     // degrees
    public volatile boolean fidgets = true;
    public volatile int custom1 = 0xFF3355, custom2 = 0x3366FF;
    public volatile int inspectKey = 70;        // F
    public volatile int swingFor = 1;           // viewmodel trails: 0 everything, 1 swords, 2 swords and axes

    public static final int ARM_SHOWN = 0, ARM_HIDDEN = 1, ARM_OFF = 2;
    public static final String[] ARM_NAMES = {"Shown", "Hidden, the knife where your hand holds it", "Off (Minecraft's own hold)"};

    // viewmodel effects
    public volatile int fx;                     // FX_*
    public volatile float fxOpacity = 0.45f;
    public volatile float fxGlow = 0.6f;
    public volatile int fxColor = 0x66D9FF;
    public volatile boolean trails;
    public volatile float trailSeconds = 0.14f;

    public static final int FX_OFF = 0, FX_GLASS = 1, FX_HOLOGRAM = 2, FX_GLOW = 3;
    public static final String[] FX_NAMES = {"Off", "Glass", "Hologram", "Glow"};

    /** What the adapter knows about this frame. */
    public static final class Frame {
        public float swing;            // Minecraft's swing progress 0..1 (0 = not swinging)
        public boolean critReady;      // a hit now would be a critical hit
        public float walk;             // distance walked (for the step bob)
        public float moving;           // 0 standing .. 1 walking .. 1.3 sprinting
        public boolean sprinting;
        public boolean onGround = true;
        public float vy;               // up/down speed, blocks per tick
        public boolean leftHanded;
        public boolean slim;
        public String skinTexture;     // the player's skin (64 x 64)
        public float light = 1f;       // how lit the hand is, 0..1
        public float pitch;            // radians, + looking up
    }

    // ------------------------------------------------------------------ state

    private final long t0 = System.nanoTime();
    private final Random random = new Random();
    private KnifeAnims.Anim action;
    private double actionAt;
    private final float[] fading = new float[KnifeAnims.CHANNELS];
    private double fadingAt = -10;
    private KnifeAnims.Anim lastHit, lastInspect, lastIdle;
    private double lastHitAt = -10, nextIdle = 9;
    private int combo;
    private float lastSwing;
    private int shownKnife = -1, shownSkin = -1;
    private boolean wasHolding;

    // trail samples: time, blade base xyz, tip xyz
    private static final int TRAIL = 24;
    private final float[][] trail = new float[TRAIL][7];
    private int trailHead, trailCount;
    private final M4[] ghosts = {new M4(), new M4(), new M4(), new M4()};
    private final double[] ghostAt = new double[4];
    private int ghostHead;

    private KnifeView() {
    }

    /** Previews can drive the clock themselves; the game leaves this null. */
    public volatile java.util.function.DoubleSupplier clock;

    public double now() {
        java.util.function.DoubleSupplier c = clock;
        return c != null ? c.getAsDouble() : (System.nanoTime() - t0) / 1e9;
    }

    /** True while a knife replaces the sword you hold (the adapter then skips Minecraft's hand). */
    public boolean wants(boolean holdingSword) {
        return enabled && holdingSword;
    }

    // ------------------------------------------------------------------ events

    /** You pulled the knife out (or changed knife): the draw flourish. */
    public void equip() {
        start(KnifeModels.get(knife).pinA != null ? KnifeAnims.DRAW_BUTTERFLY : KnifeAnims.DRAW);
        nextIdle = now() + 8 + random.nextFloat() * 6;
    }

    /** The inspect key: one of the four inspects, never the same twice in a row. */
    public void inspect() {
        if (KnifeModels.get(knife).pinA != null && random.nextFloat() < 0.5f && lastInspect != KnifeAnims.FLIPS) {
            lastInspect = KnifeAnims.FLIPS;
        } else {
            lastInspect = pick(KnifeAnims.INSPECTS, lastInspect);
        }
        start(lastInspect);
    }

    private void hit(boolean crit) {
        double t = now();
        if (t - lastHitAt > 1.2) {
            combo = 0;
        }
        lastHitAt = t;
        KnifeAnims.Anim a;
        if (crit) {
            a = lastHit == KnifeAnims.STAB ? KnifeAnims.LUNGE : lastHit == KnifeAnims.LUNGE ? KnifeAnims.STAB
                    : random.nextBoolean() ? KnifeAnims.STAB : KnifeAnims.LUNGE;
        } else if (combo % 3 == 2) {
            a = KnifeAnims.CHOP;
        } else if (random.nextFloat() < 0.22f && lastHit != KnifeAnims.UPPERCUT) {
            a = KnifeAnims.UPPERCUT;
        } else {
            a = lastHit == KnifeAnims.SLASH_FORE ? KnifeAnims.SLASH_BACK : KnifeAnims.SLASH_FORE;
        }
        if (a == lastHit && a == KnifeAnims.CHOP) {
            a = KnifeAnims.SLASH_FORE;
        }
        combo++;
        lastHit = a;
        start(a);
        nextIdle = t + 8 + random.nextFloat() * 6;
    }

    private KnifeAnims.Anim pick(KnifeAnims.Anim[] from, KnifeAnims.Anim not) {
        KnifeAnims.Anim a;
        do {
            a = from[random.nextInt(from.length)];
        } while (a == not && from.length > 1);
        return a;
    }

    private void start(KnifeAnims.Anim a) {
        double t = now();
        // whatever was playing fades out quickly instead of snapping
        java.util.Arrays.fill(fading, 0);
        if (action != null) {
            float f = (float) ((t - actionAt) / action.seconds);
            if (f < 1) {
                action.add(f, 1f, fading);
                for (int ch : new int[] {KnifeAnims.SPIN, KnifeAnims.TWIST, KnifeAnims.LOOP}) {
                    fading[ch] = wrap(fading[ch]);
                }
            }
        }
        fadingAt = t;
        action = a;
        actionAt = t;
    }

    private static float wrap(float deg) {
        deg %= 360f;
        if (deg > 180) {
            deg -= 360;
        } else if (deg < -180) {
            deg += 360;
        }
        return deg;
    }

    // ------------------------------------------------------------------ the frame

    private final float[] ch = new float[KnifeAnims.CHANNELS];
    private final float[] tmp = new float[3], tmpN = new float[3];

    /** Fills out with this frame's arm and knife (out is cleared first). */
    public void build(Frame f, Out out) {
        out.clear();
        double t = now();
        if (shownKnife != knife || shownSkin != skin || !wasHolding) {
            if (shownKnife != -1 || !wasHolding) {
                equip();
            }
            shownKnife = knife;
            shownSkin = skin;
        }
        wasHolding = true;
        // a new swing started: a hit (critical if you were falling)
        if (f.swing > 0 && (lastSwing == 0 || f.swing < lastSwing - 0.15f)) {
            hit(f.critReady);
        }
        lastSwing = f.swing;
        // nothing happening for a while: a little fidget
        if (fidgets && t > nextIdle && (action == null || t - actionAt > action.seconds) && f.moving < 0.1f) {
            lastIdle = pick(KnifeAnims.IDLES, lastIdle);
            start(lastIdle);
            nextIdle = t + 9 + random.nextFloat() * 8;
        } else if (f.moving >= 0.1f && t > nextIdle - 3) {
            nextIdle = t + 3;
        }

        java.util.Arrays.fill(ch, 0);
        if (action != null) {
            float p = (float) ((t - actionAt) / action.seconds);
            if (p >= 1) {
                action = null;
            } else {
                action.add(ease(action, p), 1f, ch);
            }
        }
        float fade = (float) ((t - fadingAt) / 0.12);
        if (fade < 1) {
            float w = 1 - fade;
            w = w * w * (3 - 2 * w);
            for (int i = 0; i < ch.length; i++) {
                ch[i] += fading[i] * w;
            }
        }
        moveBody(f, t);

        Shine.setPitch(f.pitch);
        Shine.daylight = Math.max(0.05f, Math.min(1f, f.light));

        KnifeModels.Knife k = KnifeModels.get(knife);
        hold = k.hold;
        M4 view = f.leftHanded ? M4.mirrorX() : new M4();
        M4 knifeM;
        if (armMode == ARM_OFF) {
            knifeM = minecraftHold(view, k, f.swing);
        } else {
            M4 arm = armMatrix(view);
            M4 hand = handMatrix(arm);
            if (armMode == ARM_SHOWN) {
                drawArm(out, arm, hand, f);
            }
            knifeM = knifeMatrix(hand, k);
        }
        if (ch[KnifeAnims.TOSS] != 0) {
            knifeM = new M4().translate(0, ch[KnifeAnims.TOSS], 0).mul(knifeM);
        }
        drawKnife(out, k, knifeM, f, t);
        if (trails) {
            trailsFor(out, k, knifeM, t);
        }
    }

    /** True while a knife is in your hand on screen. */
    public boolean holding() {
        return wasHolding && enabled;
    }

    /** The inspect key is pressed: true if the knife used it (then Minecraft shouldn't see it). */
    public boolean key(int key) {
        if (key == inspectKey && holding()) {
            inspect();
            return true;
        }
        return false;
    }

    // smoothed movement state
    private float moveS, sprintS, airS, land, landV, lastVy;
    private boolean wasOnGround = true;
    private double lastBodyAt;
    private float stepPhase;

    /**
     * How walking, sprinting, jumping and landing move the arm: a figure-eight sway with your
     * steps, the knife lowered and tipped forward while you sprint, lifted as you rise and pressed
     * down as you fall, a springy dip when you land, and slow breathing when you stand still.
     */
    private void moveBody(Frame f, double t) {
        float dt = (float) Math.max(0.0, Math.min(0.1, t - lastBodyAt));
        lastBodyAt = t;
        float k = 1f - (float) Math.exp(-dt * 8.0);
        float walkTarget = f.onGround ? Math.min(1f, f.moving) : 0f;
        float sprintTarget = f.sprinting && f.onGround && f.moving > 0.3f ? 1f : 0f;
        moveS += (walkTarget - moveS) * k;
        sprintS += (sprintTarget - sprintS) * (1f - (float) Math.exp(-dt * 6.0));
        airS += ((f.onGround ? 0f : 1f) - airS) * (1f - (float) Math.exp(-dt * 10.0));
        // landing: a little spring pressed by how fast you were falling
        if (f.onGround && !wasOnGround && lastVy < -0.25f) {
            landV -= Math.min(0.6f, -lastVy) * 1.2f;
        }
        wasOnGround = f.onGround;
        lastVy = f.vy;
        float accel = -land * 180f - landV * 18f;
        landV += accel * dt;
        land += landV * dt;
        // steps: one cycle per two steps, following how far you've walked
        stepPhase = f.walk * (float) Math.PI;
        float ph = stepPhase;
        float m = moveS * (1f + 0.5f * sprintS);
        float sx = (float) Math.sin(ph), cy = (float) Math.cos(2 * ph);
        // walking: figure eight, the knife lagging a touch
        ch[KnifeAnims.FX] += sx * 0.016f * m;
        ch[KnifeAnims.FY] += -(1f - cy) * 0.010f * m;
        ch[KnifeAnims.ROLL] += sx * 2.2f * m;
        ch[KnifeAnims.YAW] += (float) Math.cos(ph) * 1.4f * m;
        ch[KnifeAnims.WP] += (float) Math.sin(2 * ph + 0.6) * 2.0f * m;
        // sprinting: lowered, pulled in and tipped forward
        ch[KnifeAnims.FY] -= 0.045f * sprintS;
        ch[KnifeAnims.FX] += 0.025f * sprintS;
        ch[KnifeAnims.FZ] += 0.04f * sprintS;
        ch[KnifeAnims.PITCH] += 14f * sprintS;
        ch[KnifeAnims.WP] += 18f * sprintS;
        ch[KnifeAnims.WR] -= 12f * sprintS;
        // in the air: lifted while rising, pressed down when falling
        float rise = Math.max(-1f, Math.min(1f, f.vy * 2.2f));
        ch[KnifeAnims.FY] += airS * rise * 0.03f;
        ch[KnifeAnims.PITCH] += airS * -rise * 6f;
        ch[KnifeAnims.FY] += land * 0.06f;
        ch[KnifeAnims.WP] += land * -25f;
        // breathing when still
        float still = 1f - Math.min(1f, moveS + airS);
        ch[KnifeAnims.FY] += (float) Math.sin(t * 1.5) * 0.004f * still;
        ch[KnifeAnims.WR] += (float) Math.sin(t * 0.9) * 1.2f * still;
    }

    /** You're no longer holding the knife. */
    public void hidden() {
        wasHolding = false;
        trailCount = 0;
    }

    private static float ease(KnifeAnims.Anim a, float p) {
        return p;
    }

    // ------------------------------------------------------------------ the arm

    // the resting hold: where the fist is (view space, blocks), which way the forearm comes up
    // to it from below the screen, and how the wrist bends the blade upright
    static float REST_X = 0.24f, REST_Y = -0.15f, REST_Z = -0.70f;
    static float[] REST_ARM = {-0.45f, 0.55f, -0.70f};
    /** Wrist bend (pitch, yaw, roll) and the knife's turn round its length, for each way of holding it. */
    static float[][] REST_WRIST = {
        {25f, 10f, 0f, 40f},     // hammer: blade upright out of the fist, its flat toward you
        {-10f, 0f, 70f, 0f},     // reverse (karambit): ring on top, the claw out to the side
        {-20f, -35f, 0f, 90f},   // push dagger: blade forward out of the fist
    };
    private int hold;

    /** Arm space: pixels, the arm along +y (to the hand at y 8..12), thumb toward -z. */
    private M4 armMatrix(M4 view) {
        // further away along the same line of sight = smaller on screen, same place
        float far = 1f / Math.max(0.4f, Math.min(1.6f, armSize));
        float fx = (REST_X + ch[KnifeAnims.FX]) * far, fy = (REST_Y + ch[KnifeAnims.FY]) * far, fz = (REST_Z + ch[KnifeAnims.FZ]) * far;
        float[] d = norm(REST_ARM);
        // the thumb points as nearly up as the forearm allows
        float[] up = {0, 1, 0};
        float k = dot(up, d);
        float[] thumb = norm(new float[] {up[0] - k * d[0], up[1] - k * d[1], up[2] - k * d[2]});
        float[] z = {-thumb[0], -thumb[1], -thumb[2]};
        float[] x = cross(d, z);
        return new M4(view).translate(fx, fy, fz)
                .rotY(ch[KnifeAnims.YAW])
                .rotX(ch[KnifeAnims.PITCH])
                .basis(x, d, z)
                .rotY(ch[KnifeAnims.ROLL])
                // no wrist: the whole arm turns round the fist as one piece
                .rotX(REST_WRIST[hold][0] + ch[KnifeAnims.WP])
                .rotZ(REST_WRIST[hold][1] + ch[KnifeAnims.WY])
                .rotY(REST_WRIST[hold][2] + ch[KnifeAnims.WR])
                .scale(1 / 16f)
                .translate(0, -10, 0);
    }

    private static float[] norm(float[] a) {
        float l = (float) Math.sqrt(dot(a, a));
        return new float[] {a[0] / l, a[1] / l, a[2] / l};
    }

    private static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private M4 handMatrix(M4 arm) {
        return arm;   // the fist is part of the arm (no wrist joint)
    }

    private void drawArm(Out out, M4 arm, M4 hand, Frame f) {
        String skin = f.skinTexture;
        if (skin == null) {
            return;
        }
        int alpha = fx == FX_GLASS ? Math.round(255 * fxOpacity) : fx == FX_HOLOGRAM ? Math.round(255 * Math.min(0.6f, fxOpacity)) : 255;
        int col = fx == FX_HOLOGRAM ? tint(0xFFFFFF, fxColor, 0.6f) : 0xFFFFFF;
        int argb = alpha << 24 | col;
        if (net.peregrine.client.core.modules.SkeletonLook.arm()) {
            // Skeleton look: the arm is bone (the bone arm's own space is the player model's arm)
            float c = f.slim ? -0.5f : -1f;
            M4 bm = new M4(arm).translate(-c, 2, 0);
            final int a2 = alpha;
            out.begin(Out.LIT, net.peregrine.client.core.skeleton.Skeleton.TEXTURE);
            net.peregrine.client.core.skeleton.Skeleton.arm(true, f.slim, net.peregrine.client.core.skeleton.Skeleton.lod(0), bm,
                    (x, y, z, nx, ny, nz, u, v, cc) -> out.vertex(x, y, z, nx, ny, nz, u, v, a2 << 24 | (cc & 0xFFFFFF)));
            return;
        }
        out.begin(Out.LIT, skin);
        float w = f.slim ? 3 : 4;
        // the whole arm as one piece, with its sleeve layer just outside
        box(out, arm, w, 0, 12, 40, 16, 0, argb);
        box(out, arm, w, 0, 12, 40, 32, 0.25f, argb);
        if (fx == FX_GLOW || fx == FX_HOLOGRAM) {
            int g = glowColour(fx == FX_HOLOGRAM ? 0.35f : 0.22f);
            out.begin(Out.ADD, skin);
            box(out, arm, w, 0, 12, 40, 16, 0.35f, g);
        }
    }

    /**
     * One piece of a Minecraft arm: x -w/2..w/2, y y0..y1 (pixels of 12), z -2..2, textured from
     * the skin's arm at (u, v) like Minecraft (64 x 64 skin).
     */
    private void box(Out out, M4 m, float w, float y0, float y1, int u, int v, float grow, int argb) {
        float d = 4;
        float x0 = -w / 2 - grow, x1 = w / 2 + grow, z0 = -d / 2 - grow, z1 = d / 2 + grow;
        float ya = y0 - (y0 == 0 ? grow : 0), yb = y1 + (y1 == 12 ? grow : 0);
        float tv0 = v + d + y0, tv1 = v + d + y1;
        // texture columns: outer side, front, inner side, back (Minecraft's order round the box)
        float uo = u, uf = u + d, ui = u + d + w, ub = u + 2 * d + w;
        // front (thumb side, -z): from the outer side (-x) to the inner (+x)
        face(out, m, x0, ya, z0, x1, ya, z0, x1, yb, z0, x0, yb, z0, 0, 0, -1, uf, tv0, uf + w, tv1, argb);
        // back (+z): inner to outer
        face(out, m, x1, ya, z1, x0, ya, z1, x0, yb, z1, x1, yb, z1, 0, 0, 1, ub, tv0, ub + w, tv1, argb);
        // outer side (-x): back to front
        face(out, m, x0, ya, z1, x0, ya, z0, x0, yb, z0, x0, yb, z1, -1, 0, 0, uo, tv0, uo + d, tv1, argb);
        // inner side (+x): front to back
        face(out, m, x1, ya, z0, x1, ya, z1, x1, yb, z1, x1, yb, z0, 1, 0, 0, ui, tv0, ui + d, tv1, argb);
        if (y0 == 0) {   // the shoulder end (mostly off screen)
            face(out, m, x0, ya, z1, x1, ya, z1, x1, ya, z0, x0, ya, z0, 0, -1, 0, uf, v, uf + w, v + d, argb);
        }
        if (y1 == 12) {  // the knuckles
            face(out, m, x0, yb, z0, x1, yb, z0, x1, yb, z1, x0, yb, z1, 0, 1, 0, uf + w, v, uf + 2 * w, v + d, argb);
        }
    }

    private void face(Out out, M4 m, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
                      float dx, float dy, float dz, float nx, float ny, float nz, float u0, float v0, float u1, float v1, int argb) {
        m.dir(nx, ny, nz, tmpN);
        float s = 1 / 64f;
        put(out, m, ax, ay, az, u0 * s, v0 * s, argb);
        put(out, m, bx, by, bz, u1 * s, v0 * s, argb);
        put(out, m, cx, cy, cz, u1 * s, v1 * s, argb);
        put(out, m, dx, dy, dz, u0 * s, v1 * s, argb);
    }

    private void put(Out out, M4 m, float x, float y, float z, float u, float v, int argb) {
        m.point(x, y, z, tmp);
        out.vertex(tmp[0], tmp[1], tmp[2], tmpN[0], tmpN[1], tmpN[2], u, v, argb);
    }

    // ------------------------------------------------------------------ the knife

    /** cm to arm pixels, before the size setting. */
    static final float CM = 0.5f;

    private M4 knifeMatrix(M4 hand, KnifeModels.Knife k) {
        M4 m = new M4(hand).translate(0, 10, 0)
                .translate(moveX, moveY, moveZ)
                .rotX(tilt).rotY(turn).rotZ(roll);
        switch (k.hold) {
            case KnifeModels.REVERSE:   // blade out of the little-finger side, ring above the thumb
                m.basis(new float[] {0, -1, 0}, new float[] {0, 0, 1}, new float[] {-1, 0, 0});
                break;
            case KnifeModels.PUSH:      // the T across the palm, blade out between the fingers
                m.basis(new float[] {0, 0, 1}, new float[] {0, 1, 0}, new float[] {-1, 0, 0});
                break;
            default:                    // blade up out of the thumb side, edge forward
                m.basis(new float[] {0, -1, 0}, new float[] {0, 0, -1}, new float[] {1, 0, 0});
                break;
        }
        m.scale(CM * Math.max(0.3f, size));
        m.translate(k.grip[0], k.grip[1], 0).rotY(REST_WRIST[k.hold][3]).translate(-k.grip[0], -k.grip[1], 0);
        return spun(m, k);
    }

    /** The knife's own twirl (round its spin point) and twist (round its length). */
    private M4 spun(M4 m, KnifeModels.Knife k) {
        float spin = ch[KnifeAnims.SPIN], twist = ch[KnifeAnims.TWIST];
        if (spin != 0) {
            m.translate(k.spin[0] - k.grip[0], k.spin[1] - k.grip[1], 0).rotZ(spin).translate(k.grip[0] - k.spin[0], k.grip[1] - k.spin[1], 0);
        }
        if (twist != 0) {
            m.rotY(twist);
        }
        return m.translate(-k.grip[0], -k.grip[1], 0);
    }

    /** Arm turned off: the knife sits where Minecraft holds a sword, and swings like one. */
    private M4 minecraftHold(M4 view, KnifeModels.Knife k, float swing) {
        float sq = (float) Math.sin(Math.sqrt(swing) * Math.PI);
        float s2 = (float) Math.sin(swing * swing * Math.PI);
        M4 m = new M4(view)
                .translate(-0.4f * sq, 0.2f * (float) Math.sin(Math.sqrt(swing) * Math.PI * 2), -0.2f * (float) Math.sin(swing * Math.PI))
                .translate(0.56f, -0.52f, -0.72f)
                .rotY(45 + s2 * -20).rotZ(sq * -20).rotX(sq * -80).rotY(-45)
                // Minecraft's first-person sword placement
                .translate(1.13f / 16, 3.2f / 16, 1.13f / 16).rotY(-90).rotZ(25).scale(0.68f)
                .translate(-0.5f, -0.5f, -0.5f);
        // the sword's diagonal in its picture, from the handle (lower left) to the tip (upper right)
        float r = 0.70711f;
        m.translate(0.70f, 0.30f, 0.5f).basis(new float[] {-r, -r, 0}, new float[] {-r, r, 0}, new float[] {0, 0, 1});
        m.scale(0.05f * Math.max(0.3f, size));
        return spun(m, k);
    }

    private void drawKnife(Out out, KnifeModels.Knife k, M4 m, Frame f, double t) {
        String tex = KnifeSkins.texture(skin);
        float metalBlade = KnifeSkins.metal(skin);
        boolean custom = KnifeSkins.isCustom(skin);
        int alpha = fx == FX_GLASS ? Math.round(255 * fxOpacity) : fx == FX_HOLOGRAM ? Math.round(255 * Math.min(0.55f, fxOpacity)) : 255;
        M4[] parts = partMatrices(k, m);
        // pass 1: the knife, lit by the world
        out.begin(Out.LIT, tex);
        for (KnifeModels.Part p : k.parts) {
            emit(out, p.mesh, parts[p.group], alpha, metalBlade, custom, false, 0);
        }
        // pass 2: polished metal mirrors its surroundings (added on top)
        if (fx != FX_HOLOGRAM) {
            out.begin(Out.ADD, tex);
            for (KnifeModels.Part p : k.parts) {
                emit(out, p.mesh, parts[p.group], 255, metalBlade, custom, true, 0);
            }
        }
        // effects: a soft glow round it (and the hologram's light)
        if (fx == FX_GLOW || fx == FX_HOLOGRAM) {
            out.begin(Out.ADD, tex);
            int g = glowColour(fx == FX_HOLOGRAM ? 0.5f : 0.35f);
            for (KnifeModels.Part p : KnifeModels.getLow(knife).parts) {
                glow(out, p.mesh, parts[p.group], g, fx == FX_GLOW ? 0.35f : 0.12f, t);
            }
        }
    }

    private M4[] partMatrices(KnifeModels.Knife k, M4 m) {
        M4[] r = {m, m, m};
        if (k.pinA != null) {
            float fold = Math.max(0, Math.min(1, ch[KnifeAnims.FOLD]));
            float th = 180f * fold;
            float phi = -th + ch[KnifeAnims.LOOP];
            M4 blade = new M4(m).translate(k.pinA[0], k.pinA[1], 0).rotZ(-th).translate(-k.pinA[0], -k.pinA[1], 0);
            M4 free = new M4(blade).translate(k.pinB[0], k.pinB[1], 0).rotZ(-phi).translate(-k.pinB[0], -k.pinB[1], 0);
            r[1] = blade;
            r[2] = free;
        }
        return r;
    }

    private void emit(Out out, Mesh mesh, M4 m, int alpha, float metalBlade, boolean custom, boolean shine, float inflate) {
        float[] v = mesh.v;
        int[] q = mesh.q;
        for (int i = 0; i < mesh.quads; i++) {
            int o = i * 4;
            if (shine) {
                // the handle's plastic hardly reflects: leave it out of the mirror pass
                float vv = v[q[o] * Mesh.STRIDE + 7];
                if (vv >= Shapes.HANDLE_V0 && vv < Shapes.HANDLE_V1) {
                    continue;
                }
            }
            if (alpha == 255) {
                // faces turned clearly away from you are hidden behind the front ones: skip them
                int b0 = q[o] * Mesh.STRIDE, b2 = q[o + 2] * Mesh.STRIDE;
                m.point((v[b0] + v[b2]) * 0.5f, (v[b0 + 1] + v[b2 + 1]) * 0.5f, (v[b0 + 2] + v[b2 + 2]) * 0.5f, tmp);
                m.dir(v[b0 + 3] + v[b2 + 3], v[b0 + 4] + v[b2 + 4], v[b0 + 5] + v[b2 + 5], tmpN);
                float facing = tmp[0] * tmpN[0] + tmp[1] * tmpN[1] + tmp[2] * tmpN[2];
                if (facing > 0.35f * (float) Math.sqrt(tmp[0] * tmp[0] + tmp[1] * tmp[1] + tmp[2] * tmp[2])) {
                    continue;
                }
            }
            for (int j = 0; j < 4; j++) {
                int b = q[o + j] * Mesh.STRIDE;
                m.point(v[b], v[b + 1], v[b + 2], tmp);
                m.dir(v[b + 3], v[b + 4], v[b + 5], tmpN);
                float tu = v[b + 6], tv = v[b + 7];
                float metal = tv < Shapes.BLADE_V1 ? metalBlade : tv < Shapes.HANDLE_V1 ? 0.08f : 0.9f;
                int base = 0xFFFFFF;
                if (custom) {
                    base = tv < Shapes.BLADE_V1 ? lerpRgb(custom1, custom2, tu) : tv < Shapes.HANDLE_V1 ? scaleRgb(custom2, 0.35f) : 0xFFFFFF;
                }
                if (fx == FX_HOLOGRAM) {
                    base = tint(base, fxColor, 0.7f);
                }
                int argb;
                if (shine) {
                    float[] e = Shine.reflect(tmp[0], tmp[1], tmp[2], tmpN[0], tmpN[1], tmpN[2]);
                    float k = metal * (fx == FX_GLASS ? 0.9f : 0.75f);
                    argb = 0xFF000000 | rgb(e[0] * k, e[1] * k, e[2] * k);
                    argb = mulRgb(argb, base);
                } else {
                    float lum = 1f - 0.72f * metal;
                    argb = alpha << 24 | (mulRgb(rgb(lum, lum, lum), base) & 0xFFFFFF);
                }
                out.vertex(tmp[0], tmp[1], tmp[2], tmpN[0], tmpN[1], tmpN[2], tu, tv, argb);
            }
        }
    }

    /** The mesh pushed out a little along its normals, added as light: a soft halo. */
    private void glow(Out out, Mesh mesh, M4 m, int argb, float inflatePx, double t) {
        float[] v = mesh.v;
        int[] q = mesh.q;
        float push = inflatePx / (CM * Math.max(0.3f, size));
        for (int i = 0; i < mesh.quads; i++) {
            int o = i * 4;
            for (int j = 0; j < 4; j++) {
                int b = q[o + j] * Mesh.STRIDE;
                float x = v[b] + v[b + 3] * push, y = v[b + 1] + v[b + 4] * push, z = v[b + 2] + v[b + 5] * push;
                m.point(x, y, z, tmp);
                m.dir(v[b + 3], v[b + 4], v[b + 5], tmpN);
                int c = argb;
                if (fx == FX_HOLOGRAM) {  // scan lines drifting up
                    float s = 0.65f + 0.35f * (float) Math.sin(tmp[1] * 160 - t * 6);
                    c = (c & 0xFF000000) | scaleRgb(c & 0xFFFFFF, s);
                }
                out.vertex(tmp[0], tmp[1], tmp[2], tmpN[0], tmpN[1], tmpN[2], v[b + 6], v[b + 7], c);
            }
        }
    }

    // ------------------------------------------------------------------ swing trails

    private void trailsFor(Out out, KnifeModels.Knife k, M4 m, double t) {
        boolean swinging = action != null && isHit(action) && t - actionAt < action.seconds * 0.85;
        if (swinging) {
            float[] base = new float[3], tip = new float[3];
            M4 bm = k.pinA != null ? partMatrices(k, m)[1] : m;
            float bx = k.tip[0] * 0.55f, by = k.tip[1] * 0.55f;   // the outer half of the blade
            bm.point(bx, by, 0, base);
            bm.point(k.tip[0], k.tip[1], 0, tip);
            float[] s = trail[trailHead];
            s[0] = (float) t;
            System.arraycopy(base, 0, s, 1, 3);
            System.arraycopy(tip, 0, s, 4, 3);
            trailHead = (trailHead + 1) % TRAIL;
            trailCount = Math.min(TRAIL, trailCount + 1);
            if (t - ghostAt[(ghostHead + 3) % 4] > 0.022) {
                ghosts[ghostHead].set(bm);
                ghostAt[ghostHead] = t;
                ghostHead = (ghostHead + 1) % 4;
            }
        }
        float life = Math.max(0.05f, trailSeconds);
        // the ribbon the blade sweeps through
        int col = fx == FX_OFF ? 0xDDEEFF : fxColor;
        out.begin(Out.ADD, "peregrine-client:textures/knife/trail.png");
        float[] prev = null;
        float prevA = 0;
        for (int i = trailCount - 1; i >= 0; i--) {
            float[] s = trail[(trailHead - 1 - i + TRAIL * 2) % TRAIL];
            float age = (float) (t - s[0]);
            float a = 1 - age / life;
            if (a <= 0) {
                prev = null;
                continue;
            }
            if (prev != null) {
                int c0 = Math.round(255 * prevA * prevA * 0.75f) << 24 | col;
                int c1 = Math.round(255 * a * a * 0.75f) << 24 | col;
                out.vertex(prev[1], prev[2], prev[3], 0, 0, 1, 0, 0, c0);
                out.vertex(prev[4], prev[5], prev[6], 0, 0, 1, 0, 1, c0);
                out.vertex(s[4], s[5], s[6], 0, 0, 1, 1, 1, c1);
                out.vertex(s[1], s[2], s[3], 0, 0, 1, 1, 0, c1);
            }
            prev = s;
            prevA = a;
        }
        // afterimages of the blade, fading
        Mesh blade = KnifeModels.getLow(knife).parts.get(0).mesh;
        out.begin(Out.ADD, KnifeSkins.texture(skin));
        for (int g = 1; g <= 3; g++) {
            int idx = (ghostHead - g + 8) % 4;
            float age = (float) (t - ghostAt[idx]);
            float a = 1 - age / life;
            if (a <= 0 || !swinging && age > life) {
                continue;
            }
            int c = Math.round(255 * a * 0.4f) << 24 | col;
            ghost(out, blade, ghosts[idx], c);
        }
    }

    private void ghost(Out out, Mesh mesh, M4 m, int argb) {
        float[] v = mesh.v;
        int[] q = mesh.q;
        // every other quad is plenty for a blurred copy
        for (int i = 0; i < mesh.quads; i++) {
            int o = i * 4;
            for (int j = 0; j < 4; j++) {
                int b = q[o + j] * Mesh.STRIDE;
                m.point(v[b], v[b + 1], v[b + 2], tmp);
                out.vertex(tmp[0], tmp[1], tmp[2], 0, 0, 1, v[b + 6], v[b + 7], argb);
            }
        }
    }

    private static boolean isHit(KnifeAnims.Anim a) {
        return a == KnifeAnims.SLASH_FORE || a == KnifeAnims.SLASH_BACK || a == KnifeAnims.UPPERCUT || a == KnifeAnims.CHOP
                || a == KnifeAnims.STAB || a == KnifeAnims.LUNGE;
    }

    // ------------------------------------------------------------------ colour helpers

    private int glowColour(float strength) {
        int a = Math.round(255 * Math.max(0, Math.min(1, fxGlow * strength * 2)));
        return a << 24 | fxColor;
    }

    static int rgb(float r, float g, float b) {
        return clamp(r) << 16 | clamp(g) << 8 | clamp(b);
    }

    private static int clamp(float x) {
        return Math.max(0, Math.min(255, Math.round(x * 255)));
    }

    static int mulRgb(int a, int b) {
        int r = ((a >> 16) & 255) * ((b >> 16) & 255) / 255;
        int g = ((a >> 8) & 255) * ((b >> 8) & 255) / 255;
        int bl = (a & 255) * (b & 255) / 255;
        return (a & 0xFF000000) | r << 16 | g << 8 | bl;
    }

    static int scaleRgb(int c, float k) {
        return rgb(((c >> 16) & 255) / 255f * k, ((c >> 8) & 255) / 255f * k, (c & 255) / 255f * k);
    }

    static int lerpRgb(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        return rgb((((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t) / 255f,
                (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t) / 255f,
                ((a & 255) * (1 - t) + (b & 255) * t) / 255f);
    }

    static int tint(int base, int col, float amount) {
        return lerpRgb(base, mulRgb(0xFF000000 | base, 0xFF000000 | col) & 0xFFFFFF | 0, amount) & 0xFFFFFF;
    }

    // ------------------------------------------------------------------ for the previews

    /** The current channel values (read-only use). */
    public float[] channels() {
        return ch;
    }

    /** Plays a move from its start now (previews and tests). */
    public void play(KnifeAnims.Anim a) {
        start(a);
    }

    /** Previews: pretend the move started this many seconds ago. */
    public void rewind(double seconds) {
        actionAt -= seconds;
        fadingAt -= seconds;
    }
}
