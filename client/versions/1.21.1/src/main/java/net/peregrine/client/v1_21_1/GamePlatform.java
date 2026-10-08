package net.peregrine.client.v1_21_1;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.peregrine.client.core.Platform;
import net.peregrine.client.v1_21_1.mixin.OptionInstanceAccessor;

/** What the shared core needs, read from Minecraft 1.21.1. */
final class GamePlatform implements Platform {

    private static final EquipmentSlot[] ARMOR = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static LocalPlayer player() {
        return mc().player;
    }

    @Override
    public boolean inWorld() {
        return mc().level != null && player() != null;
    }

    @Override
    public int fps() {
        return mc().getFps();
    }

    @Override
    public double x() {
        return player().getX();
    }

    @Override
    public double y() {
        return player().getY();
    }

    @Override
    public double z() {
        return player().getZ();
    }

    @Override
    public String facing() {
        String n = player().getDirection().getName();
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    @Override
    public boolean isDown(Key key) {
        var o = mc().options;
        switch (key) {
            case FORWARD: return o.keyUp.isDown();
            case LEFT: return o.keyLeft.isDown();
            case BACK: return o.keyDown.isDown();
            case RIGHT: return o.keyRight.isDown();
            case JUMP: return o.keyJump.isDown();
            case SNEAK: return o.keyShift.isDown();
            case ATTACK: return o.keyAttack.isDown();
            case USE: return o.keyUse.isDown();
            default: return false;
        }
    }

    private static void add(List<ItemInfo> out, ItemStack stack) {
        if (!stack.isEmpty()) {
            int max = stack.isDamageableItem() ? stack.getMaxDamage() : 0;
            out.add(new ItemInfo(stack, stack.getDamageValue(), max));
        }
    }

    @Override
    public List<ItemInfo> armor() {
        List<ItemInfo> out = new ArrayList<>();
        if (player() == null) {
            return out;
        }
        for (EquipmentSlot slot : ARMOR) {
            add(out, player().getItemBySlot(slot));
        }
        add(out, player().getMainHandItem());
        return out;
    }

    @Override
    public List<EffectInfo> effects() {
        List<EffectInfo> out = new ArrayList<>();
        if (player() == null) {
            return out;
        }
        for (MobEffectInstance e : player().getActiveEffects()) {
            out.add(new EffectInfo(e.getEffect().value().getDisplayName().getString(),
                    e.getAmplifier(), e.getDuration(), e.isInfiniteDuration()));
        }
        return out;
    }

    @Override
    public void setSprintHeld(boolean held) {
        mc().options.keySprint.setDown(held);
    }

    @Override
    public boolean zoomKeyDown() {
        return PeregrineClientMod.zoomKey != null && PeregrineClientMod.zoomKey.isDown()
                && mc().screen == null;
    }

    @Override
    public double gamma() {
        return mc().options.gamma().get();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void setGamma(double value) {
        // The brightness slider stops at 1.0, so set the value directly.
        ((OptionInstanceAccessor) (Object) mc().options.gamma()).peregrine$setValue(value);
    }

    @Override
    public int screenWidth() {
        return mc().getWindow().getGuiScaledWidth();
    }

    @Override
    public int screenHeight() {
        return mc().getWindow().getGuiScaledHeight();
    }

    @Override
    public Path configFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("peregrine-client.json");
    }
}
