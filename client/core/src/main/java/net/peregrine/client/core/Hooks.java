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
        "Explosions", "Hits and crits", "Breaking blocks", "Rain and water", "Everything else",
        "Totem pops", "Fire and smoke", "Magic and portals", "Spores, ash and leaves"};
    public static volatile float[] particleKeep = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
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
            } else if (n.equals("totem_of_undying")) {
                k = 8;
            } else if (n.contains("flame") || n.contains("smoke") || n.equals("campfire_cosy_smoke")
                    || n.equals("campfire_signal_smoke") || n.equals("soul") || n.equals("copper_fire_flame")) {
                k = 9;
            } else if (n.equals("enchant") || n.contains("portal") || n.equals("end_rod") || n.equals("glow")
                    || n.equals("electric_spark") || n.equals("wax_on") || n.equals("wax_off") || n.equals("scrape")
                    || n.equals("trial_omen") || n.equals("ominous_spawning") || n.equals("vault_connection")) {
                k = 10;
            } else if (n.endsWith("spore") || n.contains("ash") || n.contains("leaves") || n.equals("mycelium")
                    || n.equals("spore_blossom_air") || n.equals("falling_spore_blossom") || n.equals("firefly")
                    || n.equals("white_ash") || n.equals("pale_oak_leaves")) {
                k = 11;
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
            } else if (n.startsWith("weather.") || n.contains("lightning_bolt")) {
                g = 1;
            } else if (n.endsWith(".hurt") || n.contains(".hurt_") || n.startsWith("entity.player.attack")
                    || n.endsWith(".damage") || n.contains("shield.block")) {
                g = 2;
            } else if (n.endsWith(".step")) {
                g = 3;
            } else if (n.contains("villager") || n.contains("wandering_trader")) {
                g = 5;
            } else if (n.contains("piston") || n.contains("dispenser") || n.contains("dropper") || n.contains("crafter")
                    || n.contains("note_block") || n.contains("redstone") || n.contains("comparator") || n.contains("repeater")
                    || n.contains("observer") || n.contains("lever") || n.contains("button") || n.contains("pressure_plate")
                    || n.contains("tripwire") || n.contains("hopper") || n.contains("minecart") || n.contains("sculk_sensor")
                    || n.contains("iron_door") || n.contains("iron_trapdoor") || n.contains("copper_bulb")) {
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

    /** How far zoomed in right now (1 = not). */
    public static volatile double zoomNow = 1.0;
    public static int zoomScrollHits;

    /** Turning speed while zoomed: slower the further in, so aiming feels the same. */
    public static double zoomTurn() {
        double z = zoomNow;
        return z > 1.01 ? 1.0 / z : 1.0;
    }

    /** A mouse scroll in the game (no screen open): true if zoom used it. */
    public static boolean zoomScroll(double amount) {
        Peregrine pc = Peregrine.get();
        net.peregrine.client.core.Module m = pc == null ? null : pc.module("zoom");
        boolean used = m instanceof net.peregrine.client.core.modules.Zoom && ((net.peregrine.client.core.modules.Zoom) m).scroll(amount);
        if (used && zoomScrollHits < 1000) {
            zoomScrollHits++;
        }
        return used;
    }

    /** Block place animation: how long a placed block takes to grow in (ms), 0 = off. */
    public static volatile int placeAnimMs;
    public static volatile int placeHits;

    /** How big a placed block is drawn, t ms after it was placed (1 = full size). */
    public static float placeScale(long t) {
        int ms = placeAnimMs;
        if (ms <= 0 || t >= ms) {
            return 1f;
        }
        float x = Math.max(0f, t / (float) ms);
        float ease = 1f - (1f - x) * (1f - x) * (1f - x);  // fast at first, settling at the end
        return 0.3f + 0.7f * ease;
    }

    /** Weather on your screen: 0 = the real weather, 1 = clear, 2 = rain, 3 = thunderstorm. */
    public static volatile int weather;
    public static volatile int weatherHits;

    /** The rain (or thunder) level to show instead of the real one, or -1 to keep it. */
    public static float weatherLevel(boolean thunder) {
        int w = weather;
        if (w == 0) {
            return -1f;
        }
        if (weatherHits < 1000) {
            weatherHits++;
        }
        return thunder ? (w == 3 ? 1f : 0f) : (w >= 2 ? 1f : 0f);
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

    // ---- batch 9: nick hider, time changer, glint color, stretched view, potions

    /** Nick hider: your name as it reads in game text, and what to show instead (null = off). */
    public static volatile String nickReal;
    public static volatile String nick;
    /** Time changer: the time of day to show (ticks, 0-23999), or -1 for the real time. */
    public static volatile long timeOverride = -1;
    /** Glint color (RGB) for the enchantment shine, or 0 for Minecraft's own purple. */
    public static volatile int glintColor;
    public static volatile float glintStrength = 1f;
    /** Stretched view: 1 = normal; above 1 widens what you see, like a stretched 4:3 screen. */
    public static volatile float stretch = 1f;
    /** Stretched resolution: the HUD is stretched as well (laid out narrower, then drawn wide). */
    public static volatile boolean stretchHud;
    /** True while the HUD is being drawn stretched (the GUI width reads narrower then). */
    public static volatile boolean stretchingHud;
    public static int stretchHudHits;
    /** Borderless fullscreen took over a fullscreen switch. */
    public static int borderlessHits;
    /** Growing blocks drawn (frames). */
    public static int placeDrawHits;
    /** Motion blur: how much of the last frame stays (0 = off). */
    public static volatile float motionBlur;
    public static int motionBlurHits;

    /** The HUD's width while it's drawn stretched. */
    public static int stretchedGuiWidth(int width) {
        float s = stretch;
        if (stretchingHud && s > 1f) {
            if (stretchHudHits < 1000) {
                stretchHudHits++;
            }
            return (int) Math.ceil(width / s);
        }
        return width;
    }
    public static int textHits;
    public static int timeHits;
    public static int foilHits;
    public static int glintHits;
    public static int stretchHits;

    /** Whether a piece of text needs the nick hider's attention (cheap check first). */
    public static boolean nickIn(String text) {
        String real = nickReal, n = nick;
        return n != null && real != null && !real.isEmpty() && text != null && text.contains(real);
    }

    public static String hideNick(String text) {
        if (!nickIn(text)) {
            return text;
        }
        if (textHits < 1000) {
            textHits++;
        }
        return text.replace(nickReal, nick);
    }

    /** True for the enchantment glint textures (item and armor), by path. */
    public static boolean isGlintTexture(String path) {
        return path != null && path.startsWith("textures/misc/enchanted_glint") && path.endsWith(".png");
    }

    /**
     * Recolors the enchantment glint: its brightness pattern is kept, the color is
     * the chosen one. Returns the original if anything goes wrong.
     */
    public static byte[] tintGlint(byte[] png) {
        int rgb = glintColor;
        if (rgb == 0 || png == null) {
            return png;
        }
        try {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
            if (img == null) {
                return png;
            }
            int w = img.getWidth(), h = img.getHeight();
            java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            int tr = (rgb >> 16) & 0xFF, tg = (rgb >> 8) & 0xFF, tb = rgb & 0xFF;
            float k = glintStrength;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int c = img.getRGB(x, y);
                    int a = c >>> 24;
                    int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
                    float lum = Math.max(r, Math.max(g, b)) / 255f * k;  // the glint's own pattern
                    int nr = Math.min(255, Math.round(tr * lum)), ng = Math.min(255, Math.round(tg * lum)),
                            nb = Math.min(255, Math.round(tb * lum));
                    out.setRGB(x, y, (a << 24) | (nr << 16) | (ng << 8) | nb);
                }
            }
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(out, "png", bytes);
            if (glintHits < 1000) {
                glintHits++;
            }
            return bytes.toByteArray();
        } catch (Throwable e) {
            return png;
        }
    }

    // ---- batch 9b

    /** Hand position: per hand {x, y, z, size, turn degrees}; null = Minecraft's own. */
    public static volatile float[] handMain;
    public static volatile float[] handOff;
    /** Smooth hotbar: how fast the selection slides (0 = jump, like Minecraft). */
    public static volatile float hotbarSpeed;
    /** Titles, action bar and boss bar: size (1 = Minecraft's), offset, hidden. */
    public static volatile float titleScale = 1f;
    public static volatile float titleOffset;
    public static volatile boolean titleHidden;
    public static volatile float actionScale = 1f;
    public static volatile boolean actionHidden;
    public static volatile float bossScale = 1f;
    public static volatile boolean bossHidden;
    /** Tab list: size and ping as numbers. */
    public static volatile float tabScale = 1f;
    public static volatile boolean tabPingNumbers;
    /** Hide plants: a bit per kind (see PLANT_KINDS); 0 = all shown. */
    public static volatile int plantMask;
    public static final String[] PLANT_KINDS = {"Grass and ferns", "Flowers", "Leaf litter and bushes", "Seagrass", "Kelp"};
    private static final java.util.Map<String, Integer> PLANT_OF = new java.util.concurrent.ConcurrentHashMap<String, Integer>();
    public static int hotbarHits;
    public static int titleHits;
    public static int tabHits;
    public static int plantHits;
    public static int previewHits;
    public static int handHits;
    public static int f3Hits;
    public static int tierHits;
    public static int labelHits;

    /** Which plant kind a block is (index into PLANT_KINDS), or -1. */
    public static int plantKind(String blockId) {
        Integer k = PLANT_OF.get(blockId);
        if (k == null) {
            String n = blockId.indexOf(':') >= 0 ? blockId.substring(blockId.indexOf(':') + 1) : blockId;
            if (n.equals("short_grass") || n.equals("grass") || n.equals("tall_grass") || n.equals("fern")
                    || n.equals("large_fern")) {
                k = 0;
            } else if (n.endsWith("_tulip") || n.equals("dandelion") || n.equals("poppy") || n.equals("blue_orchid")
                    || n.equals("allium") || n.equals("azure_bluet") || n.equals("oxeye_daisy") || n.equals("cornflower")
                    || n.equals("lily_of_the_valley") || n.equals("wither_rose") || n.equals("torchflower")
                    || n.equals("sunflower") || n.equals("lilac") || n.equals("rose_bush") || n.equals("peony")
                    || n.equals("pink_petals") || n.equals("wildflowers") || n.equals("pitcher_plant")
                    || n.endsWith("eyeblossom")) {
                k = 1;
            } else if (n.equals("leaf_litter") || n.equals("dead_bush") || n.equals("bush") || n.equals("firefly_bush")
                    || n.equals("short_dry_grass") || n.equals("tall_dry_grass")) {
                k = 2;
            } else if (n.equals("seagrass") || n.equals("tall_seagrass")) {
                k = 3;
            } else if (n.equals("kelp") || n.equals("kelp_plant")) {
                k = 4;
            } else {
                k = -1;
            }
            PLANT_OF.put(blockId, k);
        }
        return k;
    }

    public static boolean hidePlant(int kind) {
        boolean hide = kind >= 0 && (plantMask & (1 << kind)) != 0;
        if (hide && plantHits < 1000) {
            plantHits++;
        }
        return hide;
    }

    // ---- MC Tiers: a player's PvP tier next to their name

    /** The tier tag for a player (like "HT3"), or null. */
    public static String tierText(String uuid) {
        return net.peregrine.client.core.modules.McTiers.text(uuid);
    }

    /** Its color (RGB). */
    public static int tierColor(String uuid) {
        return net.peregrine.client.core.modules.McTiers.color(uuid);
    }

    public static boolean tiersInTab() {
        return net.peregrine.client.core.modules.McTiers.inTab();
    }

    public static boolean tiersOverhead() {
        return net.peregrine.client.core.modules.McTiers.overhead();
    }

    // ---- Cosmetics: Peregrine capes and badges

    public static int capeHits, badgeHits, clockHits, equipHits;

    /** The cape texture name ("falcon") for a player, or null to leave theirs alone. */
    public static String capeFor(String uuid, boolean self) {
        String c = net.peregrine.client.core.modules.Cosmetics.cape(uuid, self);
        if (c != null && capeHits < 1000) {
            capeHits++;
        }
        return c;
    }

    /** The badge glyph for before a player's name, or null. */
    public static String badgeFor(String uuid, boolean self) {
        String b = net.peregrine.client.core.modules.Cosmetics.badge(uuid, self);
        if (b != null && badgeHits < 1000) {
            badgeHits++;
        }
        return b;
    }

    // ---- Item switch: how far the hand moves each tick when you change items (Minecraft: 0.4)

    public static volatile float equipStep = 0.4f;

    public static float equipStep(float vanilla) {
        if (equipStep == 0.4f) {
            return vanilla;
        }
        if (equipHits < 1000) {
            equipHits++;
        }
        return vanilla < 0 ? -equipStep : equipStep;
    }

    /** Time changer on clocks (26.1+): the clock's total ticks with the time of day swapped. */
    public static long clockTicks(long total) {
        long t = timeOverride;
        if (t < 0) {
            return total;
        }
        if (clockHits < 1000) {
            clockHits++;
        }
        return Math.floorDiv(total, 24000L) * 24000L + t;
    }

    private static final String[] DYES = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
        "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
    private static final int[] DYE_RGB = {0xF9FFFE, 0xF9801D, 0xC74EBD, 0x3AB3DA, 0xFED83D, 0x80C71F, 0xF38BAA, 0x474F52,
        0x9D9D97, 0x169C9C, 0x8932B8, 0x3C44AA, 0x835432, 0x5E7C16, 0xB02E26, 0x3A3A44};

    /** A shulker box's color from its item id (plain ones are purple). */
    public static int shulkerTint(String itemId) {
        String n = itemId.indexOf(':') >= 0 ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
        for (int i = 0; i < DYES.length; i++) {
            if (n.equals(DYES[i] + "_shulker_box")) {
                return DYE_RGB[i];
            }
        }
        return 0x976997;
    }
}
