package net.peregrine.client.v26_1;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

/**
 * "Your own" cape: frames made in the launcher's Locker (up to 4096 x 2048 for a still picture).
 * The pictures are read on a background thread, then handed to the graphics card a frame at a
 * time as they're first shown, so a big cape or a long video never stalls the game. When you
 * make a new cape in the launcher the old one's textures are let go.
 */
final class CustomCape {

    /** "custom_<version>_<frame>" -> the texture it's registered as. */
    private static final Map<String, Identifier> READY = new ConcurrentHashMap<String, Identifier>();
    /** Read from disk, waiting to be uploaded. */
    private static final Map<String, NativeImage> READ = new ConcurrentHashMap<String, NativeImage>();
    private static volatile String loading = "";
    private static volatile Identifier lastShown;

    private CustomCape() {
    }

    /** The texture for a frame, or the last frame shown while this one is still being read (null at first). */
    static Identifier id(String name, Path png) {
        Identifier id = READY.get(name);
        if (id != null) {
            lastShown = id;
            return id;
        }
        String version = version(name);
        if (!version.equals(loading)) {
            start(version, png);
        }
        NativeImage img = READ.remove(name);
        Minecraft mc = Minecraft.getInstance();
        if (img != null && mc.isSameThread()) {
            try {
                boolean sharp = img.getWidth() > 256;   // a sharp picture: smooth when scaled (pixel art stays crisp)
                AbstractTexture tex = (AbstractTexture) Images.newTexture(img);
                if (sharp) {
                    try {
                        tex.getClass().getMethod("setFilter", boolean.class, boolean.class).invoke(tex, true, false);
                    } catch (Throwable ignored) {
                        // keep the default
                    }
                }
                id = Identifier.fromNamespaceAndPath("peregrine-client", "custom_cape/" + name.toLowerCase());
                mc.getTextureManager().register(id, tex);
                READY.put(name, id);
                lastShown = id;
                return id;
            } catch (Throwable t) {
                img.close();
                System.err.println("[Peregrine] couldn't show your cape: " + t);
            }
        } else if (img != null) {
            READ.put(name, img);   // not the render thread: later
        }
        Identifier last = lastShown;
        return last != null && last.getPath().startsWith("custom_cape/custom_" + version + "_") ? last : null;
    }

    private static String version(String name) {
        int a = name.indexOf('_'), b = name.lastIndexOf('_');
        return a >= 0 && b > a ? name.substring(a + 1, b) : "";
    }

    /** A new cape: forget the old one and read every frame of this one in the background. */
    private static synchronized void start(final String version, Path png) {
        if (version.equals(loading)) {
            return;
        }
        loading = version;
        final Minecraft mc = Minecraft.getInstance();
        final List<Identifier> old = new ArrayList<Identifier>(READY.values());
        READY.clear();
        for (NativeImage img : READ.values()) {
            img.close();
        }
        READ.clear();
        lastShown = null;
        if (!old.isEmpty()) {
            mc.execute(() -> {
                for (Identifier id : old) {
                    mc.getTextureManager().release(id);
                }
            });
        }
        final Path dir = png == null ? null : png.getParent();
        if (dir == null) {
            return;
        }
        Thread t = new Thread(() -> {
            for (int i = 0; i < 1000 && version.equals(loading); i++) {
                Path f = dir.resolve(String.format("frame_%03d.png", i));
                if (!Files.isRegularFile(f)) {
                    break;
                }
                try (InputStream in = Files.newInputStream(f)) {
                    NativeImage img = NativeImage.read(in);
                    if (version.equals(loading)) {
                        READ.put("custom_" + version + "_" + i, img);
                    } else {
                        img.close();
                    }
                } catch (Throwable e) {
                    System.err.println("[Peregrine] couldn't read your cape (" + f.getFileName() + "): " + e);
                    break;
                }
            }
        }, "Peregrine cape");
        t.setDaemon(true);
        t.start();
    }
}
