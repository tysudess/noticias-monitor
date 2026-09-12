import argparse
import sys
from pathlib import Path

from PySide6.QtCore import Qt, QTimer
from PySide6.QtWidgets import QApplication

from advanced_editor_v300 import AdvancedVideoEditorWidget300
from legacy_theme import FUTURE_STYLESHEET


class EditorHost(AdvancedVideoEditorWidget300):
    def __init__(self, bridge_dir: Path):
        self.bridge_dir = bridge_dir
        self.bridge_dir.mkdir(parents=True, exist_ok=True)
        self.hwnd_file = self.bridge_dir / "hwnd.txt"
        self.show_file = self.bridge_dir / "show.flag"
        self.quit_file = self.bridge_dir / "quit.flag"
        self.error_file = self.bridge_dir / "error.txt"
        self._shown_inside_monitor = False
        self._shutdown_done = False

        app_root = self._resolve_app_root()
        videos_dir = app_root / "Videos"
        videos_dir.mkdir(parents=True, exist_ok=True)
        ffmpeg_exe = app_root / "bin" / "ffmpeg.exe"
        ffprobe_exe = app_root / "bin" / "ffprobe.exe"

        super().__init__(videos_dir, ffmpeg_exe, ffprobe_exe)
        self.setWindowTitle("MonitorVideoEditorHost")
        self.setAttribute(Qt.WidgetAttribute.WA_NativeWindow, True)
        self.resize(1450, 900)
        self.hide()

        # Força a criação do HWND ainda oculto e entrega o identificador ao Monitor
        # sem depender de stdin/stdout (inexistentes no PyInstaller --windowed).
        hwnd = int(self.winId())
        tmp = self.bridge_dir / "hwnd.tmp"
        tmp.write_text(str(hwnd), encoding="utf-8")
        tmp.replace(self.hwnd_file)

        self.bridge_timer = QTimer(self)
        self.bridge_timer.setInterval(100)
        self.bridge_timer.timeout.connect(self._poll_bridge)
        self.bridge_timer.start()

    @staticmethod
    def _resolve_app_root():
        exe = Path(sys.executable).resolve()
        # Portable final: <raiz>/bin/video-editor-host/video-editor-host.exe
        if exe.parent.name.lower() == "video-editor-host" and exe.parent.parent.name.lower() == "bin":
            return exe.parent.parent.parent
        # Desenvolvimento / fallback controlado.
        cwd = Path.cwd().resolve()
        if (cwd / "bin" / "ffmpeg.exe").exists():
            return cwd
        if (cwd.parent / "bin" / "ffmpeg.exe").exists():
            return cwd.parent
        return exe.parent

    def _write_error(self, message):
        try:
            self.error_file.write_text(str(message), encoding="utf-8")
        except Exception:
            pass

    def _poll_bridge(self):
        try:
            if self.quit_file.exists():
                self.bridge_timer.stop()
                self.close()
                return

            if not self._shown_inside_monitor and self.show_file.exists():
                self._shown_inside_monitor = True
                self.show()
        except Exception as exc:
            self._write_error(exc)

    def _shutdown_once(self):
        if self._shutdown_done:
            return
        self._shutdown_done = True
        try:
            self.shutdown()
        except Exception as exc:
            self._write_error(exc)

    def closeEvent(self, event):
        self._shutdown_once()
        event.accept()
        QApplication.quit()


def parse_args():
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument("--bridge-dir", required=True)
    return parser.parse_args()


def main():
    args = parse_args()
    bridge_dir = Path(args.bridge_dir).resolve()

    app = QApplication(sys.argv[:1])
    app.setApplicationName("Monitor de Notícias - Editor de Vídeo")
    app.setOrganizationName("MonitorDeNoticias")
    app.setQuitOnLastWindowClosed(False)
    app.setStyle("Fusion")
    app.setStyleSheet(FUTURE_STYLESHEET)

    try:
        host = EditorHost(bridge_dir)
        code = app.exec()
        host._shutdown_once()
        raise SystemExit(code)
    except SystemExit:
        raise
    except Exception as exc:
        try:
            bridge_dir.mkdir(parents=True, exist_ok=True)
            (bridge_dir / "error.txt").write_text(str(exc), encoding="utf-8")
        except Exception:
            pass
        raise


if __name__ == "__main__":
    main()
