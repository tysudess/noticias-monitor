from pathlib import Path

DASHBOARD = Path("desktop/src/main/kotlin/br/com/monitordenoticias/desktop/DashboardV5Main.kt")

src = DASHBOARD.read_text(encoding="utf-8")
before = src

# Idempotent enum insertion.
if "VIDEO_EDITOR(\"Editor de Vídeo\"" not in src:
    old = '    PDF_EDITOR("Editor de PDF", "Monte, reorganize, recorte e exporte PDFs e imagens", Icons.Default.PictureAsPdf)\n'
    new = '    PDF_EDITOR("Editor de PDF", "Monte, reorganize, recorte e exporte PDFs e imagens", Icons.Default.PictureAsPdf),\n    VIDEO_EDITOR("Editor de Vídeo", "Edite sequências, cortes e exportações de vídeo", Icons.Default.Movie)\n'
    if old not in src:
        raise SystemExit("Contrato não encontrado: enum PDF_EDITOR")
    src = src.replace(old, new, 1)

# Idempotent route integration. Keep the PDF route intact and add Video Editor beside it.
old_route = '''    if (section == V5Section.PDF_EDITOR) {
        Column(Modifier.fillMaxSize().background(V5Bg)) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                V5Sidebar(section, { section = it }, c, tick)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    PdfEditorScreenV2 { section = V5Section.HOME }
                }
            }
            V5Footer(c, tick)
        }
    } else {
'''
new_route = '''    if (section == V5Section.PDF_EDITOR || section == V5Section.VIDEO_EDITOR) {
        Column(Modifier.fillMaxSize().background(V5Bg)) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                V5Sidebar(section, { section = it }, c, tick)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    when (section) {
                        V5Section.PDF_EDITOR -> PdfEditorScreenV2 { section = V5Section.HOME }
                        V5Section.VIDEO_EDITOR -> VideoEditorScreen { section = V5Section.HOME }
                        else -> Unit
                    }
                }
            }
            V5Footer(c, tick)
        }
    } else {
'''

if "section == V5Section.PDF_EDITOR || section == V5Section.VIDEO_EDITOR" not in src:
    if old_route not in src:
        raise SystemExit("Contrato não encontrado: rota do Editor de PDF")
    src = src.replace(old_route, new_route, 1)

# Functional contract checks.
required = [
    'PDF_EDITOR("Editor de PDF"',
    'VIDEO_EDITOR("Editor de Vídeo"',
    'PdfEditorScreenV2 { section = V5Section.HOME }',
    'VideoEditorScreen { section = V5Section.HOME }',
]
for marker in required:
    if marker not in src:
        raise SystemExit(f"Falha de contrato após patch: {marker}")

if src != before:
    DASHBOARD.write_text(src, encoding="utf-8")
    print("DashboardV5Main.kt atualizado com a aba Editor de Vídeo.")
else:
    print("Patch já aplicado; nenhuma duplicação realizada.")
