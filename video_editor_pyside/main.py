from __future__ import annotations

import json
import os
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import List, Optional, Tuple

from PySide6.QtCore import Qt, QUrl, QRectF, Signal
from PySide6.QtGui import QAction, QBrush, QColor, QFont, QPainter, QPen
from PySide6.QtMultimedia import QAudioOutput, QMediaPlayer
from PySide6.QtMultimediaWidgets import QVideoWidget
from PySide6.QtWidgets import (
    QApplication,
    QFrame,
    QGraphicsDropShadowEffect,
    QHBoxLayout,
    QLabel,
    QListWidget,
    QListWidgetItem,
    QMainWindow,
    QMessageBox,
    QPushButton,
    QFileDialog,
    QSlider,
    QSizePolicy,
    QStatusBar,
    QVBoxLayout,
    QWidget,
)

SUPPORTED_EXTENSIONS = {".mp4", ".mkv", ".webm", ".mov", ".avi", ".m4v"}

BG = "#07111f"
TOP = "#0b1524"
PANEL = "#0d1828"
PANEL_2 = "#121f31"
PANEL_3 = "#18263a"
BORDER = "#243650"
TEXT = "#f1f5ff"
MUTED = "#a8b4c7"
FADED = "#66758d"
BLUE = "#168fff"
BLUE_2 = "#37a6ff"
GREEN = "#4ed69e"
RED = "#e25f65"
CYAN = "#38dde8"


def format_time(ms: int) -> str:
    ms = max(0, int(ms))
    total = ms // 1000
    h = total // 3600
    m = (total % 3600) // 60
    s = total % 60
    r = ms % 1000
    if h:
        return f"{h:02d}:{m:02d}:{s:02d}.{r:03d}"
    return f"{m:02d}:{s:02d}.{r:03d}"


def seconds_arg(ms: int) -> str:
    return f"{max(0, ms) / 1000.0:.3f}"


def hide_console_kwargs() -> dict:
    if os.name != "nt":
        return {}
    startupinfo = subprocess.STARTUPINFO()
    startupinfo.dwFlags |= subprocess.STARTF_USESHOWWINDOW
    return {"startupinfo": startupinfo, "creationflags": subprocess.CREATE_NO_WINDOW}


def discover_app_root() -> Path:
    if getattr(sys, "frozen", False):
        exe_dir = Path(sys.executable).resolve().parent
    else:
        exe_dir = Path(__file__).resolve().parent
    cwd = Path.cwd().resolve()
    candidates = [exe_dir, exe_dir.parent, exe_dir.parent.parent, exe_dir.parent.parent.parent, cwd, cwd.parent]
    seen = set()
    for candidate in candidates:
        if not candidate or candidate in seen:
            continue
        seen.add(candidate)
        if (candidate / "bin" / "ffmpeg.exe").exists() or (candidate / "MonitorDeNoticias.exe").exists():
            return candidate
    return exe_dir


APP_ROOT = discover_app_root()
BIN_DIR = APP_ROOT / "bin"
FFMPEG = BIN_DIR / "ffmpeg.exe"
FFPROBE = BIN_DIR / "ffprobe.exe"
EXPORTS_DIR = APP_ROOT / "VideoEditorExports"
EXPORTS_DIR.mkdir(parents=True, exist_ok=True)


@dataclass
class VideoInfo:
    duration_ms: int
    width: int
    height: int
    fps: float
    video_codec: str
    audio_codec: Optional[str]

    @property
    def has_audio(self) -> bool:
        return bool(self.audio_codec)


@dataclass
class Clip:
    path: Path
    info: VideoInfo
    start_ms: int = 0
    end_ms: int = 0

    def __post_init__(self) -> None:
        if self.end_ms <= 0:
            self.end_ms = self.info.duration_ms

    @property
    def duration_ms(self) -> int:
        return max(0, self.end_ms - self.start_ms)


def parse_fps(value: str) -> float:
    if not value or value == "0/0":
        return 0.0
    try:
        if "/" in value:
            a, b = value.split("/", 1)
            den = float(b)
            if den == 0:
                return 0.0
            return float(a) / den
        return float(value)
    except Exception:
        return 0.0


def probe_video(path: Path) -> VideoInfo:
    if not FFPROBE.exists():
        return VideoInfo(0, 0, 0, 0.0, "video", None)
    command = [
        str(FFPROBE),
        "-v", "error",
        "-print_format", "json",
        "-show_format",
        "-show_streams",
        str(path),
    ]
    result = subprocess.run(command, capture_output=True, text=True, timeout=60, **hide_console_kwargs())
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or result.stdout.strip() or "Falha ao analisar vídeo.")
    data = json.loads(result.stdout)
    duration = float(data.get("format", {}).get("duration") or 0.0)
    width = height = 0
    fps = 0.0
    video_codec = "video"
    audio_codec: Optional[str] = None
    for stream in data.get("streams", []):
        if stream.get("codec_type") == "video" and video_codec == "video":
            video_codec = (stream.get("codec_name") or "video").lower()
            width = int(stream.get("width") or 0)
            height = int(stream.get("height") or 0)
            fps = parse_fps(stream.get("avg_frame_rate") or stream.get("r_frame_rate") or "")
            try:
                duration = max(duration, float(stream.get("duration") or 0.0))
            except Exception:
                pass
        elif stream.get("codec_type") == "audio" and not audio_codec:
            audio_codec = (stream.get("codec_name") or "audio").lower()
    return VideoInfo(int(duration * 1000), width, height, fps, video_codec, audio_codec)


class TimelineWidget(QWidget):
    seekRequested = Signal(int)
    clipSelected = Signal(int)

    def __init__(self) -> None:
        super().__init__()
        self.clips: List[Clip] = []
        self.playhead_ms = 0
        self.selected_index = -1
        self.pixels_per_second = 8.0
        self.setMinimumHeight(170)
        self.setMouseTracking(True)
        self.setCursor(Qt.PointingHandCursor)

    def total_duration(self) -> int:
        return sum(clip.duration_ms for clip in self.clips)

    def set_clips(self, clips: List[Clip]) -> None:
        self.clips = clips
        self.updateGeometry()
        self.update()

    def set_playhead(self, ms: int) -> None:
        self.playhead_ms = max(0, min(int(ms), max(0, self.total_duration())))
        self.update()

    def set_selected(self, index: int) -> None:
        self.selected_index = index
        self.update()

    def sizeHint(self):  # noqa: N802 - Qt naming
        width = max(900, int((self.total_duration() / 1000.0) * self.pixels_per_second) + 220)
        return super().sizeHint().expandedTo(self.minimumSizeHint()).grownBy(self.contentsMargins()).expandedTo(self.minimumSize()).boundedTo(self.maximumSize()).expandedTo(self.minimumSize()).expandedTo(self.minimumSize()) if False else self.minimumSizeHint().expandedTo(self.minimumSize())

    def _timeline_rect(self) -> QRectF:
        return QRectF(170, 35, max(650, self.width() - 190), 100)

    def _x_for_time(self, ms: int) -> float:
        rect = self._timeline_rect()
        total = max(1, self.total_duration())
        return rect.left() + rect.width() * (ms / total)

    def _time_for_x(self, x: float) -> int:
        rect = self._timeline_rect()
        total = max(1, self.total_duration())
        fraction = (x - rect.left()) / max(1.0, rect.width())
        return int(max(0.0, min(1.0, fraction)) * total)

    def _clip_at(self, x: float, y: float) -> int:
        if not self.clips:
            return -1
        rect = self._timeline_rect()
        if not (rect.left() <= x <= rect.right() and 48 <= y <= 92):
            return -1
        total = max(1, self.total_duration())
        cursor = rect.left()
        for index, clip in enumerate(self.clips):
            width = rect.width() * (clip.duration_ms / total)
            if cursor <= x <= cursor + width:
                return index
            cursor += width
        return -1

    def paintEvent(self, event):  # noqa: N802 - Qt naming
        super().paintEvent(event)
        painter = QPainter(self)
        painter.setRenderHint(QPainter.Antialiasing)
        painter.fillRect(self.rect(), QColor(BG))

        rect = self._timeline_rect()
        painter.setPen(QPen(QColor(BORDER), 1))
        painter.setBrush(QBrush(QColor("#0a1320")))
        painter.drawRoundedRect(rect.adjusted(0, 0, 0, 0), 4, 4)

        painter.setFont(QFont("Segoe UI", 9))
        painter.setPen(QColor(MUTED))
        painter.drawText(18, 64, "▭  Vídeo 1")
        painter.drawText(18, 110, "♫  Áudio 1")

        total = max(1, self.total_duration())
        ruler_y = 27
        marks = 6 if total > 60000 else 5
        for i in range(marks + 1):
            t = int(total * i / max(1, marks))
            x = self._x_for_time(t)
            painter.setPen(QPen(QColor(BORDER), 1))
            painter.drawLine(int(x), ruler_y, int(x), 135)
            painter.setPen(QColor(MUTED))
            painter.drawText(int(x) + 4, 25, format_time(t))

        if self.clips:
            cursor = rect.left()
            for index, clip in enumerate(self.clips):
                width = max(28.0, rect.width() * (clip.duration_ms / total))
                video_rect = QRectF(cursor + 3, 50, width - 6, 36)
                audio_rect = QRectF(cursor + 3, 96, width - 6, 28)
                selected = index == self.selected_index

                painter.setPen(QPen(QColor(BLUE_2 if selected else BORDER), 2 if selected else 1))
                painter.setBrush(QBrush(QColor(BLUE if selected else "#123d6a")))
                painter.drawRoundedRect(video_rect, 4, 4)
                painter.setPen(QColor(TEXT))
                painter.drawText(video_rect.adjusted(8, 0, -8, 0), Qt.AlignVCenter | Qt.AlignLeft, f"{index + 1}. {clip.path.name}")

                painter.setPen(QPen(QColor("#0b777b"), 1))
                painter.setBrush(QBrush(QColor(0, 160, 170, 65)))
                painter.drawRoundedRect(audio_rect, 4, 4)
                painter.setPen(QColor(MUTED))
                painter.drawText(audio_rect, Qt.AlignCenter, "Áudio original" if clip.info.has_audio else "Sem áudio")
                cursor += width
        else:
            painter.setPen(QColor(FADED))
            painter.drawText(rect, Qt.AlignCenter, "Adicione vídeos para aparecerem na timeline")

        play_x = self._x_for_time(self.playhead_ms)
        painter.setPen(QPen(QColor(BLUE_2), 3))
        painter.drawLine(int(play_x), 35, int(play_x), 140)
        painter.setBrush(QBrush(QColor(BLUE_2)))
        painter.drawRoundedRect(QRectF(play_x - 4, 32, 8, 9), 3, 3)

    def mousePressEvent(self, event):  # noqa: N802 - Qt naming
        x = float(event.position().x())
        y = float(event.position().y())
        index = self._clip_at(x, y)
        if index >= 0:
            self.selected_index = index
            self.clipSelected.emit(index)
        self.seekRequested.emit(self._time_for_x(x))
        self.update()


class MainWindow(QMainWindow):
    def __init__(self) -> None:
        super().__init__()
        self.setWindowTitle("VideoMaster PRO - Editor de Vídeo")
        self.resize(1600, 920)
        self.clips: List[Clip] = []
        self.selected_index = -1
        self.sequence_mode = False
        self.updating_slider = False

        self.player = QMediaPlayer(self)
        self.audio = QAudioOutput(self)
        self.audio.setVolume(0.85)
        self.player.setAudioOutput(self.audio)

        self.video_widget = QVideoWidget(self)
        self.video_widget.setStyleSheet("background: black; border: 1px solid #243650; border-radius: 6px;")
        self.player.setVideoOutput(self.video_widget)
        self.player.positionChanged.connect(self.on_position_changed)
        self.player.mediaStatusChanged.connect(self.on_media_status_changed)
        self.player.errorOccurred.connect(self.on_player_error)

        self.build_ui()
        self.apply_theme()
        self.setStatusBar(QStatusBar())
        self.statusBar().showMessage("Pronto. Abra um ou mais vídeos.")

    def apply_theme(self) -> None:
        self.setStyleSheet(f"""
            QMainWindow {{ background: {BG}; color: {TEXT}; }}
            QWidget {{ color: {TEXT}; font-family: Segoe UI, Arial; font-size: 13px; }}
            QPushButton {{ background: {PANEL_3}; color: {TEXT}; border: 1px solid {BORDER}; border-radius: 8px; padding: 10px 14px; font-weight: 600; }}
            QPushButton:hover {{ background: #1d314d; }}
            QPushButton:disabled {{ color: {FADED}; background: #101a29; }}
            QListWidget {{ background: #0a1320; border: 1px solid {BORDER}; border-radius: 8px; padding: 6px; }}
            QListWidget::item {{ padding: 8px; border-radius: 6px; }}
            QListWidget::item:selected {{ background: {BLUE}; color: white; }}
            QSlider::groove:horizontal {{ height: 10px; background: #33435a; border-radius: 5px; }}
            QSlider::handle:horizontal {{ width: 16px; height: 16px; background: {BLUE_2}; margin: -4px 0; border-radius: 8px; }}
            QSlider::sub-page:horizontal {{ background: {BLUE}; border-radius: 5px; }}
            QStatusBar {{ background: #06101d; color: {MUTED}; border-top: 1px solid {BORDER}; }}
        """)

    def panel(self) -> QFrame:
        frame = QFrame()
        frame.setObjectName("panel")
        frame.setStyleSheet(f"QFrame#panel {{ background: {PANEL}; border: 1px solid {BORDER}; border-radius: 10px; }}")
        shadow = QGraphicsDropShadowEffect(frame)
        shadow.setBlurRadius(18)
        shadow.setColor(QColor(0, 0, 0, 80))
        shadow.setOffset(0, 3)
        frame.setGraphicsEffect(shadow)
        return frame

    def build_ui(self) -> None:
        root = QWidget()
        main = QVBoxLayout(root)
        main.setContentsMargins(8, 8, 8, 8)
        main.setSpacing(8)
        self.setCentralWidget(root)

        top = self.panel()
        top_l = QHBoxLayout(top)
        top_l.setContentsMargins(18, 10, 18, 10)
        logo = QLabel("▥")
        logo.setStyleSheet(f"color:{BLUE_2}; font-size:42px; font-weight:300;")
        title_box = QVBoxLayout()
        title = QLabel("VideoMaster PRO")
        title.setStyleSheet("font-size:26px; font-weight:800;")
        subtitle = QLabel("Editor PySide6 • QtMultimedia QMediaPlayer • QVideoWidget • QAudioOutput")
        subtitle.setStyleSheet(f"color:{MUTED}; font-size:13px;")
        title_box.addWidget(title)
        title_box.addWidget(subtitle)
        top_l.addWidget(logo)
        top_l.addLayout(title_box)
        top_l.addStretch(1)
        self.btn_open = QPushButton("▭  Abrir Vídeo")
        self.btn_open.clicked.connect(self.open_files)
        top_l.addWidget(self.btn_open)
        self.btn_save = QPushButton("▣  Salvar Projeto")
        self.btn_save.clicked.connect(lambda: self.info_box("Esta função não existe no motor atual: Salvar Projeto."))
        top_l.addWidget(self.btn_save)
        self.btn_settings = QPushButton("⚙  Configurações")
        self.btn_settings.clicked.connect(lambda: self.info_box("Esta função não existe no motor atual: Configurações."))
        top_l.addWidget(self.btn_settings)
        main.addWidget(top, 0)

        center = QHBoxLayout()
        center.setSpacing(8)
        main.addLayout(center, 1)

        rail = self.panel()
        rail.setFixedWidth(210)
        rail_l = QVBoxLayout(rail)
        rail_l.setContentsMargins(10, 10, 10, 10)
        for label, active in [
            ("✂  Editor de Vídeo", True),
            ("⇩  Extração", False),
            ("▤  Compactação", False),
            ("✄  Corte", False),
            ("▣  Unir Vídeos", False),
            ("↻  Converter", False),
        ]:
            b = QPushButton(label)
            b.setMinimumHeight(54)
            if active:
                b.setStyleSheet(f"background:{BLUE}; color:white; border:1px solid {BLUE_2}; border-radius:8px; font-weight:800;")
            else:
                b.clicked.connect(lambda _=False, name=label: self.info_box(f"Esta função não existe no motor atual: {name[3:]} dentro do Editor."))
            rail_l.addWidget(b)
        rail_l.addStretch(1)
        current = QLabel("Funcional agora\n\n• Abrir vários vídeos\n• Preview QMediaPlayer\n• Áudio QAudioOutput\n• Timeline visual\n• Exportar trecho")
        current.setStyleSheet(f"color:{MUTED}; background:#0a1320; border:1px solid {BORDER}; border-radius:8px; padding:12px;")
        rail_l.addWidget(current)
        center.addWidget(rail)

        media_panel = self.panel()
        media_panel.setFixedWidth(405)
        media_l = QVBoxLayout(media_panel)
        media_l.setContentsMargins(14, 14, 14, 14)
        header = QHBoxLayout()
        h = QLabel("Mídia do Projeto")
        h.setStyleSheet("font-size:20px; font-weight:800;")
        self.media_count = QLabel("0 clipes")
        self.media_count.setStyleSheet(f"color:{FADED};")
        header.addWidget(h)
        header.addStretch(1)
        header.addWidget(self.media_count)
        media_l.addLayout(header)
        media_buttons = QHBoxLayout()
        import_btn = QPushButton("⇩  Importar")
        import_btn.clicked.connect(self.open_files)
        add_btn = QPushButton("＋  Adicionar")
        add_btn.clicked.connect(self.open_files)
        media_buttons.addWidget(import_btn)
        media_buttons.addWidget(add_btn)
        media_l.addLayout(media_buttons)
        tabs = QHBoxLayout()
        for name in ["Todos", "Vídeos", "Imagens", "Áudios"]:
            btn = QPushButton(name)
            if name not in ("Todos", "Vídeos"):
                btn.setEnabled(False)
                btn.setToolTip("Esta função não existe no motor atual.")
            tabs.addWidget(btn)
        media_l.addLayout(tabs)
        self.media_list = QListWidget()
        self.media_list.itemSelectionChanged.connect(self.on_media_selection)
        media_l.addWidget(self.media_list, 1)
        self.info_label = QLabel("Nenhuma mídia importada.")
        self.info_label.setWordWrap(True)
        self.info_label.setStyleSheet(f"color:{MUTED}; background:#0a1320; border:1px solid {BORDER}; border-radius:8px; padding:10px;")
        media_l.addWidget(self.info_label)
        center.addWidget(media_panel)

        work = QVBoxLayout()
        work.setSpacing(8)
        center.addLayout(work, 1)

        preview_panel = self.panel()
        preview_l = QVBoxLayout(preview_panel)
        preview_l.setContentsMargins(14, 14, 14, 14)
        prev_header = QHBoxLayout()
        prev_title = QLabel("Pré-visualização")
        prev_title.setStyleSheet("font-size:22px; font-weight:800;")
        self.preview_meta = QLabel("QtMultimedia")
        self.preview_meta.setStyleSheet(f"color:{MUTED};")
        prev_header.addWidget(prev_title)
        prev_header.addWidget(self.preview_meta)
        prev_header.addStretch(1)
        self.engine_badge = QLabel("QMediaPlayer")
        self.engine_badge.setStyleSheet(f"color:{MUTED}; background:#09121f; border:1px solid {BORDER}; border-radius:6px; padding:7px 12px;")
        prev_header.addWidget(self.engine_badge)
        preview_l.addLayout(prev_header)
        self.video_widget.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Expanding)
        preview_l.addWidget(self.video_widget, 1)
        seek_row = QHBoxLayout()
        self.current_label = QLabel("00:00.000")
        self.duration_label = QLabel("00:00.000")
        self.slider = QSlider(Qt.Horizontal)
        self.slider.setRange(0, 1)
        self.slider.sliderPressed.connect(lambda: setattr(self, "updating_slider", True))
        self.slider.sliderReleased.connect(self.slider_seek_released)
        seek_row.addWidget(self.current_label)
        seek_row.addWidget(self.slider, 1)
        seek_row.addWidget(self.duration_label)
        preview_l.addLayout(seek_row)
        controls = QHBoxLayout()
        controls.addStretch(1)
        back = QPushButton("◀ 5s")
        back.clicked.connect(lambda: self.seek_global(self.global_position() - 5000))
        self.play_btn = QPushButton("▶")
        self.play_btn.setMinimumWidth(70)
        self.play_btn.clicked.connect(self.toggle_play)
        forward = QPushButton("5s ▶")
        forward.clicked.connect(lambda: self.seek_global(self.global_position() + 5000))
        self.mute_btn = QPushButton("🔊")
        self.mute_btn.clicked.connect(self.toggle_mute)
        controls.addWidget(back)
        controls.addWidget(self.play_btn)
        controls.addWidget(forward)
        controls.addWidget(self.mute_btn)
        controls.addStretch(1)
        preview_l.addLayout(controls)
        work.addWidget(preview_panel, 1)

        timeline_panel = self.panel()
        timeline_l = QVBoxLayout(timeline_panel)
        timeline_l.setContentsMargins(14, 14, 14, 14)
        tool_row = QHBoxLayout()
        tl_title = QLabel("Timeline")
        tl_title.setStyleSheet("font-size:20px; font-weight:800;")
        tool_row.addWidget(tl_title)
        tool_row.addStretch(1)
        cut_btn = QPushButton("✂  Exportar trecho")
        cut_btn.clicked.connect(self.export_selected)
        tool_row.addWidget(cut_btn)
        timeline_l.addLayout(tool_row)
        self.timeline = TimelineWidget()
        self.timeline.seekRequested.connect(self.seek_global)
        self.timeline.clipSelected.connect(self.select_clip)
        timeline_l.addWidget(self.timeline)
        work.addWidget(timeline_panel, 0)

        quick = QHBoxLayout()
        for label in ["Cortar Vídeo", "Unir Vídeos", "Extrair", "Compactar", "Converter"]:
            btn = QPushButton(label)
            if label == "Cortar Vídeo":
                btn.clicked.connect(self.export_selected)
            else:
                btn.clicked.connect(lambda _=False, name=label: self.info_box(f"Esta função não existe no motor atual: {name}."))
            quick.addWidget(btn)
        main.addLayout(quick)

    def info_box(self, text: str) -> None:
        self.statusBar().showMessage(text)

    def open_files(self) -> None:
        files, _ = QFileDialog.getOpenFileNames(
            self,
            "Abrir vídeos",
            str(Path.home()),
            "Vídeos (*.mp4 *.mkv *.webm *.mov *.avi *.m4v)",
        )
        if not files:
            return
        added = 0
        errors = []
        for name in files:
            path = Path(name)
            if path.suffix.lower() not in SUPPORTED_EXTENSIONS:
                errors.append(f"Formato não suportado: {path.name}")
                continue
            try:
                info = probe_video(path)
                if info.duration_ms <= 0:
                    raise RuntimeError("Não foi possível determinar a duração.")
                self.clips.append(Clip(path=path, info=info))
                added += 1
            except Exception as exc:
                errors.append(f"{path.name}: {exc}")
        self.refresh_media()
        if added and self.selected_index < 0:
            self.select_clip(0)
            self.seek_global(0)
        self.statusBar().showMessage(f"{added} vídeo(s) adicionado(s)." if added else "Nenhum vídeo adicionado.")
        if errors:
            QMessageBox.warning(self, "Arquivos não adicionados", "\n".join(errors[:6]))

    def refresh_media(self) -> None:
        self.media_list.clear()
        for index, clip in enumerate(self.clips):
            item = QListWidgetItem(f"{index + 1}. {clip.path.name}\n{clip.info.width}×{clip.info.height} • {format_time(clip.duration_ms)} • {'áudio' if clip.info.has_audio else 'sem áudio'}")
            item.setData(Qt.UserRole, index)
            self.media_list.addItem(item)
        self.media_count.setText(f"{len(self.clips)} clipe(s)")
        self.timeline.set_clips(self.clips)
        self.slider.setRange(0, max(1, self.total_duration()))
        self.duration_label.setText(format_time(self.total_duration()))

    def on_media_selection(self) -> None:
        items = self.media_list.selectedItems()
        if not items:
            return
        index = int(items[0].data(Qt.UserRole))
        self.select_clip(index)

    def select_clip(self, index: int) -> None:
        if not (0 <= index < len(self.clips)):
            return
        self.selected_index = index
        self.timeline.set_selected(index)
        if self.media_list.currentRow() != index:
            self.media_list.setCurrentRow(index)
        clip = self.clips[index]
        self.preview_meta.setText(f"{clip.info.width}×{clip.info.height} • {clip.info.fps:.2f} fps")
        self.info_label.setText(
            f"Arquivo: {clip.path.name}\n"
            f"Duração: {format_time(clip.duration_ms)}\n"
            f"Resolução: {clip.info.width}×{clip.info.height}\n"
            f"FPS: {clip.info.fps:.2f}\n"
            f"Codec: {clip.info.video_codec.upper()}\n"
            f"Áudio: {'Sim - ' + clip.info.audio_codec.upper() if clip.info.audio_codec else 'Não'}"
        )
        self.load_clip(index, autoplay=False)

    def total_duration(self) -> int:
        return sum(clip.duration_ms for clip in self.clips)

    def clip_at_global(self, global_ms: int) -> Tuple[int, int]:
        if not self.clips:
            return -1, 0
        cursor = 0
        for index, clip in enumerate(self.clips):
            next_cursor = cursor + clip.duration_ms
            if global_ms <= next_cursor or index == len(self.clips) - 1:
                return index, clip.start_ms + max(0, global_ms - cursor)
            cursor = next_cursor
        return len(self.clips) - 1, self.clips[-1].start_ms

    def global_start_for_clip(self, index: int) -> int:
        return sum(self.clips[i].duration_ms for i in range(max(0, index)))

    def global_position(self) -> int:
        if self.selected_index < 0:
            return 0
        clip = self.clips[self.selected_index]
        return self.global_start_for_clip(self.selected_index) + max(0, self.player.position() - clip.start_ms)

    def load_clip(self, index: int, autoplay: bool) -> None:
        if not (0 <= index < len(self.clips)):
            return
        self.selected_index = index
        clip = self.clips[index]
        self.timeline.set_selected(index)
        self.player.setSource(QUrl.fromLocalFile(str(clip.path)))
        self.player.setPosition(clip.start_ms)
        if autoplay:
            self.player.play()
            self.play_btn.setText("Ⅱ")
        else:
            self.play_btn.setText("▶")

    def seek_global(self, global_ms: int) -> None:
        if not self.clips:
            return
        global_ms = max(0, min(int(global_ms), self.total_duration()))
        index, local = self.clip_at_global(global_ms)
        if index != self.selected_index:
            self.load_clip(index, autoplay=self.player.playbackState() == QMediaPlayer.PlayingState)
        self.player.setPosition(local)
        self.timeline.set_playhead(global_ms)
        self.current_label.setText(format_time(global_ms))
        if not self.updating_slider:
            self.slider.setValue(global_ms)

    def slider_seek_released(self) -> None:
        value = self.slider.value()
        self.updating_slider = False
        self.seek_global(value)

    def toggle_play(self) -> None:
        if not self.clips:
            self.statusBar().showMessage("Abra um vídeo antes de reproduzir.")
            return
        if self.player.playbackState() == QMediaPlayer.PlayingState:
            self.player.pause()
            self.play_btn.setText("▶")
            return
        index, local = self.clip_at_global(self.global_position())
        if index < 0:
            index = 0
            local = self.clips[0].start_ms
        if index != self.selected_index:
            self.load_clip(index, autoplay=False)
        self.player.setPosition(local)
        self.player.play()
        self.play_btn.setText("Ⅱ")
        self.statusBar().showMessage("Reproduzindo com PySide6 QtMultimedia QMediaPlayer.")

    def on_position_changed(self, local_ms: int) -> None:
        if self.selected_index < 0 or self.updating_slider:
            return
        clip = self.clips[self.selected_index]
        if local_ms >= clip.end_ms:
            if self.player.playbackState() == QMediaPlayer.PlayingState:
                self.play_next_clip()
            return
        global_ms = self.global_start_for_clip(self.selected_index) + max(0, local_ms - clip.start_ms)
        self.timeline.set_playhead(global_ms)
        self.current_label.setText(format_time(global_ms))
        self.slider.setValue(global_ms)

    def on_media_status_changed(self, status) -> None:
        if status == QMediaPlayer.EndOfMedia and self.player.playbackState() == QMediaPlayer.PlayingState:
            self.play_next_clip()

    def play_next_clip(self) -> None:
        next_index = self.selected_index + 1
        if next_index >= len(self.clips):
            self.player.pause()
            self.play_btn.setText("▶")
            self.seek_global(self.total_duration())
            return
        self.load_clip(next_index, autoplay=True)
        self.media_list.setCurrentRow(next_index)

    def on_player_error(self, error, error_string: str) -> None:
        if error_string:
            self.statusBar().showMessage(f"Erro do player QtMultimedia: {error_string}")

    def toggle_mute(self) -> None:
        muted = self.audio.isMuted()
        self.audio.setMuted(not muted)
        self.mute_btn.setText("🔇" if not muted else "🔊")

    def export_selected(self) -> None:
        if self.selected_index < 0 or not self.clips:
            self.statusBar().showMessage("Selecione um clipe antes de exportar.")
            return
        if not FFMPEG.exists():
            QMessageBox.warning(self, "FFmpeg ausente", f"ffmpeg.exe não encontrado em {BIN_DIR}")
            return
        clip = self.clips[self.selected_index]
        out = EXPORTS_DIR / f"{clip.path.stem}_corte_{format_time(clip.start_ms).replace(':','-').replace('.','-')}_{format_time(clip.end_ms).replace(':','-').replace('.','-')}.mp4"
        command = [
            str(FFMPEG),
            "-y",
            "-i", str(clip.path),
            "-ss", seconds_arg(clip.start_ms),
            "-t", seconds_arg(clip.duration_ms),
            "-map", "0:v:0",
            "-map", "0:a?",
            "-vf", "scale=trunc(iw/2)*2:trunc(ih/2)*2",
            "-c:v", "libx264",
            "-preset", "veryfast",
            "-crf", "20",
            "-pix_fmt", "yuv420p",
            "-c:a", "aac",
            "-b:a", "160k",
            "-movflags", "+faststart",
            str(out),
        ]
        self.statusBar().showMessage("Exportando trecho selecionado...")
        try:
            result = subprocess.run(command, capture_output=True, text=True, timeout=3600, **hide_console_kwargs())
            if result.returncode != 0:
                raise RuntimeError((result.stderr or result.stdout).strip()[-900:])
            self.statusBar().showMessage(f"Exportado: {out}")
            QMessageBox.information(self, "Exportação concluída", f"Arquivo exportado:\n{out}")
        except Exception as exc:
            QMessageBox.critical(self, "Falha na exportação", str(exc))
            self.statusBar().showMessage("Falha na exportação.")


def main() -> int:
    app = QApplication(sys.argv)
    app.setApplicationName("VideoMaster PRO")
    win = MainWindow()
    win.show()
    return app.exec()


if __name__ == "__main__":
    raise SystemExit(main())
