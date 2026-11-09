package net.peregrine.client.core.settings;

import java.util.List;
import java.util.function.Supplier;

/**
 * A live list of players with buttons beside each (players near you wearing their own capes, to
 * hide or report). Shown as a block of rows on the feature's page; saves nothing.
 */
public final class PlayerListSetting extends Setting {

    /** One row: {uuid, name, state} where state is "shown" or "hidden". */
    public final Supplier<List<String[]>> rows;
    public final String empty;

    public PlayerListSetting(String id, String label, String empty, Supplier<List<String[]>> rows) {
        super(id, label);
        this.rows = rows;
        this.empty = empty;
    }

    @Override
    public Object save() {
        return "";
    }

    @Override
    public void load(String v) {
    }

    @Override
    public void reset() {
    }
}
