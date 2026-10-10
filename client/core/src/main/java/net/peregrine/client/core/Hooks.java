package net.peregrine.client.core;

/**
 * Switches that version adapters' rendering hooks read on hot paths (every
 * block of every chunk, every item, every frame). Plain fields, no lookups:
 * modules set them, hooks just read them.
 */
public final class Hooks {

    private Hooks() {
    }

    // ---- Anti base leak
    /** Every block uses the same random texture variant and rotation. */
    public static volatile boolean fixedRotation;
    /** Flowers, grass and similar plants sit in the middle of their block. */
    public static volatile boolean fixedOffset;
    /** Bedrock is drawn as diamond blocks. */
    public static volatile boolean diamondBedrock;

    /** The seed every block uses while fixedRotation is on. */
    public static final long FIXED_SEED = 0L;

    // ---- Visuals
    public static volatile boolean itemPhysics;
    public static volatile boolean customCrosshair;

    // ---- Freelook: the camera's own direction while looking around
    public static volatile boolean freelook;
    public static volatile float camYaw;
    public static volatile float camPitch;

    // ---- Particles: how much of each kind to keep (1 = all, 0 = none)
    public static final String[] PARTICLE_KINDS = {"Potion swirls", "Splash potions and XP bottles", "Lava",
        "Explosions", "Hits and crits", "Breaking blocks", "Rain and water", "Everything else"};
    public static volatile float[] particleKeep = {1, 1, 1, 1, 1, 1, 1, 1};
    /** True while any kind is thinned, so the hook skips all work otherwise. */
    public static volatile boolean particlesFiltered;
    private static final java.util.Map<String, Integer> KIND_OF = new java.util.concurrent.ConcurrentHashMap<String, Integer>();

    /** Which kind a particle id ("minecraft:lava") belongs to. */
    public static int particleKind(String id) {
        Integer k = KIND_OF.get(id);
        if (k == null) {
            String n = id.indexOf(':') >= 0 ? id.substring(id.indexOf(':') + 1) : id;
            if (n.equals("entity_effect") || n.equals("ambient_entity_effect")) {
                k = 0;
            } else if (n.equals("effect") || n.equals("instant_effect") || n.equals("witch")) {
                k = 1;
            } else if (n.contains("lava")) {
                k = 2;
            } else if (n.startsWith("explosion") || n.equals("poof") || n.equals("gust") || n.contains("gust_emitter")) {
                k = 3;
            } else if (n.equals("crit") || n.equals("enchanted_hit") || n.equals("damage_indicator") || n.equals("sweep_attack")) {
                k = 4;
            } else if (n.equals("block") || n.equals("block_crumble") || n.equals("dust_pillar")) {
                k = 5;
            } else if (n.equals("rain") || n.equals("splash") || n.equals("bubble") || n.equals("bubble_pop")
                    || n.equals("fishing") || n.contains("drip") && n.contains("water")) {
                k = 6;
            } else {
                k = 7;
            }
            KIND_OF.put(id, k);
        }
        return k;
    }

    /** Whether to let this particle appear (random, so a share of them still show). */
    public static boolean keepParticle(String id) {
        float keep = particleKeep[particleKind(id)];
        if (particleHits < 1000) {
            particleHits++;
        }
        return keep >= 1f || keep > 0f && java.util.concurrent.ThreadLocalRandom.current().nextFloat() < keep;
    }

    // ---- Hit color: crit particles (0 = Minecraft's own)
    public static volatile int critColor;

    // ---- Name tag
    public static volatile boolean ownNameTag;
    public static volatile boolean nameLogo;
    /** The Peregrine logo, as a character from the mod's font (assets/minecraft/font/default.json). */
    public static final String LOGO = "\uE100";

    // ---- Scoreboard (sidebar) size, 1 = Minecraft's
    public static volatile float scoreboardScale = 1f;

    // ---- Low fire: how far down the fire overlay moves (0 = Minecraft's)
    public static volatile float fireDrop;
    /** Small totem: sizes of the held totem (first person) and the pop animation, 1 = normal. */
    public static volatile float totemHeld = 1f;
    public static volatile float totemPop = 1f;
    public static volatile int totemHits;

    // ---- Hitboxes: which kinds show (players, mobs, items, XP, projectiles, other), color, look line
    public static volatile boolean[] hitboxKinds = {true, true, true, true, true, true};
    public static volatile int hitboxColor;        // 0 = Minecraft's white
    public static volatile boolean hitboxLookLine = true;

    /** Kinds: 0 players, 1 mobs and animals, 2 dropped items, 3 XP orbs and bottles, 4 arrows and thrown things, 5 other. */
    public static boolean showHitbox(int kind) {
        return kind < 0 || kind >= hitboxKinds.length || hitboxKinds[kind];
    }

    // ---- Inventory tweaks (Mouse Tweaks style)
    public static volatile boolean dragMove;     // hold Shift and drag: move every stack you pass over
    public static volatile boolean scrollMove;   // scroll over a stack: move items one at a time

    // ---- 1.7 animations
    public static volatile boolean oldSwing;     // the hand keeps swinging while you use an item
    public static volatile boolean oldPosition;  // held items sit lower and further out, like 1.7

    // ---- Fullbright: also light up places with no light at all (like night vision)
    public static volatile boolean nightVision;

    // ---- counters, so the self-test can see the hooks run (plain ints: racy, but
    // cheap on hot paths; hooks stop counting at 1000)
    public static int seedHits;
    public static int offsetHits;
    public static int modelHits;
    public static int bedrockSwaps;
    public static int itemPhysicsHits;
    public static int crosshairDraws;
    public static int cameraHits;
    public static int particleHits;
    public static int nameTagHits;
    public static int scoreboardHits;
    public static int inventoryTweakHits;
    public static int animationHits;
    public static int nightVisionHits;
    public static int hitboxHits;
    public static int fireHits;

    /** Mouse movement while freelook is on turns the camera, not the player (same maths as Minecraft). */
    public static void turnCamera(double yaw, double pitch) {
        camYaw += (float) yaw * 0.15f;
        camPitch = Math.max(-90f, Math.min(90f, camPitch + (float) pitch * 0.15f));
    }

    /** The settled height of a resting item, used by item physics. */
    public static float bob(float ageInTicks, float bobOffset) {
        return (float) Math.sin(ageInTicks / 10.0F + bobOffset) * 0.1F + 0.1F;
    }
}
