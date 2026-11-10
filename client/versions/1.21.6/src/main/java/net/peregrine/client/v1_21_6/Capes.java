package net.peregrine.client.v1_21_6;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;

/** Peregrine cape textures (assets/peregrine-client/textures/cape/), your own cape and other players' own capes. */
public final class Capes {

    private static final Map<String, ResourceLocation> IDS = new ConcurrentHashMap<>();

    private Capes() {
    }

    /** Null for a frame of an own cape that isn't ready yet. */
    public static ResourceLocation texture(String name) {
        if (name.startsWith("remote_")) {   // another player's own cape
            return RemoteCape.id(name, net.peregrine.client.core.modules.Cosmetics.remoteFile(name));
        }
        if (name.startsWith("custom_")) {   // yours, made in the launcher
            return CustomCape.id(name, net.peregrine.client.core.modules.Cosmetics.customFile(name));
        }
        return IDS.computeIfAbsent(name, n -> ResourceLocation.fromNamespaceAndPath("peregrine-client", "textures/cape/" + n + ".png"));
    }
}
