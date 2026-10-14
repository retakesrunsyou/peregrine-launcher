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
    "Heather": "#c38be0", "Coral": "#e07a7a", "Pink": "#ec4f9b", "Violet": "#7c5cff",
}
DEFAULT_ACCENT = "#7c5cff"


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


def round_menu(menu):
    """A menu with rounded corners (the stylesheet draws it; the window itself goes see-through)."""
    menu.setWindowFlags(menu.windowFlags() | Qt.FramelessWindowHint | Qt.NoDropShadowWindowHint)
    menu.setAttribute(Qt.WA_TranslucentBackground)
    return menu


def stylesheet() -> str:
    p, a = Theme.p, Theme.accent
    on_a = on_color(a)
    hover_a = shade(a, 112)
    # Frosted panels (header, sidebar, footer): the night sky shows through a little.
    glass = rgba(p["surface"], 0.12 if Theme.name != "light" else 0.40)
    dark = Theme.name != "light"
    # Cards are lightly see-through too, so the sky glows faintly behind them.
    card_top = rgba(shade(p["surface"], 108), 0.86 if dark else 0.94)
    card_bot = rgba(p["surface"], 0.80 if dark else 0.92)
    edge_hi = "rgba(255, 255, 255, 0.07)" if dark else "rgba(255, 255, 255, 0.9)"
    a_top, a_bot, a_edge = shade(a, 114), a, shade(a, 82)
    btn_top, btn_bot = rgba(shade(p["raised"], 112), 0.92), rgba(p["raised"], 0.92)
    rail = shade(p["bg"], 78) if dark else p["surface"]
    glass_pill = "rgba(255, 255, 255, 0.055)" if dark else "rgba(20, 24, 40, 0.045)"
    glass_hover = "rgba(255, 255, 255, 0.085)" if dark else "rgba(20, 24, 40, 0.07)"
    glass_edge = "rgba(255, 255, 255, 0.09)" if dark else "rgba(20, 24, 40, 0.09)"
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

    #navButton {{ background: transparent; border: none; border-left: 3px solid transparent;
                  border-radius: 7px; padding: 10px 12px; text-align: left; color: {p['muted']};
                  font-weight: 600; }}
    #navButton:hover {{ background: {rgba(p['raised'], 0.55)}; color: {p['text']}; }}
    #navButton:checked {{ background: qlineargradient(x1:0, y1:0, x2:1, y2:0,
                              stop:0 {rgba(a, 0.24)}, stop:1 {rgba(a, 0.04)});
                          border-left: 3px solid {a}; color: {a}; }}

    #accountChip {{ background: transparent; border: none; border-radius: 6px; padding: 4px 8px; }}
    #accountChip:hover {{ background: {p['raised']}; }}

    #card {{ background: qlineargradient(x1:0, y1:0, x2:0, y2:1, stop:0 {card_top}, stop:1 {card_bot});
             border: 1px solid {p['border']}; border-top-color: {edge_hi}; border-radius: 18px; }}
    #card:hover {{ border-color: {rgba(a, 0.55)}; }}
    #row {{ background: qlineargradient(x1:0, y1:0, x2:0, y2:1, stop:0 {card_top}, stop:1 {card_bot});
            border: 1px solid {p['border']}; border-top-color: {edge_hi}; border-radius: 14px; }}
    #row:hover {{ border-color: {rgba(a, 0.45)}; }}
    #emptyIcon {{ background: {tint(a, 0.12)}; border-radius: 16px; }}

    QPushButton {{ background: qlineargradient(x1:0, y1:0, x2:0, y2:1, stop:0 {btn_top}, stop:1 {btn_bot});
                   border: 1px solid {p['border']}; border-top-color: {edge_hi}; border-radius: 12px;
                   padding: 8px 16px; color: {p['text']}; font-weight: 600; }}
    QPushButton:hover {{ border-color: {rgba(a, 0.6)}; color: {p['text']}; }}
    QPushButton:pressed {{ background: {btn_bot}; padding-top: 9px; padding-bottom: 7px; }}
    QPushButton:disabled {{ color: {p['faint']}; }}
    QPushButton:focus {{ border-color: {a}; }}
    QPushButton#accent {{ background: qlineargradient(x1:0, y1:0, x2:0, y2:1, stop:0 {a_top}, stop:1 {a_bot});
                          color: {on_a}; border: 1px solid {a_edge}; border-top-color: {shade(a, 135)};
                          font-weight: 700; }}
    QPushButton#accent:hover {{ background: qlineargradient(x1:0, y1:0, x2:0, y2:1,
                                    stop:0 {shade(a, 124)}, stop:1 {hover_a}); }}
    QPushButton#accent:pressed {{ background: {a_edge}; padding-top: 9px; padding-bottom: 7px; }}
    QPushButton#accent:disabled {{ background: {tint(a, 0.35)}; color: {tint(on_a, 0.6)}; border-color: transparent; }}
    QPushButton#outline {{ background: {rgba(a, 0.06)}; color: {a}; border: 1px solid {rgba(a, 0.45)}; }}
    QPushButton#outline:hover {{ background: {rgba(a, 0.14)}; border-color: {a}; }}
    QPushButton#icon {{ background: transparent; border: none; padding: 6px; border-radius: 5px; }}
    QPushButton#icon:hover {{ background: {p['raised']}; }}
    QPushButton#swatch {{ border-radius: 6px; padding: 0; min-width: 28px; max-width: 28px;
                          min-height: 28px; max-height: 28px; }}
    QPushButton#tab {{ background: transparent; border: none; border-bottom: 2px solid transparent;
                       border-radius: 0; padding: 8px 4px; color: {p['muted']}; font-weight: 600; }}
    QPushButton#tab:checked {{ color: {p['text']}; border-bottom-color: {a}; }}

    QLineEdit, QComboBox, QSpinBox, QPlainTextEdit {{
        background: {rgba(p['bg'], 0.78)}; border: 1px solid {p['border']}; border-radius: 12px;
        padding: 8px 12px; selection-background-color: {rgba(a, 0.45)}; }}
    QLineEdit:focus, QComboBox:focus, QSpinBox:focus {{ border-color: {a}; }}
    QLineEdit:hover, QComboBox:hover, QSpinBox:hover {{ border-color: {p['faint']}; }}
    QComboBox::drop-down {{ border: none; width: 26px; }}
    QComboBox QAbstractItemView {{ outline: none; padding: 4px; }}
    QComboBox QAbstractItemView::item {{ min-height: 28px; padding: 0 10px; border-radius: 5px; }}
    QComboBox QAbstractItemView::item:hover {{ background: {p['raised']}; }}
    QComboBox QAbstractItemView {{ background: {p['surface']}; border: 1px solid {p['border']};
                                   selection-background-color: {tint(a, 0.2)}; }}
    #segmented {{ background: {rgba(p['bg'], 0.78)}; border: 1px solid {p['border']}; border-radius: 9px; }}
    QPushButton#segment {{ background: transparent; border: none; border-radius: 5px;
                           padding: 6px 14px; color: {p['muted']}; font-weight: 600; }}
    QPushButton#segment:hover {{ color: {p['text']}; }}
    QPushButton#segment:checked {{ background: qlineargradient(x1:0, y1:0, x2:0, y2:1,
                                       stop:0 {a_top}, stop:1 {a_bot}); color: {on_a}; }}
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
    QSlider::handle:horizontal {{ background: qradialgradient(cx:0.5, cy:0.4, radius:0.7, stop:0 #ffffff,
                                      stop:1 {shade(p['text'], 90)}); width: 16px; height: 16px;
                                  margin: -6px 0; border-radius: 8px; border: 2px solid {a}; }}
    QProgressBar {{ background: {p['raised']}; border: none; border-radius: 2px; }}
    QProgressBar::chunk {{ background: {a}; border-radius: 2px; }}
    #console {{ background: {p['bg']}; border: none; border-top: 1px solid {p['border']};
                border-radius: 0; font-family: monospace; font-size: 12px; color: {p['muted']}; }}
    #footer {{ background: {glass}; border-top: 1px solid {p['border']}; }}
    QScrollBar:vertical {{ background: transparent; width: 10px; }}
    QScrollBar::handle:vertical {{ background: {p['border']}; border-radius: 4px; min-height: 30px; }}
    QScrollBar::add-line, QScrollBar::sub-line {{ height: 0; }}
    QMenu {{ background: {p['surface']}; border: 1px solid {p['border']}; border-radius: 12px; padding: 6px; }}
    QMenu::item {{ padding: 8px 22px 8px 14px; border-radius: 8px; color: {p['text']}; background: transparent; }}
    QMenu::item:selected {{ background: {tint(a, 0.22)}; color: {p['text']}; }}
    QMenu::item:disabled {{ color: {p['faint']}; }}
    QMenu::separator {{ height: 1px; background: {p['border']}; margin: 5px 8px; }}
    QToolTip {{ background: {p['raised']}; color: {p['text']}; border: 1px solid {p['border']}; }}

    /* ---- 1.1 look: a solid icon rail, floating glass pills, big heavy titles */
    #rail {{ background: {rail}; border: none; }}
    #railGroup {{ background: {glass_pill}; border: 1px solid {glass_edge}; border-radius: 26px; }}
    #pill {{ background: {glass_pill}; border: 1px solid {glass_edge}; border-radius: 24px; }}
    #pill:hover {{ border-color: {rgba(a, 0.55)}; background: {glass_hover}; }}
    #chip {{ background: {glass_pill}; border: 1px solid {glass_edge}; border-radius: 14px; }}
    #chipText {{ font-size: 13px; font-weight: 800; color: {p['text']}; }}
    #pillTitle {{ font-size: 15px; font-weight: 750; }}
    #pillSub {{ font-size: 12px; color: {p['muted']}; }}
    #heroTitle {{ font-size: 64px; font-weight: 900; color: {p['text']}; }}
    #pageTitle {{ font-size: 40px; font-weight: 900; color: {p['text']}; }}
    #heroText {{ font-size: 16px; color: {p['muted']}; }}
    #heroStat {{ font-size: 26px; font-weight: 850; color: {p['text']}; }}
    #sectionLabel {{ font-size: 12px; font-weight: 800; color: {p['faint']}; }}
    #glassCard {{ background: {glass_pill}; border: 1px solid {glass_edge}; border-radius: 18px; }}
    #glassCard:hover {{ border-color: {rgba(a, 0.5)}; background: {glass_hover}; }}
    #footer {{ background: transparent; border: none; }}
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
    # Solid icons for the side rail.
    "s_user": '<g fill="FILL" stroke="none"><circle cx="12" cy="7.8" r="4.3"/>'
              '<path d="M3.8 20.6c0-4.4 3.7-7.2 8.2-7.2s8.2 2.8 8.2 7.2v.4H3.8z"/></g>',
    "s_home": '<path fill="FILL" stroke="none" d="M12 2.9 3.2 10.3V20a1.2 1.2 0 0 0 1.2 1.2h5V15.6h5.2v5.6h5'
              'a1.2 1.2 0 0 0 1.2-1.2v-9.7z"/>',
    "s_grid": '<g fill="FILL" stroke="none"><rect x="3" y="3" width="8" height="8" rx="2.2"/>'
              '<rect x="13" y="3" width="8" height="8" rx="2.2"/><rect x="3" y="13" width="8" height="8" rx="2.2"/>'
              '<rect x="13" y="13" width="8" height="8" rx="2.2"/></g>',
    "s_gear": '<path fill="FILL" fill-rule="evenodd" stroke="none" d="M12.00 3.80 L14.07 1.60 L16.06 2.21 L16.56 5.18 L17.80 6.20 L20.81 6.11 L21.79 7.94 L20.04 10.40 L20.20 12.00 L22.40 14.07 L21.79 16.06 L18.82 16.56 L17.80 17.80 L17.89 20.81 L16.06 21.79 L13.60 20.04 L12.00 20.20 L9.93 22.40 L7.94 21.79 L7.44 18.82 L6.20 17.80 L3.19 17.89 L2.21 16.06 L3.96 13.60 L3.80 12.00 L1.60 9.93 L2.21 7.94 L5.18 7.44 L6.20 6.20 L6.11 3.19 L7.94 2.21 L10.40 3.96Z M12 8.6a3.4 3.4 0 1 0 0 6.8a3.4 3.4 0 1 0 0-6.8z"/>',
    "s_panel": '<rect x="3" y="4" width="18" height="16" rx="3.5" fill="FILL" stroke="none"/>'
               '<path d="M9 4v16" stroke="BG" stroke-width="1.8"/>'
               '<path d="M15.5 9.5 13 12l2.5 2.5" stroke="BG" stroke-width="1.8"/>',
    "s_panel_open": '<rect x="3" y="4" width="18" height="16" rx="3.5" fill="FILL" stroke="none"/>'
                    '<path d="M9 4v16" stroke="BG" stroke-width="1.8"/>'
                    '<path d="M13 9.5 15.5 12 13 14.5" stroke="BG" stroke-width="1.8"/>',
    "s_play": '<circle cx="12" cy="12" r="11" fill="FILL" stroke="none" opacity="0.22"/>'
              '<path fill="FILL" stroke="none" d="M9.6 7.6v8.8a.6.6 0 0 0 .9.5l7-4.4a.6.6 0 0 0 0-1l-7-4.4a.6.6 0 0 0-.9.5z"/>',
    "s_plus": '<circle cx="12" cy="12" r="11" fill="FILL" stroke="none" opacity="0.14"/>'
              '<path d="M12 7.5v9M7.5 12h9" stroke="FILL" stroke-width="2"/>',
    "s_layers": '<path fill="FILL" stroke="none" d="M12 3 2.5 8 12 13l9.5-5z"/>'
                '<path d="M3.5 12.2 12 16.7l8.5-4.5M3.5 16 12 20.5l8.5-4.5" stroke="FILL" stroke-width="1.8" fill="none"/>',
    "users": '<circle cx="9" cy="8.5" r="3.5"/><path d="M2.5 19.5c.4-3.4 3-5.2 6.5-5.2s6.1 1.8 6.5 5.2"/>'
             '<path d="M15.5 5.3a3.4 3.4 0 0 1 0 6.4M18 14.6c2 .6 3.3 2.2 3.5 4.9"/>',
    "refresh": '<path d="M19.5 12a7.5 7.5 0 1 1-2.2-5.3"/><path d="M19.5 4.5v4h-4"/>',
    "user_plus": '<circle cx="9.5" cy="8" r="3.8"/><path d="M2.5 20c.4-3.6 3.2-5.6 7-5.6s6.6 2 7 5.6M18.5 7v6M15.5 10h6"/>',
    "x": '<path d="M6.5 6.5l11 11M17.5 6.5l-11 11"/>',
    "alert": '<path d="M12 3.8 2.8 19.5h18.4z"/><path d="M12 9.5v4.5M12 16.8v.2"/>',
    "info": '<circle cx="12" cy="12" r="8.5"/><path d="M12 11v5.5M12 7.8v.2"/>',
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


def icon_pixmap(name: str, color: str = None, size: int = 20, dpr: float = 2.0, bg: str = None) -> QPixmap:
    color = color or Theme.p["muted"]
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" '
           f'stroke="{color}" stroke-width="1.8" stroke-linecap="round" '
           f'stroke-linejoin="round">{_PATHS[name].replace("FILL", color).replace("BG", bg or Theme.p["bg"])}</svg>')
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
    '<defs>'
    '<linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">'
    '<stop offset="0" stop-color="#2a2150"/>'
    '<stop offset="1" stop-color="#0f1118"/>'
    '</linearGradient>'
    '<linearGradient id="falcon" x1="0" y1="0" x2="0" y2="1">'
    '<stop offset="0" stop-color="#ffd27a"/>'
    '<stop offset="1" stop-color="#f08a24"/>'
    '</linearGradient>'
    '</defs>'
    '<rect width="128" height="128" rx="28" fill="url(#bg)"/>'
    '<rect x="2" y="2" width="124" height="124" rx="26" fill="none" stroke="#7c5cff" stroke-opacity="0.32" stroke-width="2"/>'
    '<path d="M64 104 L54 62 L18 40 L56 48 L64 22 L72 48 L110 40 L74 62 Z" fill="url(#falcon)"/>'
    '<path d="M64 22 L68 36 L60 36 Z" fill="#fff1d6"/>'
    '</svg>'
)


def render_app_icon(size: int) -> QPixmap:
    return _render(APP_ICON_SVG, size, 1.0)
