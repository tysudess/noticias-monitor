from pathlib import Path
import hashlib

ROOT = Path(__file__).resolve().parents[1]
DASH = ROOT / 'desktop/src/main/kotlin/br/com/monitordenoticias/desktop/DashboardV5Main.kt'
PDF = ROOT / 'desktop/src/main/kotlin/br/com/monitordenoticias/desktop/PdfEditorScreenV2.kt'
EXTRACTOR = ROOT / 'desktop/src/main/kotlin/br/com/monitordenoticias/desktop/ExtractorVideoScreen.kt'


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


pdf_before = sha(PDF)
text = DASH.read_text(encoding='utf-8')

old_enum = '    PDF_EDITOR("Editor de PDF", "Monte, reorganize, recorte e exporte PDFs e imagens", Icons.Default.PictureAsPdf)\n'
new_enum = '    PDF_EDITOR("Editor de PDF", "Monte, reorganize, recorte e exporte PDFs e imagens", Icons.Default.PictureAsPdf),\n    EXTRACTOR("Extrator de Vídeos", "Baixe vídeos com o fluxo direto v3.0.1", Icons.Default.Download)\n'
if 'EXTRACTOR("Extrator de Vídeos"' not in text:
    if old_enum not in text:
        raise SystemExit('Ponto de inserção do enum V5Section não encontrado.')
    text = text.replace(old_enum, new_enum, 1)

old_when = '                                V5Section.SETTINGS -> V5SettingsScreen(c, tick)\n                                else -> Unit\n'
new_when = '                                V5Section.SETTINGS -> V5SettingsScreen(c, tick)\n                                V5Section.EXTRACTOR -> ExtractorVideoScreen { section = V5Section.HOME }\n                                else -> Unit\n'
if 'V5Section.EXTRACTOR -> ExtractorVideoScreen' not in text:
    if old_when not in text:
        raise SystemExit('Ponto de inserção da tela do Extrator não encontrado.')
    text = text.replace(old_when, new_when, 1)

DASH.write_text(text, encoding='utf-8')

if not EXTRACTOR.exists():
    raise SystemExit('ExtractorVideoScreen.kt ausente.')
if sha(PDF) != pdf_before:
    raise SystemExit('PROTEÇÃO: PdfEditorScreenV2.kt foi alterado durante a integração.')

updated = DASH.read_text(encoding='utf-8')
assert 'EXTRACTOR("Extrator de Vídeos"' in updated
assert 'V5Section.EXTRACTOR -> ExtractorVideoScreen' in updated
assert 'PDF_EDITOR("Editor de PDF"' in updated
print('Integração segura da aba Extrator concluída; Editor de PDF permaneceu intacto.')
