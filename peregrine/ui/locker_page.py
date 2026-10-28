"""The Locker: try on skins and capes, then wear them.

On the left, your player turns around (click to spin) wearing what you've picked. On the
right, your skin library and your capes. "Wear" puts a skin or official cape on your
Minecraft account; Peregrine capes are picked here and show in game for you and other
Peregrine players.
"""

import math
import shutil
import time
from pathlib import Path

from PySide6.QtCore import QEasingCurve, QRectF, QSize, Qt, QTimer, QVariantAnimation, Signal
from PySide6.QtGui import QColor, QImage, QPainter, QPainterPath, QPen
from PySide6.QtWidgets import QFileDialog, QGridLayout, QHBoxLayout, QLabel, QVBoxLayout, QWidget

from .. import auth, custom_cape, locker
from . import workers
from .modern import Chip, PillButton, animations_on, big_title, section_label
from .pages import clear, scroll
from .theme import Theme, icon_pixmap
from .widgets import Segmented


# ---------------------------------------------------------------------- drawing a skin

def _faces(img: QImage, slim: bool):
    """Where each part's front and back faces are in a skin: name -> (front, back, overlay front,
    overlay back, mirror) with each face as (x, y, w, h)."""
    legacy = img.height() == 32
    aw = 3 if slim else 4
    f = {
        "head": ((8, 8, 8, 8), (24, 8, 8, 8), (40, 8, 8, 8), (56, 8, 8, 8)),
        "body": ((20, 20, 8, 12), (32, 20, 8, 12), (20, 36, 8, 12), (32, 36, 8, 12)),
        "rarm": ((44, 20, aw, 12), (48 + aw, 20, aw, 12), (44, 36, aw, 12), (48 + aw, 36, aw, 12)),
        "larm": ((36, 52, aw, 12), (40 + aw, 52, aw, 12), (52, 52, aw, 12), (56 + aw, 52, aw, 12)),
        "rleg": ((4, 20, 4, 12), (12, 20, 4, 12), (4, 36, 4, 12), (12, 36, 4, 12)),
        "lleg": ((20, 52, 4, 12), (28, 52, 4, 12), (4, 52, 4, 12), (12, 52, 4, 12)),
    }
    if legacy:  # 64x32: one layer, left limbs are the right ones mirrored
        f["head"] = f["head"][:2] + ((40, 8, 8, 8), (56, 8, 8, 8))
        for k in ("body", "rarm", "rleg"):
            f[k] = f[k][:2] + (None, None)
        f["larm"] = ("mirror", "rarm")
        f["lleg"] = ("mirror", "rleg")
    return f


def paint_skin(p: QPainter, img: QImage, slim: bool, back: bool, x: float, y: float, s: float,
               cape: QImage = None, overlay: bool = True):
    """Draws a skin flat, from the front or the back, at x, y with s screen pixels per skin pixel.
    The figure is 16 skin pixels wide and 32 high."""
    if img is None or img.isNull():
        return
    faces = _faces(img, slim)
    aw = 3 if slim else 4
    # where each part goes on screen (in skin pixels), seen from the front
    place = {"head": (4, 0), "body": (4, 8), "rarm": (4 - aw, 8), "larm": (12, 8), "rleg": (4, 20), "lleg": (8, 20)}
    if back:  # from behind, left and right swap sides
        place = {"head": (4, 0), "body": (4, 8), "larm": (4 - aw, 8), "rarm": (12, 8), "lleg": (4, 20), "rleg": (8, 20)}

    def face_of(part, layer):
        f = faces[part]
        mirror = False
        if f[0] == "mirror":
            f = faces[f[1]]
            mirror = True
        rect = f[(1 if back else 0) + (2 if layer else 0)]
        return rect, mirror

    def blit(rect, mirror, px, py, inflate=0.0):
        if rect is None:
            return
        fx, fy, fw, fh = rect
        part = img.copy(fx, fy, fw, fh)
        if mirror:
            part = part.mirrored(True, False)
        target = QRectF(x + (px - inflate) * s, y + (py - inflate) * s, (fw + 2 * inflate) * s, (fh + 2 * inflate) * s)
        p.drawImage(target, part)

    p.setRenderHint(QPainter.SmoothPixmapTransform, False)
    order = ["rleg", "lleg", "body", "rarm", "larm", "head"]
    for part in order:
        rect, mirror = face_of(part, False)
        px, py = place[part]
        blit(rect, mirror, px, py)
    if back and cape is not None and not cape.isNull():
        k = cape.width() / 64.0
        front = cape.copy(round(1 * k), round(1 * k), round(10 * k), round(16 * k))
        p.drawImage(QRectF(x + 3 * s, y + 8 * s, 10 * s, 16 * s), front)
    if overlay:
        for part in order:
            rect, mirror = face_of(part, True)
            if rect is None:
                continue
            px, py = place[part]
            if back and cape is not None and part == "body":
                continue  # under the cape
            blit(rect, mirror, px, py, 0.5 if part != "head" else 0.6)


# ---------------------------------------------------------------------- the player in 3D

def _boxes(slim: bool, legacy: bool):
    """The player's boxes: (x0, y0, z0, w, h, d, u, v, grow, mirror, limb) in skin pixels.
    x to the player's left, y down from the top of the head, z toward you."""
    aw = 3 if slim else 4
    out = [(-4, 0, -4, 8, 8, 8, 0, 0, 0, False, None), (-4, 8, -2, 8, 12, 4, 16, 16, 0, False, None),
           (-4 - aw, 8, -2, aw, 12, 4, 40, 16, 0, False, "r"), (-4, 20, -2, 4, 12, 4, 0, 16, 0, False, None)]
    if legacy:
        out += [(4, 8, -2, aw, 12, 4, 40, 16, 0, True, "l"), (0, 20, -2, 4, 12, 4, 0, 16, 0, True, None),
                (-4, 0, -4, 8, 8, 8, 32, 0, 0.5, False, None)]
    else:
        out += [(4, 8, -2, aw, 12, 4, 32, 48, 0, False, "l"), (0, 20, -2, 4, 12, 4, 16, 48, 0, False, None),
                (-4, 0, -4, 8, 8, 8, 32, 0, 0.5, False, None), (-4, 8, -2, 8, 12, 4, 16, 32, 0.25, False, None),
                (-4 - aw, 8, -2, aw, 12, 4, 40, 32, 0.25, False, "r"), (4, 8, -2, aw, 12, 4, 48, 48, 0.25, False, "l"),
                (-4, 20, -2, 4, 12, 4, 0, 32, 0.25, False, None), (0, 20, -2, 4, 12, 4, 0, 48, 0.25, False, None)]
    return out


class _Shaded:
    """Pieces of a texture, darkened for the sides that face away from the light (cached)."""

    def __init__(self):
        self.cache = {}

    def get(self, img, key, rect, shade):
        k = (key, rect, shade)
        part = self.cache.get(k)
        if part is None:
            x, y, w, h = rect
            part = img.copy(int(x), int(y), max(1, int(w)), max(1, int(h)))
            if shade < 255:
                q = QPainter(part)
                q.setCompositionMode(QPainter.CompositionMode_SourceAtop)
                q.fillRect(part.rect(), QColor(0, 0, 0, 255 - shade))
                q.end()
            if len(self.cache) > 600:
                self.cache.clear()
            self.cache[k] = part
        return part


def paint_3d(p: QPainter, shaded: _Shaded, img: QImage, img_key, slim: bool, cape: QImage, cape_key,
             cx: float, top: float, s: float, yaw_deg: float, pitch_deg: float, t: float):
    """Draws the player in 3D (straight-on view, like Minecraft's own menus), farthest faces first."""
    from PySide6.QtGui import QTransform
    legacy = img.height() == 32
    tw, th = img.width() / 64.0, img.height() / (32.0 if legacy else 64.0)  # pixels per skin pixel
    cy, sy = math.cos(math.radians(yaw_deg)), math.sin(math.radians(yaw_deg))
    cp, sp = math.cos(math.radians(pitch_deg)), math.sin(math.radians(pitch_deg))
    swing = math.sin(t * 1.6) * 0.06

    def limb(pt, lb):
        if lb is None:
            return pt
        y0, z0, a = lb
        c, sn = math.cos(a), math.sin(a)
        y, z = pt[1] - y0, pt[2] - z0
        return (pt[0], y0 + y * c - z * sn, z0 + y * sn + z * c)

    def rot(pt):
        x, y, z = pt[0], pt[1] - 16, pt[2]
        x1 = x * cy + z * sy
        z1 = -x * sy + z * cy
        y2 = y * cp - z1 * sp
        z2 = y * sp + z1 * cp
        return (cx + x1 * s, top + (y2 + 16) * s, z2)

    faces = []

    def add_box(x0, y0, z0, w, h, d, uvs, grow, mirror, lb, tex, key, ppu, ppv, shades=(255, 235, 205, 205, 245, 170)):
        x0, y0, z0 = x0 - grow, y0 - grow, z0 - grow
        bw, bh, bd = w + 2 * grow, h + 2 * grow, d + 2 * grow
        x1, y1, z1 = x0 + bw, y0 + bh, z0 + bd
        geo = [((x0, y0, z1), (bw, 0, 0), (0, bh, 0), (0, 0, 1)), ((x1, y0, z0), (-bw, 0, 0), (0, bh, 0), (0, 0, -1)),
               ((x0, y0, z0), (0, 0, bd), (0, bh, 0), (-1, 0, 0)), ((x1, y0, z1), (0, 0, -bd), (0, bh, 0), (1, 0, 0)),
               ((x0, y0, z0), (bw, 0, 0), (0, 0, bd), (0, -1, 0)), ((x0, y1, z1), (bw, 0, 0), (0, 0, -bd), (0, 1, 0))]
        for i, (p0, eu, ev, n) in enumerate(geo):
            # the face's direction after turning: skip the ones facing away
            nl = n if lb is None else (n[0], n[1] * math.cos(lb[2]) - n[2] * math.sin(lb[2]),
                                       n[1] * math.sin(lb[2]) + n[2] * math.cos(lb[2]))
            nz = nl[1] * sp + (-nl[0] * sy + nl[2] * cy) * cp
            if nz <= 1e-4:
                continue
            if mirror:
                p0 = (p0[0] + eu[0], p0[1] + eu[1], p0[2] + eu[2])
                eu = (-eu[0], -eu[1], -eu[2])
            a = rot(limb(p0, lb))
            b = rot(limb((p0[0] + eu[0], p0[1] + eu[1], p0[2] + eu[2]), lb))
            c = rot(limb((p0[0] + ev[0], p0[1] + ev[1], p0[2] + ev[2]), lb))
            m = rot(limb((p0[0] + eu[0] / 2 + ev[0] / 2, p0[1] + eu[1] / 2 + ev[1] / 2, p0[2] + eu[2] / 2 + ev[2] / 2), lb))
            ux, uy, uw, uh = uvs[i]
            shade = int(shades[i] * (0.78 + 0.22 * nz))
            faces.append((m[2] + grow * 0.01, a, b, c, tex, key, (ux * ppu, uy * ppv, uw * ppu, uh * ppv), shade))

    for (x0, y0, z0, w, h, d, u, v, grow, mirror, lb) in _boxes(slim, legacy):
        uvs = [(u + d, v + d, w, h), (u + 2 * d + w, v + d, w, h), (u, v + d, d, h), (u + d + w, v + d, d, h),
               (u + d, v, w, d), (u + d + w, v, w, d)]
        if mirror:
            uvs[2], uvs[3] = uvs[3], uvs[2]
        lbv = None if lb is None else (9.0, 0.0, swing if lb == "r" else -swing)
        add_box(x0, y0, z0, w, h, d, uvs, grow, mirror, lbv, img, img_key, tw, th)
    if cape is not None and not cape.isNull():
        k = cape.width() / 64.0
        tilt = math.radians(-8 - 3 * math.sin(t * 1.3))
        # outside first (seen from behind), then the inside, the edges
        cuv = [(12, 1, 10, 16), (1, 1, 10, 16), (0, 1, 1, 16), (11, 1, 1, 16), (1, 0, 10, 1), (11, 0, 10, 1)]
        add_box(-5, 8, -3.1, 10, 16, 1, cuv, 0, False, (8.0, -2.6, tilt), cape, cape_key, k, k)

    faces.sort(key=lambda f: f[0])
    p.save()
    p.setRenderHint(QPainter.SmoothPixmapTransform, False)
    for _, a, b, c, tex, key, rect, shade in faces:
        part = shaded.get(tex, key, rect, shade)
        pw, ph = part.width(), part.height()
        g = 0.012  # a hair bigger, so no seams show
        ax, ay = (b[0] - a[0]), (b[1] - a[1])
        bx, by = (c[0] - a[0]), (c[1] - a[1])
        ox, oy = a[0] - (ax + bx) * g, a[1] - (ay + by) * g
        p.setTransform(QTransform(ax * (1 + 2 * g) / pw, ay * (1 + 2 * g) / pw, bx * (1 + 2 * g) / ph,
                                  by * (1 + 2 * g) / ph, ox, oy))
        p.drawImage(0, 0, part)
    p.restore()


class SkinView(QWidget):
    """The big player in the Locker, in 3D: drag to turn it any way, click to spin it round.
    Left alone it turns slowly on its own, like a showcase."""

    def __init__(self, scale=9):
        super().__init__()
        self.scale = scale
        self.img = None
        self.img_key = None
        self.slim = False
        self.cape = None
        self.cape_frames = None   # moving capes: their frames
        self.cape_ms = 0.125
        self.cape_key = None
        self.backdrop = None      # the game's main menu background, behind your player
        self.angle = 25.0         # 0 = facing you, 180 = back
        self.pitch = -10.0
        self.velocity = 0.0
        self.drag_from = None
        self.dragged = False
        self.idle_since = time.monotonic()
        self.last = time.monotonic()
        self.shaded = _Shaded()
        self.t0 = time.monotonic()
        self.setMinimumSize(16 * 5 + 60, 32 * 5 + 50)
        from PySide6.QtWidgets import QSizePolicy
        self.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Expanding)
        self.setCursor(Qt.OpenHandCursor)
        self.setToolTip("Drag to turn · click to spin")
        self.timer = QTimer(self, interval=16)
        self.timer.timeout.connect(self._tick)
        self.timer.start()
        self.anim = QVariantAnimation(self, duration=700, easingCurve=QEasingCurve.OutCubic)
        self.anim.valueChanged.connect(self._turn)

    def sizeHint(self):
        return QSize(16 * self.scale + 80, 32 * self.scale + 70)

    def _turn(self, v):
        self.angle = float(v)
        self.update()

    def _tick(self):
        now = time.monotonic()
        dt = min(0.05, now - self.last)
        self.last = now
        if self.drag_from is None and self.anim.state() != QVariantAnimation.Running and animations_on():
            if abs(self.velocity) > 5:
                self.angle += self.velocity * dt
                self.velocity *= 0.04 ** dt
            elif now - self.idle_since > 3:
                self.angle += 14 * dt  # a slow showcase turn
        self.update()

    def set_skin(self, data: bytes, slim: bool):
        img = QImage.fromData(data, "PNG") if data else None
        self.img = img.convertToFormat(QImage.Format_ARGB32) if img is not None and not img.isNull() else None
        self.img_key = hash(data) if data else None
        self.slim = slim
        self.update()

    def set_cape(self, data_or_path, frames=None, frame_ms=125):
        self.cape = None
        self.cape_frames = None
        self.cape_ms = max(20, frame_ms) / 1000.0
        if frames:
            imgs = [QImage(str(f)) for f in frames]
            self.cape_frames = [i.convertToFormat(QImage.Format_ARGB32) for i in imgs if not i.isNull()] or None
            self.cape_key = _file_key(frames[0])
        elif data_or_path:
            img = QImage(str(data_or_path)) if not isinstance(data_or_path, (bytes, bytearray)) \
                else QImage.fromData(data_or_path, "PNG")
            self.cape = img.convertToFormat(QImage.Format_ARGB32) if not img.isNull() else None
            self.cape_key = _file_key(data_or_path) if not isinstance(data_or_path, (bytes, bytearray)) else hash(data_or_path)
        self.update()

    def set_backdrop(self, path):
        from PySide6.QtGui import QPixmap
        pm = QPixmap(str(path)) if path else QPixmap()
        self.backdrop = pm if not pm.isNull() else None
        self.update()

    def show_back(self, back=True):
        """Turns round to the back (to show off a cape) or the front."""
        base = math.floor((self.angle + 180) / 360) * 360
        end = base + (180.0 if back else 0.0)
        if abs(end - self.angle) > 180:
            end += -360 if end > self.angle else 360
        self.velocity = 0
        self.idle_since = time.monotonic()
        if not animations_on():
            self._turn(end)
            return
        self.anim.stop()
        self.anim.setStartValue(self.angle)
        self.anim.setEndValue(end)
        self.anim.start()

    def mousePressEvent(self, e):
        self.anim.stop()
        self.drag_from = e.position()
        self.dragged = False
        self.velocity = 0
        self.setCursor(Qt.ClosedHandCursor)

    def mouseMoveEvent(self, e):
        if self.drag_from is None:
            return
        d = e.position() - self.drag_from
        if abs(d.x()) + abs(d.y()) > 2:
            self.dragged = True
        self.angle += d.x() * 0.9
        self.pitch = max(-35.0, min(25.0, self.pitch - d.y() * 0.4))
        self.velocity = d.x() * 0.9 * 40
        self.drag_from = e.position()
        self.idle_since = time.monotonic()
        self.update()

    def mouseReleaseEvent(self, e):
        self.drag_from = None
        self.setCursor(Qt.OpenHandCursor)
        self.idle_since = time.monotonic()
        if not self.dragged:  # a click: spin round
            a = self.angle % 360
            self.show_back(not (90 < a < 270))

    def hideEvent(self, e):
        self.timer.stop()

    def showEvent(self, e):
        self.last = time.monotonic()
        self.timer.start()

    def paintEvent(self, _):
        p = QPainter(self)
        p.setRenderHint(QPainter.Antialiasing)
        w, h = self.width(), self.height()
        t = time.monotonic() - self.t0
        if self.backdrop is not None:
            clip = QPainterPath()
            clip.addRoundedRect(QRectF(self.rect()), 14, 14)
            p.save()
            p.setClipPath(clip)
            p.setRenderHint(QPainter.SmoothPixmapTransform)
            bw, bh = self.backdrop.width(), self.backdrop.height()
            k = max(w / bw, h / bh)
            p.drawPixmap(QRectF((w - bw * k) / 2, (h - bh * k) / 2, bw * k, bh * k), self.backdrop,
                         QRectF(self.backdrop.rect()))
            p.fillRect(self.rect(), QColor(0, 0, 0, 70))
            p.restore()
        # a soft spotlight and a shadow on the floor
        glow = QColor(Theme.accent)
        glow.setAlpha(40)
        from PySide6.QtGui import QRadialGradient
        rg = QRadialGradient(w / 2, h * 0.45, w * 0.6)
        rg.setColorAt(0, glow)
        g2 = QColor(glow)
        g2.setAlpha(0)
        rg.setColorAt(1, g2)
        p.fillRect(self.rect(), rg)
        s = max(3.0, min((w - 40) / 20.0, (h - 50) / 34.0, float(self.scale)))
        top = (h - 32 * s) / 2 - 6
        p.setPen(Qt.NoPen)
        p.setBrush(QColor(0, 0, 0, 80))
        p.drawEllipse(QRectF(w / 2 - 8 * s, top + 32 * s - 4, 16 * s, 10))
        if self.img is None:
            p.setPen(QColor(Theme.p["faint"]))
            p.drawText(self.rect(), Qt.AlignCenter, "Pick a skin")
            return
        cape, cape_key = self.cape, self.cape_key
        if self.cape_frames:
            i = int(t / self.cape_ms) % len(self.cape_frames)
            cape, cape_key = self.cape_frames[i], f"{self.cape_key}#{i}"
        bob = math.sin(t * 2.0) * 1.2 if animations_on() else 0
        paint_3d(p, self.shaded, self.img, self.img_key, self.slim, cape, cape_key, w / 2, top + bob, s,
                 self.angle, self.pitch, t)
        p.end()


class _Card(QWidget):
    """A tile in the Locker grid: picture, name, a ring when picked, a lift on hover."""
    clicked = Signal()

    def __init__(self, title, sub="", w=118, h=168):
        super().__init__()
        self.title, self.sub = title, sub
        self.setFixedSize(w, h)
        self.setCursor(Qt.PointingHandCursor)
        self.selected = False
        self.worn = False
        self.hover = 0.0
        self.anim = QVariantAnimation(self, duration=160, easingCurve=QEasingCurve.OutCubic)
        self.anim.valueChanged.connect(self._set)
        self.shown_t = 1.0  # 0..1 as the card pops in

    def appear(self, delay_ms):
        """Pops in: rises and fades into place (cards arrive one after another)."""
        if not animations_on():
            return
        self.shown_t = 0.0
        a = QVariantAnimation(self, duration=380, startValue=0.0, endValue=1.0, easingCurve=QEasingCurve.OutBack)
        a.valueChanged.connect(lambda v: (setattr(self, "shown_t", float(v)), self.update()))
        self._appear = a
        QTimer.singleShot(delay_ms, a.start)

    def _set(self, v):
        self.hover = float(v)
        self.update()

    def _go(self, to):
        if not animations_on():
            return self._set(to)
        self.anim.stop()
        self.anim.setStartValue(self.hover)
        self.anim.setEndValue(to)
        self.anim.start()

    def enterEvent(self, e):
        self._go(1.0)

    def leaveEvent(self, e):
        self._go(0.0)

    def mouseReleaseEvent(self, e):
        if e.button() == Qt.LeftButton and self.rect().contains(e.position().toPoint()):
            self.clicked.emit()

    def set_selected(self, on):
        self.selected = on
        self.update()

    def picture(self, p, r):
        pass

    def paintEvent(self, _):
        p = QPainter(self)
        p.setRenderHint(QPainter.Antialiasing)
        if self.shown_t < 1.0:
            p.setOpacity(max(0.0, min(1.0, self.shown_t)))
            p.translate(0, (1.0 - self.shown_t) * 16)
        lift = 3 * self.hover
        r = QRectF(self.rect()).adjusted(2, 2 - lift + 3, -2, -2 - lift)
        path = QPainterPath()
        path.addRoundedRect(r, 16, 16)
        dark = Theme.name != "light"
        fill = QColor(255, 255, 255, int(12 + 14 * self.hover)) if dark else QColor(20, 24, 40, int(10 + 10 * self.hover))
        p.fillPath(path, fill)
        edge = QColor(Theme.accent) if self.selected else (QColor(255, 255, 255, 30) if dark else QColor(20, 24, 40, 30))
        p.setPen(QPen(edge, 2 if self.selected else 1))
        p.drawPath(path)
        self.picture(p, QRectF(r.left() + 8, r.top() + 8, r.width() - 16, r.height() - 52))
        p.setPen(QColor(Theme.p["text"]))
        f = p.font()
        f.setPixelSize(13)
        f.setBold(True)
        p.setFont(f)
        p.drawText(QRectF(r.left() + 8, r.bottom() - 42, r.width() - 16, 18), Qt.AlignHCenter | Qt.AlignVCenter,
                   p.fontMetrics().elidedText(self.title, Qt.ElideRight, int(r.width() - 16)))
        f.setBold(False)
        f.setPixelSize(11)
        p.setFont(f)
        p.setPen(QColor(Theme.accent if self.worn else Theme.p["muted"]))
        p.drawText(QRectF(r.left() + 8, r.bottom() - 24, r.width() - 16, 16), Qt.AlignHCenter | Qt.AlignVCenter,
                   "Wearing" if self.worn else self.sub)
        p.end()


class SkinCard(_Card):
    def __init__(self, title, sub, data, slim):
        super().__init__(title, sub)
        img = QImage.fromData(data, "PNG") if data else QImage()
        self.img = img.convertToFormat(QImage.Format_ARGB32) if not img.isNull() else None
        self.slim = slim

    def picture(self, p, r):
        s = min(r.width() / 16, r.height() / 32)
        paint_skin(p, self.img, self.slim, False, r.center().x() - 8 * s, r.top() + (r.height() - 32 * s) / 2, s)


def _file_key(path):
    """A picture's cache key: its path and when it last changed (your own cape keeps its file names)."""
    try:
        return f"{str(path)[:200]}@{Path(path).stat().st_mtime_ns}"
    except (OSError, TypeError, ValueError):
        return str(path)[:200]


class CapeCard(_Card):
    def __init__(self, title, sub, image, frames=None, frame_ms=125):
        super().__init__(title, sub)
        self.img = image
        self.frames = [QImage(str(f)) for f in frames] if frames else None
        self.ms = max(20, frame_ms) / 1000.0
        if self.frames:
            self._t0 = time.monotonic()
            self._timer = QTimer(self, interval=max(20, int(frame_ms)))
            self._timer.timeout.connect(self._next)
            self._timer.start()

    def _next(self):
        self.img = self.frames[int((time.monotonic() - self._t0) / self.ms) % len(self.frames)]
        self.update()

    def picture(self, p, r):
        if self.img is None or self.img.isNull():
            p.setPen(QPen(QColor(Theme.p["faint"]), 2, Qt.DashLine))
            p.drawRoundedRect(r.adjusted(r.width() / 2 - 26, 6, -(r.width() / 2 - 26), -6), 6, 6)
            return
        k = self.img.width() / 64.0
        front = self.img.copy(round(k), round(k), round(10 * k), round(16 * k))
        s = min(r.width() / 10, r.height() / 16)
        p.setRenderHint(QPainter.SmoothPixmapTransform, k > 1)
        p.drawImage(QRectF(r.center().x() - 5 * s, r.top() + (r.height() - 16 * s) / 2, 10 * s, 16 * s), front)


class BackgroundCard(_Card):
    def __init__(self, title, sub, path):
        super().__init__(title, sub, w=196, h=150)
        from PySide6.QtGui import QPixmap
        pm = QPixmap(str(path)) if path else QPixmap()
        self.pm = pm if not pm.isNull() else None

    def picture(self, p, r):
        box = QRectF(r.left(), r.top() + 2, r.width(), r.width() * 9 / 16)
        clip = QPainterPath()
        clip.addRoundedRect(box, 10, 10)
        p.save()
        p.setClipPath(clip)
        if self.pm is not None:
            p.setRenderHint(QPainter.SmoothPixmapTransform)
            bw, bh = self.pm.width(), self.pm.height()
            k = max(box.width() / bw, box.height() / bh)
            p.drawPixmap(QRectF(box.center().x() - bw * k / 2, box.center().y() - bh * k / 2, bw * k, bh * k), self.pm,
                         QRectF(self.pm.rect()))
        else:  # Minecraft's own panorama: a little sky and grass
            p.fillRect(box, QColor("#4a6fa5"))
            p.fillRect(QRectF(box.left(), box.top() + box.height() * 0.66, box.width(), box.height() * 0.34),
                       QColor("#5d8a3a"))
            p.fillRect(QRectF(box.left(), box.top() + box.height() * 0.8, box.width(), box.height() * 0.2),
                       QColor("#7a5a3a"))
        p.restore()


class AddCard(_Card):
    def __init__(self, title, sub, icon_name):
        super().__init__(title, sub)
        self.icon_name = icon_name

    def picture(self, p, r):
        size = 34
        circle = QRectF(r.center().x() - 30, r.center().y() - 30, 60, 60)
        c = QColor(Theme.accent)
        c.setAlpha(int(40 + 40 * self.hover))
        p.setPen(Qt.NoPen)
        p.setBrush(c)
        p.drawEllipse(circle)
        p.drawPixmap(int(r.center().x() - size / 2), int(r.center().y() - size / 2),
                     icon_pixmap(self.icon_name, Theme.accent, size))


# ---------------------------------------------------------------------- the page

class LockerPage(QWidget):

    def __init__(self, say=None):
        super().__init__(objectName="page")
        self.say = say or (lambda text: None)
        self.tab = "skins"
        self.picked = None          # ("skin", entry) / ("account-skin", data) / ("pcape", i) / ("mcape", cape)
        self.account_profile = None
        self.account_skin = None    # bytes of the skin on your account
        self.cards = []
        v = QVBoxLayout(self)
        v.setContentsMargins(56, 30, 56, 20)
        v.setSpacing(16)
        head = QHBoxLayout()
        col = QVBoxLayout()
        col.setSpacing(10)
        col.addWidget(Chip("Your look"))
        col.addWidget(big_title("Locker"))
        head.addLayout(col)
        head.addStretch()
        self.tabs = Segmented([("skins", "Skins"), ("capes", "Capes"), ("backgrounds", "Menu background")], "skins")
        self.tabs.changed.connect(self.show_tab)
        head.addWidget(self.tabs, 0, Qt.AlignBottom)
        v.addLayout(head)

        body = QHBoxLayout()
        body.setSpacing(24)
        # left: the preview
        left = QWidget(objectName="glassCard")
        left.setFixedWidth(300)
        lv = QVBoxLayout(left)
        lv.setContentsMargins(18, 16, 18, 18)
        lv.setSpacing(10)
        self.view = SkinView(12)
        lv.addWidget(self.view, 1)
        turn = QHBoxLayout()
        turn.setSpacing(6)
        turn.addStretch()
        for label, back in (("Front", False), ("Back", True)):
            b = PillButton(label, None, "ghost")
            b.setFixedHeight(34)
            b.clicked.connect(lambda _=False, back=back: self.view.show_back(back))
            turn.addWidget(b)
        turn.addStretch()
        lv.addLayout(turn)
        self.pick_name = QLabel("", objectName="pillTitle", alignment=Qt.AlignCenter)
        lv.addWidget(self.pick_name)
        self.arms = Segmented([("classic", "Classic arms"), ("slim", "Slim arms")], "classic")
        self.arms.changed.connect(self.set_arms)
        lv.addWidget(self.arms, 0, Qt.AlignHCenter)
        self.wear = PillButton("Wear", "check", "primary")
        self.wear.clicked.connect(self.wear_picked)
        lv.addWidget(self.wear, 0, Qt.AlignHCenter)
        self.status = QLabel("", objectName="faint", alignment=Qt.AlignCenter, wordWrap=True)
        lv.addWidget(self.status)
        lv.addStretch()
        body.addWidget(left)
        # right: the grid
        grid_host = QWidget()
        self.grid_col = QVBoxLayout(grid_host)
        self.grid_col.setContentsMargins(0, 0, 8, 0)
        self.grid_col.setSpacing(12)
        body.addWidget(scroll(grid_host), 1)
        v.addLayout(body, 1)
        self.loaded = False

    # ------------------------------------------------------------ data

    def build(self):
        """Called when the page opens: refresh what your account wears."""
        self.view.set_backdrop(locker.background_asset(locker.menu_background()))
        worn = locker.peregrine_cape()
        self.view.set_cape(locker.cape_asset(worn), locker.cape_frames(worn))
        self.render(pop=True)
        acct = auth.active_account()
        if acct and acct.get("type") == "msa":
            self.status.setText("Checking your account…")

            def got(prof):
                self.account_profile = prof
                self.status.setText("")
                if prof["skin"]:
                    workers.run(locker.fetch_image, prof["skin"]["url"], done=self._got_account_skin,
                                failed=lambda m: None)
                for c in prof["capes"]:
                    workers.run(locker.fetch_image, c["url"], done=lambda d, c=c: self._got_cape_image(c, d),
                                failed=lambda m: None)
                self.render()
            workers.run(locker.profile, acct, done=got, failed=lambda m: self.status.setText(m))
        else:
            self.account_profile = None
            self.status.setText("Sign in with Microsoft to wear skins and official capes. "
                                "You can still collect skins and pick your Peregrine cape.")

    def _got_account_skin(self, data):
        self.account_skin = data
        if self.picked is None:
            self.pick(("account-skin", data))
        self.render()

    def _got_cape_image(self, cape, data):
        cape["image"] = data
        if cape.get("active"):
            self.view.set_cape(data)
        self.render()

    # ------------------------------------------------------------ grid

    def show_tab(self, key):
        self.tab = key
        self.tabs.set(key)
        self.render(pop=True)

    def _grid(self, title, cards):
        if title:
            self.grid_col.addWidget(section_label(title))
        holder = QWidget()
        g = QGridLayout(holder)
        g.setContentsMargins(0, 0, 0, 0)
        g.setSpacing(12)
        card_w = max((c.width() for c in cards), default=118) + 12
        cols = max(1, (self.width() - 300 - 24 - 112 - 20) // card_w)
        for i, c in enumerate(cards):
            g.addWidget(c, i // cols, i % cols)
            self.cards.append(c)
            if getattr(self, "_pop", False):
                c.appear(40 * (len(self.cards) - 1))
        g.setColumnStretch(cols, 1)
        self.grid_col.addWidget(holder)
        from .modern import fade_in
        fade_in(holder, 220, 0)

    def render(self, pop=False):
        self._pop = pop
        clear(self.grid_col)
        self.cards = []
        if self.tab == "skins":
            cards = []
            if self.account_skin:
                variant = (self.account_profile or {}).get("skin", {}) or {}
                c = SkinCard("On your account", variant.get("variant", "classic").title(), self.account_skin,
                             variant.get("variant") == "slim")
                c.worn = True
                c.key = ("account-skin", self.account_skin)
                c.clicked.connect(lambda c=c: self.pick(c.key))
                cards.append(c)
            add = AddCard("Add a skin", "From a file", "plus")
            add.clicked.connect(self.add_file)
            cards.append(add)
            copy = AddCard("Copy a player", "By their name", "user_plus")
            copy.clicked.connect(self.add_player)
            cards.append(copy)
            self._grid("", cards)
            lib = locker.skins()
            if lib:
                lcards = []
                for s in lib:
                    try:
                        data = locker.skin_bytes(s)
                    except OSError:
                        continue
                    c = SkinCard(s["name"], s["variant"].title(), data, s["variant"] == "slim")
                    c.key = ("skin", s)
                    c.clicked.connect(lambda c=c: self.pick(c.key))
                    lcards.append(c)
                self._grid(f"Your skins  ({len(lcards)})", lcards)
        elif self.tab == "backgrounds":
            bcards = []
            current = locker.menu_background()
            for i, name in enumerate(locker.BACKGROUNDS):
                path = locker.background_asset(i)
                if i == locker.CUSTOM and path is None:
                    c = AddCard("Your picture", "Choose one", "image")
                    c.clicked.connect(self.pick_picture)
                    bcards.append(c)
                    continue
                c = BackgroundCard(name, "Minecraft" if i == 0 else ("Yours" if i == locker.CUSTOM else "Peregrine"), path)
                c.worn = i == current
                c.key = ("bg", i)
                c.clicked.connect(lambda i=i: self.set_background(i))
                bcards.append(c)
            if locker.background_asset(locker.CUSTOM) is not None:
                c = AddCard("Change picture", "Choose another", "image")
                c.clicked.connect(self.pick_picture)
                bcards.append(c)
            self._grid("Behind Peregrine's main menu in game", bcards)
        else:
            pc = []
            worn = locker.peregrine_cape()
            for i, name in enumerate(locker.CAPE_NAMES):
                if i == locker.CUSTOM_CAPE:
                    continue
                path = locker.cape_asset(i)
                c = CapeCard(name, "Moving" if locker.cape_frames(i) else "Peregrine",
                             QImage(str(path)) if path else None, locker.cape_frames(i))
                c.worn = i == worn
                c.key = ("pcape", i)
                c.clicked.connect(lambda c=c: self.pick(c.key))
                pc.append(c)
            self._grid("Peregrine capes · other Peregrine players see these", pc)
            own = []
            if custom_cape.exists():
                i = locker.CUSTOM_CAPE
                frames = locker.cape_frames(i)
                path = locker.cape_asset(i)
                c = CapeCard("Your own", "Moving" if frames else "Picture",
                             QImage(str(path)) if path else None, frames, locker.cape_frame_ms(i))
                c.worn = i == worn
                c.key = ("pcape", i)
                c.clicked.connect(lambda c=c: self.pick(c.key))
                own.append(c)
                add = AddCard("New cape", "Image, GIF, video", "image")
            else:
                add = AddCard("Make your own", "Image, GIF, video", "image")
            add.clicked.connect(self.pick_cape_file)
            own.append(add)
            self._grid("Your own cape · up to 4K, only you see it", own)
            prof = self.account_profile
            if prof and prof["capes"]:
                mc = []
                none = CapeCard("No cape", "Minecraft", None)
                none.worn = not any(c["active"] for c in prof["capes"])
                none.key = ("mcape", None)
                none.clicked.connect(lambda c=none: self.pick(c.key))
                mc.append(none)
                for cape in prof["capes"]:
                    img = QImage.fromData(cape["image"], "PNG") if cape.get("image") else None
                    c = CapeCard(cape["alias"], "Minecraft", img)
                    c.worn = cape["active"]
                    c.key = ("mcape", cape)
                    c.clicked.connect(lambda c=c: self.pick(c.key))
                    mc.append(c)
                self._grid("Your Minecraft capes · everyone sees these", mc)
        self.grid_col.addStretch()
        self._mark()

    def _mark(self):
        for c in self.cards:
            k = getattr(c, "key", None)
            c.set_selected(k is not None and self.picked is not None and k[0] == self.picked[0]
                           and (k[1] is self.picked[1] or k[1] == self.picked[1]))

    def resizeEvent(self, e):
        super().resizeEvent(e)
        if self.isVisible():
            QTimer.singleShot(0, self.render)

    # ------------------------------------------------------------ picking and wearing

    def pick(self, key):
        self.picked = key
        kind, what = key
        self.arms.setVisible(kind == "skin")
        if kind == "skin":
            self.view.set_skin(locker.skin_bytes(what), what["variant"] == "slim")
            self.arms.set(what["variant"])
            self.pick_name.setText(what["name"])
            self.wear.setText("WEAR THIS SKIN")
            self.view.show_back(False)
        elif kind == "account-skin":
            v = ((self.account_profile or {}).get("skin") or {}).get("variant", "classic")
            self.view.set_skin(what, v == "slim")
            self.pick_name.setText("Your skin right now")
            self.wear.setText("SAVE TO MY SKINS")
            self.view.show_back(False)
        elif kind == "pcape":
            self.view.set_cape(locker.cape_asset(what), locker.cape_frames(what), locker.cape_frame_ms(what))
            self.pick_name.setText("Your own cape" if what == locker.CUSTOM_CAPE
                                   else locker.CAPE_NAMES[what] + " cape" if what else "No Peregrine cape")
            self.wear.setText("WEAR THIS CAPE")
            self.view.show_back(bool(what))
        elif kind == "mcape":
            self.view.set_cape(what.get("image") if what else None)
            self.pick_name.setText(what["alias"] if what else "No Minecraft cape")
            self.wear.setText("WEAR THIS CAPE")
            self.view.show_back(bool(what))
        if self.view.img is None and self.account_skin:
            v = ((self.account_profile or {}).get("skin") or {}).get("variant", "classic")
            self.view.set_skin(self.account_skin, v == "slim")
        self.wear.updateGeometry()
        self.status.setText("")
        self._mark()

    def set_arms(self, variant):
        if self.picked and self.picked[0] == "skin":
            locker.rename(self.picked[1]["id"], variant=variant)
            self.picked[1]["variant"] = variant
            self.view.set_skin(locker.skin_bytes(self.picked[1]), variant == "slim")
            self.render()

    def set_background(self, index):
        locker.set_menu_background(index)
        self.view.set_backdrop(locker.background_asset(index))
        self._done(f"Main menu background: {locker.BACKGROUNDS[index]}. It shows next time the game opens its menu.")
        self.render()

    def pick_picture(self):
        path, _ = QFileDialog.getOpenFileName(self, "Choose a picture", "", "Pictures (*.png *.jpg *.jpeg *.webp *.bmp)")
        if not path:
            return
        try:
            locker.set_custom_background(path)
        except Exception as e:
            from .sheet import tell
            tell(self, "Couldn't use that picture", str(e), "error")
            return
        self.set_background(locker.CUSTOM)

    # ------------------------------------------------------------ your own cape

    CAPE_FILTER = ("Pictures and videos (*.png *.jpg *.jpeg *.webp *.gif *.bmp *.tga *.tif *.tiff "
                   "*.mp4 *.webm *.mov *.mkv *.m4v *.avi)")

    def pick_cape_file(self):
        if getattr(self, "_making_cape", False):
            return
        path, _ = QFileDialog.getOpenFileName(self, "Choose a picture, GIF or video for your cape", "", self.CAPE_FILTER)
        if not path:
            return
        from .sheet import tell
        try:
            what = custom_cape.kind(path)
        except custom_cape.CapeError as e:
            tell(self, "Can't make a cape from that", str(e), "error")
            return
        self._making_cape = True
        self._busy("Making your cape…")
        if what == "video" and not shutil.which("ffmpeg"):
            # no ffmpeg: Qt plays the video here (the window keeps going), then the rest happens in the background
            try:
                got = custom_cape.read_video_qt(path, progress=self.status.setText)
            except custom_cape.CapeError as e:
                got = e
            if not got or isinstance(got, Exception):
                self._making_cape = False
                self._idle()
                tell(self, "Couldn't read that video",
                     (str(got) + " " if isinstance(got, Exception) else "")
                     + "Try a GIF, or install ffmpeg so Peregrine can read any video.", "error")
                return
            self.status.setText("Making your cape…")
            workers.run(custom_cape.make, got, Path(path).name, done=self._cape_made, failed=self._cape_failed)
            return
        workers.run(custom_cape.make_from_file, path, done=self._cape_made, failed=self._cape_failed)

    def _cape_made(self, meta):
        self._making_cape = False
        locker.set_peregrine_cape(locker.CUSTOM_CAPE)
        w, h = (meta or {}).get("size", [0, 0])
        n = (meta or {}).get("frames", 1)
        size = f"{w} × {h}" + (f", {n} frames" if n > 1 else "")
        self.render()
        self.pick(("pcape", locker.CUSTOM_CAPE))
        self._done(f"Your cape is ready and on ({size}). Only you see it, in Minecraft 26.1.")

    def _cape_failed(self, message):
        self._making_cape = False
        self._idle()
        from .sheet import tell
        tell(self, "Couldn't make your cape", message, "error")

    def _idle(self):
        self.status.setText("")
        self.wear.setEnabled(True)

    def _busy(self, text):
        self.status.setText(text)
        self.wear.setEnabled(False)

    def _done(self, text):
        self.status.setText(text)
        self.wear.setEnabled(True)
        self.say(text)

    def wear_picked(self):
        if not self.picked:
            self.status.setText("Pick a skin or cape first.")
            return
        kind, what = self.picked
        acct = auth.active_account()
        if kind == "pcape":
            locker.set_peregrine_cape(what)
            from .. import profiles
            if acct and acct.get("type") == "msa" and profiles.available():
                workers.run(lambda: profiles.announce(acct, force=True), failed=lambda m: None)
            if what == locker.CUSTOM_CAPE:
                self._done("Wearing your own cape in game. Only you see it; other players see no cape.")
            else:
                self._done(f"Wearing the {locker.CAPE_NAMES[what]} cape in game" if what else "Peregrine cape off")
            self.render()
            return
        if kind == "account-skin":
            entry = locker.add_skin(what, "My skin", ((self.account_profile or {}).get("skin") or {}).get("variant"))
            self._done("Saved to your skins")
            self.render()
            self.pick(("skin", entry))
            return
        if kind == "skin":
            self._busy("Putting it on your account…")

            def worn(prof):
                self.account_profile = prof
                self.account_skin = locker.skin_bytes(what)
                self._done("Done! Rejoin a world or server to see your new skin.")
                self.render()
            workers.run(locker.wear_skin, acct, what, done=worn, failed=lambda m: self._done(m))
            return
        if kind == "mcape":
            self._busy("Changing your cape…")

            def changed(_):
                if self.account_profile:
                    for c in self.account_profile["capes"]:
                        c["active"] = bool(what) and c["id"] == what["id"]
                self._done("Done! Rejoin to see it.")
                self.render()
            workers.run(locker.wear_cape, acct, what["id"] if what else None, done=changed,
                        failed=lambda m: self._done(m))

    def add_file(self):
        path, _ = QFileDialog.getOpenFileName(self, "Add a skin", "", "Skins (*.png)")
        if not path:
            return
        try:
            entry = locker.add_skin_file(path)
        except Exception as e:
            from .sheet import tell
            tell(self, "Couldn't add that skin", str(e), "error")
            return
        self.render()
        self.pick(("skin", entry))

    def add_player(self):
        from .sheet import get_text, tell
        name, ok = get_text(self, "Copy a player's skin", "Type their Minecraft name. Their current skin "
                                   "goes into your skins.", "Minecraft name", ok="Copy")
        if not ok or not name:
            return
        self.status.setText(f"Getting {name}'s skin…")

        def got(entry):
            self.status.setText("")
            self.render()
            self.pick(("skin", entry))
        workers.run(locker.add_skin_from_player, name, done=got,
                    failed=lambda m: (self.status.setText(""), tell(self, "Couldn't copy that skin", m, "error")))

    def contextMenuEvent(self, e):
        """Right-click a skin to rename or remove it."""
        child = self.childAt(e.pos())
        while child is not None and not isinstance(child, SkinCard):
            child = child.parentWidget()
        if child is None or getattr(child, "key", (None,))[0] != "skin":
            return
        entry = child.key[1]
        from PySide6.QtWidgets import QMenu
        from .theme import round_menu
        menu = round_menu(QMenu(self))
        menu.addAction("Rename", lambda: self._rename(entry))
        menu.addAction("Remove", lambda: self._remove(entry))
        menu.exec(e.globalPos())

    def _rename(self, entry):
        from .sheet import get_text
        name, ok = get_text(self, "Rename skin", "", "Name", entry["name"], ok="Save")
        if ok and name:
            locker.rename(entry["id"], name=name)
            self.render()

    def _remove(self, entry):
        from .sheet import ask
        if ask(self, f"Remove {entry['name']}?", "It's taken out of your skins (not off your account).",
               "Remove", kind="danger"):
            locker.remove(entry["id"])
            if self.picked and self.picked[0] == "skin" and self.picked[1]["id"] == entry["id"]:
                self.picked = None
            self.render()

    def sizeHint(self):
        return QSize(900, 600)
