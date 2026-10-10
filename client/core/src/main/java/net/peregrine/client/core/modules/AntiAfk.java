package net.peregrine.client.core.modules;

import java.util.Random;

import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.BoolSetting;
import net.peregrine.client.core.settings.SliderSetting;

/**
 * Anti-AFK: after you've been idle for a while, now and then jump, swing or look
 * around a little, at uneven times, so you aren't kicked from AFK areas. Anything
 * you do yourself counts as being back.
 */
public final class AntiAfk extends Module {

    private final SliderSetting idle = add(new SliderSetting("idle", "Start after idle for", 15f, 300f, 5f, 60f, "%.0f s"));
    private final BoolSetting jump = add(new BoolSetting("jump", "Jump", true));
    private final BoolSetting swing = add(new BoolSetting("swing", "Swing", true));
    private final BoolSetting look = add(new BoolSetting("look", "Look around a little", true));

    private final Random random = new Random();
    private int idleTicks;
    private int nextAction;
    private int jumpHold;
    private float lastYaw = Float.NaN, lastPitch;
    private float expectedYaw, expectedPitch;  // where our own turns left the view

    public AntiAfk() {
        super("anti_afk", "Anti-AFK", "After you're idle, jump, swing and look around now and then so you aren't kicked",
                Category.UTILITY, false);
    }

    @Override
    public String warning() {
        return "Some servers don't allow anti-AFK. Check the rules first";
    }

    /** Ticks of idling before acting. */
    int idleNeeded() {
        return Math.round(idle.value * 20);
    }

    boolean active() {
        return idleTicks >= idleNeeded();
    }

    private boolean playerActed(Platform p) {
        for (Platform.Key k : Platform.Key.values()) {
            if (p.isDown(k)) {
                return true;
            }
        }
        // Real mouse clicks (our own swings don't count as clicks).
        boolean clicked = net.peregrine.client.core.Peregrine.get().cps(0) + net.peregrine.client.core.Peregrine.get().cps(1) > 0;
        float yaw = p.yaw(), pitch = p.pitch();
        boolean turned = !Float.isNaN(lastYaw)
                && (Math.abs(yaw - expectedYaw) > 0.5f || Math.abs(pitch - expectedPitch) > 0.5f);
        lastYaw = yaw;
        lastPitch = pitch;
        expectedYaw = yaw;
        expectedPitch = pitch;
        return clicked || turned;
    }

    @Override
    public void tick(Platform p) {
        if (jumpHold > 0 && --jumpHold == 0) {
            p.afkJump(false);
        }
        if (playerActed(p) && jumpHold == 0) {
            idleTicks = 0;
            nextAction = 0;
            return;
        }
        idleTicks++;
        if (!active()) {
            return;
        }
        if (nextAction > 0) {
            nextAction--;
            return;
        }
        nextAction = 50 + random.nextInt(170);  // every 2.5 to 11 seconds, never on a beat
        int pick = random.nextInt(3);
        if (pick == 0 && jump.value) {
            p.afkJump(true);
            jumpHold = 2 + random.nextInt(4);
        } else if (pick == 1 && swing.value) {
            p.afkSwing();
        } else if (look.value) {
            float dy = (random.nextFloat() - 0.5f) * 40f;
            float dp = (random.nextFloat() - 0.5f) * 12f;
            p.afkLook(dy, dp);
            expectedYaw = p.yaw();
            expectedPitch = p.pitch();
        } else if (jump.value) {
            p.afkJump(true);
            jumpHold = 3;
        }
    }

    @Override
    protected void onDisable() {
        if (jumpHold > 0) {
            platform().afkJump(false);
            jumpHold = 0;
        }
        idleTicks = 0;
    }
}
