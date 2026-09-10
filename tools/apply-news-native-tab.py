from pathlib import Path

p = Path('desktop/src/main/kotlin/br/com/monitordenoticias/desktop/DashboardV5Main.kt')
s = p.read_text(encoding='utf-8')

needle = '    SOURCES("Fontes", "Fontes nacionais, regionais e mídias especializadas", Icons.Default.Storage),\n    HISTORY('
replacement = '    SOURCES("Fontes", "Fontes nacionais, regionais e mídias especializadas", Icons.Default.Storage),\n    EXTRACT_NEWS("Extrator de notícias", "Extração de matérias integrada ao Monitor", Icons.Default.Description),\n    HISTORY('
if needle not in s:
    raise SystemExit('enum anchor not found')
s = s.replace(needle, replacement, 1)

needle = '                            V5Section.SOURCES -> V5SourcesScreen(c, tick)\n                            V5Section.HISTORY -> V5HistoryScreen(c, tick)'
replacement = '                            V5Section.SOURCES -> V5SourcesScreen(c, tick)\n                            V5Section.EXTRACT_NEWS -> V5NativeNewsExtractorScreen()\n                            V5Section.HISTORY -> V5HistoryScreen(c, tick)'
if needle not in s:
    raise SystemExit('screen anchor not found')
s = s.replace(needle, replacement, 1)

p.write_text(s, encoding='utf-8')
print('Native news extractor tab wired into DashboardV5Main.kt')
