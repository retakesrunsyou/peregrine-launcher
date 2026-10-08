"""Download a player's skin from Mojang so the launcher can show their face."""

import base64
import json
import time
from pathlib import Path
from typing import Optional

from . import net, paths

SESSION = "https://sessionserver.mojang.com/session/minecraft/profile/"
MAX_AGE = 24 * 3600


def skin_path(uuid: str) -> Optional[Path]:
    """Cached skin PNG for a player, or None if it can't be fetched."""
    path = paths.DATA / "skins" / f"{uuid}.png"
    if path.exists() and time.time() - path.stat().st_mtime < MAX_AGE:
        return path
    try:
        profile = net.get_json(SESSION + uuid)
        textures = next(p["value"] for p in profile["properties"] if p["name"] == "textures")
        url = json.loads(base64.b64decode(textures))["textures"]["SKIN"]["url"]
        net.fetch(net.Download(url.replace("http://", "https://"), path))
        path.touch()
        return path
    except Exception:
        return path if path.exists() else None
