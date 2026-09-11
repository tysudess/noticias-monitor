from pathlib import Path

p = Path('desktop/src/main/kotlin/br/com/monitordenoticias/desktop/DashboardV5Main.kt')
s = p.read_text(encoding='utf-8')

if 'PDF_EDITOR("Editor de PDF"' not in s:
    anchor = '    EXTRACT_NEWS("Extrator de notícias", "Extração de matérias integrada ao Monitor", Icons.Default.Description),\n'
    if anchor not in s:
        raise SystemExit('Âncora EXTRACT_NEWS não encontrada no dashboard V8 integrado.')
    s = s.replace(anchor, anchor + '    PDF_EDITOR("Editor de PDF", "Editor de PDF original v0.3.1 integrado ao Monitor", Icons.Default.PictureAsPdf),\n', 1)

if 'V5Section.PDF_EDITOR -> EmbeddedPdfEditorScreen()' not in s:
    anchor = '                            V5Section.EXTRACT_NEWS -> NativeNewsExtractorScreen()\n'
    if anchor not in s:
        raise SystemExit('Rota EXTRACT_NEWS não encontrada no dashboard V8 integrado.')
    s = s.replace(anchor, anchor + '                            V5Section.PDF_EDITOR -> EmbeddedPdfEditorScreen()\n', 1)

p.write_text(s, encoding='utf-8')
print('PDF editor tab wired into DashboardV5Main.kt')
