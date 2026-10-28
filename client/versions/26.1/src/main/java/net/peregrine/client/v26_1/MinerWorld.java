package net.peregrine.client.v26_1;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.peregrine.client.core.bot.BotWorld;

/** The area miner's hands and eyes in 26.1 (singleplayer only). */
public final class MinerWorld implements BotWorld {

    /** Plain blocks it may use to fill lava, water and holes (never anything valuable). */
    private static final Set<String> FILLERS = new HashSet<String>(Arrays.asList(
            "cobblestone", "cobbled_deepslate", "stone", "deepslate", "dirt", "coarse_dirt", "netherrack", "andesite",
            "diorite", "granite", "tuff", "blackstone", "basalt", "smooth_basalt", "sandstone", "red_sandstone", "end_stone",
            "calcite", "mud", "packed_mud", "cobblestone_slab"));

    private static final MinerWorld INSTANCE = new MinerWorld();
    private int eatTicks;
    private int foodAtStart = -1;

    private MinerWorld() {
    }

    /** Null unless you're playing a singleplayer world. */
    public static MinerWorld get() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gameMode == null || !mc.hasSingleplayerServer()) {
            return null;
        }
        return INSTANCE;
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static LocalPlayer me() {
        return mc().player;
    }

    private static ClientLevel level() {
        return mc().level;
    }

    private static BlockState at(int x, int y, int z) {
        return level().getBlockState(new BlockPos(x, y, z));
    }

    @Override
    public int[] feet() {
        BlockPos p = me().blockPosition();
        return new int[] {p.getX(), p.getY(), p.getZ()};
    }

    @Override
    public double[] pos() {
        return new double[] {me().getX(), me().getY(), me().getZ()};
    }

    @Override
    public boolean onGround() {
        return me().onGround();
    }

    @Override
    public boolean solid(int x, int y, int z) {
        BlockState s = at(x, y, z);
        BlockPos p = new BlockPos(x, y, z);
        return !s.isAir() && !s.getCollisionShape(level(), p).isEmpty();
    }

    @Override
    public boolean fluid(int x, int y, int z) {
        return !at(x, y, z).getFluidState().isEmpty();
    }

    @Override
    public boolean lava(int x, int y, int z) {
        return at(x, y, z).getFluidState().is(FluidTags.LAVA);
    }

    @Override
    public boolean breakable(int x, int y, int z) {
        return at(x, y, z).getDestroySpeed(level(), new BlockPos(x, y, z)) >= 0;
    }

    @Override
    public boolean present(int x, int y, int z) {
        BlockState s = at(x, y, z);
        return !s.isAir() && (s.getFluidState().isEmpty() || !s.canBeReplaced());   // not air, not plain liquid
    }

    @Override
    public void look(double x, double y, double z) {
        LocalPlayer p = me();
        double dx = x - p.getX(), dy = y - p.getEyeY(), dz = z - p.getZ();
        double h = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, h));
        // turn smoothly, like a hand on the mouse
        float cy = p.getYRot(), cp = p.getXRot();
        float dyaw = wrap(yaw - cy), dpitch = pitch - cp;
        float step = 30f;
        cy += Math.max(-step, Math.min(step, dyaw));
        cp += Math.max(-step, Math.min(step, dpitch));
        p.setYRot(cy);
        p.setXRot(Math.max(-90f, Math.min(90f, cp)));
        p.setYHeadRot(cy);
    }

    private static float wrap(float a) {
        a %= 360f;
        if (a > 180f) {
            a -= 360f;
        } else if (a < -180f) {
            a += 360f;
        }
        return a;
    }

    @Override
    public void walk(boolean forward) {
        mc().options.keyUp.setDown(forward);
        mc().options.keySprint.setDown(false);
    }

    @Override
    public boolean mine(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockState s = level().getBlockState(pos);
        if (s.isAir()) {
            return true;
        }
        bestTool(s, pos);
        LocalPlayer p = me();
        Vec3 eye = p.getEyePosition();
        Direction face = Direction.getApproximateNearest(eye.x - (x + 0.5), eye.y - (y + 0.5), eye.z - (z + 0.5));
        mc().gameMode.continueDestroyBlock(pos, face);
        p.swing(InteractionHand.MAIN_HAND);
        return level().getBlockState(pos).isAir() || !present(x, y, z);
    }

    /** The hotbar tool that breaks this block fastest. */
    private void bestTool(BlockState s, BlockPos pos) {
        Inventory inv = me().getInventory();
        int best = inv.getSelectedSlot();
        float speed = inv.getItem(best).getDestroySpeed(s);
        for (int i = 0; i < 9; i++) {
            ItemStack st = inv.getItem(i);
            float v = st.getDestroySpeed(s);
            if (v > speed + 0.01f && healthy(st)) {
                best = i;
                speed = v;
            }
        }
        if (best != inv.getSelectedSlot()) {
            inv.setSelectedSlot(best);
        }
    }

    private static boolean healthy(ItemStack st) {
        return !st.isDamageableItem() || st.getMaxDamage() - st.getDamageValue() > 8;
    }

    @Override
    public boolean place(int x, int y, int z) {
        BlockPos target = new BlockPos(x, y, z);
        if (solid(x, y, z)) {
            return true;
        }
        InteractionHand hand = fillerHand();
        if (hand == null) {
            return false;
        }
        for (Direction d : Direction.values()) {
            BlockPos n = target.relative(d);
            if (!solid(n.getX(), n.getY(), n.getZ())) {
                continue;
            }
            Direction faceOfN = d.getOpposite();
            Vec3 hitAt = Vec3.atCenterOf(n).add(faceOfN.getStepX() * 0.5, faceOfN.getStepY() * 0.5, faceOfN.getStepZ() * 0.5);
            look(hitAt.x, hitAt.y, hitAt.z);
            mc().gameMode.useItemOn(me(), hand, new BlockHitResult(hitAt, faceOfN, n, false));
            me().swing(hand);
            return solid(x, y, z);
        }
        return false;
    }

    /** The off hand if it holds a filler block, else a hotbar slot with one (selected). */
    private InteractionHand fillerHand() {
        if (isFiller(me().getOffhandItem())) {
            return InteractionHand.OFF_HAND;
        }
        Inventory inv = me().getInventory();
        for (int i = 0; i < 9; i++) {
            if (isFiller(inv.getItem(i))) {
                inv.setSelectedSlot(i);
                return InteractionHand.MAIN_HAND;
            }
        }
        return null;
    }

    private static boolean isFiller(ItemStack st) {
        return st != null && !st.isEmpty() && FILLERS.contains(BuiltInRegistries.ITEM.getKey(st.getItem()).getPath());
    }

    @Override
    public boolean hasFiller() {
        if (isFiller(me().getOffhandItem())) {
            return true;
        }
        Inventory inv = me().getInventory();
        for (int i = 0; i < 9; i++) {
            if (isFiller(inv.getItem(i))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int food() {
        return me().getFoodData().getFoodLevel();
    }

    @Override
    public boolean hasFood() {
        return foodSlot() >= 0;
    }

    private int foodSlot() {
        Inventory inv = me().getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty() && st.has(DataComponents.FOOD)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean eat() {
        int slot = foodSlot();
        if (slot < 0) {
            mc().options.keyUse.setDown(false);
            return true;
        }
        if (eatTicks == 0) {
            foodAtStart = food();
            me().getInventory().setSelectedSlot(slot);
        }
        eatTicks++;
        mc().options.keyUse.setDown(true);
        if (food() > foodAtStart || eatTicks > 60) {
            mc().options.keyUse.setDown(false);
            eatTicks = 0;
            return true;
        }
        return false;
    }

    @Override
    public boolean toolOk() {
        return healthy(me().getMainHandItem());
    }

    @Override
    public boolean inventoryFull() {
        return me().getInventory().getFreeSlot() < 0;
    }

    @Override
    public void release() {
        Minecraft mc = mc();
        mc.options.keyUp.setDown(false);
        mc.options.keyUse.setDown(false);
        eatTicks = 0;
        if (mc.gameMode != null) {
            mc.gameMode.stopDestroyBlock();
        }
    }
}
