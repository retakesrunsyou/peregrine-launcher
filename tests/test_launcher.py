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
client_mod._release_assets = lambda: release


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

srv.shutdown()
print("\nALL LAUNCHER TESTS PASSED" if not failures else f"\n{failures} FAILED")
sys.exit(1 if failures else 0)
