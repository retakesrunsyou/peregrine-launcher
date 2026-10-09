#!/usr/bin/env python3
"""Plays Minecraft through the real launcher code and checks Peregrine Client works.

    python3 tests/play_test.py MC_VERSION JAR_DIR OUT_DIR

Needs an X display (run under xvfb-run), xdotool and ImageMagick's `import`.
Uses a throwaway launcher folder inside OUT_DIR and an offline test account.

1. Creates a Fabric instance and prepares it exactly like pressing Play does
   (Fabric, Java, libraries, Peregrine Client from JAR_DIR, Fabric API).
2. Makes a world with the matching Minecraft server.
3. Launches twice: once on the main menu, once straight into the world. The mod's
   self-test (SelfTest.java) drives the menus, asks this script for real key
   presses, clicks and screenshots, and writes PASS/FAIL lines.
4. Writes OUT_DIR/summary.txt and exits non-zero if anything failed.
"""

import functools
import http.server
import os
import re
import shutil
import subprocess
import sys
import threading
import time
from pathlib import Path

MC, JARS, OUT = sys.argv[1], Path(sys.argv[2]).resolve(), Path(sys.argv[3]).resolve()
WIDTH, HEIGHT = 1280, 720
if OUT.exists():
    shutil.rmtree(OUT)
OUT.mkdir(parents=True)
os.environ["XDG_DATA_HOME"] = str(OUT / "home" / "data")
os.environ["XDG_CONFIG_HOME"] = str(OUT / "home" / "config")
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from peregrine import auth, client_mod, config, game, instances, paths  # noqa: E402

problems = []
results = []


def finish_report():
    (OUT / "summary.txt").write_text("\n".join(results) + "\n")


import atexit  # noqa: E402
atexit.register(finish_report)  # the summary is written even if this script dies


def note(kind, text):
    line = f"{kind} {text}"
    print(line, flush=True)
    results.append(line)
    if kind == "FAIL":
        problems.append(text)


def progress(done, total, msg):
    if total and done == total:
        print(f"  {msg}: {total} done", flush=True)


# ---------------------------------------------------------------- serve the jars

class Quiet(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *a):
        pass


server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), functools.partial(Quiet, directory=str(JARS)))
threading.Thread(target=server.serve_forever, daemon=True).start()
port = server.server_address[1]
client_mod._release_assets = lambda max_age=0: {
    f.name: {"name": f.name, "browser_download_url": f"http://127.0.0.1:{port}/{f.name}",
             "size": f.stat().st_size}
    for f in JARS.glob("*.jar")}

# ---------------------------------------------------------------- prepare like Play does

paths.ensure()
cfg = config.load()
cfg.update(width=WIDTH, height=HEIGHT, discord=False, check_updates=False, use_gamemode=False)
config.save(cfg)

# Which Java would Mojang's own runtime give? (Logged so a silent fallback shows up.)
try:
    comp = game.version_json(MC).get("javaVersion", {}).get("component", "jre-legacy")
    j = game.install_java(comp)
    note("INFO", f"Mojang runtime {comp}: {j} (Java {game.java_major(str(j)) if j else '-'})")
except Exception as e:
    note("INFO", f"Mojang runtime unavailable: {e!r}")

inst = instances.create("Test", MC, "fabric")
t0 = time.time()
try:
    cfg, prof, info, java = inst.prepare(progress)
except Exception as e:
    note("FAIL", f"launcher couldn't prepare Minecraft {MC}: {e!r}")
    raise
note("PASS", f"launcher prepared Fabric {MC} in {time.time() - t0:.0f}s with {java} (Java {game.java_major(java)})")

mods = sorted(p.name for p in (inst.game_dir / "mods").glob("*.jar"))
note("PASS" if f"peregrine-client-{MC}.jar" in mods else "FAIL", f"Peregrine Client installed ({', '.join(mods)})")
note("PASS" if any(m.startswith("fabric-api") for m in mods) else "FAIL", "Fabric API installed")
need = int(prof.get("javaVersion", {}).get("majorVersion", 8))
note("PASS" if game.java_major(java) >= need else "FAIL", f"Java {game.java_major(java)} meets the required {need}")

# ---------------------------------------------------------------- a world to join

def make_world():
    url = game.version_json(MC)["downloads"]["server"]["url"]
    sdir = OUT / "server"
    sdir.mkdir()
    jar = sdir / "server.jar"
    from peregrine import net
    net.fetch(net.Download(url, jar))
    (sdir / "eula.txt").write_text("eula=true\n")
    (sdir / "server.properties").write_text(
        "level-name=world\nonline-mode=false\ndifficulty=peaceful\nspawn-protection=0\n"
        "view-distance=4\nsimulation-distance=4\n")
    proc = subprocess.Popen([java, "-Xmx2G", "-jar", "server.jar", "nogui"], cwd=sdir,
                            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                            text=True)
    deadline = time.time() + 600
    for line in proc.stdout:
        if "Done (" in line:
            proc.stdin.write("stop\n")
            proc.stdin.flush()
        if time.time() > deadline:
            proc.kill()
            break
    proc.wait(60)
    world = sdir / "world"
    if not (world / "level.dat").is_file():
        raise RuntimeError("the server didn't make a world")
    shutil.copytree(world, inst.game_dir / "saves" / "world")


try:
    make_world()
    note("PASS", "made a test world with the Minecraft server")
except Exception as e:
    note("FAIL", f"couldn't make a test world: {e!r}")

# Skip first-run screens and keep software rendering quick.
(inst.game_dir / "options.txt").write_text(
    "onboardAccessibility:false\nrenderDistance:4\nsimulationDistance:5\ntutorialStep:none\n"
    "skipMultiplayerWarning:true\njoinedFirstServer:true\nnarrator:0\nsoundCategory_master:0.0\n"
    "pauseOnLostFocus:false\n")

# ---------------------------------------------------------------- play

def window_titles():
    """Titles of the windows on screen, read the way taskbars do (_NET_WM_NAME, then WM_NAME)."""
    ids = subprocess.run(["xdotool", "search", "--onlyvisible", "--classname", "."],
                         capture_output=True, text=True).stdout.split()
    titles = []
    for wid in ids:
        out = subprocess.run(["xprop", "-id", wid, "_NET_WM_NAME", "WM_NAME"], capture_output=True, text=True).stdout
        for line in out.splitlines():
            if "=" in line and '"' in line:
                titles.append(line.split("=", 1)[1].strip().strip('"'))
                break
    match = "\n".join(t for t in titles if t.startswith("Peregrine Client"))
    return match, " | ".join(titles)


class VirtualScreen:
    """Our own Xvfb, restarted if it dies (it occasionally crashes on CI machines)."""
    def __init__(self):
        self.proc = None

    def ensure(self):
        if self.proc is not None and self.proc.poll() is None:
            return
        n = 77
        lock = Path(f"/tmp/.X{n}-lock")
        lock.unlink(missing_ok=True)
        self.proc = subprocess.Popen(["Xvfb", f":{n}", "-screen", "0", f"{WIDTH}x{HEIGHT}x24", "-nolisten", "tcp"],
                                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        os.environ["DISPLAY"] = f":{n}"
        for _ in range(100):
            if Path(f"/tmp/.X11-unix/X{n}").exists():
                break
            time.sleep(0.1)
        time.sleep(0.5)


screen = VirtualScreen()


def shot(path: Path):
    """Screenshot of the whole virtual screen."""
    subprocess.run(f"xwd -root -silent | convert xwd:- png:'{path}'", shell=True, check=False,
                   timeout=30)


def xdo(*args):
    subprocess.run(["xdotool", *map(str, args)], check=False)


def handle(command: str, label: str):
    parts = command.split()
    what = parts[0]
    if what == "shot":
        time.sleep(0.5)
        shot(OUT / f"{label}-{parts[1]}.png")
        if label == "title" and parts[1] == "01-title":
            names, every = window_titles()
            note("PASS" if names.startswith("Peregrine Client") else "FAIL",
                 f"game window is titled {names.splitlines()[0]!r}" if names
                 else f"game window isn't titled Peregrine Client (windows: {every[:200]})")
    elif what == "key":
        xdo("key", "--delay", 80, parts[1])
    elif what in ("keydown", "keyup"):
        xdo(what, parts[1])
    elif what == "click":
        xdo("click", parts[1])
    elif what == "mousemove":
        xdo("mousemove", parts[1], parts[2])
    elif what == "clickat":
        xdo("mousemove", parts[1], parts[2])
        time.sleep(0.3)
        xdo("click", 1)
    elif what == "rclickat":
        xdo("mousemove", parts[1], parts[2])
        time.sleep(0.3)
        xdo("click", 3)
    elif what == "type":
        xdo("type", "--delay", 120, " ".join(parts[1:]))
    elif what == "drag":
        x1, y1, x2, y2 = map(int, parts[1:5])
        xdo("mousemove", x1, y1)
        time.sleep(0.3)
        xdo("mousedown", 1)
        for i in range(1, 11):
            time.sleep(0.05)
            xdo("mousemove", x1 + (x2 - x1) * i // 10, y1 + (y2 - y1) * i // 10)
        time.sleep(0.3)
        xdo("mouseup", 1)
    else:
        note("FAIL", f"unknown request from the mod: {command}")
    time.sleep(0.3)


def play(phase: str, plan: str, extra_game_args: list, timeout: int):
    """Runs a phase; if the virtual screen crashed underneath it, runs it once more."""
    for attempt in (1, 2):
        mark = len(results)
        screen.ensure()
        play_once(phase, plan, extra_game_args, timeout)
        log = (OUT / f"game-{phase}.log").read_text(errors="replace")
        if "XIO:  fatal IO error" not in log or attempt == 2:
            return
        # Not Peregrine's fault: forget this attempt's results and go again.
        dropped = results[mark:]
        del results[mark:]
        for line in dropped:
            if line.startswith("FAIL "):
                problems.remove(line[5:])
        note("INFO", f"{phase}: the test machine's virtual screen crashed; running this part again")
        shutil.rmtree(OUT / f"selftest-{phase}", ignore_errors=True)


def play_once(phase: str, plan: str, extra_game_args: list, timeout: int):
    st = OUT / f"selftest-{phase}"
    st.mkdir()
    cfg["extra_jvm_args"] = (f"-Dperegrine.selftest={st} -Dperegrine.selftest.phase={plan} "
                             f"-Dperegrine.selftest.width={WIDTH}")
    account = auth.offline_account("Tester")
    cmd = game.build_command(prof, info, java, account, inst.game_dir, cfg) + extra_game_args
    log = open(OUT / f"game-{phase}.log", "w")
    proc = subprocess.Popen(cmd, cwd=inst.game_dir, env=game.launch_env(cfg),
                            stdout=log, stderr=subprocess.STDOUT)
    xdo("mousemove", WIDTH // 2, HEIGHT // 2)
    handled = set()
    start = time.time()
    shot_at = start
    while proc.poll() is None:
        if time.time() - start > timeout:
            note("FAIL", f"{phase}: game still running after {timeout}s, stopping it")
            shot(OUT / f"timeout-{phase}.png")
            proc.kill()
            break
        ready = [r for r in st.glob("req-*") if not r.name.endswith(".tmp")]  # skip half-written ones
        for req in sorted(ready, key=lambda p: int(p.name.split("-")[1])):
            if req.name in handled:
                continue
            handled.add(req.name)
            handle(req.read_text().strip(), phase)
            (st / ("ack-" + req.name.split("-")[1])).write_text("ok\n")
        if time.time() - shot_at > 60:  # a progress picture every minute, for debugging
            shot_at = time.time()
            shot(OUT / f"progress-{phase}.png")
        time.sleep(0.2)
    proc.wait()
    log.close()
    code = proc.returncode
    res = st / "results.txt"
    lines = res.read_text().splitlines() if res.is_file() else []
    for line in lines:
        kind, _, text = line.partition(" ")
        note(kind if kind in ("PASS", "FAIL") else "INFO", f"{phase}: {text}")
    if not (st / "finished").exists():
        note("FAIL", f"{phase}: the self-test didn't finish (game exit code {code})")
    elif code not in (0, None):
        note("FAIL", f"{phase}: game exited with code {code}")
    latest = inst.game_dir / "logs" / "latest.log"
    if latest.is_file():
        shutil.copy(latest, OUT / f"latest-{phase}.log")
    check_log(phase, (OUT / f"game-{phase}.log").read_text(errors="replace"))


def check_log(phase: str, text: str):
    """Look for problems the self-test can't see: mixins that didn't apply, crashes."""
    ours = re.compile(r"net\.peregrine|peregrine-client|peregrine\$|\[peregrine\]", re.I)
    bad = []
    for line in text.splitlines():
        low = line.lower()
        if "self-test" in low:
            continue
        if line.strip().startswith("at net.peregrine"):
            bad.append("stack trace through Peregrine code: " + line.strip())
        elif ours.search(line) and ("warn" in low or "error" in low or "exception" in low):
            bad.append(line)
        elif "---- minecraft crash report ----" in low:
            bad.append(line)
    for line in bad[:20]:
        note("FAIL", f"{phase} log: {line.strip()[:400]}")
    crash = list((inst.game_dir / "crash-reports").glob("*.txt")) if (inst.game_dir / "crash-reports").is_dir() else []
    for c in crash:
        shutil.move(str(c), OUT / c.name)
        note("FAIL", f"{phase}: crash report {c.name}")


def guarded(*args):
    try:
        play(*args)
    except Exception as e:  # a broken test run must show up as a failure, not as "0 failed"
        note("FAIL", f"{args[0]}: the test script hit an error: {e!r}")


guarded("title", "title", [], 900)
if (inst.game_dir / "saves" / "world").is_dir():
    guarded("world", "world", ["--quickPlaySingleplayer", "world"], 1500)
    # Again with the one-click FPS mods, which many players use with Peregrine.
    try:
        added = inst.install_performance_mods(progress)
        note("PASS", "FPS mods installed: " + ", ".join(f for _, f in added))
        shutil.rmtree(inst.game_dir / "saves" / "world")
        shutil.copytree(OUT / "server" / "world", inst.game_dir / "saves" / "world")
        guarded("fps-mods", "world", ["--quickPlaySingleplayer", "world"], 1500)
    except Exception as e:
        note("FAIL", f"couldn't install the FPS mods: {e!r}")

server.shutdown()
print("\n" + ("ALL CHECKS PASSED" if not problems else f"{len(problems)} PROBLEM(S)"))
sys.exit(1 if problems else 0)
