"""Profiles: share your setup under your Minecraft name, and look up a friend's to grab
their mods, resource packs, shaders and in-game mod settings."""

from PySide6.QtCore import Qt
from PySide6.QtWidgets import (QCheckBox, QComboBox, QFrame, QHBoxLayout, QLabel, QLineEdit,
                               QVBoxLayout, QWidget)

from .. import auth, instances, profiles
from . import workers
from .modern import Chip, PillButton, big_title, section_label
from .pages import clear, empty_state, scroll


class ProfilesPage(QWidget):

    def __init__(self, say=None):
        super().__init__(objectName="page")
        self.say = say or (lambda text: None)
        self.found = None
        self.picks = {}
        v = QVBoxLayout(self)
        v.setContentsMargins(56, 30, 56, 20)
        v.setSpacing(14)
        col = QVBoxLayout()
        col.setSpacing(12)
        col.addWidget(Chip("Profiles"))
        col.addWidget(big_title("Profiles"))
        sub = QLabel("Share your setup under your Minecraft name, or grab a friend's: their mods, "
                     "resource packs, shaders and in-game settings.", objectName="muted")
        sub.setWordWrap(True)
        col.addWidget(sub)
        v.addLayout(col)
        body = QWidget()
        self.body = QVBoxLayout(body)
        self.body.setContentsMargins(0, 0, 0, 0)
        self.body.setSpacing(14)
        v.addWidget(scroll(body), 1)
        self.build()

    # ------------------------------------------------------------------ layout

    def build(self):
        clear(self.body)
        if not profiles.available():
            self.body.addWidget(empty_state("users", "Profiles aren't switched on yet",
                                            "They need the free profile service set up once (see profiles/README.md)."))
            self.body.addStretch()
            return
        # look someone up
        find = QFrame(objectName="glassCard")
        f = QHBoxLayout(find)
        f.setContentsMargins(16, 12, 12, 12)
        f.setSpacing(12)
        self.query = QLineEdit(placeholderText="Look up a player by their Minecraft name")
        self.query.returnPressed.connect(self.lookup)
        f.addWidget(self.query, 1)
        go = PillButton("Look up", "search", "primary")
        go.clicked.connect(self.lookup)
        f.addWidget(go)
        self.body.addWidget(find)
        self.result = QVBoxLayout()
        self.result.setSpacing(10)
        self.body.addLayout(self.result)

        # your own profile
        mine = QFrame(objectName="card")
        m = QVBoxLayout(mine)
        m.setContentsMargins(22, 18, 22, 18)
        m.setSpacing(10)
        m.addWidget(QLabel("Your profile", objectName="h2"))
        acct = auth.active_account()
        who = acct["name"] if acct and acct.get("type") == "msa" else None
        note = QLabel((f"Share {who}'s setup so friends can find it by that name. Only things from Modrinth are "
                       "listed (your own files stay private), and your waypoints, nickname and servers are never shared. "
                       "Nothing is shared until you press Share.") if who else
                      "Sign in with a Microsoft account to share your profile.", objectName="muted")
        note.setWordWrap(True)
        m.addWidget(note)
        if who:
            row = QHBoxLayout()
            row.addWidget(QLabel("Share what's in"))
            self.share_from = QComboBox()
            for inst in instances.all_instances():
                if inst.data.get("loader") == "fabric":
                    self.share_from.addItem(f"{inst.name}  ({inst.data.get('mc_version')})", str(inst.folder))
            row.addWidget(self.share_from, 1)
            m.addLayout(row)
            self.share_settings = QCheckBox("Include my in-game mod settings (HUD layout, modules)")
            self.share_settings.setChecked(True)
            m.addWidget(self.share_settings)
            btns = QHBoxLayout()
            share = PillButton("Share / update", "check", "primary")
            share.clicked.connect(self.share)
            stop = PillButton("Stop sharing", "x", "ghost")
            stop.clicked.connect(self.unshare)
            btns.addWidget(share)
            btns.addWidget(stop)
            btns.addStretch()
            m.addLayout(btns)
        self.body.addWidget(mine)
        self.body.addStretch()

    # ------------------------------------------------------------------ actions

    def _busy(self, text):
        self.say(text)

    def lookup(self):
        name = self.query.text().strip()
        if not name:
            return
        self._busy(f"Looking up {name}…")
        clear(self.result)
        workers.run(profiles.lookup, name, done=self.show, failed=self.not_found)

    def not_found(self, msg):
        self.say(msg)
        clear(self.result)
        self.result.addWidget(empty_state("users", "No profile", msg))

    def show(self, prof):
        self.found = prof
        self.picks = {}
        clear(self.result)
        card = QFrame(objectName="card")
        c = QVBoxLayout(card)
        c.setContentsMargins(22, 18, 22, 18)
        c.setSpacing(8)
        c.addWidget(QLabel(f"{prof.get('name')}'s setup", objectName="h2"))
        c.addWidget(QLabel(f"Minecraft {prof.get('minecraft') or '?'} · updated {str(prof.get('updated', ''))[:10]}",
                           objectName="faint"))
        for kind, title in (("mods", "Mods"), ("resourcepacks", "Resource packs"), ("shaders", "Shaders")):
            items = prof.get(kind) or []
            if not items:
                continue
            c.addWidget(section_label(f"{title}  ({len(items)})"))
            for it in items:
                box = QCheckBox(it.get("title") or it.get("file") or it.get("project"))
                box.setChecked(True)
                box.setToolTip(it.get("file", ""))
                self.picks[(kind, it["project"])] = box
                c.addWidget(box)
        self.take_settings = QCheckBox("Use their in-game mod settings too (yours are kept as a backup)")
        self.take_settings.setEnabled(bool(prof.get("client_settings")))
        c.addWidget(self.take_settings)
        row = QHBoxLayout()
        row.addWidget(QLabel("Add to"))
        self.target = QComboBox()
        for inst in instances.all_instances():
            if inst.data.get("loader") == "fabric":
                self.target.addItem(f"{inst.name}  ({inst.data.get('mc_version')})", str(inst.folder))
        row.addWidget(self.target, 1)
        get = PillButton("Get selected", "plus", "primary")
        get.clicked.connect(self.take)
        row.addWidget(get)
        c.addLayout(row)
        if self.target.count() == 0:
            c.addWidget(QLabel("Make a Fabric instance first, then come back.", objectName="muted"))
        self.result.addWidget(card)
        self.say(f"{prof.get('name')}'s profile")

    def _inst(self, combo):
        from pathlib import Path
        folder = combo.currentData()
        return instances.Instance(Path(folder)) if folder else None

    def take(self):
        inst = self._inst(self.target)
        if inst is None or not self.found:
            return
        chosen = {"mods": [], "resourcepacks": [], "shaders": []}
        for (kind, project), box in self.picks.items():
            if box.isChecked():
                chosen[kind].append(project)
        settings = self.take_settings.isChecked()
        self._busy(f"Adding to {inst.name}…")
        workers.run(lambda: profiles.take(self.found, inst, chosen["mods"], chosen["resourcepacks"],
                                          chosen["shaders"], settings),
                    done=lambda s: self.say(
                        f"Added {len(s['added'])} to {inst.name}" +
                        (f", {len(s['unavailable'])} not available for {inst.data.get('mc_version')}" if s["unavailable"] else "") +
                        (" and their in-game settings" if s["settings"] else "")),
                    failed=lambda m: self.say(f"Couldn't add them: {m}"))

    def share(self):
        acct = auth.active_account()
        inst = self._inst(self.share_from)
        if not acct or inst is None:
            self.say("Pick an instance to share")
            return
        self._busy("Sharing your profile…")
        workers.run(lambda: profiles.share(acct, inst, self.share_settings.isChecked()),
                    done=lambda p: self.say(
                        f"Shared: {len(p['mods'])} mods, {len(p['resourcepacks'])} packs, {len(p['shaders'])} shaders" +
                        (f" ({p['skipped']} files not from Modrinth stay private)" if p.get("skipped") else "")),
                    failed=lambda m: self.say(f"Couldn't share: {m}"))

    def unshare(self):
        acct = auth.active_account()
        if not acct:
            return
        self._busy("Removing your profile…")
        workers.run(lambda: profiles.unshare(acct), done=lambda _: self.say("Your profile isn't shared any more"),
                    failed=lambda m: self.say(f"Couldn't stop sharing: {m}"))
