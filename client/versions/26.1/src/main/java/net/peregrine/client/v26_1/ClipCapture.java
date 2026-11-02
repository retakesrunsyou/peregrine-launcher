package net.peregrine.client.v26_1;

import java.nio.ByteBuffer;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.peregrine.client.core.modules.Clips;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL45;

/**
 * Clips in 26.1: each finished frame (when the recorder wants one) is scaled down to the clip's
 * size and flipped upright on the graphics card, then read back without waiting: into one of
 * three pixel buffers, picked up a couple of frames later when the copy has finished. So the
 * game never stalls for a recording frame.
 */
public final class ClipCapture {

    private static final int SLOTS = 3;
    private static int tex, fbo, readFbo, w, h;
    private static final int[] pbo = new int[SLOTS];
    private static final long[] fence = new long[SLOTS];
    private static int next;
    private static boolean broken;
    // auto clips: what you last hit
    private static java.lang.ref.WeakReference<Entity> lastHit = new java.lang.ref.WeakReference<Entity>(null);
    private static long lastHitAt;

    private ClipCapture() {
    }

    /** At the end of every frame, before it goes to the screen. */
    public static void frame() {
        if (broken) {
            return;
        }
        try {
            collect();
            Minecraft mc = Minecraft.getInstance();
            int[] size = Clips.wantedSize(mc.getWindow().getWidth(), mc.getWindow().getHeight());
            if (size == null || !Clips.wantFrame()) {
                return;
            }
            RenderTarget t = mc.getMainRenderTarget();
            if (t == null || !(t.getColorTexture() instanceof GlTexture)) {
                return;
            }
            setup(size[0], size[1]);
            int slot = next;
            if (fence[slot] != 0) {
                return;   // the reader is behind: skip this frame rather than wait
            }
            next = (next + 1) % SLOTS;
            // scale down and flip upright in one copy
            GlStateManager._disableScissorTest();
            GlStateManager._colorMask(15);
            GL45.glNamedFramebufferTexture(readFbo, GL30.GL_COLOR_ATTACHMENT0, ((GlTexture) t.getColorTexture()).glId(), 0);
            GL45.glBlitNamedFramebuffer(readFbo, fbo, 0, 0, t.width, t.height, 0, h, w, 0, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
            // start reading it back into a pixel buffer (finishes in the background)
            GlStateManager._pixelStore(GL11.GL_PACK_ALIGNMENT, 4);
            GlStateManager._pixelStore(GL11.GL_PACK_ROW_LENGTH, 0);
            GlStateManager._pixelStore(GL11.GL_PACK_SKIP_ROWS, 0);
            GlStateManager._pixelStore(GL11.GL_PACK_SKIP_PIXELS, 0);
            GlStateManager._glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbo[slot]);
            GL45.glGetTextureImage(tex, 0, GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, w * h * 4, 0L);
            GlStateManager._glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            fence[slot] = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        } catch (Throwable e) {
            broken = true;
            System.err.println("[Peregrine] clip recording stopped: " + e);
        }
    }

    /** Hands finished read-backs to the recorder. */
    private static void collect() {
        for (int i = 0; i < SLOTS; i++) {
            long f = fence[i];
            if (f == 0) {
                continue;
            }
            int r = GL32.glClientWaitSync(f, 0, 0L);
            if (r != GL32.GL_ALREADY_SIGNALED && r != GL32.GL_CONDITION_SATISFIED) {
                continue;
            }
            GL32.glDeleteSync(f);
            fence[i] = 0;
            ByteBuffer data = GL45.glMapNamedBufferRange(pbo[i], 0, (long) w * h * 4, GL30.GL_MAP_READ_BIT);
            if (data != null) {
                Clips.frame(data, w, h);
                GL45.glUnmapNamedBuffer(pbo[i]);
            }
        }
    }

    private static void setup(int nw, int nh) {
        if (fbo == 0) {
            fbo = GL45.glCreateFramebuffers();
            readFbo = GL45.glCreateFramebuffers();
        }
        if (tex != 0 && nw == w && nh == h) {
            return;
        }
        for (int i = 0; i < SLOTS; i++) {
            if (fence[i] != 0) {
                GL32.glDeleteSync(fence[i]);
                fence[i] = 0;
            }
            if (pbo[i] != 0) {
                GL15.glDeleteBuffers(pbo[i]);
            }
            pbo[i] = GL45.glCreateBuffers();
            GL45.glNamedBufferData(pbo[i], (long) nw * nh * 4, GL15.GL_STREAM_READ);
        }
        if (tex != 0) {
            GL11.glDeleteTextures(tex);
        }
        tex = GL45.glCreateTextures(GL11.GL_TEXTURE_2D);
        GL45.glTextureStorage2D(tex, 1, GL11.GL_RGBA8, nw, nh);
        GL45.glNamedFramebufferTexture(fbo, GL30.GL_COLOR_ATTACHMENT0, tex, 0);
        w = nw;
        h = nh;
        next = 0;
    }

    // ------------------------------------------------------------------ auto clips

    /** You hit something. */
    public static void attacked(Entity target) {
        lastHit = new java.lang.ref.WeakReference<Entity>(target);
        lastHitAt = System.currentTimeMillis();
    }

    /** True once if what you hit in the last few seconds has died. */
    public static boolean killedSomething() {
        Entity e = lastHit.get();
        if (e == null || System.currentTimeMillis() - lastHitAt > 4000) {
            return false;
        }
        if (e instanceof LivingEntity && ((LivingEntity) e).isDeadOrDying()) {
            lastHit = new java.lang.ref.WeakReference<Entity>(null);
            return true;
        }
        return false;
    }
}
