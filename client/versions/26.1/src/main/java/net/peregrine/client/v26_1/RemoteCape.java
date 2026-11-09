package net.peregrine.client.v26_1;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

/**
 * Other players' own capes: each frame ("remote_<hash>_<frame>") is read from its downloaded file
 * on a background thread, then handed to the graphics card on the render thread. At most a few
 * hundred frames are kept; past that they're let go and read again when seen.
 */
final class RemoteCape {

    private static final int MAX = 400;
    private static final Map<String, Identifier> READY = new ConcurrentHashMap<String, Identifier>();
    private static final Map<String, NativeImage> READ = new ConcurrentHashMap<String, NativeImage>();
    private static final Set<String> READING = ConcurrentHashMap.newKeySet();

    private RemoteCape() {
    }

    /** The texture for a frame, or null while it's being read. */
    static Identifier id(String name, Path png) {
        Identifier id = READY.get(name);
        if (id != null) {
            return id;
        }
        Minecraft mc = Minecraft.getInstance();
        NativeImage img = READ.remove(name);
        if (img != null && mc.isSameThread()) {
            try {
                if (READY.size() >= MAX) {
                    final java.util.List<Identifier> old = new java.util.ArrayList<Identifier>(READY.values());
                    READY.clear();
                    for (Identifier o : old) {
                        mc.getTextureManager().release(o);
                    }
                }
                AbstractTexture tex = (AbstractTexture) Images.newTexture(img);
                id = Identifier.fromNamespaceAndPath("peregrine-client", "remote_cape/" + name.toLowerCase());
                mc.getTextureManager().register(id, tex);
                READY.put(name, id);
                return id;
            } catch (Throwable t) {
                img.close();
                return null;
            }
        } else if (img != null) {
            READ.put(name, img);
            return null;
        }
        if (png != null && READING.add(name)) {
            Thread t = new Thread(() -> {
                try (InputStream in = Files.newInputStream(png)) {
                    READ.put(name, NativeImage.read(in));
                } catch (Throwable e) {
                    // a broken download: it's skipped
                } finally {
                    READING.remove(name);
                }
            }, "Peregrine remote cape");
            t.setDaemon(true);
            t.start();
        }
        return null;
    }
}
