"""The launcher's animated background: a night sky with twinkling stars, the odd
shooting star, and now and then a peregrine gliding across.

Kept light on purpose: about 140 tiny dots redrawn 30 times a second, a cached
gradient behind them, and nothing at all while a game is running or the window
is hidden or minimised.
"""

import math
import random
import time

from PySide6.QtCore import QByteArray, QPointF, QRectF, Qt, QTimer
from PySide6.QtGui import QColor, QImage, QLinearGradient, QPainter, QPixmap, QRadialGradient
from PySide6.QtSvg import QSvgRenderer
from PySide6.QtWidgets import QWidget

from .theme import LOGO_SVG, Theme

FPS = 24


class Sky(QWidget):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setAttribute(Qt.WA_OpaquePaintEvent)  # we paint every pixel; Qt can skip clearing
        rng = random.Random(7)
        # x, y (0-1), size, brightness, twinkle speed, phase, depth (0 far - 1 near)
        self.stars = [(rng.random(), rng.random(), rng.choice((1, 1, 1, 1.5, 2)),
                       rng.uniform(0.25, 0.9), rng.uniform(0.6, 2.2), rng.uniform(0, 6.3),
                       rng.random()) for _ in range(140)]
        self.rng = random.Random()
        self.start = time.monotonic()
        self.backdrop = None       # cached gradient, rebuilt on resize or theme change
        self.backdrop_key = None
        self.bird = None           # current flight, or None
        self.bird_pix = None
        self.bird_key = None
        self.next_bird = time.monotonic() + 6
        self.meteor = None
        self.next_meteor = time.monotonic() + 4
        self.enabled = True
        self.paused = False
        self.timer = QTimer(self)
        self.timer.setInterval(1000 // FPS)
        self.timer.timeout.connect(self._tick)
        # The bird and shooting stars fly on a see-through layer above everything,
        # so they cross the sidebar and dividers instead of being cut off by them.
        self.overlay = _Flyers(self)

    def _tick(self):
        self.update()
        now = time.monotonic()
        if self.bird or self.meteor or now >= min(self.next_bird, self.next_meteor):
            self.overlay.update()

    def resizeEvent(self, e):
        self.overlay.setGeometry(self.rect())
        self.overlay.raise_()
        super().resizeEvent(e)

    def childEvent(self, e):
        super().childEvent(e)
        if hasattr(self, "overlay") and e.added():
            self.overlay.raise_()  # stay on top of anything added later

    # ------------------------------------------------------------ on / off

    def set_enabled(self, on: bool):
        self.enabled = on
        self._sync()

    def set_paused(self, paused: bool):
        """Paused while a game runs or the window is minimised: the game gets all the power."""
        self.paused = paused
        self._sync()

    def _sync(self):
        running = self.enabled and not self.paused and self.isVisible()
        if running and not self.timer.isActive():
            self.timer.start()
        elif not running and self.timer.isActive():
            self.timer.stop()
        self.update()

    def showEvent(self, e):
        self._sync()
        super().showEvent(e)

    def hideEvent(self, e):
        self.timer.stop()
        super().hideEvent(e)

    # ------------------------------------------------------------ drawing

    def _dark(self) -> bool:
        return Theme.name != "light"

    def _backdrop(self) -> QPixmap:
        key = (self.width(), self.height(), Theme.name, Theme.accent)
        if key != self.backdrop_key:
            pm = QPixmap(max(1, self.width()), max(1, self.height()))
            p = QPainter(pm)
            bg = QColor(Theme.p["bg"])
            top = bg.darker(135) if self._dark() else bg.lighter(103)
            g = QLinearGradient(0, 0, 0, self.height())
            g.setColorAt(0, top)
            g.setColorAt(1, bg)
            p.fillRect(pm.rect(), g)
            # A faint glow of the accent color low on the horizon, like the last light of dusk.
            glow = QColor(Theme.accent)
            glow.setAlpha(26 if self._dark() else 18)
            r = QRadialGradient(QPointF(self.width() * 0.78, self.height() * 1.05), self.width() * 0.7)
            r.setColorAt(0, glow)
            glow.setAlpha(0)
            r.setColorAt(1, glow)
            p.fillRect(pm.rect(), r)
            # A whisper of grain hides the colour banding smooth gradients get on 8-bit screens.
            p.drawTiledPixmap(pm.rect(), self._grain())
            p.end()
            self.backdrop, self.backdrop_key = pm, key
        return self.backdrop

    @staticmethod
    def _grain() -> QPixmap:
        rng = random.Random(3)
        img = QImage(96, 96, QImage.Format_ARGB32_Premultiplied)
        img.fill(Qt.transparent)
        for y in range(96):
            for x in range(96):
                v = rng.randint(0, 255)
                a = rng.randint(0, 6)
                img.setPixelColor(x, y, QColor(v, v, v, a))
        return QPixmap.fromImage(img)

    def _bird_pixmap(self, size: int) -> QPixmap:
        key = (size, Theme.accent)
        if key != self.bird_key:
            svg = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="14 18 100 90">'
                   f'{LOGO_SVG.format(c=Theme.accent)}</svg>')
            img = QImage(size * 2, size * 2, QImage.Format_ARGB32_Premultiplied)
            img.fill(Qt.transparent)
            p = QPainter(img)
            p.setRenderHint(QPainter.Antialiasing)
            QSvgRenderer(QByteArray(svg.encode())).render(p)
            p.end()
            pm = QPixmap.fromImage(img)
            pm.setDevicePixelRatio(2)
            self.bird_pix, self.bird_key = pm, key
        return self.bird_pix

    def paintEvent(self, e):
        p = QPainter(self)
        p.drawPixmap(0, 0, self._backdrop())
        if not self.enabled:
            p.end()
            return
        now = time.monotonic()
        t = now - self.start
        w, h = self.width(), self.height()
        p.setRenderHint(QPainter.Antialiasing)
        p.setPen(Qt.NoPen)

        star = QColor("#ffffff" if self._dark() else Theme.p["faint"])
        for sx, sy, size, bright, speed, phase, depth in self.stars:
            # Stars drift very slowly to the left; nearer ones a little faster.
            x = (sx * w - t * (1.5 + depth * 4)) % w
            y = sy * h * 0.92
            twinkle = 0.55 + 0.45 * math.sin(t * speed + phase)
            star.setAlphaF(max(0.0, min(1.0, bright * twinkle * (1 if self._dark() else 0.45))))
            p.setBrush(star)
            p.drawEllipse(QRectF(x, y, size, size))

        p.end()

    def paint_flyers(self, p):
        if not self.enabled or self.paused:
            return
        now = time.monotonic()
        p.setRenderHint(QPainter.Antialiasing)
        self._meteor(p, now, self.width(), self.height())
        self._bird(p, now, self.width(), self.height())

    def _meteor(self, p, now, w, h):
        if self.meteor is None and now >= self.next_meteor:
            self.meteor = (now, self.rng.uniform(0.2, 0.9) * w, self.rng.uniform(0.02, 0.35) * h)
        if self.meteor is None:
            return
        born, x0, y0 = self.meteor
        age = (now - born) / 0.9  # lasts 0.9 s
        if age >= 1:
            self.meteor = None
            self.next_meteor = now + self.rng.uniform(9, 22)
            return
        dist = age * min(w, 520)
        x, y = x0 - dist, y0 + dist * 0.45
        fade = math.sin(age * math.pi)
        tail = QLinearGradient(QPointF(x, y), QPointF(x + 70, y - 31))
        c = QColor("#ffffff" if self._dark() else Theme.accent)
        c.setAlphaF(0.85 * fade)
        tail.setColorAt(0, c)
        c.setAlphaF(0)
        tail.setColorAt(1, c)
        p.save()
        pen = p.pen()
        pen.setBrush(tail)
        pen.setWidthF(1.6)
        pen.setCapStyle(Qt.RoundCap)
        p.setPen(pen)
        p.drawLine(QPointF(x, y), QPointF(x + 70, y - 31))
        p.restore()

    def _bird(self, p, now, w, h):
        if self.bird is None and now >= self.next_bird:
            left_to_right = self.rng.random() < 0.5
            self.bird = (now, left_to_right, self.rng.uniform(0.12, 0.45) * h, self.rng.uniform(9, 13))
        if self.bird is None:
            return
        born, ltr, base_y, duration = self.bird
        age = (now - born) / duration
        if age >= 1:
            self.bird = None
            self.next_bird = now + self.rng.uniform(25, 45)
            return
        size = 40
        travel = w + size * 4
        x = -size * 2 + age * travel if ltr else w + size * 2 - age * travel
        # Gentle rise and fall as it glides, with quick wing beats every so often.
        y = base_y + math.sin(age * math.pi * 3) * 14
        # Bursts of wing beats, then a glide with wings held out.
        flapping = int((now - born) * 0.9) % 2 == 0
        flap = 1.0 - 0.45 * abs(math.cos((now - born) * 9)) if flapping else 1.0
        tilt = math.cos(age * math.pi * 3) * 8 * (1 if ltr else -1)
        fade = min(1.0, age * 6, (1 - age) * 6)
        pm = self._bird_pixmap(size)
        p.save()
        p.setOpacity(0.9 * fade)
        p.translate(x, y)
        p.rotate(tilt + (90 if ltr else -90))  # the logo points up; turn it to face its way
        p.scale(flap, 1.0)  # squeezing the wingspan reads as a wing beat
        p.drawPixmap(QPointF(-size / 2, -size / 2), pm)
        p.restore()


class _Flyers(QWidget):
    """The top layer: draws only the bird and shooting stars, never takes clicks."""

    def __init__(self, sky: Sky):
        super().__init__(sky)
        self.sky = sky
        self.setAttribute(Qt.WA_TransparentForMouseEvents)
        self.setAttribute(Qt.WA_NoSystemBackground)

    def paintEvent(self, e):
        p = QPainter(self)
        self.sky.paint_flyers(p)
        p.end()
