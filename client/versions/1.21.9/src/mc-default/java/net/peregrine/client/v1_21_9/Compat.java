package net.peregrine.client.v1_21_9;

import net.minecraft.client.Minecraft;

/** What differs between the Minecraft versions this adapter covers (this copy: default). */
public final class Compat {

    private Compat() {
    }

    /** True while the F3 screen is open (not just a debug option such as hitboxes switched on). */
    public static boolean f3Open(Minecraft mc) {
        return mc.debugEntries.isF3Visible();
    }

    /** A sound's id ("minecraft:entity.generic.explode"). */
    public static String soundId(net.minecraft.client.resources.sounds.SoundInstance sound) {
        return String.valueOf(sound.getLocation());
    }

    /** Chunk fade-in time, where Minecraft has it. */
    public static boolean setChunkFade(double seconds) {
        return false;  // Minecraft added the fade-in in 1.21.11
    }

    /** Drops a texture so it's read again on next use (the glint after a color change). */
    public static void releaseTexture(net.minecraft.client.Minecraft mc, String path) {
        mc.getTextureManager().release(net.minecraft.resources.ResourceLocation.withDefaultNamespace(path));
    }
}
