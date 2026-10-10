package net.peregrine.client.v1_21_6;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.peregrine.client.core.Hooks;

/**
 * Ore outlines: the ores near you with at least one open side (so you could see
 * them), found a couple of times a second. Each entry: x, y, z, color.
 */
public final class OreScan {

    private static final Map<Block, Integer> COLOR = new IdentityHashMap<Block, Integer>();
    private static List<long[]> found = new ArrayList<long[]>();
    private static long lastScan;

    private OreScan() {
    }

    private static int colorOf(Block block) {
        Integer c = COLOR.get(block);
        if (c == null) {
            c = Hooks.oreColor(String.valueOf(BuiltInRegistries.BLOCK.getKey(block)));
            COLOR.put(block, c);
        }
        return c;
    }

    private static boolean open(BlockState s) {
        return s.isAir() || !s.canOcclude();
    }

    public static List<long[]> ores() {
        Minecraft mc = Minecraft.getInstance();
        if (!Hooks.oreOutlines || mc.level == null || mc.player == null) {
            found = new ArrayList<long[]>();
            return found;
        }
        long now = System.currentTimeMillis();
        if (now - lastScan < 400) {
            return found;
        }
        lastScan = now;
        List<long[]> out = new ArrayList<long[]>();
        BlockPos center = mc.player.blockPosition();
        int r = Hooks.oreRange;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    pos.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    BlockState s = mc.level.getBlockState(pos);
                    if (s.isAir()) {
                        continue;
                    }
                    int c = colorOf(s.getBlock());
                    if (c == 0) {
                        continue;
                    }
                    for (Direction dir : Direction.values()) {
                        if (open(mc.level.getBlockState(pos.relative(dir)))) {
                            out.add(new long[] {pos.getX(), pos.getY(), pos.getZ(), c});
                            break;
                        }
                    }
                    if (out.size() >= 400) {
                        found = out;
                        return out;
                    }
                }
            }
        }
        found = out;
        return out;
    }
}
