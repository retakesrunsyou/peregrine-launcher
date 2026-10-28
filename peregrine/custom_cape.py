"""Your own cape: a picture, a GIF or a short video turned into a Minecraft cape.

Only you see it (it stays on this computer). The picture fills the outside of the cape, the
inside gets a darker mirror of it, and the thin edges take its average colour. Still pictures
are kept sharp up to 4K (a 4096 x 2048 cape texture); moving ones get as much resolution as fits
a memory budget for their number of frames (up to 2048 x 1024), so they stay smooth in game.
Elytra take their wings from the same picture.

Written to <data>/capes/custom/: frame_000.png ... and cape.json
    {"v": version, "frames": n, "ms": [per-frame milliseconds], "size": [w, h], "name": source}
which the game reads (the Cosmetics module's "Your own" cape)."""

import json
import math
import os
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path

from . import paths

DIR = paths.DATA / "capes" / "custom"
PICTURES = ("png", "jpg", "jpeg", "webp", "bmp", "gif", "tga", "tif", "tiff")
VIDEOS = ("mp4", "webm", "mov", "mkv", "avi", "m4v")
MAX_FRAMES = 160
MAX_SECONDS = 8.0
MAX_FPS = 24
STILL_SCALE = 64                 # 64 x 32 cape layout at 64x: 4096 x 2048 ("4K")
MOVING_BUDGET = 128 * 1024 * 1024    # all frames together, in memory and on the graphics card
MOVING_MAX_SCALE = 32            # 2048 x 1024
PREVIEW_SCALE = 6                # the launcher's previews: 384 x 192
PREVIEW_FRAMES = 64


class CapeError(RuntimeError):
    pass


def exists() -> bool:
    return (DIR / "cape.json").is_file() and (DIR / "frame_000.png").is_file()


def info() -> dict:
    try:
        return json.loads((DIR / "cape.json").read_text())
    except (OSError, ValueError):
        return {}


def frames() -> list:
    """The cape's frame files, in order (one for a still picture)."""
    n = int(info().get("frames", 0))
    out = [DIR / f"frame_{i:03d}.png" for i in range(n)]
    return [f for f in out if f.is_file()]


def _preview_step(n: int) -> int:
    return max(1, math.ceil(n / PREVIEW_FRAMES))


def preview():
    """(still picture or None, moving frames or None, ms per preview frame) for the launcher."""
    meta = info()
    n = int(meta.get("frames", 0))
    if n < 1:
        return None, None, 125
    step = _preview_step(n)
    small = [DIR / f"small_{i:03d}.png" for i in range(0, n, step)]
    small = [f for f in small if f.is_file()]
    if n == 1 or len(small) < 2:
        return (small[0] if small else DIR / "frame_000.png"), None, 125
    ms = meta.get("ms") or [100]
    return None, small, max(20, int(sum(ms) / len(ms) * step))


def remove() -> None:
    shutil.rmtree(DIR, ignore_errors=True)


def kind(path) -> str:
    ext = Path(path).suffix.lower().lstrip(".")
    if ext in VIDEOS:
        return "video"
    if ext in PICTURES:
        return "picture"
    raise CapeError("Pick a picture (PNG, JPG, WebP, GIF) or a video (MP4, WebM, MOV).")


# ---------------------------------------------------------------------- reading the source

def read_picture(path):
    """[(QImage, ms)]: one frame for a still picture, every frame of a GIF or moving WebP."""
    from PySide6.QtGui import QImageReader
    r = QImageReader(str(path))
    r.setAutoTransform(True)
    first = r.read()
    if first.isNull():
        raise CapeError(f"Couldn't read that picture ({r.errorString()}).")
    if not r.supportsAnimation():
        return [(first, 0)]
    out = [(first.copy(), r.nextImageDelay() or 100)]
    while r.canRead() and len(out) < 1000:   # each read() gives the next frame
        img = r.read()
        if img.isNull():
            break
        out.append((img.copy(), r.nextImageDelay() or 100))
    if len(out) == 1:
        out = [(first, 0)]
    return out


def _plain_env() -> dict:
    """The environment for ffmpeg: without the packaged app's own library folder (its Qt libraries
    can clash with the ones ffmpeg uses)."""
    env = dict(os.environ)
    if "LD_LIBRARY_PATH_ORIG" in env:
        orig = env.pop("LD_LIBRARY_PATH_ORIG")
        if orig:
            env["LD_LIBRARY_PATH"] = orig
        else:
            env.pop("LD_LIBRARY_PATH", None)
    elif getattr(sys, "frozen", False):
        env.pop("LD_LIBRARY_PATH", None)
    return env


def read_video_ffmpeg(path):
    """Frames from a video with the ffmpeg program, if it's installed."""
    ff = shutil.which("ffmpeg")
    if not ff:
        return None
    from PySide6.QtGui import QImage
    with tempfile.TemporaryDirectory() as tmp:
        cmd = [ff, "-v", "error", "-t", str(MAX_SECONDS), "-i", str(path),
               "-vf", f"fps={MAX_FPS},scale='min(1280,iw)':-2", str(Path(tmp) / "f_%04d.png")]
        try:
            r = subprocess.run(cmd, timeout=180, capture_output=True, env=_plain_env())
        except (OSError, subprocess.SubprocessError) as e:
            raise CapeError(f"Couldn't read that video ({e}).")
        if r.returncode != 0:
            why = (r.stderr or b"").decode(errors="replace").strip().splitlines()
            raise CapeError("Couldn't read that video" + (f" ({why[-1][:200]})." if why else "."))
        files = sorted(Path(tmp).glob("f_*.png"))
        return [(QImage(str(f)).copy(), int(1000 / MAX_FPS)) for f in files]


def read_video_qt(path, progress=None):
    """Frames from a video with Qt's own player (runs on the window's thread; the window stays responsive)."""
    try:
        from PySide6.QtCore import QEventLoop, QTimer, QUrl
        from PySide6.QtMultimedia import QMediaPlayer, QVideoSink
    except ImportError:
        return None
    player = QMediaPlayer()
    sink = QVideoSink()
    player.setVideoOutput(sink)
    got = []
    state = {"next": 0.0, "err": None}
    loop = QEventLoop()
    step = 1000.0 / MAX_FPS

    def frame(f):
        t = player.position()
        if t + 1 < state["next"] or not f.isValid():
            return
        img = f.toImage()
        if img.isNull():
            return
        if img.width() > 1280:
            from PySide6.QtCore import Qt
            img = img.scaledToWidth(1280, Qt.TransformationMode.SmoothTransformation)
        got.append((img.copy(), int(step)))
        state["next"] = t + step
        if progress:
            progress(f"Reading the video… {len(got)} frames")
        if len(got) >= MAX_FRAMES or t >= MAX_SECONDS * 1000:
            player.stop()
            loop.quit()

    def status(s):
        if s in (QMediaPlayer.MediaStatus.EndOfMedia, QMediaPlayer.MediaStatus.InvalidMedia):
            if s == QMediaPlayer.MediaStatus.InvalidMedia:
                state["err"] = "That video can't be played here."
            loop.quit()

    sink.videoFrameChanged.connect(frame)
    player.mediaStatusChanged.connect(status)
    player.errorOccurred.connect(lambda e, s="": (state.__setitem__("err", s or "That video can't be played here."), loop.quit()))
    QTimer.singleShot(int(MAX_SECONDS * 1000 + 15000), loop.quit)
    player.setSource(QUrl.fromLocalFile(str(path)))
    player.setPlaybackRate(2.0)
    player.play()
    loop.exec()
    player.stop()
    if state["err"] and not got:
        raise CapeError(state["err"])
    return got


# ---------------------------------------------------------------------- making the cape

def _trim(frames_in):
    """At most MAX_SECONDS and MAX_FRAMES; frames faster than MAX_FPS are merged."""
    out, total, carry = [], 0, 0
    min_ms = int(1000 / MAX_FPS)
    for img, ms in frames_in:
        ms = ms or 100
        if out and ms + carry < min_ms:
            carry += ms
            continue
        out.append([img, ms + carry])
        carry = 0
        total += out[-1][1]
        if total >= MAX_SECONDS * 1000 or len(out) >= MAX_FRAMES:
            break
    return out


def scale_for(n_frames: int, src_h: int) -> int:
    """Pixels per cape pixel: as sharp as the source needs, within the memory budget."""
    want = max(4, math.ceil(src_h / 16))
    if n_frames <= 1:
        return max(8, min(STILL_SCALE, want))
    fit = int(math.sqrt(MOVING_BUDGET / (n_frames * 64 * 32 * 4)))
    return max(4, min(MOVING_MAX_SCALE, fit, max(8, want)))


def is_layout(img) -> bool:
    """Already a cape texture (64 x 32 or a sharper 2:1 version of it): used as it is."""
    return img.width() == 2 * img.height() and img.width() % 64 == 0


def compose(img, s):
    """One cape texture (64s x 32s) from a picture."""
    from PySide6.QtCore import QRect, QRectF, Qt
    from PySide6.QtGui import QColor, QImage, QPainter
    W, H = 64 * s, 32 * s
    out = QImage(W, H, QImage.Format.Format_ARGB32_Premultiplied)
    out.fill(Qt.GlobalColor.transparent)
    p = QPainter(out)
    p.setRenderHint(QPainter.RenderHint.SmoothPixmapTransform, True)
    if is_layout(img):
        p.drawImage(QRectF(0, 0, W, H), img, QRectF(0, 0, img.width(), img.height()))
        p.end()
        return out
    # the picture cut to the cape's shape (10 wide, 16 tall), centred
    iw, ih = img.width(), img.height()

    def cover(aspect):
        cw, ch = (ih * aspect, ih) if iw / ih > aspect else (iw, iw / aspect)
        return QRectF((iw - cw) / 2, (ih - ch) / 2, cw, ch)

    src = cover(10 / 16)
    # its average colour, a little darker: behind see-through parts, and on the thin edges
    c = img.copy(src.toRect()).scaled(1, 1, Qt.AspectRatioMode.IgnoreAspectRatio,
                                      Qt.TransformationMode.SmoothTransformation).pixelColor(0, 0)
    c = QColor(int(c.red() * 0.7), int(c.green() * 0.7), int(c.blue() * 0.7))
    front = QRectF(1 * s, 1 * s, 10 * s, 16 * s)
    back = QRectF(12 * s, 1 * s, 10 * s, 16 * s)
    wing = QRectF(22 * s, 0, 24 * s, 22 * s)       # elytra wings take the picture too
    for r in (front, back, wing):
        p.fillRect(r, c)
    for r in (QRect(0, 1 * s, 1 * s, 16 * s), QRect(11 * s, 1 * s, 1 * s, 16 * s),
              QRect(1 * s, 0, 10 * s, 1 * s), QRect(11 * s, 0, 10 * s, 1 * s)):
        p.fillRect(r, c)
    p.drawImage(front, img, src)
    # inside: the same, mirrored and darker
    p.save()
    p.translate(back.x() + back.width(), back.y())
    p.scale(-1, 1)
    p.drawImage(QRectF(0, 0, back.width(), back.height()), img, src)
    p.restore()
    p.fillRect(back, QColor(0, 0, 0, 120))
    p.drawImage(wing, img, cover(wing.width() / wing.height()))
    p.end()
    return out


def make(source_frames, name: str, progress=None) -> dict:
    """Writes the cape from [(QImage, ms)] frames (call off the window's thread). Returns cape.json."""
    from PySide6.QtCore import Qt
    fr = _trim(source_frames)
    if not fr:
        raise CapeError("There was nothing in that file to make a cape from.")
    first = fr[0][0]
    if is_layout(first):   # a cape texture already: keep its sharpness (within the budget for moving ones)
        s = min(first.width() // 64, scale_for(len(fr), 16 * STILL_SCALE) if len(fr) > 1 else STILL_SCALE)
    else:
        s = scale_for(len(fr), max(img.height() for img, _ in fr))
    tmp = DIR.with_name("custom-new")
    shutil.rmtree(tmp, ignore_errors=True)
    tmp.mkdir(parents=True, exist_ok=True)
    for i, (img, ms) in enumerate(fr):
        tex = compose(img, s)
        if not tex.save(str(tmp / f"frame_{i:03d}.png"), "PNG", 60):
            raise CapeError("Couldn't save the cape.")
        if i % _preview_step(len(fr)) == 0:    # small copies for the launcher's previews
            tex.scaled(64 * PREVIEW_SCALE, 32 * PREVIEW_SCALE, Qt.AspectRatioMode.IgnoreAspectRatio,
                       Qt.TransformationMode.SmoothTransformation).save(str(tmp / f"small_{i:03d}.png"), "PNG")
        if progress:
            progress(f"Making your cape… {i + 1} of {len(fr)}")
    meta = {"v": int(time.time() * 1000) % 2_000_000_000, "frames": len(fr),
            "ms": [int(ms) for _, ms in fr] if len(fr) > 1 else [0],
            "size": [64 * s, 32 * s], "name": name}
    (tmp / "cape.json").write_text(json.dumps(meta))
    shutil.rmtree(DIR, ignore_errors=True)
    tmp.rename(DIR)
    return meta


def make_from_file(path, progress=None) -> dict:
    """Pictures and GIFs, and videos when the ffmpeg program is installed (else read_video_qt
    on the window's thread first, then make())."""
    name = Path(path).name
    if kind(path) == "picture":
        return make(read_picture(path), name, progress)
    got = read_video_ffmpeg(path)
    if got is None:
        raise CapeError("Videos need ffmpeg here.")
    if not got:
        raise CapeError("Couldn't read any pictures from that video.")
    return make(got, name, progress)
