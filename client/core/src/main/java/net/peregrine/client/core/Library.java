package net.peregrine.client.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Your mods, shader packs and resource packs, in game: what's installed (switch on or off,
 * remove), and Modrinth to browse and install more, matched to this Minecraft version.
 * Files live in the instance's folders, the same ones the launcher uses.
 */
public final class Library {

    public enum Kind {
        MODS("mods", "mod", "Mods", "mod"),
        SHADERS("shaderpacks", "shader", "Shaders", "shader pack"),
        PACKS("resourcepacks", "resourcepack", "Resource packs", "resource pack");

        public final String folder, projectType, label, noun;

        Kind(String folder, String projectType, String label, String noun) {
            this.folder = folder;
            this.projectType = projectType;
            this.label = label;
            this.noun = noun;
        }
    }

    /** Something installed. */
    public static final class Item {
        public final Path path;
        public final String file;        // file name without ".disabled"
        public String name, version = "", description = "", modId = "";
        public boolean enabled, ours;
        public String iconFile;          // a PNG in the icon cache, or null
        public String project;           // its Modrinth project, once known

        Item(Path path) {
            this.path = path;
            String n = path.getFileName().toString();
            this.enabled = !n.endsWith(".disabled");
            this.file = enabled ? n : n.substring(0, n.length() - ".disabled".length());
            this.name = prettyName(file);
        }
    }

    /** A Modrinth search result. */
    public static final class Result {
        public final String project, slug, title, description, author, iconUrl;
        public final long downloads;
        public volatile String state = "";   // "", "working", "done", or a problem

        Result(JsonObject o) {
            project = str(o, "project_id");
            slug = str(o, "slug");
            title = str(o, "title");
            description = str(o, "description");
            author = str(o, "author");
            iconUrl = str(o, "icon_url");
            downloads = o.has("downloads") ? o.get("downloads").getAsLong() : 0;
        }
    }

    static final String API = "https://api.modrinth.com/v2";
    static final String UA = "retakesrunsyou/peregrine-launcher (Peregrine Client in game)";

    private static final Map<Kind, List<Item>> INSTALLED = new ConcurrentHashMap<Kind, List<Item>>();
    private static final Map<Kind, Long> SCANNED = new ConcurrentHashMap<Kind, Long>();
    private static final Set<Kind> SCANNING = Collections.newSetFromMap(new ConcurrentHashMap<Kind, Boolean>());
    private static final Set<String> KNOWN_PROJECTS = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    public static volatile boolean modsChanged;   // mods only change after a restart

    private Library() {
    }

    // ------------------------------------------------------------------ folders

    /** The instance's folder (the launcher starts the game in it). */
    public static Path gameDir() {
        String g = System.getProperty("peregrine.gameDir");
        if (g != null && !g.isEmpty()) {
            return Paths.get(g).toAbsolutePath();
        }
        Peregrine pc = Peregrine.get();
        return pc != null ? pc.platform().gameDir().toAbsolutePath().normalize() : Paths.get("").toAbsolutePath();
    }

    public static Path folder(Kind k) {
        return gameDir().resolve(k.folder);
    }

    static Path cache() {
        String data = System.getProperty("peregrine.data");
        Path base = data != null && !data.isEmpty() ? Paths.get(data, "client") : gameDir().resolve("peregrine");
        return base.resolve("icon-cache");
    }

    /** Opens a folder in the computer's file manager. */
    public static void openFolder(Path dir) {
        try {
            Files.createDirectories(dir);
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            String[] cmd = os.contains("win") ? new String[] {"explorer", dir.toString()}
                    : os.contains("mac") ? new String[] {"open", dir.toString()} : new String[] {"xdg-open", dir.toString()};
            new ProcessBuilder(cmd).start();
        } catch (IOException e) {
            System.out.println("[Peregrine] couldn't open " + dir + ": " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ installed

    /** What's installed (scanned in the background; refreshed every few seconds while asked for). */
    public static List<Item> installed(Kind k) {
        Long at = SCANNED.get(k);
        if ((at == null || System.currentTimeMillis() - at > 4000) && SCANNING.add(k)) {
            Thread t = new Thread(() -> {
                try {
                    INSTALLED.put(k, scan(k));
                } catch (Exception e) {
                    System.out.println("[Peregrine] couldn't list " + k.folder + ": " + e);
                } finally {
                    SCANNED.put(k, System.currentTimeMillis());
                    SCANNING.remove(k);
                }
            }, "Peregrine library");
            t.setDaemon(true);
            t.start();
        }
        List<Item> l = INSTALLED.get(k);
        return l == null ? Collections.<Item>emptyList() : l;
    }

    public static boolean scanned(Kind k) {
        return SCANNED.containsKey(k);
    }

    /** Look again straight away (after a change). */
    public static void rescan(Kind k) {
        SCANNED.remove(k);
    }

    static List<Item> scan(Kind k) throws IOException {
        Path dir = folder(k);
        List<Item> out = new ArrayList<Item>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        Map<Path, Item> old = new HashMap<Path, Item>();
        List<Item> before = INSTALLED.get(k);
        if (before != null) {
            for (Item i : before) {
                old.put(i.path, i);
            }
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path f : ds) {
                String n = f.getFileName().toString().toLowerCase(Locale.ROOT);
                boolean file = Files.isRegularFile(f);
                boolean ok = k == Kind.MODS ? file && (n.endsWith(".jar") || n.endsWith(".jar.disabled"))
                        : (file && (n.endsWith(".zip") || n.endsWith(".zip.disabled"))) || Files.isDirectory(f);
                if (!ok || n.startsWith(".")) {
                    continue;
                }
                Item prev = old.get(f);
                Item it = new Item(f);
                if (prev != null) {  // keep what we already read from it
                    it.name = prev.name;
                    it.version = prev.version;
                    it.description = prev.description;
                    it.modId = prev.modId;
                    it.iconFile = prev.iconFile;
                    it.ours = prev.ours;
                    it.project = prev.project;
                } else {
                    readDetails(k, it);
                }
                out.add(it);
            }
        }
        Collections.sort(out, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return out;
    }

    private static void readDetails(Kind k, Item it) {
        String lower = it.file.toLowerCase(Locale.ROOT);
        it.ours = lower.startsWith("peregrine-client");
        if (Files.isDirectory(it.path)) {
            Path meta = it.path.resolve("pack.mcmeta");
            if (Files.isRegularFile(meta)) {
                try {
                    it.description = packDescription(new String(Files.readAllBytes(meta), StandardCharsets.UTF_8));
                } catch (IOException e) {
                    // no description then
                }
            }
            Path png = it.path.resolve("pack.png");
            if (Files.isRegularFile(png)) {
                try (InputStream in = Files.newInputStream(png)) {
                    it.iconFile = cacheIcon("local-" + it.path.toString().hashCode(), in);
                } catch (IOException e) {
                    // no icon
                }
            }
            return;
        }
        try (ZipFile z = new ZipFile(it.path.toFile())) {
            if (k == Kind.MODS) {
                ZipEntry e = z.getEntry("fabric.mod.json");
                if (e != null) {
                    JsonObject o = JsonParser.parseString(read(z.getInputStream(e))).getAsJsonObject();
                    if (o.has("name")) {
                        it.name = o.get("name").getAsString();
                    }
                    it.modId = str(o, "id");
                    it.version = str(o, "version");
                    it.description = str(o, "description");
                    String icon = null;
                    if (o.has("icon") && o.get("icon").isJsonPrimitive()) {
                        icon = o.get("icon").getAsString();
                    } else if (o.has("icon") && o.get("icon").isJsonObject()) {
                        for (Map.Entry<String, JsonElement> en : o.getAsJsonObject("icon").entrySet()) {
                            icon = en.getValue().getAsString();  // the last (usually biggest) size
                        }
                    }
                    if (icon != null && z.getEntry(icon) != null) {
                        it.iconFile = cacheIcon("mod-" + it.modId + "-" + it.version, z.getInputStream(z.getEntry(icon)));
                    }
                }
            } else {
                ZipEntry meta = z.getEntry("pack.mcmeta");
                if (meta != null) {
                    it.description = packDescription(read(z.getInputStream(meta)));
                }
                ZipEntry png = z.getEntry("pack.png");
                if (png != null) {
                    it.iconFile = cacheIcon("pack-" + it.file.hashCode() + "-" + png.getSize(), z.getInputStream(png));
                }
            }
        } catch (Exception e) {
            // not a zip we can read: just its name then
        }
    }

    static String packDescription(String mcmeta) {
        try {
            JsonObject o = JsonParser.parseString(mcmeta).getAsJsonObject().getAsJsonObject("pack");
            JsonElement d = o.get("description");
            return d == null ? "" : stripFormatting(textOf(d));
        } catch (Exception e) {
            return "";
        }
    }

    private static String textOf(JsonElement d) {
        if (d.isJsonPrimitive()) {
            return d.getAsString();
        }
        StringBuilder b = new StringBuilder();
        if (d.isJsonArray()) {
            for (JsonElement e : d.getAsJsonArray()) {
                b.append(textOf(e));
            }
        } else if (d.isJsonObject()) {
            JsonObject o = d.getAsJsonObject();
            if (o.has("text")) {
                b.append(o.get("text").getAsString());
            }
            if (o.has("extra")) {
                b.append(textOf(o.get("extra")));
            }
        }
        return b.toString();
    }

    static String stripFormatting(String s) {
        return s.replaceAll("§.", "").replace('\n', ' ').trim();
    }

    /** "sodium-fabric-0.6.0+mc1.21.1.jar" -> "Sodium Fabric" (until the real name is read). */
    static String prettyName(String file) {
        String n = file.replaceAll("(?i)\\.(jar|zip)$", "");
        n = n.replaceAll("[-_+ ](v?\\d.*)$", "");
        n = n.replace('_', ' ').replace('-', ' ').trim();
        if (n.isEmpty()) {
            n = file;
        }
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    /** Switch a mod or pack file on or off (a ".disabled" ending, like the launcher uses). */
    public static boolean setEnabled(Kind k, Item it, boolean on) {
        if (it.enabled == on || it.ours) {
            return false;
        }
        Path target = it.path.resolveSibling(on ? it.file : it.file + ".disabled");
        try {
            Files.move(it.path, target);
            if (k == Kind.MODS) {
                modsChanged = true;
            }
            rescan(k);
            return true;
        } catch (IOException e) {
            System.out.println("[Peregrine] couldn't switch " + it.file + ": " + e.getMessage());
            return false;
        }
    }

    public static boolean remove(Kind k, Item it) {
        if (it.ours) {
            return false;
        }
        try {
            if (Files.isDirectory(it.path)) {
                deleteTree(it.path);
            } else {
                Files.deleteIfExists(it.path);
            }
            if (k == Kind.MODS) {
                modsChanged = true;
            }
            if (it.project != null) {
                KNOWN_PROJECTS.remove(it.project);
            }
            rescan(k);
            return true;
        } catch (IOException e) {
            System.out.println("[Peregrine] couldn't remove " + it.file + ": " + e.getMessage());
            return false;
        }
    }

    private static void deleteTree(Path p) throws IOException {
        if (Files.isDirectory(p)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(p)) {
                for (Path c : ds) {
                    deleteTree(c);
                }
            }
        }
        Files.deleteIfExists(p);
    }

    // ------------------------------------------------------------------ Modrinth

    /** Is this Modrinth project already installed (as far as we know)? */
    public static boolean has(String project) {
        return KNOWN_PROJECTS.contains(project);
    }

    /** Learns which Modrinth projects the installed files are (by their hashes), once per folder change. */
    public static void identify(Kind k) {
        final List<Item> items = new ArrayList<Item>(installed(k));
        if (items.isEmpty()) {
            return;
        }
        Thread t = new Thread(() -> {
            try {
                Map<String, Item> byHash = new HashMap<String, Item>();
                for (Item it : items) {
                    if (it.project == null && Files.isRegularFile(it.path)) {
                        byHash.put(sha1(it.path), it);
                    }
                }
                if (byHash.isEmpty()) {
                    return;
                }
                JsonObject body = new JsonObject();
                JsonArray hashes = new JsonArray();
                for (String h : byHash.keySet()) {
                    hashes.add(h);
                }
                body.add("hashes", hashes);
                body.addProperty("algorithm", "sha1");
                JsonObject found = JsonParser.parseString(http("POST", API + "/version_files", body.toString()))
                        .getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : found.entrySet()) {
                    Item it = byHash.get(e.getKey());
                    String project = str(e.getValue().getAsJsonObject(), "project_id");
                    if (it != null && !project.isEmpty()) {
                        it.project = project;
                        KNOWN_PROJECTS.add(project);
                    }
                }
            } catch (Exception e) {
                // offline: results just won't say "Installed"
            }
        }, "Peregrine library ids");
        t.setDaemon(true);
        t.start();
    }

    /** Searches Modrinth for things that work on this Minecraft version. */
    public static List<Result> search(Kind k, String query, String mcVersion, int offset) throws IOException {
        JsonArray facets = new JsonArray();
        facets.add(facet("project_type:" + k.projectType));
        if (k == Kind.MODS) {
            facets.add(facet("categories:fabric"));
        }
        if (k != Kind.SHADERS) {  // shader packs rarely list every Minecraft version
            facets.add(facet("versions:" + mcVersion));
        }
        String url = API + "/search?limit=20&offset=" + offset
                + "&index=" + (query.trim().isEmpty() ? "downloads" : "relevance")
                + "&query=" + URLEncoder.encode(query.trim(), "UTF-8")
                + "&facets=" + URLEncoder.encode(facets.toString(), "UTF-8");
        JsonObject o = JsonParser.parseString(http("GET", url, null)).getAsJsonObject();
        List<Result> out = new ArrayList<Result>();
        for (JsonElement e : o.getAsJsonArray("hits")) {
            out.add(new Result(e.getAsJsonObject()));
        }
        return out;
    }

    private static JsonArray facet(String f) {
        JsonArray a = new JsonArray();
        a.add(f);
        return a;
    }

    /** Downloads the newest version that fits (and, for mods, what it needs). Runs in the background. */
    public static void install(final Kind k, final Result r, final String mcVersion) {
        if ("working".equals(r.state)) {
            return;
        }
        r.state = "working";
        Thread t = new Thread(() -> {
            try {
                Set<String> done = new HashSet<String>();
                installProject(k, r.project, mcVersion, done, true);
                r.state = "done";
                if (k == Kind.MODS) {
                    modsChanged = true;
                }
                rescan(k);
            } catch (Exception e) {
                r.state = e.getMessage() == null ? "Couldn't install it" : e.getMessage();
            }
        }, "Peregrine install");
        t.setDaemon(true);
        t.start();
    }

    private static void installProject(Kind k, String project, String mcVersion, Set<String> done, boolean main)
            throws IOException {
        if (!done.add(project) || (!main && KNOWN_PROJECTS.contains(project))) {
            return;
        }
        String loaders = k == Kind.MODS ? "[\"fabric\"]" : k == Kind.SHADERS ? "[\"iris\"]" : "[\"minecraft\"]";
        String url = API + "/project/" + URLEncoder.encode(project, "UTF-8") + "/version?loaders="
                + URLEncoder.encode(loaders, "UTF-8") + "&game_versions="
                + URLEncoder.encode("[\"" + mcVersion + "\"]", "UTF-8");
        JsonArray versions = JsonParser.parseString(http("GET", url, null)).getAsJsonArray();
        if (versions.size() == 0 && k == Kind.SHADERS) {  // shader packs: any version for Iris will do
            versions = JsonParser.parseString(http("GET", API + "/project/" + URLEncoder.encode(project, "UTF-8")
                    + "/version?loaders=" + URLEncoder.encode(loaders, "UTF-8"), null)).getAsJsonArray();
        }
        if (versions.size() == 0) {
            throw new IOException(main ? "No version for Minecraft " + mcVersion : "A needed mod has no version for " + mcVersion);
        }
        JsonObject v = versions.get(0).getAsJsonObject();
        JsonObject file = null;
        for (JsonElement f : v.getAsJsonArray("files")) {
            JsonObject fo = f.getAsJsonObject();
            if (file == null || (fo.has("primary") && fo.get("primary").getAsBoolean())) {
                file = fo;
            }
        }
        if (file == null) {
            throw new IOException("That version has no file");
        }
        String name = str(file, "filename");
        if (name.isEmpty() || name.contains("/") || name.contains("\\") || name.startsWith(".")) {
            throw new IOException("Odd file name from Modrinth");
        }
        Path dir = folder(k);
        Files.createDirectories(dir);
        Path dest = dir.resolve(name);
        if (!Files.exists(dest) && !Files.exists(dir.resolve(name + ".disabled"))) {
            Path tmp = dir.resolve(name + ".part");
            download(str(file, "url"), tmp);
            String want = file.has("hashes") ? str(file.getAsJsonObject("hashes"), "sha1") : "";
            if (!want.isEmpty() && !want.equalsIgnoreCase(sha1(tmp))) {
                Files.deleteIfExists(tmp);
                throw new IOException("The download was damaged, try again");
            }
            Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
        }
        KNOWN_PROJECTS.add(project);
        if (k == Kind.MODS && v.has("dependencies")) {
            for (JsonElement de : v.getAsJsonArray("dependencies")) {
                JsonObject d = de.getAsJsonObject();
                String dp = str(d, "project_id");
                if ("required".equals(str(d, "dependency_type")) && !dp.isEmpty()) {
                    installProject(k, dp, mcVersion, done, false);
                }
            }
        }
    }

    // ------------------------------------------------------------------ icons

    private static final Map<String, String> ICON_FILES = new ConcurrentHashMap<String, String>();
    private static final Set<String> ICON_LOADING = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    /** A Modrinth icon as a cached PNG file, fetched in the background (null until it's there). */
    public static String webIcon(final String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        String f = ICON_FILES.get(url);
        if (f != null || !ICON_LOADING.add(url)) {
            return f == null || f.isEmpty() ? null : f;
        }
        Thread t = new Thread(() -> {
            try {
                HttpURLConnection c = open(url);
                try (InputStream in = c.getInputStream()) {
                    String file = cacheIcon("web-" + Integer.toHexString(url.hashCode()), in);
                    ICON_FILES.put(url, file == null ? "" : file);
                }
            } catch (Exception e) {
                ICON_FILES.put(url, "");
            }
        }, "Peregrine icon");
        t.setDaemon(true);
        t.start();
        return null;
    }

    /** Decodes a picture (PNG, JPEG, GIF), shrinks it to 64 x 64 and keeps it as a PNG. */
    static String cacheIcon(String key, InputStream in) {
        try {
            Path dir = cache();
            Files.createDirectories(dir);
            Path out = dir.resolve(key.replaceAll("[^A-Za-z0-9._-]", "_") + ".png");
            if (Files.isRegularFile(out)) {
                return out.toString();
            }
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(in);
            if (img == null) {
                return null;  // WebP and the like: no icon then
            }
            java.awt.image.BufferedImage small = new java.awt.image.BufferedImage(64, 64,
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = small.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    img.getWidth() <= 32 ? java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                            : java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, 64, 64, null);
            g.dispose();
            Path tmp = dir.resolve(out.getFileName() + ".tmp");
            javax.imageio.ImageIO.write(small, "png", tmp.toFile());
            Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
            return out.toString();
        } catch (Throwable e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ helpers

    static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    private static String read(InputStream in) throws IOException {
        try (InputStream i = in) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = i.read(buf)) > 0) {
                b.write(buf, 0, n);
            }
            return new String(b.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", UA);
        c.setInstanceFollowRedirects(true);
        return c;
    }

    static String http(String method, String url, String body) throws IOException {
        String fake = System.getProperty("peregrine.fakeModrinth");  // for previews of the menu, without the internet
        if (fake != null && url.contains("/search")) {
            return new String(Files.readAllBytes(Paths.get(fake)), StandardCharsets.UTF_8);
        }
        HttpURLConnection c = open(url);
        c.setRequestMethod(method);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream o = c.getOutputStream()) {
                o.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        int code = c.getResponseCode();
        if (code >= 300) {
            throw new IOException("Modrinth answered " + code);
        }
        return read(c.getInputStream());
    }

    private static void download(String url, Path to) throws IOException {
        if (!url.startsWith("https://")) {
            throw new IOException("Not a safe download link");
        }
        HttpURLConnection c = open(url);
        if (c.getResponseCode() >= 300) {
            throw new IOException("Download failed (" + c.getResponseCode() + ")");
        }
        try (InputStream in = c.getInputStream()) {
            Files.copy(in, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static String sha1(Path p) throws IOException {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            try (InputStream in = Files.newInputStream(p)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) {
                    md.update(buf, 0, n);
                }
            }
            StringBuilder b = new StringBuilder();
            for (byte x : md.digest()) {
                b.append(String.format("%02x", x));
            }
            return b.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    /** "1234567" -> "1.2M" */
    public static String count(long n) {
        if (n >= 1_000_000) {
            return String.format(Locale.ROOT, "%.1fM", n / 1e6).replace(".0M", "M");
        }
        if (n >= 1_000) {
            return String.format(Locale.ROOT, "%.1fK", n / 1e3).replace(".0K", "K");
        }
        return String.valueOf(n);
    }

    // ------------------------------------------------------------------ shaders (Iris)

    /** Is Iris (the shader loader) in this game? */
    public static boolean irisPresent() {
        try {
            Class.forName("net.irisshaders.iris.Iris");
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /** The shader pack in use, "" for none, or null when Iris isn't here. */
    public static String shaderInUse() {
        try {
            Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
            Object cfg = iris.getMethod("getIrisConfig").invoke(null);
            boolean on = (Boolean) cfg.getClass().getMethod("areShadersEnabled").invoke(cfg);
            Object name = cfg.getClass().getMethod("getShaderPackName").invoke(cfg);
            if (name instanceof java.util.Optional) {
                name = ((java.util.Optional<?>) name).orElse(null);
            }
            return on && name != null ? name.toString() : "";
        } catch (Throwable e) {
            return null;
        }
    }

    /** Uses a shader pack (null switches shaders off). Must run on the game's thread. */
    public static String useShader(String pack) {
        try {
            Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
            Object cfg = iris.getMethod("getIrisConfig").invoke(null);
            if (pack != null) {
                cfg.getClass().getMethod("setShaderPackName", String.class).invoke(cfg, pack);
            }
            cfg.getClass().getMethod("setShadersEnabled", boolean.class).invoke(cfg, pack != null);
            cfg.getClass().getMethod("save").invoke(cfg);
            iris.getMethod("reload").invoke(null);
            return null;
        } catch (Throwable e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            return "Iris couldn't switch: " + c.getClass().getSimpleName();
        }
    }
}
