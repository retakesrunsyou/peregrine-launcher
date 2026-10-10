package net.peregrine.client.v1_21_9;

import net.minecraft.client.Minecraft;

/** What differs between the Minecraft versions this adapter covers (this copy: 1.21.11). */
public final class Compat {

    private Compat() {
    }

    /** True while the F3 screen is open (not just a debug option such as hitboxes switched on). */
    public static boolean f3Open(Minecraft mc) {
        return mc.debugEntries.isOverlayVisible();
    }

    /** A sound's id ("minecraft:entity.generic.explode"). */
    public static String soundId(net.minecraft.client.resources.sounds.SoundInstance sound) {
        return String.valueOf(sound.getIdentifier());
    }

    /** Chunk fade-in time, where Minecraft has it. */
    public static boolean setChunkFade(double seconds) {
        net.minecraft.client.Minecraft.getInstance().options.chunkSectionFadeInTime().set(seconds);
        return true;
    }
}
