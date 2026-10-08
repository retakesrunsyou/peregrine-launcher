"""Custom controls: a sliding toggle switch and a segmented picker."""

from PySide6.QtCore import (Property, QEasingCurve, QPropertyAnimation, QRectF, QSize, Qt,
                            Signal)
from PySide6.QtGui import QColor, QPainter
from PySide6.QtWidgets import QAbstractButton, QButtonGroup, QHBoxLayout, QPushButton, QWidget

from .theme import Theme


class Toggle(QAbstractButton):
    """A squared-off switch. One click slides the knob across."""

    W, H, PAD = 42, 22, 3

    def __init__(self, checked: bool = False, parent=None):
        super().__init__(parent)
        self.setCheckable(True)
        self.setChecked(checked)
        self.setCursor(Qt.PointingHandCursor)
        self.setFocusPolicy(Qt.StrongFocus)
        self._pos = 1.0 if checked else 0.0
        self._anim = QPropertyAnimation(self, b"knob", self)
        self._anim.setDuration(140)
        self._anim.setEasingCurve(QEasingCurve.OutCubic)
        self.toggled.connect(self._slide)

    def sizeHint(self):
        return QSize(self.W, self.H)

    def _get(self):
        return self._pos

    def _set(self, v):
        self._pos = v
        self.update()

    knob = Property(float, _get, _set)

    def _slide(self, on):
        self._anim.stop()
        self._anim.setStartValue(self._pos)
        self._anim.setEndValue(1.0 if on else 0.0)
        self._anim.start()

    def paintEvent(self, _):
        p = QPainter(self)
        p.setRenderHint(QPainter.Antialiasing)
        p.setPen(Qt.NoPen)
        off, on = QColor(Theme.p["border"]), QColor(Theme.accent)
        t = self._pos
        track = QColor(int(off.red() + (on.red() - off.red()) * t),
                       int(off.green() + (on.green() - off.green()) * t),
                       int(off.blue() + (on.blue() - off.blue()) * t))
        if not self.isEnabled():
            track.setAlpha(110)
        p.setBrush(track)
        p.drawRoundedRect(QRectF(0, 0, self.W, self.H), 6, 6)
        k = self.H - 2 * self.PAD
        x = self.PAD + (self.W - 2 * self.PAD - k) * t
        p.setBrush(QColor("#ffffff"))
        p.drawRoundedRect(QRectF(x, self.PAD, k, k), 4, 4)
        if self.hasFocus():
            p.setBrush(Qt.NoBrush)
            p.setPen(QColor(Theme.accent))
            p.drawRoundedRect(QRectF(0.5, 0.5, self.W - 1, self.H - 1), 6, 6)
        p.end()

    def hitButton(self, pos):
        return self.rect().contains(pos)


class Segmented(QWidget):
    """A row of joined buttons where exactly one is selected."""
    changed = Signal(str)

    def __init__(self, options: list, current: str = None, parent=None):
        super().__init__(parent)
        self.setObjectName("segmented")
        h = QHBoxLayout(self)
        h.setContentsMargins(3, 3, 3, 3)
        h.setSpacing(2)
        self.group = QButtonGroup(self)
        self.buttons = {}
        for key, label in options:
            b = QPushButton(label, objectName="segment", checkable=True)
            b.setCursor(Qt.PointingHandCursor)
            b.clicked.connect(lambda _=False, k=key: self.changed.emit(k))
            self.group.addButton(b)
            self.buttons[key] = b
            h.addWidget(b)
        self.set(current or options[0][0])

    def set(self, key):
        if key in self.buttons:
            self.buttons[key].setChecked(True)

    def value(self) -> str:
        return next((k for k, b in self.buttons.items() if b.isChecked()), "")

    def set_enabled(self, key, enabled):
        self.buttons[key].setEnabled(enabled)
