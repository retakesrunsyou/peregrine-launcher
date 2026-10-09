"""Colors, the app stylesheet, and small line icons drawn as SVG."""

from PySide6.QtCore import QByteArray, QRectF, Qt
from PySide6.QtGui import QColor, QIcon, QImage, QPainter, QPainterPath, QPixmap
from PySide6.QtSvg import QSvgRenderer

THEMES = {
    # Dusk: slate blue-grey, like the sky a falcon hunts in.
    "dusk": dict(bg="#171a21", surface="#1f232c", raised="#272c37", border="#323846",
                 text="#edeef2", muted="#959bab", faint="#646b7b", danger="#e5676b"),
    "midnight": dict(bg="#0e1015", surface="#15181f", raised="#1c2029", border="#262b36",
                     text="#e8e9ee", muted="#8b91a0", faint="#5a6070", danger="#e5676b"),
    "light": dict(bg="#eef0f4", surface="#ffffff", raised="#f4f5f8", border="#d8dce4",
                  text="#1a1d24", muted="#5d6474", faint="#9298a6", danger="#c9454a"),
}
THEME_NAMES = {"dusk": "Dusk", "midnight": "Midnight", "light": "Light"}

ACCENTS = {
    "Amber": "#e8a33d", "Sky": "#5aa9e6", "Moss": "#7bc47f",
    "Heather": "#c38be0", "Coral": "#e07a7a", "Pink": "#ec4f9b",
}


class Theme:
    """The current palette. Widgets read colors from here."""
    name = "dusk"
    accent = "#e8a33d"
    p = THEMES["dusk"]

    @classmethod
    def set(cls, name: str, accent: str) -> None:
        cls.name = name if name in THEMES else "dusk"
        cls.accent = accent
        cls.p = THEMES[cls.name]


def rgba(hex_color: str, alpha: float) -> str:
    c = hex_color.lstrip("#")
    r, g, b = int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16)
    return f"rgba({r}, {g}, {b}, {round(alpha * 255)})"


def on_color(hex_color: str) -> str:
    """Readable text color (dark or white) to put on top of a fill."""
    c = QColor(hex_color)
    lum = 0.2126 * c.redF() ** 2.2 + 0.7152 * c.greenF() ** 2.2 + 0.0722 * c.blueF() ** 2.2
    return "#15171c" if lum > 0.28 else "#ffffff"


def tint(hex_color: str, alpha: float) -> str:
    c = QColor(hex_color)
    return f"rgba({c.red()},{c.green()},{c.blue()},{int(alpha * 255)})"


def shade(hex_color: str, factor: int) -> str:
    """Lighter (factor > 100) or darker (< 100) version of a color."""
    c = QColor(hex_color)
    return (c.lighter(factor) if factor >= 100 else c.darker(int(10000 / factor))).name()


def stylesheet() -> str:
    p, a = Theme.p, Theme.accent
    on_a = on_color(a)
    hover_a = shade(a, 112)
    # Frosted panels (header, sidebar, footer): the night sky shows through a little.
    glass = rgba(p["surface"], 0.40 if Theme.name != "light" else 0.62)
    return f"""
    QWidget {{ color: {p['text']}; font-size: 14px; }}
    QMainWindow, QDialog {{ background: {p['bg']}; }}
    #page {{ background: transparent; }}
    QScrollArea, #scrollBody {{ background: transparent; border: none; }}

    #header {{ background: {glass}; border-bottom: 1px solid {p['border']}; }}
    #nav {{ background: {glass}; border-right: 1px solid {p['border']}; }}
    #appName {{ font-size: 18px; font-weight: 700; }}
    #h1 {{ font-size: 28px; font-weight: 700; }}
    #h2 {{ font-size: 16px; font-weight: 650; }}
    #muted {{ color: {p['muted']}; }}
    #faint {{ color: {p['faint']}; font-size: 12px; }}

    #navButton {{ background: transparent; border: none; border-radius: 6px;
                  padding: 10px 12px; text-align: left; color: {p['muted']}; font-weight: 600; }}
    #navButton:hover {{ background: {p['raised']}; color: {p['text']}; }}
    #navButton:checked {{ background: {tint(a, 0.14)}; color: {a}; }}

    #accountChip {{ background: transparent; border: none; border-radius: 6px; padding: 4px 8px; }}
    #accountChip:hover {{ background: {p['raised']}; }}

    #card {{ background: {p['surface']}; border: 1px solid {p['border']}; border-radius: 10px; }}
    #card:hover {{ border-color: {p['faint']}; }}
    #row {{ background: {p['surface']}; border: 1px solid {p['border']}; border-radius: 8px; }}
    #row:hover {{ border-color: {p['faint']}; }}
    #emptyIcon {{ background: {tint(a, 0.12)}; border-radius: 16px; }}

    QPushButton {{ background: {p['raised']}; border: 1px solid {p['border']}; border-radius: 6px;
                   padding: 8px 16px; color: {p['text']}; }}
    QPushButton:hover {{ border-color: {p['faint']}; }}
    QPushButton:disabled {{ color: {p['faint']}; }}
    QPushButton:focus {{ border-color: {a}; }}
    QPushButton#accent {{ background: {a}; color: {on_a}; border: none; font-weight: 650; }}
    QPushButton#accent:hover {{ background: {hover_a}; }}
    QPushButton#accent:disabled {{ background: {tint(a, 0.35)}; color: {tint(on_a, 0.6)}; }}
    QPushButton#outline {{ background: transparent; color: {a}; border: 1px solid {p['border']}; }}
    QPushButton#outline:hover {{ border-color: {a}; }}
    QPushButton#icon {{ background: transparent; border: none; padding: 6px; border-radius: 5px; }}
    QPushButton#icon:hover {{ background: {p['raised']}; }}
    QPushButton#swatch {{ border-radius: 6px; padding: 0; min-width: 28px; max-width: 28px;
                          min-height: 28px; max-height: 28px; }}
    QPushButton#tab {{ background: transparent; border: none; border-bottom: 2px solid transparent;
                       border-radius: 0; padding: 8px 4px; color: {p['muted']}; font-weight: 600; }}
    QPushButton#tab:checked {{ color: {p['text']}; border-bottom-color: {a}; }}

    QLineEdit, QComboBox, QSpinBox, QPlainTextEdit {{
        background: {p['bg']}; border: 1px solid {p['border']}; border-radius: 6px; padding: 7px 9px; }}
    QLineEdit:focus, QComboBox:focus, QSpinBox:focus {{ border-color: {a}; }}
    QLineEdit:hover, QComboBox:hover, QSpinBox:hover {{ border-color: {p['faint']}; }}
    QComboBox::drop-down {{ border: none; width: 26px; }}
    QComboBox QAbstractItemView {{ outline: none; padding: 4px; }}
    QComboBox QAbstractItemView::item {{ min-height: 28px; padding: 0 10px; border-radius: 5px; }}
    QComboBox QAbstractItemView::item:hover {{ background: {p['raised']}; }}
    QComboBox QAbstractItemView {{ background: {p['surface']}; border: 1px solid {p['border']};
                                   selection-background-color: {tint(a, 0.2)}; }}
    #segmented {{ background: {p['bg']}; border: 1px solid {p['border']}; border-radius: 7px; }}
    QPushButton#segment {{ background: transparent; border: none; border-radius: 5px;
                           padding: 6px 14px; color: {p['muted']}; font-weight: 600; }}
    QPushButton#segment:hover {{ color: {p['text']}; }}
    QPushButton#segment:checked {{ background: {a}; color: {on_a}; }}
    QPushButton#segment:disabled {{ color: {p['faint']}; }}
    #badge {{ background: {tint(a, 0.16)}; color: {a}; border-radius: 4px; padding: 2px 7px;
              font-size: 12px; font-weight: 600; }}
    #badgeMuted {{ background: {p['raised']}; color: {p['muted']}; border-radius: 4px; padding: 2px 7px;
                   font-size: 12px; font-weight: 600; }}
    QCheckBox {{ spacing: 10px; }}
    QCheckBox::indicator {{ width: 18px; height: 18px; border-radius: 5px;
                            border: 1px solid {p['faint']}; background: {p['bg']}; }}
    QCheckBox::indicator:checked {{ background: {a}; border-color: {a}; }}
    QSlider::groove:horizontal {{ height: 4px; background: {p['border']}; border-radius: 2px; }}
    QSlider::sub-page:horizontal {{ background: {a}; border-radius: 2px; }}
    QSlider::handle:horizontal {{ background: {p['text']}; width: 16px; height: 16px;
                                  margin: -6px 0; border-radius: 8px; }}
    QProgressBar {{ background: {p['raised']}; border: none; border-radius: 2px; }}
    QProgressBar::chunk {{ background: {a}; border-radius: 2px; }}
    #console {{ background: {p['bg']}; border: none; border-top: 1px solid {p['border']};
                border-radius: 0; font-family: monospace; font-size: 12px; color: {p['muted']}; }}
    #footer {{ background: {glass}; border-top: 1px solid {p['border']}; }}
    QScrollBar:vertical {{ background: transparent; width: 10px; }}
    QScrollBar::handle:vertical {{ background: {p['border']}; border-radius: 4px; min-height: 30px; }}
    QScrollBar::add-line, QScrollBar::sub-line {{ height: 0; }}
    QToolTip {{ background: {p['raised']}; color: {p['text']}; border: 1px solid {p['border']}; }}
    """


# ------------------------------------------------------------------ icons

_PATHS = {
    "home": '<path d="M4 11 12 4l8 7v8.5a.5.5 0 0 1-.5.5H15v-6H9v6H4.5a.5.5 0 0 1-.5-.5z"/>',
    "settings": '<circle cx="12" cy="12" r="3"/><circle cx="12" cy="12" r="7.5"/>'
                '<path d="M12 2v2.5M12 19.5V22M2 12h2.5M19.5 12H22M4.9 4.9l1.8 1.8M17.3 17.3l1.8 1.8'
                'M4.9 19.1l1.8-1.8M17.3 6.7l1.8-1.8"/>',
    "account": '<circle cx="12" cy="8" r="4"/><path d="M4 21c.5-4 3.8-6 8-6s7.5 2 8 6"/>',
    "trash": '<path d="M4 7h16M9 7V4.5h6V7M6.5 7l1 13h9l1-13M10 11v5.5M14 11v5.5"/>',
    "folder": '<path d="M3.5 6.5a1 1 0 0 1 1-1H9l2 2h8.5a1 1 0 0 1 1 1V18a1 1 0 0 1-1 1h-15a1 1 0 0 1-1-1z"/>',
    "plus": '<path d="M12 5v14M5 12h14"/>',
    "back": '<path d="M14.5 5.5 8 12l6.5 6.5"/>',
    "console": '<path d="M5 7l5 5-5 5M12.5 17H19"/>',
    "bolt": '<path d="M13 3 5 13.5h6L10 21l8-10.5h-6z"/>',
    "check": '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
    "search": '<circle cx="11" cy="11" r="6.5"/><path d="M16 16l4 4"/>',
    "package": '<path d="M12 3 4 7v10l8 4 8-4V7z"/><path d="M4 7l8 4 8-4M12 11v10"/>',
    "play": '<path fill="FILL" d="M8 5.8v12.4a.6.6 0 0 0 .9.5l9.8-6.2a.6.6 0 0 0 0-1L8.9 5.3a.6.6 0 0 0-.9.5z"/>',
    "clock": '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>',
    "cube": '<path d="M12 3 4 7v10l8 4 8-4V7z"/><path d="M4 7l8 4 8-4M12 11v10"/>',
    "image": '<rect x="3.5" y="5" width="17" height="14" rx="2"/><circle cx="9" cy="10" r="1.6"/>'
             '<path d="M5 17l4.5-4.5 3 3 2.5-2.5L19.5 17"/>',
    "sun": '<circle cx="12" cy="12" r="4"/><path d="M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2'
           'M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4"/>',
    "gauge": '<path d="M4.5 17a8.5 8.5 0 1 1 15 0"/><path d="M12 13l4-4"/><circle cx="12" cy="13" r="1"/>',
}

LOGO_SVG = ('<path d="M64 104 L54 62 L18 40 L56 48 L64 22 L72 48 L110 40 L74 62 Z" fill="{c}"/>')


def _render(svg: str, size: int, dpr: float) -> QPixmap:
    renderer = QSvgRenderer(QByteArray(svg.encode()))
    px = int(size * dpr)
    img = QImage(px, px, QImage.Format_ARGB32_Premultiplied)
    img.fill(Qt.transparent)
    painter = QPainter(img)
    painter.setRenderHint(QPainter.Antialiasing)
    renderer.render(painter)
    painter.end()
    pm = QPixmap.fromImage(img)
    pm.setDevicePixelRatio(dpr)
    return pm


def icon(name: str, color: str = None, size: int = 20, dpr: float = 2.0) -> QIcon:
    return QIcon(icon_pixmap(name, color, size, dpr))


def icon_pixmap(name: str, color: str = None, size: int = 20, dpr: float = 2.0) -> QPixmap:
    color = color or Theme.p["muted"]
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" '
           f'stroke="{color}" stroke-width="1.8" stroke-linecap="round" '
           f'stroke-linejoin="round">{_PATHS[name].replace("FILL", color)}</svg>')
    return _render(svg, size, dpr)


def logo_pixmap(size: int = 32, dpr: float = 2.0) -> QPixmap:
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="14 18 100 90">'
           f'{LOGO_SVG.format(c=Theme.accent)}</svg>')
    return _render(svg, size, dpr)


def avatar_pixmap(skin_path, name: str, size: int = 32, dpr: float = 2.0) -> QPixmap:
    """A player's face cropped from their skin, or a letter tile if there's no skin."""
    px = int(size * dpr)
    out = QPixmap(px, px)
    out.fill(Qt.transparent)
    painter = QPainter(out)
    clip = QPainterPath()
    clip.addRoundedRect(QRectF(0, 0, px, px), px * 0.2, px * 0.2)
    painter.setClipPath(clip)
    skin = QImage(str(skin_path)) if skin_path else QImage()
    if not skin.isNull() and skin.width() >= 64:
        scale = skin.width() // 64
        face = skin.copy(8 * scale, 8 * scale, 8 * scale, 8 * scale)
        hat = skin.copy(40 * scale, 8 * scale, 8 * scale, 8 * scale)
        painter.drawImage(QRectF(0, 0, px, px), face)  # nearest-neighbour keeps it pixelly
        painter.drawImage(QRectF(0, 0, px, px), hat)
    else:
        painter.fillRect(0, 0, px, px, QColor(Theme.p["raised"]))
        painter.setPen(QColor(Theme.accent))
        f = painter.font()
        f.setPixelSize(int(px * 0.5))
        f.setBold(True)
        painter.setFont(f)
        painter.drawText(QRectF(0, 0, px, px), Qt.AlignCenter, (name[:1] or "?").upper())
    painter.end()
    out.setDevicePixelRatio(dpr)
    return out


APP_ICON_SVG = (
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 128 128">'
    '<rect width="128" height="128" rx="28" fill="#171a21"/>'
    '<path d="M64 104 L54 62 L18 40 L56 48 L64 22 L72 48 L110 40 L74 62 Z" fill="#e8a33d"/>'
    '<path d="M64 22 L68 36 L60 36 Z" fill="#f6d9a6"/></svg>')


def render_app_icon(size: int) -> QPixmap:
    return _render(APP_ICON_SVG, size, 1.0)
