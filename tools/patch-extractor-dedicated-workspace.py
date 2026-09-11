from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DASH = ROOT / 'desktop/src/main/kotlin/br/com/monitordenoticias/desktop/DashboardV5Main.kt'
PDF = ROOT / 'desktop/src/main/kotlin/br/com/monitordenoticias/desktop/PdfEditorScreenV2.kt'
if not DASH.exists() or not PDF.exists():
    raise SystemExit('DashboardV5Main.kt ou PdfEditorScreenV2.kt ausente.')

pdf_before = PDF.read_bytes()
src = DASH.read_text(encoding='utf-8')

needle = '''    if (section == V5Section.PDF_EDITOR) {\n        Column(Modifier.fillMaxSize().background(V5Bg)) {\n            Row(Modifier.weight(1f).fillMaxWidth()) {\n                V5Sidebar(section, { section = it }, c, tick)\n                Box(Modifier.weight(1f).fillMaxHeight()) {\n                    PdfEditorScreenV2 { section = V5Section.HOME }\n                }\n            }\n            V5Footer(c, tick)\n        }\n    } else {'''
replacement = '''    if (section == V5Section.PDF_EDITOR) {\n        Column(Modifier.fillMaxSize().background(V5Bg)) {\n            Row(Modifier.weight(1f).fillMaxWidth()) {\n                V5Sidebar(section, { section = it }, c, tick)\n                Box(Modifier.weight(1f).fillMaxHeight()) {\n                    PdfEditorScreenV2 { section = V5Section.HOME }\n                }\n            }\n            V5Footer(c, tick)\n        }\n    } else if (section == V5Section.EXTRACTOR) {\n        Column(Modifier.fillMaxSize().background(V5Bg)) {\n            Row(Modifier.weight(1f).fillMaxWidth()) {\n                V5Sidebar(section, { section = it }, c, tick)\n                Box(Modifier.weight(1f).fillMaxHeight()) {\n                    ExtractorVideoScreen { section = V5Section.HOME }\n                }\n            }\n            V5Footer(c, tick)\n        }\n    } else {'''

if 'else if (section == V5Section.EXTRACTOR)' not in src:
    if needle not in src:
        raise SystemExit('Bloco de workspace do PDF não encontrado para espelhar o Extrator.')
    src = src.replace(needle, replacement, 1)

DASH.write_text(src, encoding='utf-8')
if PDF.read_bytes() != pdf_before:
    raise SystemExit('PROTEÇÃO: Editor PDF foi alterado pelo patch de workspace.')
updated = DASH.read_text(encoding='utf-8')
assert 'else if (section == V5Section.EXTRACTOR)' in updated
assert 'ExtractorVideoScreen { section = V5Section.HOME }' in updated
print('Extrator integrado como workspace dedicada no mesmo padrão estrutural do Editor de PDF; PDF intacto.')
