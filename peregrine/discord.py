"""Discord Rich Presence: shows "Playing Java 1.21.11 · On AquaMC" on your profile,
with the Peregrine logo, for everyone who looks.

Talks to the Discord app over its local socket (a named pipe on Windows). Nothing is
sent anywhere else, and if Discord isn't running this quietly does nothing.
The pictures are served from the project's GitHub, so they show without any setup.
"""

import json
import os
import socket
import struct
import threading
import time
import uuid
from pathlib import Path

from . import DISCORD_APP_ID, NAME

OP_HANDSHAKE, OP_FRAME, OP_CLOSE = 0, 1, 2

_ASSETS = "https://raw.githubusercontent.com/retakesrunsyou/peregrine-launcher/main/assets/discord/"
LOGO = _ASSETS + "peregrine.png"
GAME = _ASSETS + "minecraft.png"
SITE = "https://github.com/retakesrunsyou/peregrine-launcher"

# Where Discord puts its socket: normal installs, Flatpak, Snap, and Vesktop.
_SUBDIRS = ["", "app/com.discordapp.Discord", "app/com.discordapp.DiscordCanary",
            "snap.discord", "snap.discord-canary", ".flatpak/dev.vencord.Vesktop/xdg-run",
            "app/dev.vencord.Vesktop"]


class _Pipe:
    """Windows: Discord listens on a named pipe; this gives it the socket calls used below."""

    def __init__(self, path):
        self.f = open(path, "r+b", buffering=0)

    def sendall(self, data):
        self.f.write(data)

    def recv(self, n):
        return self.f.read(n)

    def settimeout(self, _):
        pass

    def close(self):
        self.f.close()


def _socket_paths():
    bases = [os.environ.get(v) for v in ("XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP")]
    bases = [b for b in bases if b] + ["/tmp"]
    for base in dict.fromkeys(bases):
        for sub in _SUBDIRS:
            for i in range(10):
                yield Path(base) / sub / f"discord-ipc-{i}"


class Presence:
    def __init__(self, app_id: str = DISCORD_APP_ID):
        self.app_id = app_id
        self.sock = None
        self.lock = threading.Lock()

    # ---- low level

    def _send(self, op: int, payload: dict) -> None:
        data = json.dumps(payload).encode()
        self.sock.sendall(struct.pack("<II", op, len(data)) + data)

    def _recv(self) -> dict:
        header = self._read_exact(8)
        op, length = struct.unpack("<II", header)
        return json.loads(self._read_exact(length) or b"{}")

    def _read_exact(self, n: int) -> bytes:
        buf = b""
        while len(buf) < n:
            chunk = self.sock.recv(n - len(buf))
            if not chunk:
                raise ConnectionError("Discord closed the connection")
            buf += chunk
        return buf

    def _connect(self) -> bool:
        if self.sock:
            return True
        if not self.app_id:
            return False
        windows = os.name == "nt"
        paths = [rf"\\.\pipe\discord-ipc-{i}" for i in range(10)] if windows else _socket_paths()
        for path in paths:
            s = None
            try:
                if windows:
                    s = _Pipe(path)
                else:
                    if not path.exists():
                        continue
                    s = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
                    s.settimeout(3)
                    s.connect(str(path))
                self.sock = s
                self._send(OP_HANDSHAKE, {"v": 1, "client_id": self.app_id})
                reply = self._recv()
                if reply.get("evt") == "READY":
                    return True
            except (OSError, ValueError, ConnectionError):
                pass
            if s is not None:
                try:
                    s.close()
                except OSError:
                    pass
            self.sock = None
        return False

    def _set(self, activity) -> bool:
        with self.lock:
            for _ in range(2):  # reconnect once if Discord restarted
                if not self._connect():
                    return False
                try:
                    self._send(OP_FRAME, {"cmd": "SET_ACTIVITY", "nonce": str(uuid.uuid4()),
                                          "args": {"pid": os.getpid(), "activity": activity}})
                    self._recv()
                    return True
                except (OSError, ValueError, ConnectionError):
                    self.close()
            return False

    # ---- what the launcher calls (all safe to call from any thread)

    def idle(self) -> bool:
        return self._set({
            "details": "In the launcher",
            "assets": {"large_image": LOGO, "large_text": f"{NAME} Launcher"},
            "buttons": [{"label": f"Get {NAME}", "url": SITE}],
        })

    def playing(self, version: str, where: str, instance: str = "", started: float = 0) -> bool:
        """version: "1.21.11"; where: "On AquaMC", "In singleplayer", "In the menus"."""
        return self._set({
            "details": f"Playing Java {version}",
            "state": where or "In the menus",
            "timestamps": {"start": int(started or time.time())},
            "assets": {"large_image": LOGO, "large_text": f"{NAME} Launcher",
                       "small_image": GAME, "small_text": instance or f"Minecraft {version}"},
            "buttons": [{"label": f"Get {NAME}", "url": SITE}],
        })

    def clear(self) -> bool:
        return self._set(None)

    def close(self) -> None:
        if self.sock:
            try:
                self._send(OP_CLOSE, {})
            except OSError:
                pass
            self.sock.close()
            self.sock = None


_presence = Presence()


def update(kind: str, *args) -> None:
    """Fire-and-forget update in the background so the UI never waits on Discord."""
    from . import config
    if not config.load().get("discord", True):
        threading.Thread(target=_presence.clear, daemon=True).start()
        return
    fn = {"idle": _presence.idle, "playing": _presence.playing, "clear": _presence.clear}[kind]
    threading.Thread(target=fn, args=args, daemon=True).start()


def where_from_log(line: str):
    """What Peregrine Client says you're doing ("[Peregrine] Playing: ..."), as Discord text, or None."""
    i = line.find("[Peregrine] Playing: ")
    if i < 0:
        return None
    what = line[i + len("[Peregrine] Playing: "):].strip()
    if what == "menu":
        return "In the menus"
    if what == "single":
        return "In singleplayer"
    if what.startswith("server|"):
        parts = what.split("|", 2)
        address = parts[1] if len(parts) > 1 else ""
        name = parts[2] if len(parts) > 2 and parts[2] else address
        return f"On {name}" if name else "On a server"
    return None
