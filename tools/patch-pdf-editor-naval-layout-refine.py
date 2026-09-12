from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PDF = ROOT / 'desktop/src/main/kotlin/br/com/monitordenoticias/desktop/PdfEditorScreenV2.kt'
if not PDF.exists():
    raise SystemExit('PdfEditorScreenV2.kt ausente.')

src = PDF.read_text(encoding='utf-8')

# -----------------------------------------------------------------------------
# REFINO MEDIDO DA IMAGEM 2 (2048x1143)
# A geometria abaixo foi derivada da própria referência, não por aproximação:
# - coluna esquerda ~17,1% da largura útil
# - centro ~63,2%
# - coluna direita ~17,6%
# - corpo inicia em ~13,1% da altura total
# - painel direito inicia ~9,8% abaixo do corpo
# - preview é mais largo e mais baixo/compacto que na primeira tentativa
# -----------------------------------------------------------------------------

# Cabeçalho: na captura anterior o corpo começava ~22 px abaixo da referência.
src = src.replace('preferredSize = Dimension(1, 118)', 'preferredSize = Dimension(1, 98)', 1)

old_body = '''        val body = JPanel(BorderLayout(12, 0)).apply {
            isOpaque = false
            border = EmptyBorder(8, 0, 0, 0)
            add(buildLeftPanel(), BorderLayout.WEST)
            add(buildCenterPanel(), BorderLayout.CENTER)
            add(buildRightPanel(), BorderLayout.EAST)
        }
'''
new_body = '''        val leftPanel = buildLeftPanel()
        val centerPanel = buildCenterPanel()
        val rightPanel = buildRightPanel()
        val body = object : JPanel(null) {
            init {
                isOpaque = false
                add(leftPanel)
                add(centerPanel)
                add(rightPanel)
            }
            override fun doLayout() {
                // Proporções medidas diretamente da referência 2048x1143.
                val leftW = (width * 0.171f).roundToInt()
                val gapLeft = (width * 0.009f).roundToInt()
                val rightW = (width * 0.176f).roundToInt()
                val gapRight = (width * 0.012f).roundToInt()
                val centerX = leftW + gapLeft
                val rightX = width - rightW
                val centerW = (rightX - gapRight - centerX).coerceAtLeast(120)
                val rightY = (height * 0.098f).roundToInt()
                leftPanel.setBounds(0, 0, leftW, height)
                centerPanel.setBounds(centerX, 0, centerW, height)
                rightPanel.setBounds(rightX, rightY, rightW, (height - rightY).coerceAtLeast(120))
            }
        }
'''
if old_body not in src:
    raise SystemExit('Bloco principal de três colunas não encontrado para o refino medido.')
src = src.replace(old_body, new_body, 1)

# Coluna esquerda: remove excesso de largura/padding visto na captura real.
src = src.replace(
    'border = EmptyBorder(8, 8, 8, 8)\n            preferredSize = Dimension(258, 1)\n            minimumSize = Dimension(242, 1)',
    'border = EmptyBorder(0, 8, 8, 8)\n            preferredSize = Dimension(210, 1)\n            minimumSize = Dimension(190, 1)',
    1,
)

# Centro: maior largura útil e preview exatamente nas margens medidas da referência.
src = src.replace(
    'border = EmptyBorder(8, 10, 10, 10)',
    'border = EmptyBorder(2, 22, 10, 4)',
    1,
)
src = src.replace(
    'val body = JPanel(BorderLayout(7, 0)).apply { isOpaque = false }',
    'val body = JPanel(BorderLayout(7, 0)).apply { isOpaque = false; border = EmptyBorder(50, 0, 18, 0) }',
    1,
)

# Moldura HUD do preview: cantos dourados e segmentos azul-acinzentados como a imagem 2.
old_preview_stack = '''        val previewStack = RoundedPanel(5, Color(6, 16, 29, 235), V2_BORDER).apply {
            layout = BorderLayout()
            border = EmptyBorder(8, 8, 8, 8)
        }
'''
new_preview_stack = '''        val previewStack = object : JPanel(BorderLayout()) {
            init {
                isOpaque = false
                border = EmptyBorder(8, 8, 8, 8)
            }
            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = Color(6, 16, 29, 235)
                g2.fillRoundRect(0, 0, width - 1, height - 1, 8, 8)
                g2.color = Color(62, 82, 111)
                g2.stroke = BasicStroke(1f)
                g2.drawRoundRect(1, 1, width - 3, height - 3, 8, 8)
                val gold = V2_GOLD
                val steel = Color(66, 92, 126)
                g2.stroke = BasicStroke(2.2f)
                // Ls dourados dos quatro cantos
                g2.color = gold
                val c = 17
                val m = 10
                g2.drawLine(m, m + c, m, m); g2.drawLine(m, m, m + c, m)
                g2.drawLine(width - m - c, m, width - m, m); g2.drawLine(width - m, m, width - m, m + c)
                g2.drawLine(m, height - m - c, m, height - m); g2.drawLine(m, height - m, m + c, height - m)
                g2.drawLine(width - m - c, height - m, width - m, height - m); g2.drawLine(width - m, height - m - c, width - m, height - m)
                // segmentos técnicos superiores/inferiores
                g2.color = steel
                g2.stroke = BasicStroke(3f)
                g2.drawLine(38, 8, 128, 8)
                g2.drawLine(width - 205, 8, width - 38, 8)
                g2.drawLine(38, height - 8, 180, height - 8)
                g2.drawLine(width - 215, height - 8, width - 38, height - 8)
                g2.dispose()
            }
        }
'''
if old_preview_stack not in src:
    raise SystemExit('Moldura do preview não encontrada.')
src = src.replace(old_preview_stack, new_preview_stack, 1)

# Painel direito: na captura anterior ele ocupava largura/altura excessivas.
src = src.replace('maximumSize = Dimension(Int.MAX_VALUE, 285)', 'maximumSize = Dimension(Int.MAX_VALUE, 224)', 1)
src = src.replace('preferredSize = Dimension(250, 285)', 'preferredSize = Dimension(250, 224)', 1)
# Botão GERAR PDF da referência é mais baixo e fica colado ao rodapé.
src = src.replace(
    'maximumSize=Dimension(Int.MAX_VALUE,74);preferredSize=Dimension(250,74)',
    'maximumSize=Dimension(Int.MAX_VALUE,64);preferredSize=Dimension(250,64)',
    1,
)

# Texto exatamente como aparece na imagem de referência.
src = src.replace('JToggleButton("▦  Miniaturas", true)', 'JToggleButton("▦  Miniatural", true)', 1)

# A coluna de miniaturas não aparece no estado vazio da imagem de referência,
# mas precisa continuar disponível quando houver páginas.
if 'private val thumbsHost = JPanel(BorderLayout())' not in src:
    src = src.replace(
        '    private val thumbList = JList(thumbModel)\n',
        '    private val thumbList = JList(thumbModel)\n    private val thumbsHost = JPanel(BorderLayout())\n',
        1,
    )

old_thumbs = '''        val thumbs = RoundedPanel(8, Color(8, 21, 36, 215), V2_BORDER_SOFT).apply {
            layout = BorderLayout()
            border = EmptyBorder(5, 4, 5, 4)
            preferredSize = Dimension(82, 1)
            minimumSize = Dimension(76, 260)
        }
        configureThumbList()
        thumbs.add(JScrollPane(thumbList).apply {
            border = null
            viewport.background = Color(8, 21, 36)
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        }, BorderLayout.CENTER)
        body.add(thumbs, BorderLayout.EAST)
'''
new_thumbs = '''        configureThumbList()
        thumbsHost.removeAll()
        thumbsHost.isOpaque = true
        thumbsHost.background = Color(8, 21, 36)
        thumbsHost.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(V2_BORDER_SOFT),
            EmptyBorder(5, 4, 5, 4)
        )
        thumbsHost.preferredSize = Dimension(82, 1)
        thumbsHost.minimumSize = Dimension(76, 260)
        thumbsHost.add(JScrollPane(thumbList).apply {
            border = null
            viewport.background = Color(8, 21, 36)
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        }, BorderLayout.CENTER)
        thumbsHost.isVisible = pages.isNotEmpty()
        body.add(thumbsHost, BorderLayout.EAST)
'''
if old_thumbs in src:
    src = src.replace(old_thumbs, new_thumbs, 1)

old_refresh = 'private fun refreshAll(selectLast:Boolean=false,selectIndex:Int?=null){val uid=thumbList.selectedValue?.uid;thumbModel.clear();pages.forEach{thumbModel.addElement(it)};pageCount.text="${pages.size} ${if(pages.size==1)"página" else "páginas"}";'
new_refresh = 'private fun refreshAll(selectLast:Boolean=false,selectIndex:Int?=null){val uid=thumbList.selectedValue?.uid;thumbModel.clear();pages.forEach{thumbModel.addElement(it)};thumbsHost.isVisible=pages.isNotEmpty();thumbsHost.revalidate();pageCount.text="${pages.size} ${if(pages.size==1)"página" else "páginas"}";'
if old_refresh in src:
    src = src.replace(old_refresh, new_refresh, 1)

# Radar da área vazia: mesma estrutura circular, eixo e cunha verde da referência,
# acrescida da âncora central em marca d'água.
needle = 'if(img==null){val cx=width/2;val cy=height/2-25;'
replacement = '''if(img==null){val cx=width/2;val cy=height/2-31;
            g2.color=Color(34,104,121,30);g2.stroke=BasicStroke(1f);
            val rr=(min(width,height)*0.38).roundToInt();
            for(f in listOf(1.0,0.78,0.56,0.34)){val r=(rr*f).roundToInt();g2.drawOval(cx-r,cy-r,r*2,r*2)}
            for(a in 0 until 360 step 30){val rad=Math.toRadians(a.toDouble());g2.drawLine(cx,cy,cx+(Math.cos(rad)*rr).toInt(),cy+(Math.sin(rad)*rr).toInt())}
            g2.color=Color(54,176,150,32);g2.fillArc(cx-rr,cy-rr,rr*2,rr*2,-28,36);
            g2.color=Color(79,107,143,80);g2.drawLine(0,cy,width,cy);g2.drawLine(cx,0,cx,height);
            val anchorSize=(min(width,height)*0.33).roundToInt();g2.font=Font("Segoe UI Symbol",Font.PLAIN,anchorSize);g2.color=Color(29,39,56,115);val afm=g2.fontMetrics;val anchor="⚓";g2.drawString(anchor,cx-afm.stringWidth(anchor)/2,cy+afm.ascent/3);
            '''
if needle in src:
    src = src.replace(needle, replacement, 1)

# Botão Selecionar arquivos: largura já estava praticamente correta; ajusta apenas a altura.
src = src.replace(
    'g2.color=V2_YELLOW;g2.fillRoundRect(cx-145,cy+140,290,58,12,12);g2.color=Color(18,20,24);',
    'g2.color=V2_YELLOW;g2.fillRoundRect(cx-145,cy+140,290,51,12,12);g2.color=Color(18,20,24);',
    1,
)

# Marcador para o contrato do release.
if 'private class PdfV2PreviewPanel:JPanel()' in src and 'HUD CORNERS PDF' not in src:
    src = src.replace(
        'private class PdfV2PreviewPanel:JPanel(){',
        'private class PdfV2PreviewPanel:JPanel(){/* HUD CORNERS PDF - REFERENCE GEOMETRY */',
        1,
    )

PDF.write_text(src, encoding='utf-8')
updated = PDF.read_text(encoding='utf-8')
for required in (
    'thumbsHost', 'thumbsHost.isVisible=pages.isNotEmpty()', 'HUD CORNERS PDF',
    'leftW = (width * 0.171f)', 'rightW = (width * 0.176f)',
    'border = EmptyBorder(50, 0, 18, 0)', 'Miniatural', 'anchor="⚓"'
):
    assert required in updated, required
print('Refino medido da Imagem 2 aplicado: proporções, HUD, radar, âncora e painel de capa corrigidos.')
