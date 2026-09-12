from pathlib import Path
import hashlib

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'desktop/src/main/kotlin/br/com/monitordenoticias/desktop'
DASH = BASE / 'DashboardV5Main.kt'
PDF = BASE / 'PdfEditorScreenV2.kt'
EXTRACTOR_SCREEN = BASE / 'ExtractorVideoScreen.kt'
EXTRACTOR_ENGINE = BASE / 'ExtractorVideoEngine.kt'
EDITOR_SCREEN = BASE / 'VideoEditorScreen.kt'
PYSIDE_MAIN = ROOT / 'video_editor_pyside/main.py'
PYSIDE_REQ = ROOT / 'video_editor_pyside/requirements.txt'

for required in (DASH, PDF, EXTRACTOR_SCREEN, EXTRACTOR_ENGINE, EDITOR_SCREEN, PYSIDE_MAIN, PYSIDE_REQ):
    if not required.exists():
        raise SystemExit(f'Arquivo obrigatório ausente: {required}')


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


protected_before = {
    PDF: sha(PDF),
    EXTRACTOR_SCREEN: sha(EXTRACTOR_SCREEN),
    EXTRACTOR_ENGINE: sha(EXTRACTOR_ENGINE),
}

src = DASH.read_text(encoding='utf-8')

if 'VIDEO_EDITOR("Editor de Vídeo"' not in src:
    old = '    EXTRACTOR("Extrator de Vídeos", "Baixe vídeos com o fluxo direto v3.0.1", Icons.Default.Download)\n}'
    new = '    EXTRACTOR("Extrator de Vídeos", "Baixe vídeos com o fluxo direto v3.0.1", Icons.Default.Download),\n    VIDEO_EDITOR("Editor de Vídeo", "Abra o editor nativo PySide6/QtMultimedia", Icons.Default.Movie)\n}'
    if old not in src:
        raise SystemExit('Ponto de inserção da aba Editor de Vídeo não encontrado no enum V5Section.')
    src = src.replace(old, new, 1)

if 'else if (section == V5Section.VIDEO_EDITOR)' not in src:
    extractor_workspace = '''    } else if (section == V5Section.EXTRACTOR) {\n        Column(Modifier.fillMaxSize().background(V5Bg)) {\n            Row(Modifier.weight(1f).fillMaxWidth()) {\n                V5Sidebar(section, { section = it }, c, tick)\n                Box(Modifier.weight(1f).fillMaxHeight()) {\n                    ExtractorVideoScreen { section = V5Section.HOME }\n                }\n            }\n            V5Footer(c, tick)\n        }\n    } else {'''
    editor_workspace = '''    } else if (section == V5Section.EXTRACTOR) {\n        Column(Modifier.fillMaxSize().background(V5Bg)) {\n            Row(Modifier.weight(1f).fillMaxWidth()) {\n                V5Sidebar(section, { section = it }, c, tick)\n                Box(Modifier.weight(1f).fillMaxHeight()) {\n                    ExtractorVideoScreen { section = V5Section.HOME }\n                }\n            }\n            V5Footer(c, tick)\n        }\n    } else if (section == V5Section.VIDEO_EDITOR) {\n        Box(Modifier.fillMaxSize().background(V5Bg)) {\n            VideoEditorScreen { section = V5Section.HOME }\n        }\n    } else {'''
    if extractor_workspace not in src:
        raise SystemExit('Workspace dedicada do Extrator não encontrada; integração do Editor foi bloqueada para não adivinhar a estrutura.')
    src = src.replace(extractor_workspace, editor_workspace, 1)

DASH.write_text(src, encoding='utf-8')

for path, before in protected_before.items():
    if sha(path) != before:
        raise SystemExit(f'PROTEÇÃO: {path.name} foi alterado durante a integração do Editor de Vídeo.')

updated = DASH.read_text(encoding='utf-8')
updated_editor = EDITOR_SCREEN.read_text(encoding='utf-8')
updated_pyside = PYSIDE_MAIN.read_text(encoding='utf-8')
updated_req = PYSIDE_REQ.read_text(encoding='utf-8')
assert 'VIDEO_EDITOR("Editor de Vídeo"' in updated
assert 'else if (section == V5Section.VIDEO_EDITOR)' in updated
assert 'VideoEditorScreen { section = V5Section.HOME }' in updated
assert 'EXTRACTOR("Extrator de Vídeos"' in updated
assert 'PDF_EDITOR("Editor de PDF"' in updated
assert 'PySideVideoEditorLauncher' in updated_editor
assert 'video-editor/VideoEditorPySide/VideoEditorPySide.exe' in updated_editor
assert 'QMediaPlayer' in updated_pyside
assert 'QVideoWidget' in updated_pyside
assert 'QAudioOutput' in updated_pyside
assert 'PySide6==6.9.1' in updated_req
print('Editor de Vídeo integrado como launcher PySide6/QtMultimedia; PDF e motor/tela do Extrator permaneceram byte a byte intactos.')
