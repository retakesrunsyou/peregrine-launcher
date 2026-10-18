package net.peregrine.client.core.modules;

import java.util.List;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.HudModule;
import net.peregrine.client.core.Peregrine;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.SkinFaces;
import net.peregrine.client.core.Theme;
import net.peregrine.client.core.VoiceChat;
import net.peregrine.client.core.settings.BoolSetting;
import net.peregrine.client.core.settings.ChoiceSetting;
import net.peregrine.client.core.settings.KeySetting;
import net.peregrine.client.core.settings.SliderSetting;

/** Voice chat: how your mic works, and (while you're in a party) who's talking, on screen. */
public final class VoiceHud extends HudModule {

    public final ChoiceSetting mode = add(new ChoiceSetting("mode", "Microphone", 0, "Talks when you speak", "Push to talk"));
    public final KeySetting talkKey = add(new KeySetting("talk", "Push-to-talk key"));
    public final KeySetting muteKey = add(new KeySetting("mute", "Key to mute your mic"));
    public final SliderSetting sensitivity = add(new SliderSetting("sensitivity", "Voice pickup", 0f, 100f, 5f, 35f, "%.0f%%")
            .withText(v -> v < 25 ? "Picks up quiet voices" : v > 70 ? "Only loud speech" : "Normal"));
    public final SliderSetting volume = add(new SliderSetting("volume", "Party volume", 0f, 200f, 5f, 100f, "%.0f%%"));
    private final BoolSetting onlyTalking = add(new BoolSetting("only_talking", "Only show who's talking", false));

    public VoiceHud() {
        super("voice", "Voice chat", "Talk with friends in a party, on any server; shows who's talking", true, 0.01f, 0.30f);
        talkKey.key = 86;  // V
    }

    @Override
    public void tick(Platform p) {
        VoiceChat.pushToTalk = mode.index == 1;
        VoiceChat.pttHeld = talkKey.key >= 0 && p.keyDown(talkKey.key);
        VoiceChat.sensitivity = sensitivity.value / 100f;
        VoiceChat.volume = volume.value / 100f;
    }

    @Override
    public void keyPressed(int key, Platform p) {
        if (key == muteKey.key && muteKey.key >= 0) {
            VoiceChat.setMicMuted(!VoiceChat.micMuted);
        }
    }

    private List<VoiceChat.Member> shown() {
        List<VoiceChat.Member> all = VoiceChat.members();
        if (!onlyTalking.value) {
            return all;
        }
        List<VoiceChat.Member> out = new java.util.ArrayList<VoiceChat.Member>();
        for (VoiceChat.Member m : all) {
            if (m.id == VoiceChat.myId() ? VoiceChat.talking() : m.talking()) {
                out.add(m);
            }
        }
        return out;
    }

    @Override
    public boolean hasContent(Platform p) {
        return VoiceChat.inParty() && !shown().isEmpty();
    }

    @Override
    public int width(Draw d, Platform p) {
        int w = 60;
        for (VoiceChat.Member m : shown()) {
            w = Math.max(w, d.width(m.name) + 30);
        }
        return w;
    }

    @Override
    public int height(Draw d, Platform p) {
        return 4 + shown().size() * 12;
    }

    @Override
    public void render(Draw d, Platform p, int x, int y) {
        int a = Peregrine.get().accent();
        int w = width(d, p);
        panel(d, x, y, w, height(d, p));
        int yy = y + 2;
        for (VoiceChat.Member m : shown()) {
            boolean me = m.id == VoiceChat.myId();
            boolean talking = me ? VoiceChat.talking() : m.talking();
            boolean muted = me ? VoiceChat.micMuted : m.muted;
            int[] face = SkinFaces.get(m.uuid);
            if (talking) {
                d.roundRect(x + 2, yy, 10, 10, 2, a);
            }
            if (face != null) {
                SkinFaces.draw(d, face, x + 3, yy + 1, 8);
            } else {
                d.rect(x + 3, yy + 1, 8, 8, 0xFF555A66);
            }
            d.text(m.name, x + 15, yy + 2, talking ? Theme.TEXT : Theme.MUTED, true);
            if (muted) {  // a small red slash for a muted mic
                int mx = x + w - 10;
                d.rect(mx + 2, yy + 2, 3, 5, 0xFFBFC4CF);
                for (int i = 0; i < 7; i++) {
                    d.rect(mx + i, yy + 1 + i, 1, 1, Theme.BAD);
                }
            }
            yy += 12;
        }
    }
}
