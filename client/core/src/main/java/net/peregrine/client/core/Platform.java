package net.peregrine.client.core;

import java.nio.file.Path;
import java.util.List;

/**
 * Everything the core needs from Minecraft. Each Minecraft version has a small
 * adapter that implements this; the features themselves never touch Minecraft.
 *
 * Keep this Java 8 compatible so old versions (like 1.8.9) can share the core.
 */
public interface Platform {

    enum Key { FORWARD, LEFT, BACK, RIGHT, JUMP, SNEAK, ATTACK, USE }

    /** True while a world is loaded and the player exists. */
    boolean inWorld();

    int fps();

    double x();

    double y();

    double z();

    /** "North", "South", "East" or "West". */
    String facing();

    boolean isDown(Key key);

    /** Worn armor (helmet first) then the held item; empty slots skipped. */
    List<ItemInfo> armor();

    List<EffectInfo> effects();

    /** Holds (or releases) the sprint key, for toggle sprint. */
    void setSprintHeld(boolean held);

    boolean zoomKeyDown();

    double gamma();

    void setGamma(double value);

    /** Screen size in GUI-scaled pixels (what Draw coordinates use). */
    int screenWidth();

    int screenHeight();

    Path configFile();

    // ---- added for the main menu and server HUD items

    /** Screens the core can ask the game to open. */
    enum Screen { SINGLEPLAYER, MULTIPLAYER, OPTIONS, PEREGRINE_MENU, QUIT, NONE, TITLE, REALMS, LANGUAGE, ACCESSIBILITY,
        /** Minecraft's own pause menu (advancements, statistics, open to LAN...). */
        VANILLA_PAUSE }

    void openScreen(Screen which);

    /** Whether that screen is the one showing now (NONE = no screen, playing). */
    boolean showing(Screen which);

    /** For the self-test: puts a stone block 3 blocks in front of you on your screen only, as if placed
     *  (so the place animation can be seen). Returns false where that isn't possible. */
    default boolean testPlaceBlock() {
        return false;
    }

    /**
     * Loads a PNG file as a texture for Draw.texture. Returns its id, or null if it can't be
     * read. Called again for the same file, it replaces the old picture.
     */
    default String loadImage(java.nio.file.Path png) {
        return null;
    }

    /** Your Minecraft session token (for Mojang's own services), or null when offline. */
    default String accessToken() {
        return null;
    }

    /** Whether the main menu can show your player (the adapter places it at TitleMenu.playerBox()). */
    default boolean supportsPlayerPreview() {
        return false;
    }

    /** Whether joinServer works here. */
    default boolean supportsJoin() {
        return false;
    }

    /** Connects to a server (from the title screen's "Jump back in"). */
    default void joinServer(String address, String name) {
    }

    /** Leaves the world or server, saving like Minecraft's own button does. */
    default void leaveWorld() {
    }

    /** True in singleplayer (the world runs on this computer). */
    default boolean localWorld() {
        return serverAddress() == null;
    }

    /** The signed-in player's name. */
    String playerName();

    /** e.g. "1.21.1". */
    String minecraftVersion();

    /** Ping to the current server in ms, or -1 in singleplayer. */
    int ping();

    /** Address of the current server, or null in singleplayer. */
    String serverAddress();

    /** The server's name as in your server list (null if it has none, e.g. direct connect). */
    default String serverName() {
        return null;
    }

    /** The server's icon (PNG) if the game has it, else null. */
    default byte[] serverIcon() {
        return null;
    }

    /** In-game day number, starting at 1. */
    long worldDay();

    // ---- added in batch 2

    /** Time of day in ticks, 0-23999 (0 = sunrise). */
    long worldTime();

    /** Biome id at the player, like "minecraft:plains". */
    String biome();

    /** Horizontal look direction in Minecraft degrees (0 = south, 90 = west). */
    float yaw();

    int food();

    float saturation();

    /** Number of potions (drinkable, splash and lingering) in the inventory. */
    int potionCount();

    /** Active resource packs, top of the list first. */
    List<String> resourcePacks();

    /** Built-in Minecraft settings that modules can switch. */
    enum Option { TOGGLE_SNEAK, STATIC_FOV, STEADY_CAMERA, NO_MENU_BLUR, FEWER_PARTICLES, CHUNK_BORDERS, HITBOXES, NO_HURT_CAM }

    /** Turns an option on, or back to Minecraft's default when off. */
    void setOption(Option option, boolean on);

    /** Writes Minecraft's options to options.txt now, so a setting Peregrine changed back stays changed. */
    default void saveOptions() {
    }

    // ---- added in batch 3 (defaults keep older adapters and tests working)

    /** The player's health in half-hearts (20 = full), or -1 outside a world. */
    default float health() {
        return -1;
    }

    default float maxHealth() {
        return 20;
    }

    /** Real pixels per GUI pixel (Minecraft's GUI Scale, worked out from the window). */
    default double guiScale() {
        return 1;
    }

    /** Dimension id, like "minecraft:the_nether", or "" if unknown. */
    default String dimension() {
        return "";
    }

    /** How many of something are in the inventory: "totem" or "arrow". */
    default int itemCount(String what) {
        return 0;
    }

    // ---- added in batch 4

    /** Vertical look angle in degrees (-90 = straight up, 90 = straight down). */
    default float pitch() {
        return 0;
    }

    /** Distance from your eyes to the entity under the crosshair, or -1 if none. */
    default double targetDistance() {
        return -1;
    }

    /** Name of the entity under the crosshair, or "". */
    default String targetName() {
        return "";
    }

    /** Health of the entity under the crosshair (half-hearts), or -1 if it has none. */
    default float targetHealth() {
        return -1;
    }

    default float targetMaxHealth() {
        return 20;
    }

    /** Name of the block under the crosshair, or "". */
    default String lookedAtBlock() {
        return "";
    }

    /** How many of the held item you have in total, or 0 with an empty hand. */
    default int heldItemCount() {
        return 0;
    }

    default String heldItemName() {
        return "";
    }

    /** Players in the tab list, or -1 outside a world. */
    default int onlinePlayers() {
        return -1;
    }

    default int xpLevel() {
        return 0;
    }

    /** Progress to the next level, 0-1. */
    default float xpProgress() {
        return 0;
    }

    /** Block light where you stand (0-15), or -1 outside a world. Mobs spawn at 0. */
    default int lightLevel() {
        return -1;
    }

    /** Ticks left of the red flash after you're hurt (0 when not hurt). */
    default int hurtTime() {
        return 0;
    }

    // ---- added in batch 5 (render hooks)

    /** Rebuilds every loaded chunk's mesh (like F3+A), after a block-look setting changes. */
    default void reloadChunks() {
    }

    /** An item to show as an icon ("minecraft:clock"), as a stack for Draw.item, or null. */
    default Object icon(String itemId) {
        return null;
    }

    /** Chunk animation: how long new chunks take to fade in, in seconds (false where Minecraft can't). */
    default boolean setChunkFade(double seconds) {
        return false;
    }

    /** Anti-AFK actions: hold jump, swing your hand, turn your view a little. */
    default void afkJump(boolean held) {
    }

    default void afkSwing() {
    }

    default void afkLook(float yaw, float pitch) {
    }

    /** Reloads resource packs (like F3+T), after a change to what they may replace. */
    default void reloadResources() {
    }

    /** Is this key (a GLFW key code) held right now, while playing with no screen open? */
    default boolean keyDown(int key) {
        return false;
    }

    /** Is the freelook key (Left Alt by default) held, while playing? */
    default boolean freelookKeyDown() {
        return false;
    }

    /** 0 = first person, 1 = third person behind, 2 = third person in front. */
    default int cameraMode() {
        return 0;
    }

    default void setCameraMode(int mode) {
    }

    // ---- performance (Stable FPS)

    /** Render distance in chunks, or -1 if unknown. */
    default int renderDistance() {
        return -1;
    }

    default void setRenderDistance(int chunks) {
    }

    /** Entity distance, 0.5 - 5.0 (1.0 = Minecraft's default). */
    default double entityDistance() {
        return 1.0;
    }

    default void setEntityDistance(double scale) {
    }

    /** 0 = all particles, 1 = decreased, 2 = minimal. */
    default int particleLevel() {
        return 0;
    }

    default void setParticleLevel(int level) {
    }

    // ---- batch 6

    /** Every block in the inventory (all block items), or 0. */
    default int blockItemCount() {
        return 0;
    }

    /**
     * Minecraft's chat size settings, each 0-1 as in its Chat Settings screen:
     * "scale" (text size), "width", "height" (while chat is open), "unfocused" (height while closed).
     * Returns -1 if unknown.
     */
    default double chatOption(String key) {
        return -1;
    }

    default void setChatOption(String key, double value) {
    }

    /** Self-test only: puts bedrock and a dropped item in front of the player, on this client only. */
    default void testScene() {
    }

    /** Colors the flash on mobs and players when they're hurt; 0 = Minecraft's red. */
    default void setHitColor(int argb) {
    }

    /** False for features this Minecraft version can't do; they're hidden from the menu. */
    default boolean supports(String moduleId) {
        return true;
    }

    // ---- batch 9

    /** Sends a chat message, or a command if it starts with "/". */
    default void sendChat(String message) {
    }

    /** How many of an item (by id, like "minecraft:elytra" or "elytra") you're carrying. */
    default int countItem(String itemId) {
        return 0;
    }

    /** Items on cooldown right now: {stack (for Draw.item), Float fraction left (1 = just started), String item id}. */
    default List<Object[]> cooldowns() {
        return java.util.Collections.emptyList();
    }

    /** The signed-in player's UUID without dashes, or "". */
    default String playerUuid() {
        return "";
    }

    // ---- batch 9b

    /**
     * Where a point in the world is on screen: {x, y} in GUI pixels plus its
     * distance from the camera, or null when it's behind you.
     */
    default float[] project(double x, double y, double z) {
        return null;
    }

    /** Lit TNT near you: {x, y, z, fuse ticks left}. */
    default List<double[]> primedTnt() {
        return java.util.Collections.emptyList();
    }

    /** Which world you're in, for per-world waypoints: the server address or the save's name. */
    default String worldKey() {
        String s = serverAddress();
        return s != null ? "server:" + s : "world";
    }

    /** Entities in the world near you (for the F3 panel), or -1. */
    default int entityCount() {
        return -1;
    }

    /** Re-reads textures (by path in the minecraft namespace) without a full resource reload. */
    default void refreshTextures(String... paths) {
        reloadResources();
    }

    final class ItemInfo {
        public final Object stack; // the version's ItemStack, passed back to Draw.item
        public final int damage;
        public final int maxDamage;

        public ItemInfo(Object stack, int damage, int maxDamage) {
            this.stack = stack;
            this.damage = damage;
            this.maxDamage = maxDamage;
        }

        public boolean damageable() {
            return maxDamage > 0;
        }

        public int remaining() {
            return maxDamage - damage;
        }
    }

    final class EffectInfo {
        public final String name;
        public final int amplifier;
        public final int durationTicks;
        public final boolean infinite;

        public EffectInfo(String name, int amplifier, int durationTicks, boolean infinite) {
            this.name = name;
            this.amplifier = amplifier;
            this.durationTicks = durationTicks;
            this.infinite = infinite;
        }
    }

    // ---- flight boost

    /** 0 survival, 1 creative, 2 adventure, 3 spectator, -1 not in a world. */
    default int gameMode() {
        return -1;
    }

    default boolean flying() {
        return false;
    }

    /** Minecraft's flying speed (0.05 normally). */
    default float flySpeed() {
        return 0.05f;
    }

    default void setFlySpeed(float speed) {
    }

    // ---- inventory HUD

    /** The 36 inventory slots (0-8 the hotbar, 9-35 the rest): the version's ItemStack, or null if empty. */
    default List<Object> inventory() {
        return java.util.Collections.emptyList();
    }

    /** {count, damage, max damage} of one of the version's ItemStacks. */
    default int[] stackInfo(Object stack) {
        return new int[] {1, 0, 0};
    }

    // ---- mods, shaders and resource packs in the menu

    /** The instance folder (where mods, shaderpacks and resourcepacks live). */
    default java.nio.file.Path gameDir() {
        java.nio.file.Path c = configFile();
        return c == null ? java.nio.file.Paths.get(".") : c.toAbsolutePath().getParent().getParent();
    }

    /** Resource packs switched on, bottom first, as Minecraft names them ("file/Faithful.zip", "vanilla"...). */
    default List<String> selectedPackIds() {
        return java.util.Collections.emptyList();
    }

    /** Switches exactly these packs on (bottom first) and reloads the game's resources. */
    default void setSelectedPacks(List<String> ids) {
    }

    /** The block state at a position, as Minecraft writes it ("minecraft:oak_stairs[facing=north,...]"), or null when that spot isn't loaded. */
    default String blockStateAt(int x, int y, int z) {
        return null;
    }
}
