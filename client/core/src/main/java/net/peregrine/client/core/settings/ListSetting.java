package net.peregrine.client.core.settings;

import java.util.LinkedHashSet;
import java.util.Set;

/** A remembered list of short ids (players whose capes you hid). Not shown as a row. */
public final class ListSetting extends Setting {

    public final Set<String> items = new LinkedHashSet<String>();

    public ListSetting(String id) {
        super(id, id);
    }

    @Override
    public boolean visible() {
        return false;
    }

    @Override
    public Object save() {
        return String.join(",", items);
    }

    @Override
    public void load(String v) {
        items.clear();
        if (v != null) {
            for (String s : v.split(",")) {
                if (!s.trim().isEmpty() && items.size() < 500) {
                    items.add(s.trim());
                }
            }
        }
    }

    @Override
    public void reset() {
        items.clear();
    }
}
