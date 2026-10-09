package net.peregrine.client.v1_21_6;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.peregrine.client.core.Compat;
import net.peregrine.client.core.Platform;
import net.peregrine.client.v1_21_6.mixin.OptionInstanceAccessor;

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
        return Compat.facing(player().getYRot());
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
        if (PeregrineClientMod.zoomKey == null || mc().screen != null) {
            return false;
        }
        // Before 1.21.9 only one binding can own a key, and C is also "Save Hotbar",
        // so read the key itself instead of trusting the binding.
        com.mojang.blaze3d.platform.InputConstants.Key key =
                net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.getBoundKeyOf(PeregrineClientMod.zoomKey);
        long window = mc().getWindow().getWindow();
        if (key.getType() == com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM) {
            return com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, key.getValue());
        }
        if (key.getType() == com.mojang.blaze3d.platform.InputConstants.Type.MOUSE) {
            return org.lwjgl.glfw.GLFW.glfwGetMouseButton(window, key.getValue()) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        }
        return PeregrineClientMod.zoomKey.isDown();
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

    @Override
    public void openScreen(Screen which) {
        Minecraft mc = mc();
        net.minecraft.client.gui.screens.Screen current = mc.screen;
        switch (which) {
            case SINGLEPLAYER: mc.setScreen(new SelectWorldScreen(current)); break;
            case MULTIPLAYER: mc.setScreen(new JoinMultiplayerScreen(current)); break;
            case OPTIONS: mc.setScreen(new OptionsScreen(current, mc.options)); break;
            case PEREGRINE_MENU: mc.setScreen(new PeregrineScreen()); break;
            case QUIT: mc.stop(); break;
            case NONE: mc.setScreen(null); break;
            case TITLE: mc.setScreen(new net.minecraft.client.gui.screens.TitleScreen()); break;
            default: break;
        }
    }

    @Override
    public boolean showing(Screen which) {
        net.minecraft.client.gui.screens.Screen s = mc().screen;
        switch (which) {
            case NONE: return s == null;
            case TITLE: return s instanceof PeregrineTitleScreen;
            case SINGLEPLAYER: return s instanceof SelectWorldScreen;
            case MULTIPLAYER: return s instanceof JoinMultiplayerScreen;
            case OPTIONS: return s instanceof net.minecraft.client.gui.screens.options.OptionsScreen;
            case PEREGRINE_MENU: return s instanceof PeregrineScreen;
            default: return false;
        }
    }

    @Override
    public String playerName() {
        return mc().getUser().getName();
    }

    @Override
    public String minecraftVersion() {
        return FabricLoader.getInstance().getModContainer("minecraft")
                .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?");
    }

    @Override
    public int ping() {
        if (mc().getCurrentServer() == null || mc().getConnection() == null || player() == null) {
            return -1;
        }
        PlayerInfo info = mc().getConnection().getPlayerInfo(player().getUUID());
        return info == null ? -1 : info.getLatency();
    }

    @Override
    public String serverAddress() {
        ServerData server = mc().getCurrentServer();
        return server == null ? null : server.ip;
    }

    @Override
    public long worldTime() {
        return mc().level == null ? 0 : mc().level.getDayTime() % 24000L;
    }

    @Override
    public String biome() {
        return mc().level.getBiome(player().blockPosition()).unwrapKey()
                .map(Compat::keyId).orElse("unknown");
    }

    @Override
    public float yaw() {
        return player().getYRot();
    }

    @Override
    public int food() {
        return player().getFoodData().getFoodLevel();
    }

    @Override
    public float saturation() {
        return player().getFoodData().getSaturationLevel();
    }

    @Override
    public int potionCount() {
        var inv = player().getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.getItem() instanceof PotionItem) {
                n += stack.getCount();
            }
        }
        return n;
    }

    @Override
    public float health() {
        return mc().player == null ? -1 : mc().player.getHealth();
    }

    @Override
    public float maxHealth() {
        return mc().player == null ? 20 : mc().player.getMaxHealth();
    }

    @Override
    public String dimension() {
        return mc().level == null ? "" : Compat.keyId(mc().level.dimension());
    }

    @Override
    public int itemCount(String what) {
        if (mc().player == null) {
            return 0;
        }
        var inv = player().getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            boolean match = what.equals("totem")
                    ? stack.is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING)
                    : what.equals("arrow") && stack.getItem() instanceof net.minecraft.world.item.ArrowItem;
            if (match) {
                n += stack.getCount();
            }
        }
        return n;
    }

    @Override
    public List<String> resourcePacks() {
        List<String> out = new ArrayList<>();
        for (Pack pack : mc().getResourcePackRepository().getSelectedPacks()) {
            out.add(0, pack.getTitle().getString());  // Minecraft lists the top pack last
        }
        return out;
    }

    private boolean chunkBorders;

    /** Sets an enum setting by position, without naming its class (it moves between versions). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setEnum(OptionInstance option, int ordinal) {
        Object[] values = ((Enum<?>) option.get()).getDeclaringClass().getEnumConstants();
        option.set(values[ordinal]);
    }

    @Override
    public void setOption(Option option, boolean on) {
        var o = mc().options;
        switch (option) {
            case TOGGLE_SNEAK: o.toggleCrouch().set(on); break;
            case STATIC_FOV: o.fovEffectScale().set(on ? 0.0 : 1.0); break;
            case STEADY_CAMERA:
                o.bobView().set(!on);
                o.damageTiltStrength().set(on ? 0.0 : 1.0);
                break;
            case NO_MENU_BLUR: o.menuBackgroundBlurriness().set(on ? 0 : 5); break;
            case FEWER_PARTICLES: setEnum(o.particles(), on ? 2 : 0); break;  // MINIMAL : ALL
            case CHUNK_BORDERS:
                if (chunkBorders != on) {
                    chunkBorders = mc().debugRenderer.switchRenderChunkborder();
                }
                break;
            case HITBOXES: mc().getEntityRenderDispatcher().setRenderHitBoxes(on); break;
            default: break;
        }
    }

    @Override
    public long worldDay() {
        return mc().level == null ? 0 : mc().level.getDayTime() / 24000L + 1;
    }
}
