"""The launcher's animated background: a night sky behind the whole window.

Stars twinkle and drift, a moon glows, clouds slide past and dim its glow when
they cover it, shooting stars streak by, and now and then a peregrine glides
across. Every launch gets its own sky: star field, clouds, the paths and timing
of shooting stars and the bird are all random. In the Light theme it becomes a
calm daytime sky.

Kept light on purpose: everything heavy (gradients, the moon, its halo, clouds)
is drawn once into images and reused; each frame only places them. Nothing runs
while a game is playing or the window is hidden, minimised or in the background.
"""

import math
import random
import time

from PySide6.QtCore import QByteArray, QPointF, QRectF, Qt, QTimer
from PySide6.QtGui import (QColor, QImage, QLinearGradient, QPainter, QPainterPath, QPixmap,
                           QRadialGradient)
from PySide6.QtSvg import QSvgRenderer
from PySide6.QtWidgets import QWidget

from .theme import LOGO_SVG, Theme

FPS = 24          # the bird and shooting stars move this smoothly
SKY_EVERY = 3     # the slow sky (twinkles, clouds) is redrawn every 3rd frame: 8 a second


def _soft_blob(p: QPainter, x: float, y: float, r: float, color: QColor, alpha: float):
    g = QRadialGradient(QPointF(x, y), r)
    c = QColor(color)
    c.setAlphaF(alpha)
    g.setColorAt(0, c)
    c.setAlphaF(alpha * 0.55)
    g.setColorAt(0.55, c)
    c.setAlphaF(0)
    g.setColorAt(1, c)
    p.setBrush(g)
    p.drawEllipse(QPointF(x, y), r, r)


class _Cloud:
    """One cloud: a soft pre-drawn image (plus a brighter 'lit' copy for when it
    passes the moon) that drifts slowly across."""

    def __init__(self, rng: random.Random, w: int, h: int, first: bool):
        self.scale = rng.uniform(0.6, 1.5)
        self.stretch = rng.uniform(0.8, 1.5)  # some long and flat, some tall and puffy
        self.speed = rng.uniform(4, 9) * self.scale  # px a second; bigger ones are nearer and faster
        self.y = rng.uniform(0.08, 0.62) * h
        self.seed = rng.randrange(1 << 30)
        self.x = rng.uniform(-0.2, 1.0) * w if first else w + rng.uniform(20, 300)
        self.images = None
        self.key = None

    def build(self, dark: bool):
        key = (dark, Theme.accent)
        if key == self.key:
            return
        rng = random.Random(self.seed)
        cw, ch = int(300 * self.scale * self.stretch), int(110 * self.scale)
        q = 2  # drawn at twice the size, so edges stay smooth on sharp screens
        base_color = QColor(118, 128, 156) if dark else QColor(255, 255, 255)
        lit_color = QColor(214, 220, 236) if dark else QColor(255, 248, 232)
        blobs = []
        for _ in range(rng.randint(34, 52)):
            t = rng.random()
            bx = cw * (0.12 + 0.76 * t)
            hump = math.sin(t * math.pi)  # puffier in the middle
            by = ch * (0.62 - 0.28 * hump * rng.uniform(0.6, 1.0))
            r = ch * rng.uniform(0.18, 0.34) * (0.7 + 0.5 * hump)
            blobs.append((bx, by, r))
        images = []
        for color, strength in ((base_color, 0.30 if dark else 0.55), (lit_color, 0.42 if dark else 0.6)):
            img = QImage(cw * q, ch * q, QImage.Format_ARGB32_Premultiplied)
            img.fill(Qt.transparent)
            p = QPainter(img)
            p.setRenderHint(QPainter.Antialiasing)
            p.setPen(Qt.NoPen)
            p.scale(q, q)
            for bx, by, r in blobs:
                _soft_blob(p, bx, by, r, color, strength)
            # Light catching the tops of the puffs gives the cloud some volume.
            top = QColor(color).lighter(125)
            for bx, by, r in blobs:
                _soft_blob(p, bx + r * 0.12, by - r * 0.32, r * 0.62, top, strength * 0.42)
            # A flatter, darker underside, like real clouds.
            p.setCompositionMode(QPainter.CompositionMode_DestinationIn)
            # Wispy edges: a few faint stray puffs around the outside.
            for _ in range(10):
                t = rng.random()
                _soft_blob(p, cw * (0.05 + 0.9 * t), ch * rng.uniform(0.35, 0.8),
                           ch * rng.uniform(0.08, 0.16), color, strength * 0.35)
            p.resetTransform()
            fade = QLinearGradient(0, 0, 0, ch * q)
            fade.setColorAt(0, QColor(0, 0, 0, 255))
            fade.setColorAt(0.75, QColor(0, 0, 0, 230))
            fade.setColorAt(1, QColor(0, 0, 0, 0))
            p.fillRect(img.rect(), fade)
            p.end()
            pm = QPixmap.fromImage(img)
            pm.setDevicePixelRatio(q)
            images.append(pm)
        self.images, self.key = images, key

    def rect(self) -> QRectF:
        pm = self.images[0]
        d = pm.devicePixelRatio()
        return QRectF(self.x, self.y, pm.width() / d, pm.height() / d)


class Sky(QWidget):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setAttribute(Qt.WA_OpaquePaintEvent)  # we paint every pixel; Qt can skip clearing
        rng = random.Random()  # a new sky every launch
        tints = [(255, 255, 255)] * 6 + [(200, 218, 255), (255, 236, 210)]
        # x, y (0-1), size, brightness, twinkle speed, phase, depth (0 far - 1 near), tint
        self.stars = [(rng.random(), rng.random() ** 1.2, rng.choice((1, 1, 1, 1, 1.4, 1.8)),
                       rng.uniform(0.25, 0.95), rng.uniform(0.5, 2.4), rng.uniform(0, 6.3),
                       rng.random(), rng.choice(tints)) for _ in range(rng.randint(170, 230))]
        # A few bright stars that sparkle with a little cross of light.
        self.bright = [(rng.random(), rng.uniform(0.04, 0.55), rng.uniform(1.2, 2.2), rng.uniform(0, 6.3))
                       for _ in range(rng.randint(5, 9))]
        self.cloud_seed = rng.randrange(1 << 30)
        self.rng = random.Random()
        self.start = time.monotonic()
        self.last = self.start
        self.cache = {}            # pre-drawn images, rebuilt on resize or theme change
        self.cache_key = None
        self.clouds = []
        self.moon_light = 1.0      # 1 = clear, 0 = fully behind a cloud (eased)
        self.bird = None           # (born, left_to_right, y, duration) while one is flying
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
        self.frame = 0
        self.flyer_rects = []  # where the bird and shooting star were drawn last
        # The bird and shooting stars fly on a see-through layer above everything,
        # so they cross the sidebar and dividers instead of being cut off by them.
        self.overlay = _Flyers(self)

    def _tick(self):
        self.frame += 1
        if self.frame % SKY_EVERY == 0:
            self.update()  # also repaints the flyers on top
            return
        # In between, only the small areas around the bird and shooting star are redrawn.
        now = time.monotonic()
        if self.bird or self.meteor or now >= min(self.next_bird, self.next_meteor):
            if not self.flyer_rects:
                self.overlay.update()
            for r in self.flyer_rects:
                self.overlay.update(r.adjusted(-60, -60, 60, 60).toAlignedRect())

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
            self.last = time.monotonic()
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

    # ------------------------------------------------------------ cached images

    def _dark(self) -> bool:
        return Theme.name != "light"

    def _moon_pos(self) -> QPointF:
        return QPointF(self.width() * 0.80, max(110.0, self.height() * 0.24))

    def _images(self) -> dict:
        key = (self.width(), self.height(), Theme.name, Theme.accent)
        if key == self.cache_key:
            return self.cache
        w, h, dark = max(1, self.width()), max(1, self.height()), self._dark()
        c = {}

        # Sky gradient, a glow of the accent low on the horizon, and fine grain
        # that hides the colour banding smooth gradients get on 8-bit screens.
        pm = QPixmap(w, h)
        p = QPainter(pm)
        bg = QColor(Theme.p["bg"])
        g = QLinearGradient(0, 0, 0, h)
        if dark:
            g.setColorAt(0, QColor(9, 12, 22))
            g.setColorAt(0.55, bg.darker(115))
            g.setColorAt(1, bg)
        else:
            g.setColorAt(0, QColor(214, 228, 246))
            g.setColorAt(1, bg)
        p.fillRect(pm.rect(), g)
        glow = QColor(Theme.accent)
        glow.setAlpha(30 if dark else 22)
        r = QRadialGradient(QPointF(w * 0.25, h * 1.1), w * 0.75)
        r.setColorAt(0, glow)
        glow.setAlpha(0)
        r.setColorAt(1, glow)
        p.fillRect(pm.rect(), r)
        p.drawTiledPixmap(pm.rect(), self._grain())
        p.end()
        c["backdrop"] = pm

        # The moon (or the sun, in the Light theme) and its halo.
        size = 58
        img = QImage(size * 2, size * 2, QImage.Format_ARGB32_Premultiplied)
        img.fill(Qt.transparent)
        p = QPainter(img)
        p.setRenderHint(QPainter.Antialiasing)
        p.setPen(Qt.NoPen)
        p.scale(2, 2)
        disc = QRadialGradient(QPointF(size * 0.42, size * 0.4), size * 0.62)
        if dark:
            disc.setColorAt(0, QColor(250, 248, 240))
            disc.setColorAt(0.75, QColor(222, 222, 214))
            disc.setColorAt(1, QColor(176, 180, 186))
        else:
            disc.setColorAt(0, QColor(255, 252, 236))
            disc.setColorAt(1, QColor(255, 226, 160))
        p.setBrush(disc)
        p.drawEllipse(QRectF(1, 1, size - 2, size - 2))
        if dark:
            crater = QColor(150, 154, 160, 70)
            for cx, cy, cr in ((0.32, 0.36, 0.11), (0.6, 0.3, 0.07), (0.55, 0.62, 0.13),
                               (0.3, 0.66, 0.06), (0.7, 0.5, 0.05), (0.45, 0.48, 0.04)):
                p.setBrush(crater)
                p.drawEllipse(QPointF(size * cx, size * cy), size * cr, size * cr)
            # A soft shadow on one side: a moon just past full.
            shade = QPainterPath()
            shade.addEllipse(QRectF(1, 1, size - 2, size - 2))
            cut = QPainterPath()
            cut.addEllipse(QRectF(-size * 0.18, -2, size * 1.02, size + 4))
            p.setBrush(QColor(20, 24, 36, 120))
            p.drawPath(shade.subtracted(cut))
        p.end()
        moon = QPixmap.fromImage(img)
        moon.setDevicePixelRatio(2)
        c["moon"] = moon

        hr = 190
        img = QImage(hr * 2, hr * 2, QImage.Format_ARGB32_Premultiplied)
        img.fill(Qt.transparent)
        p = QPainter(img)
        halo = QRadialGradient(QPointF(hr, hr), hr)
        tone = QColor(200, 214, 255) if dark else QColor(255, 222, 160)
        for stop, a in ((0, 0.5), (0.14, 0.26), (0.38, 0.09), (1, 0)):
            tone.setAlphaF(a)
            halo.setColorAt(stop, tone)
        p.fillRect(img.rect(), halo)
        p.end()
        c["halo"] = QPixmap.fromImage(img)

        # A soft star sprite (drawn once, placed many times) for smooth, glowing stars.
        img = QImage(32, 32, QImage.Format_ARGB32_Premultiplied)
        img.fill(Qt.transparent)
        p = QPainter(img)
        glow = QRadialGradient(QPointF(16, 16), 16)
        for stop, a in ((0, 1.0), (0.18, 0.85), (0.4, 0.22), (1, 0)):
            glow.setColorAt(stop, QColor(255, 255, 255, int(255 * a)))
        p.fillRect(img.rect(), glow)
        p.end()
        star = QPixmap.fromImage(img)
        star.setDevicePixelRatio(4)
        c["star"] = star

        self.cache, self.cache_key = c, key
        if not self.clouds:
            rng = random.Random(self.cloud_seed)
            self.clouds = [_Cloud(rng, w, h, first=True) for _ in range(rng.randint(4, 7))]
        for cloud in self.clouds:
            cloud.build(dark)
        return c

    @staticmethod
    def _grain() -> QPixmap:
        rng = random.Random(3)
        img = QImage(96, 96, QImage.Format_ARGB32_Premultiplied)
        img.fill(Qt.transparent)
        for y in range(96):
            for x in range(96):
                v = rng.randint(0, 255)
                img.setPixelColor(x, y, QColor(v, v, v, rng.randint(0, 6)))
        return QPixmap.fromImage(img)

    def _bird_pixmap(self, size: int) -> QPixmap:
        key = (size, Theme.accent)
        if key != self.bird_key:
            svg = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="14 18 100 90">'
                   f'{LOGO_SVG.format(c=Theme.accent)}</svg>')
            img = QImage(size * 3, size * 3, QImage.Format_ARGB32_Premultiplied)
            img.fill(Qt.transparent)
            p = QPainter(img)
            p.setRenderHint(QPainter.Antialiasing)
            QSvgRenderer(QByteArray(svg.encode())).render(p)
            p.end()
            pm = QPixmap.fromImage(img)
            pm.setDevicePixelRatio(3)
            self.bird_pix, self.bird_key = pm, key
        return self.bird_pix

    # ------------------------------------------------------------ drawing

    def paintEvent(self, e):
        c = self._images()
        p = QPainter(self)
        p.drawPixmap(0, 0, c["backdrop"])
        if not self.enabled:
            p.end()
            return
        now = time.monotonic()
        dt = min(0.2, max(0.0, now - self.last)) if self.timer.isActive() else 0.0
        self.last = now
        t = now - self.start
        w, h, dark = self.width(), self.height(), self._dark()
        p.setRenderHint(QPainter.Antialiasing)
        p.setPen(Qt.NoPen)

        if dark:
            self._stars(p, t, w, h, c["star"])

        # Clouds move first, so the moonlight knows how much of the moon they cover.
        for cloud in self.clouds:
            cloud.x -= cloud.speed * dt
            if cloud.x + cloud.rect().width() < -20:
                fresh = _Cloud(self.rng, w, h, first=False)
                fresh.build(dark)
                self.clouds[self.clouds.index(cloud)] = fresh
        moon = self._moon_pos()
        cover = 0.0
        for cloud in self.clouds:
            r = cloud.rect().adjusted(cloud.rect().width() * 0.12, cloud.rect().height() * 0.25,
                                      -cloud.rect().width() * 0.12, -cloud.rect().height() * 0.2)
            if r.contains(moon):
                # Deeper inside the cloud = more of the light blocked.
                dx = min(moon.x() - r.left(), r.right() - moon.x()) / max(1.0, r.width() / 2)
                cover = max(cover, min(1.0, 0.35 + dx) * 0.85)
        target = 1.0 - cover
        self.moon_light += (target - self.moon_light) * min(1.0, dt * 2.5)
        light = self.moon_light

        # The halo, dimmed by any cloud in front of the moon.
        halo = c["halo"]
        p.setOpacity((0.75 if dark else 0.9) * (0.25 + 0.75 * light))
        p.drawPixmap(QPointF(moon.x() - halo.width() / 2, moon.y() - halo.height() / 2), halo)
        p.setOpacity(1.0)
        mp = c["moon"]
        p.drawPixmap(QPointF(moon.x() - mp.width() / 2 / mp.devicePixelRatio(),
                             moon.y() - mp.height() / 2 / mp.devicePixelRatio()), mp)

        # Clouds over the moon; the ones near it catch its light on their edges.
        for cloud in self.clouds:
            r = cloud.rect()
            base, lit = cloud.images
            near = max(0.0, 1.0 - math.hypot(r.center().x() - moon.x(), r.center().y() - moon.y()) / 260)
            p.setOpacity(1.0)
            p.drawPixmap(r.topLeft(), base)
            if near > 0.02:
                p.setOpacity(near * (0.4 + 0.6 * light))
                p.drawPixmap(r.topLeft(), lit)
        p.setOpacity(1.0)
        p.end()

    def _stars(self, p, t, w, h, sprite):
        for sx, sy, size, bright, speed, phase, depth, tint in self.stars:
            x = (sx * w - t * (1.2 + depth * 3.5)) % w   # slow drift; nearer stars a bit faster
            y = sy * h * 0.95
            twinkle = 0.55 + 0.45 * math.sin(t * speed + phase)
            p.setOpacity(max(0.0, min(1.0, bright * twinkle)))
            d = size * 2.6  # the sprite's soft glow reaches past the star itself
            p.drawPixmap(QRectF(x - d / 2, y - d / 2, d, d), sprite, QRectF(sprite.rect()))
        p.setOpacity(1.0)
        col = QColor(255, 255, 255)
        for sx, sy, size, phase in self.bright:
            x = (sx * w - t * 2.2) % w
            y = sy * h
            s = 0.5 + 0.5 * math.sin(t * 1.3 + phase)
            arm = size * (3 + 4 * s)
            d = size * (5 + 2 * s)
            p.setOpacity(0.55 + 0.4 * s)
            p.drawPixmap(QRectF(x - d / 2, y - d / 2, d, d), sprite, QRectF(sprite.rect()))
            # Fine spikes of light that fade towards their tips.
            for dx, dy in ((1, 0), (0, 1)):
                g = QLinearGradient(QPointF(x - arm * dx, y - arm * dy), QPointF(x + arm * dx, y + arm * dy))
                col.setAlphaF(0)
                g.setColorAt(0, col)
                col.setAlphaF(0.55 * s + 0.15)
                g.setColorAt(0.5, col)
                col.setAlphaF(0)
                g.setColorAt(1, col)
                p.setBrush(g)
                if dx:
                    p.drawRect(QRectF(x - arm, y - 0.35, arm * 2, 0.7))
                else:
                    p.drawRect(QRectF(x - 0.35, y - arm, 0.7, arm * 2))
        p.setOpacity(1.0)

    # ------------------------------------------------------------ flyers (top layer)

    def paint_flyers(self, p):
        if not self.enabled or self.paused:
            return
        now = time.monotonic()
        p.setRenderHint(QPainter.Antialiasing)
        self.flyer_rects = []
        self._meteor(p, now, self.width(), self.height())
        self._bird(p, now, self.width(), self.height())

    def _meteor(self, p, now, w, h):
        if self.meteor is None and now >= self.next_meteor:
            rng = self.rng
            to_left = rng.random() < 0.7
            self.meteor = {
                "born": now, "x": rng.uniform(0.15, 0.95) * w, "y": rng.uniform(0.02, 0.35) * h,
                "life": rng.uniform(0.6, 1.4), "dir": -1 if to_left else 1,
                "slope": rng.uniform(0.25, 0.7), "reach": rng.uniform(0.6, 1.1) * min(w, 620),
                "tail": rng.uniform(50, 130), "width": rng.uniform(1.2, 2.4),
                "fireball": rng.random() < 0.12,  # now and then a bright one with a glowing head
            }
        m = self.meteor
        if m is None:
            return
        age = (now - m["born"]) / m["life"]
        if age >= 1:
            self.meteor = None
            # Now and then two come close together.
            self.next_meteor = now + (self.rng.uniform(0.4, 1.2) if self.rng.random() < 0.18
                                      else self.rng.uniform(6, 22))
            return
        ease = 1 - (1 - age) ** 1.6  # quick at first, slowing as it burns out
        dist = ease * m["reach"]
        x, y = m["x"] + m["dir"] * dist, m["y"] + dist * m["slope"]
        fade = math.sin(age * math.pi)
        tail_len = m["tail"] * (0.5 + 0.5 * fade)
        norm = math.hypot(1, m["slope"])
        tx, ty = x - m["dir"] * tail_len / norm, y - tail_len * m["slope"] / norm
        tail = QLinearGradient(QPointF(x, y), QPointF(tx, ty))
        c = QColor("#ffffff" if self._dark() else Theme.accent)
        c.setAlphaF(0.95 * fade)
        tail.setColorAt(0, c)
        c.setAlphaF(0.35 * fade)
        tail.setColorAt(0.3, c)
        c.setAlphaF(0)
        tail.setColorAt(1, c)
        p.save()
        pen = p.pen()
        pen.setBrush(tail)
        pen.setWidthF(m["width"] * (1.6 if m["fireball"] else 1.0))
        pen.setCapStyle(Qt.RoundCap)
        p.setPen(pen)
        p.drawLine(QPointF(x, y), QPointF(tx, ty))
        self.flyer_rects.append(QRectF(QPointF(x, y), QPointF(tx, ty)).normalized().adjusted(-8, -8, 8, 8))
        p.setPen(Qt.NoPen)
        r = 7 if m["fireball"] else 3.5
        glow = QRadialGradient(QPointF(x, y), r)
        head = QColor(255, 244, 220) if m["fireball"] else QColor(255, 255, 255)
        head.setAlphaF(0.95 * fade)
        glow.setColorAt(0, head)
        head.setAlphaF(0)
        glow.setColorAt(1, head)
        p.setBrush(glow)
        p.drawEllipse(QPointF(x, y), r, r)
        p.restore()

    def _bird(self, p, now, w, h):
        if self.bird is None and now >= self.next_bird:
            rng = self.rng
            self.bird = (now, rng.random() < 0.5, rng.uniform(0.1, 0.55) * h, rng.uniform(8, 15),
                         rng.randint(30, 52), rng.uniform(-0.18, 0.18) * h, rng.uniform(1.5, 4), rng.uniform(8, 22))
        if self.bird is None:
            return
        born, ltr, base_y, duration, size, climb, waves, sway = self.bird
        age = (now - born) / duration
        if age >= 1:
            self.bird = None
            self.next_bird = now + self.rng.uniform(20, 50)
            return
        if age < 0:
            return
        travel = w + size * 4
        x = -size * 2 + age * travel if ltr else w + size * 2 - age * travel
        # Gentle rise and fall as it glides, with bursts of wing beats.
        y = base_y + climb * age + math.sin(age * math.pi * waves) * sway
        flapping = int((now - born) * 0.9) % 2 == 0
        flap = 1.0 - 0.45 * abs(math.cos((now - born) * 9)) if flapping else 1.0
        tilt = (math.cos(age * math.pi * waves) * 8 + math.degrees(math.atan2(climb, travel)) * 0.6) * (1 if ltr else -1)
        fade = min(1.0, age * 6, (1 - age) * 6)
        pm = self._bird_pixmap(size)
        p.save()
        p.setOpacity(0.92 * fade)
        p.translate(x, y)
        p.rotate(tilt + (90 if ltr else -90))  # the logo points up; turn it to face its way
        p.scale(flap, 1.0)  # squeezing the wingspan reads as a wing beat
        p.drawPixmap(QPointF(-size / 2, -size / 2), pm)
        p.restore()
        self.flyer_rects.append(QRectF(x - size, y - size, size * 2, size * 2))


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
