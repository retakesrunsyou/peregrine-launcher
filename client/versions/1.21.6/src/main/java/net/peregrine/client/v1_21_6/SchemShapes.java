package net.peregrine.client.v1_21_6;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Peregrine;
import net.peregrine.client.core.Schematics;

/** The placed schematic's outlines: what's missing, wrong or turned wrong, near you (drawn like ore veins). */
final class SchemShapes {

    private static final int MAX_GROUP = 64;
    private static List<OreScan.Vein> found = new ArrayList<OreScan.Vein>();
    private static long last;

    private SchemShapes() {
    }

    static List<OreScan.Vein> veins() {
        Minecraft mc = Minecraft.getInstance();
        if (!Hooks.schematicOn || mc.level == null || mc.player == null) {
            found = new ArrayList<OreScan.Vein>();
            return found;
        }
        long now = System.currentTimeMillis();
        if (now - last < 500) {
            return found;
        }
        last = now;
        List<int[]> blocks = Schematics.overlay(Peregrine.get().platform(), 20, 5000);
        Map<Long, Integer> todo = new HashMap<Long, Integer>();
        for (int[] b : blocks) {
            todo.put(BlockPos.asLong(b[0], b[1], b[2]), b[3]);
        }
        List<OreScan.Vein> out = new ArrayList<OreScan.Vein>();
        ArrayDeque<Long> queue = new ArrayDeque<Long>();
        List<BlockPos> group = new ArrayList<BlockPos>();
        while (!todo.isEmpty() && out.size() < 600) {
            Map.Entry<Long, Integer> first = todo.entrySet().iterator().next();
            int color = first.getValue();
            todo.remove(first.getKey());
            queue.add(first.getKey());
            group.clear();
            while (!queue.isEmpty() && group.size() < MAX_GROUP) {
                BlockPos p = BlockPos.of(queue.poll());
                group.add(p);
                for (Direction dir : Direction.values()) {
                    long n = p.relative(dir).asLong();
                    Integer c = todo.get(n);
                    if (c != null && c == color) {
                        todo.remove(n);
                        queue.add(n);
                    }
                }
            }
            for (Long left : queue) {  // a group that got too big: the rest start new groups
                todo.put(left, color);
            }
            queue.clear();
            int mx = Integer.MAX_VALUE, my = Integer.MAX_VALUE, mz = Integer.MAX_VALUE;
            for (BlockPos p : group) {
                mx = Math.min(mx, p.getX());
                my = Math.min(my, p.getY());
                mz = Math.min(mz, p.getZ());
            }
            VoxelShape shape = Shapes.empty();
            for (BlockPos p : group) {
                double dx = p.getX() - mx, dy = p.getY() - my, dz = p.getZ() - mz;
                shape = Shapes.joinUnoptimized(shape, Shapes.box(dx + 0.02, dy + 0.02, dz + 0.02, dx + 0.98, dy + 0.98, dz + 0.98), BooleanOp.OR);
            }
            out.add(new OreScan.Vein(new BlockPos(mx, my, mz), shape.optimize(), color));
        }
        found = out;
        if (!out.isEmpty() && Hooks.schematicHits < 1000) {
            Hooks.schematicHits++;
        }
        return out;
    }
}
