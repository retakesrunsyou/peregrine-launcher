"""Game window sizes: common screen resolutions, filtered to what a screen can show."""

# (width, height, nickname). Nicknames are what people search for.
PRESETS = [
    (1280, 720, "HD"),
    (1280, 800, ""),
    (1366, 768, ""),
    (1440, 900, ""),
    (1600, 900, "HD+"),
    (1680, 1050, ""),
    (1920, 1080, "Full HD"),
    (1920, 1200, ""),
    (2560, 1080, "Ultrawide"),
    (2560, 1440, "QHD"),
    (2560, 1600, ""),
    (2880, 1800, ""),
    (3440, 1440, "Ultrawide QHD"),
    (3840, 1600, "Ultrawide"),
    (3840, 2160, "4K"),
    (5120, 1440, "Super ultrawide"),
    (5120, 2880, "5K"),
    (7680, 4320, "8K"),
]

MINECRAFT_DEFAULT = (854, 480)


def aspect(w: int, h: int) -> str:
    """'16:9', '16:10', '21:9'... by nearest common ratio."""
    ratios = {"4:3": 4 / 3, "16:10": 16 / 10, "16:9": 16 / 9, "21:9": 64 / 27, "24:10": 2.4, "32:9": 32 / 9}
    r = w / h
    return min(ratios, key=lambda k: abs(ratios[k] - r))


def label(w: int, h: int, nickname: str = "") -> str:
    parts = [f"{w} × {h}", aspect(w, h)]
    if nickname:
        parts.append(nickname)
    return "   ·   ".join(parts)


def presets_for(screen_w: int, screen_h: int) -> list:
    """Every preset that fits on this screen, biggest first. The screen's own size is
    included even when it isn't a common one."""
    fits = [(w, h, n) for w, h, n in PRESETS if w <= screen_w and h <= screen_h]
    if screen_w and screen_h and (screen_w, screen_h) not in [(w, h) for w, h, _ in fits]:
        fits.append((screen_w, screen_h, ""))
    return sorted(fits, key=lambda p: p[0] * p[1], reverse=True)
