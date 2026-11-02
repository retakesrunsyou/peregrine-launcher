"""Clips: your saved game clips and screenshots.

Clips are made in the game (Clips feature: an always-on replay buffer, the clip key saves the
last moments). Here you see them all with thumbnails, watch them, trim them, rename and delete
them; screenshots from every instance are here too. ffmpeg (which the game records with) is set
up from this page."""

import os
import time
from pathlib import Path

from PySide6.QtCore import QRectF, QSize, Qt, QTimer, QUrl, Signal
from PySide6.QtGui import QColor, QDesktopServices, QGuiApplication, QImage, QPainter, QPainterPath, QPen, QPixmap
from PySide6.QtWidgets import (QDialog, QFrame, QGridLayout, QHBoxLayout, QInputDialog, QLabel, QProgressBar,
                               QPushButton, QSlider, QVBoxLayout, QWidget)

from .. import ffmpeg_tool, instances, paths
from . import workers
from .modern import Chip, big_title, section_label
from .pages import clear, scroll
from .theme import Theme, icon_pixmap
from .widgets import Segmented

CLIPS = paths.DATA / "clips"
THUMBS = CLIPS / ".thumbs"
VIDEO = (".mp4", ".mkv", ".mov", ".webm")


def clip_files():
    if not CLIPS.is_dir():
        return []
    out = [p for p in CLIPS.iterdir() if p.suffix.lower() in VIDEO and p.is_file()]
    return sorted(out, key=lambda p: p.stat().st_mtime, reverse=True)


def screenshot_files():
    out = []
    try:
        insts = instances.all_instances()
    except Exception:
        insts = []
    for inst in insts:
        d = inst.game_dir / "screenshots"
        if d.is_dir():
            out += [(p, inst.name) for p in d.iterdir() if p.suffix.lower() in (".png", ".jpg") and p.is_file()]
    return sorted(out, key=lambda e: e[0].stat().st_mtime, reverse=True)


def thumb_for(video: Path) -> Path:
    return THUMBS / (video.stem + ".jpg")


def make_thumb(video: Path):
    """A small picture from a second into the clip (cached)."""
    t = thumb_for(video)
    if t.is_file() and t.stat().st_mtime >= video.stat().st_mtime:
        return t
    THUMBS.mkdir(parents=True, exist_ok=True)
    ffmpeg_tool.run(["-ss", "1", "-i", str(video), "-frames:v", "1", "-vf", "scale=480:-2", "-q:v", "4", str(t)], timeout=30)
    return t


def size_text(n: float) -> str:
    for unit in ("B", "KB", "MB", "GB"):
        if n < 1024 or unit == "GB":
            return f"{n:.0f} {unit}" if unit in ("B", "KB") else f"{n:.1f} {unit}"
        n /= 1024
    return ""


def length_text(s: float) -> str:
    s = int(round(s))
    return f"{s // 3600}:{s % 3600 // 60:02d}:{s % 60:02d}" if s >= 3600 else f"{s // 60}:{s % 60:02d}"


class MediaCard(QFrame):
    clicked = Signal()

    def __init__(self, title, sub, w=232, h=176):
        super().__init__()
        self.setFixedSize(w, h)
        self.setCursor(Qt.PointingHandCursor)
        self.title, self.sub = title, sub
        self.pix = None
        self.badge = ""
        self.hover = False

    def set_picture(self, path):
        img = QImage(str(path))
        if not img.isNull():
            self.pix = QPixmap.fromImage(img)
            self.update()

    def enterEvent(self, e):
        self.hover = True
        self.update()

    def leaveEvent(self, e):
        self.hover = False
        self.update()

    def mouseReleaseEvent(self, e):
        if e.button() == Qt.LeftButton:
            self.clicked.emit()

    def paintEvent(self, _):
        p = QPainter(self)
        p.setRenderHint(QPainter.Antialiasing)
        p.setRenderHint(QPainter.SmoothPixmapTransform)
        r = QRectF(self.rect()).adjusted(1, 1, -1, -1)
        bg = QColor(Theme.p["surface"])
        p.setPen(QPen(QColor(Theme.accent) if self.hover else QColor(0, 0, 0, 0), 2))
        p.setBrush(bg)
        p.drawRoundedRect(r, 12, 12)
        pic = QRectF(r.x() + 8, r.y() + 8, r.width() - 16, r.height() - 58)
        path = QPainterPath()
        path.addRoundedRect(pic, 8, 8)
        p.save()
        p.setClipPath(path)
        p.fillRect(pic, QColor(0, 0, 0, 90))
        if self.pix:
            k = max(pic.width() / self.pix.width(), pic.height() / self.pix.height())
            pw, ph = self.pix.width() * k, self.pix.height() * k
            p.drawPixmap(QRectF(pic.center().x() - pw / 2, pic.center().y() - ph / 2, pw, ph), self.pix, QRectF(self.pix.rect()))
        p.restore()
        if self.badge:
            p.setPen(Qt.NoPen)
            p.setBrush(QColor(0, 0, 0, 170))
            fm = p.fontMetrics()
            bw = fm.horizontalAdvance(self.badge) + 12
            p.drawRoundedRect(QRectF(pic.right() - bw - 6, pic.bottom() - 24, bw, 18), 6, 6)
            p.setPen(QColor("white"))
            p.drawText(QRectF(pic.right() - bw - 6, pic.bottom() - 24, bw, 18), Qt.AlignCenter, self.badge)
        p.setPen(QColor(Theme.p["text"]))
        f = p.font()
        f.setBold(True)
        p.setFont(f)
        p.drawText(QRectF(r.x() + 12, r.bottom() - 46, r.width() - 24, 20), Qt.AlignLeft | Qt.AlignVCenter,
                   p.fontMetrics().elidedText(self.title, Qt.ElideRight, int(r.width() - 24)))
        f.setBold(False)
        p.setFont(f)
        p.setPen(QColor(Theme.p["faint"]))
        p.drawText(QRectF(r.x() + 12, r.bottom() - 26, r.width() - 24, 18), Qt.AlignLeft | Qt.AlignVCenter, self.sub)


class ClipViewer(QDialog):
    """Watch a clip, trim it, rename or delete it."""
    changed = Signal()

    def __init__(self, path: Path, parent=None):
        super().__init__(parent)
        self.path = path
        self.setWindowTitle(path.stem)
        self.resize(960, 640)
        v = QVBoxLayout(self)
        self.length = 0.0
        self.player = None
        try:
            from PySide6.QtMultimedia import QAudioOutput, QMediaPlayer
            from PySide6.QtMultimediaWidgets import QVideoWidget
            self.video = QVideoWidget()
            self.player = QMediaPlayer(self)
            self.audio = QAudioOutput(self)
            self.player.setAudioOutput(self.audio)
            self.player.setVideoOutput(self.video)
            self.player.setSource(QUrl.fromLocalFile(str(path)))
            self.player.durationChanged.connect(self._duration)
            self.player.positionChanged.connect(self._position)
            v.addWidget(self.video, 1)
        except Exception:
            pic = QLabel(alignment=Qt.AlignCenter)
            t = thumb_for(path)
            if t.is_file():
                pic.setPixmap(QPixmap(str(t)).scaled(880, 495, Qt.KeepAspectRatio, Qt.SmoothTransformation))
            v.addWidget(pic, 1)
            v.addWidget(QLabel("This copy of Peregrine can't play videos itself: use Open in your player.", objectName="faint"))
        # playback row
        row = QHBoxLayout()
        self.play_btn = QPushButton("Play")
        self.play_btn.clicked.connect(self.toggle)
        self.seek = QSlider(Qt.Horizontal)
        self.seek.setRange(0, 1000)
        self.seek.sliderMoved.connect(self._seek)
        self.time = QLabel("0:00", objectName="faint")
        row.addWidget(self.play_btn)
        row.addWidget(self.seek, 1)
        row.addWidget(self.time)
        v.addLayout(row)
        # trim row
        trim = QHBoxLayout()
        self.start, self.end = 0.0, 0.0
        self.trim_label = QLabel("Trim: whole clip", objectName="faint")
        set_start = QPushButton("Start here")
        set_end = QPushButton("End here")
        save_trim = QPushButton("Save trimmed copy", objectName="accent")
        set_start.clicked.connect(lambda: self._mark("start"))
        set_end.clicked.connect(lambda: self._mark("end"))
        save_trim.clicked.connect(self.save_trimmed)
        trim.addWidget(self.trim_label, 1)
        trim.addWidget(set_start)
        trim.addWidget(set_end)
        trim.addWidget(save_trim)
        v.addLayout(trim)
        # file row
        files = QHBoxLayout()
        for text, fn in (("Open in your player", self.open_external), ("Show in folder", self.show_folder),
                         ("Rename", self.rename), ("Delete", self.delete)):
            b = QPushButton(text)
            b.clicked.connect(fn)
            files.addWidget(b)
        files.addStretch(1)
        self.status = QLabel("", objectName="faint")
        files.addWidget(self.status)
        v.addLayout(files)
        if self.player is None:
            self.length = ffmpeg_tool.duration(path)
            self.end = self.length
            self.play_btn.setEnabled(False)
            self.seek.setEnabled(False)

    def _duration(self, ms):
        self.length = ms / 1000.0
        if self.end <= 0:
            self.end = self.length
        self._trim_text()

    def _position(self, ms):
        if self.length > 0 and not self.seek.isSliderDown():
            self.seek.setValue(int(1000 * ms / 1000.0 / self.length))
        self.time.setText(f"{length_text(ms / 1000)} / {length_text(self.length)}")

    def _seek(self, v):
        if self.player and self.length > 0:
            self.player.setPosition(int(v / 1000 * self.length * 1000))

    def toggle(self):
        if not self.player:
            return
        from PySide6.QtMultimedia import QMediaPlayer
        if self.player.playbackState() == QMediaPlayer.PlaybackState.PlayingState:
            self.player.pause()
            self.play_btn.setText("Play")
        else:
            self.player.play()
            self.play_btn.setText("Pause")

    def _now(self):
        return self.player.position() / 1000.0 if self.player else 0.0

    def _mark(self, which):
        t = self._now()
        if which == "start":
            self.start = min(t, self.end - 0.5) if self.end > 0 else t
        else:
            self.end = max(t, self.start + 0.5)
        self._trim_text()

    def _trim_text(self):
        if self.start <= 0.05 and (self.length <= 0 or self.end >= self.length - 0.05):
            self.trim_label.setText("Trim: whole clip (play to a spot, then Start here / End here)")
        else:
            self.trim_label.setText(f"Trim: {length_text(self.start)} to {length_text(self.end)} "
                                    f"({length_text(self.end - self.start)})")

    def save_trimmed(self):
        if self.end <= self.start:
            self.status.setText("Set where it starts and ends first")
            return
        out = self.path.with_name(f"{self.path.stem} (trimmed).mp4")
        n = 2
        while out.exists():
            out = self.path.with_name(f"{self.path.stem} (trimmed {n}).mp4")
            n += 1
        self.status.setText("Saving…")
        args = ["-ss", f"{self.start:.3f}", "-to", f"{self.end:.3f}", "-i", str(self.path), "-c", "copy",
                "-avoid_negative_ts", "make_zero", "-movflags", "+faststart", str(out)]
        workers.run(lambda: ffmpeg_tool.run(args), done=lambda _: (self.status.setText(f"Saved {out.name}"), self.changed.emit()),
                    failed=lambda m: self.status.setText(m))

    def open_external(self):
        QDesktopServices.openUrl(QUrl.fromLocalFile(str(self.path)))

    def show_folder(self):
        QDesktopServices.openUrl(QUrl.fromLocalFile(str(self.path.parent)))

    def rename(self):
        name, ok = QInputDialog.getText(self, "Rename clip", "Name", text=self.path.stem)
        name = (name or "").strip().replace("/", "-").replace("\\", "-")
        if not ok or not name or name == self.path.stem:
            return
        new = self.path.with_name(name + self.path.suffix)
        if new.exists():
            self.status.setText("A clip with that name exists")
            return
        if self.player:
            self.player.stop()
            self.player.setSource(QUrl())
        self.path.rename(new)
        old_thumb = thumb_for(self.path)
        if old_thumb.is_file():
            old_thumb.rename(thumb_for(new))
        self.path = new
        self.setWindowTitle(new.stem)
        if self.player:
            self.player.setSource(QUrl.fromLocalFile(str(new)))
        self.changed.emit()

    def delete(self):
        from .sheet import ask
        if not ask(self, "Delete this clip?", self.path.name, "Delete"):
            return
        if self.player:
            self.player.stop()
            self.player.setSource(QUrl())
        self.path.unlink(missing_ok=True)
        thumb_for(self.path).unlink(missing_ok=True)
        self.changed.emit()
        self.accept()

    def closeEvent(self, e):
        if self.player:
            self.player.stop()
        super().closeEvent(e)


class ShotViewer(QDialog):
    changed = Signal()

    def __init__(self, path: Path, parent=None):
        super().__init__(parent)
        self.path = path
        self.setWindowTitle(path.name)
        self.resize(960, 640)
        v = QVBoxLayout(self)
        pic = QLabel(alignment=Qt.AlignCenter)
        self.img = QImage(str(path))
        pic.setPixmap(QPixmap.fromImage(self.img).scaled(920, 540, Qt.KeepAspectRatio, Qt.SmoothTransformation))
        v.addWidget(pic, 1)
        row = QHBoxLayout()
        for text, fn in (("Copy picture", self.copy), ("Show in folder", self.show_folder), ("Delete", self.delete)):
            b = QPushButton(text)
            b.clicked.connect(fn)
            row.addWidget(b)
        row.addStretch(1)
        self.status = QLabel("", objectName="faint")
        row.addWidget(self.status)
        v.addLayout(row)

    def copy(self):
        QGuiApplication.clipboard().setImage(self.img)
        self.status.setText("Copied")

    def show_folder(self):
        QDesktopServices.openUrl(QUrl.fromLocalFile(str(self.path.parent)))

    def delete(self):
        from .sheet import ask
        if ask(self, "Delete this screenshot?", self.path.name, "Delete"):
            self.path.unlink(missing_ok=True)
            self.changed.emit()
            self.accept()


class ClipsPage(QWidget):

    def __init__(self, say=None):
        super().__init__(objectName="page")
        self.say = say or (lambda t: None)
        self.tab = "clips"
        v = QVBoxLayout(self)
        v.setContentsMargins(56, 30, 56, 20)
        v.setSpacing(14)
        head = QHBoxLayout()
        col = QVBoxLayout()
        col.setSpacing(10)
        col.addWidget(Chip("Your moments"))
        col.addWidget(big_title("Clips"))
        head.addLayout(col)
        head.addStretch()
        self.tabs = Segmented([("clips", "Clips"), ("shots", "Screenshots")], "clips")
        self.tabs.changed.connect(self.show_tab)
        head.addWidget(self.tabs, 0, Qt.AlignBottom)
        v.addLayout(head)
        # ffmpeg / how-to bar
        bar = QHBoxLayout()
        self.info = QLabel("", objectName="muted")
        self.info.setWordWrap(True)
        self.setup_btn = QPushButton("Set up ffmpeg", objectName="accent")
        self.setup_btn.clicked.connect(self.setup_ffmpeg)
        self.progress = QProgressBar(textVisible=False)
        self.progress.setFixedSize(160, 4)
        self.progress.hide()
        folder = QPushButton("Open clips folder")
        folder.clicked.connect(lambda: (CLIPS.mkdir(parents=True, exist_ok=True),
                                        QDesktopServices.openUrl(QUrl.fromLocalFile(str(CLIPS)))))
        bar.addWidget(self.info, 1)
        bar.addWidget(self.progress)
        bar.addWidget(self.setup_btn)
        bar.addWidget(folder)
        v.addLayout(bar)
        self.body = QVBoxLayout()
        holder = QWidget()
        holder.setLayout(self.body)
        v.addWidget(scroll(holder), 1)
        self.cards = []

    def showEvent(self, e):
        super().showEvent(e)
        self.render()

    def show_tab(self, key):
        self.tab = key
        self.tabs.set(key)
        self.render()

    def _info(self):
        ff = ffmpeg_tool.find()
        self.setup_btn.setVisible(ff is None)
        size = sum(p.stat().st_size for p in clip_files())
        if ff is None:
            self.info.setText("Clips record with ffmpeg, which isn't on this computer yet. Set it up once (about 90 MB), "
                              "then turn on Clips in the game (Right Shift → Utility → Clips). F8 saves the last 30 seconds.")
        else:
            self.info.setText(f"Turn on Clips in the game (Right Shift → Utility → Clips): F8 saves the last 30 seconds, and "
                              f"its settings save up to an hour, the whole day or the whole session. "
                              f"{len(clip_files())} clips, {size_text(size)}.")

    def setup_ffmpeg(self):
        self.setup_btn.setEnabled(False)
        self.progress.show()
        self.progress.setRange(0, 0)

        def progress(done, total, _msg):
            if total:
                self.progress.setRange(0, total)
                self.progress.setValue(done)

        t = workers.Task(ffmpeg_tool.download, with_progress=True)
        t.progress.connect(progress)
        t.done.connect(lambda _: (self.progress.hide(), self.setup_btn.setEnabled(True), self.say("ffmpeg is ready for Clips"),
                                  self.render()))
        t.failed.connect(lambda m: (self.progress.hide(), self.setup_btn.setEnabled(True), self.info.setText(m)))
        t.start()

    def render(self):
        self._info()
        clear(self.body)
        self.cards = []
        if self.tab == "clips":
            files = clip_files()
            if not files:
                self.body.addWidget(QLabel("No clips yet. Press F8 in game (with Clips on) to save the last 30 seconds.",
                                           objectName="faint"))
                self.body.addStretch()
                return
            self._grid([(f, self._clip_card(f)) for f in files])
        else:
            shots = screenshot_files()
            if not shots:
                self.body.addWidget(QLabel("No screenshots yet. F2 in game takes one.", objectName="faint"))
                self.body.addStretch()
                return
            cards = []
            for path, inst in shots[:200]:
                c = MediaCard(path.stem, inst)
                c.set_picture(path)
                c.clicked.connect(lambda p=path: self._open_shot(p))
                cards.append((path, c))
            self._grid(cards)
        self.body.addStretch()

    def _clip_card(self, f: Path):
        st = f.stat()
        c = MediaCard(f.stem, time.strftime("%d %b %Y, %H:%M", time.localtime(st.st_mtime)) + "  ·  " + size_text(st.st_size))
        t = thumb_for(f)
        if t.is_file():
            c.set_picture(t)
        elif ffmpeg_tool.find():
            workers.run(make_thumb, f, done=lambda path, c=c: c.set_picture(path), failed=lambda m: None)
        if ffmpeg_tool.find("ffprobe"):
            workers.run(ffmpeg_tool.duration, f, done=lambda d, c=c: (setattr(c, "badge", length_text(d) if d else ""), c.update()),
                        failed=lambda m: None)
        c.clicked.connect(lambda p=f: self._open_clip(p))
        return c

    def _grid(self, cards):
        holder = QWidget()
        g = QGridLayout(holder)
        g.setContentsMargins(0, 0, 0, 0)
        g.setSpacing(14)
        cols = max(1, (self.width() - 112) // 246)
        for i, (_, c) in enumerate(cards):
            g.addWidget(c, i // cols, i % cols)
            self.cards.append(c)
        g.setColumnStretch(cols, 1)
        self.body.addWidget(holder)

    def _open_clip(self, path):
        d = ClipViewer(path, self)
        d.changed.connect(self.render)
        d.exec()

    def _open_shot(self, path):
        d = ShotViewer(path, self)
        d.changed.connect(self.render)
        d.exec()

    def resizeEvent(self, e):
        super().resizeEvent(e)
        if self.isVisible():
            QTimer.singleShot(0, self.render)
