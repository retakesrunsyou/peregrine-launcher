"""The Windows installer's and uninstaller's window: a small frameless card with the
launcher's night sky, the falcon, one big button and smooth slides between steps.

run_installer() is the installer (packaging/setup_app.py); run_uninstaller() is what
"Peregrine.exe --uninstall" shows (Settings → Apps → Uninstall).
"""

import math
import os
import random
import sys
import time
from pathlib import Path

from PySide6.QtCore import (
    QEasingCurve, QObject, QPoint, QPointF, QRectF, QSize, Qt, QThread, QTimer, QVariantAnimation, Signal,
)
from PySide6.QtGui import (
    QColor, QFont, QLinearGradient, QPainter, QPainterPath, QPen, QRadialGradient,
)
from PySide6.QtWidgets import (
    QAbstractButton, QApplication, QFileDialog, QGraphicsOpacityEffect, QHBoxLayout, QLabel, QVBoxLayout, QWidget,
)

from .theme import Theme, icon_pixmap, logo_pixmap, render_app_icon, stylesheet

W, H, SHADOW, RADIUS = 760, 480, 22, 20
ACCENT = "#7c5cff"
AMBER = "#f0a33a"


def _font(px, weight=QFont.Bold, spacing=0.0):
    f = QFont()
    f.setPixelSize(px)
    f.setWeight(weight)
    if spacing:
        f.setLetterSpacing(QFont.AbsoluteSpacing, spacing)
    return f


def _anim(parent, ms, start, end, curve, step, done=None):
    a = QVariantAnimation(parent)
    a.setDuration(ms)
    a.setStartValue(float(start))
    a.setEndValue(float(end))
    a.setEasingCurve(curve)
    a.valueChanged.connect(lambda v: step(float(v)))
    if done:
        a.finished.connect(done)
    a.start()
    return a


# ---------------------------------------------------------------------- pieces

class _Sky:
    """Twinkling stars and two slow glows, drawn behind everything."""

    def __init__(self, seed=7):
        rnd = random.Random(seed)
        self.stars = [(rnd.random(), rnd.random() ** 1.4, rnd.uniform(0.5, 1.6), rnd.uniform(0, 6.28),
                       rnd.uniform(0.6, 1.8)) for _ in range(90)]
        self.t0 = time.monotonic()

    def paint(self, p: QPainter, r: QRectF):
        t = time.monotonic() - self.t0
        g = QLinearGradient(r.topLeft(), r.bottomRight())
        g.setColorAt(0, QColor("#1d1740"))
        g.setColorAt(0.55, QColor("#12131d"))
        g.setColorAt(1, QColor("#0c0d14"))
        p.fillRect(r, g)
        for cx, cy, rad, col, ph in ((0.18 + 0.05 * math.sin(t * 0.21), 0.12, 0.62, ACCENT, 0),
                                     (0.86 + 0.04 * math.cos(t * 0.17), 0.95, 0.55, AMBER, 1)):
            rg = QRadialGradient(QPointF(r.left() + cx * r.width(), r.top() + cy * r.height()), rad * r.width())
            c = QColor(col)
            c.setAlpha(int(46 + 12 * math.sin(t * 0.6 + ph)))
            rg.setColorAt(0, c)
            c2 = QColor(col)
            c2.setAlpha(0)
            rg.setColorAt(1, c2)
            p.fillRect(r, rg)
        p.setPen(Qt.NoPen)
        for x, y, size, phase, speed in self.stars:
            a = 0.35 + 0.65 * (0.5 + 0.5 * math.sin(t * speed + phase))
            p.setBrush(QColor(255, 255, 255, int(150 * a * (1 - y * 0.6))))
            p.drawEllipse(QPointF(r.left() + x * r.width(), r.top() + y * r.height() * 0.9), size * 0.6, size * 0.6)


class Logo(QWidget):
    """The falcon tile with a soft glow that breathes."""

    def __init__(self, size=96):
        super().__init__()
        self.size_ = size
        self.pm = render_app_icon(size * 2)
        self.pm.setDevicePixelRatio(2.0)
        self.setFixedSize(size + 40, size + 40)
        self.t0 = time.monotonic()
        self.spin = 0.0  # 0..1 while working: the glow turns

    def paintEvent(self, _):
        p = QPainter(self)
        p.setRenderHint(QPainter.Antialiasing)
        p.setRenderHint(QPainter.SmoothPixmapTransform)
        t = time.monotonic() - self.t0
        c = QPointF(self.width() / 2, self.height() / 2)
        glow = QRadialGradient(c, self.size_ * 0.85)
        a = QColor(ACCENT)
        a.setAlpha(int(70 + 30 * math.sin(t * 1.6)))
        glow.setColorAt(0, a)
        a2 = QColor(ACCENT)
        a2.setAlpha(0)
        glow.setColorAt(1, a2)
        p.fillRect(self.rect(), glow)
        if self.spin > 0:
            pen = QPen(QColor(255, 255, 255, int(110 * self.spin)), 2.2, Qt.SolidLine, Qt.RoundCap)
            p.setPen(pen)
            rr = QRectF(c.x() - self.size_ / 2 - 9, c.y() - self.size_ / 2 - 9, self.size_ + 18, self.size_ + 18)
            p.drawArc(rr, int(-t * 260 * 16), 70 * 16)
            p.drawArc(rr, int((-t * 260 + 180) * 16), 70 * 16)
        bob = 2.5 * math.sin(t * 1.3)
        p.drawPixmap(QPointF(c.x() - self.size_ / 2, c.y() - self.size_ / 2 + bob), self.pm)
        p.end()


class GlowBar(QWidget):
    """A rounded progress bar that glides to its value and has a moving shine."""

    def __init__(self):
        super().__init__()
        self.setFixedHeight(12)
        self.target = 0.0
        self.shown = 0.0
        self.t0 = time.monotonic()
        self.last = time.monotonic()

    def set_value(self, v):
        self.target = max(0.0, min(1.0, v))

    def tick(self):
        now = time.monotonic()
        dt = min(0.1, now - self.last)
        self.last = now
        self.shown += (self.target - self.shown) * (1 - math.exp(-9 * dt))
        if abs(self.target - self.shown) < 0.0005:
            self.shown = self.target
        self.update()

    def paintEvent(self, _):
        p = QPainter(self)
        p.setRenderHint(QPainter.Antialiasing)
        r = QRectF(self.rect()).adjusted(0.5, 0.5, -0.5, -0.5)
        path = QPainterPath()
        path.addRoundedRect(r, r.height() / 2, r.height() / 2)
        p.fillPath(path, QColor(255, 255, 255, 22))
        w = r.width() * self.shown
        if w > 1:
            fill = QRectF(r.left(), r.top(), max(r.height(), w), r.height())
            fp = QPainterPath()
            fp.addRoundedRect(fill, r.height() / 2, r.height() / 2)
            g = QLinearGradient(fill.topLeft(), fill.topRight())
            g.setColorAt(0, QColor(ACCENT))
            g.setColorAt(1, QColor("#b18cff"))
            p.fillPath(fp, g)
            # the shine sweeping along the filled part
            t = (time.monotonic() - self.t0) % 1.6 / 1.6
            sx = fill.left() + (fill.width() + 80) * t - 40
            sg = QLinearGradient(QPointF(sx - 40, 0), QPointF(sx + 40, 0))
            sg.setColorAt(0, QColor(255, 255, 255, 0))
            sg.setColorAt(0.5, QColor(255, 255, 255, 90))
            sg.setColorAt(1, QColor(255, 255, 255, 0))
            p.save()
            p.setClipPath(fp)
            p.fillRect(fill, sg)
            p.restore()
        p.end()


class CheckMark(QWidget):
    """A ring that draws itself, then a tick, then a little pop."""

    def __init__(self, color="#3ddc84", icon=None):
        super().__init__()
        self.setFixedSize(110, 110)
        self.color, self.icon = color, icon
        self.t = 0.0

    def play(self):
        self.t = 0.0
        self._a = _anim(self, 900, 0, 1, QEasingCurve.Linear, self._set)

    def _set(self, v):
        self.t = v
        self.update()

    def paintEvent(self, _):
        p = QPainter(self)
        p.setRenderHint(QPainter.Antialiasing)
        c = QPointF(55, 55)
        col = QColor(self.color)
        ring = min(1.0, self.t / 0.5)
        tick = max(0.0, min(1.0, (self.t - 0.4) / 0.4))
        pop = 1 + 0.08 * math.sin(max(0.0, min(1.0, (self.t - 0.75) / 0.25)) * math.pi)
        p.translate(c)
        p.scale(pop, pop)
        p.translate(-c)
        bg = QColor(col)
        bg.setAlpha(int(40 * ring))
        p.setPen(Qt.NoPen)
        p.setBrush(bg)
        p.drawEllipse(c, 40, 40)
        p.setPen(QPen(col, 4, Qt.SolidLine, Qt.RoundCap))
        p.setBrush(Qt.NoBrush)
        p.drawArc(QRectF(15, 15, 80, 80), 90 * 16, int(-360 * 16 * ring))
        if self.icon:
            p.setOpacity(tick)
            p.drawPixmap(QPointF(c.x() - 20, c.y() - 20), icon_pixmap(self.icon, col.name(), 40))
        elif tick > 0:
            path = QPainterPath()
            pts = [QPointF(38, 57), QPointF(50, 69), QPointF(73, 44)]
            l1 = 1.0
            l2 = 1.8
            d = tick * (l1 + l2)
            path.moveTo(pts[0])
            if d <= l1:
                path.lineTo(pts[0] + (pts[1] - pts[0]) * (d / l1))
            else:
                path.lineTo(pts[1])
                path.lineTo(pts[1] + (pts[2] - pts[1]) * ((d - l1) / l2))
            p.setPen(QPen(col, 5.5, Qt.SolidLine, Qt.RoundCap, Qt.RoundJoin))
            p.drawPath(path)
        p.end()


class _WinButton(QAbstractButton):
    """The minimize and close buttons in the corner."""

    def __init__(self, kind, parent):
        super().__init__(parent)
        self.kind = kind
        self.setFixedSize(40, 30)
        self.setCursor(Qt.PointingHandCursor)
        self.setFocusPolicy(Qt.NoFocus)
        self.h = 0.0

    def enterEvent(self, e):
        self._a = _anim(self, 120, self.h, 1, QEasingCurve.OutCubic, self._set)

    def leaveEvent(self, e):
        self._a = _anim(self, 160, self.h, 0, QEasingCurve.OutCubic, self._set)

    def _set(self, v):
        self.h = v
        self.update()

    def paintEvent(self, _):
        p = QPainter(self)
        p.setRenderHint(QPainter.Antialiasing)
        bg = QColor("#e5484d") if self.kind == "close" else QColor(255, 255, 255)
        bg.setAlpha(int((220 if self.kind == "close" else 30) * self.h))
        path = QPainterPath()
        path.addRoundedRect(QRectF(self.rect()).adjusted(3, 3, -3, -3), 8, 8)
        p.fillPath(path, bg)
        p.setPen(QPen(QColor(255, 255, 255, int(170 + 85 * self.h)), 1.4, Qt.SolidLine, Qt.RoundCap))
        cx, cy = self.width() / 2, self.height() / 2
        if self.kind == "close":
            p.drawLine(QPointF(cx - 5, cy - 5), QPointF(cx + 5, cy + 5))
            p.drawLine(QPointF(cx + 5, cy - 5), QPointF(cx - 5, cy + 5))
        else:
            p.drawLine(QPointF(cx - 5, cy), QPointF(cx + 5, cy))
        p.end()


class LinkButton(QAbstractButton):
    def __init__(self, text, parent=None):
        super().__init__(parent)
        self.setText(text)
        self.setCursor(Qt.PointingHandCursor)
        self.setFont(_font(12, QFont.DemiBold))
        self.h = False

    def sizeHint(self):
        return QSize(self.fontMetrics().horizontalAdvance(self.text()) + 4, 20)

    def enterEvent(self, e):
        self.h = True
        self.update()

    def leaveEvent(self, e):
        self.h = False
        self.update()

    def paintEvent(self, _):
        p = QPainter(self)
        p.setPen(QColor("#ffffff" if self.h else "#b8a6ff"))
        p.setFont(self.font())
        p.drawText(self.rect(), Qt.AlignCenter, self.text())
        if self.h:
            p.drawLine(2, self.height() - 3, self.width() - 2, self.height() - 3)
        p.end()


# ---------------------------------------------------------------------- the window

class Shell(QWidget):
    """Frameless rounded window with a shadow, the sky, a drag area and page slides."""

    def __init__(self, title):
        super().__init__()
        Theme.set("midnight", ACCENT)
        QApplication.instance().setStyleSheet(stylesheet())
        self.setWindowTitle(title)
        self.setWindowFlags(Qt.FramelessWindowHint | Qt.Window)
        self.setAttribute(Qt.WA_TranslucentBackground)
        self.setFixedSize(W + 2 * SHADOW, H + 2 * SHADOW)
        self.sky = _Sky()
        self.card = QRectF(SHADOW, SHADOW, W, H)
        self.content = QWidget(self)
        self.content.setGeometry(SHADOW, SHADOW + 40, W, H - 40)
        self.pages = {}
        self.current = None
        self._drag = None
        self.opacity = 0.0
        self.rise = 1.0
        # title bar
        icon = QLabel(self)
        icon.setPixmap(logo_pixmap(18))
        icon.move(SHADOW + 18, SHADOW + 13)
        name = QLabel(title, self)
        name.setStyleSheet("color: rgba(255,255,255,0.72); font-size: 12px; font-weight: 700;")
        name.move(SHADOW + 44, SHADOW + 12)
        name.adjustSize()
        self.min_btn = _WinButton("min", self)
        self.min_btn.move(SHADOW + W - 92, SHADOW + 8)
        self.min_btn.clicked.connect(self.showMinimized)
        self.close_btn = _WinButton("close", self)
        self.close_btn.move(SHADOW + W - 50, SHADOW + 8)
        self.close_btn.clicked.connect(self.close)
        self.timer = QTimer(self, interval=16)
        self.timer.timeout.connect(self._frame)
        self.timer.start()
        self.bars = []
        self.logos = []

    def _frame(self):
        if self.isMinimized() or not self.isVisible():
            return
        for b in self.bars:
            if b.isVisible():
                b.tick()
        for lg in self.logos:
            if lg.isVisible():
                lg.update()
        self.update(self.card.toRect())

    # ---- looks

    def paintEvent(self, _):
        p = QPainter(self)
        p.setRenderHint(QPainter.Antialiasing)
        r = self.card
        for i, a in ((18, 8), (12, 14), (7, 22), (3, 34)):
            path = QPainterPath()
            path.addRoundedRect(r.adjusted(-i, -i + 6, i, i + 6), RADIUS + i, RADIUS + i)
            p.fillPath(path, QColor(0, 0, 0, a))
        clip = QPainterPath()
        clip.addRoundedRect(r, RADIUS, RADIUS)
        p.setClipPath(clip)
        self.sky.paint(p, r)
        p.setClipping(False)
        p.setBrush(Qt.NoBrush)
        p.setPen(QPen(QColor(255, 255, 255, 26), 1))
        p.drawRoundedRect(r.adjusted(0.5, 0.5, -0.5, -0.5), RADIUS, RADIUS)
        p.end()

    def showEvent(self, e):
        super().showEvent(e)
        if self.opacity == 0.0:
            self.setWindowOpacity(0.0)
            start = self.pos()
            self._show = _anim(self, 380, 0, 1, QEasingCurve.OutCubic,
                               lambda v: (self.setWindowOpacity(v), self.move(start + QPoint(0, int(16 * (1 - v))))))
            self.opacity = 1.0

    def close(self):
        if self.current == "installing":
            return False  # let it finish; it only takes a moment
        if getattr(self, "_closing", False):
            return super().close()
        self._closing = True
        start = self.pos()
        self._hide = _anim(self, 200, 1, 0, QEasingCurve.InCubic,
                           lambda v: (self.setWindowOpacity(v), self.move(start + QPoint(0, int(10 * (1 - v))))),
                           done=lambda: QWidget.close(self))
        return True

    # ---- dragging the window by its top

    def mousePressEvent(self, e):
        if e.button() == Qt.LeftButton and e.position().y() < SHADOW + 48:
            handle = self.windowHandle()
            if handle is not None and handle.startSystemMove():
                return
            self._drag = e.globalPosition().toPoint() - self.pos()

    def mouseMoveEvent(self, e):
        if self._drag is not None:
            self.move(e.globalPosition().toPoint() - self._drag)

    def mouseReleaseEvent(self, e):
        self._drag = None

    # ---- pages

    def add_page(self, key, widget):
        widget.setParent(self.content)
        widget.setGeometry(0, 0, W, H - 40)
        widget.hide()
        self.pages[key] = widget
        return widget

    def show_page(self, key, direction=1):
        new = self.pages[key]
        old = self.pages.get(self.current) if self.current else None
        self.current = key
        new.setGeometry(0, 0, W, H - 40)
        if old is None or old is new:
            new.show()
            return
        fx_new, fx_old = QGraphicsOpacityEffect(new), QGraphicsOpacityEffect(old)
        new.setGraphicsEffect(fx_new)
        old.setGraphicsEffect(fx_old)
        fx_new.setOpacity(0.0)
        new.show()
        new.raise_()
        dx = 60 * direction

        def step(v):
            try:
                fx_old.setOpacity(1 - v)
                old.move(int(-dx * v), 0)
                fx_new.setOpacity(v)
                new.move(int(dx * (1 - v)), 0)
            except RuntimeError:
                pass

        def done():
            try:
                old.hide()
                old.move(0, 0)
                old.setGraphicsEffect(None)
                new.setGraphicsEffect(None)
            except RuntimeError:
                pass
        self._slide = _anim(self, 420, 0, 1, QEasingCurve.OutCubic, step, done)


def _center_col(page):
    v = QVBoxLayout(page)
    v.setContentsMargins(56, 6, 56, 28)
    v.setSpacing(0)
    return v


def _title(text, px=44, spacing=4.0):
    lab = QLabel(text)
    lab.setFont(_font(px, QFont.Black, spacing))
    lab.setAlignment(Qt.AlignCenter)
    lab.setStyleSheet(f"color: #ffffff; font-size: {px}px; font-weight: 900;")  # (the app style sets sizes)
    return lab


def _sub(text, px=14, color="rgba(255,255,255,0.62)"):
    lab = QLabel(text)
    lab.setWordWrap(True)
    lab.setAlignment(Qt.AlignCenter)
    lab.setStyleSheet(f"color: {color}; font-size: {px}px;")
    return lab


def _option(text, checked):
    from .widgets import Toggle
    w = QWidget()
    h = QHBoxLayout(w)
    h.setContentsMargins(0, 0, 0, 0)
    h.setSpacing(10)
    t = Toggle(checked)
    lab = QLabel(text)
    lab.setStyleSheet("color: rgba(255,255,255,0.8); font-size: 13px; font-weight: 600;")
    h.addWidget(t)
    h.addWidget(lab)
    w.toggle = t
    lab.mousePressEvent = lambda e: t.toggle()
    lab.setCursor(Qt.PointingHandCursor)
    return w


class _Worker(QObject):
    progress = Signal(float, str)
    done = Signal(str)

    def __init__(self, fn):
        super().__init__()
        self.fn = fn

    def run(self):
        try:
            self.fn(lambda d, t, msg: self.progress.emit(d / max(1, t), msg))
            self.done.emit("")
        except Exception as e:  # shown on the error page
            import traceback
            traceback.print_exc()
            self.done.emit(str(e) or e.__class__.__name__)


def _run_in_thread(owner, fn, on_progress, on_done):
    th = QThread(owner)
    wk = _Worker(fn)
    wk.moveToThread(th)
    th.started.connect(wk.run)
    wk.progress.connect(on_progress)
    wk.done.connect(on_done)
    wk.done.connect(th.quit)
    owner._thread, owner._worker = th, wk
    th.start()


# ---------------------------------------------------------------------- installer

class InstallerWindow(Shell):

    def __init__(self, version, payload, target=None, desktop=True, startup=False, auto=False, launch_after=True,
                 backend=None):
        super().__init__("Peregrine Setup")
        from .. import winsetup
        from .modern import PillButton
        self.version, self.payload = version, payload
        self.backend = backend or winsetup
        have = self.backend.installed() if hasattr(self.backend, "installed") else {}
        self.target = Path(target) if target else (have.get("dir") or self.backend.default_dir())
        self.old_version = have.get("version", "") if have else ""
        self.launch_after = launch_after
        self.ok = False

        # -- welcome
        pg = QWidget()
        v = _center_col(pg)
        self.logo = Logo(92)
        self.logos.append(self.logo)
        v.addWidget(self.logo, 0, Qt.AlignHCenter)
        v.addSpacing(2)
        v.addWidget(_title("PEREGRINE"))
        lab = QLabel("LAUNCHER")
        lab.setFont(_font(13, QFont.ExtraBold, 7.0))
        lab.setAlignment(Qt.AlignCenter)
        lab.setStyleSheet("color: #b8a6ff; font-size: 13px; font-weight: 800;")
        v.addWidget(lab)
        v.addSpacing(10)
        if have and self.old_version and self.old_version != version:
            what = f"Updates Peregrine from {self.old_version} to {version}. Your games and settings stay as they are."
        elif have:
            what = f"Peregrine {version} is already installed. Reinstalling keeps your games and settings."
        else:
            what = "Fast to launch. Faster to play. Minecraft: Java Edition with the good stuff built in."
        v.addWidget(_sub(what))
        v.addStretch(1)
        verb = "UPDATE" if have and self.old_version and self.old_version != version else (
            "REINSTALL" if have else "INSTALL")
        self.go = PillButton(verb, "bolt", "primary", big=True)
        self.go.setMinimumWidth(240)
        self.go.clicked.connect(self.start)
        v.addWidget(self.go, 0, Qt.AlignHCenter)
        v.addSpacing(18)
        opts = QHBoxLayout()
        opts.setSpacing(26)
        opts.addStretch()
        self.opt_desktop = _option("Desktop shortcut", desktop)
        self.opt_startup = _option("Start with Windows", startup)
        opts.addWidget(self.opt_desktop)
        opts.addWidget(self.opt_startup)
        opts.addStretch()
        v.addLayout(opts)
        v.addSpacing(12)
        where = QHBoxLayout()
        where.addStretch()
        self.where = QLabel()
        self.where.setStyleSheet("color: rgba(255,255,255,0.42); font-size: 12px;")
        where.addWidget(self.where)
        change = LinkButton("Change")
        change.clicked.connect(self.pick_folder)
        where.addWidget(change)
        where.addStretch()
        v.addLayout(where)
        self._show_where()
        self.add_page("welcome", pg)

        # -- installing
        pg = QWidget()
        v = _center_col(pg)
        v.addStretch(1)
        self.work_logo = Logo(76)
        self.work_logo.spin = 1.0
        self.logos.append(self.work_logo)
        v.addWidget(self.work_logo, 0, Qt.AlignHCenter)
        v.addSpacing(10)
        v.addWidget(_title("INSTALLING", 30, 3.0))
        v.addSpacing(6)
        self.status = _sub("Getting ready")
        v.addWidget(self.status)
        v.addSpacing(22)
        self.bar = GlowBar()
        self.bars.append(self.bar)
        row = QHBoxLayout()
        row.addSpacing(40)
        row.addWidget(self.bar, 1)
        self.pct = QLabel("0%")
        self.pct.setFixedWidth(52)
        self.pct.setAlignment(Qt.AlignRight | Qt.AlignVCenter)
        self.pct.setStyleSheet("color: #ffffff; font-size: 13px; font-weight: 800;")
        row.addWidget(self.pct)
        row.addSpacing(40)
        v.addLayout(row)
        v.addStretch(1)
        v.addWidget(_sub("Your worlds and settings are never touched.", 12, "rgba(255,255,255,0.38)"))
        self.add_page("installing", pg)
        self._pct_timer = QTimer(self, interval=33)
        self._pct_timer.timeout.connect(lambda: self.pct.setText(f"{round(self.bar.shown * 100)}%"))

        # -- done
        pg = QWidget()
        v = _center_col(pg)
        v.addStretch(1)
        self.check = CheckMark()
        v.addWidget(self.check, 0, Qt.AlignHCenter)
        v.addSpacing(6)
        v.addWidget(_title("YOU'RE ALL SET", 30, 3.0))
        v.addSpacing(8)
        v.addWidget(_sub("Peregrine Launcher is installed. It's in your Start menu"
                         + (" and on your desktop." if desktop else ".")))
        self.done_sub = v.itemAt(v.count() - 1).widget()
        v.addStretch(1)
        btns = QHBoxLayout()
        btns.setSpacing(12)
        btns.addStretch()
        close = PillButton("Close", None, "ghost")
        close.clicked.connect(self.close)
        self.launch_btn = PillButton("Launch Peregrine", "play", "primary", big=True)
        self.launch_btn.clicked.connect(self.launch)
        btns.addWidget(close)
        btns.addWidget(self.launch_btn)
        btns.addStretch()
        v.addLayout(btns)
        self.add_page("done", pg)

        # -- error
        pg = QWidget()
        v = _center_col(pg)
        v.addStretch(1)
        self.err_mark = CheckMark("#e5676b", "alert")
        v.addWidget(self.err_mark, 0, Qt.AlignHCenter)
        v.addWidget(_title("COULDN'T INSTALL", 28, 3.0))
        v.addSpacing(8)
        self.err = _sub("")
        self.err.setTextInteractionFlags(Qt.TextSelectableByMouse)
        v.addWidget(self.err)
        v.addStretch(1)
        btns = QHBoxLayout()
        btns.setSpacing(12)
        btns.addStretch()
        close = PillButton("Close", None, "ghost")
        close.clicked.connect(self.close)
        retry = PillButton("Try again", "refresh", "primary", big=True)
        retry.clicked.connect(self.start)
        btns.addWidget(close)
        btns.addWidget(retry)
        btns.addStretch()
        v.addLayout(btns)
        self.add_page("error", pg)

        self.show_page("welcome")
        if auto:
            QTimer.singleShot(500, self.start)

    def _show_where(self):
        t = str(self.target)
        if len(t) > 52:
            t = "…" + t[-50:]
        self.where.setText(f"Installs to  {t}   ·")

    def pick_folder(self):
        d = QFileDialog.getExistingDirectory(self, "Install Peregrine in", str(self.target.parent))
        if d:
            p = Path(d)
            self.target = p if p.name.lower() == "peregrine" else p / "Peregrine"
            self._show_where()

    def start(self):
        self.bar.target = self.bar.shown = 0.0
        self.status.setText("Getting ready")
        self._pct_timer.start()
        self.show_page("installing")
        desktop, startup = self.opt_desktop.toggle.isChecked(), self.opt_startup.toggle.isChecked()
        self.done_sub.setText("Peregrine Launcher is installed. It's in your Start menu"
                              + (" and on your desktop." if desktop else "."))

        def work(progress):
            self.backend.install(self.payload, self.target, self.version, desktop, startup, progress)
        _run_in_thread(self, work, self._progress, self._finished)

    def _progress(self, frac, msg):
        self.bar.set_value(frac)
        self.status.setText(msg)

    def _finished(self, error):
        if error:
            self._pct_timer.stop()
            self.err.setText(f"{error}\n\nClose Peregrine if it's open, then try again.")
            self.show_page("error")
            QTimer.singleShot(250, self.err_mark.play)
            return
        self.ok = True
        self.bar.set_value(1.0)

        def finish():
            self._pct_timer.stop()
            self.pct.setText("100%")
            self.show_page("done")
            QTimer.singleShot(260, self.check.play)
            if getattr(self, "auto_launch", False):
                QTimer.singleShot(1300, self.launch)
        QTimer.singleShot(550, finish)  # let the bar glide to the end first

    def launch(self):
        try:
            self.backend.launch(self.target)
        except Exception as e:
            print(f"couldn't start Peregrine: {e}")
        self.close()


# ---------------------------------------------------------------------- uninstaller

class UninstallerWindow(Shell):

    def __init__(self, app_dir, backend=None):
        super().__init__("Uninstall Peregrine")
        from .. import winsetup
        from .modern import PillButton
        self.app_dir = Path(app_dir)
        self.backend = backend or winsetup
        self.done_ok = False

        pg = QWidget()
        v = _center_col(pg)
        v.addStretch(1)
        mark = CheckMark("#e5676b", "trash")
        mark.t = 1.0
        v.addWidget(mark, 0, Qt.AlignHCenter)
        v.addWidget(_title("UNINSTALL PEREGRINE?", 28, 2.5))
        v.addSpacing(8)
        v.addWidget(_sub("Removes the launcher, its shortcuts and its entry in Apps. "
                         "Your instances, worlds and settings stay unless you switch on the option below."))
        v.addSpacing(18)
        self.opt_data = _option("Also delete my instances, worlds and settings", False)
        row = QHBoxLayout()
        row.addStretch()
        row.addWidget(self.opt_data)
        row.addStretch()
        v.addLayout(row)
        self.warn = _sub("This deletes every world in your Peregrine instances. It can't be undone.", 12, "#ff8a8e")
        self.warn.setVisible(False)
        v.addSpacing(6)
        v.addWidget(self.warn)
        self.opt_data.toggle.toggled.connect(self.warn.setVisible)
        v.addStretch(1)
        btns = QHBoxLayout()
        btns.setSpacing(12)
        btns.addStretch()
        cancel = PillButton("Cancel", None, "ghost")
        cancel.clicked.connect(self.close)
        go = PillButton("Uninstall", "trash", "danger", big=True)
        go.clicked.connect(self.start)
        btns.addWidget(cancel)
        btns.addWidget(go)
        btns.addStretch()
        v.addLayout(btns)
        self.add_page("confirm", pg)

        pg = QWidget()
        v = _center_col(pg)
        v.addStretch(1)
        self.check = CheckMark()
        v.addWidget(self.check, 0, Qt.AlignHCenter)
        v.addWidget(_title("PEREGRINE WAS REMOVED", 28, 2.5))
        v.addSpacing(8)
        self.bye = _sub("Thanks for flying with Peregrine.")
        v.addWidget(self.bye)
        v.addStretch(1)
        close = PillButton("Close", None, "primary", big=True)
        close.clicked.connect(self.close)
        v.addWidget(close, 0, Qt.AlignHCenter)
        self.add_page("done", pg)

        self.add_page("error", QWidget())
        self.show_page("confirm")

    def start(self):
        remove = self.opt_data.toggle.isChecked()
        try:
            self.backend.uninstall(self.app_dir, remove)
        except Exception as e:
            self.bye.setText(f"Some parts couldn't be removed: {e}")
        if remove:
            self.bye.setText("Your instances, worlds and settings were deleted too.")
        self.done_ok = True
        self.show_page("done")
        QTimer.singleShot(260, self.check.play)


# ---------------------------------------------------------------------- entry points

def _app(argv):
    app = QApplication.instance() or QApplication(argv[:1])
    app.setApplicationDisplayName("Peregrine")
    from PySide6.QtGui import QIcon, QPixmap
    pm = QPixmap(render_app_icon(256))
    app.setWindowIcon(QIcon(pm))
    return app


def run_uninstaller(app_dir, quiet=False) -> int:
    from .. import winsetup
    if quiet:
        winsetup.uninstall(Path(app_dir), False)
        return 0
    app = _app(sys.argv)
    win = UninstallerWindow(app_dir)
    win.show()
    app.exec()
    return 0


def run_installer(version, payload, args) -> int:
    """args: the installer's command line (the /SILENT style switches it understands)."""
    from .. import winsetup
    flags = {a.split("=", 1)[0].upper(): (a.split("=", 1)[1] if "=" in a else "") for a in args}
    tasks = flags.get("/TASKS", "desktopicon").lower().split(",") if "/TASKS" in flags else ["desktopicon"]
    target = flags.get("/DIR") or None
    relaunch = "/RESTARTAPPLICATIONS" in flags or "/LAUNCH" in flags
    if "/VERYSILENT" in flags:
        try:
            winsetup.install(payload, Path(target) if target else (winsetup.installed().get("dir") or winsetup.default_dir()),
                             version, "desktopicon" in tasks, "startup" in tasks)
        except Exception as e:
            print(f"install failed: {e}", file=sys.stderr)
            return 1
        if relaunch:
            winsetup.launch(Path(target) if target else (winsetup.installed().get("dir") or winsetup.default_dir()))
        return 0
    app = _app(sys.argv)
    win = InstallerWindow(version, payload, target, "desktopicon" in tasks, "startup" in tasks,
                          auto="/SILENT" in flags)
    win.auto_launch = "/SILENT" in flags and relaunch
    win.show()
    app.exec()
    return 0 if win.ok or not ("/SILENT" in flags) else 1


def screenshots(out: Path, version="1.0.0") -> None:
    """Saves a picture of every step (used by the build checks)."""

    class Fake:
        def installed(self):
            return {}

        def default_dir(self):
            return Path(os.environ.get("LOCALAPPDATA", str(Path.home()))) / "Programs" / "Peregrine"

        def install(self, *a, **k):
            pass

        def launch(self, *a):
            pass

        def uninstall(self, *a):
            pass

    out.mkdir(parents=True, exist_ok=True)
    app = _app(sys.argv)

    def pump(s):
        end = time.time() + s
        while time.time() < end:
            app.processEvents()
            time.sleep(0.016)

    win = InstallerWindow(version, None, backend=Fake())
    win.show()
    pump(1.2)
    win.grab().save(str(out / "setup-1-welcome.png"))
    win._pct_timer.start()
    win.show_page("installing")
    pump(0.6)
    win._progress(0.42, "Copying files  612 / 1,480")
    pump(1.0)
    win.grab().save(str(out / "setup-2-installing.png"))
    win._finished("")
    pump(2.2)
    win.grab().save(str(out / "setup-3-done.png"))
    win.current = "done"
    win._finished("Access is denied: 'Peregrine.exe'")
    pump(1.6)
    win.grab().save(str(out / "setup-4-error.png"))
    QWidget.close(win)
    un = UninstallerWindow(Path("."), backend=Fake())
    un.show()
    pump(1.0)
    un.opt_data.toggle.setChecked(True)
    pump(0.5)
    un.grab().save(str(out / "uninstall-1-confirm.png"))
    un.start()
    pump(1.6)
    un.grab().save(str(out / "uninstall-2-done.png"))
    QWidget.close(un)
