"""Your own Click sounds: a sound file per kind of key or mouse button, used by the game's
Click sounds instead of Peregrine's own. Kept as short 16-bit WAV files in
<data>/click-sounds/<kind>.wav (what the game can play straight away)."""

import shutil
import subprocess
import wave
from pathlib import Path

from . import paths

DIR = paths.DATA / "click-sounds"
KINDS = (
    ("letter", "Letters"),
    ("number", "Numbers"),
    ("space", "Spacebar"),
    ("enter", "Big keys (Enter, Backspace, Shift)"),
    ("other", "Other keys"),
    ("mouse_left", "Left mouse button"),
    ("mouse_right", "Right mouse button"),
    ("mouse_middle", "Middle mouse button"),
    ("mouse_side", "Side mouse buttons"),
)
FILTER = "Sounds (*.wav *.ogg *.mp3 *.flac *.m4a *.opus)"
MAX_SECONDS = 1.5


class SoundError(RuntimeError):
    pass


def current(kind: str):
    """Your own sound for a kind, or None (Peregrine's own is used)."""
    p = DIR / f"{kind}.wav"
    return p if p.is_file() else None


def clear(kind: str) -> None:
    (DIR / f"{kind}.wav").unlink(missing_ok=True)


def _plain_wav(path: Path) -> bool:
    """A WAV the game can play as it is: PCM, 8 or 16 bit."""
    try:
        with wave.open(str(path), "rb") as w:
            return w.getsampwidth() in (1, 2) and w.getnchannels() in (1, 2) and w.getnframes() > 0
    except (wave.Error, OSError, EOFError):
        return False


def set_sound(kind: str, source) -> Path:
    """Uses a sound file for a kind (converted with ffmpeg if it isn't a plain WAV)."""
    source = Path(source)
    DIR.mkdir(parents=True, exist_ok=True)
    out = DIR / f"{kind}.wav"
    tmp = DIR / f"{kind}.new.wav"
    if source.suffix.lower() == ".wav" and _plain_wav(source):
        shutil.copyfile(source, tmp)
    else:
        from .custom_cape import _plain_env
        ff = shutil.which("ffmpeg") or _bundled_ffmpeg()
        if not ff:
            raise SoundError("That file needs converting, which needs ffmpeg. Pick a WAV file instead, "
                             "or set up ffmpeg under Clips.")
        r = subprocess.run([ff, "-v", "error", "-y", "-i", str(source), "-t", str(MAX_SECONDS), "-ac", "2", "-ar", "44100",
                            "-c:a", "pcm_s16le", str(tmp)], capture_output=True, timeout=60, env=_plain_env())
        if r.returncode != 0 or not tmp.is_file():
            why = (r.stderr or b"").decode(errors="replace").strip().splitlines()
            raise SoundError("Couldn't read that sound" + (f" ({why[-1][:160]})." if why else "."))
    tmp.replace(out)
    return out


def _bundled_ffmpeg():
    """The ffmpeg Peregrine downloaded for clips, if any."""
    for name in ("ffmpeg", "ffmpeg.exe"):
        p = paths.DATA / "tools" / "ffmpeg" / name
        if p.is_file():
            return str(p)
    return None
