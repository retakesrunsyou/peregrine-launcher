package net.peregrine.client.core;

/**
 * Helpers that let adapters avoid Minecraft classes Mojang keeps renaming,
 * so one adapter can cover several Minecraft versions.
 */
public final class Compat {

    private static final String[] FACING = {"South", "West", "North", "East"};

    private Compat() {
    }

    /** Minecraft yaw (0 = south, 90 = west) to "North", "East", "South" or "West". */
    public static String facing(float yaw) {
        int i = Math.floorMod(Math.round(yaw / 90f), 4);
        return FACING[i];
    }

    /**
     * Pulls the id out of a ResourceKey's toString(), e.g.
     * "ResourceKey[minecraft:worldgen/biome / minecraft:plains]" → "minecraft:plains".
     * Works whether Minecraft calls the id class ResourceLocation or Identifier.
     */
    public static String keyId(Object key) {
        String s = String.valueOf(key);
        int slash = s.lastIndexOf(" / ");
        int end = s.lastIndexOf(']');
        if (slash >= 0 && end > slash) {
            return s.substring(slash + 3, end);
        }
        return s;
    }
}
