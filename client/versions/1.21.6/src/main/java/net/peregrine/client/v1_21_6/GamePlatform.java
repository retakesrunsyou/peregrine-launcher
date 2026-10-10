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
    public double guiScale() {
        var w = mc().getWindow();
        return (double) w.getWidth() / Math.max(1, w.getGuiScaledWidth());
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

    // ---- performance (Stable FPS)

    @Override
    public int renderDistance() {
        return mc().options.renderDistance().get();
    }

    @Override
    public void setRenderDistance(int chunks) {
        mc().options.renderDistance().set(chunks);
    }

    @Override
    public double entityDistance() {
        return mc().options.entityDistanceScaling().get();
    }

    @Override
    public void setEntityDistance(double scale) {
        mc().options.entityDistanceScaling().set(scale);
    }

    @Override
    public int particleLevel() {
        return ((Enum<?>) mc().options.particles().get()).ordinal();
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void setParticleLevel(int level) {
        net.minecraft.client.OptionInstance option = mc().options.particles();
        Object[] all = ((Enum) option.get()).getDeclaringClass().getEnumConstants();
        option.set(all[Math.max(0, Math.min(all.length - 1, level))]);
    }

    // ---- batch 6

    @Override
    public int blockItemCount() {
        if (mc().player == null) {
            return 0;
        }
        var inv = player().getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof net.minecraft.world.item.BlockItem) {
                n += stack.getCount();
            }
        }
        return n;
    }

    private net.minecraft.client.OptionInstance<Double> chat(String key) {
        var o = mc().options;
        switch (key) {
            case "scale": return o.chatScale();
            case "width": return o.chatWidth();
            case "height": return o.chatHeightFocused();
            case "unfocused": return o.chatHeightUnfocused();
            default: return null;
        }
    }

    @Override
    public double chatOption(String key) {
        var option = chat(key);
        return option == null ? -1 : option.get();
    }

    @Override
    public void setChatOption(String key, double value) {
        var option = chat(key);
        if (option != null) {
            option.set(Math.max(0.0, Math.min(1.0, value)));
        }
    }

    // ---- batch 5: render hooks

    @Override
    public void testScene() {
        if (mc().player == null || mc().level == null) {
            return;
        }
        try {
            mc().player.setXRot(25.0F);  // look down a little, at the dropped item
            net.minecraft.world.phys.Vec3 look = mc().player.getLookAngle();
            double fx = look.x, fz = look.z;
            double len = Math.max(1e-6, Math.sqrt(fx * fx + fz * fz));
            fx /= len;
            fz /= len;
            double px = mc().player.getX(), py = mc().player.getY(), pz = mc().player.getZ();
            for (int i = -1; i <= 1; i++) {  // a short bedrock wall a few blocks ahead
                for (int h = 0; h <= 1; h++) {
                    net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(
                            px + fx * 6 - fz * i, py + h, pz + fz * 6 + fx * i);
                    mc().level.setBlock(pos, net.minecraft.world.level.block.Blocks.BEDROCK.defaultBlockState(), 3);
                }
            }
            net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(
                    mc().level, px + fx * 3.5 - fz * 0.8, py + 0.5, pz + fz * 3.5 + fx * 0.8,
                    new ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
            mc().level.addEntity(item);
            net.minecraft.core.BlockPos ore = net.minecraft.core.BlockPos.containing(px + fx * 4 + fz * 2, py, pz + fz * 4 - fx * 2);
            mc().level.setBlock(ore, net.minecraft.world.level.block.Blocks.DIAMOND_ORE.defaultBlockState(), 3);  // ore outlines
            mc().level.setBlock(ore.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);  // with an open side
            for (int i = 0; i < 40; i++) {  // lava sparks, for the particle test
                mc().level.addParticle(net.minecraft.core.particles.ParticleTypes.LAVA,
                        px + fx * 3, py + 1, pz + fz * 3, 0, 0, 0);
            }
            mc().player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));  // for the animation test
            mc().player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
                    new ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING));  // for the small totem test
            mc().gameRenderer.displayItemActivation(new ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING));
        } catch (Throwable t) {
            System.err.println("[Peregrine] test scene: " + t);
        }
    }


    @Override
    public void reloadChunks() {
        mc().execute(() -> {
            if (mc().level != null) {
                mc().levelRenderer.allChanged();
            }
        });
    }

    private java.util.Map<String, Object> icons;

    @Override
    public Object icon(String itemId) {
        if (icons == null) {  // every item by its id, once (works the same on every version)
            icons = new java.util.HashMap<String, Object>();
            for (net.minecraft.world.item.Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
                icons.put(String.valueOf(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item)), item);
            }
        }
        Object v = icons.get(itemId);
        if (v instanceof net.minecraft.world.item.Item) {
            if (System.currentTimeMillis() < iconsRetryAt) {
                return null;
            }
            try {
                v = new ItemStack((net.minecraft.world.item.Item) v);
            } catch (RuntimeException notReady) {
                // Some versions can't make item stacks on the main menu yet (their item
                // data isn't loaded); the menu draws its own icon and tries again later.
                iconsRetryAt = System.currentTimeMillis() + 3000;
                return null;
            }
            icons.put(itemId, v);
        }
        return v;
    }

    private long iconsRetryAt;

    // ---- batch 8

    @Override
    public boolean setChunkFade(double seconds) {
        return false;  // Minecraft added the fade-in in 1.21.11
    }

    @Override
    public void afkJump(boolean held) {
        mc().options.keyJump.setDown(held);
    }

    @Override
    public void afkSwing() {
        if (mc().player != null) {
            mc().player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        }
    }

    @Override
    public void afkLook(float yaw, float pitch) {
        if (mc().player != null) {
            mc().player.setYRot(mc().player.getYRot() + yaw);
            mc().player.setXRot(Math.max(-90f, Math.min(90f, mc().player.getXRot() + pitch)));
        }
    }

    @Override
    public boolean supports(String moduleId) {
        return !moduleId.equals("chunk_fade");  // Minecraft added the fade-in in 1.21.11
    }

    @Override
    public void reloadResources() {
        if (mc() != null) {
            mc().reloadResourcePacks();
        }
    }

    @Override
    public boolean keyDown(int key) {
        if (key < 0 || mc().screen != null) {
            return false;
        }
        long window = org.lwjgl.glfw.GLFW.glfwGetCurrentContext();  // the game's window, on the render thread
        return window != 0 && org.lwjgl.glfw.GLFW.glfwGetKey(window, key) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }

    @Override
    public boolean freelookKeyDown() {
        return PeregrineClientMod.freelookKey != null && PeregrineClientMod.freelookKey.isDown()
                && mc().screen == null;
    }

    @Override
    public int cameraMode() {
        return mc().options.getCameraType().ordinal();
    }

    @Override
    public void setCameraMode(int mode) {
        net.minecraft.client.CameraType[] all = net.minecraft.client.CameraType.values();
        mc().options.setCameraType(all[Math.max(0, Math.min(all.length - 1, mode))]);
    }

    @Override
    public void setHitColor(int argb) {
        mc().execute(() -> {
            try {
                net.minecraft.client.renderer.texture.DynamicTexture tex =
                        ((net.peregrine.client.v1_21_6.mixin.OverlayTextureAccessor) (Object) mc().gameRenderer.overlayTexture())
                                .peregrine$texture();
                com.mojang.blaze3d.platform.NativeImage img = tex.getPixels();
                if (img == null) {
                    return;
                }
                int c = argb == 0 ? 0xB3FF0000 : argb;  // 0 = Minecraft's own red
                for (int y = 0; y < 8; y++) {
                    for (int x = 0; x < 16; x++) {
                        img.setPixel(x, y, c);
                    }
                }
                tex.upload();
            } catch (Throwable t) {
                System.err.println("[Peregrine] Couldn't change the hit color: " + t);
            }
        });
    }

    // ---- batch 4: aim, blocks, experience

    @Override
    public float pitch() {
        return mc().player == null ? 0 : mc().player.getXRot();
    }

    private net.minecraft.world.entity.Entity target() {
        net.minecraft.world.phys.HitResult hit = mc().hitResult;
        if (mc().player == null || !(hit instanceof net.minecraft.world.phys.EntityHitResult)
                || hit.getType() != net.minecraft.world.phys.HitResult.Type.ENTITY) {
            return null;
        }
        return ((net.minecraft.world.phys.EntityHitResult) hit).getEntity();
    }

    @Override
    public double targetDistance() {
        if (target() == null) {
            return -1;
        }
        return mc().player.getEyePosition().distanceTo(mc().hitResult.getLocation());
    }

    @Override
    public String targetName() {
        net.minecraft.world.entity.Entity e = target();
        return e == null ? "" : e.getName().getString();
    }

    @Override
    public float targetHealth() {
        net.minecraft.world.entity.Entity e = target();
        return e instanceof net.minecraft.world.entity.LivingEntity
                ? ((net.minecraft.world.entity.LivingEntity) e).getHealth() : -1;
    }

    @Override
    public float targetMaxHealth() {
        net.minecraft.world.entity.Entity e = target();
        return e instanceof net.minecraft.world.entity.LivingEntity
                ? ((net.minecraft.world.entity.LivingEntity) e).getMaxHealth() : 20;
    }

    @Override
    public String lookedAtBlock() {
        net.minecraft.world.phys.HitResult hit = mc().hitResult;
        if (mc().level == null || !(hit instanceof net.minecraft.world.phys.BlockHitResult)
                || hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
            return "";
        }
        net.minecraft.core.BlockPos pos = ((net.minecraft.world.phys.BlockHitResult) hit).getBlockPos();
        return mc().level.getBlockState(pos).getBlock().getName().getString();
    }

    @Override
    public int heldItemCount() {
        if (mc().player == null) {
            return 0;
        }
        ItemStack held = mc().player.getMainHandItem();
        if (held.isEmpty()) {
            return 0;
        }
        var inv = player().getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.getItem() == held.getItem()) {
                n += stack.getCount();
            }
        }
        return Math.max(n, held.getCount());
    }

    @Override
    public String heldItemName() {
        return mc().player == null ? "" : mc().player.getMainHandItem().getHoverName().getString();
    }

    @Override
    public int onlinePlayers() {
        return mc().player == null || mc().getConnection() == null ? -1 : mc().getConnection().getOnlinePlayers().size();
    }

    @Override
    public int xpLevel() {
        return mc().player == null ? 0 : mc().player.experienceLevel;
    }

    @Override
    public float xpProgress() {
        return mc().player == null ? 0 : mc().player.experienceProgress;
    }

    @Override
    public int lightLevel() {
        if (mc().player == null || mc().level == null) {
            return -1;
        }
        return mc().level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, mc().player.blockPosition());
    }

    @Override
    public int hurtTime() {
        return mc().player == null ? 0 : mc().player.hurtTime;
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
                break;
            case NO_HURT_CAM:
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
