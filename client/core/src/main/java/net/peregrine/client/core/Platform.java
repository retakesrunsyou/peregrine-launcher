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
