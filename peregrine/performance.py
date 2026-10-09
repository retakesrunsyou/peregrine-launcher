"""Performance first: every instance gets the right speed-up mods for its exact
Minecraft version and fast game settings, kept up to date automatically.

- Mods come from Modrinth, picked for the instance's version and loader each
  time the version changes (and checked for updates once a day).
- A mod the player already has (their own copy, or one they switched off in
  Content) is left alone, so nothing is ever installed twice.
- If one of these mods crashes the game, it's switched off for that instance
  and the player is told; the next Play works without it.
- Fast settings are written to options.txt once per instance (and again when
  the preset improves); anything the player changes afterwards is kept.
"""

import json
import re
import shutil
import tempfile
import time
import zipfile
from pathlib import Path

from . import modrinth, net

# Each mod targets a different bottleneck; all are client-side, work on any
# server, and are tested together on every supported version by the play test.
MODS = [
    ("sodium", "Sodium", "a much faster renderer: the biggest FPS gain"),
    ("lithium", "Lithium", "faster game logic: mobs, physics, block ticks"),
    ("ferrite-core", "FerriteCore", "less memory, so fewer garbage-collector stutters"),
    ("entityculling", "Entity Culling", "skips mobs and chests hidden behind walls"),
    ("immediatelyfast", "ImmediatelyFast", "faster HUD, text, maps and entities"),
    ("modernfix", "ModernFix", "faster start-up and lower memory"),
    ("moreculling", "More Culling", "skips hidden faces of leaves, glass and more"),
    ("badoptimizations", "BadOptimizations", "caches lightmap, sky and clouds work every frame"),
    ("scalablelux", "ScalableLux", "a faster lighting engine: smoother chunk loading"),
    ("dynamic-fps", "Dynamic FPS", "slows down in the background to save power and heat"),
]
SLUGS = [m[0] for m in MODS]

CHECK_EVERY = 24 * 3600  # look for newer versions of the mods once a day

# Fast settings. Keys a Minecraft version doesn't know are ignored by it, so one
# list covers 1.21 to 26.x (graphicsMode before 1.21.11, the preset after).
PRESET_VERSION = 4
FAST_SETTINGS = {
    "enableVsync": "false",            # don't cap FPS to the monitor
    "maxFps": "260",                   # 260 = unlimited
    "graphicsMode": "0",               # Fast (1.21 - 1.21.10)
    # 1.21.11 and later: a named preset is applied over the settings every time the game
    # loads, so mark ours "custom" and let the individual settings below do the work.
    "graphicsPreset": '"custom"',
    "cutoutLeaves": "false",           # solid leaves
    "improvedTransparency": "false",
    "renderClouds": '"false"',         # no clouds
    "entityShadows": "false",
    "biomeBlendRadius": "0",           # no colour blending between biomes: much faster chunk building
    "particles": "1",                  # decreased
    "entityDistanceScaling": "0.75",
    "weatherRadius": "5",
    "chunkSectionFadeInTime": "0.0",   # chunks appear at once
    "menuBackgroundBlurriness": "0",
    "ao": "false",                     # smooth lighting off
}
# These are only lowered, never raised.
LOWER_ONLY = {"renderDistance": 10, "simulationDistance": 8}


# ------------------------------------------------------------------ settings

def apply_fast_settings(game_dir: Path, force: bool = False) -> list:
    """Write the fast settings into options.txt. Returns the keys changed."""
    opts = game_dir / "options.txt"
    lines = opts.read_text(errors="replace").splitlines() if opts.exists() else []
    current = {}
    for line in lines:
        if ":" in line:
            k, v = line.split(":", 1)
            current[k] = v
    changed = []
    for key, value in FAST_SETTINGS.items():
        if current.get(key) != value:
            current[key] = value
            changed.append(key)
    for key, cap in LOWER_ONLY.items():
        have = current.get(key, "")
        if not have.isdigit() or int(have) > cap:
            current[key] = str(cap)
            changed.append(key)
    game_dir.mkdir(parents=True, exist_ok=True)
    tmp = opts.with_suffix(".tmp")
    tmp.write_text("\n".join(f"{k}:{v}" for k, v in current.items()) + "\n")
    tmp.replace(opts)
    return changed


# ------------------------------------------------------------------ mods

def mod_id(jar: Path):
    """The Fabric mod id inside a jar, or None."""
    try:
        with zipfile.ZipFile(jar) as z:
            return json.loads(z.read("fabric.mod.json").decode("utf-8", "replace"), strict=False).get("id")
    except (OSError, KeyError, ValueError, zipfile.BadZipFile):
        return None


def _installed_ids(mods: Path, skip=()) -> dict:
    """{mod id: file} for every jar in the folder, switched-off ones included."""
    out = {}
    if mods.is_dir():
        for f in mods.iterdir():
            if f.name in skip or not (f.name.endswith(".jar") or f.name.endswith(".jar.disabled")):
                continue
            mid = mod_id(f)
            if mid:
                out.setdefault(mid, f)
    return out


def _meta(jar: Path):
    try:
        with zipfile.ZipFile(jar) as z:
            return json.loads(z.read("fabric.mod.json").decode("utf-8", "replace"), strict=False)
    except (OSError, KeyError, ValueError, zipfile.BadZipFile):
        return None


# ---- the version rules mods declare in fabric.mod.json ("depends", "breaks")

def _version(text: str):
    """'0.6.13+mc1.21.1' -> ((0, 6, 13), pre) where pre sorts a pre-release first.
    None if it isn't a version we can read (snapshots and such: assume it's fine)."""
    text = str(text).split("+", 1)[0].strip()
    core, _, pre = text.partition("-")
    parts = core.split(".")
    if not parts or not all(p.isdigit() for p in parts):
        return None
    nums = tuple(int(p) for p in parts)
    return nums, (0, pre) if pre else (1, "")


def _cmp(a, b) -> int:
    an, bn = a[0], b[0]
    width = max(len(an), len(bn))
    an, bn = an + (0,) * (width - len(an)), bn + (0,) * (width - len(bn))
    if an != bn:
        return -1 if an < bn else 1
    if a[1] != b[1]:
        return -1 if a[1] < b[1] else 1
    return 0


def _matches(have: str, rule) -> bool:
    """Does version `have` satisfy a Fabric version rule? Unreadable rules count as yes."""
    if isinstance(rule, list):
        return any(_matches(have, r) for r in rule) if rule else True
    rule = str(rule).strip()
    if rule in ("", "*"):
        return True
    v = _version(have)
    if v is None:
        return True
    for part in rule.split():
        op = next((o for o in (">=", "<=", ">", "<", "=", "~", "^") if part.startswith(o)), "")
        target = part[len(op):]
        if target.endswith((".x", ".X", ".*")) and not op:
            prefix = _version(target[:-2])
            if prefix is None:
                return True
            if v[0][:len(prefix[0])] != prefix[0]:
                return False
            continue
        t = _version(target)
        if t is None:
            return True
        c = _cmp(v, t)
        if op == ">=" and c < 0 or op == "<=" and c > 0 or op == ">" and c <= 0 or op == "<" and c >= 0:
            return False
        if op in ("", "=") and c != 0:
            return False
        if op in ("~", "^"):
            if c < 0:
                return False
            n = t[0]
            if op == "~" and len(n) >= 2 and v[0][:2] != n[:2] or op == "^" and v[0][:1] != n[:1]:
                return False
    return True


def conflicts(mods: Path, mc: str, ours: set) -> dict:
    """Mods of ours that the game would refuse to start with: they need another
    Minecraft or library version, or another mod says it breaks with them.
    Returns {filename: reason}."""
    jars = {}
    for f in mods.iterdir() if mods.is_dir() else []:
        if f.name.endswith(".jar"):
            m = _meta(f)
            if m and m.get("id"):
                jars[f.name] = m
    versions = {m["id"]: str(m.get("version", "")) for m in jars.values()}
    for m in jars.values():
        for p in m.get("provides", []) or []:
            versions.setdefault(p, str(m.get("version", "")))
    versions["minecraft"] = mc
    bad = {}
    for name, m in jars.items():
        for dep, rule in (m.get("depends") or {}).items():
            if name in ours and dep in versions and not _matches(versions[dep], rule):
                bad[name] = f"needs {dep} {rule}"
        for dep, rule in (m.get("breaks") or {}).items():
            if dep in versions and _matches(versions[dep], rule):
                if name in ours:
                    bad[name] = f"doesn't work with {dep} {versions[dep]}"
                else:  # someone else's mod says it breaks with one of ours: ours goes
                    for other, om in jars.items():
                        if om.get("id") == dep and other in ours:
                            bad[other] = f"{m.get('id')} doesn't work with it"
    return bad


def _best(project: str, mc: str):
    return modrinth.best_version(project, mc, "fabric")


def _fetch_with_deps(project: str, mc: str, mods: Path, have: dict, ours: set, progress):
    """Download a mod and its required libraries, skipping any mod id that's
    already in the folder (unless it's an older copy we put there ourselves,
    which is replaced). Returns (main filename or None, [(mod id, filename)])."""
    added, queue, seen, main = [], [project], set(), None
    tmp = Path(tempfile.mkdtemp(prefix="peregrine-mods-"))
    try:
        while queue:
            p = queue.pop(0)
            version = _best(p, mc)
            if version is None or version["project_id"] in seen:
                continue
            seen.add(version["project_id"])
            file = next((f for f in version["files"] if f.get("primary")), version["files"][0])
            dest = mods / file["filename"]
            if dest.exists():
                mid = mod_id(dest)
            else:
                dl = tmp / file["filename"]
                net.fetch_all([net.Download(file["url"], dl, file.get("hashes", {}).get("sha1"),
                                            size=file.get("size"))], "Performance mods", progress)
                mid = mod_id(dl)
                old = have.get(mid) if mid else None
                if old is not None and old.name not in ours:
                    continue  # the player's own copy (or Fabric API from the in-game menu)
                if old is not None and old.exists():
                    old.unlink()  # our older copy
                shutil.move(str(dl), dest)
            if mid:
                have[mid] = dest
            if p == project:
                main = dest.name
            added.append((mid or p, dest.name))
            for dep in version.get("dependencies", []):
                if dep.get("dependency_type") == "required" and dep.get("project_id"):
                    queue.append(dep["project_id"])
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    return main, added


def _remove(mods: Path, names) -> None:
    for name in names:
        for f in (mods / name, mods / (name + ".disabled")):
            if f.exists():
                f.unlink()


def sync(inst, progress=None, force: bool = False) -> dict:
    """Make the instance's performance mods match its version. Returns a summary."""
    data = inst.data
    mods = inst.game_dir / "mods"
    # slug -> filename for the mods, "dep:<mod id>" -> filename for their libraries
    tracked = dict(data.get("perf_files") or {})
    on = data.get("performance", True) and data.get("loader") == "fabric"

    if not on:
        _remove(mods, tracked.values())
        inst.update(perf_files={})
        return {"removed": [k for k in tracked if not k.startswith("dep:")]}

    mc = data["mc_version"]
    now = time.time()
    fresh = (data.get("perf_mc") == mc and now - data.get("perf_checked", 0) < CHECK_EVERY
             and all((mods / n).exists() or (mods / (n + ".disabled")).exists() for n in tracked.values()))
    if fresh and not force:
        return {"checked": False}

    mods.mkdir(parents=True, exist_ok=True)
    if data.get("perf_mc") not in (None, mc):
        # New Minecraft version: everything we added was built for the old one.
        disabled = {k for k, n in tracked.items() if (mods / (n + ".disabled")).exists()}
        _remove(mods, tracked.values())
        tracked = {}
        off = sorted(set(data.get("perf_off") or []) | {k for k in disabled if not k.startswith("dep:")})
        inst.update(perf_off=off)
    skip = data.get("perf_skip") or {}
    if isinstance(skip, list):
        skip = {}
    skip = {s: v for s, v in skip.items() if v == mc}  # a crash on an older version doesn't count
    off = set(data.get("perf_off") or [])               # switched off by the player
    summary = {"added": [], "updated": [], "unavailable": [], "kept": []}
    for slug in SLUGS:
        if slug in skip or slug in off:
            continue
        mine = tracked.get(slug)
        if mine and (mods / (mine + ".disabled")).exists():
            off.add(slug)  # switched off in Content: leave it off
            continue
        try:
            version = _best(slug, mc)
        except Exception:
            summary["kept"].append(slug)  # offline: keep what's there
            continue
        if version is None:
            summary["unavailable"].append(slug)
            continue
        file = next((f for f in version["files"] if f.get("primary")), version["files"][0])
        if mine == file["filename"] and (mods / mine).exists():
            continue  # up to date
        have = _installed_ids(mods)
        try:
            main, added = _fetch_with_deps(slug, mc, mods, have, set(tracked.values()), progress)
        except Exception as e:
            print(f"[peregrine] {slug} not installed: {e}")
            continue
        for mid, fn in added:
            if fn != main and mid != "fabric-api":  # Fabric API belongs to the in-game menu too
                tracked[f"dep:{mid}"] = fn
        if main:
            if mine and mine != main and (mods / mine).exists():
                (mods / mine).unlink()
            tracked[slug] = main
            (summary["updated"] if mine else summary["added"]).append(slug)
        else:
            summary["kept"].append(slug)  # the player already has their own copy
    # Mods sometimes declare they don't work with a particular version of each other
    # (or of Minecraft). Take out ours until the game would start; the next daily
    # check tries them again, so a fixed release comes back by itself.
    for _ in range(len(tracked) + 1):
        bad = conflicts(mods, mc, set(tracked.values()))
        if not bad:
            break
        for key, fn in list(tracked.items()):
            if fn in bad:
                print(f"[peregrine] not using {fn}: {bad[fn]}")
                (mods / fn).unlink()
                tracked.pop(key)
                if not key.startswith("dep:"):
                    summary["unavailable"].append(key)
    tracked = {k: v for k, v in tracked.items() if (mods / v).exists() or (mods / (v + ".disabled")).exists()}
    inst.update(perf_files=tracked, perf_mc=mc, perf_checked=now, perf_skip=skip, perf_off=sorted(off))
    if progress and summary["unavailable"]:
        progress(1, 1, f"Not out yet for {mc}: {', '.join(summary['unavailable'])}")
    return summary


# ------------------------------------------------------------------ crashes

def after_crash(inst) -> list:
    """After the game crashed: if a performance mod is named in the crash, switch it
    off for this instance (it'll be tried again when it or the game updates).
    Returns the names of the mods switched off."""
    texts = []
    for f in [inst.log_file] + sorted((inst.game_dir / "crash-reports").glob("*.txt"))[-1:]:
        try:
            texts.append(f.read_text(errors="replace")[-200000:])
        except OSError:
            pass
    text = "\n".join(texts).lower()
    if not text:
        return []
    tracked = dict(inst.data.get("perf_files") or {})
    mods = inst.game_dir / "mods"
    blamed = []
    # Fabric's "incompatible mods" message names the mod at fault first:
    # "Mod 'More Culling' (moreculling) 1.0 is incompatible with ... 'Sodium' (sodium)".
    subjects = set(re.findall(r"mod '[^'\n]*' \(([a-z0-9_\-]+)\)[^\n]*?(?:is incompatible|requires)", text))
    text = "\n".join(l for l in text.splitlines()
                     if not re.search(r"mod '[^'\n]*' \([a-z0-9_\-]+\)[^\n]*?(?:is incompatible|requires)", l))
    for slug, name in tracked.items():
        if slug.startswith("dep:"):
            continue
        mid = mod_id(mods / name) if (mods / name).exists() else None
        keys = {k for k in (mid, slug) if k}
        if keys & subjects or any(re.search(rf"(mod|caused by|from|mixin|by) [^\n]{{0,40}}\b{re.escape(k)}\b", text)
               or f"{k}.mixins.json" in text or f"/{k}-" in text for k in keys):
            blamed.append(slug)
    if not blamed:
        return []
    for slug in blamed:
        f = mods / tracked.pop(slug)
        if f.exists():
            f.unlink()
    skip = inst.data.get("perf_skip") or {}
    if isinstance(skip, list):
        skip = {}
    for slug in blamed:
        skip[slug] = inst.data["mc_version"]  # tried again on the next Minecraft version
    inst.update(perf_files=tracked, perf_skip=skip)
    return [dict((s, n) for s, n, _ in MODS).get(s, s) for s in blamed]
