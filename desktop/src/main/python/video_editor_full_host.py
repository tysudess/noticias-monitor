import argparse
import sys
from pathlib import Path

from PySide6.QtCore import Qt, QTimer
from PySide6.QtGui import QWindow
from PySide6.QtWidgets import QApplication, QScrollArea, QVBoxLayout, QWidget

from advanced_editor_v300 import AdvancedVideoEditorWidget300
from legacy_theme import FUTURE_STYLESHEET


class EditorHost(QWidget):
    """Contêiner de integração.

    O motor original permanece inteiro dentro de ``self.editor``. Esta classe
    apenas o hospeda, cria a rolagem da aba e vincula a janela Qt ao HWND do
    painel do Monitor ANTES de exibi-la. Isso evita o comportamento de uma
    janela Qt top-level reaparecer por cima do Monitor quando QVideoWidget
    muda de estado ao carregar/reproduzir mídia.
    """

    def __init__(self, bridge_dir: Path, parent_hwnd: int):
        super().__init__()
        self.bridge_dir = bridge_dir
        self.bridge_dir.mkdir(parents=True, exist_ok=True)
        self.hwnd_file = self.bridge_dir / "hwnd.txt"
        self.quit_file = self.bridge_dir / "quit.flag"
        self.error_file = self.bridge_dir / "error.txt"
        self._shutdown_done = False
        self._foreign_parent = None

        app_root = self._resolve_app_root()
        videos_dir = app_root / "Videos"
        videos_dir.mkdir(parents=True, exist_ok=True)
        ffmpeg_exe = app_root / "bin" / "ffmpeg.exe"
        ffprobe_exe = app_root / "bin" / "ffprobe.exe"

        # O editor abaixo é exatamente o AdvancedVideoEditorWidget300 antigo.
        # Nenhuma função de preview/timeline/corte/exportação é reimplementada.
        self.editor = AdvancedVideoEditorWidget300(videos_dir, ffmpeg_exe, ffprobe_exe, self)

        # A tela antiga é mais alta que a área disponível dentro do Monitor.
        # Mantemos a interface original e oferecemos apenas uma rolagem externa.
        self.editor.setMinimumWidth(1180)
        self.editor.setMinimumHeight(1100)

        self.scroll = QScrollArea(self)
        self.scroll.setWidget(self.editor)
        self.scroll.setWidgetResizable(True)
        self.scroll.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAsNeeded)
        self.scroll.setVerticalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAsNeeded)
        self.scroll.setFrameShape(QScrollArea.Shape.NoFrame)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(0)
        layout.addWidget(self.scroll)

        self.setWindowTitle("MonitorVideoEditorHost")
        self.setWindowFlag(Qt.WindowType.FramelessWindowHint, True)
        self.setAttribute(Qt.WidgetAttribute.WA_NativeWindow, True)
        self.resize(1450, 900)

        # Cria o HWND Qt ainda oculto, informa ao Qt que o pai é uma janela
        # estrangeira do Monitor e somente DEPOIS mostra o host.
        hwnd = int(self.winId())
        handle = self.windowHandle()
        self._foreign_parent = QWindow.fromWinId(int(parent_hwnd))
        if handle is None or self._foreign_parent is None:
            raise RuntimeError("Não foi possível criar a relação nativa Monitor/Qt.")
        handle.setParent(self._foreign_parent)
        self.move(0, 0)
        self.show()

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
        except Exception as exc:
            self._write_error(exc)

    def _shutdown_once(self):
        if self._shutdown_done:
            return
        self._shutdown_done = True
        try:
            self.editor.shutdown()
        except Exception as exc:
            self._write_error(exc)

    def closeEvent(self, event):
        self._shutdown_once()
        event.accept()
        QApplication.quit()


def parse_args():
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument("--bridge-dir", required=True)
    parser.add_argument("--parent-hwnd", required=True, type=int)
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
        host = EditorHost(bridge_dir, args.parent_hwnd)
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
