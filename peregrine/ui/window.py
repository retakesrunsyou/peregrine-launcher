"""The main window: header, side navigation, pages, and the status footer."""

import os
import sys

from PySide6.QtCore import QSize, Qt
from PySide6.QtWidgets import (
    QApplication, QButtonGroup, QFrame, QHBoxLayout, QLabel, QMainWindow, QMessageBox,
    QPlainTextEdit, QProgressBar, QPushButton, QStackedWidget, QVBoxLayout, QWidget,
)

from .. import NAME, UPDATE_REPO, VERSION, auth, avatars, config, discord, paths, performance, updater
from . import workers
from .dialogs import LoginDialog, NewInstanceDialog
from .browse import BrowsePage, ModBrowser, ModpacksPage
from .pages import AccountsPage, ContentPage, HomePage, SettingsPage, icon_button
from .sky import Sky
from .theme import Theme, avatar_pixmap, icon, logo_pixmap, stylesheet, tint


class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle(f"{NAME} Launcher")  # display name also set app-wide
        self.resize(1020, 680)
        self.setMinimumSize(760, 520)
        self.skins = {}          # uuid -> skin path
        self.game_task = None    # the instance being installed/played
        self.game_inst = None

        # The whole window sits on an animated night sky (see sky.py); the header,
        # sidebar and footer are see-through panels over it.
        self.sky = Sky()
        root = self.sky
        outer = QVBoxLayout(root)
        outer.setContentsMargins(0, 0, 0, 0)
        outer.setSpacing(0)
        self.setCentralWidget(root)

        # ---- header
        header = QFrame(objectName="header")
        header.setFixedHeight(64)
        h = QHBoxLayout(header)
        h.setContentsMargins(20, 0, 16, 0)
        self.logo = QLabel()
        h.addWidget(self.logo)
        h.addSpacing(8)
        h.addWidget(QLabel(f"{NAME} Launcher", objectName="appName"))
        h.addStretch()
        self.chip = QPushButton(objectName="accountChip")
        self.chip.setCursor(Qt.PointingHandCursor)
        self.chip.clicked.connect(lambda: self.go(3))
        chip_l = QHBoxLayout(self.chip)
        chip_l.setContentsMargins(6, 4, 8, 4)
        chip_l.setSpacing(10)
        self.chip_face = QLabel()
        chip_text = QVBoxLayout()
        chip_text.setSpacing(0)
        self.chip_name = QLabel(objectName="h2")
        self.chip_kind = QLabel(objectName="faint")
        chip_text.addWidget(self.chip_name)
        chip_text.addWidget(self.chip_kind)
        chip_l.addWidget(self.chip_face)
        chip_l.addLayout(chip_text)
        self.chip.setMinimumSize(170, 50)
        h.addWidget(self.chip)
        outer.addWidget(header)

        # ---- update banner (hidden until a new version is found)
        self.banner = QFrame(objectName="banner")
        b = QHBoxLayout(self.banner)
        b.setContentsMargins(20, 8, 16, 8)
        self.banner_text = QLabel()
        self.update_btn = QPushButton("Update now", objectName="accent")
        self.update_btn.clicked.connect(self.install_update)
        later = QPushButton("Later")
        later.clicked.connect(self.banner.hide)
        b.addWidget(self.banner_text, 1)
        b.addWidget(later)
        b.addWidget(self.update_btn)
        self.banner.hide()
        self.pending_update = None
        outer.addWidget(self.banner)

        # ---- body: nav + pages
        body = QHBoxLayout()
        body.setSpacing(0)
        nav = QFrame(objectName="nav")
        nav.setFixedWidth(216)
        n = QVBoxLayout(nav)
        n.setContentsMargins(14, 18, 14, 16)
        n.setSpacing(4)
        self.nav_group = QButtonGroup(self)
        self.nav_buttons = {}
        n.addWidget(self._nav("Home", "home", 0))
        n.addWidget(self._nav("Mods", "cube", 5))
        n.addWidget(self._nav("Resource packs", "image", 6))
        n.addWidget(self._nav("Modpacks", "package", 4))
        n.addStretch()
        line = QFrame()
        line.setFixedHeight(1)
        line.setStyleSheet(f"background: {Theme.p['border']};")
        self.nav_line = line
        n.addWidget(line)
        n.addSpacing(8)
        n.addWidget(self._nav("Settings", "settings", 2))
        n.addWidget(self._nav("Accounts", "account", 3))
        ver = QLabel(f"v{VERSION}", objectName="faint")
        ver.setContentsMargins(12, 8, 0, 0)
        n.addWidget(ver)
        body.addWidget(nav)

        self.stack = QStackedWidget()
        self.home = HomePage()
        self.content = ContentPage()
        self.settings = SettingsPage()
        self.accounts = AccountsPage(self.skins)
        self.modpacks = ModpacksPage()
        self.mods = BrowsePage("mod", "Mods", "Fabric mods from Modrinth, for the instance you pick. "
                                              "Required libraries come along automatically.")
        self.packs = BrowsePage("resourcepack", "Resource packs",
                                "Texture packs from Modrinth. Added packs are switched on for the next launch.")
        for page in (self.home, self.content, self.settings, self.accounts, self.modpacks, self.mods, self.packs):
            self.stack.addWidget(page)
        body.addWidget(self.stack, 1)
        outer.addLayout(body, 1)

        self.home.play.connect(self.play)
        self.home.open_content.connect(self.open_content)
        self.home.new_instance.connect(self.new_instance)
        self.content.back.connect(lambda: self.go(0))
        self.content.install_fps.connect(self.install_fps)
        self.content.browse.connect(self.browse_mods)
        self.modpacks.install.connect(self.install_modpack)
        self.settings.appearance_changed.connect(self.apply_theme)
        self.settings.check_updates.connect(lambda: self.check_updates(manual=True))
        self.accounts.changed.connect(self.refresh_accounts)
        self.accounts.add_microsoft.connect(self.add_microsoft)

        # ---- footer: status, progress, console
        footer = QFrame(objectName="footer")
        f = QHBoxLayout(footer)
        f.setContentsMargins(20, 6, 12, 6)
        self.status = QLabel("Ready", objectName="faint")
        self.bar = QProgressBar(textVisible=False)
        self.bar.setFixedSize(180, 4)
        self.bar.hide()
        self.console_btn = icon_button("console", "Show console")
        self.console_btn.setCheckable(True)
        self.console_btn.toggled.connect(lambda on: self.console.setVisible(on))
        f.addWidget(self.status, 1)
        f.addWidget(self.bar)
        f.addWidget(self.console_btn)
        self.console = QPlainTextEdit(objectName="console", readOnly=True)
        self.console.setMaximumBlockCount(4000)
        self.console.setFixedHeight(200)
        self.console.hide()
        outer.addWidget(self.console)
        outer.addWidget(footer)

        from .pages import sync_screen_size
        sync_screen_size()
        self.apply_theme()
        self.go(0)
        self.refresh_accounts()
        if config.load()["check_updates"]:
            self.check_updates()
        discord.update("idle")

    # ---------------------------------------------------------- chrome

    def _nav(self, text, icon_name, index):
        b = QPushButton(f"  {text}", objectName="navButton", checkable=True)
        b.setIconSize(QSize(18, 18))
        b.setCursor(Qt.PointingHandCursor)
        b.clicked.connect(lambda: self.go(index))
        self.nav_group.addButton(b)
        self.nav_buttons[index] = (b, icon_name)
        return b

    def go(self, index):
        changed = self.stack.currentIndex() != index
        self.stack.setCurrentIndex(index)
        if changed:
            if index == 0:
                self.home.animate_in()  # its cards animate themselves (no fade on top of that)
            else:
                self._fade_in(self.stack.currentWidget())
        if index == 2:
            self.settings.build()
        nav_index = 0 if index == 1 else index  # Content lives under Home
        for i, (b, name) in self.nav_buttons.items():
            b.setChecked(i == nav_index)
            b.setIcon(icon(name, Theme.accent if i == nav_index else None, 18))

    def _fade_in(self, page):
        """A quick fade as pages change, so switching feels smooth rather than abrupt."""
        from PySide6.QtCore import QEasingCurve, QPropertyAnimation
        from PySide6.QtWidgets import QGraphicsOpacityEffect
        effect = QGraphicsOpacityEffect(page)
        page.setGraphicsEffect(effect)
        anim = QPropertyAnimation(effect, b"opacity", page)
        anim.setDuration(160)
        anim.setStartValue(0.0)
        anim.setEndValue(1.0)
        anim.setEasingCurve(QEasingCurve.OutCubic)
        # Drop the effect afterwards: it would otherwise slow down scrolling.
        anim.finished.connect(lambda: page.setGraphicsEffect(None))
        anim.start()
        self._page_anim = anim

    def apply_theme(self):
        cfg = config.load()
        Theme.set(cfg["theme"], cfg["accent"])
        QApplication.instance().setStyleSheet(stylesheet())
        self.sky.set_enabled(bool(cfg.get("animations", True)))
        self.logo.setPixmap(logo_pixmap(30))
        self.nav_line.setStyleSheet(f"background: {Theme.p['border']};")
        self.banner.setStyleSheet(f"#banner {{ background: {tint(Theme.accent, 0.12)}; "
                                  f"border-bottom: 1px solid {Theme.p['border']}; }}")
        self.console_btn.setIcon(icon("console", None, 18))
        self.go(self.stack.currentIndex())
        self.home.refresh()
        self.accounts.refresh()
        self.refresh_chip()

    # -------------------------------------------------------- accounts

    def refresh_accounts(self):
        self.accounts.refresh()
        self.refresh_chip()
        for a in auth.load_accounts()["accounts"]:
            if a.get("type") == "msa" and a["uuid"] not in self.skins:
                workers.run(avatars.skin_path, a["uuid"],
                            done=lambda path, u=a["uuid"]: self.got_skin(u, path))

    def got_skin(self, uuid, path):
        if path:
            self.skins[uuid] = path
            self.accounts.refresh()
            self.refresh_chip()

    def refresh_chip(self):
        a = auth.active_account()
        if a:
            self.chip_name.setText(a["name"])
            self.chip_kind.setText("Minecraft account" if a.get("type") == "msa" else "Offline test")
            self.chip_face.setPixmap(avatar_pixmap(self.skins.get(a["uuid"]), a["name"], 34))
        else:
            self.chip_name.setText("Sign in")
            self.chip_kind.setText("No account yet")
            self.chip_face.setPixmap(avatar_pixmap(None, "?", 34))

    def add_microsoft(self):
        cfg = config.load()
        if not cfg["client_id"]:
            QMessageBox.information(self, "Client ID needed",
                "This copy of Peregrine doesn't have Microsoft sign-in set up yet.\n\n"
                "Developer: put your approved client ID in CLIENT_ID in peregrine/__init__.py, "
                "or add it under Settings → Microsoft login.")
            return
        dlg = LoginDialog(self, cfg["client_id"])
        if dlg.exec() and dlg.account:
            auth.add_account(dlg.account)
            self.refresh_accounts()
            self.say(f"Signed in as {dlg.account['name']}")

    # ------------------------------------------------------- instances

    def new_instance(self):
        dlg = NewInstanceDialog(self, config.load()["show_snapshots"])
        if dlg.exec():
            try:
                dlg.create()
            except Exception as e:
                QMessageBox.warning(self, "Couldn't create the instance", str(e))
            self.home.refresh()

    def open_content(self, inst):
        self.content.open(inst)
        self.go(1)

    def install_fps(self, inst):
        if self.busy():
            return
        self._start(workers.Task(inst.install_performance_mods, with_progress=True), inst,
                    done=lambda _: (self.say("FPS mods installed"), self.content.refresh()))

    def play(self, inst):
        account = auth.active_account()
        if not account:
            self.go(3)
            self.say("Add an account to play")
            return
        if self.busy():
            return
        cfg = config.load()
        self.console.clear()
        if cfg["open_console"]:
            self.console_btn.setChecked(True)
        import time
        inst.update(last_played=time.time())
        task = workers.Task(inst.launch, account, with_progress=True)
        task.log.connect(self.console.appendPlainText)
        task.log.connect(self._watch_game_log)
        try:
            log = open(inst.log_file, "w", encoding="utf-8", errors="replace")
            task.log.connect(lambda line: log.write(line + "\n"))
            task.finished.connect(log.close)
        except OSError:
            pass  # the console still shows output
        self._start(task, inst, done=lambda code: self.game_closed(inst, code))

    def game_closed(self, inst, code):
        if code == 0:
            self.say("Minecraft closed")
            return
        try:
            blamed = performance.after_crash(inst)
        except Exception as e:
            print(f"[peregrine] couldn't check the crash: {e}")
            blamed = []
        if blamed:
            names = ", ".join(blamed)
            self.say(f"{names} crashed the game, so it's switched off for {inst.name}. Press Play again.")
            QMessageBox.information(
                self, "Fixed a crash",
                f"Minecraft crashed because of {names}, one of the performance mods.\n\n"
                f"It's switched off for {inst.name} now, so the game will start next time. "
                "It'll be tried again when you move this instance to another Minecraft version.")
        else:
            self.say(f"Minecraft closed with an error (code {code}). Open the console to see why.")

    def busy(self) -> bool:
        if self.game_task and self.game_task.isRunning():
            self.say("Wait for the current game or download to finish")
            return True
        return False

    def _start(self, task, inst, done):
        self.game_task, self.game_inst = task, inst
        self.home.set_state(inst.folder, "busy")
        task.progress.connect(self.on_progress)
        task.done.connect(done)
        task.failed.connect(self.on_failed)
        task.finished.connect(self.on_finished)
        task.start()

    def on_progress(self, done, total, msg):
        if msg == "Starting Minecraft" and self.game_inst:
            self.bar.show()
            self.bar.setMaximum(0)  # moving bar: we can't know how long Minecraft takes
            self.game_loading = True
            self.sky.set_paused(True)  # the game gets all the power
            self.say("Minecraft is loading…")
            self.home.set_state(self.game_inst.folder, "running")
            discord.update("playing", self.game_inst.name, self.game_inst.subtitle())
            mode = config.load()["on_launch"]
            if mode == "minimize":
                self.showMinimized()
            elif mode == "hide":
                self.hide()
            return
        self.bar.show()
        self.bar.setMaximum(max(total, 1))
        self.bar.setValue(done if total else 1)
        self.say(f"{msg}  {done} of {total}" if total else msg)

    def _watch_game_log(self, line):
        """Notice when Minecraft has finished loading, from its own log."""
        if getattr(self, "game_loading", False) and (
                "Sound engine started" in line or "Created:" in line and "textures-atlas" in line):
            self.game_loading = False
            self.bar.hide()
            self.bar.setMaximum(1)
            if self.game_inst:
                self.say(f"Playing {self.game_inst.name}")

    def on_failed(self, msg):
        QMessageBox.warning(self, "Something went wrong", msg)
        self.say("Ready")

    def changeEvent(self, e):
        from PySide6.QtCore import QEvent
        if e.type() in (QEvent.WindowStateChange, QEvent.ActivationChange):
            # The sky only moves while you're looking at the launcher.
            playing = bool(self.game_task and self.game_task.isRunning())
            self.sky.set_paused(self.isMinimized() or playing or not self.isActiveWindow())
        super().changeEvent(e)

    def on_finished(self):
        self.sky.set_paused(self.isMinimized() or not self.isActiveWindow())
        self.game_loading = False
        self.bar.setMaximum(1)
        self.bar.hide()
        discord.update("idle")
        if self.game_inst:
            self.home.set_state(self.game_inst.folder, "")
            # Show what Play and installs added (Peregrine Client, Fabric API, mods).
            if self.stack.currentWidget() is self.content and self.content.inst is not None \
                    and self.content.inst.folder == self.game_inst.folder:
                self.content.refresh()
        if self.isHidden() or self.isMinimized():
            self.showNormal()
            self.activateWindow()
        self.refresh_chip()

    def browse_mods(self, inst):
        dlg = ModBrowser(self, inst)
        dlg.exec()
        if dlg.added:
            self.content.refresh()

    def install_modpack(self, hit, btn):
        if self.busy():
            return
        btn.setEnabled(False)
        btn.setText("Installing…")
        from .. import modpacks
        task = workers.Task(modpacks.install, hit, with_progress=True)
        task.progress.connect(self.on_progress)
        task.finished.connect(self.bar.hide)

        def done(inst):
            self.say(f"{hit['title']} is ready to play")
            self.home.refresh()
            try:
                btn.setText("Installed")
            except RuntimeError:
                pass
            self.go(0)

        def failed(msg):
            QMessageBox.warning(self, "Couldn't install the modpack", msg)
            self.say("Ready")
            try:
                btn.setText("Install")
                btn.setEnabled(True)
            except RuntimeError:
                pass
        task.done.connect(done)
        task.failed.connect(failed)
        self.game_task = task
        task.start()

    # --------------------------------------------------------- updates

    def check_updates(self, manual=False):
        if not UPDATE_REPO:
            return
        if manual:
            self.say("Checking for updates…")
        workers.run(updater.check,
                    done=lambda u: self.got_update(u, manual),
                    failed=lambda m: self.say("Couldn't check for updates") if manual else None)

    def got_update(self, update, manual):
        if not update:
            if manual:
                self.say(f"You're up to date ({VERSION})")
            return
        self.pending_update = update
        self.banner_text.setText(f"Peregrine {update['version']} is ready to install. "
                                 f"You're on {VERSION}.")
        if update["notes"]:
            self.banner_text.setToolTip(update["notes"])
        self.update_btn.setEnabled(True)
        self.banner.show()

    def install_update(self):
        if not self.pending_update or self.busy():
            return
        self.update_btn.setEnabled(False)
        task = workers.Task(updater.apply, self.pending_update, with_progress=True)
        task.progress.connect(lambda d, t, m: self.say(m))
        task.done.connect(self.update_installed)
        task.failed.connect(lambda m: (QMessageBox.warning(self, "Update failed", m),
                                       self.update_btn.setEnabled(True), self.say("Ready")))
        self.game_task = task
        task.start()

    def update_installed(self, _):
        QMessageBox.information(self, "Updated",
                                f"Peregrine {self.pending_update['version']} is installed. "
                                "It will restart now.")
        updater.restart()

    def say(self, text):
        self.status.setText(text)


def app_icon():
    """The falcon icon at several sizes, for the window, taskbar and dock."""
    from PySide6.QtGui import QIcon
    from .theme import render_app_icon
    ic = QIcon()
    for size in (16, 24, 32, 48, 64, 128, 256):
        ic.addPixmap(render_app_icon(size))
    return ic


def install_crash_handler(app):
    """Log unexpected errors and show them, instead of the app silently vanishing."""
    import datetime
    import traceback
    log = paths.LOGS / "launcher.log"

    def hook(kind, value, tb):
        text = "".join(traceback.format_exception(kind, value, tb))
        try:
            with open(log, "a") as f:
                f.write(f"\n--- {datetime.datetime.now():%Y-%m-%d %H:%M:%S} (v{VERSION})\n{text}")
        except OSError:
            pass
        sys.__stderr__.write(text)
        try:
            QMessageBox.warning(None, "Something went wrong",
                                f"{value}\n\nPeregrine kept running. Details were saved to {log}")
        except Exception:
            pass
    sys.excepthook = hook


def main():
    paths.ensure()
    # "peregrine" as the program name lets the desktop match the window to
    # peregrine.desktop, so the taskbar and dock show the right icon.
    app = QApplication(["peregrine"] + sys.argv[1:])
    app.setApplicationName("peregrine")
    app.setApplicationDisplayName(f"{NAME} Launcher")
    app.setDesktopFileName("peregrine")
    app.setWindowIcon(app_icon())
    install_crash_handler(app)
    cfg = config.load()
    Theme.set(cfg["theme"], cfg["accent"])
    win = MainWindow()
    win.show()
    code = app.exec()
    if workers._running:
        # A game or download is still going. Leave without tearing down its
        # thread (Qt would abort); the game runs on by itself.
        sys.stdout.flush()
        os._exit(code)
    sys.exit(code)
