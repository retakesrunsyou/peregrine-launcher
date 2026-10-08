"""Modrinth browsing: the Modpacks page and the "Add mods" window."""

from PySide6.QtCore import Qt, Signal
from PySide6.QtGui import QPixmap
from PySide6.QtWidgets import (
    QDialog, QFrame, QHBoxLayout, QLabel, QLineEdit, QPushButton, QVBoxLayout, QWidget,
)

from .. import modrinth
from . import workers
from .theme import Theme, icon, icon_pixmap


def _count(n: int) -> str:
    for size, suffix in ((1_000_000, "M"), (1_000, "K")):
        if n >= size:
            return f"{n / size:.1f}".rstrip("0").rstrip(".") + suffix
    return str(n)


def set_icon_async(label: QLabel, url: str, size: int) -> None:
    """Show a placeholder now, then the project's icon once it's downloaded."""
    label.setPixmap(icon_pixmap("folder", Theme.p["faint"], size // 2))

    def show(path):
        if not path:
            return
        try:
            pm = QPixmap(str(path))
            if not pm.isNull():
                label.setPixmap(pm.scaled(size, size, Qt.KeepAspectRatio, Qt.SmoothTransformation))
        except RuntimeError:
            pass  # the row was closed before the icon arrived
    workers.run(modrinth.icon_path, url, done=show)


class ResultRow(QFrame):
    action = Signal(object, object)  # (hit, button)

    def __init__(self, hit: dict, action_text: str):
        super().__init__(objectName="row")
        h = QHBoxLayout(self)
        h.setContentsMargins(12, 12, 12, 12)
        h.setSpacing(14)
        pic = QLabel()
        pic.setFixedSize(48, 48)
        pic.setAlignment(Qt.AlignCenter)
        pic.setStyleSheet(f"background: {Theme.p['raised']}; border-radius: 6px;")
        set_icon_async(pic, hit.get("icon_url"), 48)
        h.addWidget(pic, 0, Qt.AlignTop)

        col = QVBoxLayout()
        col.setSpacing(3)
        top = QHBoxLayout()
        top.addWidget(QLabel(hit["title"], objectName="h2"))
        meta = QLabel(f"by {hit.get('author', 'unknown')}   ↓ {_count(hit.get('downloads', 0))}",
                      objectName="faint")
        top.addWidget(meta)
        top.addStretch()
        col.addLayout(top)
        text = " ".join((hit.get("description") or "").split())
        if len(text) > 140:  # two lines at most; long blurbs were cut off mid-line
            text = text[:137].rsplit(" ", 1)[0] + "…"
        desc = QLabel(text, objectName="muted")
        desc.setWordWrap(True)
        desc.setAlignment(Qt.AlignLeft | Qt.AlignTop)
        desc.setToolTip(hit.get("description", ""))
        col.addWidget(desc)
        h.addLayout(col, 1)

        btn = QPushButton(action_text, objectName="accent")
        btn.setCursor(Qt.PointingHandCursor)
        btn.clicked.connect(lambda: self.action.emit(hit, btn))
        h.addWidget(btn, 0, Qt.AlignVCenter)


class SearchList(QWidget):
    """Search box + results + "Show more", for one kind of Modrinth project."""
    action = Signal(object, object)

    def __init__(self, project_type: str, action_text: str, game_version: str = None,
                 placeholder: str = "Search"):
        super().__init__()
        self.project_type, self.action_text, self.game_version = project_type, action_text, game_version
        self.offset, self.query, self.loading = 0, "", False

        v = QVBoxLayout(self)
        v.setContentsMargins(0, 0, 0, 0)
        v.setSpacing(12)
        bar = QHBoxLayout()
        self.box = QLineEdit(placeholderText=placeholder)
        self.box.returnPressed.connect(self.new_search)
        self.box.addAction(icon("search", None, 16), QLineEdit.LeadingPosition)
        go = QPushButton("Search")
        go.clicked.connect(self.new_search)
        bar.addWidget(self.box, 1)
        bar.addWidget(go)
        v.addLayout(bar)

        from .pages import scroll
        body = QWidget()
        self.list = QVBoxLayout(body)
        self.list.setContentsMargins(0, 0, 4, 0)
        self.list.setSpacing(8)
        self.status = QLabel("", objectName="muted")
        self.status.setAlignment(Qt.AlignCenter)
        self.more = QPushButton("Show more")
        self.more.clicked.connect(self.load)
        self.more.hide()
        self.list.addWidget(self.status)
        self.list.addWidget(self.more, 0, Qt.AlignHCenter)
        self.list.addStretch()
        v.addWidget(scroll(body), 1)
        self.started = False

    def start(self):
        """Load the first page the first time this is shown."""
        if not self.started:
            self.started = True
            self.new_search()

    def new_search(self):
        self.query = self.box.text().strip()
        self.offset = 0
        while self.list.count() > 3:  # keep status, more, stretch
            item = self.list.takeAt(0)
            if item.widget():
                item.widget().deleteLater()
        self.load()

    def load(self):
        if self.loading:
            return
        self.loading = True
        self.more.hide()
        self.status.setText("Loading…")
        self.status.show()
        workers.run(modrinth.search, self.query, self.project_type, self.game_version, "fabric",
                    self.offset, done=self.got, failed=self.fail)

    def got(self, hits):
        self.loading = False
        self.status.hide()
        for hit in hits:
            row = ResultRow(hit, self.action_text)
            row.action.connect(self.action.emit)
            self.list.insertWidget(self.list.count() - 3, row)
        self.offset += len(hits)
        if not hits and self.offset == 0:
            self.status.setText("Nothing found. Try a different search.")
            self.status.show()
        self.more.setVisible(len(hits) == 20)

    def fail(self, msg):
        self.loading = False
        self.status.setText("Couldn't reach Modrinth. Check your connection and search again.")
        self.status.show()


class ModpacksPage(QWidget):
    install = Signal(object, object)

    def __init__(self):
        super().__init__(objectName="page")
        from .pages import page_header
        v = QVBoxLayout(self)
        v.setContentsMargins(32, 28, 32, 20)
        v.setSpacing(18)
        v.addLayout(page_header("Modpacks", "Fabric modpacks from Modrinth. Each installs as its own instance."))
        self.search = SearchList("modpack", "Install", placeholder="Search modpacks")
        self.search.action.connect(self.install.emit)
        v.addWidget(self.search, 1)

    def showEvent(self, e):
        self.search.start()
        super().showEvent(e)


class ModBrowser(QDialog):
    """Search and add Fabric mods to one instance."""

    def __init__(self, parent, inst):
        super().__init__(parent)
        self.inst = inst
        self.added = False
        self.setWindowTitle(f"Add mods to {inst.name}")
        self.resize(720, 620)
        v = QVBoxLayout(self)
        v.setContentsMargins(22, 20, 22, 18)
        v.setSpacing(12)
        v.addWidget(QLabel(f"Add mods to {inst.name}", objectName="h2"))
        v.addWidget(QLabel(f"Showing Fabric mods for Minecraft {inst.data['mc_version']}. "
                           "Required libraries are added automatically.", objectName="muted"))
        self.search = SearchList("mod", "Add", inst.data["mc_version"], "Search mods")
        self.search.action.connect(self.add)
        v.addWidget(self.search, 1)
        done = QPushButton("Done", objectName="accent")
        done.clicked.connect(self.accept)
        v.addWidget(done, 0, Qt.AlignRight)
        self.search.start()

    def add(self, hit, btn):
        btn.setEnabled(False)
        btn.setText("Adding…")

        def ok(_):
            self.added = True
            try:
                btn.setText("Added")
            except RuntimeError:
                pass

        def bad(msg):
            try:
                btn.setText("Retry")
                btn.setEnabled(True)
                btn.setToolTip(msg)
            except RuntimeError:
                pass
        workers.run(modrinth.install, [hit["project_id"]], self.inst.data["mc_version"], "fabric",
                    self.inst.game_dir / "mods", done=ok, failed=bad)
