package net.peregrine.client.v1_21_2;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;

/** Peregrine cape textures (assets/peregrine-client/textures/cape/). */
public final class Capes {

    private static final Map<String, ResourceLocation> IDS = new ConcurrentHashMap<>();

    private Capes() {
    }

    public static ResourceLocation texture(String name) {
        return IDS.computeIfAbsent(name, n -> ResourceLocation.fromNamespaceAndPath("peregrine-client", "textures/cape/" + n + ".png"));
    }
}
