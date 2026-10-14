"""Friends: your Xbox friends (the list Minecraft uses), who's online and playing,
and adding people by gamertag."""

from PySide6.QtCore import Qt, QTimer, Signal
from PySide6.QtGui import QPixmap
from PySide6.QtWidgets import QFrame, QHBoxLayout, QLabel, QLineEdit, QVBoxLayout, QWidget

from .. import auth, friends, modrinth
from . import workers
from .modern import ONLINE, Chip, PillButton, big_title, rounded_pixmap, section_label
from .pages import clear, empty_state, icon_button, scroll
from .theme import Theme, avatar_pixmap

REFRESH_MS = 120_000


def set_picture(label: QLabel, url: str, name: str, size: int):
    label.setPixmap(avatar_pixmap(None, name, size))
    if not url:
        return
    url = url + ("&" if "?" in url else "?") + "w=96&h=96"

    def show(path):
        if not path:
            return
        try:
            pm = QPixmap(str(path))
            if not pm.isNull():
                label.setPixmap(rounded_pixmap(pm, size, 0.5))
        except RuntimeError:
            pass
    workers.run(modrinth.icon_path, url, done=show)


class FriendRow(QFrame):
    remove = Signal(object)
    add = Signal(object, object)

    def __init__(self, person: dict, mode: str = "friend"):
        super().__init__(objectName="glassCard")
        self.person = person
        h = QHBoxLayout(self)
        h.setContentsMargins(14, 10, 12, 10)
        h.setSpacing(14)
        pic = QLabel()
        pic.setFixedSize(44, 44)
        set_picture(pic, person["picture"], person["gamertag"], 44)
        h.addWidget(pic)
        col = QVBoxLayout()
        col.setSpacing(2)
        col.addWidget(QLabel(person["gamertag"], objectName="pillTitle"))
        status = QHBoxLayout()
        status.setSpacing(7)
        dot = QLabel()
        dot.setFixedSize(8, 8)
        dot.setStyleSheet(f"background: {ONLINE if person['online'] else Theme.p['faint']}; border-radius: 4px;")
        status.addWidget(dot)
        status.addWidget(QLabel(person["status"] or ("Online" if person["online"] else "Offline"),
                                objectName="pillSub"))
        status.addStretch()
        col.addLayout(status)
        h.addLayout(col, 1)
        if mode == "friend":
            x = icon_button("trash", "Remove friend", Theme.p["danger"])
            x.clicked.connect(lambda: self.remove.emit(person))
            h.addWidget(x)
        else:
            already = person.get("following")
            b = PillButton("Added" if already else "Add", "s_plus", "ghost" if already else "primary")
            b.setEnabled(not already)
            b.clicked.connect(lambda: self.add.emit(person, b))
            h.addWidget(b)


class FriendsPage(QWidget):
    counts = Signal(int, int)       # online, total (for the top bar)
    sign_in = Signal()

    def __init__(self):
        super().__init__(objectName="page")
        self.people = []
        self.loading = False
        self.error = ""
        v = QVBoxLayout(self)
        v.setContentsMargins(56, 30, 56, 20)
        v.setSpacing(14)
        head = QHBoxLayout()
        col = QVBoxLayout()
        col.setSpacing(12)
        self.chip = Chip("Friends")
        col.addWidget(self.chip)
        col.addWidget(big_title("Friends"))
        self.sub = QLabel("", objectName="muted")
        col.addWidget(self.sub)
        head.addLayout(col)
        head.addStretch()
        self.reload = PillButton("Refresh", "refresh", "ghost")
        self.reload.clicked.connect(self.load)
        head.addWidget(self.reload, 0, Qt.AlignBottom)
        v.addLayout(head)

        # add by gamertag
        self.add_box = QFrame(objectName="glassCard")
        a = QHBoxLayout(self.add_box)
        a.setContentsMargins(16, 12, 12, 12)
        a.setSpacing(12)
        self.query = QLineEdit(placeholderText="Add a friend by their gamertag")
        self.query.returnPressed.connect(self.search)
        a.addWidget(self.query, 1)
        find = PillButton("Find", "search", "primary")
        find.clicked.connect(self.search)
        a.addWidget(find)
        v.addWidget(self.add_box)
        self.results = QVBoxLayout()
        self.results.setSpacing(8)
        v.addLayout(self.results)

        body = QWidget()
        self.list = QVBoxLayout(body)
        self.list.setContentsMargins(0, 4, 4, 0)
        self.list.setSpacing(8)
        v.addWidget(scroll(body), 1)

        self.timer = QTimer(self)
        self.timer.setInterval(REFRESH_MS)
        self.timer.timeout.connect(self.load)
        self.timer.start()
        QTimer.singleShot(1500, self.load)

    def account(self):
        a = auth.active_account()
        return a if a and a.get("type") == "msa" else None

    # ------------------------------------------------------------ data

    def load(self):
        account = self.account()
        if account is None:
            self.people, self.error = [], ""
            self.render()
            self.counts.emit(-1, 0)
            return
        if self.loading:
            return
        self.loading = True
        self.sub.setText("Loading your friends…")
        workers.run(friends.friends, account, done=self._loaded, failed=self._failed)

    def _loaded(self, people):
        self.loading = False
        self.people, self.error = people, ""
        self.render()
        self.counts.emit(sum(1 for p in people if p["online"]), len(people))

    def _failed(self, msg):
        self.loading = False
        self.error = msg
        self.render()
        self.counts.emit(-1, 0)

    def search(self):
        account = self.account()
        q = self.query.text().strip()
        if account is None or not q:
            return
        clear(self.results)
        self.results.addWidget(QLabel("Looking…", objectName="muted"))
        workers.run(friends.search, account, q, done=self._found,
                    failed=lambda m: self._show_results(None, m))

    def _found(self, people):
        self._show_results(people, "")

    def _show_results(self, people, error):
        clear(self.results)
        if error:
            self.results.addWidget(QLabel(error, objectName="muted"))
            return
        if not people:
            self.results.addWidget(QLabel("Nobody with that gamertag.", objectName="muted"))
            return
        self.results.addWidget(section_label("Search results"))
        for p in people[:5]:
            row = FriendRow(p, "result")
            row.add.connect(self.add)
            self.results.addWidget(row)

    def add(self, person, button):
        account = self.account()
        if account is None:
            return
        button.setEnabled(False)
        button.setText("ADDING…")

        def ok(_):
            try:
                button.setText("ADDED")
            except RuntimeError:
                pass
            self.load()

        def bad(msg):
            try:
                button.setText("ADD")
                button.setEnabled(True)
            except RuntimeError:
                pass
            self.sub.setText(msg)
        workers.run(friends.add, account, person["xuid"], done=ok, failed=bad)

    def remove(self, person):
        account = self.account()
        if account is None:
            return
        from .sheet import ask
        if not ask(self, f"Remove {person['gamertag']}?", "They'll be taken off your friends list.", "Remove",
                   kind="danger"):
            return
        workers.run(friends.remove, account, person["xuid"], done=lambda _: self.load(),
                    failed=lambda m: self.sub.setText(m))

    # ------------------------------------------------------------ view

    def render(self):
        clear(self.list)
        signed_in = self.account() is not None
        self.add_box.setVisible(signed_in)
        self.reload.setVisible(signed_in)
        if not signed_in:
            self.sub.setText("")
            self.chip.set_dot(None)
            self.list.addWidget(empty_state(
                "users", "Sign in to see your friends",
                "Friends come from your Microsoft account: the same friends list Minecraft uses "
                "for Realms and Bedrock. Add your Microsoft account to see who's online."))
            b = PillButton("Sign in", "s_user", "primary")
            b.clicked.connect(self.sign_in.emit)
            self.list.addWidget(b, 0, Qt.AlignHCenter)
            self.list.addStretch()
            return
        if self.error:
            self.sub.setText("")
            self.list.addWidget(empty_state("users", "Couldn't load your friends", self.error))
            self.list.addStretch()
            return
        online = [p for p in self.people if p["online"]]
        offline = [p for p in self.people if not p["online"]]
        self.chip.set_dot(ONLINE if online else None)
        self.sub.setText(f"{len(online)} online  ·  {len(self.people)} friends" if self.people else "")
        if not self.people:
            self.list.addWidget(empty_state("user_plus", "No friends yet",
                                            "Search for someone's gamertag above to add them."))
        for label, group in ((f"Online — {len(online)}", online), (f"Offline — {len(offline)}", offline)):
            if not group:
                continue
            self.list.addWidget(section_label(label))
            for p in group:
                row = FriendRow(p)
                row.remove.connect(self.remove)
                self.list.addWidget(row)
        self.list.addStretch()
