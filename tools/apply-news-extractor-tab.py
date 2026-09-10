from pathlib import Path

DASH = Path('desktop/src/main/kotlin/br/com/monitordenoticias/desktop/DashboardV5Main.kt')
text = DASH.read_text(encoding='utf-8')


def one(old: str, new: str, label: str):
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 match, found {count}')
    text = text.replace(old, new, 1)

one(
    '    SOURCES("Fontes", "Fontes nacionais, regionais e mídias especializadas", Icons.Default.Storage),\n    HISTORY("Histórico",',
    '    SOURCES("Fontes", "Fontes nacionais, regionais e mídias especializadas", Icons.Default.Storage),\n    EXTRACT_NEWS("Extrator de notícias", "Extrator de Matérias Windows v1.25.19 integrado", Icons.Default.Description),\n    HISTORY("Histórico",',
    'enum section'
)

one(
    '            Item("Sair", onClick = { controller.close(); exitApplication() })',
    '            Item("Sair", onClick = { shutdownIntegratedTools(); controller.close(); exitApplication() })',
    'tray exit cleanup'
)

one(
    '                            V5Section.SOURCES -> V5SourcesScreen(c, tick)\n                            V5Section.HISTORY -> V5HistoryScreen(c, tick)',
    '                            V5Section.SOURCES -> V5SourcesScreen(c, tick)\n                            V5Section.EXTRACT_NEWS -> V5IntegratedNewsExtractorScreen()\n                            V5Section.HISTORY -> V5HistoryScreen(c, tick)',
    'screen routing'
)

DASH.write_text(text, encoding='utf-8')
print('News extractor tab wired into V8 dashboard.')
