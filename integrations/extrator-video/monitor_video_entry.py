"""Ponte mínima entre o Monitor de Notícias e o Extrator de Vídeos v3.0.1.

Nenhuma função do extrator é reimplementada aqui. O arquivo importa a versão
original e apenas seleciona, quando solicitado pelo Monitor, a página Download
ou Editor já existente no QStackedWidget original.
"""

import os
from pathlib import Path

from PySide6.QtCore import QTimer

import refined_layout_v301 as appmod

CONTROL_FILE = os.environ.get("MONITOR_VIDEO_CONTROL", "").strip()
START_PAGE = os.environ.get("MONITOR_VIDEO_PAGE", "download").strip().lower() or "download"

_original_init = appmod.core.MainWindow.__init__


def _apply_page(window, page):
    requested = (page or "download").strip().lower()
    if requested == "editor":
        window.ref_stack.setCurrentIndex(1)
        if "editor" in window.ref_nav:
            window.ref_nav["editor"].setChecked(True)
    else:
        # No layout original, Download ocupa o índice 0.
        window.ref_stack.setCurrentIndex(0)
        if "download" in window.ref_nav:
            window.ref_nav["download"].setChecked(True)


def _patched_init(self, *args, **kwargs):
    _original_init(self, *args, **kwargs)
    self._monitor_page = ""

    def poll_monitor_page():
        requested = START_PAGE
        if CONTROL_FILE:
            try:
                requested = Path(CONTROL_FILE).read_text(encoding="utf-8").strip() or requested
            except OSError:
                pass
        requested = requested.lower()
        if requested != self._monitor_page:
            _apply_page(self, requested)
            self._monitor_page = requested

    poll_monitor_page()
    self._monitor_page_timer = QTimer(self)
    self._monitor_page_timer.setInterval(250)
    self._monitor_page_timer.timeout.connect(poll_monitor_page)
    self._monitor_page_timer.start()


appmod.core.MainWindow.__init__ = _patched_init


def main():
    return appmod.main()


if __name__ == "__main__":
    main()
