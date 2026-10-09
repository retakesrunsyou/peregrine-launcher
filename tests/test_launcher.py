#!/usr/bin/env python3
"""Offline checks for launcher code that's hard to see going wrong by hand.

    python3 tests/test_launcher.py
"""

import functools
import http.server
import os
import sys
import tempfile
import threading
from pathlib import Path

TMP = Path(tempfile.mkdtemp())
os.environ["XDG_DATA_HOME"] = str(TMP / "data")
os.environ["XDG_CONFIG_HOME"] = str(TMP / "config")
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from peregrine import client_mod, game, paths  # noqa: E402

failures = 0


def check(label, ok):
    global failures
    print(("ok   " if ok else "FAIL ") + label)
    if not ok:
        failures += 1


# ---------------------------------------------------------------- client mod updates

serve = TMP / "serve"
serve.mkdir()
srv = http.server.ThreadingHTTPServer(("127.0.0.1", 0), functools.partial(
    type("Q", (http.server.SimpleHTTPRequestHandler,), {"log_message": lambda *a: None}),
    directory=str(serve)))
threading.Thread(target=srv.serve_forever, daemon=True).start()
url = f"http://127.0.0.1:{srv.server_address[1]}"


class Inst:
    def __init__(self, folder):
        self.game_dir = folder
        self.data = {"loader": "fabric", "mc_version": "1.21.1"}


inst = Inst(TMP / "inst")
mods = inst.game_dir / "mods"
mods.mkdir(parents=True)
(mods / "fabric-api-0.1.jar").write_bytes(b"x")  # so Modrinth isn't asked
jar = "peregrine-client-1.21.1.jar"
release = {}
client_mod._release_assets = lambda max_age=0: release


def publish(content: bytes, asset_id: int):
    (serve / jar).write_bytes(content)
    release.clear()
    release[jar] = {"name": jar, "id": asset_id, "updated_at": f"t{asset_id}", "size": len(content),
                    "browser_download_url": f"{url}/{jar}"}


publish(b"AAAA", 1)
check("first launch installs the in-game mod", client_mod.sync(inst, True) == "installed"
      and (mods / jar).read_bytes() == b"AAAA")
publish(b"BBBB", 2)  # a new release with a jar of the same size
client_mod.sync(inst, True)
check("a new release replaces a same-size jar", (mods / jar).read_bytes() == b"BBBB")
(serve / jar).write_bytes(b"CCCC")  # same release: no new download expected
client_mod.sync(inst, True)
check("the same release isn't downloaded again", (mods / jar).read_bytes() == b"BBBB")
(mods / "peregrine-client-1.20.jar").write_bytes(b"old")
client_mod.sync(inst, True)
check("copies for other versions are removed", not (mods / "peregrine-client-1.20.jar").exists())
(mods / jar).rename(mods / (jar + ".disabled"))
check("a mod switched off in Content stays off", client_mod.sync(inst, True) == "turned off in Content"
      and not (mods / jar).exists())
(mods / (jar + ".disabled")).rename(mods / jar)
check("turning the setting off removes it", client_mod.sync(inst, False) == "removed" and not (mods / jar).exists())
release.clear()
check("versions without a build launch without it", client_mod.sync(inst, True) == "no build for this version")

# A release whose files weren't attached yet when the launcher last looked:
# the launcher checks again at once instead of waiting an hour.
seen_ages = []
def late_release(max_age=3600):
    seen_ages.append(max_age)
    if max_age > 60:
        return {}
    publish(b"DDDD", 3)
    return release
client_mod._release_assets = late_release
check("a just-published build is found on the next Play", client_mod.sync(inst, True) == "installed"
      and (mods / jar).read_bytes() == b"DDDD" and seen_ages == [3600, 60])

# ---------------------------------------------------------------- Java choice

real = sys.executable  # not java: java_major returns 0 for it
check("java_major reads a real JDK", game.java_major("java") >= 8 if os.system("java -version >/dev/null 2>&1") == 0 else True)
check("java_major of something that isn't Java is 0", game.java_major("/bin/true") == 0)
fake = TMP / "java8"
fake.write_text('#!/bin/sh\necho \'openjdk version "1.8.0_402"\' >&2\n')
fake.chmod(0o755)
check("Java 8's version string is read as 8", game.java_major(str(fake)) == 8)
try:
    game.find_java({"javaVersion": {"majorVersion": 21}}, str(fake))
    check("a too-old Java in Settings is refused", False)
except RuntimeError as e:
    check("a too-old Java in Settings is refused", "needs Java 21" in str(e))

fake25 = TMP / "java25"
fake25.write_text('#!/bin/sh\necho \'openjdk version "25.0.1" 2025-10-21\' >&2\n')
fake25.chmod(0o755)
calls = []
orig = game.install_java, game.install_adoptium
game.install_java = lambda c, p=None: calls.append("mojang") or None
game.install_adoptium = lambda m, p=None: calls.append(f"temurin{m}") or fake25
check("falls back to Temurin when Mojang has no runtime",
      game.find_java({"javaVersion": {"majorVersion": 25, "component": "java-runtime-x"}}) == str(fake25)
      and calls == ["mojang", "temurin25"])
game.install_java = lambda c, p=None: fake  # Mojang gives an old Java: must not be used
calls.clear()
check("an old Mojang runtime isn't used for a newer game",
      game.find_java({"javaVersion": {"majorVersion": 25}}) == str(fake25))
game.install_java, game.install_adoptium = orig

# ---------------------------------------------------------------- library rules

libs = [
    {"name": "a:windows-only:1", "downloads": {"artifact": {"url": "u", "path": "w.jar"}},
     "rules": [{"action": "allow", "os": {"name": "windows"}}]},
    {"name": "a:everywhere:1", "downloads": {"artifact": {"url": "u", "path": "e.jar"}}},
    {"name": "a:not-mac:1", "downloads": {"artifact": {"url": "u", "path": "n.jar"}},
     "rules": [{"action": "allow"}, {"action": "disallow", "os": {"name": "osx"}}]},
    {"name": "net.fabricmc:fabric-loader:0.16", "url": "https://maven.fabricmc.net/"},
    {"name": "net.fabricmc:fabric-loader:0.15", "url": "https://maven.fabricmc.net/"},
]
cp, nat = game.collect_libraries(libs)
names = [d.path.name for d in cp]
check("library rules keep Linux and shared libraries only", names[:2] == ["e.jar", "n.jar"] and "w.jar" not in names)
check("a mod loader's library wins over a duplicate", [n for n in names if "fabric-loader" in n] == ["fabric-loader-0.16.jar"])

# ---------------------------------------------------------------- window sizes

from peregrine import config, display  # noqa: E402
fits = display.presets_for(1920, 1080)
check("sizes bigger than the screen aren't offered", all(w <= 1920 and h <= 1080 for w, h, _ in fits))
check("the biggest size that fits comes first", fits[0][:2] == (1920, 1080))
odd = display.presets_for(1700, 1000)
check("an unusual screen size is still offered", odd[0][:2] == (1700, 1000))
check("sizes are labelled with their shape", display.label(2560, 1080, "Ultrawide") == "2560 × 1080   ·   21:9   ·   Ultrawide"
      and display.aspect(1920, 1200) == "16:10" and display.aspect(5120, 1440) == "32:9")
import json  # noqa: E402
paths.CONFIG.mkdir(parents=True, exist_ok=True)
paths.CONFIG_FILE.write_text(json.dumps({"width": 1600, "height": 900}))
check("a size chosen in an older version is kept", config.load()["window_mode"] == "size")
paths.CONFIG_FILE.write_text(json.dumps({"width": 0, "height": 0}))
check("new players get Fit my screen", config.load()["window_mode"] == "screen")
paths.CONFIG_FILE.unlink()
check("no settings yet means Fit my screen", config.load()["window_mode"] == "screen")

srv.shutdown()
# ---------------------------------------------------------------- performance mode

import io  # noqa: E402
import json as _json  # noqa: E402
import zipfile  # noqa: E402

from peregrine import fabric, instances, net, performance  # noqa: E402

IDS = {"sodium": "sodium", "lithium": "lithium", "ferrite-core": "ferritecore", "entityculling": "entityculling",
       "immediatelyfast": "immediatelyfast", "modernfix": "modernfix", "moreculling": "moreculling",
       "dynamic-fps": "dynamic_fps", "fabric-api": "fabric-api", "cloth": "cloth-config"}
catalog = {}   # (project, mc) -> version number; missing = not out for that version
asked = []


RULES = {}  # mod id -> extra fabric.mod.json fields (version, breaks, depends)


def fake_jar(mid):
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        z.writestr("fabric.mod.json", _json.dumps(dict({"id": mid, "version": "1.0.0"}, **RULES.get(mid, {}))))
    return buf.getvalue()


def fake_best(project, mc, loader):
    asked.append(project)
    n = catalog.get((project, mc))
    if n is None:
        return None
    deps = [{"dependency_type": "required", "project_id": "cloth"}] if project == "moreculling" else []
    if project == "sodium":
        deps.append({"dependency_type": "required", "project_id": "fabric-api"})
    name = f"{project}-{n}+{mc}.jar"
    return {"project_id": project, "dependencies": deps,
            "files": [{"primary": True, "filename": name, "url": f"fake://{project}/{name}"}]}


def fake_fetch_all(downloads, label, progress=None):
    for d in downloads:
        project = d.url.split("/")[2]
        d.path.parent.mkdir(parents=True, exist_ok=True)
        d.path.write_bytes(fake_jar(IDS[project]))


performance.modrinth.best_version = fake_best
performance.net.fetch_all = fake_fetch_all
for slug in performance.SLUGS + ["cloth", "fabric-api"]:
    catalog[(slug, "1.21.1")] = 1
del catalog[("moreculling", "1.21.1")]  # pretend it isn't out for 1.21.1 yet

pinst = instances.create("Perf", "1.21.1", "fabric", True)
pmods = pinst.game_dir / "mods"
pmods.mkdir(parents=True)
(pmods / "fabric-api-0.1.jar").write_bytes(fake_jar("fabric-api"))  # from the in-game menu
(pmods / "my-sodium-custom.jar").write_bytes(fake_jar("sodium"))    # the player's own copy
r = performance.sync(pinst)
files = pinst.data["perf_files"]
check("performance mode installs the speed-up mods for this version",
      {"lithium", "ferrite-core", "entityculling", "immediatelyfast", "modernfix", "dynamic-fps"} <= set(files))
check("the player's own Sodium is kept, not doubled",
      "sodium" not in files and sorted(p.name for p in pmods.glob("sodium*")) == [])
check("a mod not out for this version is skipped", "moreculling" in r["unavailable"])
check("Fabric API isn't installed twice", sorted(p.name for p in pmods.iterdir() if "fabric-api" in p.name) == ["fabric-api-0.1.jar"])
asked.clear()
check("no Modrinth lookups again the same day", performance.sync(pinst) == {"checked": False} and not asked)

# a mod comes out for 1.21.1, another gets an update
catalog[("moreculling", "1.21.1")] = 1
catalog[("lithium", "1.21.1")] = 2
pinst.update(perf_checked=0)
r = performance.sync(pinst)
check("a newly released mod is added the next day (with its library)",
      "moreculling" in pinst.data["perf_files"] and "dep:cloth-config" in pinst.data["perf_files"])
check("an update replaces the old file", (pmods / "lithium-2+1.21.1.jar").exists() and not (pmods / "lithium-1+1.21.1.jar").exists())

# switched off in Content: stays off
f = pmods / pinst.data["perf_files"]["entityculling"]
f.rename(f.with_name(f.name + ".disabled"))
pinst.update(perf_checked=0)
performance.sync(pinst)
check("a mod switched off in Content stays off", not f.exists() and f.with_name(f.name + ".disabled").exists())

# moving the instance to another Minecraft version
for slug in performance.SLUGS + ["cloth", "fabric-api"]:
    catalog[(slug, "1.21.4")] = 7
pinst.update(mc_version="1.21.4")
performance.sync(pinst)
names = sorted(p.name for p in pmods.iterdir())
check("a new Minecraft version swaps every mod for that version",
      not any("+1.21.1" in n for n in names) and "lithium-7+1.21.4.jar" in names)
check("a mod the player switched off stays off on the new version", "entityculling" not in pinst.data["perf_files"])
check("the player's own files are never touched", "my-sodium-custom.jar" in names and "fabric-api-0.1.jar" in names)

# a crash caused by one of the mods
pinst.log_file.write_text("[main/ERROR]: Mixin apply for mod lithium failed lithium.mixins.json:ai.Foo\n"
                          "Caused by: org.spongepowered.asm.mixin.throwables.MixinApplyError\n")
blamed = performance.after_crash(pinst)
check("a crash from a performance mod switches it off", blamed == ["Lithium"]
      and not any("lithium" in n for n in (p.name for p in pmods.iterdir())))
pinst.update(perf_checked=0)
performance.sync(pinst)
check("...and it isn't put back on this version", "lithium" not in pinst.data["perf_files"])
pinst.log_file.write_text("[main/INFO]: Loading 12 mods:\n\t- sodium 0.6\n\t- lithium 0.14\nSomething else broke\n")
check("a crash that doesn't name a mod blames nothing", performance.after_crash(pinst) == [])

# switching performance mode off
pinst.set_performance(False)
names = sorted(p.name for p in pmods.iterdir())
check("turning performance mode off removes only what it added",
      names == ["fabric-api-0.1.jar", "my-sodium-custom.jar"] and pinst.data["perf_files"] == {})

# mods that declare they don't work together (as on Minecraft 1.21: More Culling 1.0.10
# needs a newer Sodium than the newest one for 1.21)
for slug in performance.SLUGS + ["cloth", "fabric-api"]:
    catalog[(slug, "1.21")] = 3
RULES["sodium"] = {"version": "0.6.13+mc1.21.1"}
RULES["moreculling"] = {"version": "1.0.10", "breaks": {"sodium": "<=0.6.13"}}
RULES["modernfix"] = {"version": "5.0", "depends": {"minecraft": "~1.21.1"}}
cinst = instances.create("Conflicts", "1.21", "fabric", True)
r = performance.sync(cinst)
cfiles = cinst.data["perf_files"]
check("a mod that says it breaks with this Sodium is left out", "moreculling" not in cfiles and "sodium" in cfiles
      and not any("moreculling" in p.name for p in (cinst.game_dir / "mods").iterdir()))
check("a mod built for a newer Minecraft is left out", "modernfix" not in cfiles)
check("the rest still install", {"lithium", "ferrite-core", "dynamic-fps"} <= set(cfiles))
RULES.clear()
cinst.log_file.write_text("Incompatible mods found!\n - Mod 'More Culling' (moreculling) 1.0.10 is incompatible "
                          "with version 0.6.13 or earlier of mod 'Sodium' (sodium), yet a conflicting version is present\n")
cinst.update(perf_files=dict(cfiles, moreculling="moreculling-3+1.21.jar"))
(cinst.game_dir / "mods" / "moreculling-3+1.21.jar").write_bytes(fake_jar("moreculling"))
check("Fabric's 'incompatible mods' crash blames the mod at fault, not Sodium",
      performance.after_crash(cinst) == ["More Culling"] and "sodium" in cinst.data["perf_files"])

# fast settings
opts = pinst.game_dir / "options.txt"
opts.write_text("renderDistance:16\nsimulationDistance:5\nfov:0.5\nao:true\n")
changed = performance.apply_fast_settings(pinst.game_dir)
o = dict(l.split(":", 1) for l in opts.read_text().splitlines())
check("fast settings: high render distance lowered to 10", o["renderDistance"] == "10")
check("fast settings: a low simulation distance is kept", o["simulationDistance"] == "5")
check("fast settings: other settings untouched", o["fov"] == "0.5")
check("fast settings: smooth lighting off, Fast graphics, no clouds",
      o["ao"] == "false" and o["graphicsMode"] == "0" and o["graphicsPreset"] == '"custom"' and o["renderClouds"] == '"false"')

# a Vanilla instance from before performance mode gets Fabric for speed, and goes back when it's off
vinst = instances.create("Old vanilla", "1.21.1", "vanilla")
vinst.data.pop("performance")
vinst.save()
vinst = instances.Instance(vinst.folder)
fabric.supported_game_versions = lambda: frozenset({"1.21.1"})
vinst._prepare_performance(None)
check("an old Vanilla instance moves to Fabric for performance mode",
      vinst.data["loader"] == "fabric" and vinst.data.get("auto_fabric"))
check("...and gets fast settings once", vinst.data.get("perf_settings") == performance.PRESET_VERSION
      and (vinst.game_dir / "options.txt").is_file())
vinst.set_performance(False)
check("switching performance mode off puts it back to Vanilla", vinst.data["loader"] == "vanilla")
explicit = instances.create("Pure", "1.21.1", "vanilla")
explicit._prepare_performance(None)
check("an instance made as Vanilla stays Vanilla", explicit.data["loader"] == "vanilla")

print("\nALL LAUNCHER TESTS PASSED" if not failures else f"\n{failures} FAILED")
sys.exit(1 if failures else 0)
