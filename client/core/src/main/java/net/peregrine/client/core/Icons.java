package net.peregrine.client.core;

import java.util.HashMap;
import java.util.Map;

/** Which Minecraft item stands for each feature on its tile in the menu. */
final class Icons {

    private static final Map<String, String> ITEM = new HashMap<String, String>();

    private static void put(String item, String... ids) {
        for (String id : ids) {
            ITEM.put(id, "minecraft:" + item);
        }
    }

    static {
        put("redstone_torch", "fps");
        put("filled_map", "coords", "chunk");
        put("compass", "compass");
        put("recovery_compass", "rotation");
        put("clock", "clock", "stopwatch");
        put("daylight_detector", "day");
        put("stone_button", "cps");
        put("oak_button", "keystrokes");
        put("diamond_chestplate", "armor");
        put("potion", "effects", "potion_count");
        put("totem_of_undying", "totems", "small_totem");
        put("arrow", "arrows");
        put("anvil", "durability_alert");
        put("stick", "reach");
        put("iron_sword", "combo");
        put("target", "target");
        put("grass_block", "block_info");
        put("cobblestone", "block_count");
        put("player_head", "players");
        put("experience_bottle", "xp");
        put("torch", "light");
        put("golden_apple", "health");
        put("cooked_beef", "food");
        put("netherrack", "nether_coords");
        put("writable_book", "session", "chat_size");
        put("feather", "speed");
        put("oak_sapling", "biome");
        put("chest", "memory", "inventory_tweaks");
        put("ender_pearl", "ping");
        put("beacon", "server");
        put("painting", "packs");
        put("bundle", "item_count");
        put("spyglass", "zoom", "static_fov");
        put("glowstone", "fullbright");
        put("sugar", "toggle_sprint");
        put("leather_boots", "toggle_sneak");
        put("ender_eye", "freelook");
        put("crossbow", "crosshair");
        put("red_dye", "hit_color");
        put("apple", "item_physics");
        put("bedrock", "anti_leak");
        put("glass", "hitboxes");
        put("repeater", "stable_fps");
        put("blaze_powder", "fewer_particles");
        put("flint_and_steel", "low_fire");
        put("item_frame", "clean_menus");
        put("name_tag", "own_nametag");
        put("oak_sign", "scoreboard");
        put("golden_sword", "old_animations");
        put("oak_door", "main_menu");
        put("shield", "no_hurt_cam");
        put("sunflower", "clear_weather");
        put("shears", "clean_edges");
        put("tripwire_hook", "steady_camera");
        put("glass_pane", "no_menu_blur");
        put("scaffolding", "chunk_borders");
        put("cobweb", "fog");
        put("wooden_sword", "swing_speed");
        put("diamond_ore", "block_outline");
        put("white_wool", "static_sky");
        put("moss_block", "chunk_fade");
        put("note_block", "sound_filters");
        put("red_bed", "anti_afk");
    }

    static String of(Module m) {
        String item = ITEM.get(m.id);
        if (item != null) {
            return item;
        }
        switch (m.category) {
            case HUD: return "minecraft:item_frame";
            case UTILITY: return "minecraft:iron_pickaxe";
            case VISUALS: return "minecraft:ender_eye";
            default: return "minecraft:redstone";
        }
    }

    private Icons() {
    }
}
