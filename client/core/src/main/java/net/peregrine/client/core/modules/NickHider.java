package net.peregrine.client.core.modules;

import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.TextSetting;

/**
 * Shows your name as something else everywhere it appears on your screen (chat,
 * tab list, name tags, scoreboard): handy for recording and streaming. Only you
 * see it; the server and other players still see your real name.
 */
public final class NickHider extends Module {

    private final TextSetting nick = add(new TextSetting("nick", "Show my name as", "Player", 16, "Player"));

    public NickHider() {
        super("nick_hider", "Nick hider", "Your name shows as something else on your screen (for recording)",
                Category.UTILITY, false);
    }

    private void apply(Platform p) {
        String real = p.playerName();
        String n = nick.value.trim();
        Hooks.nickReal = real == null || real.isEmpty() ? null : real;
        Hooks.nick = n.isEmpty() ? "Player" : n;
    }

    @Override
    public void tick(Platform p) {
        apply(p);
    }

    @Override
    protected void onEnable() {
        apply(platform());
    }

    @Override
    protected void onDisable() {
        Hooks.nick = null;
    }
}
