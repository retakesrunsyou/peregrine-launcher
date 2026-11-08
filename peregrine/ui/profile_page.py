"""Library: one instance at a glance. Its details, quick actions (Play, Update all, Add mods)
and the optimization mods Peregrine keeps on it, each with a switch."""

import re

from PySide6.QtCore import QPointF, QRectF, Qt, Signal
from PySide6.QtGui import QColor, QFont, QLinearGradient, QPainter, QPainterPath, QPen, QPixmap
from PySide6.QtWidgets import (
    QAbstractButton, QFrame, QHBoxLayout, QLabel, QMenu, QPushButton, QSizePolicy, QVBoxLayout, QWidget,
)

from .. import instances, modrinth, performance
from . import workers
from .pages import clear, last_played, scroll
from .theme import Theme, icon_pixmap, round_menu


def _version_of(filename: str, mc: str) -> str:
    """'sodium-fabric-0.6.13+mc1.21.1.jar' -> '0.6.13' (the mod's own version, not Minecraft's)."""
    name = re.sub(r"\.jar(\.disabled)?$", "", filename)
    name = re.sub(r"[+\-_]?mc\d+(\.\d+)+", "", name)   # "+mc1.21.1", "-mc1.21.1"
    name = name.replace(f"-{mc}-", "-").replace(f"-{mc}", "")
    for m in re.finditer(r"\d+(?:\.\d+)+(?:-(?:alpha|beta|rc|pre)[\w.]*)?", name):
        if m.group(0) != mc:
            return m.group(0)
    return ""


class _Underline(QWidget):
    """The accent line under the page title, fading out to the right."""

    def __init__(self, parent=None):
        super().__init__(parent)
        self.setFixedHeight(3)

    def paintEvent(self, _):
        p = QPainter(self)
        p.setRenderHint(QPainter.Antialiasing)
        g = QLinearGradient(0, 0, self.width(), 0)
        c = QColor(Theme.accent)
        g.setColorAt(0, c)
        c2 = QColor(c)
        c2.setAlpha(120)
        g.setColorAt(0.75, c2)
        c3 = QColor(c)
        c3.setAlpha(0)
        g.setColorAt(1, c3)
        path = QPainterPath()
        path.addRoundedRect(QRectF(self.rect()), 1.5, 1.5)
        p.fillPath(path, g)
        p.end()


class Check(QAbstractButton):
    """A rounded accent checkbox."""

    def __init__(self, on: bool, parent=None):
        super().__init__(parent)
        self.setCheckable(True)
        self.setChecked(on)
        self.setCursor(Qt.PointingHandCursor)
        self.setFixedSize(24, 24)

    def paintEvent(self, _):
        p = QPainter(self)
        p.setRenderHint(QPainter.Antialiasing)
        r = QRectF(2, 2, 20, 20)
        path = QPainterPath()
        path.addRoundedRect(r, 6, 6)
        if self.isChecked():
            p.fillPath(path, QColor(Theme.accent))
            pen = QPen(QColor("#ffffff"), 2.2)
            pen.setCapStyle(Qt.RoundCap)
            pen.setJoinStyle(Qt.RoundJoin)
            p.setPen(pen)
            p.drawPolyline([QPointF(7.5, 12.5), QPointF(10.8, 15.6), QPointF(16.8, 8.8)])
        else:
            p.setPen(QPen(QColor(Theme.p["faint"]), 1.6))
            p.drawPath(path)
        p.end()


class _Icon(QLabel):
    """A mod's icon in a rounded tile (its first letter until the real icon arrives)."""

    def __init__(self, name: str, size: int = 40):
        super().__init__()
        self.size_ = size
        self.setFixedSize(size, size)
        self.letter = name[:1].upper()
        self.pm = None

    def set_pixmap(self, pm: QPixmap):
        self.pm = pm
        self.update()

    def paintEvent(self, _):
        p = QPainter(self)
        p.setRenderHint(QPainter.Antialiasing)
        p.setRenderHint(QPainter.SmoothPixmapTransform)
        r = QRectF(0, 0, self.size_, self.size_)
        path = QPainterPath()
        path.addRoundedRect(r, 10, 10)
        p.fillPath(path, QColor(255, 255, 255, 16))
        if self.pm is not None and not self.pm.isNull():
            p.setClipPath(path)
            pad = 4
            p.drawPixmap(r.adjusted(pad, pad, -pad, -pad).toRect(), self.pm)
        else:
            f = QFont(self.font())
            f.setPixelSize(int(self.size_ * 0.45))
            f.setWeight(QFont.Bold)
            p.setFont(f)
            p.setPen(QColor(Theme.accent))
            p.drawText(r, Qt.AlignCenter, self.letter)
        p.end()


class ModRow(QFrame):
    toggled = Signal(str, bool)

    def __init__(self, slug: str, name: str, about: str, version: str, on: bool, installed: bool):
        super().__init__(objectName="modRow")
        self.slug = slug
        h = QHBoxLayout(self)
        h.setContentsMargins(12, 10, 14, 10)
        h.setSpacing(14)
        self.icon = _Icon(name)
        h.addWidget(self.icon)
        col = QVBoxLayout()
        col.setSpacing(2)
        t = QLabel(name, objectName="modName")
        col.addWidget(t)
        s = QLabel(about[:1].upper() + about[1:], objectName="modAbout")
        s.setWordWrap(False)
        s.setSizePolicy(QSizePolicy.Ignored, QSizePolicy.Preferred)
        col.addWidget(s)
        h.addLayout(col, 1)
        ver = QLabel(version if installed else ("Next Play" if on else "Off"), objectName="modVersion")
        h.addWidget(ver)
        h.addSpacing(6)
        self.check = Check(on)
        self.check.setToolTip("Use this mod in this instance")
        self.check.toggled.connect(lambda v: self.toggled.emit(self.slug, v))
        h.addWidget(self.check)

        def show(path, ic=self.icon):
            if path:
                try:
                    ic.set_pixmap(QPixmap(str(path)))
                except RuntimeError:
                    pass
        workers.run(modrinth.project_icon, slug, done=show)


class LibraryPage(QWidget):
    play = Signal(object)
    update_all = Signal(object)
    add_mods = Signal(object)
    open_content = Signal(object)
    all_instances = Signal()

    def __init__(self):
        super().__init__(objectName="page")
        self.inst = None
        v = QVBoxLayout(self)
        v.setContentsMargins(40, 4, 40, 14)
        v.setSpacing(0)
        head = QHBoxLayout()
        head.setSpacing(12)
        tcol = QVBoxLayout()
        tcol.setSpacing(8)
        self.title = QLabel(objectName="profileTitle")
        tcol.addWidget(self.title)
        self.underline = _Underline()
        tcol.addWidget(self.underline)
        head.addLayout(tcol)
        head.addStretch(1)
        self.switch = QPushButton("Change instance", objectName="ghostSmall")
        self.switch.setCursor(Qt.PointingHandCursor)
        self.switch.setIcon(icon_pixmap_icon("library"))
        self.switch.clicked.connect(self.pick_instance)
        head.addWidget(self.switch, 0, Qt.AlignTop)
        v.addLayout(head)
        v.addSpacing(22)

        body = QHBoxLayout()
        body.setSpacing(18)
        left = QVBoxLayout()
        left.setSpacing(14)

        self.details = QFrame(objectName="card")
        d = QVBoxLayout(self.details)
        d.setContentsMargins(20, 18, 20, 18)
        d.setSpacing(12)
        d.addWidget(QLabel("Instance Details", objectName="cardTitle"))
        self.rows = QVBoxLayout()
        self.rows.setSpacing(10)
        d.addLayout(self.rows)
        left.addWidget(self.details)

        left.addSpacing(6)
        left.addWidget(QLabel("Quick Actions", objectName="cardTitle"))
        self.play_btn = QPushButton("Play", objectName="bigAccent")
        self.play_btn.setCursor(Qt.PointingHandCursor)
        self.play_btn.setFixedHeight(42)
        self.play_btn.clicked.connect(lambda: self.inst and self.play.emit(self.inst))
        left.addWidget(self.play_btn)
        for text, sig in (("Update All", self.update_all), ("Add Mods", self.add_mods)):
            b = QPushButton(text, objectName="bigGhost")
            b.setCursor(Qt.PointingHandCursor)
            b.setFixedHeight(40)
            b.clicked.connect(lambda _=False, s=sig: self.inst and s.emit(self.inst))
            left.addWidget(b)
        left.addStretch(1)
        lw = QWidget()
        lw.setLayout(left)
        lw.setFixedWidth(300)
        body.addWidget(lw)

        self.mods_card = QFrame(objectName="card")
        m = QVBoxLayout(self.mods_card)
        m.setContentsMargins(20, 18, 20, 18)
        m.setSpacing(12)
        mh = QHBoxLayout()
        mh.addWidget(QLabel("Active Optimization Mods", objectName="cardTitle"))
        mh.addStretch(1)
        self.suite = QPushButton("Core Performance Suite  ", objectName="chipBtn")
        self.suite.setCursor(Qt.PointingHandCursor)
        self.suite.setLayoutDirection(Qt.RightToLeft)
        self.suite.setIcon(icon_pixmap_icon("s_gear"))
        self.suite.setToolTip("All of this instance's mods")
        self.suite.clicked.connect(lambda: self.inst and self.open_content.emit(self.inst))
        mh.addWidget(self.suite)
        m.addLayout(mh)
        self.list_body = QWidget()
        self.list = QVBoxLayout(self.list_body)
        self.list.setContentsMargins(0, 0, 4, 0)
        self.list.setSpacing(8)
        m.addWidget(scroll(self.list_body), 1)
        body.addWidget(self.mods_card, 1)
        v.addLayout(body, 1)

    # ------------------------------------------------------------------ data

    def show_instance(self, inst):
        self.inst = inst
        self.refresh()

    def refresh(self):
        insts = instances.all_instances()
        if self.inst is not None:
            self.inst = next((i for i in insts if i.folder == self.inst.folder), None)
        if self.inst is None:
            self.inst = max(insts, key=lambda i: i.data.get("last_played", 0), default=None)
        inst = self.inst
        clear(self.rows)
        clear(self.list)
        self.switch.setVisible(len(insts) > 1)
        if inst is None:
            self.title.setText("Library")
            self.rows.addWidget(QLabel("No instances yet. Make one to start playing.", objectName="muted"))
            self.play_btn.setEnabled(False)
            return
        self.play_btn.setEnabled(True)
        self.title.setText(f"Profile: {inst.name}")
        self.underline.setFixedWidth(max(120, self.title.sizeHint().width()))
        data = inst.data
        loader = data.get("loader", "vanilla")
        loader_text = "Vanilla" if loader == "vanilla" else f"{loader.capitalize()} {data.get('loader_version') or ''}".strip()
        on = data.get("performance", True) and loader == "fabric"
        tracked = data.get("perf_files") or {}
        off = set(data.get("perf_off") or [])
        active = sum(1 for s in performance.SLUGS if s in tracked and s not in off)
        if not on:
            perf, color = "Off", Theme.p["muted"]
        elif active >= len(performance.SLUGS) - 1:
            perf, color = f"Excellent · {active} mods active", "#4ade80"
        elif active:
            perf, color = f"Good · {active} mods active", "#facc15"
        else:
            perf, color = "Mods download on the next Play", Theme.p["muted"]
        bench = data.get("benchmark_fps")
        if bench and on:
            perf += f" · {int(bench)} FPS"
        for label, value, col in (("Game Version", data.get("mc_version", "?"), None),
                                  ("Mod Loader", loader_text, None),
                                  ("Performance Status", perf, color),
                                  ("Last Played", last_played(data.get("last_played")).replace("Played ", "").capitalize(), None)):
            box = QVBoxLayout()
            box.setSpacing(1)
            box.addWidget(QLabel(label, objectName="detailLabel"))
            val = QLabel(value, objectName="detailValue")
            if col:
                val.setStyleSheet(f"color: {col};")
            box.addWidget(val)
            self.rows.addLayout(box)

        if loader != "fabric":
            note = QLabel("Optimization mods need Fabric. Peregrine moves this instance to Fabric when you press Play "
                          "with Performance mode on.", objectName="muted")
            note.setWordWrap(True)
            self.list.addWidget(note)
        mods = inst.game_dir / "mods"
        mc = data.get("mc_version", "")
        for slug, name, about in performance.MODS:
            fn = tracked.get(slug)
            installed = bool(fn) and ((mods / fn).exists() or (mods / (fn + ".disabled")).exists())
            enabled = on and slug not in off and not (installed and (mods / (fn + ".disabled")).exists())
            row = ModRow(slug, name, about, _version_of(fn, mc) if fn else "", enabled, installed)
            row.toggled.connect(self.set_mod)
            self.list.addWidget(row)
        self.list.addStretch(1)

    def set_mod(self, slug: str, on: bool):
        """Switch one optimization mod for this instance (the file is kept, just switched off)."""
        inst = self.inst
        if inst is None:
            return
        off = set(inst.data.get("perf_off") or [])
        fn = (inst.data.get("perf_files") or {}).get(slug)
        mods = inst.game_dir / "mods"
        try:
            if on:
                off.discard(slug)
                if fn and (mods / (fn + ".disabled")).exists():
                    (mods / (fn + ".disabled")).rename(mods / fn)
                updates = {"perf_off": sorted(off), "perf_checked": 0}
                if not inst.data.get("performance", True):
                    updates["performance"] = True
                inst.update(**updates)
            else:
                off.add(slug)
                if fn and (mods / fn).exists():
                    (mods / fn).rename(mods / (fn + ".disabled"))
                inst.update(perf_off=sorted(off))
        except OSError as e:
            print(f"[peregrine] couldn't switch {slug}: {e}")
        self.refresh()

    def pick_instance(self):
        menu = round_menu(QMenu(self))
        for inst in sorted(instances.all_instances(), key=lambda i: -i.data.get("last_played", 0)):
            act = menu.addAction(f"{inst.name}   ·   {inst.data.get('mc_version', '')}")
            act.triggered.connect(lambda _=False, i=inst: self.show_instance(i))
        menu.addSeparator()
        menu.addAction("All instances…").triggered.connect(self.all_instances.emit)
        menu.exec(self.switch.mapToGlobal(self.switch.rect().bottomLeft()))


def icon_pixmap_icon(name: str):
    from PySide6.QtGui import QIcon
    return QIcon(icon_pixmap(name, Theme.p["muted"], 16))
