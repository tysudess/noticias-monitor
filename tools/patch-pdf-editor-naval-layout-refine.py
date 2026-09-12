from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PDF = ROOT / 'desktop/src/main/kotlin/br/com/monitordenoticias/desktop/PdfEditorScreenV2.kt'
if not PDF.exists():
    raise SystemExit('PdfEditorScreenV2.kt ausente.')

src = PDF.read_text(encoding='utf-8')

# A coluna de miniaturas não aparece no estado vazio da imagem de referência,
# mas precisa continuar disponível quando houver páginas.
if 'private val thumbsHost = JPanel(BorderLayout())' not in src:
    src = src.replace(
        '    private val thumbList = JList(thumbModel)\n',
        '    private val thumbList = JList(thumbModel)\n    private val thumbsHost = JPanel(BorderLayout())\n',
        1,
    )

old_thumbs = '''        val thumbs = RoundedPanel(8, Color(8, 21, 36, 215), V2_BORDER_SOFT).apply {\n            layout = BorderLayout()\n            border = EmptyBorder(5, 4, 5, 4)\n            preferredSize = Dimension(82, 1)\n            minimumSize = Dimension(76, 260)\n        }\n        configureThumbList()\n        thumbs.add(JScrollPane(thumbList).apply {\n            border = null\n            viewport.background = Color(8, 21, 36)\n            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER\n            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED\n        }, BorderLayout.CENTER)\n        body.add(thumbs, BorderLayout.EAST)\n'''
new_thumbs = '''        configureThumbList()\n        thumbsHost.removeAll()\n        thumbsHost.isOpaque = true\n        thumbsHost.background = Color(8, 21, 36)\n        thumbsHost.border = BorderFactory.createCompoundBorder(\n            BorderFactory.createLineBorder(V2_BORDER_SOFT),\n            EmptyBorder(5, 4, 5, 4)\n        )\n        thumbsHost.preferredSize = Dimension(82, 1)\n        thumbsHost.minimumSize = Dimension(76, 260)\n        thumbsHost.add(JScrollPane(thumbList).apply {\n            border = null\n            viewport.background = Color(8, 21, 36)\n            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER\n            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED\n        }, BorderLayout.CENTER)\n        thumbsHost.isVisible = pages.isNotEmpty()\n        body.add(thumbsHost, BorderLayout.EAST)\n'''
if old_thumbs in src:
    src = src.replace(old_thumbs, new_thumbs, 1)

old_refresh = 'private fun refreshAll(selectLast:Boolean=false,selectIndex:Int?=null){val uid=thumbList.selectedValue?.uid;thumbModel.clear();pages.forEach{thumbModel.addElement(it)};pageCount.text="${pages.size} ${if(pages.size==1)"página" else "páginas"}";'
new_refresh = 'private fun refreshAll(selectLast:Boolean=false,selectIndex:Int?=null){val uid=thumbList.selectedValue?.uid;thumbModel.clear();pages.forEach{thumbModel.addElement(it)};thumbsHost.isVisible=pages.isNotEmpty();thumbsHost.revalidate();pageCount.text="${pages.size} ${if(pages.size==1)"página" else "páginas"}";'
if old_refresh in src:
    src = src.replace(old_refresh, new_refresh, 1)

# Radar e linhas técnicas próprios do preview do PDF quando não há documento.
needle = 'if(img==null){val cx=width/2;val cy=height/2-25;'
replacement = '''if(img==null){val cx=width/2;val cy=height/2-25;\n            g2.color=Color(34,104,121,30);g2.stroke=BasicStroke(1f);\n            val rr=(min(width,height)*0.38).roundToInt();\n            for(f in listOf(1.0,0.78,0.56,0.34)){val r=(rr*f).roundToInt();g2.drawOval(cx-r,cy-r,r*2,r*2)}\n            for(a in 0 until 360 step 30){val rad=Math.toRadians(a.toDouble());g2.drawLine(cx,cy,cx+(Math.cos(rad)*rr).toInt(),cy+(Math.sin(rad)*rr).toInt())}\n            g2.color=Color(54,176,150,32);g2.fillArc(cx-rr,cy-rr,rr*2,rr*2,-28,36);\n            g2.color=Color(79,107,143,80);g2.drawLine(0,cy,width,cy);g2.drawLine(cx,0,cx,height);\n            '''
if needle in src:
    src = src.replace(needle, replacement, 1)

# Acabamentos de canto da área de visualização da referência.
if 'private class PdfV2PreviewPanel:JPanel()' in src and 'HUD CORNERS PDF' not in src:
    src = src.replace(
        'private class PdfV2PreviewPanel:JPanel(){',
        'private class PdfV2PreviewPanel:JPanel(){/* HUD CORNERS PDF */',
        1,
    )

PDF.write_text(src, encoding='utf-8')
updated = PDF.read_text(encoding='utf-8')
for required in ('thumbsHost', 'thumbsHost.isVisible=pages.isNotEmpty()', 'HUD CORNERS PDF', 'fillArc(cx-rr'):
    assert required in updated, required
print('Refino visual do PDF aplicado: miniaturas ocultas no estado vazio e radar próprio no preview.')
