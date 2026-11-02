"""ffmpeg for Clips: the game records with it, and the launcher's Clips page uses it for
thumbnails and trimming. Peregrine uses one already on your computer, or downloads a ready-made
build (from the FFmpeg-Builds project on GitHub) into its own tools folder."""

import os
import platform
import shutil
import subprocess
import sys
import tarfile
import zipfile
from pathlib import Path

from . import net, paths

DIR = paths.DATA / "tools" / "ffmpeg"
BASE = "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/"


def _exe(name: str) -> str:
    return name + (".exe" if sys.platform == "win32" else "")


def find(tool: str = "ffmpeg"):
    """The ffmpeg (or ffprobe) to use, or None."""
    own = DIR / _exe(tool)
    if own.is_file():
        return str(own)
    return shutil.which(tool)


def downloadable() -> bool:
    machine = platform.machine().lower()
    return (sys.platform == "win32" or sys.platform.startswith("linux")) and machine in ("x86_64", "amd64")


def download(progress=None) -> str:
    """Downloads ffmpeg and ffprobe into Peregrine's tools folder; returns ffmpeg's path."""
    if not downloadable():
        raise RuntimeError("There's no ready-made ffmpeg for this computer. Install ffmpeg yourself and restart Peregrine.")
    name = "ffmpeg-master-latest-win64-gpl.zip" if sys.platform == "win32" else "ffmpeg-master-latest-linux64-gpl.tar.xz"
    DIR.mkdir(parents=True, exist_ok=True)
    archive = DIR / name
    with net.session.get(BASE + name, stream=True, timeout=60) as r:
        r.raise_for_status()
        total = int(r.headers.get("content-length") or 0)
        done = 0
        with open(archive, "wb") as f:
            for chunk in r.iter_content(1 << 20):
                f.write(chunk)
                done += len(chunk)
                if progress:
                    progress(done, total, "Downloading ffmpeg")
    try:
        wanted = {_exe("ffmpeg"), _exe("ffprobe")}
        if name.endswith(".zip"):
            with zipfile.ZipFile(archive) as z:
                for info in z.infolist():
                    base = info.filename.rsplit("/", 1)[-1]
                    if base in wanted and "/bin/" in info.filename:
                        with z.open(info) as src, open(DIR / base, "wb") as dst:
                            shutil.copyfileobj(src, dst)
        else:
            with tarfile.open(archive) as t:
                for m in t.getmembers():
                    base = m.name.rsplit("/", 1)[-1]
                    if base in wanted and "/bin/" in m.name and m.isfile():
                        src = t.extractfile(m)
                        with open(DIR / base, "wb") as dst:
                            shutil.copyfileobj(src, dst)
                        os.chmod(DIR / base, 0o755)
    finally:
        archive.unlink(missing_ok=True)
    path = DIR / _exe("ffmpeg")
    if not path.is_file():
        raise RuntimeError("The ffmpeg download didn't contain ffmpeg.")
    return str(path)


def run(args, timeout=120):
    """Runs ffmpeg (args without the program); raises with its last error line on failure."""
    ff = find()
    if not ff:
        raise RuntimeError("ffmpeg isn't set up yet.")
    from .custom_cape import _plain_env
    r = subprocess.run([ff, "-hide_banner", "-loglevel", "error", "-y", *map(str, args)],
                       capture_output=True, timeout=timeout, env=_plain_env())
    if r.returncode != 0:
        lines = (r.stderr or b"").decode(errors="replace").strip().splitlines()
        raise RuntimeError("ffmpeg: " + (lines[-1] if lines else "failed"))


def duration(path) -> float:
    """A video's length in seconds (0 if unknown)."""
    probe = find("ffprobe")
    if not probe:
        return 0.0
    from .custom_cape import _plain_env
    try:
        r = subprocess.run([probe, "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(path)],
                           capture_output=True, timeout=20, env=_plain_env())
        return float((r.stdout or b"0").decode().strip() or 0)
    except (OSError, ValueError, subprocess.SubprocessError):
        return 0.0
