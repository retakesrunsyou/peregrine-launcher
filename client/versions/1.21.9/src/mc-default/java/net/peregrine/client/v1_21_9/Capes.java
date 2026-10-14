package net.peregrine.client.v1_21_9;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.ClientAsset;

/** Peregrine cape textures (assets/peregrine-client/textures/cape/). */
public final class Capes {

    private static final Map<String, ClientAsset.Texture> IDS = new ConcurrentHashMap<>();

    private Capes() {
    }

    public static ClientAsset.Texture texture(String name) {
        return IDS.computeIfAbsent(name, n -> new ClientAsset.ResourceTexture(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("peregrine-client", "cape/" + n)));
    }
}
