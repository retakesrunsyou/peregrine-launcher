"""Servers: what you played recently, and how they're doing right now.

Recent servers come from Minecraft's own quick play log (every instance is started
with one) and the names and icons from its server list (servers.dat). Live player
counts come from the same status ping the multiplayer screen uses.
"""

import base64
import gzip
import json
import random
import socket
import struct
import time
from datetime import datetime
from pathlib import Path

QUICK_PLAY_LOG = "quickPlay/log.json"   # inside an instance's game folder

# ------------------------------------------------------------- addresses


def split_address(address: str):
    """'host[:port]' -> (host, port or None)."""
    address = address.strip()
    if address.startswith("["):  # [ipv6]:port
        host, _, rest = address[1:].partition("]")
        return host, int(rest[1:]) if rest.startswith(":") and rest[1:].isdigit() else None
    if address.count(":") == 1:
        host, port = address.split(":")
        if port.isdigit():
            return host, int(port)
    return address, None


def _nameservers() -> list:
    out = []
    try:
        for line in Path("/etc/resolv.conf").read_text().splitlines():
            parts = line.split()
            if len(parts) >= 2 and parts[0] == "nameserver":
                out.append(parts[1])
    except OSError:
        pass
    return out or ["1.1.1.1"]


def _dns_name(data: bytes, i: int):
    """Read a (possibly compressed) DNS name at i -> (name, next index)."""
    labels, jumped, end = [], False, i
    for _ in range(64):
        n = data[i]
        if n == 0:
            i += 1
            break
        if n & 0xC0 == 0xC0:
            if not jumped:
                end = i + 2
            i = ((n & 0x3F) << 8) | data[i + 1]
            jumped = True
            continue
        labels.append(data[i + 1:i + 1 + n].decode("ascii", "replace"))
        i += 1 + n
    return ".".join(labels), (end if jumped else i)


def srv_lookup(host: str, timeout: float = 2.0):
    """The _minecraft._tcp SRV record for host -> (target, port), or None."""
    qid = random.randrange(65536)
    query = struct.pack(">HHHHHH", qid, 0x0100, 1, 0, 0, 0)
    for label in f"_minecraft._tcp.{host}".split("."):
        query += bytes([len(label)]) + label.encode("ascii", "ignore")
    query += b"\0" + struct.pack(">HH", 33, 1)  # type SRV, class IN
    for ns in _nameservers()[:2]:
        try:
            with socket.socket(socket.AF_INET6 if ":" in ns else socket.AF_INET, socket.SOCK_DGRAM) as s:
                s.settimeout(timeout)
                s.sendto(query, (ns, 53))
                data = s.recv(4096)
        except OSError:
            continue
        if len(data) < 12 or struct.unpack(">H", data[:2])[0] != qid:
            continue
        qd, an = struct.unpack(">HH", data[4:8])
        i = 12
        for _ in range(qd):
            _, i = _dns_name(data, i)
            i += 4
        best = None
        for _ in range(an):
            _, i = _dns_name(data, i)
            rtype, _, _, rdlen = struct.unpack(">HHIH", data[i:i + 10])
            i += 10
            if rtype == 33:
                prio, weight, port = struct.unpack(">HHH", data[i:i + 6])
                target, _ = _dns_name(data, i + 6)
                if best is None or prio < best[0]:
                    best = (prio, target, port)
            i += rdlen
        return (best[1], best[2]) if best else None
    return None


def resolve(address: str):
    """Where to connect for a server address, following SRV records like the game."""
    host, port = split_address(address)
    if port is not None:
        return host, port
    try:
        srv = srv_lookup(host)
    except (OSError, IndexError, struct.error, UnicodeError):
        srv = None
    return srv if srv else (host, 25565)


# --------------------------------------------------------------- status

def _varint(n: int) -> bytes:
    n &= 0xFFFFFFFF
    out = b""
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out += bytes([b | 0x80])
        else:
            return out + bytes([b])


def _read_varint(sock) -> int:
    v = 0
    for shift in range(0, 35, 7):
        b = sock.recv(1)
        if not b:
            raise ConnectionError("server closed the connection")
        v |= (b[0] & 0x7F) << shift
        if not b[0] & 0x80:
            return v
    raise ValueError("bad varint")


def _recv_exact(sock, n: int) -> bytes:
    buf = b""
    while len(buf) < n:
        chunk = sock.recv(n - len(buf))
        if not chunk:
            raise ConnectionError("server closed the connection")
        buf += chunk
    return buf


def text_of(component) -> str:
    """A chat component (the MOTD) as plain text, without § formatting codes."""
    def walk(c):
        if isinstance(c, str):
            return c
        if isinstance(c, list):
            return "".join(walk(x) for x in c)
        if isinstance(c, dict):
            return walk(c.get("text", "")) + walk(c.get("translate", "") if "text" not in c else "") \
                + "".join(walk(x) for x in c.get("extra", []))
        return ""
    raw = walk(component)
    out, skip = [], False
    for ch in raw:
        if skip:
            skip = False
        elif ch == "§":
            skip = True
        else:
            out.append(ch)
    return "".join(out).strip()


def status(address: str, timeout: float = 4.0) -> dict:
    """Ask a server how it's doing. Returns online, max, motd, version, favicon (PNG
    bytes or None) and latency (ms). Raises OSError/ValueError when it can't be reached."""
    host, port = resolve(address)
    start = time.monotonic()
    with socket.create_connection((host, port), timeout=timeout) as s:
        s.settimeout(timeout)
        hs = b"\x00" + _varint(-1) + _varint(len(host.encode())) + host.encode() + struct.pack(">H", port) + _varint(1)
        s.sendall(_varint(len(hs)) + hs + b"\x01\x00")
        _read_varint(s)
        if _read_varint(s) != 0:
            raise ValueError("unexpected reply")
        data = _recv_exact(s, _read_varint(s))
        latency = int((time.monotonic() - start) * 1000)
    info = json.loads(data.decode("utf-8", "replace"))
    players = info.get("players") or {}
    favicon = None
    fav = info.get("favicon") or ""
    if fav.startswith("data:image/png;base64,"):
        try:
            favicon = base64.b64decode(fav.split(",", 1)[1].replace("\n", ""))
        except ValueError:
            favicon = None
    return {
        "online": int(players.get("online", 0)),
        "max": int(players.get("max", 0)),
        "motd": text_of(info.get("description", "")),
        "version": (info.get("version") or {}).get("name", ""),
        "favicon": favicon,
        "latency": latency,
    }


# ---------------------------------------------------------- server list

def _nbt(data: bytes):
    """Just enough NBT to read servers.dat (uncompressed or gzipped)."""
    if data[:2] == b"\x1f\x8b":
        data = gzip.decompress(data)
    pos = 0

    def take(n):
        nonlocal pos
        out = data[pos:pos + n]
        if len(out) < n:
            raise ValueError("short NBT")
        pos += n
        return out

    def string():
        return take(struct.unpack(">H", take(2))[0]).decode("utf-8", "replace")

    def payload(t):
        if t == 1:
            return take(1)[0]
        if t == 2:
            return struct.unpack(">h", take(2))[0]
        if t == 3:
            return struct.unpack(">i", take(4))[0]
        if t == 4:
            return struct.unpack(">q", take(8))[0]
        if t == 5:
            return struct.unpack(">f", take(4))[0]
        if t == 6:
            return struct.unpack(">d", take(8))[0]
        if t == 7:
            return take(struct.unpack(">i", take(4))[0])
        if t == 8:
            return string()
        if t == 9:
            et, n = take(1)[0], struct.unpack(">i", take(4))[0]
            return [payload(et) for _ in range(n)]
        if t == 10:
            out = {}
            while True:
                tt = take(1)[0]
                if tt == 0:
                    return out
                name = string()
                out[name] = payload(tt)
        if t == 11:
            n = struct.unpack(">i", take(4))[0]
            return list(struct.unpack(f">{n}i", take(4 * n)))
        if t == 12:
            n = struct.unpack(">i", take(4))[0]
            return list(struct.unpack(f">{n}q", take(8 * n)))
        raise ValueError(f"unknown NBT tag {t}")

    if take(1)[0] != 10:
        raise ValueError("not NBT")
    string()
    return payload(10)


def server_list(game_dir: Path) -> list:
    """The servers in an instance's multiplayer list: [{name, ip, icon (PNG bytes)}]."""
    try:
        root = _nbt((Path(game_dir) / "servers.dat").read_bytes())
    except (OSError, ValueError, IndexError, struct.error):
        return []
    out = []
    for s in root.get("servers", []):
        if not isinstance(s, dict) or not s.get("ip"):
            continue
        icon = None
        if s.get("icon"):
            try:
                icon = base64.b64decode(s["icon"])
            except ValueError:
                icon = None
        out.append({"name": s.get("name", ""), "ip": s["ip"], "icon": icon})
    return out


# --------------------------------------------------------------- recent

def _when(value) -> float:
    if isinstance(value, (int, float)):
        return float(value) / (1000 if value > 1e11 else 1)
    if isinstance(value, str):
        try:
            return datetime.fromisoformat(value.replace("Z", "+00:00")).timestamp()
        except ValueError:
            return 0.0
    return 0.0


def _log_entries(data):
    """Multiplayer entries in a quick play log, however its version lays them out."""
    if isinstance(data, dict):
        data = data.get("entries") or data.get("worlds") or [data]
    for e in data if isinstance(data, list) else []:
        if not isinstance(e, dict):
            continue
        world = e.get("quickPlayWorld") or e.get("world") or e
        if not isinstance(world, dict):
            continue
        kind = str(world.get("type", "")).lower()
        address = world.get("id") or world.get("address")
        if kind == "multiplayer" and address:
            yield {"address": address, "name": world.get("name") or "",
                   "last_played": _when(e.get("lastPlayedTime") or e.get("last_played"))}


def _history_file() -> Path:
    from . import paths
    return paths.DATA / "recent-servers.json"


def recent_servers(instances) -> list:
    """Servers played recently, newest first, one per address:
    [{address, name, icon, last_played, inst}].

    Minecraft's log only keeps the last thing you played, so what it says is
    added to Peregrine's own history each time."""
    by_folder = {str(Path(inst.folder)): inst for inst in instances}
    try:
        history = json.loads(_history_file().read_text())
    except (OSError, ValueError):
        history = []
    best = {}
    for h in history if isinstance(history, list) else []:
        inst = by_folder.get(h.get("folder", ""))
        if inst is not None and h.get("address"):
            listed = {s["ip"].lower(): s for s in server_list(Path(inst.game_dir))}.get(h["address"].lower())
            best[h["address"].lower()] = {
                "address": h["address"], "name": (listed or {}).get("name") or h.get("name", ""),
                "icon": (listed or {}).get("icon"), "last_played": float(h.get("last_played", 0)), "inst": inst}
    for inst in instances:
        game_dir = Path(inst.game_dir)
        names = {s["ip"].lower(): s for s in server_list(game_dir)}
        try:
            data = json.loads((game_dir / QUICK_PLAY_LOG).read_text())
        except (OSError, ValueError):
            data = []
        when_file = 0.0
        try:
            when_file = (game_dir / QUICK_PLAY_LOG).stat().st_mtime
        except OSError:
            pass
        for e in _log_entries(data):
            e["last_played"] = e["last_played"] or when_file
            listed = names.get(e["address"].lower())
            e["icon"] = listed["icon"] if listed else None
            if listed and listed["name"]:
                e["name"] = listed["name"]
            e["inst"] = inst
            key = e["address"].lower()
            if key not in best or e["last_played"] > best[key]["last_played"]:
                best[key] = e
    out = sorted(best.values(), key=lambda e: e["last_played"], reverse=True)[:30]
    try:
        f = _history_file()
        f.parent.mkdir(parents=True, exist_ok=True)
        tmp = f.with_suffix(".tmp")
        tmp.write_text(json.dumps([{"address": e["address"], "name": e["name"], "last_played": e["last_played"],
                                    "folder": str(Path(e["inst"].folder))} for e in out], indent=1))
        tmp.replace(f)
    except OSError:
        pass
    return out


def display_name(entry: dict) -> str:
    """What to call a server: its list name, or a tidy version of its address."""
    name = (entry.get("name") or "").strip()
    if name and name != "Minecraft Server":
        return name
    host, _ = split_address(entry["address"])
    parts = [p for p in host.split(".") if p not in ("play", "mc", "www", "join", "hub")]
    core = parts[-2] if len(parts) >= 2 else (parts[0] if parts else host)
    return core.replace("-", " ").title() if not core.replace(".", "").isdigit() else host
