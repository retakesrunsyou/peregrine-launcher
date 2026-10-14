package net.peregrine.client.core.modules;

import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.OptionModule;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.BoolSetting;
import net.peregrine.client.core.settings.ChoiceSetting;

/** Hitboxes without F3+B, with a choice of what shows them, their color and the look line. */
public final class Hitboxes extends OptionModule {

    static final String[] COLORS = {"White", "Accent", "Red", "Green", "Cyan", "Yellow", "Pink"};
    static final int[] RGB = {0, -1, 0xFF4040, 0x40FF60, 0x30E0FF, 0xFFE040, 0xFF60D0};

    private final BoolSetting players = add(new BoolSetting("players", "Players", true));
    private final BoolSetting mobs = add(new BoolSetting("mobs", "Mobs and animals", true));
    private final BoolSetting items = add(new BoolSetting("items", "Dropped items", false));
    private final BoolSetting xp = add(new BoolSetting("xp", "XP orbs and XP bottles", false));
    private final BoolSetting projectiles = add(new BoolSetting("projectiles", "Arrows and thrown things", true));
    private final BoolSetting other = add(new BoolSetting("other", "Everything else", true));
    private final BoolSetting look = add(new BoolSetting("look", "Line showing where they look", false));
    private final ChoiceSetting color = add(new ChoiceSetting("color", "Color", 0, COLORS));

    public Hitboxes() {
        super("hitboxes", "Hitboxes", "Hitboxes without F3+B: pick what shows them, their color and the look line",
                Category.VISUALS, Platform.Option.HITBOXES);
    }

    @Override
    public void tick(Platform p) {
        super.tick(p);
        Hooks.hitboxKinds = new boolean[] {players.value, mobs.value, items.value, xp.value, projectiles.value, other.value};
        Hooks.hitboxLookLine = look.value;
        int c = RGB[color.index];
        Hooks.hitboxColor = c == -1 ? (net.peregrine.client.core.Peregrine.get().accent() & 0xFFFFFF) : c;
    }

    @Override
    protected void onDisable() {
        super.onDisable();
        Hooks.hitboxKinds = new boolean[] {true, true, true, true, true, true};
        Hooks.hitboxLookLine = true;
        Hooks.hitboxColor = 0;
    }
}
