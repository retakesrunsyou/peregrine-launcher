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

        perf_row = _toggle_row("FPS boost: Sodium, Lithium, FerriteCore and 4 more performance mods",
                               True)
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
        self.perf_row.setEnabled(self.loader.value() == "fabric")

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
        self.memory = QSpinBox(minimum=0, maximum=65536, singleStep=512, suffix=" MB")
        self.memory.setSpecialValueText("Use launcher setting")
        self.memory.setValue(inst.data.get("memory_mb") or 0)

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
        optimize = QPushButton("Optimize video settings")
        optimize.setToolTip("Turns off V-Sync and costly extras like entity shadows, "
                            "and lowers simulation distance. Takes effect next launch.")
        optimize.clicked.connect(self.optimize)
        form.addRow("Performance", optimize)
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
        d["memory_mb"] = self.memory.value()
        self.inst.save()
