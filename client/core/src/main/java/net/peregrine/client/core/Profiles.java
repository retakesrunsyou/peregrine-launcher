package net.peregrine.client.core;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Profiles: every feature's settings saved under a name ("PvP", "Building"...), next to the
 * settings file, to switch between from the menu. Using one loads it over the current settings.
 */
final class Profiles {

    private static String current;

    private Profiles() {
    }

    private static Path dir(Peregrine p) {
        return p.configPath().resolveSibling("peregrine-profiles");
    }

    static List<String> list(Peregrine p) {
        List<String> out = new ArrayList<String>();
        Path d = dir(p);
        if (Files.isDirectory(d)) {
            try (DirectoryStream<Path> s = Files.newDirectoryStream(d, "*.json")) {
                for (Path f : s) {
                    String n = f.getFileName().toString();
                    out.add(n.substring(0, n.length() - 5));
                }
            } catch (Exception e) {
                // none then
            }
        }
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    static String current(Peregrine p) {
        return current;
    }

    static void saveNew(Peregrine p) {
        List<String> have = list(p);
        int n = have.size() + 1;
        while (have.contains("Profile " + n)) {
            n++;
        }
        save(p, "Profile " + n);
    }

    static void save(Peregrine p, String name) {
        Config.saveTo(p, dir(p).resolve(safe(name) + ".json"));
        current = name;
    }

    static void use(Peregrine p, String name) {
        Path f = dir(p).resolve(safe(name) + ".json");
        if (Files.isRegularFile(f)) {
            Config.loadFrom(p, f);
            current = name;
            p.markDirty();   // and it's what the game uses from now on
        }
    }

    static void delete(Peregrine p, String name) {
        try {
            Files.deleteIfExists(dir(p).resolve(safe(name) + ".json"));
        } catch (Exception e) {
            // leave it
        }
        if (name.equals(current)) {
            current = null;
        }
    }

    private static String safe(String name) {
        return name.replaceAll("[^A-Za-z0-9 _\\-]", "_");
    }
}
