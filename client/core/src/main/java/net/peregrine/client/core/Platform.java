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
    enum Screen { SINGLEPLAYER, MULTIPLAYER, OPTIONS, PEREGRINE_MENU, QUIT, NONE, TITLE }

    void openScreen(Screen which);

    /** Whether that screen is the one showing now (NONE = no screen, playing). */
    boolean showing(Screen which);

    /** The signed-in player's name. */
    String playerName();

    /** e.g. "1.21.1". */
    String minecraftVersion();

    /** Ping to the current server in ms, or -1 in singleplayer. */
    int ping();

    /** Address of the current server, or null in singleplayer. */
    String serverAddress();

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
    enum Option { TOGGLE_SNEAK, STATIC_FOV, STEADY_CAMERA, NO_MENU_BLUR, FEWER_PARTICLES, CHUNK_BORDERS, HITBOXES }

    /** Turns an option on, or back to Minecraft's default when off. */
    void setOption(Option option, boolean on);

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
}
