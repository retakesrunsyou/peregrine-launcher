"""The Locker: try on skins and capes, then wear them.

On the left, your player turns around (click to spin) wearing what you've picked. On the
right, your skin library and your capes. "Wear" puts a skin or official cape on your
Minecraft account; Peregrine capes are picked here and show in game for you and other
Peregrine players.
"""

import math
import time

from PySide6.QtCore import QEasingCurve, QRectF, QSize, Qt, QTimer, QVariantAnimation, Signal
from PySide6.QtGui import QColor, QImage, QPainter, QPainterPath, QPen
from PySide6.QtWidgets import QFileDialog, QGridLayout, QHBoxLayout, QLabel, QVBoxLayout, QWidget

from .. import auth, locker
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


class SkinView(QWidget):
    """The big player in the Locker: idles gently, spins around when clicked."""

    def __init__(self, scale=9):
        super().__init__()
        self.scale = scale
        self.img = None
        self.slim = False
        self.cape = None
        self.backdrop = None  # the game's main menu background, behind your player
        self.angle = 0.0   # 0 = front, 180 = back
        self.t0 = time.monotonic()
        self.setMinimumSize(16 * 5 + 60, 32 * 5 + 50)
        from PySide6.QtWidgets import QSizePolicy
        self.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Expanding)
        self.setCursor(Qt.PointingHandCursor)
        self.setToolTip("Click to turn around")
        self.timer = QTimer(self, interval=33)
        self.timer.timeout.connect(self.update)
        self.timer.start()
        self.anim = QVariantAnimation(self, duration=650, easingCurve=QEasingCurve.InOutCubic)
        self.anim.valueChanged.connect(self._turn)

    def sizeHint(self):
        return QSize(16 * self.scale + 80, 32 * self.scale + 70)

    def _turn(self, v):
        self.angle = float(v)
        self.update()

    def set_skin(self, data: bytes, slim: bool):
        img = QImage.fromData(data, "PNG") if data else None
        self.img = img.convertToFormat(QImage.Format_ARGB32) if img is not None and not img.isNull() else None
        self.slim = slim
        self.update()

    def set_cape(self, data_or_path):
        self.cape = None
        if data_or_path:
            img = QImage(str(data_or_path)) if not isinstance(data_or_path, (bytes, bytearray)) \
                else QImage.fromData(data_or_path, "PNG")
            self.cape = img if not img.isNull() else None
        self.update()

    def set_backdrop(self, path):
        from PySide6.QtGui import QPixmap
        pm = QPixmap(str(path)) if path else QPixmap()
        self.backdrop = pm if not pm.isNull() else None
        self.update()

    def show_back(self, back=True):
        end = 180.0 if back else 0.0
        if not animations_on():
            self._turn(end)
            return
        self.anim.stop()
        self.anim.setStartValue(self.angle)
        self.anim.setEndValue(end)
        self.anim.start()

    def mousePressEvent(self, e):
        self.show_back(self.angle < 90)

    def hideEvent(self, e):
        self.timer.stop()

    def showEvent(self, e):
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
        glow.setAlpha(36)
        from PySide6.QtGui import QRadialGradient
        rg = QRadialGradient(w / 2, h * 0.45, w * 0.6)
        rg.setColorAt(0, glow)
        g2 = QColor(glow)
        g2.setAlpha(0)
        rg.setColorAt(1, g2)
        p.fillRect(self.rect(), rg)
        s = max(3.0, min((w - 40) / 18.0, (h - 50) / 32.0, float(self.scale)))
        sx = abs(math.cos(math.radians(self.angle)))
        p.setPen(Qt.NoPen)
        p.setBrush(QColor(0, 0, 0, 70))
        foot = (h - 32 * s) / 2 - 10 + 32 * s
        p.drawEllipse(QRectF(w / 2 - 9 * s * max(0.5, sx), foot - 6, 18 * s * max(0.5, sx), 12))
        if self.img is None:
            p.setPen(QColor(Theme.p["faint"]))
            p.drawText(self.rect(), Qt.AlignCenter, "Pick a skin")
            return
        bob = math.sin(t * 2.0) * 1.5 if animations_on() else 0
        back = self.angle > 90
        p.save()
        p.translate(w / 2, 0)
        p.scale(max(0.02, sx), 1)
        paint_skin(p, self.img, self.slim, back, -8 * s, (h - 32 * s) / 2 - 10 + bob, s, self.cape)
        p.restore()
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


class CapeCard(_Card):
    def __init__(self, title, sub, image):
        super().__init__(title, sub)
        self.img = image

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
        self.view = SkinView(8)
        lv.addWidget(self.view, 1)
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
        self.view.set_cape(locker.cape_asset(locker.peregrine_cape()))
        self.render()
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
        self.render()

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
        g.setColumnStretch(cols, 1)
        self.grid_col.addWidget(holder)
        from .modern import fade_in
        fade_in(holder, 220, 0)

    def render(self):
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
                path = locker.cape_asset(i)
                c = CapeCard(name, "Peregrine", QImage(str(path)) if path else None)
                c.worn = i == worn
                c.key = ("pcape", i)
                c.clicked.connect(lambda c=c: self.pick(c.key))
                pc.append(c)
            self._grid("Peregrine capes · other Peregrine players see these", pc)
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
            self.view.set_cape(locker.cape_asset(what))
            self.pick_name.setText(locker.CAPE_NAMES[what] + " cape" if what else "No Peregrine cape")
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
