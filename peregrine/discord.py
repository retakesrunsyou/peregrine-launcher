"""Discord Rich Presence: shows "Playing Survival · Fabric 1.21.1" on your profile.

Talks to the Discord app over its local socket. Nothing is sent anywhere else,
and if Discord isn't running this quietly does nothing.
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

# Where Discord puts its socket: normal installs, Flatpak, Snap, and Vesktop.
_SUBDIRS = ["", "app/com.discordapp.Discord", "app/com.discordapp.DiscordCanary",
            "snap.discord", "snap.discord-canary", ".flatpak/dev.vencord.Vesktop/xdg-run",
            "app/dev.vencord.Vesktop"]


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
        for path in _socket_paths():
            if not path.exists():
                continue
            s = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
            s.settimeout(3)
            try:
                s.connect(str(path))
                self.sock = s
                self._send(OP_HANDSHAKE, {"v": 1, "client_id": self.app_id})
                reply = self._recv()
                if reply.get("evt") == "READY":
                    return True
            except (OSError, ValueError, ConnectionError):
                pass
            s.close()
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
            "assets": {"large_image": "peregrine", "large_text": f"{NAME} Launcher"},
        })

    def playing(self, instance_name: str, subtitle: str) -> bool:
        return self._set({
            "details": f"Playing {instance_name}",
            "state": subtitle,
            "timestamps": {"start": int(time.time())},
            "assets": {"large_image": "peregrine", "large_text": f"{NAME} Launcher",
                       "small_image": "minecraft", "small_text": "Minecraft: Java Edition"},
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
