package net.peregrine.client.core.modules;

import net.peregrine.client.core.Benchmark;
import net.peregrine.client.core.Draw;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Peregrine;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.ActionSetting;

/** Benchmark: a one-minute flight round where you stand that measures your FPS, lows and stutters. */
public final class BenchmarkRun extends Module {

    public final ActionSetting run = add(new ActionSetting("run", "One-minute test (the camera flies, you stay put)",
            () -> Benchmark.INSTANCE.running() ? "Stop" : "Start", () -> {
                Benchmark b = Benchmark.INSTANCE;
                if (b.running()) {
                    b.stop();
                } else {
                    b.start(Peregrine.get().platform());
                    Peregrine.get().menu().close();
                }
            }));

    public BenchmarkRun() {
        super("benchmark", "Benchmark", "Measures your FPS, 1% lows and stutters on the same flight every time, to compare settings",
                Category.PERFORMANCE, true);
    }

    @Override
    public void renderWorld(Draw d, Platform p) {
        Benchmark.INSTANCE.render(d, p);
    }

    @Override
    protected void onDisable() {
        Benchmark.INSTANCE.stop();
    }
}
