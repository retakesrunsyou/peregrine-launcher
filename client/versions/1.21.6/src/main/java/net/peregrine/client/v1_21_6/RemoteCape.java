package net.peregrine.client.v1_21_6;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Other players' own capes: all of a cape's frames ("remote_<hash>_<frame>") are read from their
 * downloaded files on a background thread, in order, then handed to the graphics card on the
 * render thread as they're first shown. Until a frame is ready the last one shown stays up, so a
 * moving cape never flickers. Sharp capes are drawn smoothly. At most BUDGET bytes are kept on the
 * graphics card: past that, the capes seen longest ago are let go (read again when seen).
 */
final class RemoteCape {

    private static final long BUDGET = 256L * 1024 * 1024;
    private static final int READ_AHEAD = 48;
    private static final Map<String, ResourceLocation> READY = new ConcurrentHashMap<String, ResourceLocation>();
    private static final Map<String, Long> BYTES = new ConcurrentHashMap<String, Long>();
    private static final Map<String, NativeImage> READ = new ConcurrentHashMap<String, NativeImage>();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
    private static final Map<String, ResourceLocation> LAST = new ConcurrentHashMap<String, ResourceLocation>();
    private static final Map<String, Long> SEEN = new ConcurrentHashMap<String, Long>();
    private static long used;

    private RemoteCape() {
    }

    private static String hashOf(String name) {
        int a = name.indexOf('_'), b = name.lastIndexOf('_');
        return a >= 0 && b > a ? name.substring(a + 1, b) : name;
    }

    /** The texture for a frame, or the last frame of the same cape shown while it's read (null at first). */
    static ResourceLocation id(String name, Path png) {
        String hash = hashOf(name);
        SEEN.put(hash, System.currentTimeMillis());
        ResourceLocation id = READY.get(name);
        if (id != null) {
            LAST.put(hash, id);
            return id;
        }
        Minecraft mc = Minecraft.getInstance();
        NativeImage img = READ.remove(name);
        if (img != null && mc.isSameThread()) {
            try {
                long bytes = (long) img.getWidth() * img.getHeight() * 4;
                makeRoom(mc, hash, bytes);
                boolean sharp = img.getWidth() > 256;   // a sharp picture: smooth when scaled (pixel art stays crisp)
                AbstractTexture tex = (AbstractTexture) Images.newTexture(img);
                if (sharp) {
                    try {
                        tex.getClass().getMethod("setFilter", boolean.class, boolean.class).invoke(tex, true, false);
                    } catch (Throwable ignored) {
                        // keep the default
                    }
                }
                id = ResourceLocation.fromNamespaceAndPath("peregrine-client", "remote_cape/" + name.toLowerCase());
                mc.getTextureManager().register(id, tex);
                READY.put(name, id);
                BYTES.put(name, bytes);
                used += bytes;
                LAST.put(hash, id);
                return id;
            } catch (Throwable t) {
                img.close();
            }
        } else if (img != null) {
            READ.put(name, img);   // not the render thread: later
        } else if (png != null) {
            start(hash, png.getParent());
        }
        return LAST.get(hash);
    }

    /** Lets go of the capes seen longest ago until this frame fits. */
    private static void makeRoom(Minecraft mc, String keep, long bytes) {
        while (used + bytes > BUDGET) {
            String oldest = null;
            long when = Long.MAX_VALUE;
            for (String name : READY.keySet()) {
                String h = hashOf(name);
                Long s = SEEN.get(h);
                if (!h.equals(keep) && s != null && s < when) {
                    when = s;
                    oldest = h;
                }
            }
            if (oldest == null) {
                return;
            }
            List<String> names = new ArrayList<String>();
            for (String name : READY.keySet()) {
                if (hashOf(name).equals(oldest)) {
                    names.add(name);
                }
            }
            for (String name : names) {
                ResourceLocation gone = READY.remove(name);
                Long b = BYTES.remove(name);
                used -= b == null ? 0 : b;
                if (gone != null) {
                    mc.getTextureManager().release(gone);
                }
            }
            LAST.remove(oldest);
        }
    }

    /** Reads a cape's frames in order in the background (a few dozen ahead of what's shown). */
    private static void start(final String hash, final Path dir) {
        if (dir == null || !LOADING.add(hash)) {
            return;
        }
        Thread t = new Thread(() -> {
            try {
                for (int i = 0; i < 1000; i++) {
                    Path f = dir.resolve(String.format("frame_%03d.png", i));
                    if (!Files.isRegularFile(f)) {
                        break;
                    }
                    String name = "remote_" + hash + "_" + i;
                    if (READY.containsKey(name) || READ.containsKey(name)) {
                        continue;
                    }
                    for (int w = 0; READ.size() >= READ_AHEAD; w++) {
                        if (w > 250) {   // nobody's looking at it any more
                            return;
                        }
                        Thread.sleep(20);
                    }
                    try (InputStream in = Files.newInputStream(f)) {
                        READ.put(name, NativeImage.read(in));
                    } catch (Throwable e) {
                        break;   // a broken download: the rest is skipped
                    }
                }
            } catch (InterruptedException ignored) {
                // stopping
            } finally {
                LOADING.remove(hash);
            }
        }, "Peregrine remote cape");
        t.setDaemon(true);
        t.start();
    }
}
