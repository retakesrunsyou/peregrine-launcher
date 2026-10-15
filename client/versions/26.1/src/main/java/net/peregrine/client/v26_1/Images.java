package net.peregrine.client.v26_1;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;

/** Pictures from files (your skin, your main menu picture) turned into textures the menus can draw. */
final class Images {

    private Images() {
    }

    static String load(Path png) {
        try (InputStream in = Files.newInputStream(png)) {
            NativeImage img = NativeImage.read(in);
            Object tex = newTexture(img);
            try {  // smooth when stretched (where this version allows choosing)
                tex.getClass().getMethod("setFilter", boolean.class, boolean.class).invoke(tex, true, false);
            } catch (Throwable ignored) {
                // keep the default
            }
            String path = "dynamic/" + Integer.toHexString(png.toAbsolutePath().toString().hashCode());
            Object id = net.minecraft.resources.Identifier.fromNamespaceAndPath("peregrine-client", path);
            Minecraft.getInstance().getTextureManager().register((net.minecraft.resources.Identifier) id, (net.minecraft.client.renderer.texture.AbstractTexture) tex);
            return id.toString();
        } catch (Throwable t) {
            System.err.println("[Peregrine] couldn't load " + png.getFileName() + ": " + t);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Object newTexture(NativeImage img) throws Exception {
        for (Constructor<?> c : DynamicTexture.class.getConstructors()) {
            Class<?>[] p = c.getParameterTypes();
            if (p.length == 1 && p[0] == NativeImage.class) {
                return c.newInstance(img);
            }
            if (p.length == 2 && p[0] == Supplier.class && p[1] == NativeImage.class) {
                return c.newInstance((Supplier<String>) () -> "peregrine-picture", img);
            }
        }
        throw new IllegalStateException("no way to make a texture here");
    }
}
