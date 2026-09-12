Host de pré-visualização do Editor de Vídeo.

Arquitetura reproduzida do editor antigo funcional:
- PySide6 QMediaPlayer
- PySide6 QAudioOutput
- PySide6 QVideoWidget
- atualização de posição a cada 55 ms

O executável video-preview-host.exe é gerado no CI com PyInstaller e embutido dentro da aba do Monitor via HWND/SetParent.
