"""Modrinth browsing: the Mods, Resource packs and Modpacks pages, and the "Add mods" window."""

from PySide6.QtCore import Qt, Signal
from PySide6.QtGui import QPixmap
from PySide6.QtWidgets import (
    QComboBox, QFrame, QHBoxLayout, QLabel, QLineEdit, QPushButton, QVBoxLayout, QWidget,
)

from .. import instances, modrinth
from . import workers
from .sheet import Sheet
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


# The organiser: Modrinth's own categories, grouped the way players look for things.
CATEGORIES = {
    "mod": [("All mods", None), ("Performance (FPS)", "optimization"), ("Tweaks and utilities", "utility"),
            ("Game mechanics", "game-mechanics"), ("Social and chat", "social"), ("Equipment and PvP", "equipment"),
            ("Storage", "storage"), ("Decoration", "decoration"), ("Adventure", "adventure"),
            ("Technology", "technology"), ("Magic", "magic"), ("World generation", "worldgen"),
            ("Mobs", "mobs"), ("Libraries", "library")],
    "resourcepack": [("All packs", None), ("PvP", "combat"), ("Simple and clean", "simplistic"),
                     ("Vanilla-like", "vanilla-like"), ("Tweaks", "tweaks"), ("Utility", "utility"),
                     ("Realistic", "realistic"), ("Themed", "themed"), ("Decoration", "decoration"),
                     ("16x", "16x"), ("32x", "32x"), ("64x", "64x"), ("128x and up", "128x")],
    "shader": [("All shaders", None), ("Fast", "potato"), ("Light", "low"), ("Medium", "medium"),
               ("Heavy", "high"), ("Realistic", "realistic"), ("Cartoon", "cartoon"), ("Fantasy", "fantasy")],
    "modpack": [("All modpacks", None), ("Performance", "optimization"), ("Adventure", "adventure"),
                ("Challenging", "challenging"), ("Lightweight", "lightweight"), ("Multiplayer", "multiplayer"),
                ("Quests", "quests"), ("Technology", "technology"), ("Magic", "magic")],
}
SORTS = [("Most downloaded", "downloads"), ("Most relevant", "relevance"), ("Most followed", "follows"),
         ("Newest", "newest"), ("Recently updated", "updated")]


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
        self.category = QComboBox()
        for label, key in CATEGORIES.get(project_type, [("All", None)]):
            self.category.addItem(label, key)
        self.category.setToolTip("Show one kind only")
        self.category.currentIndexChanged.connect(lambda _: self.new_search())
        self.sort = QComboBox()
        for label, key in SORTS:
            self.sort.addItem(label, key)
        self.sort.currentIndexChanged.connect(lambda _: self.new_search())
        bar.addWidget(self.box, 1)
        bar.addWidget(self.category)
        bar.addWidget(self.sort)
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
        sort = self.sort.currentData()
        if self.query and sort == "downloads" and self.sort.currentIndex() == 0:
            sort = "relevance"  # a typed search reads best by relevance unless picked otherwise
        workers.run(modrinth.search, self.query, self.project_type, self.game_version, "fabric",
                    self.offset, 20, self.category.currentData(), sort, done=self.got, failed=self.fail)

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
        v = QVBoxLayout(self)
        v.setContentsMargins(0, 0, 0, 0)
        v.setSpacing(18)
        v.addWidget(QLabel("Fabric modpacks from Modrinth. Each installs as its own instance.", objectName="muted"))
        self.search = SearchList("modpack", "Install", placeholder="Search modpacks")
        self.search.action.connect(self.install.emit)
        v.addWidget(self.search, 1)

    def showEvent(self, e):
        self.search.start()
        super().showEvent(e)


class BrowsePage(QWidget):
    """A sidebar page for finding mods or resource packs and adding them to an instance."""

    def __init__(self, project_type: str, title: str, subtitle: str):
        super().__init__(objectName="page")
        self.project_type = project_type
        v = QVBoxLayout(self)
        v.setContentsMargins(0, 0, 0, 0)
        v.setSpacing(14)
        v.addWidget(QLabel(subtitle, objectName="muted", wordWrap=True))
        row = QHBoxLayout()
        row.addWidget(QLabel("Add to", objectName="muted"))
        self.target = QComboBox()
        self.target.setMinimumWidth(260)
        self.target.currentIndexChanged.connect(self.target_changed)
        row.addWidget(self.target)
        self.note = QLabel("", objectName="faint")
        row.addWidget(self.note, 1)
        v.addLayout(row)
        self.holder = QVBoxLayout()
        v.addLayout(self.holder, 1)
        self.search = None
        self.version = None

    def refresh_targets(self):
        keep = self.target.currentData()
        self.target.blockSignals(True)
        self.target.clear()
        for inst in instances.all_instances():
            if self.project_type in ("mod", "shader") and inst.data["loader"] != "fabric":
                continue
            self.target.addItem(f"{inst.name}   ·   {inst.subtitle()}", str(inst.folder))
        if keep:
            i = self.target.findData(keep)
            if i >= 0:
                self.target.setCurrentIndex(i)
        self.target.blockSignals(False)
        self.target_changed()

    def inst(self):
        folder = self.target.currentData()
        if not folder:
            return None
        from pathlib import Path
        try:
            return instances.Instance(Path(folder))
        except (OSError, ValueError):
            return None

    def target_changed(self, *_):
        inst = self.inst()
        version = inst.data["mc_version"] if inst else None
        if self.target.count() == 0:
            self.note.setText("Make a Fabric instance first." if self.project_type in ("mod", "shader") else
                              "Make an instance first.")
        else:
            self.note.setText(f"Showing what works on Minecraft {version}" if version else "")
        if self.search is not None and version == self.version:
            return
        self.version = version
        if self.search is not None:
            self.search.setParent(None)
            self.search.deleteLater()
        kind = {"mod": "mods", "shader": "shaders"}.get(self.project_type, "resource packs")
        self.search = SearchList(self.project_type, "Add", version, f"Search {kind}")
        self.search.action.connect(self.add)
        self.holder.addWidget(self.search, 1)
        if self.isVisible():
            self.search.start()

    def showEvent(self, e):
        self.refresh_targets()
        if self.search is not None:
            self.search.start()
        super().showEvent(e)

    def add(self, hit, btn):
        inst = self.inst()
        if inst is None:
            return
        btn.setEnabled(False)
        btn.setText("Adding…")
        mc = inst.data["mc_version"]

        def work():
            if self.project_type == "mod":
                added = modrinth.install([hit["project_id"]], mc, "fabric", inst.game_dir / "mods")
                inst.remember_mods(added)
                return added
            if self.project_type == "shader":
                from .. import recommended
                return recommended.install_shader(inst, hit["project_id"])
            name = modrinth.install_pack(hit["project_id"], mc, inst.game_dir / "resourcepacks")
            inst.enable_resource_pack(name)
            return name

        def ok(_):
            try:
                btn.setText("Added")
                btn.setToolTip(f"Added to {inst.name}" + ("" if self.project_type == "mod" else " and switched on")
                               + (" (with Iris to run it)" if self.project_type == "shader" else ""))
            except RuntimeError:
                pass

        def bad(msg):
            try:
                btn.setText("Retry")
                btn.setEnabled(True)
                btn.setToolTip(msg)
            except RuntimeError:
                pass
        workers.run(work, done=ok, failed=bad)


class ModBrowser(Sheet):
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
        title = QLabel(f"Add mods to {inst.name}", objectName="sheetTitle")
        title.setContentsMargins(0, 0, 40, 0)
        v.addWidget(title)
        v.addWidget(QLabel(f"Showing Fabric mods for Minecraft {inst.data['mc_version']}. "
                           "Required libraries are added automatically.", objectName="muted"))
        self.search = SearchList("mod", "Add", inst.data["mc_version"], "Search mods")
        self.search.action.connect(self.add)
        v.addWidget(self.search, 1)
        from .modern import PillButton
        done = PillButton("Done", "check", "primary")
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
        def work():
            added = modrinth.install([hit["project_id"]], self.inst.data["mc_version"], "fabric",
                                     self.inst.game_dir / "mods")
            self.inst.remember_mods(added)
            return added
        workers.run(work, done=ok, failed=bad)


class MarketPage(QWidget):
    """Mods & packs: find mods, resource packs and modpacks in one place."""

    def __init__(self, mods, packs, modpacks, shaders=None):
        super().__init__(objectName="page")
        from PySide6.QtWidgets import QStackedWidget
        from .pages import page_header
        from .widgets import Segmented
        v = QVBoxLayout(self)
        v.setContentsMargins(56, 30, 56, 20)
        v.setSpacing(18)
        head = page_header("Mods & packs", "", chip="Add to an instance")
        self.tabs = Segmented([("mods", "Mods"), ("packs", "Resource packs"), ("shaders", "Shaders"),
                               ("modpacks", "Modpacks")], "mods")
        head.addWidget(self.tabs, 0, Qt.AlignBottom)
        v.addLayout(head)
        self.stack = QStackedWidget()
        self.pages = {"mods": mods, "packs": packs, "modpacks": modpacks}
        if shaders is not None:
            self.pages["shaders"] = shaders
        for p in self.pages.values():
            self.stack.addWidget(p)
        v.addWidget(self.stack, 1)
        self.tabs.changed.connect(self.show_tab)

    def show_tab(self, key):
        self.tabs.set(key)
        self.stack.setCurrentWidget(self.pages[key])


class RecommendedDialog(Sheet):
    """Recommended mods for one instance: pick and add in one go."""

    def __init__(self, parent, inst):
        super().__init__(parent)
        from .. import recommended
        from .modern import PillButton, big_title
        from .pages import scroll
        from .widgets import Toggle
        self.inst = inst
        self.added = False
        self.setWindowTitle(f"Recommended mods for {inst.name}")
        self.resize(640, 640)
        v = QVBoxLayout(self)
        v.setContentsMargins(28, 24, 28, 22)
        v.setSpacing(12)
        v.addWidget(big_title("Recommended"))
        sub = QLabel("Popular, well-made mods that go with Peregrine Client. Each is the version made "
                     f"for Minecraft {inst.data['mc_version']}.", objectName="muted", wordWrap=True)
        v.addWidget(sub)
        body = QWidget()
        rows = QVBoxLayout(body)
        rows.setSpacing(8)
        rows.setContentsMargins(0, 0, 4, 0)
        self.picks = {}
        for slug, title, what, marker in recommended.MODS:
            row = QFrame(objectName="glassCard")
            h = QHBoxLayout(row)
            h.setContentsMargins(16, 10, 14, 10)
            col = QVBoxLayout()
            col.setSpacing(2)
            col.addWidget(QLabel(title, objectName="pillTitle"))
            col.addWidget(QLabel(what, objectName="pillSub", wordWrap=True))
            h.addLayout(col, 1)
            have = recommended.installed(inst, marker)
            if have:
                h.addWidget(QLabel("Added", objectName="badge"))
            else:
                t = Toggle(False)
                self.picks[slug] = t
                h.addWidget(t)
            rows.addWidget(row)
        rows.addStretch()
        v.addWidget(scroll(body), 1)
        self.status = QLabel("", objectName="faint")
        bottom = QHBoxLayout()
        bottom.addWidget(self.status, 1)
        self.go = PillButton("Add selected", "s_plus", "primary")
        self.go.clicked.connect(self.add)
        self.go.setEnabled(bool(self.picks))
        bottom.addWidget(self.go)
        v.addLayout(bottom)

    def add(self):
        from .. import recommended
        chosen = [slug for slug, t in self.picks.items() if t.isChecked()]
        if not chosen:
            self.status.setText("Switch on the ones you want first.")
            return
        self.go.setEnabled(False)
        self.status.setText("Adding…")
        task = workers.Task(recommended.install, self.inst, chosen, with_progress=True)
        task.progress.connect(lambda d, t, m: self.status.setText(m))

        def done(missing):
            self.added = True
            self.status.setText("Added. " + (f"Not made for this version yet: {', '.join(missing)}." if missing else ""))
            self.go.setText("DONE")
            self.go.setEnabled(True)
            self.go.clicked.disconnect()
            self.go.clicked.connect(self.accept)

        def failed(msg):
            self.status.setText(msg)
            self.go.setEnabled(True)
        task.done.connect(done)
        task.failed.connect(failed)
        self._task = task
        task.start()
