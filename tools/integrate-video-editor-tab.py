from pathlib import Path
import hashlib

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'desktop/src/main/kotlin/br/com/monitordenoticias/desktop'
DASH = BASE / 'DashboardV5Main.kt'
PDF = BASE / 'PdfEditorScreenV2.kt'
EXTRACTOR_SCREEN = BASE / 'ExtractorVideoScreen.kt'
EXTRACTOR_ENGINE = BASE / 'ExtractorVideoEngine.kt'
EDITOR_SCREEN = BASE / 'VideoEditorScreen.kt'
EDITOR_ENGINE = BASE / 'VideoEditorEngine.kt'
EDITOR_PREVIEW = BASE / 'VideoEditorPreview.kt'
EDITOR_PROCESS = BASE / 'VideoEditorProcess.kt'

for required in (DASH, PDF, EXTRACTOR_SCREEN, EXTRACTOR_ENGINE, EDITOR_SCREEN, EDITOR_ENGINE, EDITOR_PREVIEW, EDITOR_PROCESS):
    if not required.exists():
        raise SystemExit(f'Arquivo obrigatório ausente: {required.name}')


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


protected_before = {
    PDF: sha(PDF),
    EXTRACTOR_SCREEN: sha(EXTRACTOR_SCREEN),
    EXTRACTOR_ENGINE: sha(EXTRACTOR_ENGINE),
}

# Correção defensiva: a tela deve exibir o formato pelo próprio arquivo aberto, sem inventar metadado inexistente.
editor_src = EDITOR_SCREEN.read_text(encoding='utf-8')
editor_src = editor_src.replace('info?.format ?: input.extension.uppercase()', 'input.extension.uppercase()')
EDITOR_SCREEN.write_text(editor_src, encoding='utf-8')

# Correção defensiva: o preview não deve voltar ao caminho direto/JavaFX instável.
# A versão atual usa proxy FFmpeg + sequência de frames em cache, para manter velocidade estável.
engine_src = EDITOR_ENGINE.read_text(encoding='utf-8')
old_preview_direct = '''            if (canPlayDirectly(input, info)) {
                update(100, "Preview pronto.")
                return@runCatching input
            }

'''
if old_preview_direct in engine_src:
    engine_src = engine_src.replace(old_preview_direct, '', 1)
EDITOR_ENGINE.write_text(engine_src, encoding='utf-8')

src = DASH.read_text(encoding='utf-8')

if 'VIDEO_EDITOR("Editor de Vídeo"' not in src:
    old = '    EXTRACTOR("Extrator de Vídeos", "Baixe vídeos com o fluxo direto v3.0.1", Icons.Default.Download)\n}'
    new = '    EXTRACTOR("Extrator de Vídeos", "Baixe vídeos com o fluxo direto v3.0.1", Icons.Default.Download),\n    VIDEO_EDITOR("Editor de Vídeo", "Abra, marque um trecho e exporte um novo MP4", Icons.Default.Movie)\n}'
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
updated_engine = EDITOR_ENGINE.read_text(encoding='utf-8')
updated_preview = EDITOR_PREVIEW.read_text(encoding='utf-8')
assert 'VIDEO_EDITOR("Editor de Vídeo"' in updated
assert 'else if (section == V5Section.VIDEO_EDITOR)' in updated
assert 'VideoEditorScreen { section = V5Section.HOME }' in updated
assert 'EXTRACTOR("Extrator de Vídeos"' in updated
assert 'PDF_EDITOR("Editor de PDF"' in updated
assert 'info?.format' not in updated_editor
assert 'return@runCatching input' not in updated_engine
assert 'preparePreviewFrames' in updated_engine
assert 'VideoEditorFrameSequence' in updated_engine
assert 'previewSequence' in updated_editor
assert 'LaunchedEffect(playing, previewSequence, busy, outMs, inMs, playbackSeed)' in updated_editor
assert 'extractPreviewFrame' not in updated_editor
assert 'javafx.scene.media.MediaPlayer' not in updated_preview
print('Editor de Vídeo integrado em tela cheia; preview estabilizado com sequência de frames FFmpeg em cache; PDF e motor/tela do Extrator permaneceram byte a byte intactos.')
