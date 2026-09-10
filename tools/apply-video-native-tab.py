from pathlib import Path

path = Path('desktop/src/main/kotlin/br/com/monitordenoticias/desktop/DashboardV5Main.kt')
text = path.read_text(encoding='utf-8')

if 'EXTRACT_VIDEO(' not in text:
    old = '    EXTRACT_NEWS("Extrator de notícias", "Extração de matérias integrada ao Monitor", Icons.Default.Description),\n    HISTORY('
    new = '    EXTRACT_NEWS("Extrator de notícias", "Extração de matérias integrada ao Monitor", Icons.Default.Description),\n    EXTRACT_VIDEO("Extrator de vídeos", "Download e edição em timeline integrados ao Monitor", Icons.Default.VideoLibrary),\n    HISTORY('
    if old not in text:
        raise SystemExit('Ponto do enum V5Section não encontrado')
    text = text.replace(old, new, 1)

if 'V5Section.EXTRACT_VIDEO -> V5NativeVideoExtractorScreen()' not in text:
    old = '                            V5Section.EXTRACT_NEWS -> V5NativeNewsExtractorScreen()\n                            V5Section.HISTORY ->'
    new = '                            V5Section.EXTRACT_NEWS -> V5NativeNewsExtractorScreen()\n                            V5Section.EXTRACT_VIDEO -> V5NativeVideoExtractorScreen()\n                            V5Section.HISTORY ->'
    if old not in text:
        raise SystemExit('Ponto do when V5Section não encontrado')
    text = text.replace(old, new, 1)

path.write_text(text, encoding='utf-8')
print('Native video extractor tab wired into DashboardV5Main.kt')
