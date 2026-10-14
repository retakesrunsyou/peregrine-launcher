package net.peregrine.client.core.modules;

import net.peregrine.client.core.Platform;

public final class BiomeHud extends LineHud {

    public BiomeHud() {
        super("biome", "Biome", "The biome you're standing in", "Biome", 0f, 0.455f);
    }

    /** "minecraft:dark_forest" → "Dark Forest". */
    static String pretty(String id) {
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        StringBuilder out = new StringBuilder();
        for (String word : path.split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    @Override
    String value(Platform p) {
        return pretty(p.biome());
    }
}
