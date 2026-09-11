from pathlib import Path

p = Path('desktop/src/main/kotlin/br/com/monitordenoticias/desktop/DashboardV5Main.kt')
s = p.read_text(encoding='utf-8')

pdf_enum = '    PDF_EDITOR("Editor de PDF", "Editor de PDF original v0.3.1 integrado ao Monitor", Icons.Default.PictureAsPdf),\n'
if 'PDF_EDITOR("Editor de PDF"' not in s:
    anchor = '    EXTRACT_NEWS("Extrator de notícias", "Extração de matérias integrada ao Monitor", Icons.Default.Description),\n'
    if anchor not in s:
        raise SystemExit('Âncora EXTRACT_NEWS não encontrada no dashboard V8 integrado.')
    s = s.replace(anchor, anchor + pdf_enum, 1)

pdf_route = '                            V5Section.PDF_EDITOR -> EmbeddedPdfEditorScreen()\n'
if 'V5Section.PDF_EDITOR -> EmbeddedPdfEditorScreen()' not in s:
    anchor = '                            V5Section.EXTRACT_NEWS -> V5NativeNewsExtractorScreen()\n'
    if anchor not in s:
        raise SystemExit('Rota V5 EXTRACT_NEWS não encontrada no dashboard V8 integrado.')
    s = s.replace(anchor, anchor + pdf_route, 1)

# Fail closed: never allow a build that lists the PDF tab without routing it.
if 'PDF_EDITOR("Editor de PDF"' not in s:
    raise SystemExit('Falha ao inserir a entrada Editor de PDF no menu.')
if 'V5Section.PDF_EDITOR -> EmbeddedPdfEditorScreen()' not in s:
    raise SystemExit('Falha ao ligar a rota Editor de PDF à tela incorporada.')

p.write_text(s, encoding='utf-8')
print('PDF editor v0.3.1 tab and route verified in DashboardV5Main.kt')
