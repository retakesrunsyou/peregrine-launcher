"""Everything needed to install and start a Minecraft version.

Reads Mojang's version manifest, works out which libraries/assets/Java a
version needs, downloads them, and builds the java command line.
"""

import platform
import re
import shutil
import zipfile
from pathlib import Path
from typing import Optional

from . import NAME, VERSION, net, paths

MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
RUNTIME_INDEX_URL = ("https://launchermeta.mojang.com/v1/products/java-runtime/"
                     "2ec0cc96c44e5a76b9c8b8c39a2e7a66a4d0f3aa/all.json")
RESOURCES_URL = "https://resources.download.minecraft.net"
MAVEN_CENTRAL = "https://repo1.maven.org/maven2/"

_manifest_cache = None


# ---------------------------------------------------------------- versions

def manifest(refresh: bool = False) -> dict:
    global _manifest_cache
    if _manifest_cache is None or refresh:
        _manifest_cache = net.get_json_cached(MANIFEST_URL, paths.CACHE / "version_manifest.json",
                                              0 if refresh else 3600)
    return _manifest_cache


def list_versions(include_snapshots: bool = False) -> list:
    return [v["id"] for v in manifest()["versions"]
            if v["type"] == "release" or (include_snapshots and v["type"] == "snapshot")]


def version_json(version_id: str) -> dict:
    """Download (or read cached) version JSON."""
    path = paths.VERSIONS / version_id / f"{version_id}.json"
    if path.is_file():  # already downloaded: no network needed (version files never change)
        import json
        try:
            return json.loads(path.read_text())
        except ValueError:
            path.unlink()
    entry = next((v for v in manifest()["versions"] if v["id"] == version_id), None)
    if entry is None:
        raise ValueError(f"Unknown Minecraft version: {version_id}")
    net.fetch_all([net.Download(entry["url"], path, entry.get("sha1"))], "Version info")
    import json
    return json.loads(path.read_text())


# ------------------------------------------------------------------- rules

FEATURES = {}  # demo mode, custom resolution, quick play... all off


def _os_matches(os_rule: dict) -> bool:
    if "name" in os_rule and os_rule["name"] != "linux":
        return False
    if "arch" in os_rule:
        is_32 = platform.architecture()[0] == "32bit"
        if os_rule["arch"] == "x86" and not is_32:
            return False
    return True


def rules_allow(rules: Optional[list], features: dict = FEATURES) -> bool:
    if not rules:
        return True
    allowed = False
    for rule in rules:
        ok = True
        if "os" in rule and not _os_matches(rule["os"]):
            ok = False
        for key, want in rule.get("features", {}).items():
            if features.get(key, False) != want:
                ok = False
        if ok:
            allowed = rule["action"] == "allow"
    return allowed


# --------------------------------------------------------------- libraries

def maven_path(name: str) -> str:
    """'group:artifact:version[:classifier][@ext]' -> relative jar path."""
    ext = "jar"
    if "@" in name:
        name, ext = name.split("@", 1)
    parts = name.split(":")
    group, artifact, version = parts[:3]
    classifier = f"-{parts[3]}" if len(parts) > 3 else ""
    return f"{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}{classifier}.{ext}"


def _lib_key(name: str) -> str:
    parts = name.split("@")[0].split(":")
    classifier = parts[3] if len(parts) > 3 else ""
    return f"{parts[0]}:{parts[1]}:{classifier}"


def collect_libraries(libraries: list):
    """Return (classpath_downloads, native_downloads_with_excludes)."""
    classpath, natives, seen = [], [], set()
    for lib in libraries:
        if not rules_allow(lib.get("rules")):
            continue
        name = lib["name"]
        key = _lib_key(name)
        downloads = lib.get("downloads", {})

        # Old-style natives (LWJGL 2 / 3.2): a classifier to unzip.
        if "natives" in lib:
            classifier = lib["natives"].get("linux")
            if classifier:
                classifier = classifier.replace("${arch}", "64")
                info = downloads.get("classifiers", {}).get(classifier)
                if info:
                    dl = net.Download(info["url"], paths.LIBRARIES / info["path"], info.get("sha1"),
                                      size=info.get("size"))
                    natives.append((dl, lib.get("extract", {}).get("exclude", [])))

        if key in seen:
            continue  # an earlier (mod-loader) entry wins
        if "artifact" in downloads:
            art = downloads["artifact"]
            if art.get("url"):
                classpath.append(net.Download(art["url"], paths.LIBRARIES / art["path"], art.get("sha1"),
                                              size=art.get("size")))
                seen.add(key)
        elif "downloads" not in lib:
            # Maven-style entry (used by Fabric): name + repository url.
            rel = maven_path(name)
            base = lib.get("url", MAVEN_CENTRAL)
            if not base.endswith("/"):
                base += "/"
            classpath.append(net.Download(base + rel, paths.LIBRARIES / rel, lib.get("sha1"),
                                          size=lib.get("size")))
            seen.add(key)
    return classpath, natives


def extract_natives(natives: list, target: Path) -> None:
    target.mkdir(parents=True, exist_ok=True)
    for dl, excludes in natives:
        with zipfile.ZipFile(dl.path) as z:
            for member in z.namelist():
                if member.endswith("/") or any(member.startswith(e) for e in excludes):
                    continue
                if member.startswith("META-INF"):
                    continue
                out = target / member
                if not out.resolve().is_relative_to(target.resolve()):
                    continue  # ignore weird paths
                out.parent.mkdir(parents=True, exist_ok=True)
                with z.open(member) as src, open(out, "wb") as dst:
                    shutil.copyfileobj(src, dst)


# ------------------------------------------------------------------ assets

def install_assets(profile: dict, progress=None) -> tuple:
    """Download the asset index + objects. Returns (assets_root, index_id, legacy_dir)."""
    import json
    idx = profile["assetIndex"]
    index_path = paths.ASSETS / "indexes" / f"{idx['id']}.json"
    net.fetch_all([net.Download(idx["url"], index_path, idx.get("sha1"))], "Asset index")
    index = json.loads(index_path.read_text())

    downloads = []
    for obj in index["objects"].values():
        h = obj["hash"]
        downloads.append(net.Download(f"{RESOURCES_URL}/{h[:2]}/{h}",
                                      paths.ASSETS / "objects" / h[:2] / h, h, size=obj.get("size")))
    net.fetch_all(downloads, "Assets", progress)

    legacy_dir = None
    if index.get("virtual") or index.get("map_to_resources"):
        # Very old versions want assets laid out by name instead of by hash.
        legacy_dir = paths.ASSETS / "virtual" / idx["id"]
        for name, obj in index["objects"].items():
            h = obj["hash"]
            dest = legacy_dir / name
            if not dest.exists():
                dest.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(paths.ASSETS / "objects" / h[:2] / h, dest)
    return paths.ASSETS, idx["id"], legacy_dir


# -------------------------------------------------------------------- java

def install_java(component: str, progress=None) -> Optional[Path]:
    """Install Mojang's own Java runtime for Linux. Returns path to java, or None."""
    home = paths.RUNTIMES / component
    java = home / "bin" / "java"
    marker = home / ".peregrine-complete"
    if marker.exists() and java.exists():
        return java
    if platform.machine() not in ("x86_64", "AMD64"):
        return None  # Mojang only ships x86_64 Linux runtimes
    index = net.get_json(RUNTIME_INDEX_URL)
    builds = index.get("linux", {}).get(component) or []
    if not builds:
        return None
    files = net.get_json(builds[0]["manifest"]["url"])["files"]

    downloads, links = [], []
    for rel, info in files.items():
        dest = home / rel
        if info["type"] == "directory":
            dest.mkdir(parents=True, exist_ok=True)
        elif info["type"] == "file":
            raw = info["downloads"]["raw"]
            downloads.append(net.Download(raw["url"], dest, raw.get("sha1"), size=raw.get("size"),
                                          executable=info.get("executable", False)))
        elif info["type"] == "link":
            links.append((dest, info["target"]))
    net.fetch_all(downloads, f"Java ({component})", progress)
    for dest, target in links:
        dest.parent.mkdir(parents=True, exist_ok=True)
        if dest.is_symlink() or dest.exists():
            dest.unlink()
        dest.symlink_to(target)
    marker.touch()
    return java


def find_java(profile: dict, override: str = "", progress=None) -> str:
    if override:
        return override
    component = profile.get("javaVersion", {}).get("component", "jre-legacy")
    try:
        java = install_java(component, progress)
        if java:
            return str(java)
    except Exception:
        pass  # fall back to the system Java below
    system = shutil.which("java")
    if system:
        return system
    need = profile.get("javaVersion", {}).get("majorVersion", 8)
    raise RuntimeError(f"No Java found. Install Java {need} (e.g. openjdk-{need}-jre) "
                       f"or set a Java path in Settings.")


# ----------------------------------------------------------------- profile

def merge_profiles(parent: dict, child: dict) -> dict:
    """Layer a mod-loader profile (child) on top of a vanilla version (parent)."""
    merged = dict(parent)
    merged["id"] = child.get("id", parent["id"])
    merged["mainClass"] = child.get("mainClass", parent["mainClass"])
    merged["libraries"] = child.get("libraries", []) + parent.get("libraries", [])
    if "arguments" in child:
        args = {k: list(v) for k, v in parent.get("arguments", {}).items()}
        for kind in ("jvm", "game"):
            args[kind] = args.get(kind, []) + child["arguments"].get(kind, [])
        merged["arguments"] = args
    if "minecraftArguments" in child:
        merged["minecraftArguments"] = child["minecraftArguments"]
    merged["_vanilla_id"] = parent["id"]
    return merged


def install(profile: dict, progress=None) -> dict:
    """Download everything a (possibly merged) profile needs. Returns launch info."""
    vanilla_id = profile.get("_vanilla_id", profile["id"])
    client = profile["downloads"]["client"]
    client_jar = paths.VERSIONS / vanilla_id / f"{vanilla_id}.jar"

    classpath, natives = collect_libraries(profile["libraries"])
    files = classpath + [dl for dl, _ in natives]
    files.append(net.Download(client["url"], client_jar, client.get("sha1"), size=client.get("size")))

    log_arg = None
    logging_cfg = profile.get("logging", {}).get("client")
    if logging_cfg:
        f = logging_cfg["file"]
        log_path = paths.ASSETS / "log_configs" / f["id"]
        files.append(net.Download(f["url"], log_path, f.get("sha1")))
        log_arg = logging_cfg["argument"].replace("${path}", str(log_path))

    net.fetch_all(files, "Libraries", progress)

    natives_dir = paths.NATIVES / profile["id"]
    if natives:
        extract_natives(natives, natives_dir)
    natives_dir.mkdir(parents=True, exist_ok=True)

    assets_root, index_id, legacy_dir = install_assets(profile, progress)
    return {
        "classpath": [str(d.path) for d in classpath] + [str(client_jar)],
        "natives_dir": str(natives_dir),
        "assets_root": str(assets_root),
        "assets_index": index_id,
        "legacy_assets": str(legacy_dir) if legacy_dir else None,
        "log_arg": log_arg,
    }


# --------------------------------------------------------------- arguments

_VAR = re.compile(r"\$\{(\w+)\}")


def _expand(items: list, values: dict, features: dict = FEATURES) -> list:
    out = []
    for item in items:
        if isinstance(item, dict):
            if not rules_allow(item.get("rules"), features):
                continue
            val = item["value"]
            out.extend(val if isinstance(val, list) else [val])
        else:
            out.append(item)
    return [_VAR.sub(lambda m: str(values.get(m.group(1), m.group(0))), s) for s in out]


def build_command(profile: dict, info: dict, java: str, account: dict,
                  game_dir: Path, cfg: dict) -> list:
    values = {
        "auth_player_name": account["name"],
        "auth_uuid": account["uuid"],
        "auth_access_token": account.get("mc_token") or "0",
        "auth_session": account.get("mc_token") or "0",
        "auth_xuid": account.get("xuid", ""),
        "clientid": cfg.get("client_id", ""),
        "user_type": "msa" if account.get("type") == "msa" else "legacy",
        "user_properties": "{}",
        "version_name": profile["id"],
        "version_type": NAME,
        "game_directory": str(game_dir),
        "assets_root": info["assets_root"],
        "game_assets": info["legacy_assets"] or info["assets_root"],
        "assets_index_name": info["assets_index"],
        "natives_directory": info["natives_dir"],
        "launcher_name": NAME.lower(),
        "launcher_version": VERSION,
        "classpath": ":".join(info["classpath"]),
        "classpath_separator": ":",
        "library_directory": str(paths.LIBRARIES),
    }

    width, height = int(cfg.get("width") or 0), int(cfg.get("height") or 0)
    features = {"has_custom_resolution": bool(width and height)}
    values.update(resolution_width=width, resolution_height=height)

    if "arguments" in profile:
        jvm = _expand(profile["arguments"].get("jvm", []), values, features)
        game = _expand(profile["arguments"].get("game", []), values, features)
    else:  # versions before 1.13
        jvm = _expand(["-Djava.library.path=${natives_directory}", "-cp", "${classpath}"], values)
        game = _expand(profile["minecraftArguments"].split(), values)
        if features["has_custom_resolution"]:
            game += ["--width", str(width), "--height", str(height)]
    if cfg.get("fullscreen"):
        game.append("--fullscreen")

    major = int(profile.get("javaVersion", {}).get("majorVersion", 8))
    tuned = jvm_tuning(cfg, major)
    if info.get("log_arg"):
        tuned.append(info["log_arg"])
    tuned += cfg.get("extra_jvm_args", "").split()

    cmd = [java] + tuned + jvm + [profile["mainClass"]] + game
    if cfg.get("use_gamemode") and shutil.which("gamemoderun"):
        cmd = ["gamemoderun"] + cmd
    return cmd


# ------------------------------------------------------------- performance

def jvm_tuning(cfg: dict, java_major: int) -> list:
    """Memory and garbage-collector settings that cut stutter in Minecraft."""
    mx = int(cfg["max_memory_mb"])
    ms = min(int(cfg.get("min_memory_mb") or mx), mx)
    gc = cfg.get("gc", "auto")
    common = [
        f"-Xmx{mx}M", f"-Xms{ms}M",
        "-XX:+DisableExplicitGC",        # mods can't force full-pause collections
        "-XX:+PerfDisableSharedMem",     # avoids disk stalls from JVM stats files
    ]
    # ZGC: tiny pauses even with lots of RAM. Generational ZGC needs Java 21+.
    if gc == "zgc" and java_major >= 21:
        return common + ["-XX:+UseZGC", "-XX:+ZGenerational"]
    # G1 tuned for a game: short pauses, room for the young generation.
    return common + [
        "-XX:+UseG1GC", "-XX:+ParallelRefProcEnabled", "-XX:+UnlockExperimentalVMOptions",
        "-XX:G1NewSizePercent=30", "-XX:G1MaxNewSizePercent=40", "-XX:G1ReservePercent=20",
        "-XX:G1HeapRegionSize=16M", "-XX:MaxGCPauseMillis=40",
        "-XX:G1MixedGCCountTarget=4", "-XX:InitiatingHeapOccupancyPercent=15",
    ]


def has_nvidia() -> bool:
    return Path("/proc/driver/nvidia/version").exists()


def launch_env(cfg: dict) -> dict:
    """Environment for the game process: graphics driver speed-ups."""
    import os
    env = dict(os.environ)
    if cfg.get("driver_boost", True):
        # Let the graphics driver work on a second CPU thread. Big FPS gains
        # on AMD/Intel (Mesa); NVIDIA's equivalent setting.
        env.setdefault("mesa_glthread", "true")
        env.setdefault("__GL_THREADED_OPTIMIZATIONS", "1")
        env.setdefault("__GL_SHADER_DISK_CACHE", "1")
    if cfg.get("dedicated_gpu"):
        # Laptops with two GPUs: run on the stronger one.
        env["DRI_PRIME"] = "1"
        if has_nvidia():
            env["__NV_PRIME_RENDER_OFFLOAD"] = "1"
            env["__GLX_VENDOR_LIBRARY_NAME"] = "nvidia"
            env["__VK_LAYER_NV_optimus"] = "NVIDIA_only"
    return env
