"""Recent: the server you played last, big, with its live player count and a Play
button that takes you straight back in. Other recent servers sit underneath."""

from pathlib import Path

from PySide6.QtCore import QRectF, Qt, QTimer, Signal
from PySide6.QtGui import QColor, QFont, QImage, QLinearGradient, QPainter, QPixmap
from PySide6.QtWidgets import QFrame, QHBoxLayout, QLabel, QSizePolicy, QVBoxLayout, QWidget

from .. import instances, servers
from . import workers
from .modern import (
    ONLINE, OFFLINE, Chip, CountUp, PillButton, big_title, count_text, fade_in, heavy_font,
    pixmap_from_png, rounded_pixmap, section_label,
)
from .pages import last_played
from .theme import Theme, icon_pixmap

PING_EVERY_MS = 60_000


def newest_screenshot(inst):
    """The latest screenshot taken in an instance (the page's backdrop), or None."""
    folder = Path(inst.game_dir) / "screenshots"
    try:
        shots = [p for p in folder.iterdir() if p.suffix.lower() in (".png", ".jpg", ".jpeg")]
    except OSError:
        return None
    return max(shots, key=lambda p: p.stat().st_mtime) if shots else None


def soft_blur(img: QImage, width: int) -> QImage:
    """A cheap, smooth blur: shrink a lot, then grow back."""
    if img.isNull():
        return img
    small = img.scaledToWidth(max(24, width // 18), Qt.SmoothTransformation)
    small = small.scaledToWidth(max(48, width // 9), Qt.SmoothTransformation)
    return small.scaledToWidth(width, Qt.SmoothTransformation)


class ServerCard(QFrame):
    """A small card for another recent server ('Jump back in')."""
    clicked = Signal(object)

    def __init__(self, entry: dict):
        super().__init__(objectName="glassCard")
        self.entry = entry
        self.setCursor(Qt.PointingHandCursor)
        self.setFixedHeight(68)
        self.setMinimumWidth(200)
        self.setMaximumWidth(280)
        h = QHBoxLayout(self)
        h.setContentsMargins(12, 10, 14, 10)
        h.setSpacing(12)
        self.icon = QLabel()
        self.icon.setFixedSize(44, 44)
        self.icon.setPixmap(server_icon(entry.get("icon"), 44))
        h.addWidget(self.icon)
        col = QVBoxLayout()
        col.setSpacing(1)
        name = QLabel(servers.display_name(entry), objectName="pillTitle")
        col.addWidget(name)
        self.sub = QLabel(entry["address"], objectName="pillSub")
        col.addWidget(self.sub)
        h.addLayout(col, 1)

    def set_status(self, st):
        try:
            if st:
                self.sub.setText(f"{count_text(st['online'])} playing")
                if st.get("favicon") and not self.entry.get("icon"):
                    self.icon.setPixmap(server_icon(st["favicon"], 44))
            else:
                self.sub.setText("Offline")
        except RuntimeError:
            pass

    def mouseReleaseEvent(self, e):
        if e.button() == Qt.LeftButton:
            self.clicked.emit(self.entry)
        super().mouseReleaseEvent(e)


def server_icon(png: bytes, size: int) -> QPixmap:
    pm = pixmap_from_png(png) if png else QPixmap()
    if pm.isNull():
        return icon_pixmap("package", Theme.p["muted"], size)
    return rounded_pixmap(pm, size)


class RecentPage(QWidget):
    join = Signal(object, str)        # instance, server address
    play = Signal(object)             # instance (no server)
    open_content = Signal(object)     # instance
    browse = Signal()
    new_instance = Signal()
    current_changed = Signal(object, object)  # entry or None, status or None (for the top bar)

    def __init__(self):
        super().__init__(objectName="page")
        self.entries = []
        self.entry = None
        self.status = {}          # address -> status dict (or None when unreachable)
        self.backdrop = None      # blurred screenshot
        self._backdrop_src = None
        self.busy = set()

        v = QVBoxLayout(self)
        v.setContentsMargins(64, 24, 56, 28)
        v.setSpacing(0)
        v.addStretch(3)

        self.chip = Chip("Recent")
        v.addWidget(self.chip)
        v.addSpacing(18)
        self.title = big_title("", hero=True)
        self.title.setWordWrap(True)
        self.title.setMaximumWidth(900)
        v.addWidget(self.title)
        v.addSpacing(6)

        meta = QHBoxLayout()
        meta.setSpacing(10)
        self.icon = QLabel()
        self.icon.setFixedSize(30, 30)
        meta.addWidget(self.icon)
        self.meta = QLabel("", objectName="muted")
        meta.addWidget(self.meta)
        meta.addStretch()
        v.addLayout(meta)
        v.addSpacing(14)

        self.text = QLabel("", objectName="heroText")
        self.text.setWordWrap(True)
        self.text.setMaximumWidth(560)
        self.text.setFont(heavy_font(16, QFont.Normal))
        v.addWidget(self.text)
        v.addSpacing(20)

        stats = QHBoxLayout()
        stats.setSpacing(28)
        players = QVBoxLayout()
        players.setSpacing(0)
        self.count = CountUp()
        players.addWidget(self.count)
        self.count_label = section_label("players online")
        players.addWidget(self.count_label)
        self.players_box = QWidget()
        self.players_box.setLayout(players)
        stats.addWidget(self.players_box)
        last = QVBoxLayout()
        last.setSpacing(0)
        self.last = QLabel("", objectName="heroStat")
        self.last.setFont(heavy_font(28, QFont.Black))
        last.addWidget(self.last)
        self.last_label = section_label("")
        last.addWidget(self.last_label)
        self.last_box = QWidget()
        self.last_box.setLayout(last)
        stats.addWidget(self.last_box)
        stats.addStretch()
        v.addLayout(stats)
        v.addSpacing(26)

        buttons = QHBoxLayout()
        buttons.setSpacing(16)
        self.play_btn = PillButton("Play", "s_play", "primary", big=True)
        self.play_btn.clicked.connect(self._play)
        buttons.addWidget(self.play_btn)
        self.second_btn = PillButton("Instance", "s_plus", "ghost", big=True)
        self.second_btn.clicked.connect(self._second)
        buttons.addWidget(self.second_btn)
        buttons.addStretch()
        v.addLayout(buttons)
        v.addStretch(4)

        self.more_label = section_label("Jump back in")
        v.addWidget(self.more_label)
        v.addSpacing(10)
        self.more = QHBoxLayout()
        self.more.setSpacing(12)
        v.addLayout(self.more)
        v.addSpacing(22)

        bottom = QHBoxLayout()
        mods = PillButton("Mods & packs", "s_layers", "ghost")
        mods.clicked.connect(self.browse.emit)
        bottom.addWidget(mods)
        bottom.addStretch()
        v.addLayout(bottom)

        self.timer = QTimer(self)
        self.timer.setInterval(PING_EVERY_MS)
        self.timer.timeout.connect(self.ping_all)

    # ------------------------------------------------------------ data

    def refresh(self):
        try:
            self.entries = servers.recent_servers(instances.all_instances())
        except Exception as e:  # never let a damaged file break the page
            print(f"[peregrine] couldn't read recent servers: {e}")
            self.entries = []
        keep = self.entry["address"].lower() if self.entry else None
        pick = next((e for e in self.entries if e["address"].lower() == keep), None)
        self.show_entry(pick or (self.entries[0] if self.entries else None), animate=False)
        self.ping_all()

    def ping_all(self):
        for e in self.entries[:5]:
            self.ping(e["address"])

    def ping(self, address):
        if address in self.busy:
            return
        self.busy.add(address)
        if self.entry and self.entry["address"] == address and address not in self.status:
            self.current_changed.emit(self.entry, "checking")

        def done(st, a=address):
            self.busy.discard(a)
            self.status[a] = st
            self._status_arrived(a)

        def failed(_msg, a=address):
            self.busy.discard(a)
            self.status[a] = None
            self._status_arrived(a)
        workers.run(servers.status, address, done=done, failed=failed)

    def _status_arrived(self, address):
        try:
            if self.entry and self.entry["address"] == address:
                self._show_status()
            for i in range(self.more.count()):
                w = self.more.itemAt(i).widget()
                if isinstance(w, ServerCard) and w.entry["address"] == address:
                    w.set_status(self.status.get(address))
        except RuntimeError:
            pass

    # ------------------------------------------------------------ view

    def show_entry(self, entry, animate=True):
        self.entry = entry
        insts = instances.all_instances()
        if entry is None:
            self._show_empty(insts)
        else:
            self.chip.set_text("Recent")
            self.title.setText(servers.display_name(entry).upper())
            self.icon.setPixmap(server_icon(entry.get("icon"), 30))
            self.icon.show()
            inst = entry["inst"]
            self.meta.setText(f"{entry['address']}   ·   {inst.name}")
            self.text.setText("Checking the server…")
            self.players_box.show()
            self.count.set_value(0)
            self.count.setText("–")
            self.last.setText(last_played(entry["last_played"]).replace("Played ", "").capitalize())
            self.last_label.setText("last played")
            self.last_box.show()
            self.play_btn.setText("PLAY")
            self.play_btn.icon_name = "s_play"
            self.second_btn.setText("INSTANCE")
            self.second_btn.show()
            self.play_btn.updateGeometry()
            self._show_status()
            self._set_backdrop(inst)
        self._fill_more()
        if animate:
            for n, w in enumerate((self.chip, self.title, self.text, self.play_btn, self.second_btn)):
                fade_in(w, 280, 0, n * 45)

    def _show_empty(self, insts):
        self.chip.set_text("Recent")
        self.chip.set_dot(None)
        self.icon.hide()
        self.players_box.hide()
        if insts:
            last = max(insts, key=lambda i: i.data.get("last_played") or 0)
            self.title.setText("NO SERVERS YET")
            self.meta.setText(f"{last.name}   ·   {last.subtitle()}")
            self.text.setText("Join a server in Minecraft and it shows up here with its live player "
                              "count, ready to jump back into with one click.")
            self.last.setText(last_played(last.data.get("last_played")).replace("Played ", "").capitalize())
            self.last_label.setText(last.name)
            self.last_box.show()
            self.play_btn.setText("PLAY")
            self.play_btn.icon_name = "s_play"
            self.second_btn.setText("INSTANCE")
            self.second_btn.show()
            self._set_backdrop(last)
        else:
            self.title.setText("WELCOME")
            self.meta.setText("")
            self.text.setText("Make an instance: pick a Minecraft version and Peregrine sets up Fabric, "
                              "performance mods and the in-game menu for you.")
            self.last_box.hide()
            self.play_btn.setText("NEW INSTANCE")
            self.play_btn.icon_name = "s_plus"
            self.second_btn.hide()
            self._set_backdrop(None)
        self.current_changed.emit(None, None)

    def _show_status(self):
        e = self.entry
        if e is None:
            return
        if e["address"] not in self.status:
            self.chip.set_dot(Theme.p["faint"])
            self.current_changed.emit(e, "checking")
            return
        st = self.status[e["address"]]
        if st is None:
            self.chip.set_dot(OFFLINE)
            self.text.setText("The server didn't answer. It may be down, or your internet may be.")
            self.count.setText("–")
            self.count_label.setText("server offline")
        else:
            self.chip.set_dot(ONLINE)
            motd = st["motd"] or "A Minecraft server."
            self.text.setText(motd)
            self.count.set_value(st["online"])
            self.count_label.setText(f"of {count_text(st['max'])} online" if st["max"] else "players online")
            bits = [e["address"]]
            if st.get("version"):
                bits.append(st["version"])
            bits.append(f"{st['latency']} ms")
            bits.append(e["inst"].name)
            self.meta.setText("   ·   ".join(bits))
            if st.get("favicon") and not e.get("icon"):
                self.icon.setPixmap(server_icon(st["favicon"], 30))
        self.current_changed.emit(e, st)

    def _fill_more(self):
        from .pages import clear
        clear(self.more)
        others = [e for e in self.entries if e is not self.entry][:4]
        self.more_label.setVisible(bool(others))
        for e in others:
            card = ServerCard(e)
            card.clicked.connect(lambda entry: self.show_entry(entry))
            card.set_status(self.status.get(e["address"])) if e["address"] in self.status else None
            self.more.addWidget(card)
        self.more.addStretch()

    def _set_backdrop(self, inst):
        shot = newest_screenshot(inst) if inst is not None else None
        if shot == self._backdrop_src:
            return
        self._backdrop_src = shot
        self.backdrop = None
        if shot is not None:
            img = QImage(str(shot))
            if not img.isNull():
                self.backdrop = soft_blur(img, 1280)
        self.update()

    def current_inst(self):
        if self.entry:
            return self.entry["inst"]
        insts = instances.all_instances()
        return max(insts, key=lambda i: i.data.get("last_played") or 0) if insts else None

    def _play(self):
        if self.entry:
            self.join.emit(self.entry["inst"], self.entry["address"])
            return
        inst = self.current_inst()
        if inst is None:
            self.new_instance.emit()
        else:
            self.play.emit(inst)

    def _second(self):
        inst = self.current_inst()
        if inst is not None:
            self.open_content.emit(inst)

    # ------------------------------------------------------- painting

    def paintEvent(self, e):
        if self.backdrop is None:
            return super().paintEvent(e)
        p = QPainter(self)
        p.setRenderHint(QPainter.SmoothPixmapTransform)
        w, h = self.width(), self.height()
        img = self.backdrop
        scale = max(w / img.width(), h / img.height())
        dw, dh = img.width() * scale, img.height() * scale
        p.setOpacity(0.55 if Theme.name != "light" else 0.35)
        p.drawImage(QRectF((w - dw) / 2, (h - dh) / 2, dw, dh), img)
        p.setOpacity(1.0)
        bg = QColor(Theme.p["bg"])
        g = QLinearGradient(0, 0, w, 0)
        for stop, alpha in ((0.0, 0.92), (0.45, 0.70), (1.0, 0.25)):
            c = QColor(bg)
            c.setAlphaF(alpha)
            g.setColorAt(stop, c)
        p.fillRect(self.rect(), g)
        v = QLinearGradient(0, h * 0.55, 0, h)
        c0, c1 = QColor(bg), QColor(bg)
        c0.setAlphaF(0.0)
        c1.setAlphaF(0.85)
        v.setColorAt(0, c0)
        v.setColorAt(1, c1)
        p.fillRect(self.rect(), v)
        p.end()

    def showEvent(self, e):
        self.timer.start()
        super().showEvent(e)

    def hideEvent(self, e):
        self.timer.stop()
        super().hideEvent(e)
