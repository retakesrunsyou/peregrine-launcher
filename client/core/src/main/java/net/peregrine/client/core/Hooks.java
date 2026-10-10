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

    /**
     * Clean menus: resource packs you add change the game itself (blocks, items, mobs,
     * the HUD while playing) but not the menus: title screen, buttons, menu
     * backgrounds, fonts and splash texts stay Minecraft's own.
     */
    // ---- batch 8

    /** Fog: off, clear water, clear lava, and a color (0 = Minecraft's). */
    public static volatile boolean fogOff;
    public static volatile boolean clearWater;
    public static volatile boolean clearLava;
    public static volatile int fogColor;
    public static volatile int fogHits;

    /** Swing speed, visual only: 1 = normal, 2 = twice as fast. */
    public static volatile float swingSpeed = 1f;
    public static volatile int swingHits;

    /** Swing duration in ticks after the speed setting (always at least 1). */
    public static int swingDuration(int ticks) {
        float s = swingSpeed;
        if (s == 1f) {
            return ticks;
        }
        if (swingHits < 1000) {
            swingHits++;
        }
        return Math.max(1, Math.round(ticks / s));
    }

    /** Block outline: color (0 = Minecraft's), thickness (1 = normal), and outlines on ores you can see. */
    public static volatile int outlineColor;
    public static volatile float outlineWidth = 1f;
    public static volatile boolean oreOutlines;
    public static volatile int oreRange = 12;
    public static volatile int oreHits;
    public static volatile int outlineHits;
    /** True while Peregrine draws ore outlines through Minecraft's own outline code (keep their colors). */
    public static volatile boolean drawingOres;

    /** Ore outline: which ores, their colors, and how thick (1 = normal, 1.21.11 and newer). */
    public static final String[] ORE_TYPES = {"Diamond", "Emerald", "Gold", "Iron", "Redstone", "Lapis", "Copper",
        "Coal", "Nether quartz", "Ancient debris"};
    public static final int[] ORE_COLORS = {0xFF4FE3E8, 0xFF3BE070, 0xFFFFD23C, 0xFFE8C9A8, 0xFFFF3B3B, 0xFF3A62F0,
        0xFFE8814A, 0xFF8C8C8C, 0xFFF2EEE6, 0xFFA0644C};
    public static volatile boolean[] oreEnabled = {true, true, true, true, true, true, true, false, true, true};
    public static volatile float oreWidth = 1f;

    /** Which ore type a block is ("minecraft:deepslate_diamond_ore" is Diamond), or -1. */
    public static int oreType(String id) {
        if (id == null) {
            return -1;
        }
        String n = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        if (n.equals("ancient_debris")) {
            return 9;
        }
        if (!n.endsWith("_ore")) {
            return -1;
        }
        String[] keys = {"diamond", "emerald", "gold", "iron", "redstone", "lapis", "copper", "coal", "quartz"};
        for (int i = 0; i < keys.length; i++) {
            if (n.contains(keys[i])) {
                return i;
            }
        }
        return -1;
    }

    /** Outline color for an ore block, or 0 if it isn't an ore (or that ore is switched off). */
    public static int oreColor(String id) {
        int t = oreType(id);
        return t < 0 || !oreEnabled[t] ? 0 : ORE_COLORS[t];
    }

    /** Static sky: clouds and/or the sun, moon and stars stop moving (only on your screen). */
    public static volatile boolean staticClouds;
    public static volatile boolean staticSky;
    public static volatile int skyHits;

    /** Sound filters: how loud each kind of sound is (1 = normal). */
    public static final String[] SOUND_GROUPS = {"Explosions", "Rain and thunder", "Hits and hurt sounds",
        "Footsteps", "Mobs", "Villagers", "Pistons and redstone", "Portals and ambience", "Fireworks"};
    public static volatile float[] soundLevel = {1, 1, 1, 1, 1, 1, 1, 1, 1};
    public static volatile boolean soundFiltered;
    public static volatile int soundHits;
    private static final java.util.Map<String, Integer> SOUND_GROUP_OF = new java.util.concurrent.ConcurrentHashMap<String, Integer>();

    /** Which group a sound ("minecraft:entity.generic.explode") belongs to, or -1. */
    public static int soundGroup(String id) {
        Integer g = SOUND_GROUP_OF.get(id);
        if (g == null) {
            String n = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
            if (n.contains("explode") || n.contains("explosion") || n.contains("tnt.primed")) {
                g = 0;
            } else if (n.startsWith("weather.")) {
                g = 1;
            } else if (n.endsWith(".hurt") || n.contains(".hurt_") || n.startsWith("entity.player.attack")
                    || n.endsWith(".damage") || n.contains("shield.block")) {
                g = 2;
            } else if (n.endsWith(".step")) {
                g = 3;
            } else if (n.contains("villager") || n.contains("wandering_trader")) {
                g = 5;
            } else if (n.contains("piston") || n.contains("dispenser") || n.contains("dropper")
                    || n.contains("note_block") || n.contains("redstone") || n.contains("comparator")) {
                g = 6;
            } else if (n.contains("portal") || n.startsWith("ambient.") || n.contains(".ambient")) {
                g = 7;
            } else if (n.contains("firework")) {
                g = 8;
            } else if (n.startsWith("entity.") && !n.startsWith("entity.player") && !n.startsWith("entity.experience")
                    && !n.startsWith("entity.item")) {
                g = 4;
            } else {
                g = -1;
            }
            SOUND_GROUP_OF.put(id, g);
        }
        return g;
    }

    /** Volume multiplier for a sound under the filters. */
    public static float soundFactor(String id) {
        if (!soundFiltered) {
            return 1f;
        }
        int g = soundGroup(id);
        float f = g < 0 ? 1f : soundLevel[g];
        if (f != 1f && soundHits < 1000) {
            soundHits++;
        }
        return f;
    }

    public static volatile boolean cleanMenus = true;
    public static volatile int packFiltered;
    private static final String[] MENU_ONLY = {
        "textures/gui/title/", "textures/gui/sprites/widget/", "textures/gui/sprites/icon/",
        "textures/gui/sprites/server_list/", "textures/gui/sprites/world_list/", "textures/gui/sprites/popup/",
        "textures/gui/sprites/toast/", "textures/gui/sprites/notification/", "textures/gui/sprites/transferable_list/",
        "textures/gui/sprites/pending_invite/", "textures/gui/presets/", "textures/gui/menu_",
        "textures/gui/inworld_menu_", "textures/gui/header_separator", "textures/gui/footer_separator",
        "textures/gui/inworld_header_separator", "textures/gui/inworld_footer_separator",
        "textures/gui/tab_header_background", "textures/gui/options_background", "textures/gui/light_dirt_background",
        "font/", "textures/font/", "texts/",
    };

    /** Should this file be skipped in this pack? Only packs the player added ("file/...") are touched. */
    public static boolean keepOutOfPack(String packId, String namespace, String path) {
        if (!cleanMenus || packId == null || !packId.startsWith("file/")) {
            return false;
        }
        if (!namespace.equals("minecraft") && !namespace.equals("realms")) {
            return false;
        }
        for (String prefix : MENU_ONLY) {
            if (path.startsWith(prefix)) {
                if (packFiltered < 100000) {
                    packFiltered++;
                }
                return true;
            }
        }
        return false;
    }
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
