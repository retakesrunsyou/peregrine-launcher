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

    /** An id for a texture Peregrine makes (pictures from files). */
    public static Object textureId(String namespace, String path) {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    public static void registerTexture(Object id, net.minecraft.client.renderer.texture.AbstractTexture texture) {
        net.minecraft.client.Minecraft.getInstance().getTextureManager().register((net.minecraft.resources.ResourceLocation) id, texture);
    }

    /** Draws part of a texture in the menus (u, v and sizes in units of a texSize x texSize texture). */
    public static void blit(net.minecraft.client.gui.GuiGraphics g, String id, int x, int y, int w, int h, float u, float v,
                            int uw, int vh, int texSize, int argb) {
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, net.minecraft.resources.ResourceLocation.parse(id), x, y, u, v, w, h, uw, vh,
                texSize, texSize, argb);
    }
}
