package net.peregrine.client.core.modules;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.HudModule;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.Theme;
import net.peregrine.client.core.settings.BoolSetting;
import net.peregrine.client.core.settings.TextSetting;

/** Counts the items you pick (elytras, pearls, golden apples...) with their icons. */
public final class ItemTrackerHud extends HudModule {

    private final TextSetting items = add(new TextSetting("items", "Items (comma separated)",
            "ender_pearl, golden_apple, experience_bottle", 160, "ender_pearl, elytra"));
    private final BoolSetting hideZero = add(new BoolSetting("hide_zero", "Hide items you have none of", false));
    private final BoolSetting vertical = add(new BoolSetting("vertical", "Stack them up and down", true));

    private String cachedText;
    private String[] ids = new String[0];

    public ItemTrackerHud() {
        super("item_tracker", "Item counter", "Counts the items you choose, like pearls, elytras or golden apples",
                false, 1f, 0.42f);
    }

    private String[] ids() {
        if (!items.value.equals(cachedText)) {
            cachedText = items.value;
            java.util.List<String> out = new java.util.ArrayList<String>();
            for (String part : items.value.split("[,;\\s]+")) {
                String id = part.trim().toLowerCase().replace(' ', '_');
                if (!id.isEmpty()) {
                    out.add(id.contains(":") ? id : "minecraft:" + id);
                }
            }
            ids = out.toArray(new String[0]);
        }
        return ids;
    }

    private java.util.List<String> shown(Platform p) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        for (String id : ids()) {
            if (p.icon(id) != null && (!hideZero.value || p.countItem(id) > 0)) {
                out.add(id);
            }
        }
        return out;
    }

    @Override
    public boolean hasContent(Platform p) {
        return !shown(p).isEmpty();
    }

    private int cellW(Draw d) {
        return 18 + d.width("999") + 4;
    }

    @Override
    public int width(Draw d, Platform p) {
        int n = Math.max(1, shown(p).size());
        return vertical.value ? cellW(d) + 4 : n * cellW(d) + 4;
    }

    @Override
    public int height(Draw d, Platform p) {
        int n = Math.max(1, shown(p).size());
        return vertical.value ? n * 18 + 4 : 22;
    }

    @Override
    public void render(Draw d, Platform p, int x, int y) {
        panel(d, x, y, width(d, p), height(d, p));
        int cx = x + 2, cy = y + 2;
        for (String id : shown(p)) {
            int n = p.countItem(id);
            d.item(p.icon(id), cx + 1, cy + 1);
            d.text(String.valueOf(n), cx + 19, cy + 5, n == 0 ? Theme.FAINT : Theme.TEXT, true);
            if (vertical.value) {
                cy += 18;
            } else {
                cx += cellW(d);
            }
        }
    }
}
