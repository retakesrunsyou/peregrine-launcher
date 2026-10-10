package net.peregrine.client.v26_2;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.peregrine.client.core.Hooks;

/**
 * Ore outlines: the ore veins near you that you could see (at least one block
 * with an open side), found a couple of times a second. Each vein is one merged
 * shape, so it gets a single outline around the outside instead of a box per block.
 */
public final class OreScan {

    /** One vein: its corner, its merged shape (relative to the corner) and its color. */
    public static final class Vein {
        public final BlockPos origin;
        public final VoxelShape shape;
        public final int color;

        Vein(BlockPos origin, VoxelShape shape, int color) {
            this.origin = origin;
            this.shape = shape;
            this.color = color;
        }
    }

    private static final int MAX_VEIN = 64;
    private static final Map<Block, Integer> TYPE = new IdentityHashMap<Block, Integer>();
    private static List<Vein> found = new ArrayList<Vein>();
    private static long lastScan;

    private OreScan() {
    }

    private static int typeOf(Block block) {
        Integer t = TYPE.get(block);
        if (t == null) {
            t = Hooks.oreType(String.valueOf(BuiltInRegistries.BLOCK.getKey(block)));
            TYPE.put(block, t);
        }
        return t;
    }

    private static boolean open(BlockState s) {
        return s.isAir() || !s.canOcclude();
    }

    public static List<Vein> veins() {
        Minecraft mc = Minecraft.getInstance();
        if (!Hooks.oreOutlines || mc.level == null || mc.player == null) {
            found = new ArrayList<Vein>();
            return found;
        }
        long now = System.currentTimeMillis();
        if (now - lastScan < 400) {
            return found;
        }
        lastScan = now;
        boolean[] on = Hooks.oreEnabled;
        BlockPos center = mc.player.blockPosition();
        int r = Hooks.oreRange;
        // every enabled ore in range: position -> type
        Map<Long, Integer> ores = new HashMap<Long, Integer>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    pos.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    BlockState s = mc.level.getBlockState(pos);
                    if (s.isAir()) {
                        continue;
                    }
                    int t = typeOf(s.getBlock());
                    if (t >= 0 && t < on.length && on[t]) {
                        ores.put(pos.asLong(), t);
                    }
                }
            }
        }
        // group touching ores of the same kind into veins; keep the veins you can see
        List<Vein> out = new ArrayList<Vein>();
        ArrayDeque<Long> queue = new ArrayDeque<Long>();
        List<BlockPos> vein = new ArrayList<BlockPos>();
        while (!ores.isEmpty() && out.size() < 200) {
            Map.Entry<Long, Integer> first = ores.entrySet().iterator().next();
            int type = first.getValue();
            ores.remove(first.getKey());
            queue.add(first.getKey());
            vein.clear();
            boolean visible = false;
            while (!queue.isEmpty()) {
                BlockPos p = BlockPos.of(queue.poll());
                if (vein.size() < MAX_VEIN) {
                    vein.add(p);
                }
                for (Direction dir : Direction.values()) {
                    BlockPos n = p.relative(dir);
                    Integer nt = ores.get(n.asLong());
                    if (nt != null && nt == type) {
                        ores.remove(n.asLong());
                        queue.add(n.asLong());
                    } else if (!visible && nt == null && open(mc.level.getBlockState(n))) {
                        visible = true;
                    }
                }
            }
            if (!visible) {
                continue;
            }
            int mx = Integer.MAX_VALUE, my = Integer.MAX_VALUE, mz = Integer.MAX_VALUE;
            for (BlockPos p : vein) {
                mx = Math.min(mx, p.getX());
                my = Math.min(my, p.getY());
                mz = Math.min(mz, p.getZ());
            }
            VoxelShape shape = Shapes.empty();
            for (BlockPos p : vein) {
                double dx = p.getX() - mx, dy = p.getY() - my, dz = p.getZ() - mz;
                shape = Shapes.joinUnoptimized(shape, Shapes.box(dx, dy, dz, dx + 1, dy + 1, dz + 1), BooleanOp.OR);
            }
            out.add(new Vein(new BlockPos(mx, my, mz), shape.optimize(), Hooks.ORE_COLORS[type]));
        }
        found = out;
        return out;
    }
}
