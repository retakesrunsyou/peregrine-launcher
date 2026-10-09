"""Pop-up windows: Microsoft sign-in, new instance, instance settings."""

from PySide6.QtCore import Qt, QUrl
from PySide6.QtGui import QDesktopServices, QFont, QGuiApplication
from PySide6.QtWidgets import (
    QComboBox, QDialog, QFormLayout, QHBoxLayout, QLabel, QLineEdit, QMessageBox,
    QPushButton, QSpinBox, QVBoxLayout, QWidget,
)

from .. import auth, fabric, game, instances
from . import workers
from .theme import Theme
from .widgets import Segmented, Toggle


def _toggle_row(text: str, checked: bool):
    """A label with a switch on the right, used in forms."""
    w = QWidget()
    h = QHBoxLayout(w)
    h.setContentsMargins(0, 0, 0, 0)
    label = QLabel(text)
    label.setWordWrap(True)
    t = Toggle(checked)
    h.addWidget(label, 1)
    h.addWidget(t)
    w.toggle, w.label = t, label
    return w


def _buttons(dialog, ok_text: str):
    row = QHBoxLayout()
    row.addStretch()
    cancel = QPushButton("Cancel")
    cancel.clicked.connect(dialog.reject)
    ok = QPushButton(ok_text, objectName="accent")
    ok.setDefault(True)
    ok.clicked.connect(dialog.accept)
    row.addWidget(cancel)
    row.addWidget(ok)
    return row, ok


def _title(text: str, sub: str = "") -> QWidget:
    w = QWidget()
    v = QVBoxLayout(w)
    v.setContentsMargins(0, 0, 0, 8)
    v.addWidget(QLabel(text, objectName="h2"))
    if sub:
        s = QLabel(sub, objectName="muted")
        s.setWordWrap(True)
        v.addWidget(s)
    return w


class LoginDialog(QDialog):
    def __init__(self, parent, client_id: str):
        super().__init__(parent)
        self.setWindowTitle("Sign in with Microsoft")
        self.client_id, self.flow, self.account, self.cancelled = client_id, None, None, False

        v = QVBoxLayout(self)
        v.setContentsMargins(24, 22, 24, 20)
        v.setSpacing(12)
        v.addWidget(_title("Sign in with Microsoft",
                           "Use the Microsoft account that owns Minecraft: Java Edition. "
                           "Your password stays with Microsoft."))
        self.code = QLabel("…")
        self.code.setFont(QFont("monospace", 28, QFont.Bold))
        self.code.setAlignment(Qt.AlignCenter)
        self.code.setTextInteractionFlags(Qt.TextSelectableByMouse)
        self.code.setStyleSheet(f"color: {Theme.accent}; padding: 12px;")
        self.hint = QLabel("Getting a code from Microsoft…", objectName="muted")
        self.hint.setAlignment(Qt.AlignCenter)
        self.hint.setWordWrap(True)
        v.addWidget(self.code)
        v.addWidget(self.hint)

        row = QHBoxLayout()
        row.addStretch()
        cancel = QPushButton("Cancel")
        cancel.clicked.connect(self.reject)
        self.open_btn = QPushButton("Copy code and open browser", objectName="accent")
        self.open_btn.setEnabled(False)
        self.open_btn.clicked.connect(self.open_browser)
        row.addWidget(cancel)
        row.addWidget(self.open_btn)
        v.addLayout(row)
        self.resize(460, 0)

        workers.run(auth.start_device_login, client_id, done=self.got_code, failed=self.fail)

    def got_code(self, flow):
        self.flow = flow
        self.code.setText(flow["user_code"])
        self.hint.setText(f"Enter this code at {flow['verification_uri']}")
        self.open_btn.setEnabled(True)
        workers.run(self._wait, done=self.success, failed=self.fail)

    def _wait(self):
        tokens = auth.wait_for_device_login(self.client_id, self.flow, lambda: self.cancelled)
        return auth.complete_login(tokens)

    def open_browser(self):
        QGuiApplication.clipboard().setText(self.flow["user_code"])
        QDesktopServices.openUrl(QUrl(self.flow["verification_uri"]))

    def success(self, account):
        self.account = account
        self.accept()

    def fail(self, msg):
        if not self.cancelled:
            QMessageBox.warning(self, "Sign-in failed", msg)
            self.reject()

    def reject(self):
        self.cancelled = True
        super().reject()


class NewInstanceDialog(QDialog):
    def __init__(self, parent, show_snapshots: bool):
        super().__init__(parent)
        self.setWindowTitle("New instance")
        v = QVBoxLayout(self)
        v.setContentsMargins(24, 22, 24, 20)
        v.addWidget(_title("New instance", "Each instance has its own worlds, mods and settings."))

        form = QFormLayout()
        form.setSpacing(12)
        self.name = QLineEdit(placeholderText="Survival")
        self.description = QLineEdit(placeholderText="A world to call your own")
        self.version = QComboBox()
        self.version.addItem("Loading versions…")
        self.version.setEnabled(False)
        snap_row = _toggle_row("Include snapshots", show_snapshots)
        self.snapshots = snap_row.toggle
        self.snapshots.toggled.connect(self.load_versions)

        self.loader = Segmented([("fabric", "Fabric"), ("vanilla", "Vanilla")], "fabric")
        self.wanted_loader = "fabric"  # what the player picked; kept while versions load
        self.loader.changed.connect(self.pick_loader)
        loaders = QHBoxLayout()
        loaders.addWidget(self.loader)
        loaders.addStretch()

        perf_row = _toggle_row("Performance mode: Sodium and 9 more speed-up mods picked for this "
                               "version, plus fast game settings", True)
        self.perf, self.perf_row = perf_row.toggle, perf_row
        self.version.currentTextChanged.connect(self.update_options)

        form.addRow("Name", self.name)
        form.addRow("Description", self.description)
        form.addRow("Version", self.version)
        form.addRow("", snap_row)
        form.addRow("Loader", loaders)
        form.addRow("", perf_row)
        v.addLayout(form)
        v.addSpacing(8)
        row, self.ok = _buttons(self, "Create instance")
        self.ok.setEnabled(False)
        v.addLayout(row)
        self.resize(480, 0)

        self.fabric_versions = frozenset()
        self.versions_loaded = False
        self.load_versions()
        workers.run(fabric.supported_game_versions, done=self.got_fabric)

    def load_versions(self):
        workers.run(game.list_versions, self.snapshots.isChecked(), done=self.got_versions,
                    failed=lambda m: QMessageBox.warning(self, "Couldn't load versions",
                                                         f"Check your internet connection.\n\n{m}"))

    def got_versions(self, versions):
        self.versions_loaded = False  # ignore the empty/partial states while refilling
        self.version.clear()
        self.version.addItems(versions)
        self.versions_loaded = True
        self.version.setEnabled(True)
        self.ok.setEnabled(True)
        self.update_options()

    def pick_loader(self, key):
        self.wanted_loader = key
        self.update_options()

    def got_fabric(self, versions):
        self.fabric_versions = versions
        self.update_options()

    def update_options(self):
        if not self.versions_loaded:
            return  # still showing "Loading versions…"
        v = self.version.currentText()
        fabric_ok = not self.fabric_versions or v in self.fabric_versions
        self.loader.set_enabled("fabric", fabric_ok)
        # Fabric when the player wants it and this version has it; Vanilla otherwise.
        self.loader.set("fabric" if fabric_ok and self.wanted_loader == "fabric" else "vanilla")
        fabric_on = self.loader.value() == "fabric"
        self.perf_row.setEnabled(fabric_on)
        if not fabric_on and self.perf.isChecked():
            self.perf.setChecked(False)  # Vanilla means vanilla: no mods
            self._perf_auto_off = True
        elif fabric_on and getattr(self, "_perf_auto_off", False):
            self.perf.setChecked(True)  # back to Fabric: performance mode back on
            self._perf_auto_off = False

    def create(self):
        loader = self.loader.value()
        return instances.create(self.name.text().strip() or self.version.currentText(),
                                self.version.currentText(), loader,
                                loader == "fabric" and self.perf.isChecked(),
                                self.description.text().strip())


class InstanceSettingsDialog(QDialog):
    def __init__(self, parent, inst):
        super().__init__(parent)
        self.inst = inst
        self.setWindowTitle(f"{inst.name} settings")
        v = QVBoxLayout(self)
        v.setContentsMargins(24, 22, 24, 20)
        v.addWidget(_title(inst.name, inst.subtitle()))

        form = QFormLayout()
        form.setSpacing(12)
        self.name = QLineEdit(inst.name)
        self.description = QLineEdit(inst.data.get("description", ""))
        self.memory = QComboBox()
        self.memory.addItem("Same as Settings", 0)
        for gb in (2, 3, 4, 6, 8, 10, 12, 16):
            self.memory.addItem(f"{gb} GB", gb * 1024)
        current = inst.data.get("memory_mb") or 0
        found = self.memory.findData(current)
        if found < 0:  # an older custom value: keep it selectable
            self.memory.addItem(f"{current / 1024:g} GB", current)
            found = self.memory.count() - 1
        self.memory.setCurrentIndex(found)

        colors = QHBoxLayout()
        self.color = inst.color
        self.swatches = []
        for c in instances.INSTANCE_COLORS:
            b = QPushButton(objectName="swatch", checkable=True)
            b.setStyleSheet(f"QPushButton {{ background: {c}; border: 2px solid transparent; }}"
                            f"QPushButton:checked {{ border-color: {Theme.p['text']}; }}")
            b.setChecked(c == self.color)
            b.clicked.connect(lambda _=False, c=c: self.pick(c))
            self.swatches.append((c, b))
            colors.addWidget(b)
        colors.addStretch()

        form.addRow("Name", self.name)
        form.addRow("Description", self.description)
        form.addRow("Color", colors)
        form.addRow("Memory", self.memory)
        perf_row = _toggle_row("Performance mode: speed-up mods matched to this version, "
                               "kept up to date, and fast settings", inst.performance)
        self.perf = perf_row.toggle
        form.addRow("Performance", perf_row)
        optimize = QPushButton("Apply fast settings now")
        optimize.setToolTip("Fast graphics, no clouds or shadows, smooth lighting off, "
                            "V-Sync off, render distance 10. Takes effect next launch.")
        optimize.clicked.connect(self.optimize)
        form.addRow("", optimize)
        v.addLayout(form)
        v.addSpacing(8)
        row, _ = _buttons(self, "Save")
        v.addLayout(row)
        self.resize(460, 0)

    def optimize(self):
        try:
            changed = self.inst.optimize_video()
        except OSError as e:
            QMessageBox.warning(self, "Couldn't update settings", str(e))
            return
        QMessageBox.information(self, "Video settings optimized",
                                f"Updated {len(changed)} settings for more FPS."
                                if changed else "Video settings were already optimized.")

    def pick(self, color):
        self.color = color
        for c, b in self.swatches:
            b.setChecked(c == color)

    def save(self):
        d = self.inst.data
        d["name"] = self.name.text().strip() or d["name"]
        d["description"] = self.description.text().strip()
        d["color"] = self.color
        d["memory_mb"] = int(self.memory.currentData() or 0)
        self.inst.save()
        if self.perf.isChecked() != self.inst.performance:
            try:
                self.inst.set_performance(self.perf.isChecked())
            except Exception as e:
                QMessageBox.warning(self, "Performance mode", f"Couldn't change performance mode: {e}")
