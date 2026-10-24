package net.peregrine.client.v26_1;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.peregrine.client.core.modules.ZeroDelay;
import net.peregrine.client.v26_1.mixin.EntityRemovedAccessor;
import net.peregrine.client.v26_1.mixin.MinecraftInputAccessor;

/** The game side of Zero delay. Anything that goes wrong turns that part off for the session. */
public final class ZeroDelayHooks {

    /** Crystals hidden on your hit, waiting for the server to confirm: entity id -> {entity, time}. */
    private static final Map<Integer, Object[]> HIDDEN = new ConcurrentHashMap<Integer, Object[]>();
    private static final Set<Integer> GONE = ConcurrentHashMap.newKeySet();
    private static boolean crystalsBroken, anchorsBroken, inputBroken, gpuBroken;
    private static Method glFinish;

    private ZeroDelayHooks() {
    }

    // ------------------------------------------------------------------ crystals

    /** You hit something: a crystal disappears now (the server's explosion follows). */
    public static void attacked(Player player, Entity target) {
        if (!ZeroDelay.fastCrystals || crystalsBroken || !(target instanceof EndCrystal) || player.isSpectator()) {
            return;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || target.isRemoved()) {
                return;
            }
            int id = target.getId();
            GONE.remove(id);
            HIDDEN.put(id, new Object[] {target, System.currentTimeMillis()});
            mc.level.removeEntity(id, Entity.RemovalReason.KILLED);
            // look again straight away, so a crystal can be placed where it was in this same frame
            ((MinecraftInputAccessor) mc).peregrine$pick(1f);
        } catch (Throwable e) {
            crystalsBroken = true;
            System.err.println("[Peregrine] zero delay crystals turned off: " + e);
        }
    }

    /** The server removed these entities (crystal confirmed gone). Called off and on the game thread. */
    public static void removed(java.util.List<Integer> ids) {
        if (HIDDEN.isEmpty()) {
            return;
        }
        for (int i = 0; i < ids.size(); i++) {
            int id = ids.get(i);
            if (HIDDEN.remove(id) != null) {
                GONE.add(id);
            }
        }
    }

    /** Every frame: crystals the server never removed come back. */
    public static void frame() {
        Minecraft mc = Minecraft.getInstance();
        if (!HIDDEN.isEmpty()) {
            long now = System.currentTimeMillis();
            long wait = Math.max(450, Math.min(1500, ping(mc) * 2L + 250));
            for (Iterator<Map.Entry<Integer, Object[]>> it = HIDDEN.entrySet().iterator(); it.hasNext();) {
                Map.Entry<Integer, Object[]> e = it.next();
                if (now - (Long) e.getValue()[1] < wait) {
                    continue;
                }
                it.remove();
                try {
                    Entity ent = (Entity) e.getValue()[0];
                    if (mc.level != null && mc.level.getEntity(ent.getId()) == null && !GONE.contains(ent.getId())) {
                        ((EntityRemovedAccessor) ent).peregrine$unsetRemoved();
                        mc.level.addEntity(ent);
                    }
                } catch (Throwable t) {
                    crystalsBroken = true;
                    System.err.println("[Peregrine] zero delay crystals turned off: " + t);
                }
            }
            if (GONE.size() > 256) {
                GONE.clear();
            }
        }
        if (ZeroDelay.lowLatency && !gpuBroken) {
            try {
                if (glFinish == null) {
                    glFinish = Class.forName("org.lwjgl.opengl.GL11").getMethod("glFinish");
                }
                glFinish.invoke(null);   // wait for the graphics card: frames aren't queued up behind your input
            } catch (Throwable t) {
                gpuBroken = true;
            }
        }
    }

    private static int ping(Minecraft mc) {
        try {
            PlayerInfo info = mc.getConnection() == null || mc.player == null ? null : mc.getConnection().getPlayerInfo(mc.player.getUUID());
            return info == null ? 100 : info.getLatency();
        } catch (Throwable e) {
            return 100;
        }
    }

    // ------------------------------------------------------------------ anchors

    /**
     * You're about to use a block. A charged respawn anchor outside the Nether explodes: it's
     * removed now, inside Minecraft's own block prediction, so the server's answer puts it back
     * if it didn't explode after all.
     */
    public static void usingBlock(Player player, InteractionHand hand, BlockHitResult hit) {
        if (!ZeroDelay.fastAnchors || anchorsBroken) {
            return;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            Level level = mc.level;
            if (level == null || hit == null || level.dimension() == Level.NETHER || player.isSpectator()) {
                return;
            }
            BlockPos pos = hit.getBlockPos();
            BlockState s = level.getBlockState(pos);
            if (!(s.getBlock() instanceof RespawnAnchorBlock)) {
                return;
            }
            int charge = s.getValue(RespawnAnchorBlock.CHARGE);
            if (charge <= 0) {
                return;
            }
            boolean mainFuel = player.getMainHandItem().is(Items.GLOWSTONE), offFuel = player.getOffhandItem().is(Items.GLOWSTONE);
            if (charge < RespawnAnchorBlock.MAX_CHARGES && (mainFuel || offFuel)) {
                return;   // that charges it instead
            }
            if (hand == InteractionHand.OFF_HAND && mainFuel) {
                return;
            }
            if (player.isSecondaryUseActive() && (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty())) {
                return;   // sneaking with an item: the item is used, not the anchor
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 11);
        } catch (Throwable e) {
            anchorsBroken = true;
            System.err.println("[Peregrine] zero delay anchors turned off: " + e);
        }
    }

    // ------------------------------------------------------------------ instant input

    /** Right after a mouse button or key: attack, use and hotbar keys act now, not next tick. */
    public static void input() {
        if (!ZeroDelay.instantInput || inputBroken) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.gameMode == null || mc.getOverlay() != null) {
            return;
        }
        try {
            MinecraftInputAccessor acc = (MinecraftInputAccessor) mc;
            for (int i = 0; i < mc.options.keyHotbarSlots.length; i++) {
                if (!mc.player.isSpectator() && mc.options.keyHotbarSlots[i].consumeClick()) {
                    mc.player.getInventory().setSelectedSlot(i);
                }
            }
            if (mc.player.isUsingItem()) {
                return;   // Minecraft handles these on its tick while you use an item
            }
            while (mc.options.keyAttack.consumeClick()) {
                acc.peregrine$startAttack();
            }
            while (mc.options.keyUse.consumeClick()) {
                acc.peregrine$startUseItem();
            }
        } catch (Throwable e) {
            inputBroken = true;
            System.err.println("[Peregrine] instant clicks turned off: " + e);
        }
    }
}
