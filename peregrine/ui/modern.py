"""The 1.1 look: pill buttons, the icon rail, chips, glass pills and the live
status ring. Everything animates a little, and nothing animates while hidden."""

from PySide6.QtCore import (
    Property, QEasingCurve, QPointF, QPropertyAnimation, QRect, QRectF, QSize, Qt, QTimer,
    QVariantAnimation, Signal,
)
from PySide6.QtGui import QColor, QFont, QPainter, QPainterPath, QPen, QPixmap
from PySide6.QtWidgets import (
    QAbstractButton, QFrame, QHBoxLayout, QLabel, QPushButton, QSizePolicy, QVBoxLayout, QWidget,
)

from .. import config
from .theme import Theme, icon_pixmap, on_color


def animations_on() -> bool:
    return bool(config.load().get("animations", True))


def _mix(a: QColor, b: QColor, t: float) -> QColor:
    return QColor(round(a.red() + (b.red() - a.red()) * t), round(a.green() + (b.green() - a.green()) * t),
                  round(a.blue() + (b.blue() - a.blue()) * t), round(a.alpha() + (b.alpha() - a.alpha()) * t))


def dark() -> bool:
    return Theme.name != "light"


def heavy_font(px: int, weight=QFont.Black, spacing: float = 0) -> QFont:
    f = QFont()
    f.setPixelSize(px)
    f.setWeight(weight)
    if spacing:
        f.setLetterSpacing(QFont.AbsoluteSpacing, spacing)
    return f


# ------------------------------------------------------------------ buttons

class PillButton(QPushButton):
    """A rounded pill: 'primary' (accent fill) or 'ghost' (glass), with a round icon
    badge on the left like a play button. Brightens on hover, dips on press."""

    def __init__(self, text: str, icon_name: str = None, kind: str = "primary", big: bool = False, parent=None):
        super().__init__(text.upper(), parent)
        self.kind, self.icon_name, self.big = kind, icon_name, big
        self._hover = 0.0
        self.setCursor(Qt.PointingHandCursor)
        self.setFont(heavy_font(17 if big else 14, QFont.Black if big else QFont.ExtraBold, 0.4))
        self._anim = QVariantAnimation(self)
        self._anim.setDuration(150)
        self._anim.setEasingCurve(QEasingCurve.OutCubic)
        self._anim.valueChanged.connect(self._set_hover)
        self.setSizePolicy(QSizePolicy.Fixed, QSizePolicy.Fixed)
        # press: the pill dips a little and a ripple spreads from where it was clicked
        self._press = 0.0
        self._ripple = None  # (x, y, t 0..1)
        self._press_anim = QVariantAnimation(self)
        self._press_anim.valueChanged.connect(self._set_press)
        self._ripple_anim = QVariantAnimation(self, duration=520, startValue=0.0, endValue=1.0,
                                              easingCurve=QEasingCurve.OutCubic)
        self._ripple_anim.valueChanged.connect(self._set_ripple)

    def _set_press(self, v):
        self._press = float(v)
        self.update()

    def _set_ripple(self, v):
        if self._ripple is not None:
            self._ripple = (self._ripple[0], self._ripple[1], float(v))
            self.update()

    def _press_to(self, to, ms):
        if not animations_on():
            self._set_press(to)
            return
        self._press_anim.stop()
        self._press_anim.setDuration(ms)
        self._press_anim.setEasingCurve(QEasingCurve.OutCubic if to else QEasingCurve.OutBack)
        self._press_anim.setStartValue(self._press)
        self._press_anim.setEndValue(to)
        self._press_anim.start()

    def mousePressEvent(self, e):
        if e.button() == Qt.LeftButton and self.isEnabled():
            self._press_to(1.0, 90)
            if animations_on():
                pos = e.position()
                self._ripple = (pos.x(), pos.y(), 0.0)
                self._ripple_anim.stop()
                self._ripple_anim.start()
        super().mousePressEvent(e)

    def mouseReleaseEvent(self, e):
        self._press_to(0.0, 260)
        super().mouseReleaseEvent(e)

    def _set_hover(self, v):
        self._hover = float(v)
        self.update()

    def _fade(self, to: float):
        if not animations_on():
            self._set_hover(to)
            return
        self._anim.stop()
        self._anim.setStartValue(self._hover)
        self._anim.setEndValue(to)
        self._anim.start()

    def enterEvent(self, e):
        self._fade(1.0)
        super().enterEvent(e)

    def leaveEvent(self, e):
        self._fade(0.0)
        super().leaveEvent(e)

    def sizeHint(self):
        h = 64 if self.big else 46
        badge = (h - 22) if self.icon_name else 0
        w = self.fontMetrics().horizontalAdvance(self.text()) + (h * 0.75) + badge + (14 if badge else 0)
        return QSize(int(w), h)

    def minimumSizeHint(self):
        return self.sizeHint()

    def paintEvent(self, _):
        p = QPainter(self)
        p.setRenderHint(QPainter.Antialiasing)
        r = QRectF(self.rect()).adjusted(1, 1, -1, -1)
        if self._press:
            k = 0.035 * self._press  # dip: shrink a touch around the middle
            r = r.adjusted(r.width() * k / 2, r.height() * k, -r.width() * k / 2, -r.height() * k)
        radius = r.height() / 2
        accent = QColor(Theme.accent)
        enabled = self.isEnabled()
        down = self.isDown()
        if self.kind == "danger":
            accent = QColor(Theme.p["danger"])
        if self.kind in ("primary", "danger"):
            base = accent if enabled else QColor(accent.red(), accent.green(), accent.blue(), 90)
            fill = _mix(base, base.lighter(118), self._hover)
            if down:
                fill = base.darker(112)
            if enabled and self._hover > 0:  # soft glow under it
                for i, a in ((6, 18), (3, 30)):
                    glow = QColor(accent)
                    glow.setAlpha(int(a * self._hover))
                    path = QPainterPath()
                    path.addRoundedRect(r.adjusted(-i, -i + 3, i, i + 3), radius + i, radius + i)
                    p.fillPath(path, glow)
            text_color = QColor(on_color(accent.name()))
            border = None
        else:
            if dark():
                fill = _mix(QColor(255, 255, 255, 14), QColor(255, 255, 255, 26), self._hover)
                border = _mix(QColor(255, 255, 255, 24), accent, self._hover * 0.8)
            else:
                fill = _mix(QColor(20, 24, 40, 12), QColor(20, 24, 40, 22), self._hover)
                border = _mix(QColor(20, 24, 40, 30), accent, self._hover * 0.8)
            if down:
                fill.setAlpha(fill.alpha() + 12)
            text_color = QColor(Theme.p["text"] if enabled else Theme.p["faint"])
        path = QPainterPath()
        path.addRoundedRect(r, radius, radius)
        p.fillPath(path, fill)
        if self._ripple is not None and self._ripple[2] < 1.0:
            x, y, t = self._ripple
            p.save()
            p.setClipPath(path)
            ring = QColor(255, 255, 255, int(70 * (1 - t)))
            reach = max(r.width(), r.height()) * 1.1 * t
            p.setPen(Qt.NoPen)
            p.setBrush(ring)
            p.drawEllipse(QPointF(x, y), reach, reach)
            p.restore()
        if border is not None:
            p.setPen(QPen(border, 1.2))
            p.drawPath(path)
        x = r.left() + r.height() * 0.38
        if self.icon_name:
            size = int(r.height() - 22)
            badge_color = text_color.name() if self.kind in ("primary", "danger") else Theme.p["text"]
            pm = icon_pixmap(self.icon_name, badge_color, size)
            p.drawPixmap(QPointF(x - 4, r.top() + (r.height() - size) / 2), pm)
            x += size + 10
        p.setPen(text_color)
        p.setFont(self.font())
        p.drawText(QRectF(x, r.top(), r.right() - x - r.height() * 0.3, r.height()),
                   Qt.AlignVCenter | Qt.AlignLeft, self.text())
        p.end()


class RailButton(QAbstractButton):
    """A square icon button for the side rail (the sliding highlight is the rail's)."""

    def __init__(self, icon_name: str, tip: str, parent=None):
        super().__init__(parent)
        self.icon_name = icon_name
        self.setCheckable(True)
        self.setToolTip(tip)
        self.setCursor(Qt.PointingHandCursor)
        self.setFixedSize(54, 54)
        self._hover = False

    def enterEvent(self, e):
        self._hover = True
        self.update()
        super().enterEvent(e)

    def leaveEvent(self, e):
        self._hover = False
        self.update()
        super().leaveEvent(e)

    def paintEvent(self, _):
        p = QPainter(self)
        p.setRenderHint(QPainter.Antialiasing)
        if self.isChecked():
            color = Theme.accent
        elif self._hover:
            color = Theme.p["text"]
        else:
            color = Theme.p["muted"] if dark() else Theme.p["faint"]
        size = 26
        pm = icon_pixmap(self.icon_name, color, size)
        p.drawPixmap((self.width() - size) // 2, (self.height() - size) // 2, pm)
        p.end()


class _Highlight(QWidget):
    """The soft square that slides to the selected rail button."""

    def __init__(self, parent):
        super().__init__(parent)
        self.setAttribute(Qt.WA_TransparentForMouseEvents)

    def paintEvent(self, _):
        p = QPainter(self)
        p.setRenderHint(QPainter.Antialiasing)
        c = QColor(Theme.accent)
        c.setAlpha(40)
        path = QPainterPath()
        path.addRoundedRect(QRectF(self.rect()).adjusted(2, 2, -2, -2), 16, 16)
        p.fillPath(path, c)
        p.end()


class Rail(QFrame):
    """The left side rail: logo on top, a pill of page buttons in the middle and
    the collapse button at the bottom. Collapses to nothing with a slide."""
    picked = Signal(str)
    WIDTH = 92

    def __init__(self, logo: QLabel, items: list, parent=None):
        """items: [(key, icon_name, tooltip)]"""
        super().__init__(parent, objectName="rail")
        self.setFixedWidth(self.WIDTH)
        v = QVBoxLayout(self)
        v.setContentsMargins(0, 22, 0, 22)
        v.setSpacing(0)
        v.addWidget(logo, 0, Qt.AlignHCenter)
        v.addStretch(1)
        self.group = QFrame(objectName="railGroup")
        self.group.setFixedWidth(64)
        g = QVBoxLayout(self.group)
        g.setContentsMargins(5, 7, 5, 7)
        g.setSpacing(4)
        self.highlight = _Highlight(self.group)
        self.highlight.hide()
        self.buttons = {}
        for key, icon_name, tip in items:
            b = RailButton(icon_name, tip)
            b.clicked.connect(lambda _=False, k=key: self.picked.emit(k))
            g.addWidget(b, 0, Qt.AlignHCenter)
            self.buttons[key] = b
        v.addWidget(self.group, 0, Qt.AlignHCenter)
        v.addStretch(1)
        v.addSpacing(72)  # room for the collapse button, which floats so it stays when collapsed
        self._anim = None
        self._width_anim = None
        self.current = None

    def select(self, key):
        self.current = key
        for k, b in self.buttons.items():
            b.setChecked(k == key)
        b = self.buttons.get(key)
        if b is None:
            self.highlight.hide()
            return
        QTimer.singleShot(0, lambda: self._move_highlight(b))

    def _move_highlight(self, b):
        try:
            target = b.geometry()
        except RuntimeError:
            return
        if target.width() <= 0:
            return
        if not self.highlight.isVisible() or not animations_on():
            self.highlight.setGeometry(target)
            self.highlight.show()
            self.highlight.lower()
            return
        self._anim = QPropertyAnimation(self.highlight, b"geometry", self)
        self._anim.setDuration(240)
        self._anim.setEasingCurve(QEasingCurve.OutBack)
        self._anim.setStartValue(self.highlight.geometry())
        self._anim.setEndValue(target)
        self._anim.start()

    def resizeEvent(self, e):
        super().resizeEvent(e)
        if self.current in self.buttons:
            self.highlight.setGeometry(self.buttons[self.current].geometry())

    def _get_w(self):
        return self.width()

    def _set_w(self, w):
        self.setFixedWidth(int(w))

    railWidth = Property(int, _get_w, _set_w)

    def set_open(self, open_: bool):
        end = self.WIDTH if open_ else 0
        if not animations_on():
            self.setFixedWidth(end)
            return
        self._width_anim = QPropertyAnimation(self, b"railWidth", self)
        self._width_anim.setDuration(260)
        self._width_anim.setEasingCurve(QEasingCurve.OutCubic)
        self._width_anim.setStartValue(self.width())
        self._width_anim.setEndValue(end)
        self._width_anim.start()


class FloatButton(QAbstractButton):
    """A round glass button that floats over everything (the rail's collapse button)."""

    def __init__(self, icon_name: str, tip: str, parent=None):
        super().__init__(parent)
        self.icon_name = icon_name
        self.setToolTip(tip)
        self.setCursor(Qt.PointingHandCursor)
        self.setFixedSize(54, 54)
        self._hover = False

    def enterEvent(self, e):
        self._hover = True
        self.update()

    def leaveEvent(self, e):
        self._hover = False
        self.update()

    def paintEvent(self, _):
        p = QPainter(self)
        p.setRenderHint(QPainter.Antialiasing)
        path = QPainterPath()
        path.addEllipse(QRectF(self.rect()).adjusted(1, 1, -1, -1))
        fill = QColor(255, 255, 255, 30 if self._hover else 16) if dark() else QColor(20, 24, 40, 26 if self._hover else 14)
        p.fillPath(path, fill)
        size = 24
        color = Theme.p["text"] if self._hover else Theme.p["muted"]
        p.drawPixmap((self.width() - size) // 2, (self.height() - size) // 2,
                     icon_pixmap(self.icon_name, color, size, bg=Theme.p["bg"]))
        p.end()


# -------------------------------------------------------------- pills etc.

class Pill(QFrame):
    """A clickable glass pill (the top bar's profile and server pills)."""
    clicked = Signal()

    def __init__(self, parent=None):
        super().__init__(parent, objectName="pill")
        self.setCursor(Qt.PointingHandCursor)
        self.setMinimumHeight(64)

    def mouseReleaseEvent(self, e):
        if e.button() == Qt.LeftButton and self.rect().contains(e.position().toPoint()):
            self.clicked.emit()
        super().mouseReleaseEvent(e)


class Chip(QFrame):
    """'● HOME': a small label with a status dot, above a page's big title."""

    def __init__(self, text: str, dot: str = None, parent=None):
        super().__init__(parent, objectName="chip")
        h = QHBoxLayout(self)
        h.setContentsMargins(14, 7, 16, 7)
        h.setSpacing(10)
        self.dot = QLabel()
        self.dot.setFixedSize(10, 10)
        h.addWidget(self.dot)
        self.label = QLabel(text.upper(), objectName="chipText")
        self.label.setFont(heavy_font(13, QFont.ExtraBold, 1.0))
        h.addWidget(self.label)
        self.set_dot(dot)
        self.setSizePolicy(QSizePolicy.Fixed, QSizePolicy.Fixed)

    def set_dot(self, color: str = None):
        color = color or Theme.p["faint"]
        self.dot.setStyleSheet(f"background: {color}; border-radius: 5px;")

    def set_text(self, text: str):
        self.label.setText(text.upper())


def big_title(text: str, hero: bool = False) -> QLabel:
    t = QLabel(text.upper(), objectName="heroTitle" if hero else "pageTitle")
    t.setFont(heavy_font(64 if hero else 40, QFont.Black, -1.0 if hero else -0.5))
    return t


def section_label(text: str) -> QLabel:
    lab = QLabel(text.upper(), objectName="sectionLabel")
    lab.setFont(heavy_font(12, QFont.ExtraBold, 1.0))
    return lab


ONLINE = "#3ddc84"
OFFLINE = "#e5676b"


class StatusRing(QWidget):
    """The ring on the server pill: spins while checking, then green (up) or red (down)."""

    def __init__(self, size: int = 34, parent=None):
        super().__init__(parent)
        self.setFixedSize(size, size)
        self.state = "idle"
        self.angle = 0
        self.timer = QTimer(self)
        self.timer.setInterval(16)
        self.timer.timeout.connect(self._spin)

    def set_state(self, state: str):
        self.state = state
        if state == "checking" and animations_on():
            self.timer.start()
        else:
            self.timer.stop()
        self.update()

    def _spin(self):
        if not self.isVisible():
            self.timer.stop()
            return
        self.angle = (self.angle + 6) % 360
        self.update()

    def showEvent(self, e):
        if self.state == "checking" and animations_on():
            self.timer.start()
        super().showEvent(e)

    def paintEvent(self, _):
        p = QPainter(self)
        p.setRenderHint(QPainter.Antialiasing)
        r = QRectF(self.rect()).adjusted(4, 4, -4, -4)
        track = QColor(Theme.p["text"])
        track.setAlpha(28)
        p.setPen(QPen(track, 3))
        p.drawEllipse(r)
        if self.state == "checking":
            p.setPen(QPen(QColor(Theme.accent), 3, Qt.SolidLine, Qt.RoundCap))
            p.drawArc(r, -self.angle * 16, 100 * 16)
        elif self.state in ("online", "offline"):
            c = QColor(ONLINE if self.state == "online" else OFFLINE)
            p.setPen(QPen(c, 3))
            p.drawEllipse(r)
            p.setPen(Qt.NoPen)
            p.setBrush(c)
            p.drawEllipse(r.center(), 4, 4)
        p.end()


def rounded_pixmap(pm: QPixmap, size: int, radius: float = 0.22, dpr: float = 2.0) -> QPixmap:
    """A picture cut to a rounded square (server icons, gamerpics)."""
    px = int(size * dpr)
    out = QPixmap(px, px)
    out.fill(Qt.transparent)
    if pm is None or pm.isNull():
        out.setDevicePixelRatio(dpr)
        return out
    p = QPainter(out)
    p.setRenderHint(QPainter.Antialiasing)
    p.setRenderHint(QPainter.SmoothPixmapTransform, pm.width() > 64)  # keep pixel art crisp
    clip = QPainterPath()
    clip.addRoundedRect(QRectF(0, 0, px, px), px * radius, px * radius)
    p.setClipPath(clip)
    p.drawPixmap(QRect(0, 0, px, px), pm)
    p.end()
    out.setDevicePixelRatio(dpr)
    return out


def pixmap_from_png(data: bytes) -> QPixmap:
    pm = QPixmap()
    if data:
        pm.loadFromData(data, "PNG")
    return pm


def count_text(n: int) -> str:
    return f"{n:,}"


class CountUp(QLabel):
    """A number that counts up to its value (the live player count)."""

    def __init__(self, parent=None):
        super().__init__("", parent, objectName="heroStat")
        self.setFont(heavy_font(28, QFont.Black))
        self._anim = QVariantAnimation(self)
        self._anim.setDuration(700)
        self._anim.setEasingCurve(QEasingCurve.OutCubic)
        self._anim.valueChanged.connect(lambda v: self.setText(count_text(int(v))))
        self.value = 0

    def set_value(self, n: int):
        start = self.value
        self.value = n
        if not animations_on() or start == n:
            self.setText(count_text(n))
            return
        self._anim.stop()
        self._anim.setStartValue(start)
        self._anim.setEndValue(n)
        self._anim.start()


def fade_in(widget: QWidget, ms: int = 260, rise: int = 14, delay: int = 0):
    """Fade a widget in while it rises a few pixels into place."""
    if not animations_on():
        return
    from PySide6.QtWidgets import QGraphicsOpacityEffect
    effect = QGraphicsOpacityEffect(widget)
    effect.setOpacity(0.0)
    widget.setGraphicsEffect(effect)
    anim = QPropertyAnimation(effect, b"opacity", widget)
    anim.setDuration(ms)
    anim.setStartValue(0.0)
    anim.setEndValue(1.0)
    anim.setEasingCurve(QEasingCurve.OutCubic)

    def done():
        try:
            widget.setGraphicsEffect(None)
        except RuntimeError:
            pass
    anim.finished.connect(done)
    widget._fade_anim = anim
    if rise:
        start = widget.pos()
        move = QPropertyAnimation(widget, b"pos", widget)
        move.setDuration(ms + 60)
        move.setStartValue(start + QPointF(0, rise).toPoint())
        move.setEndValue(start)
        move.setEasingCurve(QEasingCurve.OutCubic)
        widget._rise_anim = move
    else:
        move = None

    def go():
        try:
            anim.start()
            if move is not None and widget.parentWidget() is not None and widget.parentWidget().layout() is None:
                move.start()  # only for freely placed widgets; layouts place the rest
        except RuntimeError:
            pass
    QTimer.singleShot(delay, go)
