import json
import os
import sys
from pathlib import Path

from PySide6.QtCore import QObject, QTimer, QUrl, Signal, Slot
from PySide6.QtMultimedia import QAudioOutput, QMediaPlayer
from PySide6.QtMultimediaWidgets import QVideoWidget
from PySide6.QtWidgets import QApplication


class PreviewHost(QObject):
    command = Signal(dict)

    def __init__(self):
        super().__init__()
        self.video = QVideoWidget()
        self.video.setWindowTitle("MonitorVideoPreviewHost")
        self.video.setStyleSheet("background:#020A10;")
        self.video.resize(960, 540)
        self.video.show()

        self.player = QMediaPlayer(self)
        self.audio = QAudioOutput(self)
        self.player.setAudioOutput(self.audio)
        self.player.setVideoOutput(self.video)
        self.audio.setVolume(0.72)

        self.player.errorOccurred.connect(self._on_error)
        self.player.mediaStatusChanged.connect(self._on_status)
        self.player.playbackStateChanged.connect(self._on_state)

        self.timer = QTimer(self)
        self.timer.setInterval(55)
        self.timer.timeout.connect(self._tick)
        self.timer.start()

        self.command.connect(self.handle_command)
        self._emit({"type": "hwnd", "value": int(self.video.winId())})

    def _emit(self, payload):
        try:
            sys.stdout.write(json.dumps(payload, ensure_ascii=False) + "\n")
            sys.stdout.flush()
        except Exception:
            pass

    def _on_error(self, *args):
        msg = self.player.errorString() or "Falha de reprodução no QMediaPlayer."
        self._emit({"type": "error", "message": msg})

    def _on_status(self, status):
        self._emit({"type": "mediaStatus", "value": int(status.value)})

    def _on_state(self, state):
        self._emit({"type": "state", "playing": state == QMediaPlayer.PlaybackState.PlayingState})

    def _tick(self):
        self._emit({
            "type": "tick",
            "position": int(self.player.position()),
            "duration": int(self.player.duration()),
            "playing": self.player.playbackState() == QMediaPlayer.PlaybackState.PlayingState,
        })

    @Slot(dict)
    def handle_command(self, cmd):
        action = cmd.get("action")
        if action == "load":
            path = str(cmd.get("path") or "")
            if not path or not Path(path).exists():
                self._emit({"type": "error", "message": f"Arquivo não encontrado: {path}"})
                return
            self.player.stop()
            self.player.setSource(QUrl.fromLocalFile(os.path.abspath(path)))
            pos = max(0, int(cmd.get("position") or 0))
            autoplay = bool(cmd.get("autoplay"))

            def ready_seek():
                if self.player.duration() > 0:
                    self.player.setPosition(pos)
                    if autoplay:
                        self.player.play()
                else:
                    QTimer.singleShot(50, ready_seek)

            QTimer.singleShot(10, ready_seek)
        elif action == "play":
            self.player.play()
        elif action == "pause":
            self.player.pause()
        elif action == "seek":
            self.player.setPosition(max(0, int(cmd.get("position") or 0)))
        elif action == "volume":
            self.audio.setVolume(max(0.0, min(1.0, float(cmd.get("value") or 0.0))))
        elif action == "stop":
            self.player.stop()
        elif action == "quit":
            self.player.stop()
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
    app.setQuitOnLastWindowClosed(False)
    host = PreviewHost()

    import threading
    threading.Thread(target=read_commands, args=(host,), daemon=True).start()
    sys.exit(app.exec())


if __name__ == "__main__":
    main()
