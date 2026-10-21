package net.peregrine.client.v1_21_2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Schematics;

/**
 * Ghost blocks: where the placed schematic still needs a block, a see-through copy of that
 * block (its texture, its shape's outline box) so you can see what goes where before placing it.
 */
public final class SchemGhosts {

    private static final int LIGHT = 0xF000F0;     // full brightness: ghosts glow evenly
    private static final int OVERLAY = 10 << 16;   // no hurt/white overlay
    private static final float IN = 0.002f;        // just inside the block, so nothing flickers against neighbours

    private static Map<String, Block> blocks;
    private static final Map<String, BlockState> STATES = new HashMap<String, BlockState>();
    private static final Map<BlockState, TextureAtlasSprite> SPRITES = new HashMap<BlockState, TextureAtlasSprite>();
    private static final Map<BlockState, float[]> BOXES = new HashMap<BlockState, float[]>();
    private static long cachedAt;
    private static List<Object[]> sortedFor;
    private static long sortedAt;
    private static List<Object[]> sorted = Collections.emptyList();
    private static Set<Long> filled = Collections.emptySet();

    private SchemGhosts() {
    }

    /** Whether there are ghost blocks to draw this frame. */
    public static boolean wanted() {
        return Hooks.schematicGhosts && !broken && !Schematics.ghosts().isEmpty();
    }

    private static boolean broken;  // set if drawing ever fails: then ghosts stay off instead of crashing the game

    /** Turns ghosts off for this session after a failure. */
    public static void off(Throwable e) {
        broken = true;
        System.err.println("[Peregrine] ghost blocks turned off: " + e);
    }

    /** Draws every ghost into vc (pose: camera-relative, like the block outline). */
    public static void emit(PoseStack.Pose pose, VertexConsumer vc, double cx, double cy, double cz) {
        if (broken) {
            return;
        }
        try {
            draw(pose, vc, cx, cy, cz);
        } catch (RuntimeException e) {
            broken = true;
            System.err.println("[Peregrine] ghost blocks turned off: " + e);
        }
    }

    private static void draw(PoseStack.Pose pose, VertexConsumer vc, double cx, double cy, double cz) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - cachedAt > 15000) {  // textures may have been reloaded
            SPRITES.clear();
            cachedAt = now;
        }
        List<Object[]> list = order(cx, cy, cz, now);
        int alpha = Math.max(8, Math.min(255, Hooks.schematicGhostAlpha));
        int drawn = 0;
        for (Object[] g : list) {
            int x = (Integer) g[0], y = (Integer) g[1], z = (Integer) g[2];
            BlockState s = state((String) g[3]);
            if (s == null) {
                continue;
            }
            TextureAtlasSprite sp = sprite(s);
            if (sp == null) {
                continue;
            }
            float[] b = box(s, x, y, z);
            float ox = (float) (x - cx), oy = (float) (y - cy), oz = (float) (z - cz);
            cube(pose, vc, sp, ox, oy, oz, b, x, y, z, alpha);
            drawn++;
        }
        if (drawn > 0 && Hooks.schematicGhostHits < 100000) {
            Hooks.schematicGhostHits++;
        }
    }

    /** The ghosts, farthest first (see-through things look right drawn back to front). */
    private static List<Object[]> order(final double cx, final double cy, final double cz, long now) {
        List<Object[]> g = Schematics.ghosts();
        if (g != sortedFor || now - sortedAt > 250) {
            List<Object[]> s = new ArrayList<Object[]>(g);
            Collections.sort(s, (a, b) -> Double.compare(dist(b, cx, cy, cz), dist(a, cx, cy, cz)));
            Set<Long> f = new HashSet<Long>();
            for (Object[] o : g) {
                f.add(BlockPos.asLong((Integer) o[0], (Integer) o[1], (Integer) o[2]));
            }
            sorted = s;
            filled = f;
            sortedFor = g;
            sortedAt = now;
        }
        return sorted;
    }

    private static double dist(Object[] o, double cx, double cy, double cz) {
        double dx = (Integer) o[0] + 0.5 - cx, dy = (Integer) o[1] + 0.5 - cy, dz = (Integer) o[2] + 0.5 - cz;
        return dx * dx + dy * dy + dz * dz;
    }

    /** A box's six faces, skipping faces against another ghost (only where both are full blocks). */
    private static void cube(PoseStack.Pose p, VertexConsumer vc, TextureAtlasSprite sp, float ox, float oy, float oz, float[] b,
                             int x, int y, int z, int alpha) {
        float x0 = ox + b[0] + IN, y0 = oy + b[1] + IN, z0 = oz + b[2] + IN;
        float x1 = ox + b[3] - IN, y1 = oy + b[4] - IN, z1 = oz + b[5] - IN;
        boolean full = b[0] == 0f && b[1] == 0f && b[2] == 0f && b[3] == 1f && b[4] == 1f && b[5] == 1f;
        float u0 = sp.getU0(), u1 = sp.getU1(), v0 = sp.getV0(), v1 = sp.getV1();
        float du = u1 - u0, dv = v1 - v0;
        // each face is shaded a little differently, like real blocks, so the shape reads
        if (!(full && filled.contains(BlockPos.asLong(x, y + 1, z)))) {   // top
            int c = color(1.0f, alpha);
            quad(p, vc, c, 0, 1, 0, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0,
                    u0 + du * b[0], v0 + dv * b[2], u0 + du * b[3], v0 + dv * b[5]);
        }
        if (!(full && filled.contains(BlockPos.asLong(x, y - 1, z)))) {   // bottom
            int c = color(0.5f, alpha);
            quad(p, vc, c, 0, -1, 0, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1,
                    u0 + du * b[0], v0 + dv * b[2], u0 + du * b[3], v0 + dv * b[5]);
        }
        float tv0 = v0 + dv * (1f - b[4]), tv1 = v0 + dv * (1f - b[1]);
        if (!(full && filled.contains(BlockPos.asLong(x, y, z - 1)))) {   // north
            int c = color(0.8f, alpha);
            quad(p, vc, c, 0, 0, -1, x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0,
                    u0 + du * b[0], tv0, u0 + du * b[3], tv1);
        }
        if (!(full && filled.contains(BlockPos.asLong(x, y, z + 1)))) {   // south
            int c = color(0.8f, alpha);
            quad(p, vc, c, 0, 0, 1, x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1,
                    u0 + du * b[0], tv0, u0 + du * b[3], tv1);
        }
        if (!(full && filled.contains(BlockPos.asLong(x - 1, y, z)))) {   // west
            int c = color(0.6f, alpha);
            quad(p, vc, c, -1, 0, 0, x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1,
                    u0 + du * b[2], tv0, u0 + du * b[5], tv1);
        }
        if (!(full && filled.contains(BlockPos.asLong(x + 1, y, z)))) {   // east
            int c = color(0.6f, alpha);
            quad(p, vc, c, 1, 0, 0, x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0,
                    u0 + du * b[2], tv0, u0 + du * b[5], tv1);
        }
    }

    /** Four corners (top-left, bottom-left, bottom-right, top-right), the texture from ua,va to ub,vb. */
    private static void quad(PoseStack.Pose p, VertexConsumer vc, int c, float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float ua, float va, float ub, float vb) {
        vertex(p, vc, ax, ay, az, c, ua, va, nx, ny, nz);
        vertex(p, vc, bx, by, bz, c, ua, vb, nx, ny, nz);
        vertex(p, vc, cx, cy, cz, c, ub, vb, nx, ny, nz);
        vertex(p, vc, dx, dy, dz, c, ub, va, nx, ny, nz);
    }

    private static void vertex(PoseStack.Pose p, VertexConsumer vc, float x, float y, float z, int c, float u, float v,
                               float nx, float ny, float nz) {
        vc.addVertex(p, x, y, z).setColor(c).setUv(u, v).setOverlay(OVERLAY).setLight(LIGHT).setNormal(p, nx, ny, nz);
    }

    private static int color(float shade, int alpha) {
        int l = Math.round(255 * (0.25f + 0.75f * shade));  // a little bright, so ghosts stand out from real blocks
        return (alpha << 24) | (l << 16) | (l << 8) | l;
    }

    /** "minecraft:oak_stairs[facing=north,half=bottom]" as a block state (null if unknown). */
    static BlockState state(String text) {
        if (STATES.containsKey(text)) {
            return STATES.get(text);
        }
        BlockState s = null;
        try {
            if (blocks == null) {
                Map<String, Block> m = new HashMap<String, Block>();
                for (Block b : BuiltInRegistries.BLOCK) {
                    m.put(String.valueOf(BuiltInRegistries.BLOCK.getKey(b)), b);
                }
                blocks = m;
            }
            int a = text.indexOf('[');
            String id = a < 0 ? text : text.substring(0, a);
            if (!id.contains(":")) {
                id = "minecraft:" + id;
            }
            Block b = blocks.get(id);
            if (b != null) {
                s = b.defaultBlockState();
                if (a >= 0 && text.endsWith("]")) {
                    for (String kv : text.substring(a + 1, text.length() - 1).split(",")) {
                        int e = kv.indexOf('=');
                        if (e > 0) {
                            Property<?> prop = b.getStateDefinition().getProperty(kv.substring(0, e).trim());
                            if (prop != null) {
                                s = with(s, prop, kv.substring(e + 1).trim());
                            }
                        }
                    }
                }
            }
        } catch (RuntimeException e) {
            s = null;
        }
        if (STATES.size() > 4096) {
            STATES.clear();
        }
        STATES.put(text, s);
        return s;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState s, Property<T> p, String value) {
        java.util.Optional<T> v = p.getValue(value);
        return v.isPresent() ? s.setValue(p, v.get()) : s;
    }

    private static TextureAtlasSprite sprite(BlockState s) {
        TextureAtlasSprite sp = SPRITES.get(s);
        if (sp == null && !SPRITES.containsKey(s)) {
            try {
                sp = Minecraft.getInstance().getBlockRenderer().getBlockModelShaper().getParticleIcon(s);
            } catch (RuntimeException e) {
                sp = null;
            }
            SPRITES.put(s, sp);
        }
        return sp;
    }

    /** The block's outline box within its space (a slab is half height, a torch is thin...). */
    private static float[] box(BlockState s, int x, int y, int z) {
        float[] b = BOXES.get(s);
        if (b == null) {
            b = new float[] {0f, 0f, 0f, 1f, 1f, 1f};
            try {
                VoxelShape shape = s.getShape(Minecraft.getInstance().level, new BlockPos(x, y, z));
                if (!shape.isEmpty()) {
                    AABB bb = shape.bounds();
                    b = new float[] {clamp(bb.minX), clamp(bb.minY), clamp(bb.minZ), clamp(bb.maxX), clamp(bb.maxY), clamp(bb.maxZ)};
                    if (b[3] - b[0] < 0.05f || b[4] - b[1] < 0.05f || b[5] - b[2] < 0.05f) {
                        b = new float[] {0f, 0f, 0f, 1f, 1f, 1f};
                    }
                }
            } catch (RuntimeException e) {
                // some blocks need a real world around them: show a full cube
            }
            BOXES.put(s, b);
        }
        return b;
    }

    private static float clamp(double v) {
        return (float) Math.max(0.0, Math.min(1.0, v));
    }
}
