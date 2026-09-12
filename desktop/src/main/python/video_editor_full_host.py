import json
import sys
import threading
from pathlib import Path

from PySide6.QtCore import Qt, Signal, Slot
from PySide6.QtWidgets import QApplication

from advanced_editor_v300 import AdvancedVideoEditorWidget300
from legacy_theme import FUTURE_STYLESHEET


class EditorHost(AdvancedVideoEditorWidget300):
    command = Signal(dict)

    def __init__(self):
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

        self.command.connect(self.handle_command)
        self._emit({"type": "hwnd", "value": int(self.winId())})

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

    def _emit(self, payload):
        try:
            sys.stdout.write(json.dumps(payload, ensure_ascii=False) + "\n")
            sys.stdout.flush()
        except Exception:
            pass

    @Slot(dict)
    def handle_command(self, cmd):
        action = str(cmd.get("action") or "")
        if action == "show":
            self.show()
            self.raise_()
            self.activateWindow()
        elif action == "resize":
            w = max(1, int(cmd.get("width") or 1))
            h = max(1, int(cmd.get("height") or 1))
            self.resize(w, h)
        elif action == "quit":
            try:
                self.shutdown()
            finally:
                self.close()
                QApplication.quit()


def read_commands(host):
    for line in sys.stdin:
        try:
            obj = json.loads(line)
            host.command.emit(obj)
        except Exception as exc:
            host._emit({"type": "error", "message": str(exc)})


def main():
    app = QApplication(sys.argv)
    app.setApplicationName("Monitor de Notícias - Editor de Vídeo")
    app.setOrganizationName("MonitorDeNoticias")
    app.setQuitOnLastWindowClosed(False)
    app.setStyle("Fusion")
    app.setStyleSheet(FUTURE_STYLESHEET)

    host = EditorHost()
    threading.Thread(target=read_commands, args=(host,), daemon=True).start()
    sys.exit(app.exec())


if __name__ == "__main__":
    main()
