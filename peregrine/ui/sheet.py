"""Pop-ups that match the launcher: a card that springs up over the window while the rest
dims behind it, with the launcher's own pill buttons. Used for every question, notice and
form (delete instance, sign in, new instance...).

A Sheet lives inside the main window rather than as a separate window, so it always sits
right over the launcher, follows it when it moves or resizes, and never shows up as a
plain system box. It keeps QDialog's handy API: exec() returns 1 (OK) or 0 (cancelled),
accept()/reject()/done(), and the accepted/rejected/finished signals.
"""

from PySide6.QtCore import (
    QEasingCurve, QEvent, QEventLoop, QObject, QPoint, QPointF, QRect, QRectF, QSize, Qt, QTimer,
    QVariantAnimation, Signal,
)
from PySide6.QtGui import QColor, QPainter, QPainterPath, QPen, QPixmap, QRegion
from PySide6.QtWidgets import (
    QAbstractButton, QApplication, QFrame, QHBoxLayout, QLabel, QLineEdit, QVBoxLayout, QWidget,
)

from .theme import Theme, icon_pixmap

MARGIN = 28          # space kept between a sheet and the window's edges
OPEN_MS, CLOSE_MS = 260, 170


def _animations_on() -> bool:
    try:
        from .modern import animations_on
        return animations_on()
    except Exception:
        return True


def _dark() -> bool:
    return Theme.name != "light"


class _Backdrop(QWidget):
    """Dims the launcher behind a sheet, draws the sheet's soft shadow, and (while the
    sheet springs in or out) draws the sheet itself from a snapshot, scaled and faded."""

    clicked = Signal()

    def __init__(self, host: QWidget):
        super().__init__(host)
        self.level = 0.0          # 0..1: how far the sheet is in
        self.snapshot = None      # the sheet's picture while it animates
        self.card = QRect()
        self.scale = 1.0
        self.setAttribute(Qt.WA_NoSystemBackground)
        self.setFocusPolicy(Qt.NoFocus)

    def mousePressEvent(self, e):
        if not self.card.contains(e.position().toPoint()):
            self.clicked.emit()
        e.accept()

    def wheelEvent(self, e):
        e.accept()  # don't scroll the page behind

    def paintEvent(self, _):
        p = QPainter(self)
        p.setRenderHint(QPainter.Antialiasing)
        p.setRenderHint(QPainter.SmoothPixmapTransform)
        lv = max(0.0, min(1.0, self.level))
        p.fillRect(self.rect(), QColor(4, 6, 12, int((150 if _dark() else 95) * lv)))
        if self.card.isNull():
            return
        r = QRectF(self.card)
        c = r.center()
        s = self.scale
        r = QRectF(c.x() - r.width() * s / 2, c.y() - r.height() * s / 2 + (1 - lv) * 14,
                   r.width() * s, r.height() * s)
        # a soft shadow under the card
        for i, a in ((26, 10), (16, 16), (8, 24), (3, 30)):
            path = QPainterPath()
            path.addRoundedRect(r.adjusted(-i, -i + 10, i, i + 10), 22 + i, 22 + i)
            p.fillPath(path, QColor(0, 0, 0, int(a * lv)))
        if self.snapshot is not None:
            p.setOpacity(lv)
            p.drawPixmap(r, self.snapshot, QRectF(self.snapshot.rect()))


class _CloseButton(QAbstractButton):
    def __init__(self, parent):
        super().__init__(parent)
        self.setFixedSize(32, 32)
        self.setCursor(Qt.PointingHandCursor)
        self.setToolTip("Close")
        self.setFocusPolicy(Qt.NoFocus)
        self._h = 0.0
        self._anim = QVariantAnimation(self, duration=140, easingCurve=QEasingCurve.OutCubic)
        self._anim.valueChanged.connect(self._set)

    def _set(self, v):
        self._h = float(v)
        self.update()

    def _go(self, to):
        self._anim.stop()
        self._anim.setStartValue(self._h)
        self._anim.setEndValue(to)
        self._anim.start()

    def enterEvent(self, e):
        self._go(1.0)

    def leaveEvent(self, e):
        self._go(0.0)

    def paintEvent(self, _):
        p = QPainter(self)
        p.setRenderHint(QPainter.Antialiasing)
        base = 255 if _dark() else 20
        c = QColor(base, base, base, int(10 + 26 * self._h))
        path = QPainterPath()
        path.addEllipse(QRectF(self.rect()).adjusted(1, 1, -1, -1))
        p.fillPath(path, c)
        p.translate(16, 16)
        p.rotate(90 * self._h)
        size = 18
        col = Theme.p["text"] if self._h > 0.5 else Theme.p["muted"]
        p.drawPixmap(QPointF(-size / 2, -size / 2), icon_pixmap("x", col, size))
        p.end()


class _Guard(QObject):
    """While a sheet is up, keeps the keyboard and mouse away from the launcher behind it
    (drop-down lists and menus, which are their own little windows, still work)."""

    def __init__(self, sheet):
        super().__init__(sheet)
        self.sheet = sheet

    def eventFilter(self, obj, e):
        t = e.type()
        if t in (QEvent.KeyPress, QEvent.KeyRelease, QEvent.ShortcutOverride, QEvent.MouseButtonPress,
                 QEvent.MouseButtonDblClick, QEvent.FocusIn) and isinstance(obj, QWidget):
            sheet = self.sheet
            if not sheet.isVisible() or sheet._closing:
                return False
            if obj.window() is not sheet.host:
                return False
            if obj is sheet or sheet.isAncestorOf(obj) or obj is sheet.backdrop:
                return False
            top = _top_sheet(sheet.host)
            if top is not sheet:
                return False  # a sheet on top of this one guards instead
            if t == QEvent.FocusIn:
                QTimer.singleShot(0, sheet._focus_first)
                return False
            if t == QEvent.KeyPress:
                sheet.keyPressEvent(e)  # Esc and Enter still work if focus wandered off
            return True
        return False


def _top_sheet(host):
    stack = getattr(host, "_sheets", [])
    return stack[-1] if stack else None


class Sheet(QFrame):
    """A pop-up card over the launcher. Subclass it like a QDialog: build your layout on
    self, call exec() and check the result."""

    accepted = Signal()
    rejected = Signal()
    finished = Signal(int)

    Accepted, Rejected = 1, 0

    def __init__(self, parent=None, closable: bool = True, dismiss_outside: bool = False):
        host = parent.window() if parent is not None else QApplication.activeWindow()
        self.host = host
        self.standalone = host is None
        super().__init__(host, objectName="sheet")
        self.setAttribute(Qt.WA_StyledBackground)
        if self.standalone:
            # no launcher window to sit in (a crash before it opened): a small window of its own
            self.setWindowFlag(Qt.Dialog)
            self.setWindowModality(Qt.ApplicationModal)
            self.backdrop = None
        else:
            self.backdrop = _Backdrop(host)
            self.backdrop.hide()
            self.backdrop.clicked.connect(self._outside)
        self.dismiss_outside = dismiss_outside
        self._result = 0
        self._loop = None
        self._closing = False
        self._shown_once = False
        self._want = QSize(460, 0)
        self._anim = QVariantAnimation(self)
        self._anim.valueChanged.connect(self._step)
        self._anim.finished.connect(self._anim_done)
        self._guard = _Guard(self)
        self._apply_style()
        self.hide()
        self.close_btn = _CloseButton(self) if closable else None
        if self.close_btn:
            self.close_btn.clicked.connect(self.reject)
        if host is not None:
            host.installEventFilter(self)

    # ------------------------------------------------------------------ looks

    def _apply_style(self):
        p, a = Theme.p, Theme.accent
        top = QColor(p["surface"]).lighter(112 if _dark() else 100).name()
        edge = "rgba(255,255,255,0.10)" if _dark() else "rgba(20,24,40,0.10)"
        self.setStyleSheet(
            f"#sheet {{ background: qlineargradient(x1:0, y1:0, x2:0, y2:1, stop:0 {top}, stop:1 {p['surface']});"
            f" border: 1px solid {edge}; border-radius: 22px; }}"
            f"#sheet QLabel#sheetTitle {{ font-size: 20px; font-weight: 800; }}"
            f"#sheet QLabel#sheetText {{ color: {p['muted']}; font-size: 14px; }}")
        self._accent = a

    # ------------------------------------------------------------------ QDialog-like API

    def resize(self, *args):
        size = args[0] if len(args) == 1 else QSize(int(args[0]), int(args[1]))
        self._want = QSize(max(260, size.width()), max(0, size.height()))
        if self.isVisible():
            self._place()

    def result(self) -> int:
        return self._result

    def setResult(self, r: int):
        self._result = r

    def exec(self) -> int:
        self.show()
        if self.isVisible() or self._anim.state() == QVariantAnimation.Running:
            self._loop = QEventLoop(self)
            self._loop.exec()
            self._loop = None
        return self._result

    exec_ = exec

    def open(self):
        self.show()

    def accept(self):
        self.done(self.Accepted)

    def reject(self):
        self.done(self.Rejected)

    def done(self, r: int):
        if self._closing:
            return
        self._result = r
        self._closing = True
        if r == self.Accepted:
            self.accepted.emit()
        else:
            self.rejected.emit()
        self._animate_out()

    def show(self):
        if self.standalone:
            self.adjustSize()
            super().show()
            return
        if self.isVisible() and not self._closing:
            return
        self._closing = False
        stack = self.host.__dict__.setdefault("_sheets", [])
        if self not in stack:
            stack.append(self)
        QApplication.instance().installEventFilter(self._guard)
        self._apply_style()
        self._place()
        self.backdrop.setGeometry(self.host.rect())
        self.backdrop.raise_()
        self.backdrop.show()
        self.raise_()
        if self.close_btn:
            self.close_btn.raise_()
        if _animations_on():
            # snapshot the card and let the backdrop draw it springing in
            self.backdrop.snapshot = self._snapshot()
            self.backdrop.card = self.geometry()
            self.backdrop.level, self.backdrop.scale = 0.0, 0.94
            self._run(0.0, 1.0, OPEN_MS, QEasingCurve.OutBack, opening=True)
        else:
            self.backdrop.level = 1.0
            self.backdrop.card = self.geometry()
            self.backdrop.snapshot = None
            super().show()
            self._focus_first()
        self._shown_once = True

    def close(self):
        if self.isVisible() or (self.backdrop and self.backdrop.isVisible()):
            if not self._closing:
                self._result = self.Rejected
                self._closing = True
            self._finish(now=True)
        return True

    def keyPressEvent(self, e):
        k = e.key()
        if k == Qt.Key_Escape:
            if self.close_btn is not None or self.dismiss_outside:
                self.reject()
            e.accept()
            return
        if k in (Qt.Key_Return, Qt.Key_Enter):
            focus = QApplication.focusWidget()
            from PySide6.QtWidgets import QPlainTextEdit, QTextEdit
            if isinstance(focus, (QPlainTextEdit, QTextEdit)):
                return super().keyPressEvent(e)
            btn = self.default_button()
            if btn is not None and btn.isEnabled() and btn.isVisible():
                btn.click()
                e.accept()
                return
        super().keyPressEvent(e)

    def default_button(self):
        btn = getattr(self, "_default", None)
        if btn is not None:
            return btn
        for b in self.findChildren(QAbstractButton):
            if getattr(b, "kind", None) in ("primary", "danger") or b.objectName() == "accent":
                if b.isVisible():
                    return b
        return None

    def set_default(self, button):
        self._default = button

    # ------------------------------------------------------------------ placement

    def _place(self):
        if self.standalone or self.host is None:
            return
        hw, hh = self.host.width(), self.host.height()
        w = min(self._want.width(), hw - 2 * MARGIN)
        self.setFixedWidth(max(240, w))
        lay = self.layout()
        if lay is not None:
            lay.activate()
        h = self._want.height()
        if not h:
            if lay is not None:
                h = lay.totalHeightForWidth(self.width()) if lay.hasHeightForWidth() else lay.totalSizeHint().height()
                h = max(h, lay.totalMinimumSize().height())
            else:
                h = self.sizeHint().height()
        h = max(120, min(h, hh - 2 * MARGIN))
        if self.height() != h:
            self.setFixedHeight(h)
        self.move((hw - self.width()) // 2, max(MARGIN, (hh - self.height()) // 2 - 8))
        if self.close_btn:
            self.close_btn.move(self.width() - self.close_btn.width() - 14, 14)
        if self.backdrop is not None:
            self.backdrop.setGeometry(self.host.rect())
            self.backdrop.card = self.geometry()
            self.backdrop.update()

    def eventFilter(self, obj, e):
        if obj is self.host and e.type() == QEvent.Resize and self.isVisible():
            self._place()
        return False

    def event(self, e):
        if e.type() == QEvent.LayoutRequest and self.isVisible() and not self._want.height():
            QTimer.singleShot(0, self._place)
        return super().event(e)

    # ------------------------------------------------------------------ animation

    def _snapshot(self) -> QPixmap:
        # without the window background, so the rounded corners stay see-through
        dpr = self.devicePixelRatioF() or 1.0
        pm = QPixmap(max(1, int(self.width() * dpr)), max(1, int(self.height() * dpr)))
        pm.setDevicePixelRatio(dpr)
        pm.fill(Qt.transparent)
        self.render(pm, QPoint(), QRegion(), QWidget.RenderFlag.DrawChildren)
        return pm

    def _run(self, start, end, ms, curve, opening):
        self._opening = opening
        self._anim.stop()
        self._anim.setDuration(ms)
        self._anim.setEasingCurve(curve)
        self._anim.setStartValue(float(start))
        self._anim.setEndValue(float(end))
        self._anim.start()

    def _step(self, v):
        b = self.backdrop
        if b is None:
            return
        v = float(v)
        if self._opening:
            b.level = min(1.0, max(0.0, v))
            b.scale = 0.94 + 0.06 * v  # OutBack overshoots a hair past 1: a gentle spring
        else:
            b.level = v
            b.scale = 0.96 + 0.04 * v
        b.update()

    def _anim_done(self):
        if self._opening:
            b = self.backdrop
            b.level, b.scale, b.snapshot = 1.0, 1.0, None
            b.update()
            if not self._closing:
                QFrame.show(self)
                self.raise_()
                self._focus_first()
        else:
            self._finish(now=True)

    def _animate_out(self):
        if self.standalone or self.backdrop is None:
            self._finish(now=True)
            return
        if not _animations_on() or not self.backdrop.isVisible():
            self._finish(now=True)
            return
        if self.isVisible():
            self.backdrop.snapshot = self._snapshot()
            self.backdrop.card = self.geometry()
        start = self.backdrop.level
        QFrame.hide(self)
        self._run(start, 0.0, CLOSE_MS, QEasingCurve.InCubic, opening=False)

    def _finish(self, now=False):
        self._anim.stop()
        QFrame.hide(self)
        if self.backdrop is not None:
            self.backdrop.hide()
            self.backdrop.snapshot = None
        stack = getattr(self.host, "_sheets", []) if self.host is not None else []
        if self in stack:
            stack.remove(self)
        if not stack:
            QApplication.instance().removeEventFilter(self._guard)
        else:
            QApplication.instance().removeEventFilter(self._guard)
            QApplication.instance().installEventFilter(stack[-1]._guard)
        result = self._result
        self.finished.emit(result)
        if self._loop is not None:
            self._loop.quit()
        if self.host is not None and not stack:
            self.host.activateWindow()

    def _outside(self):
        if self.dismiss_outside:
            self.reject()
        else:
            self._nudge()

    def _nudge(self):
        """Clicked outside a form: a small shake says 'finish or cancel here first'."""
        if not _animations_on() or not self.isVisible():
            return
        x0 = self.x()
        anim = QVariantAnimation(self, duration=320, startValue=0.0, endValue=1.0)
        import math

        def step(t):
            t = float(t)
            self.move(x0 + int(math.sin(t * math.pi * 4) * 7 * (1 - t)), self.y())
        anim.valueChanged.connect(step)
        anim.finished.connect(lambda: self.move(x0, self.y()))
        anim.start()
        self._shake = anim

    def _focus_first(self):
        if not self.isVisible():
            return
        for w in self.findChildren(QWidget):
            if isinstance(w, QLineEdit) and w.isVisible() and w.isEnabled():
                w.setFocus()
                return
        b = self.default_button()
        (b or self).setFocus()


# ---------------------------------------------------------------------- ready-made pop-ups

ICONS = {"danger": ("trash", "danger"), "warning": ("alert", "#e8a33d"), "error": ("alert", "danger"),
         "info": ("info", None), "success": ("check", "#3ddc84"), "question": ("info", None)}


class _Badge(QWidget):
    def __init__(self, icon_name, color):
        super().__init__()
        self.icon_name, self.color = icon_name, color
        self.setFixedSize(52, 52)

    def paintEvent(self, _):
        p = QPainter(self)
        p.setRenderHint(QPainter.Antialiasing)
        c = QColor(self.color)
        fill = QColor(c)
        fill.setAlpha(38)
        path = QPainterPath()
        path.addRoundedRect(QRectF(self.rect()).adjusted(1, 1, -1, -1), 16, 16)
        p.fillPath(path, fill)
        ring = QColor(c)
        ring.setAlpha(80)
        p.setPen(QPen(ring, 1.2))
        p.drawPath(path)
        size = 26
        p.drawPixmap(QPointF((self.width() - size) / 2, (self.height() - size) / 2),
                     icon_pixmap(self.icon_name, c.name(), size))
        p.end()


class MessageSheet(Sheet):
    """A short question or notice: icon, title, text and one or two buttons."""

    def __init__(self, parent, title, text="", kind="info", ok="OK", cancel=None, field=None):
        super().__init__(parent, closable=False, dismiss_outside=cancel is not None or field is None)
        from .modern import PillButton
        icon_name, color = ICONS.get(kind, ICONS["info"])
        color = Theme.p["danger"] if color == "danger" else (color or Theme.accent)
        v = QVBoxLayout(self)
        v.setContentsMargins(28, 26, 28, 22)
        v.setSpacing(14)
        top = QHBoxLayout()
        top.setSpacing(16)
        top.addWidget(_Badge(icon_name, color), 0, Qt.AlignTop)
        col = QVBoxLayout()
        col.setSpacing(6)
        t = QLabel(title, objectName="sheetTitle")
        t.setWordWrap(True)
        col.addWidget(t)
        if text:
            body = QLabel(str(text), objectName="sheetText")
            body.setWordWrap(True)
            body.setTextInteractionFlags(Qt.TextSelectableByMouse)
            col.addWidget(body)
        top.addLayout(col, 1)
        v.addLayout(top)
        self.field = None
        if field is not None:
            self.field = QLineEdit(field.get("text", ""), placeholderText=field.get("placeholder", ""))
            v.addWidget(self.field)
        row = QHBoxLayout()
        row.setSpacing(10)
        row.addStretch()
        if cancel:
            c = PillButton(cancel, None, "ghost")
            c.clicked.connect(self.reject)
            row.addWidget(c)
        b = PillButton(ok, None, "danger" if kind == "danger" else "primary")
        b.clicked.connect(self.accept)
        row.addWidget(b)
        self.set_default(b)
        v.addLayout(row)
        self.resize(440, 0)
        if self.field is not None:
            self.field.textChanged.connect(lambda s: b.setEnabled(bool(s.strip())))
            b.setEnabled(bool(self.field.text().strip()))


def ask(parent, title, text="", ok="Yes", cancel="Cancel", kind="question") -> bool:
    """A yes/no question. kind='danger' gives a red button for things that can't be undone."""
    return MessageSheet(parent, title, text, kind, ok, cancel).exec() == Sheet.Accepted


def tell(parent, title, text="", kind="info", ok="OK"):
    """A notice with one button. kind: info, success, warning or error."""
    MessageSheet(parent, title, text, kind, ok).exec()


def get_text(parent, title, label="", placeholder="", text="", ok="OK"):
    """Asks for one line of text. Returns (text, True) or ("", False)."""
    m = MessageSheet(parent, title, label, "info", ok, "Cancel", field={"placeholder": placeholder, "text": text})
    ok_ = m.exec() == Sheet.Accepted
    return (m.field.text().strip(), True) if ok_ else ("", False)
