package net.peregrine.client.v1_21_9;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;

/** Peregrine cape textures (assets/peregrine-client/textures/cape/), your own cape and other players' own capes. */
public final class Capes {

    private static final Map<String, ClientAsset.Texture> IDS = new ConcurrentHashMap<>();
    /** Own capes' frames, by the texture they're registered as (a frame still loading shows the one before). */
    private static final Map<Identifier, ClientAsset.Texture> FRAMES = new ConcurrentHashMap<>();

    private Capes() {
    }

    /** Null for a frame of an own cape that isn't ready yet. */
    public static ClientAsset.Texture texture(String name) {
        if (name.startsWith("remote_") || name.startsWith("custom_")) {
            Identifier id = name.startsWith("remote_")
                    ? RemoteCape.id(name, net.peregrine.client.core.modules.Cosmetics.remoteFile(name))
                    : CustomCape.id(name, net.peregrine.client.core.modules.Cosmetics.customFile(name));
            if (id == null) {
                return null;
            }
            if (FRAMES.size() > 4000) {
                FRAMES.clear();
            }
            return FRAMES.computeIfAbsent(id, i -> new ClientAsset.ResourceTexture(i, i));
        }
        return IDS.computeIfAbsent(name, n -> new ClientAsset.ResourceTexture(Identifier.fromNamespaceAndPath("peregrine-client", "cape/" + n)));
    }
}
