#!/usr/bin/env python3
"""Opens the real launcher window, visits every page in every theme, makes an
instance with the New instance dialog, and presses Play like a player would.

    QT_QPA_PLATFORM=offscreen xvfb-run python3 tests/ui_test.py MC_VERSION JAR_DIR OUT_DIR

The game runs Peregrine Client's quick self-test (main menu, menu, quit).
Screenshots of the launcher go to OUT_DIR; exits non-zero on any problem.
"""

import functools
import http.server
import os
import shutil
import sys
import threading
import time
import traceback
from pathlib import Path

MC, JARS, OUT = sys.argv[1], Path(sys.argv[2]).resolve(), Path(sys.argv[3]).resolve()
if OUT.exists():
    shutil.rmtree(OUT)
OUT.mkdir(parents=True)
os.environ["XDG_DATA_HOME"] = str(OUT / "home" / "data")
os.environ["XDG_CONFIG_HOME"] = str(OUT / "home" / "config")
os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from PySide6.QtCore import QEvent  # noqa: E402
from PySide6.QtWidgets import QApplication  # noqa: E402

from peregrine import auth, client_mod, config, instances, paths  # noqa: E402
from peregrine.ui import window as winmod  # noqa: E402
from peregrine.ui.dialogs import InstanceSettingsDialog, NewInstanceDialog  # noqa: E402
from peregrine.ui.theme import Theme  # noqa: E402

problems, lines = [], []


def write_summary():
    (OUT / "summary.txt").write_text("\n".join(lines) + "\n")


import atexit  # noqa: E402
atexit.register(write_summary)


def note(kind, text):
    line = f"{kind} {text}"
    print(line, flush=True)
    lines.append(line)
    if kind == "FAIL":
        problems.append(text)


def crash_hook(kind, value, tb):
    note("FAIL", "launcher error: " + "".join(traceback.format_exception(kind, value, tb)).strip()[-800:])


sys.excepthook = crash_hook

# Serve the freshly built mod as if it were the latest release.
srv = http.server.ThreadingHTTPServer(("127.0.0.1", 0), functools.partial(
    type("Q", (http.server.SimpleHTTPRequestHandler,), {"log_message": lambda *a: None}),
    directory=str(JARS)))
threading.Thread(target=srv.serve_forever, daemon=True).start()
client_mod._release_assets = lambda: {
    f.name: {"name": f.name, "id": 1, "size": f.stat().st_size,
             "browser_download_url": f"http://127.0.0.1:{srv.server_address[1]}/{f.name}"}
    for f in JARS.glob("*.jar")}

paths.ensure()
cfg = config.load()
cfg.update(discord=False, check_updates=True, width=854, height=480,
           extra_jvm_args=f"-Dperegrine.selftest={OUT / 'selftest'} -Dperegrine.selftest.phase=smoke")
config.save(cfg)
(OUT / "selftest").mkdir()
auth.add_account(auth.offline_account("Tester"))

app = QApplication(["peregrine"])
Theme.set(cfg["theme"], cfg["accent"])
win = winmod.MainWindow()
win.resize(1100, 720)
win.show()


def pump(seconds, until=None):
    end = time.time() + seconds
    while time.time() < end:
        app.processEvents()
        app.sendPostedEvents(None, QEvent.DeferredDelete)  # what app.exec() would do
        if until and until():
            return True
        time.sleep(0.03)
    return bool(until and until())


def shot(widget, name):
    pump(0.3)
    widget.grab().save(str(OUT / f"{name}.png"))


pump(3)
pages = {0: "home", 4: "modpacks", 2: "settings", 3: "accounts"}
for theme in ("dusk", "midnight", "light"):
    cfg["theme"] = theme  # the way the Settings page changes it
    config.save(cfg)
    win.apply_theme()
    for index, name in pages.items():
        win.go(index)
        pump(4 if name == "modpacks" else 1)
        shot(win, f"{theme}-{name}")
cfg["theme"] = "dusk"
config.save(cfg)
win.apply_theme()
note("PASS", "every page opens in every theme")

# ---- New instance dialog, as a player would use it
dlg = NewInstanceDialog(win, False)
dlg.show()
loaded = pump(60, lambda: dlg.versions_loaded and dlg.fabric_versions)
note("PASS" if loaded else "FAIL", "New instance dialog loads Minecraft and Fabric versions")
idx = dlg.version.findText(MC)
note("PASS" if idx >= 0 else "FAIL", f"Minecraft {MC} is in the version list")
dlg.version.setCurrentIndex(max(idx, 0))
pump(0.5)
note("PASS" if dlg.loader.value() == "fabric" else "FAIL",
     f"Fabric stays selected after the lists load (got {dlg.loader.value()})")
dlg.name.setText("UI Test")
shot(dlg, "new-instance")
inst = dlg.create()
dlg.close()
note("PASS" if inst.data["loader"] == "fabric" and inst.data["performance_mods"] else "FAIL",
     f"instance created: {inst.data['loader']} {inst.data['mc_version']}, FPS boost {inst.data['performance_mods']}")
inst.update(performance_mods=False)  # keep this run about the launcher and Peregrine Client

win.home.refresh()
win.go(0)
pump(1)
shot(win, "home-with-instance")
sd = InstanceSettingsDialog(win, inst)
sd.show()
shot(sd, "instance-settings")
sd.close()
win.open_content(inst)
pump(1)
shot(win, "content")

# ---- Play
win.play(inst)
started = pump(900, lambda: win.game_task is not None and not win.game_task.isRunning())
note("PASS" if started else "FAIL", "Play: game started and closed again within 15 minutes")
pump(2)
status = win.status.text()
note("PASS" if status == "Minecraft closed" else "FAIL", f"status after the game: {status!r}")
res = OUT / "selftest" / "results.txt"
for line in (res.read_text().splitlines() if res.is_file() else ["FAIL the game's self-test didn't run"]):
    kind, _, text = line.partition(" ")
    note(kind if kind in ("PASS", "FAIL") else "INFO", f"in game: {text}")
log = inst.log_file
if log.is_file():
    shutil.copy(log, OUT / "game.log")
shot(win, "after-play")

write_summary()
srv.shutdown()
print("\n" + ("ALL UI CHECKS PASSED" if not problems else f"{len(problems)} PROBLEM(S)"))
os._exit(1 if problems else 0)
