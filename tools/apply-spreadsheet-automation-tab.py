from pathlib import Path

p = Path('desktop/src/main/kotlin/br/com/monitordenoticias/desktop/DashboardV5Main.kt')
s = p.read_text(encoding='utf-8')

if 'SPREADSHEET_AUTOMATION("Automação de Planilhas"' not in s:
    anchor = '    PDF_EDITOR("Editor de PDF", "Editor de PDF original v0.3.1 integrado ao Monitor", Icons.Default.PictureAsPdf),\n'
    if anchor not in s:
        raise SystemExit('Âncora PDF_EDITOR não encontrada no dashboard V8 integrado.')
    s = s.replace(anchor, anchor + '    SPREADSHEET_AUTOMATION("Automação de Planilhas", "Automação original v1.1.1 integrada ao Monitor", Icons.Default.TableChart),\n', 1)

if 'V5Section.SPREADSHEET_AUTOMATION -> EmbeddedSpreadsheetAutomationScreen()' not in s:
    anchor = '                            V5Section.PDF_EDITOR -> EmbeddedPdfEditorScreen()\n'
    if anchor not in s:
        raise SystemExit('Rota PDF_EDITOR não encontrada no dashboard V8 integrado.')
    s = s.replace(anchor, anchor + '                            V5Section.SPREADSHEET_AUTOMATION -> EmbeddedSpreadsheetAutomationScreen()\n', 1)

required = [
    'SPREADSHEET_AUTOMATION("Automação de Planilhas"',
    'V5Section.SPREADSHEET_AUTOMATION -> EmbeddedSpreadsheetAutomationScreen()'
]
for item in required:
    if item not in s:
        raise SystemExit(f'Integração incompleta: {item}')

p.write_text(s, encoding='utf-8')
print('Spreadsheet automation tab wired into DashboardV5Main.kt')
