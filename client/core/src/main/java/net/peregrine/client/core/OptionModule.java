package net.peregrine.client.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A module that switches one of Minecraft's own settings. Turning it off puts
 * the setting back to Minecraft's default.
 *
 * Minecraft writes its settings to options.txt whenever it saves, so a setting this module
 * switched on can end up saved there. To make sure it's always undone, the module saves
 * options.txt itself each time it changes the setting, and remembers (in
 * peregrine-options.txt) which settings it switched on: if one is still on there after the
 * module was turned off (an old version, a crash...), it's put back when the game starts.
 */
public class OptionModule extends Module {

    private final Platform.Option option;
    private boolean applied;

    public OptionModule(String id, String name, String description, Category category, Platform.Option option) {
        super(id, name, description, category, false);
        this.option = option;
    }

    @Override
    public void tick(Platform p) {
        if (!applied) {  // also covers modules switched on in a saved config
            p.setOption(option, true);
            p.saveOptions();
            applied = true;
            mark(p, option, true);
        }
    }

    @Override
    protected void onDisable() {
        Platform p = platform();
        if (applied || owned(p).contains(option.name())) {
            p.setOption(option, false);
            p.saveOptions();
            applied = false;
            mark(p, option, false);
        }
    }

    /**
     * Once at start: settings this module switched on but which are still on while the
     * module is off are switched back. Before 1.5.2 nothing was remembered, so toggle sneak
     * (the one people got stuck with) is switched back once if its module is off.
     */
    public static void repair(Platform p, Iterable<Module> modules) {
        Set<String> owned = owned(p);
        boolean firstTime = !Files.exists(file(p));
        boolean changed = false;
        for (Module m : modules) {
            if (!(m instanceof OptionModule) || m.enabled()) {
                continue;
            }
            Platform.Option o = ((OptionModule) m).option;
            if (owned.contains(o.name()) || (firstTime && o == Platform.Option.TOGGLE_SNEAK)) {
                p.setOption(o, false);
                owned.remove(o.name());
                changed = true;
            }
        }
        if (changed) {
            p.saveOptions();
        }
        write(p, owned);
    }

    private static Path file(Platform p) {
        return p.configFile().resolveSibling("peregrine-options.txt");
    }

    static Set<String> owned(Platform p) {
        Set<String> out = new LinkedHashSet<String>();
        try {
            Path f = file(p);
            if (Files.exists(f)) {
                for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                    if (!line.trim().isEmpty()) {
                        out.add(line.trim());
                    }
                }
            }
        } catch (Exception e) {
            // unreadable: treat as nothing remembered
        }
        return out;
    }

    private static void mark(Platform p, Platform.Option o, boolean on) {
        Set<String> owned = owned(p);
        if (on ? owned.add(o.name()) : owned.remove(o.name())) {
            write(p, owned);
        }
    }

    private static void write(Platform p, Set<String> owned) {
        try {
            Path f = file(p);
            if (f.getParent() != null) {
                Files.createDirectories(f.getParent());
            }
            Files.write(f, String.join("\n", owned).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            // can't save: the setting is still switched back now
        }
    }
}
