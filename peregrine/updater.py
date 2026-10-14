"""Self-updates from GitHub Releases.

A release is a GitHub release tagged like v1.2.0. Its files are built by the
release workflow:
- peregrine-launcher.zip: for installs made with install.sh (runs it again),
- Peregrine-Setup.exe: the Windows app (the installer runs quietly over the old one),
- Peregrine-x86_64.AppImage: the Linux app (the file is swapped for the new one).
User data is never touched.
"""

import os
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path
from typing import Optional

from . import UPDATE_REPO, VERSION, net

ASSET_NAME = "peregrine-launcher.zip"
WINDOWS_ASSET = "Peregrine-Setup.exe"
APPIMAGE_ASSET = "Peregrine-x86_64.AppImage"


def kind() -> str:
    """How this copy was installed: "windows", "appimage", "script" (install.sh) or "source"."""
    if getattr(sys, "frozen", False):
        if sys.platform == "win32":
            return "windows"
        if os.environ.get("APPIMAGE"):
            return "appimage"
        return "source"
    return "script" if installed_app_dir() is not None else "source"


def _asset_name() -> str:
    return {"windows": WINDOWS_ASSET, "appimage": APPIMAGE_ASSET}.get(kind(), ASSET_NAME)


def _parse(v: str) -> tuple:
    parts = []
    for p in v.strip().lstrip("vV").split("."):
        digits = "".join(ch for ch in p if ch.isdigit())
        parts.append(int(digits or 0))
    return tuple(parts)


def is_newer(latest: str, current: str = VERSION) -> bool:
    return _parse(latest) > _parse(current)


def installed_app_dir() -> Optional[Path]:
    """The folder install.sh made, or None when running from a source checkout."""
    app = Path(__file__).resolve().parent.parent
    return app if (app / "venv").is_dir() else None


def check() -> Optional[dict]:
    """Return {"version", "url", "notes"} if a newer release exists, else None."""
    if not UPDATE_REPO:
        return None
    release = net.get_json(f"https://api.github.com/repos/{UPDATE_REPO}/releases/latest",
                           headers={"Accept": "application/vnd.github+json"})
    latest = release.get("tag_name", "")
    asset = next((a for a in release.get("assets", []) if a["name"] == _asset_name()), None)
    if not asset or not is_newer(latest):
        return None
    return {"version": latest.lstrip("vV"), "url": asset["browser_download_url"],
            "notes": (release.get("body") or "").strip()}


def apply(update: dict, progress=None) -> None:
    """Download the release and run its installer. Restart the app afterwards."""
    how = kind()
    if how == "windows":
        return _apply_windows(update, progress)
    if how == "appimage":
        return _apply_appimage(update, progress)
    if installed_app_dir() is None:
        raise RuntimeError("This copy wasn't installed with install.sh, so it can't update itself. "
                           "Download the new version and run install.sh.")
    work = Path(tempfile.mkdtemp(prefix="peregrine-update-"))
    try:
        if progress:
            progress(0, 1, f"Downloading Peregrine {update['version']}")
        zip_path = work / ASSET_NAME
        net.fetch(net.Download(update["url"], zip_path))
        out = work / "src"
        with zipfile.ZipFile(zip_path) as z:
            for member in z.namelist():
                if not (out / member).resolve().is_relative_to(out.resolve()):
                    raise RuntimeError("The update file looks damaged.")
            z.extractall(out)
        installer = next(out.rglob("install.sh"), None)
        if installer is None:
            raise RuntimeError("The update is missing install.sh.")
        if progress:
            progress(1, 2, "Installing update")
        result = subprocess.run(["bash", str(installer)], capture_output=True, text=True)
        if result.returncode != 0:
            tail = (result.stdout + result.stderr).strip().splitlines()[-6:]
            raise RuntimeError("The update didn't install:\n" + "\n".join(tail))
    finally:
        shutil.rmtree(work, ignore_errors=True)


def _apply_windows(update: dict, progress=None) -> None:
    """Download the new installer and start it quietly; it closes this app and opens the new one."""
    work = Path(tempfile.mkdtemp(prefix="peregrine-update-"))
    setup = work / WINDOWS_ASSET
    if progress:
        progress(0, 1, f"Downloading Peregrine {update['version']}")
    net.fetch(net.Download(update["url"], setup))
    if progress:
        progress(1, 2, "Installing update")
    subprocess.Popen([str(setup), "/SILENT", "/SUPPRESSMSGBOXES", "/NORESTART", "/CLOSEAPPLICATIONS",
                      "/RESTARTAPPLICATIONS"], creationflags=getattr(subprocess, "DETACHED_PROCESS", 0))


def _apply_appimage(update: dict, progress=None) -> None:
    """Swap the AppImage file for the new one (the running copy keeps going until restart)."""
    current = Path(os.environ["APPIMAGE"])
    new = current.with_name(current.name + ".new")
    if progress:
        progress(0, 1, f"Downloading Peregrine {update['version']}")
    net.fetch(net.Download(update["url"], new))
    new.chmod(0o755)
    os.replace(new, current)


def restart() -> None:
    how = kind()
    if how == "windows":
        os._exit(0)  # the installer starts the new version
    if how == "appimage":
        os.execv(os.environ["APPIMAGE"], [os.environ["APPIMAGE"]])
    if getattr(sys, "frozen", False):
        os.execv(sys.executable, [sys.executable])
    os.execv(sys.executable, [sys.executable, "-m", "peregrine"])
