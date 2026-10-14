package net.peregrine.client.v1_21_2;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.peregrine.client.core.Hooks;

/**
 * Block place animation: a block you place stays out of the world's mesh for a moment
 * while a copy of it grows from the middle to full size; then the real one is let in.
 * Holding the mesh back works with Sodium too (both build from Minecraft's dirty marks).
 */
public final class PlaceAnim {

    static final class Anim {
        final BlockPos pos;
        final long start = System.currentTimeMillis();
        volatile BlockState state;
        BlockGetter level;
        BlockState old;
        int flags;
        boolean held, released;
        /** Mesh updates held back while it grows in, done when it's let in. */
        final java.util.List<Runnable> replays = new java.util.ArrayList<>();

        Anim(BlockPos pos) {
            this.pos = pos;
        }
    }

    private static final Map<BlockPos, Anim> ANIMS = new ConcurrentHashMap<>();
    /** How long the copy stays after the real block is let in (the mesh rebuild can lag a little). */
    private static final long LINGER = 450;
    private static boolean releasing;

    private PlaceAnim() {
    }

    /** You're placing a block here (called before Minecraft puts it in the world). */
    public static void begin(BlockPos pos) {
        if (Hooks.placeAnimMs > 0 && ANIMS.size() < 24) {
            ANIMS.put(pos.immutable(), new Anim(pos.immutable()));
        }
    }

    /** The placement didn't happen: forget it (and let anything held back through). */
    public static void cancel(BlockPos pos, boolean placed) {
        if (!placed) {
            Anim a = ANIMS.remove(pos);
            if (a != null) {
                release(a);
            }
        }
    }

    /** Minecraft says a block changed: hold the mesh update back while that block is growing in. */
    public static boolean holdBlock(LevelRenderer lr, BlockGetter level, BlockPos pos, BlockState old, BlockState now, int flags) {
        if (releasing || ANIMS.isEmpty()) {
            return false;
        }
        Anim a = ANIMS.get(pos);
        if (a == null || a.released) {
            return false;
        }
        if (now.isAir() || now.getRenderShape() != RenderShape.MODEL) {  // broken (or nothing to draw): stop now
            ANIMS.remove(pos);
            release(a);
            return false;
        }
        a.state = now;
        a.level = level;
        a.old = old;
        a.flags = flags;
        a.held = true;
        if (Hooks.placeHits < 1000) {
            Hooks.placeHits++;
        }
        return true;
    }

    /**
     * A mesh update for sections (x, y, z) to (x2, y2, z2): hold it back if a block growing in
     * sits there, and do it once the block is let in. Light changes and neighbours land here.
     */
    public static boolean holdSections(int x, int y, int z, int x2, int y2, int z2, Runnable replay) {
        if (releasing || ANIMS.isEmpty()) {
            return false;
        }
        for (Anim a : ANIMS.values()) {
            int sx = SectionPos.blockToSectionCoord(a.pos.getX()), sy = SectionPos.blockToSectionCoord(a.pos.getY()),
                    sz = SectionPos.blockToSectionCoord(a.pos.getZ());
            if (!a.released && sx >= x && sx <= x2 && sy >= y && sy <= y2 && sz >= z && sz <= z2) {
                a.replays.add(replay);
                return true;
            }
        }
        return false;
    }

    private static void release(Anim a) {
        if (a.released) {
            return;
        }
        a.released = true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.levelRenderer == null) {
            return;
        }
        releasing = true;
        try {
            if (a.held) {
                mc.levelRenderer.blockChanged(a.level, a.pos, a.old, a.state, a.flags);
            }
            for (Runnable r : a.replays) {
                r.run();
            }
            a.replays.clear();
        } finally {
            releasing = false;
        }
    }

    /** Lets in blocks whose time is up; drops old ones (each client tick, and while drawing). */
    public static void tick() {
        if (ANIMS.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        int ms = Math.max(1, Hooks.placeAnimMs);
        for (Iterator<Anim> it = ANIMS.values().iterator(); it.hasNext(); ) {
            Anim a = it.next();
            long t = now - a.start;
            if (t >= ms || Hooks.placeAnimMs <= 0) {
                release(a);
            }
            if (t >= ms + LINGER || (!a.held && t >= ms)) {
                it.remove();
            }
        }
    }

    /** Draws the growing copies (pose: camera-relative, like the block outline). */
    public static void draw(PoseStack pose, MultiBufferSource buffers, double camX, double camY, double camZ) {
        tick();
        if (ANIMS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean drew = false;
        for (Anim a : ANIMS.values()) {
            BlockState s = a.state;
            if (s == null) {
                continue;
            }
            long t = now - a.start;
            // growing, then just inside full size until the real block (same shape) covers it
            float k = t < Hooks.placeAnimMs ? Hooks.placeScale(t) : 0.998f;
            pose.pushPose();
            pose.translate(a.pos.getX() - camX + 0.5, a.pos.getY() - camY + 0.5, a.pos.getZ() - camZ + 0.5);
            pose.scale(k, k, k);
            pose.translate(-0.5, -0.5, -0.5);
            mc.getBlockRenderer().renderSingleBlock(s, pose, buffers, light(a.pos), OverlayTexture.NO_OVERLAY);
            pose.popPose();
            if (Hooks.placeDrawHits < 100000) {
                Hooks.placeDrawHits++;
            }
            drew = true;
        }
        if (drew && buffers instanceof MultiBufferSource.BufferSource src) {
            src.endBatch();
        }
    }

    /** The brightest light next to the block (a solid block itself sits in the dark). */
    private static int light(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        int sky = 0, block = 0;
        for (Direction d : Direction.values()) {
            int c = LevelRenderer.getLightColor(mc.level, pos.relative(d));
            sky = Math.max(sky, (c >> 20) & 15);
            block = Math.max(block, (c >> 4) & 15);
        }
        return sky << 20 | block << 4;
    }
}
