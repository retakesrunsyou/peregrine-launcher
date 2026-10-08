"""Pages: Home (instances), Content, Settings and Accounts. Modpacks lives in browse.py."""

import os

from PySide6.QtCore import QSize, Qt, QUrl, Signal
from PySide6.QtGui import QDesktopServices
from PySide6.QtWidgets import (
    QColorDialog, QComboBox, QFileDialog, QFrame, QGridLayout, QHBoxLayout,
    QLabel, QLineEdit, QMessageBox, QPushButton, QScrollArea, QSlider,
    QSpinBox, QVBoxLayout, QWidget,
)

from .. import CLIENT_ID, DISCORD_APP_ID, UPDATE_REPO, VERSION, auth, config, discord, game, instances
from .theme import ACCENTS, THEME_NAMES, Theme, avatar_pixmap, icon, icon_pixmap, on_color, tint
from .widgets import Segmented, Toggle


def scroll(content: QWidget) -> QScrollArea:
    area = QScrollArea()
    area.setWidgetResizable(True)
    area.setFrameShape(QFrame.NoFrame)
    content.setObjectName("scrollBody")
    area.setWidget(content)
    return area


def clear(layout) -> None:
    while layout.count():
        item = layout.takeAt(0)
        if item.widget():
            item.widget().deleteLater()
        elif item.layout():
            clear(item.layout())


def icon_button(name: str, tip: str, color: str = None) -> QPushButton:
    b = QPushButton(objectName="icon")
    b.setIcon(icon(name, color, 18))
    b.setIconSize(QSize(18, 18))
    b.setToolTip(tip)
    b.setCursor(Qt.PointingHandCursor)
    return b


def page_header(title: str, sub: str = "", back=None) -> QHBoxLayout:
    row = QHBoxLayout()
    if back:
        b = icon_button("back", "Back", Theme.p["text"])
        b.clicked.connect(back)
        row.addWidget(b, 0, Qt.AlignTop)
    col = QVBoxLayout()
    col.setSpacing(4)
    col.addWidget(QLabel(title, objectName="h1"))
    if sub:
        col.addWidget(QLabel(sub, objectName="muted"))
    row.addLayout(col)
    row.addStretch()
    return row


# ================================================================== home

class InstanceCard(QFrame):
    play = Signal(object)
    content = Signal(object)
    settings = Signal(object)
    delete = Signal(object)

    def __init__(self, inst, state: str = ""):
        super().__init__(objectName="card")
        self.inst = inst
        color = inst.color
        # The signature touch: each instance's own color as a stripe down the left edge.
        self.setStyleSheet(f"#card {{ border-left: 3px solid {color}; }}")
        self.setMinimumWidth(300)

        v = QVBoxLayout(self)
        v.setContentsMargins(18, 16, 16, 16)
        v.setSpacing(12)

        top = QHBoxLayout()
        top.setSpacing(12)
        tile = QLabel()
        tile.setFixedSize(42, 42)
        tile.setAlignment(Qt.AlignCenter)
        if inst.icon:
            from PySide6.QtGui import QPixmap
            tile.setPixmap(QPixmap(str(inst.icon)).scaled(42, 42, Qt.KeepAspectRatio,
                                                         Qt.SmoothTransformation))
        else:
            tile.setPixmap(icon_pixmap("folder", color, 20))
        tile.setStyleSheet(f"background: {tint(color, 0.16)}; border-radius: 7px;")
        top.addWidget(tile)
        names = QVBoxLayout()
        names.setSpacing(2)
        names.addWidget(QLabel(inst.name, objectName="h2"))
        sub_row = QHBoxLayout()
        sub_row.setSpacing(8)
        sub_row.addWidget(QLabel(inst.subtitle(), objectName="faint"))
        if inst.data.get("performance_mods"):
            badge = QLabel("FPS boost", objectName="badge")
            badge.setToolTip("Performance mods installed")
            sub_row.addWidget(badge)
        if inst.data.get("modpack"):
            sub_row.addWidget(QLabel("Modpack", objectName="badge"))
        sub_row.addStretch()
        names.addLayout(sub_row)
        top.addLayout(names, 1)
        gear = icon_button("settings", "Instance settings")
        gear.clicked.connect(lambda: self.settings.emit(inst))
        trash = icon_button("trash", "Delete instance", Theme.p["danger"])
        trash.clicked.connect(lambda: self.delete.emit(inst))
        top.addWidget(gear, 0, Qt.AlignTop)
        top.addWidget(trash, 0, Qt.AlignTop)
        v.addLayout(top)

        desc = inst.data.get("description") or "No description yet."
        d = QLabel(desc, objectName="muted")
        d.setWordWrap(True)
        v.addWidget(d)
        v.addStretch()

        content = QPushButton("Content", objectName="outline")
        content.setCursor(Qt.PointingHandCursor)
        content.clicked.connect(lambda: self.content.emit(inst))
        play = QPushButton({"busy": "Getting ready…", "running": "Playing"}.get(state, "Play"),
                           objectName="accent")
        play.setCursor(Qt.PointingHandCursor)
        play.setMinimumHeight(38)
        play.setEnabled(state == "")
        play.clicked.connect(lambda: self.play.emit(inst))
        trash.setEnabled(state == "")
        v.addWidget(content)
        v.addWidget(play)


class HomePage(QWidget):
    play = Signal(object)
    open_content = Signal(object)
    new_instance = Signal()
    changed = Signal()

    def __init__(self):
        super().__init__(objectName="page")
        self.states = {}
        v = QVBoxLayout(self)
        v.setContentsMargins(32, 28, 32, 20)
        v.setSpacing(20)
        self.header = QHBoxLayout()
        v.addLayout(self.header)

        body = QWidget()
        self.grid = QGridLayout(body)
        self.grid.setContentsMargins(0, 0, 4, 0)
        self.grid.setSpacing(16)
        v.addWidget(scroll(body), 1)
        self.columns = 2
        self.refresh()

    def set_state(self, inst_folder, state: str):
        if state:
            self.states[str(inst_folder)] = state
        else:
            self.states.pop(str(inst_folder), None)
        self.refresh()

    def refresh(self):
        clear(self.header)
        items = instances.all_instances()
        count = {0: "No instances yet", 1: "1 instance"}.get(len(items), f"{len(items)} instances")
        self.header.addLayout(page_header("Your instances", count))
        new = QPushButton("  New instance", objectName="accent")
        new.setIcon(icon("plus", on_color(Theme.accent), 16))
        new.setCursor(Qt.PointingHandCursor)
        new.clicked.connect(self.new_instance.emit)
        self.header.addWidget(new, 0, Qt.AlignTop)

        clear(self.grid)
        if not items:
            empty = QLabel("Create an instance to pick a Minecraft version and start playing.",
                           objectName="muted")
            empty.setAlignment(Qt.AlignCenter)
            self.grid.addWidget(empty, 0, 0)
            return
        for i, inst in enumerate(items):
            card = InstanceCard(inst, self.states.get(str(inst.folder), ""))
            card.play.connect(self.play.emit)
            card.content.connect(self.open_content.emit)
            card.settings.connect(self.edit)
            card.delete.connect(self.remove)
            self.grid.addWidget(card, i // self.columns, i % self.columns)
        for c in range(self.columns):
            self.grid.setColumnStretch(c, 1)
        self.grid.setRowStretch(len(items) // self.columns + 1, 1)

    def resizeEvent(self, e):
        cols = max(1, min(3, (self.width() - 64) // 340))
        if cols != self.columns:
            self.columns = cols
            self.refresh()
        super().resizeEvent(e)

    def edit(self, inst):
        from .dialogs import InstanceSettingsDialog
        dlg = InstanceSettingsDialog(self, inst)
        if dlg.exec():
            dlg.save()
            self.refresh()

    def remove(self, inst):
        if QMessageBox.question(self, "Delete instance",
                                f"Delete “{inst.name}” and all of its worlds? This can't be undone."
                                ) == QMessageBox.Yes:
            inst.delete()
            self.refresh()


# =============================================================== content

class ContentPage(QWidget):
    back = Signal()
    install_fps = Signal(object)
    browse = Signal(object)

    KINDS = [("mods", "Mods"), ("resourcepacks", "Resource packs"), ("shaderpacks", "Shaders")]

    def __init__(self):
        super().__init__(objectName="page")
        self.inst, self.kind = None, "mods"
        v = QVBoxLayout(self)
        v.setContentsMargins(32, 28, 32, 20)
        v.setSpacing(16)
        self.header = QHBoxLayout()
        v.addLayout(self.header)

        tabs = QHBoxLayout()
        tabs.setSpacing(20)
        self.tabs = {}
        for kind, label in self.KINDS:
            b = QPushButton(label, objectName="tab", checkable=True)
            b.clicked.connect(lambda _=False, k=kind: self.show_kind(k))
            self.tabs[kind] = b
            tabs.addWidget(b)
        tabs.addStretch()
        self.actions = QHBoxLayout()
        tabs.addLayout(self.actions)
        v.addLayout(tabs)

        body = QWidget()
        self.list = QVBoxLayout(body)
        self.list.setContentsMargins(0, 0, 4, 0)
        self.list.setSpacing(8)
        v.addWidget(scroll(body), 1)

    def open(self, inst):
        self.inst = inst
        self.show_kind("mods")

    def show_kind(self, kind):
        self.kind = kind
        for k, b in self.tabs.items():
            b.setChecked(k == kind)
            if k == "mods":
                b.setVisible(self.inst.data["loader"] != "vanilla")
        if kind == "mods" and self.inst.data["loader"] == "vanilla":
            return self.show_kind("resourcepacks")
        self.refresh()

    def refresh(self):
        inst = self.inst
        clear(self.header)
        self.header.addLayout(page_header(inst.name, inst.subtitle(), back=self.back.emit))

        clear(self.actions)
        if self.kind == "mods" and inst.data["loader"] == "fabric":
            add = QPushButton("  Add mods", objectName="accent")
            add.setIcon(icon("plus", on_color(Theme.accent), 16))
            add.clicked.connect(lambda: self.browse.emit(inst))
            self.actions.addWidget(add)
        if self.kind == "mods" and inst.data["loader"] == "fabric" and not inst.data.get("mods_installed"):
            fps = QPushButton("  Add FPS mods", objectName="outline")
            fps.setIcon(icon("bolt", Theme.accent, 16))
            fps.clicked.connect(lambda: self.install_fps.emit(inst))
            self.actions.addWidget(fps)
        folder = QPushButton("  Open folder")
        folder.setIcon(icon("folder", None, 16))
        folder.clicked.connect(self.open_folder)
        self.actions.addWidget(folder)

        clear(self.list)
        items = inst.content(self.kind)
        if not items:
            label = dict(self.KINDS)[self.kind].lower()
            hint = QLabel(f"No {label} yet. Drop files into the folder, then come back here.",
                          objectName="muted")
            hint.setContentsMargins(0, 24, 0, 0)
            self.list.addWidget(hint)
        for path, enabled in items:
            self.list.addWidget(self._row(path, enabled))
        self.list.addStretch()

    def _row(self, path, enabled):
        row = QFrame(objectName="row")
        h = QHBoxLayout(row)
        h.setContentsMargins(14, 8, 8, 8)
        check = Toggle(enabled)
        check.setToolTip("Turn on or off")
        check.toggled.connect(lambda on, p=path: self._toggle(p, on))
        name = path.name.removesuffix(".disabled")
        label = QLabel(name)
        if not enabled:
            label.setObjectName("faint")
        label.setTextInteractionFlags(Qt.TextSelectableByMouse)
        size = path.stat().st_size if path.is_file() else 0
        h.addWidget(check)
        h.addWidget(label, 1)
        if size:
            h.addWidget(QLabel(f"{size / 1_048_576:.1f} MB", objectName="faint"))
        trash = icon_button("trash", "Remove", Theme.p["danger"])
        trash.clicked.connect(lambda _=False, p=path: self.remove(p))
        h.addWidget(trash)
        return row

    def _toggle(self, path, on):
        try:
            self.inst.set_enabled(path, on)
        except OSError as e:
            QMessageBox.warning(self, "Couldn't change that file", str(e))
        # refresh after the slide animation so it doesn't jump
        from PySide6.QtCore import QTimer
        QTimer.singleShot(160, self.refresh)

    def remove(self, path):
        if QMessageBox.question(self, "Remove", f"Remove {path.name}?") == QMessageBox.Yes:
            if path.is_dir():
                import shutil
                shutil.rmtree(path)
            else:
                path.unlink()
            self.refresh()

    def open_folder(self):
        folder = self.inst.game_dir / self.kind
        folder.mkdir(parents=True, exist_ok=True)
        QDesktopServices.openUrl(QUrl.fromLocalFile(str(folder)))


# ============================================================== settings

class Section(QFrame):
    """A titled group of settings rows."""

    def __init__(self, title: str, sub: str = ""):
        super().__init__(objectName="card")
        self.v = QVBoxLayout(self)
        self.v.setContentsMargins(22, 18, 22, 18)
        self.v.setSpacing(14)
        self.v.addWidget(QLabel(title, objectName="h2"))
        if sub:
            s = QLabel(sub, objectName="muted")
            s.setWordWrap(True)
            self.v.addWidget(s)

    def row(self, label: str, widget, hint: str = ""):
        h = QHBoxLayout()
        col = QVBoxLayout()
        col.setSpacing(2)
        col.addWidget(QLabel(label))
        if hint:
            t = QLabel(hint, objectName="faint")
            t.setWordWrap(True)
            col.addWidget(t)
        h.addLayout(col, 1)
        if isinstance(widget, QWidget):
            h.addWidget(widget)
        else:
            h.addLayout(widget)
        self.v.addLayout(h)


class SettingsPage(QWidget):
    appearance_changed = Signal()
    check_updates = Signal()

    def __init__(self):
        super().__init__(objectName="page")
        outer = QVBoxLayout(self)
        outer.setContentsMargins(32, 28, 32, 20)
        outer.setSpacing(20)
        outer.addLayout(page_header("Settings", "Changes save as you make them."))
        body = QWidget()
        self.body = QVBoxLayout(body)
        self.body.setContentsMargins(0, 0, 4, 0)
        self.body.setSpacing(16)
        outer.addWidget(scroll(body), 1)
        self.build()

    def save(self, key, value):
        cfg = config.load()
        cfg[key] = value
        config.save(cfg)

    def build(self):
        clear(self.body)
        cfg = config.load()

        # ---- appearance
        look = Section("Appearance")
        themes = Segmented(list(THEME_NAMES.items()), cfg["theme"])
        themes.changed.connect(lambda k: (self.save("theme", k), self.appearance_changed.emit()))
        look.row("Theme", themes)

        swatches = QHBoxLayout()
        swatches.setSpacing(8)
        for label, color in ACCENTS.items():
            b = QPushButton(objectName="swatch")
            b.setToolTip(label)
            ring = Theme.p["text"] if color.lower() == cfg["accent"].lower() else "transparent"
            b.setStyleSheet(f"QPushButton {{ background: {color}; border: 2px solid {ring}; }}")
            b.setCursor(Qt.PointingHandCursor)
            b.clicked.connect(lambda _=False, c=color: self.set_accent(c))
            swatches.addWidget(b)
        custom = QPushButton("Custom…")
        custom.clicked.connect(self.custom_accent)
        swatches.addWidget(custom)
        look.row("Accent color", swatches)
        self.body.addWidget(look)

        # ---- game
        gamesec = Section("Game")
        mem = QHBoxLayout()
        slider = QSlider(Qt.Horizontal, minimum=2, maximum=32)  # in half-GB steps
        slider.setFixedWidth(220)
        slider.setValue(max(2, min(32, round(cfg["max_memory_mb"] / 512))))
        mem_label = QLabel(f"{slider.value() / 2:g} GB")
        mem_label.setFixedWidth(52)
        slider.valueChanged.connect(lambda v: mem_label.setText(f"{v / 2:g} GB"))
        slider.sliderReleased.connect(lambda: self.save("max_memory_mb", slider.value() * 512))
        slider.valueChanged.connect(lambda v: None if slider.isSliderDown()
                                    else self.save("max_memory_mb", v * 512))
        mem.addWidget(slider)
        mem.addWidget(mem_label)
        gamesec.row("Memory", mem, "4 GB suits most packs. More isn't always faster.")

        size = QHBoxLayout()
        w = QSpinBox(minimum=0, maximum=7680, singleStep=10, specialValueText="Auto")
        h = QSpinBox(minimum=0, maximum=4320, singleStep=10, specialValueText="Auto")
        w.setValue(cfg["width"])
        h.setValue(cfg["height"])
        w.valueChanged.connect(lambda v: self.save("width", v))
        h.valueChanged.connect(lambda v: self.save("height", v))
        size.addWidget(w)
        size.addWidget(QLabel("×", objectName="muted"))
        size.addWidget(h)
        gamesec.row("Window size", size)
        gamesec.row("Start in fullscreen", self._check(cfg, "fullscreen"))
        gamesec.row("Use GameMode", self._check(cfg, "use_gamemode"),
                    "Boosts performance while playing, if Feral GameMode is installed.")
        self.body.addWidget(gamesec)

        # ---- performance
        perf = Section("Performance", "These make the game itself run faster. Defaults suit most PCs.")
        gc = Segmented([("auto", "Balanced"), ("zgc", "Low pause")], cfg["gc"])
        gc.changed.connect(lambda k: self.save("gc", k))
        perf.row("Memory cleanup", gc,
                 "Low pause uses ZGC for smoother frames with 6 GB+ of memory. "
                 "Needs Minecraft 1.20.5 or newer; older versions use Balanced.")
        perf.row("Graphics driver boost", self._check(cfg, "driver_boost"),
                 "Runs the graphics driver on its own CPU thread. Often a big FPS gain on AMD and Intel.")
        perf.row("Use the dedicated GPU", self._check(cfg, "dedicated_gpu"),
                 "For laptops with two graphics chips." +
                 (" NVIDIA detected." if game.has_nvidia() else ""))
        self.body.addWidget(perf)

        # ---- java
        java = Section("Java")
        jrow = QHBoxLayout()
        path = QLineEdit(cfg["java_path"], placeholderText="Automatic")
        path.setMinimumWidth(240)
        path.editingFinished.connect(lambda: self.save("java_path", path.text().strip()))
        browse = QPushButton("Browse")
        browse.clicked.connect(lambda: self.pick_java(path))
        jrow.addWidget(path)
        jrow.addWidget(browse)
        java.row("Java executable", jrow, "Leave empty and Peregrine downloads the right Java for each version.")
        extra = QLineEdit(cfg["extra_jvm_args"], placeholderText="None")
        extra.setMinimumWidth(240)
        extra.editingFinished.connect(lambda: self.save("extra_jvm_args", extra.text().strip()))
        java.row("Extra Java arguments", extra)
        self.body.addWidget(java)

        # ---- launcher
        launcher = Section("Launcher")
        on_launch = QComboBox()
        for key, label in [("keep", "Keep the launcher open"), ("minimize", "Minimize the launcher"),
                           ("hide", "Hide until the game closes")]:
            on_launch.addItem(label, key)
        on_launch.setCurrentIndex(max(0, on_launch.findData(cfg["on_launch"])))
        on_launch.currentIndexChanged.connect(lambda i: self.save("on_launch", on_launch.itemData(i)))
        launcher.row("When the game starts", on_launch)
        launcher.row("Show the console when playing", self._check(cfg, "open_console"))
        launcher.row("Show snapshot versions", self._check(cfg, "show_snapshots"))
        if DISCORD_APP_ID:
            discord_toggle = self._check(cfg, "discord")
            discord_toggle.toggled.connect(lambda on: discord.update("idle" if on else "clear"))
            launcher.row("Show what you're playing on Discord", discord_toggle)
        launcher.row("In-game menu (Right Shift)", self._check(cfg, "ingame_menu"),
                     "Adds Peregrine Client to Fabric instances: HUD, zoom, toggle sprint and more.")
        self.body.addWidget(launcher)

        # ---- login (only shown when no client ID is built into this release)
        if CLIENT_ID:
            login = None
        else:
            login = Section("Microsoft login",
                            "Peregrine needs its own Azure app client ID, approved by Mojang, "
                            "before Microsoft accounts can sign in. See DEVELOPING.md for steps.")
            cid = QLineEdit(cfg["client_id"], placeholderText="Client ID")
            cid.setMinimumWidth(300)
            cid.editingFinished.connect(lambda: self.save("client_id", cid.text().strip()))
            login.row("Client ID", cid)
            self.body.addWidget(login)

        # ---- updates
        updates = Section("Updates", f"You're on Peregrine {VERSION}.")
        updates.row("Check for updates when Peregrine starts", self._check(cfg, "check_updates"))
        check_now = QPushButton("Check now")
        check_now.setEnabled(bool(UPDATE_REPO))
        if not UPDATE_REPO:
            check_now.setToolTip("Set UPDATE_REPO in peregrine/__init__.py to turn updates on.")
        check_now.clicked.connect(self.check_updates.emit)
        updates.row("Look for a new version", check_now)
        self.body.addWidget(updates)
        self.body.addStretch()

    def _check(self, cfg, key) -> Toggle:
        c = Toggle(bool(cfg[key]))
        c.toggled.connect(lambda on: self.save(key, on))
        return c

    def set_accent(self, color):
        self.save("accent", color)
        self.appearance_changed.emit()

    def custom_accent(self):
        c = QColorDialog.getColor(Theme.accent, self, "Accent color")
        if c.isValid():
            self.set_accent(c.name())

    def pick_java(self, field):
        p, _ = QFileDialog.getOpenFileName(self, "Choose a java executable")
        if p:
            field.setText(p)
            self.save("java_path", p)


# ============================================================== accounts

class AccountsPage(QWidget):
    changed = Signal()
    add_microsoft = Signal()

    def __init__(self, skins: dict):
        super().__init__(objectName="page")
        self.skins = skins  # uuid -> skin path, filled in by the window
        v = QVBoxLayout(self)
        v.setContentsMargins(32, 28, 32, 20)
        v.setSpacing(20)
        head = page_header("Accounts", "Switch between Minecraft accounts.")
        add = QPushButton("  Add Microsoft account", objectName="accent")
        add.setIcon(icon("plus", on_color(Theme.accent), 16))
        add.clicked.connect(self.add_microsoft.emit)
        head.addWidget(add, 0, Qt.AlignTop)
        if os.environ.get("PEREGRINE_DEV") == "1":
            dev = QPushButton("Add offline test account")
            dev.clicked.connect(self.add_offline)
            head.addWidget(dev, 0, Qt.AlignTop)
        v.addLayout(head)
        body = QWidget()
        self.list = QVBoxLayout(body)
        self.list.setContentsMargins(0, 0, 4, 0)
        self.list.setSpacing(10)
        v.addWidget(scroll(body), 1)
        self.refresh()

    def refresh(self):
        clear(self.list)
        data = auth.load_accounts()
        if not data["accounts"]:
            self.list.addWidget(QLabel("Add the Microsoft account that owns Minecraft: Java Edition.",
                                       objectName="muted"))
        for a in data["accounts"]:
            row = QFrame(objectName="row")
            h = QHBoxLayout(row)
            h.setContentsMargins(14, 12, 12, 12)
            h.setSpacing(12)
            face = QLabel()
            face.setPixmap(avatar_pixmap(self.skins.get(a["uuid"]), a["name"], 40))
            h.addWidget(face)
            col = QVBoxLayout()
            col.setSpacing(2)
            col.addWidget(QLabel(a["name"], objectName="h2"))
            kind = "Microsoft account" if a.get("type") == "msa" else "Offline test account"
            col.addWidget(QLabel(kind, objectName="faint"))
            h.addLayout(col, 1)
            if a["uuid"] == data["active"]:
                active = QLabel("  In use")
                active.setStyleSheet(f"color: {Theme.accent}; font-weight: 600;")
                h.addWidget(active)
            else:
                use = QPushButton("Use")
                use.clicked.connect(lambda _=False, u=a["uuid"]: self.use(u))
                h.addWidget(use)
            trash = icon_button("trash", "Remove account", Theme.p["danger"])
            trash.clicked.connect(lambda _=False, acc=a: self.remove(acc))
            h.addWidget(trash)
            self.list.addWidget(row)
        self.list.addStretch()

    def use(self, uuid):
        data = auth.load_accounts()
        data["active"] = uuid
        auth.save_accounts(data)
        self.changed.emit()

    def remove(self, account):
        if QMessageBox.question(self, "Remove account",
                                f"Remove {account['name']} from Peregrine?") == QMessageBox.Yes:
            auth.remove_account(account["uuid"])
            self.changed.emit()

    def add_offline(self):
        from PySide6.QtWidgets import QInputDialog
        name, ok = QInputDialog.getText(self, "Offline test account", "Username")
        if ok and name.strip():
            auth.add_account(auth.offline_account(name.strip()))
            self.changed.emit()
