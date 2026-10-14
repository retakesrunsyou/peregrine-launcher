package net.peregrine.client.core.modules;

import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.KeySetting;
import net.peregrine.client.core.settings.TextSetting;

/** Up to four keys that each send a chat command or message, like /home or /spawn. */
public final class QuickCommands extends Module {

    private final TextSetting[] text = new TextSetting[4];
    private final KeySetting[] keys = new KeySetting[4];
    private long lastSent;

    public QuickCommands() {
        super("quick_commands", "Quick commands", "Keys that send a command or message, like /home or /spawn",
                Category.UTILITY, false);
        String[] examples = {"/home", "/spawn", "/tpa", "gg"};
        for (int i = 0; i < 4; i++) {
            text[i] = add(new TextSetting("text" + (i + 1), "Command " + (i + 1), "", 96, examples[i]));
            keys[i] = add(new KeySetting("key" + (i + 1), "Key for command " + (i + 1)));
        }
    }

    @Override
    public void keyPressed(int key, Platform p) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < 4; i++) {
            if (keys[i].key == key && !text[i].value.trim().isEmpty()) {
                if (now - lastSent < 400) {
                    return;  // no spam if a key bounces or is held
                }
                lastSent = now;
                p.sendChat(text[i].value.trim());
                return;
            }
        }
    }

    @Override
    public String warning() {
        return "Some servers kick for sending messages too fast";
    }
}
