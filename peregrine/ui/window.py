"""The main window: header, side navigation, pages, and the status footer."""

import os
import sys

from PySide6.QtCore import Qt
from PySide6.QtWidgets import (
    QApplication, QFrame, QHBoxLayout, QLabel, QMainWindow,
    QPlainTextEdit, QProgressBar, QPushButton, QStackedWidget, QVBoxLayout, QWidget,
)

from .. import NAME, UPDATE_REPO, VERSION, auth, avatars, config, discord, instances, paths, performance, updater
from . import workers
from .dialogs import LoginDialog, NewInstanceDialog
from .browse import BrowsePage, MarketPage, ModBrowser, ModpacksPage
from .friends_page import FriendsPage
from .modern import FloatButton, Pill, Rail, StatusRing
from .recent import RecentPage
from .pages import AccountsPage, ContentPage, HomePage, SettingsPage, icon_button
from .sheet import tell
from .sky import Sky
from .theme import Theme, avatar_pixmap, icon, logo_pixmap, stylesheet, tint


class MainWindow(QMainWindow):
    # Pages by name. "content" (one instance's mods) lives under Home in the rail,
    # "accounts" under the profile pill, "browse" under Recent's Mods & packs button.
    RAIL = [("friends", "s_user", "Friends"), ("profiles", "users", "Profiles"), ("locker", "s_shirt", "Locker"), ("home", "s_home", "Instances"),
            ("recent", "s_grid", "Recent servers"), ("settings", "s_gear", "Settings")]
    RAIL_OF = {"content": "home", "accounts": None, "browse": "recent"}

    def __init__(self):
        super().__init__()
        self.tray, self.quitting = None, False  # the tray icon comes with make_tray()
        self.setWindowTitle(f"{NAME} Launcher")  # display name also set app-wide
        self.resize(1040, 680)
        self.setMinimumSize(860, 600)
        self.skins = {}          # uuid -> skin path
        self.game_task = None    # the instance being installed/played
        self.game_inst = None
        self.page = None
        self.friends_online = None

        # The whole window sits on an animated night sky (see sky.py). The rail is
        # solid; everything else floats over the sky as glass.
        self.sky = Sky()
        root = self.sky
        outer = QHBoxLayout(root)
        outer.setContentsMargins(0, 0, 0, 0)
        outer.setSpacing(0)
        self.setCentralWidget(root)

        # ---- side rail
        self.logo = QLabel()
        self.logo.setToolTip(f"{NAME} {VERSION}")
        self.rail = Rail(self.logo, self.RAIL)
        self.rail.picked.connect(self.go)
        outer.addWidget(self.rail)
        self.rail_open = bool(config.load().get("rail_open", True))
        if not self.rail_open:
            self.rail.setFixedWidth(0)
        self.toggle = FloatButton("s_panel" if self.rail_open else "s_panel_open", "Hide or show the side panel", root)
        self.toggle.clicked.connect(self.toggle_rail)
        root.installEventFilter(self)

        main = QVBoxLayout()
        main.setContentsMargins(0, 0, 0, 0)
        main.setSpacing(0)
        outer.addLayout(main, 1)

        # ---- top bar: you (and friends online) on the left, your server in the middle
        top = QWidget()
        top.setFixedHeight(108)
        t = QHBoxLayout(top)
        t.setContentsMargins(40, 26, 32, 10)
        t.setSpacing(16)
        self.profile = Pill()
        self.profile.clicked.connect(lambda: self.go("accounts"))
        p = QHBoxLayout(self.profile)
        p.setContentsMargins(10, 8, 22, 8)
        p.setSpacing(12)
        self.chip_face = QLabel()
        self.chip_face.setFixedSize(46, 46)
        p.addWidget(self.chip_face)
        ptext = QVBoxLayout()
        ptext.setSpacing(0)
        self.chip_name = QLabel(objectName="pillTitle")
        self.chip_kind = QLabel(objectName="pillSub")
        ptext.addWidget(self.chip_name)
        ptext.addWidget(self.chip_kind)
        p.addLayout(ptext)
        t.addWidget(self.profile)
        t.addStretch(1)

        self.server_pill = Pill()
        self.server_pill.clicked.connect(lambda: self.go("recent"))
        sp = QHBoxLayout(self.server_pill)
        sp.setContentsMargins(14, 8, 14, 8)
        sp.setSpacing(14)
        self.server_icon = QLabel()
        self.server_icon.setFixedSize(38, 38)
        sp.addWidget(self.server_icon)
        stext = QVBoxLayout()
        stext.setSpacing(0)
        self.server_name = QLabel(objectName="pillTitle")
        self.server_sub = QLabel(objectName="pillSub")
        stext.addWidget(self.server_name)
        stext.addWidget(self.server_sub)
        sp.addLayout(stext)
        sp.addSpacing(6)
        self.ring = StatusRing(34)
        sp.addWidget(self.ring)
        t.addWidget(self.server_pill)
        t.addStretch(1)
        self.version_pill = QLabel(f"v{VERSION}", objectName="faint")
        t.addWidget(self.version_pill)
        main.addWidget(top)

        # ---- update banner (hidden until a new version is found)
        self.banner = QFrame(objectName="banner")
        b = QHBoxLayout(self.banner)
        b.setContentsMargins(40, 8, 32, 8)
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
        main.addWidget(self.banner)

        # ---- pages
        self.stack = QStackedWidget()
        self.recent = RecentPage()
        self.home = HomePage()
        self.content = ContentPage()
        self.settings = SettingsPage()
        self.accounts = AccountsPage(self.skins)
        self.friends = FriendsPage()
        from .locker_page import LockerPage
        self.locker = LockerPage(say=lambda t: self.say(t))
        from .profiles_page import ProfilesPage
        self.profiles = ProfilesPage(say=lambda t: self.say(t))
        self.modpacks = ModpacksPage()
        self.mods = BrowsePage("mod", "Mods", "Fabric mods from Modrinth, for the instance you pick. "
                                              "Required libraries come along automatically.")
        self.packs = BrowsePage("resourcepack", "Resource packs",
                                "Texture packs from Modrinth. Added packs are switched on for the next launch.")
        self.shaders = BrowsePage("shader", "Shaders", "Shader packs from Modrinth. Iris comes along to run them, "
                                  "and the pack you add is switched on.")
        self.market = MarketPage(self.mods, self.packs, self.modpacks, self.shaders)
        self.pages = {"recent": self.recent, "home": self.home, "content": self.content,
                      "settings": self.settings, "accounts": self.accounts, "friends": self.friends,
                      "profiles": self.profiles, "locker": self.locker,
                      "browse": self.market}
        for page in self.pages.values():
            self.stack.addWidget(page)
        main.addWidget(self.stack, 1)

        self.recent.join.connect(self.join_server)
        self.recent.play.connect(self.play)
        self.recent.open_content.connect(self.open_content)
        self.recent.browse.connect(lambda: self.go("browse"))
        self.recent.new_instance.connect(self.new_instance)
        self.recent.current_changed.connect(self.show_server)
        self.home.play.connect(self.play)
        self.home.open_content.connect(self.open_content)
        self.home.new_instance.connect(self.new_instance)
        self.content.back.connect(lambda: self.go("home"))
        self.content.install_fps.connect(self.install_fps)
        self.content.browse.connect(self.browse_mods)
        self.content.recommended.connect(self.recommended_mods)
        self.content.find_shaders.connect(lambda: (self.market.show_tab("shaders"), self.go("browse")))
        self.modpacks.install.connect(self.install_modpack)
        self.settings.appearance_changed.connect(self.apply_theme)
        self.settings.check_updates.connect(lambda: self.check_updates(manual=True))
        self.accounts.changed.connect(self.refresh_accounts)
        self.accounts.add_microsoft.connect(self.add_microsoft)
        self.friends.counts.connect(self.got_friend_counts)
        self.friends.sign_in.connect(lambda: self.go("accounts"))

        # ---- footer: status, progress, console
        footer = QFrame(objectName="footer")
        f = QHBoxLayout(footer)
        self.footer_layout = f
        f.setContentsMargins(40 if self.rail_open else 92, 4, 24, 8)
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
        main.addWidget(self.console)
        main.addWidget(footer)

        from .pages import sync_screen_size
        sync_screen_size()
        self.apply_theme()
        self.recent.refresh()
        self.go("recent")
        self.refresh_accounts()
        if config.load()["check_updates"]:
            self.check_updates()
        discord.update("idle")

    # ---------------------------------------------------------- chrome

    def go(self, key):
        if isinstance(key, int):  # old callers: 0 home, 2 settings, 3 accounts, 4 modpacks
            key = {0: "home", 1: "content", 2: "settings", 3: "accounts", 4: "browse",
                   5: "browse", 6: "browse"}.get(key, "recent")
            if key == "browse":
                self.market.show_tab("modpacks")
        page = self.pages[key]
        changed = self.page != key
        self.page = key
        self.stack.setCurrentWidget(page)
        if changed:
            if key == "home":
                self.home.animate_in()  # its cards animate themselves
            else:
                self._fade_in(page)
        if key == "settings":
            self.settings.build()
        if key == "friends" and changed:
            self.friends.render()
        if key == "profiles" and changed:
            self.profiles.build()
        if key == "locker" and changed:
            self.locker.build()
        rail_key = self.RAIL_OF.get(key, key)
        self.rail.select(rail_key)

    def _fade_in(self, page):
        """A quick fade as pages change, so switching feels smooth rather than abrupt."""
        from PySide6.QtCore import QEasingCurve, QPoint, QPropertyAnimation
        from PySide6.QtWidgets import QGraphicsOpacityEffect
        if not config.load().get("animations", True):
            return
        effect = QGraphicsOpacityEffect(page)
        page.setGraphicsEffect(effect)
        anim = QPropertyAnimation(effect, b"opacity", page)
        anim.setDuration(220)
        anim.setStartValue(0.0)
        anim.setEndValue(1.0)
        anim.setEasingCurve(QEasingCurve.OutCubic)
        # A short slide in from the side as well, so pages glide in.
        end = page.pos()
        slide = QPropertyAnimation(page, b"pos", page)
        slide.setDuration(260)
        slide.setStartValue(end + QPoint(18, 0))
        slide.setEndValue(end)
        slide.setEasingCurve(QEasingCurve.OutCubic)

        def done():
            try:
                page.setGraphicsEffect(None)  # it would otherwise slow down scrolling
            except RuntimeError:
                pass
        anim.finished.connect(done)
        anim.start()
        slide.start()
        self._page_anim = (anim, slide)

    def toggle_rail(self):
        self.rail_open = not self.rail_open
        self.rail.set_open(self.rail_open)
        self.toggle.icon_name = "s_panel" if self.rail_open else "s_panel_open"
        self.toggle.update()
        self.footer_layout.setContentsMargins(40 if self.rail_open else 92, 4, 24, 8)
        cfg = config.load()
        cfg["rail_open"] = self.rail_open
        config.save(cfg)

    def eventFilter(self, obj, e):
        from PySide6.QtCore import QEvent
        if obj is self.sky and e.type() == QEvent.Resize:
            self.toggle.move(19, self.sky.height() - self.toggle.height() - 24)
            self.toggle.raise_()
        return super().eventFilter(obj, e)

    def apply_theme(self):
        cfg = config.load()
        Theme.set(cfg["theme"], cfg["accent"])
        QApplication.instance().setStyleSheet(stylesheet())
        from .sky import season
        self.sky.season = season() if cfg.get("seasonal", True) else ""
        self.sky.set_enabled(bool(cfg.get("animations", True)))
        self.logo.setPixmap(logo_pixmap(40))
        self.banner.setStyleSheet(f"#banner {{ background: {tint(Theme.accent, 0.12)}; }}")
        self.console_btn.setIcon(icon("console", None, 18))
        self.go(self.page or "recent")
        self.rail.update()
        for b in self.rail.buttons.values():
            b.update()
        self.home.refresh()
        self.accounts.refresh()
        self.refresh_chip()
        self.show_server(self.recent.entry, self.recent.status.get(self.recent.entry["address"], "checking")
                         if self.recent.entry else None)

    def show_server(self, entry, st):
        """The top bar's server pill: the server on the Recent page and how it's doing."""
        from .recent import server_icon
        from .. import servers
        if entry is None:
            self.server_pill.hide()
            return
        self.server_pill.show()
        self.server_name.setText(servers.display_name(entry))
        icon_png = entry.get("icon") or (st.get("favicon") if isinstance(st, dict) else None)
        self.server_icon.setPixmap(server_icon(icon_png, 38))
        if st == "checking":
            self.server_sub.setText(entry["address"])
            self.ring.set_state("checking")
        elif isinstance(st, dict):
            self.server_sub.setText(f"{st['online']:,} playing  ·  {entry['address']}")
            self.ring.set_state("online")
        else:
            self.server_sub.setText(f"Offline  ·  {entry['address']}")
            self.ring.set_state("offline")

    def got_friend_counts(self, online, total):
        self.friends_online = None if online < 0 else online
        self.refresh_chip()

    # -------------------------------------------------------- accounts

    def refresh_accounts(self):
        self.accounts.refresh()
        self.friends_online = None
        self.refresh_chip()
        self.friends.load()
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
            if a.get("type") != "msa":
                kind = "Offline test account"
            elif self.friends_online is None:
                kind = "Microsoft account"
            else:
                n = self.friends_online
                kind = f"{n} friend{'' if n == 1 else 's'} online"
            self.chip_kind.setText(kind)
            self.chip_face.setPixmap(avatar_pixmap(self.skins.get(a["uuid"]), a["name"], 46))
        else:
            self.chip_name.setText("Sign in")
            self.chip_kind.setText("No account yet")
            self.chip_face.setPixmap(avatar_pixmap(None, "?", 46))

    def add_microsoft(self):
        cfg = config.load()
        if not cfg["client_id"]:
            tell(self, "Client ID needed",
                 "This copy of Peregrine doesn't have Microsoft sign-in set up yet.\n\n"
                 "Developer: put your approved client ID in CLIENT_ID in peregrine/__init__.py, "
                 "or add it under Settings → Microsoft login.", "warning")
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
            inst = None
            try:
                inst = dlg.create()
            except Exception as e:
                tell(self, "Couldn't create the instance", str(e), "error")
            self.home.refresh()
            if inst is not None:
                self.setup_new(inst)

    def setup_new(self, inst):
        """A new instance gets its default mods straight away (not only on first Play)."""
        if not (inst.data.get("loader") == "fabric" or inst.performance) or \
                (self.game_task and self.game_task.isRunning()):
            return  # vanilla, or busy: Play adds them
        self.say(f"Adding the default mods to {inst.name}…")
        self._start(workers.Task(inst.setup_mods, with_progress=True), inst,
                    done=lambda report: self._new_ready(inst, report))

    def _new_ready(self, inst, report=None):
        self.last_setup = report
        self.say(f"{inst.name} is ready: default mods added")
        self.home.refresh()
        shown = getattr(self.content, "inst", None)
        if shown is not None and shown.folder == inst.folder:
            self.content.inst = inst
            self.content.refresh()

    def open_content(self, inst):
        self.content.open(inst)
        self.go("content")

    def install_fps(self, inst):
        if self.busy():
            return
        self._start(workers.Task(inst.install_performance_mods, with_progress=True), inst,
                    done=lambda _: (self.say("FPS mods installed"), self.content.refresh()))

    def join_server(self, inst, address):
        self.play(inst, join=address)

    def play(self, inst, join=None):
        account = auth.active_account()
        if not account:
            self.go("accounts")
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
        import functools
        task = workers.Task(functools.partial(inst.launch, join=join), account, with_progress=True)
        task.log.connect(self.console.appendPlainText)
        task.log.connect(self._watch_game_log)
        try:
            log = open(inst.log_file, "w", encoding="utf-8", errors="replace")
            task.log.connect(lambda line: log.write(line + "\n"))
            task.finished.connect(log.close)
        except OSError:
            pass  # the console still shows output
        self.joining = join
        self._start(task, inst, done=lambda code: self.game_closed(inst, code))

    def game_closed(self, inst, code):
        if code == 0:
            self.say("Minecraft closed")
            return
        try:
            from .. import extras
            blamed = performance.after_crash(inst) + extras.after_crash(inst)
        except Exception as e:
            print(f"[peregrine] couldn't check the crash: {e}")
            blamed = []
        if blamed:
            names = ", ".join(blamed)
            self.say(f"{names} crashed the game, so it's switched off for {inst.name}. Press Play again.")
            tell(
                self, "Fixed a crash",
                f"Minecraft crashed because of {names}, one of the mods Peregrine adds.\n\n"
                f"It's switched off for {inst.name} now, so the game will start next time. "
                "It'll be tried again when you move this instance to another Minecraft version.", "success")
        else:
            self.say(f"Minecraft closed with an error (code {code}). Open the console to see why.")

    def busy(self) -> bool:
        if self.game_task and self.game_task.isRunning():
            self.say("Wait for the current game or download to finish")
            return True
        return False

    def _start(self, task, inst, done):
        self.game_task, self.game_inst = task, inst
        instances.RUNNING.add(str(inst.folder))
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
            self.say(f"Minecraft is loading… joining {join_text}" if (join_text := getattr(self, "joining", None))
                     else "Minecraft is loading…")
            self.home.set_state(self.game_inst.folder, "running")
            import time as _t
            self.game_started = _t.time()
            self.game_where = "Starting up"
            self._discord_playing()
            mode = config.load()["on_launch"]
            if mode == "minimize":
                self.showMinimized()
            elif mode in ("hide", "tray"):
                if self.tray is not None:
                    self.hide()  # back with a click on the tray icon
                else:
                    self.showMinimized()  # no tray here: minimizing is the way back
            self.update_tray()
            return
        self.bar.show()
        self.bar.setMaximum(max(total, 1))
        self.bar.setValue(done if total else 1)
        self.say(f"{msg}  {done} of {total}" if total else msg)

    def _discord_playing(self):
        inst = self.game_inst
        if inst is not None:
            discord.update("playing", inst.data.get("mc_version", ""), getattr(self, "game_where", ""),
                           inst.name, getattr(self, "game_started", 0))

    def _watch_game_log(self, line):
        """Notice when Minecraft has finished loading, from its own log."""
        where = discord.where_from_log(line)
        if where is not None and where != getattr(self, "game_where", None):
            self.game_where = where
            self._discord_playing()
        if getattr(self, "game_loading", False) and (
                "Sound engine started" in line or "Created:" in line and "textures-atlas" in line):
            self.game_loading = False
            self.bar.hide()
            self.bar.setMaximum(1)
            if self.game_inst:
                self.say(f"Playing {self.game_inst.name}")

    def on_failed(self, msg):
        tell(self, "Something went wrong", msg, "error")
        self.say("Ready")

    def changeEvent(self, e):
        from PySide6.QtCore import QEvent
        if e.type() == QEvent.WindowStateChange and not self.isMinimized() and self.isVisible():
            self._morph()
        if e.type() in (QEvent.WindowStateChange, QEvent.ActivationChange):
            # The sky only moves while you're looking at the launcher.
            playing = bool(self.game_task and self.game_task.isRunning())
            self.sky.set_paused(self.isMinimized() or playing or not self.isActiveWindow())
        super().changeEvent(e)

    def _morph(self):
        """Maximize, restore and fullscreen: the old view stretches and fades into the new one,
        so the switch glides instead of snapping."""
        from PySide6.QtCore import QEasingCurve, QPropertyAnimation, QTimer
        from PySide6.QtWidgets import QGraphicsOpacityEffect, QLabel
        if not config.load().get("animations", True):
            return
        central = self.centralWidget()
        if central is None or central.width() < 50:
            return
        try:
            shot = central.grab()  # still the old size: the window hasn't been laid out again yet
        except RuntimeError:
            return
        old = getattr(self, "_morph_label", None)
        if old is not None:
            old.deleteLater()
        cover = QLabel(self)
        cover.setAttribute(Qt.WA_TransparentForMouseEvents)
        cover.setScaledContents(True)
        cover.setPixmap(shot)
        cover.setGeometry(central.geometry())
        cover.show()
        cover.raise_()
        self._morph_label = cover
        effect = QGraphicsOpacityEffect(cover)
        effect.setOpacity(1.0)
        cover.setGraphicsEffect(effect)
        self.morphs = getattr(self, "morphs", 0) + 1
        fade = QPropertyAnimation(effect, b"opacity", cover)
        fade.setDuration(280)
        fade.setStartValue(1.0)
        fade.setEndValue(0.0)
        fade.setEasingCurve(QEasingCurve.OutCubic)
        grow = QPropertyAnimation(cover, b"geometry", cover)
        grow.setDuration(280)
        grow.setEasingCurve(QEasingCurve.OutCubic)
        grow.setStartValue(central.geometry())

        def start():
            try:
                grow.setEndValue(self.centralWidget().geometry())  # the new size, once laid out
                grow.start()
                fade.start()
            except RuntimeError:
                pass

        def done():
            try:
                cover.deleteLater()
            except RuntimeError:
                pass
            if getattr(self, "_morph_label", None) is cover:
                self._morph_label = None
        fade.finished.connect(done)
        cover._anims = (fade, grow)
        QTimer.singleShot(0, start)

    # ---- system tray

    def make_tray(self):
        """The tray icon: click to open the launcher; right-click for Play, Open and Quit."""
        from PySide6.QtWidgets import QMenu, QSystemTrayIcon
        self.tray = None
        self.quitting = False
        if not QSystemTrayIcon.isSystemTrayAvailable():
            return
        tray = QSystemTrayIcon(app_icon(), self)
        from .theme import round_menu
        menu = round_menu(QMenu())
        menu.setObjectName("trayMenu")
        self.tray_open = menu.addAction(f"Open {NAME}")
        self.tray_open.triggered.connect(self.bring_back)
        self.tray_play = menu.addAction("Play")
        self.tray_play.triggered.connect(self.play_last)
        menu.addSeparator()
        quit_action = menu.addAction(f"Quit {NAME}")
        quit_action.triggered.connect(self.quit_app)
        tray.setContextMenu(menu)
        tray.activated.connect(self._tray_clicked)
        self.tray_menu = menu
        tray.show()
        self.tray = tray
        QApplication.instance().setQuitOnLastWindowClosed(False)
        self.update_tray()

    def update_tray(self):
        if getattr(self, "tray", None) is None:
            return
        playing = bool(self.game_task and self.game_task.isRunning())
        name = self.game_inst.name if self.game_inst else None
        self.tray.setToolTip(f"{NAME} Launcher" + (f"  ·  Playing {name}" if playing and name else ""))
        last = self.last_instance()
        self.tray_play.setText(f"Play {last.name}" if last else "Play")
        self.tray_play.setEnabled(last is not None and not playing)

    def last_instance(self):
        try:
            insts = instances.all_instances()
        except Exception:
            return None
        return max(insts, key=lambda i: i.data.get("last_played", 0), default=None)

    def play_last(self):
        inst = self.last_instance()
        if inst is not None:
            self.play(inst)

    def _tray_clicked(self, reason):
        from PySide6.QtWidgets import QSystemTrayIcon
        if reason in (QSystemTrayIcon.Trigger, QSystemTrayIcon.DoubleClick):
            if self.isVisible() and not self.isMinimized() and self.isActiveWindow():
                self.hide()
            else:
                self.bring_back()

    def bring_back(self):
        self.showNormal()
        self.raise_()
        self.activateWindow()

    def quit_app(self):
        self.quitting = True
        if getattr(self, "tray", None) is not None:
            self.tray.hide()
        QApplication.instance().quit()

    def closeEvent(self, e):
        if not getattr(self, "quitting", False) and getattr(self, "tray", None) is not None \
                and config.load().get("close_to_tray", True):
            e.ignore()
            self.hide()
            cfg = config.load()
            if not cfg.get("tray_told"):
                self.tray.showMessage(f"{NAME} is still here", "It's in the system tray. Right-click the icon to quit.",
                                      app_icon(), 4000)
                config.save(dict(cfg, tray_told=True))
            return
        if getattr(self, "tray", None) is not None:
            self.tray.hide()
        QApplication.instance().quit()
        super().closeEvent(e)

    def on_finished(self):
        self.sky.set_paused(self.isMinimized() or not self.isActiveWindow())
        self.game_loading = False
        self.bar.setMaximum(1)
        self.bar.hide()
        discord.update("idle")
        self.joining = None
        self.recent.refresh()  # what you just played comes first
        if self.game_inst:
            instances.RUNNING.discard(str(self.game_inst.folder))
            self.home.set_state(self.game_inst.folder, "")
            # Show what Play and installs added (Peregrine Client, Fabric API, mods).
            if self.stack.currentWidget() is self.content and self.content.inst is not None \
                    and self.content.inst.folder == self.game_inst.folder:
                self.content.refresh()
        if self.isHidden() or self.isMinimized():
            self.bring_back()
        self.update_tray()
        self.refresh_chip()

    def browse_mods(self, inst):
        dlg = ModBrowser(self, inst)
        dlg.exec()
        if dlg.added:
            self.content.refresh()

    def recommended_mods(self, inst):
        from .browse import RecommendedDialog
        dlg = RecommendedDialog(self, inst)
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
            self.go("home")

        def failed(msg):
            tell(self, "Couldn't install the modpack", msg, "error")
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
        task.failed.connect(lambda m: (tell(self, "Update failed", m, "error"),
                                       self.update_btn.setEnabled(True), self.say("Ready")))
        self.game_task = task
        task.start()

    def update_installed(self, _):
        tell(self, "Updated", f"Peregrine {self.pending_update['version']} is installed. It will restart now.",
             "success", ok="Restart")
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
            from .sheet import tell as _tell
            _tell(None, "Something went wrong", f"{value}\n\nPeregrine kept running. Details were saved to {log}",
                  "error")
        except Exception:
            pass
    sys.excepthook = hook


def appimage_integration():
    """Linux AppImage: put Peregrine in the app menu and on the desktop (once), pointing at
    wherever the AppImage file is now, so it's always a click away."""
    appimage = os.environ.get("APPIMAGE")
    if not appimage:
        return
    from pathlib import Path
    data = Path(os.environ.get("XDG_DATA_HOME") or Path.home() / ".local/share")
    apps, icons = data / "applications", data / "icons/hicolor/512x512/apps"
    try:
        apps.mkdir(parents=True, exist_ok=True)
        icons.mkdir(parents=True, exist_ok=True)
        icon = Path(getattr(sys, "_MEIPASS", ".")) / "assets" / "peregrine-512.png"
        if icon.is_file():
            import shutil
            shutil.copyfile(icon, icons / "peregrine.png")
        entry = ("[Desktop Entry]\nType=Application\nName=Peregrine Launcher\nGenericName=Minecraft Launcher\n"
                 "Comment=Launch and speed up Minecraft: Java Edition\n"
                 f'Exec="{appimage}" %U\nIcon=peregrine\nStartupWMClass=peregrine\nStartupNotify=true\n'
                 "Categories=Game;\nKeywords=minecraft;launcher;mods;fabric;\nTerminal=false\n")
        (apps / "peregrine.desktop").write_text(entry)
        cfg = config.load()
        if not cfg.get("desktop_shortcut_made"):
            import subprocess
            try:
                desk = subprocess.run(["xdg-user-dir", "DESKTOP"], capture_output=True, text=True).stdout.strip()
            except OSError:
                desk = ""
            desk = Path(desk) if desk else Path.home() / "Desktop"
            if desk.is_dir() and desk != Path.home():
                shortcut = desk / "peregrine.desktop"
                shortcut.write_text(entry)
                shortcut.chmod(0o755)
                try:
                    subprocess.run(["gio", "set", str(shortcut), "metadata::trusted", "true"], capture_output=True)
                except OSError:
                    pass
            config.save(dict(cfg, desktop_shortcut_made=True))
    except OSError as e:
        print(f"[peregrine] couldn't add the app menu entry: {e}")


def main():
    if "--uninstall" in sys.argv:
        # What Settings → Apps → Uninstall runs (the Windows app only).
        from pathlib import Path
        from .setup_ui import run_uninstaller
        if not (getattr(sys, "frozen", False) and sys.platform == "win32"):
            print("--uninstall is for the installed Windows app.")
            sys.exit(2)
        sys.exit(run_uninstaller(Path(sys.executable).parent, "--quiet" in sys.argv))
    paths.ensure()
    if sys.platform == "win32":
        # Its own taskbar button and icon, not Python's.
        try:
            import ctypes
            ctypes.windll.shell32.SetCurrentProcessExplicitAppUserModelID("Peregrine.Launcher")
        except Exception:
            pass
    appimage_integration()
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
    try:
        instances.name_folders()  # folders named like their instances (they were codes before 1.5)
    except Exception as e:
        print(f"[peregrine] couldn't rename instance folders: {e}")
    win = MainWindow()
    win.make_tray()
    if "--tray" in sys.argv and win.tray is not None:
        pass  # started with the computer: wait in the tray
    else:
        win.show()
    if "--smoke" in sys.argv:
        # Packaged-build check: start, draw a few frames, and quit cleanly.
        from PySide6.QtCore import QTimer
        QTimer.singleShot(4000, win.quit_app)
    code = app.exec()
    if workers._running:
        # A game or download is still going. Leave without tearing down its
        # thread (Qt would abort); the game runs on by itself.
        sys.stdout.flush()
        os._exit(code)
    sys.exit(code)
