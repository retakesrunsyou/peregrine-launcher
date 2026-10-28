package net.peregrine.client.v26_1;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.ClientAsset;

/** Peregrine cape textures (assets/peregrine-client/textures/cape/). */
public final class Capes {

    private static final Map<String, ClientAsset.Texture> IDS = new ConcurrentHashMap<>();

    private Capes() {
    }

    /** Null for a frame of your own cape that isn't ready yet. */
    public static ClientAsset.Texture texture(String name) {
        if (name.startsWith("custom_")) {
            ClientAsset.Texture t = IDS.get(name);
            if (t != null) {
                return t;
            }
            net.minecraft.resources.Identifier id = CustomCape.id(name, net.peregrine.client.core.modules.Cosmetics.customFile(name));
            if (id == null) {
                return null;
            }
            t = new ClientAsset.ResourceTexture(id, id);
            if (IDS.size() > 2000) {
                IDS.clear();
            }
            IDS.put(name, t);
            return t;
        }
        return IDS.computeIfAbsent(name, n -> new ClientAsset.ResourceTexture(net.minecraft.resources.Identifier.fromNamespaceAndPath("peregrine-client", "cape/" + n)));
    }
}
