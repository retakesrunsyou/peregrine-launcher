"""The Locker: your skins and capes.

- A skin library on this computer (PNG files you add, or copied from any player by name),
  and putting one on your Minecraft account (Mojang's own skin upload, the same thing
  minecraft.net does). Classic or slim arms.
- Your official Minecraft capes (Migrator, Vanilla...), and which one you wear.
- Your Peregrine cape (what other Peregrine players see), stored with the in-game settings.
"""

import base64
import hashlib
import json
import struct
import time
from pathlib import Path

from . import auth, config, net, paths

PROFILE = "https://api.minecraftservices.com/minecraft/profile"
LIBRARY = paths.DATA / "skins" / "library"
CAPE_NAMES = ("None", "Falcon", "Violet", "Ember", "Starry night", "Pumpkin", "Frost", "Your own")
CAPE_FILES = ("", "falcon", "violet", "ember", "starry", "pumpkin", "frost", "custom")
CUSTOM_CAPE = 7   # your own picture, GIF or video (custom_cape.py); only you see it


class LockerError(RuntimeError):
    pass


# ---------------------------------------------------------------------- the skin library

def _index_file() -> Path:
    return LIBRARY / "skins.json"


def skins() -> list:
    """[{"id", "name", "variant" (classic/slim), "file", "added"}], newest first."""
    try:
        items = json.loads(_index_file().read_text())
    except (OSError, ValueError):
        items = []
    out = [s for s in items if (LIBRARY / s.get("file", "")).is_file()]
    return sorted(out, key=lambda s: -s.get("added", 0))


def _save_index(items):
    LIBRARY.mkdir(parents=True, exist_ok=True)
    _index_file().write_text(json.dumps(items, indent=2))


def png_size(data: bytes):
    if len(data) < 24 or data[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    w, h = struct.unpack(">II", data[16:24])
    return w, h


def check_skin(data: bytes) -> None:
    size = png_size(data)
    if size is None:
        raise LockerError("That isn't a PNG image.")
    if size not in ((64, 64), (64, 32)):
        raise LockerError(f"A Minecraft skin is 64×64 (or the old 64×32); this one is {size[0]}×{size[1]}.")


def guess_variant(data: bytes) -> str:
    """Slim (3-pixel arms) if the 4th arm column is see-through, classic otherwise."""
    try:
        from PySide6.QtGui import QImage
        img = QImage.fromData(data, "PNG")
        if img.width() == 64 and img.height() == 64:
            see_through = sum(1 for y in range(20, 32) if (img.pixel(54, y) >> 24) == 0)
            return "slim" if see_through >= 10 else "classic"
    except Exception:
        pass
    return "classic"


def add_skin(data: bytes, name: str, variant: str = None) -> dict:
    check_skin(data)
    items = [s for s in skins()]
    digest = hashlib.sha1(data).hexdigest()[:16]
    for s in items:
        if s["id"] == digest:  # already there: just rename / move to the top
            s.update(name=name or s["name"], added=time.time())
            _save_index(items)
            return s
    LIBRARY.mkdir(parents=True, exist_ok=True)
    (LIBRARY / f"{digest}.png").write_bytes(data)
    entry = {"id": digest, "name": (name or "Skin").strip()[:32], "variant": variant or guess_variant(data),
             "file": f"{digest}.png", "added": time.time()}
    items.append(entry)
    _save_index(items)
    return entry


def add_skin_file(path) -> dict:
    p = Path(path)
    return add_skin(p.read_bytes(), p.stem.replace("_", " ").replace("-", " ").title())


def add_skin_from_player(name: str) -> dict:
    """Copies any player's current skin into the library, by their Minecraft name."""
    name = name.strip()
    try:
        who = net.get_json(f"https://api.mojang.com/users/profiles/minecraft/{name}")
    except Exception:
        raise LockerError(f"No Minecraft player is called {name}.")
    profile = net.get_json(f"https://sessionserver.mojang.com/session/minecraft/profile/{who['id']}")
    prop = next((p["value"] for p in profile.get("properties", []) if p["name"] == "textures"), None)
    tex = json.loads(base64.b64decode(prop))["textures"] if prop else {}
    skin = tex.get("SKIN")
    if not skin:
        raise LockerError(f"{who['name']} uses a default skin.")
    r = net.session.get(skin["url"].replace("http://", "https://"), timeout=30)
    r.raise_for_status()
    variant = "slim" if (skin.get("metadata") or {}).get("model") == "slim" else "classic"
    return add_skin(r.content, f"{who['name']}'s skin", variant)


def rename(skin_id: str, name: str = None, variant: str = None) -> None:
    items = skins()
    for s in items:
        if s["id"] == skin_id:
            if name is not None:
                s["name"] = name.strip()[:32] or s["name"]
            if variant in ("classic", "slim"):
                s["variant"] = variant
    _save_index(items)


def remove(skin_id: str) -> None:
    items = skins()
    keep = [s for s in items if s["id"] != skin_id]
    for s in items:
        if s["id"] == skin_id:
            try:
                (LIBRARY / s["file"]).unlink()
            except OSError:
                pass
    _save_index(keep)


def skin_bytes(skin: dict) -> bytes:
    return (LIBRARY / skin["file"]).read_bytes()


# ---------------------------------------------------------------------- your Minecraft account

def _account(account: dict) -> dict:
    if not account or account.get("type") != "msa":
        raise LockerError("Sign in with a Microsoft account to change your skin and cape.")
    account = auth.refresh(account, config.load().get("client_id", ""))
    auth.add_account(account)
    return account


def _headers(account):
    return {"Authorization": f"Bearer {account['mc_token']}"}


def _check(r):
    if r.status_code == 401:
        raise LockerError("Your sign-in expired. Sign in again on the Accounts page.")
    if r.status_code == 429:
        raise LockerError("Minecraft says that's too many changes at once. Wait a minute and try again.")
    if r.status_code >= 400:
        try:
            msg = r.json().get("errorMessage") or r.json().get("error")
        except ValueError:
            msg = None
        raise LockerError(msg or f"Minecraft didn't accept that ({r.status_code}).")
    return r


def profile(account: dict) -> dict:
    """Your account's skin and capes, as Minecraft has them right now:
    {"name", "skin": {"url", "variant"}, "capes": [{"id", "alias", "url", "active"}]}."""
    account = _account(account)
    p = _check(net.session.get(PROFILE, headers=_headers(account), timeout=30)).json()
    return _simplify(p)


def _simplify(p: dict) -> dict:
    skin = next((s for s in p.get("skins", []) if s.get("state") == "ACTIVE"), None)
    return {
        "name": p.get("name", ""),
        "skin": {"url": skin["url"].replace("http://", "https://"), "variant": skin.get("variant", "CLASSIC").lower()}
        if skin else None,
        "capes": [{"id": c["id"], "alias": c.get("alias", "Cape"), "url": c["url"].replace("http://", "https://"),
                   "active": c.get("state") == "ACTIVE"} for c in p.get("capes", [])],
    }


def wear_skin(account: dict, skin: dict) -> dict:
    """Puts a library skin on your Minecraft account. Everyone sees it after rejoining."""
    account = _account(account)
    data = skin_bytes(skin)
    check_skin(data)
    r = net.session.post(f"{PROFILE}/skins", headers=_headers(account), timeout=60,
                         data={"variant": skin.get("variant", "classic")},
                         files={"file": ("skin.png", data, "image/png")})
    _check(r)
    try:  # your player on the game's main menu wears it straight away
        own = paths.DATA / "skins" / f"{account['uuid']}.png"
        own.parent.mkdir(parents=True, exist_ok=True)
        own.write_bytes(data)
    except OSError:
        pass
    try:
        return _simplify(r.json())
    except ValueError:
        return profile(account)


def wear_cape(account: dict, cape_id: str = None) -> None:
    """Shows one of your official capes, or none (cape_id None)."""
    account = _account(account)
    if cape_id:
        _check(net.session.put(f"{PROFILE}/capes/active", headers=_headers(account), json={"capeId": cape_id},
                               timeout=30))
    else:
        _check(net.session.delete(f"{PROFILE}/capes/active", headers=_headers(account), timeout=30))


def current_skin_bytes(account: dict) -> bytes:
    """The skin on your account right now (to keep it in the library)."""
    p = profile(account)
    if not p["skin"]:
        raise LockerError("Your account uses a default skin.")
    r = net.session.get(p["skin"]["url"], timeout=30)
    r.raise_for_status()
    return r.content


def fetch_image(url: str) -> bytes:
    """A skin or cape picture from Mojang, cached on disk."""
    cache = paths.CACHE / "textures" / (hashlib.sha1(url.encode()).hexdigest() + ".png")
    if cache.is_file():
        return cache.read_bytes()
    r = net.session.get(url, timeout=30)
    r.raise_for_status()
    cache.parent.mkdir(parents=True, exist_ok=True)
    cache.write_bytes(r.content)
    return r.content


# ---------------------------------------------------------------------- the Peregrine cape

def peregrine_cape() -> int:
    """Index into CAPE_FILES of the Peregrine cape you wear (0 = none)."""
    try:
        data = json.loads(paths.CLIENT_SETTINGS.read_text())
        m = (data.get("modules") or {}).get("cosmetics") or {}
        if m.get("enabled") is False:
            return 0
        return max(0, min(len(CAPE_FILES) - 1, int((m.get("settings") or {}).get("cape", 1))))
    except (OSError, ValueError, TypeError):
        return 1


ANIMATED = {"starry": 24}


def cape_frames(index: int):
    """The frames of a moving cape (in order), or None for a still one."""
    import sys
    if index == CUSTOM_CAPE:
        from . import custom_cape
        return custom_cape.preview()[1]
    name = CAPE_FILES[index] if 0 <= index < len(CAPE_FILES) else ""
    n = ANIMATED.get(name)
    if not n:
        return None
    base = Path(getattr(sys, "_MEIPASS", Path(__file__).resolve().parent.parent))
    frames = [base / "assets" / "capes" / name / f"{name}_{i}.png" for i in range(n)]
    return frames if all(f.is_file() for f in frames) else None


def _client_settings() -> dict:
    try:
        return json.loads(paths.CLIENT_SETTINGS.read_text())
    except (OSError, ValueError):
        return {}


def _set_module_setting(module: str, key: str, value) -> None:
    data = _client_settings()
    m = data.setdefault("modules", {}).setdefault(module, {})
    m.setdefault("settings", {})[key] = value
    paths.CLIENT_SETTINGS.parent.mkdir(parents=True, exist_ok=True)
    paths.CLIENT_SETTINGS.write_text(json.dumps(data, indent=2))


def set_peregrine_cape(index: int) -> None:
    """Picks your Peregrine cape (also what the game shows next time it starts)."""
    try:
        data = json.loads(paths.CLIENT_SETTINGS.read_text())
    except (OSError, ValueError):
        data = {}
    mods = data.setdefault("modules", {})
    m = mods.setdefault("cosmetics", {})
    m["enabled"] = True
    m.setdefault("settings", {})["cape"] = int(index)
    paths.CLIENT_SETTINGS.parent.mkdir(parents=True, exist_ok=True)
    paths.CLIENT_SETTINGS.write_text(json.dumps(data, indent=2))


def cape_frame_ms(index: int) -> int:
    """How long each preview frame of a moving cape shows."""
    if index == CUSTOM_CAPE:
        from . import custom_cape
        return custom_cape.preview()[2]
    return 125


def cape_asset(index: int):
    """The bundled picture of a Peregrine cape (or your own's), or None."""
    import sys
    if index == CUSTOM_CAPE:
        from . import custom_cape
        still, frames, _ = custom_cape.preview()
        return still or (frames[0] if frames else None)
    name = CAPE_FILES[index] if 0 <= index < len(CAPE_FILES) else ""
    if not name:
        return None
    base = Path(getattr(sys, "_MEIPASS", Path(__file__).resolve().parent.parent))
    p = base / "assets" / "capes" / f"{name}.png"
    return p if p.is_file() else None



# ---------------------------------------------------------------------- the main menu's background

BACKGROUNDS = ("Minecraft panorama", "Dusk", "Aurora", "Night sky", "Your picture")
BACKGROUND_FILES = (None, "dusk", "aurora", "night", None)
CUSTOM = 4


def custom_background() -> Path:
    return paths.DATA / "client" / "backgrounds" / "custom.png"


def menu_background() -> int:
    m = (_client_settings().get("modules") or {}).get("main_menu") or {}
    try:
        return max(0, min(len(BACKGROUNDS) - 1, int((m.get("settings") or {}).get("background", 0))))
    except (TypeError, ValueError):
        return 0


def set_menu_background(index: int) -> None:
    _set_module_setting("main_menu", "background", int(index))


def set_custom_background(path) -> Path:
    """Uses a picture of yours behind the game's main menu (any common image type)."""
    from PySide6.QtCore import Qt
    from PySide6.QtGui import QImage
    img = QImage(str(path))
    if img.isNull():
        raise LockerError("That picture couldn't be opened. Try a PNG or JPG.")
    if img.width() > 2560 or img.height() > 1440:
        img = img.scaled(2560, 1440, Qt.KeepAspectRatio, Qt.SmoothTransformation)
    out = custom_background()
    out.parent.mkdir(parents=True, exist_ok=True)
    if not img.convertToFormat(QImage.Format_ARGB32).save(str(out), "PNG"):
        raise LockerError("Couldn't save the picture.")
    set_menu_background(CUSTOM)
    return out


def background_asset(index: int):
    import sys
    if index == CUSTOM:
        p = custom_background()
        return p if p.is_file() else None
    name = BACKGROUND_FILES[index] if 0 <= index < len(BACKGROUND_FILES) else None
    if not name:
        return None
    base = Path(getattr(sys, "_MEIPASS", Path(__file__).resolve().parent.parent))
    p = base / "assets" / "backgrounds" / f"{name}.png"
    return p if p.is_file() else None
