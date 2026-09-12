from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PDF = ROOT / 'desktop/src/main/kotlin/br/com/monitordenoticias/desktop/PdfEditorScreenV2.kt'
if not PDF.exists():
    raise SystemExit('PdfEditorScreenV2.kt ausente.')

src = PDF.read_text(encoding='utf-8')

# Paleta da referência naval/HUD.
start = src.index('private val V2_BG =')
end = src.index('\n\nprivate enum class PdfV2ItemKind', start)
palette = '''private val V2_BG = Color(7, 17, 31)\nprivate val V2_BG_2 = Color(10, 22, 38)\nprivate val V2_BORDER = Color(62, 82, 111)\nprivate val V2_BORDER_SOFT = Color(42, 59, 83)\nprivate val V2_TEXT = Color(241, 243, 247)\nprivate val V2_MUTED = Color(164, 175, 194)\nprivate val V2_BLUE = Color(30, 137, 255)\nprivate val V2_BLUE_2 = Color(17, 111, 227)\nprivate val V2_GREEN = Color(44, 205, 153)\nprivate val V2_YELLOW = Color(255, 174, 34)\nprivate val V2_RED = Color(220, 78, 87)\nprivate val V2_CYAN = Color(40, 228, 242)\nprivate val V2_PURPLE = Color(117, 69, 246)\nprivate val V2_GOLD = Color(214, 164, 77)'''
src = src[:start] + palette + src[end:]

# Workspace completo: cabeçalho exatamente no padrão da imagem e três colunas.
ws_start = src.index('    private fun buildWorkspace():')
ws_end = src.index('\n\n    private fun buildLeftPanel()', ws_start)
workspace = r'''    private fun buildWorkspace():JComponent {
        val root = object : JPanel(BorderLayout()) {
            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.paint = GradientPaint(0f, 0f, Color(6, 18, 32), width.toFloat(), height.toFloat(), Color(8, 20, 35))
                g2.fillRect(0, 0, width, height)
                g2.color = Color(40, 127, 145, 24)
                var x = 0
                while (x < width) { g2.drawLine(x, 0, x, height); x += 78 }
                var y = 0
                while (y < height) { g2.drawLine(0, y, width, y); y += 78 }
                val cx = width / 2
                val cy = height / 2 + 40
                g2.color = Color(43, 171, 178, 25)
                g2.stroke = BasicStroke(1f)
                for (r in intArrayOf(120, 190, 260, 330)) g2.drawOval(cx-r, cy-r, r*2, r*2)
                for (a in 0 until 360 step 30) {
                    val rad = Math.toRadians(a.toDouble())
                    g2.drawLine(cx, cy, cx + (Math.cos(rad)*335).toInt(), cy + (Math.sin(rad)*335).toInt())
                }
                g2.color = Color(70, 185, 160, 26)
                g2.fillArc(cx-325, cy-325, 650, 650, -24, 34)
                g2.dispose()
                super.paintComponent(g)
            }
        }
        root.isOpaque = false
        root.border = EmptyBorder(0, 14, 10, 14)

        val top = JPanel(BorderLayout()).apply {
            isOpaque = false
            preferredSize = Dimension(1, 118)
            border = BorderFactory.createMatteBorder(0, 0, 1, 0, V2_BORDER_SOFT)
        }
        val brand = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            preferredSize = Dimension(270, 1)
            border = EmptyBorder(13, 18, 4, 8)
            add(label("DEPARTAMENTO", 26f, Font.BOLD, V2_GOLD))
            add(label("DE IMPRENSA", 26f, Font.BOLD, V2_GOLD))
        }
        top.add(brand, BorderLayout.WEST)
        top.add(JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            border = EmptyBorder(12, 0, 0, 0)
            add(label("VISUALIZAÇÃO DO DOCUMENTO", 25f, Font.BOLD, V2_TEXT).apply { alignmentX = CENTER_ALIGNMENT })
            add(label("Adicione uma imagem ou PDF para começar a editar", 16f, Font.PLAIN, Color(225, 228, 233)).apply { alignmentX = CENTER_ALIGNMENT })
            add(label("Banco de Opiniões 1304", 13f, Font.PLAIN, Color(145, 151, 164)).apply { alignmentX = CENTER_ALIGNMENT })
        }, BorderLayout.CENTER)
        top.add(JPanel(FlowLayout(FlowLayout.RIGHT, 8, 10)).apply {
            isOpaque = false
            preferredSize = Dimension(286, 1)
            add(controlButton("←  Voltar ao Monitor") { onExit() }.apply {
                preferredSize = Dimension(190, 42)
                foreground = V2_GOLD
            })
        }, BorderLayout.EAST)
        root.add(top, BorderLayout.NORTH)

        val body = JPanel(BorderLayout(12, 0)).apply {
            isOpaque = false
            border = EmptyBorder(8, 0, 0, 0)
            add(buildLeftPanel(), BorderLayout.WEST)
            add(buildCenterPanel(), BorderLayout.CENTER)
            add(buildRightPanel(), BorderLayout.EAST)
        }
        root.add(body, BorderLayout.CENTER)
        return root
    }'''
src = src[:ws_start] + workspace + src[ws_end:]

# Barra lateral conforme a referência. Funções originais continuam ligadas aos mesmos callbacks.
left_start = src.index('    private fun buildLeftPanel()')
left_end = src.index('\n\n    private fun showResizeInfo()', left_start)
left = r'''    private fun buildLeftPanel():JComponent {
        val p = RoundedPanel(14, Color(8, 19, 34, 230), V2_BORDER_SOFT).apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = EmptyBorder(8, 8, 8, 8)
            preferredSize = Dimension(258, 1)
            minimumSize = Dimension(242, 1)
        }
        fun addNav(component: JComponent, gapAfter: Int = 4) {
            component.alignmentX = LEFT_ALIGNMENT
            p.add(component)
            if (gapAfter > 0) p.add(gap(gapAfter))
        }
        addNav(navButton("↑", "Arquivos", null, V2_GOLD, true) { chooseAll() }, 8)
        addNav(navButton("PDF", "PDF", null, V2_GOLD) { choosePdfs() })
        addNav(navButton("✂", "Cortar", null, V2_GOLD) { startCrop() })
        addNav(navButton("⛶", "Redimensionar", null, V2_GOLD) { showResizeInfo() })
        addNav(navButton("▤", "Criar", null, Color(76, 137, 207)) { createBlankPage() })
        addNav(navButton("▥", "Excluir", null, V2_GOLD) { deleteSelected() })
        addNav(navButton("↕", "Ordenar", null, Color(46, 164, 123)) { focusReorder() }, 16)

        val drop = object : JPanel() {
            init {
                isOpaque = false
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                maximumSize = Dimension(Int.MAX_VALUE, 194)
                preferredSize = Dimension(236, 194)
                border = EmptyBorder(16, 10, 10, 10)
                alignmentX = LEFT_ALIGNMENT
            }
            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.paint = GradientPaint(0f, 0f, Color(57, 51, 40, 205), 0f, height.toFloat(), Color(18, 23, 34, 220))
                g2.fillRoundRect(0, 0, width-1, height-1, 14, 14)
                g2.color = V2_GOLD
                g2.stroke = BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, floatArrayOf(5f,5f), 0f)
                g2.drawRoundRect(0, 0, width-1, height-1, 14, 14)
                g2.dispose()
                super.paintComponent(g)
            }
        }
        listOf(
            label("☁", 25f, Font.BOLD, Color(205, 201, 191)),
            label("Arraste", 14f, Font.PLAIN, Color(215, 213, 208)),
            label("PDF/imagem", 14f, Font.PLAIN, Color(215, 213, 208)),
            label("ou selecione", 13f, Font.PLAIN, Color(185, 181, 174))
        ).forEach { it.alignmentX = CENTER_ALIGNMENT; drop.add(it) }
        drop.add(gap(12))
        drop.add(blueButton("Selecionar") { chooseAll() }.apply {
            alignmentX = CENTER_ALIGNMENT
            maximumSize = Dimension(190, 46)
            preferredSize = Dimension(190, 46)
            font = font.deriveFont(Font.BOLD, 15f)
        })
        installFileDrop(drop)
        p.add(Box.createVerticalGlue())
        p.add(drop)
        return p
    }'''
src = src[:left_start] + left + src[left_end:]

# Centro: controles em faixa única e visualização grande como na referência.
center_start = src.index('    private fun buildCenterPanel()')
center_end = src.index('\n\n    private fun buildRightPanel()', center_start)
center = r'''    private fun buildCenterPanel():JComponent {
        val p = RoundedPanel(14, Color(8, 18, 32, 225), V2_BORDER_SOFT).apply {
            layout = BorderLayout(0, 8)
            border = EmptyBorder(8, 10, 10, 10)
        }

        val controls = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
            border = EmptyBorder(0, 45, 0, 8)
            add(squareControl("−") { setZoom(zoom - .15) })
            add(Box.createHorizontalStrut(8))
            zoomLabel.foreground = V2_TEXT
            zoomLabel.font = zoomLabel.font.deriveFont(16f)
            zoomLabel.preferredSize = Dimension(62, 40)
            zoomLabel.maximumSize = Dimension(62, 40)
            zoomLabel.horizontalAlignment = SwingConstants.CENTER
            add(zoomLabel)
            add(Box.createHorizontalStrut(8))
            add(squareControl("+") { setZoom(zoom + .15) })
            add(Box.createHorizontalStrut(10))
            add(controlButton("⛶  Ajustar") { setZoom(1.0) }.apply { preferredSize = Dimension(128, 40); maximumSize = Dimension(128, 40) })
            add(Box.createHorizontalStrut(10))
            add(squareControl("↶") { undo() })
            add(Box.createHorizontalStrut(2))
            add(squareControl("↷") { redo() })
            add(Box.createHorizontalStrut(10))
            add(controlButton("▱  Limpar") { clearAll() }.apply { preferredSize = Dimension(128, 40); maximumSize = Dimension(128, 40) })
            add(Box.createHorizontalGlue())
            styleModeToggle(viewThumb, true)
            styleModeToggle(viewList, false)
            ButtonGroup().apply { add(viewThumb); add(viewList) }
            viewThumb.addActionListener { setThumbMode(true) }
            viewList.addActionListener { setThumbMode(false) }
            add(viewThumb)
            add(Box.createHorizontalStrut(5))
            add(viewList)
            add(Box.createHorizontalStrut(18))
            pageCount.foreground = Color(160, 166, 177)
            pageCount.font = pageCount.font.deriveFont(13f)
            pageCount.preferredSize = Dimension(82, 40)
            pageCount.maximumSize = Dimension(82, 40)
            add(pageCount)
        }
        p.add(controls, BorderLayout.NORTH)

        val body = JPanel(BorderLayout(7, 0)).apply { isOpaque = false }
        val previewStack = RoundedPanel(5, Color(6, 16, 29, 235), V2_BORDER).apply {
            layout = BorderLayout()
            border = EmptyBorder(8, 8, 8, 8)
        }
        preview.background = Color(7, 17, 30)
        preview.border = BorderFactory.createLineBorder(Color(64, 87, 119), 1)
        preview.minimumSize = Dimension(760, 620)
        preview.onCrop = { c ->
            val idx = thumbList.selectedIndex
            if (idx in pages.indices) {
                pushUndo(); pages[idx].crop = c; redo.clear(); refreshAll(selectIndex = idx)
            }
        }
        installFileDrop(preview)
        previewStack.add(preview, BorderLayout.CENTER)
        body.add(previewStack, BorderLayout.CENTER)

        val thumbs = RoundedPanel(8, Color(8, 21, 36, 215), V2_BORDER_SOFT).apply {
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
        p.add(body, BorderLayout.CENTER)
        return p
    }'''
src = src[:center_start] + center + src[center_end:]

right_start = src.index('    private fun buildRightPanel()')
right_end = src.index('\n    private fun buildFooter()', right_start)
right = r'''    private fun buildRightPanel():JComponent {
        val p = RoundedPanel(14, Color(9, 19, 33, 232), V2_BORDER_SOFT).apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = EmptyBorder(16, 12, 12, 12)
            preferredSize = Dimension(278, 1)
            minimumSize = Dimension(260, 1)
        }
        p.add(label("Capa e Exportação", 20f, Font.BOLD, V2_TEXT))
        p.add(gap(10))
        includeCover.foreground = Color(221, 222, 225)
        includeCover.background = Color(9, 19, 33)
        includeCover.isBorderPainted = false
        includeCover.isFocusPainted = false
        includeCover.horizontalAlignment = SwingConstants.LEFT
        includeCover.alignmentX = LEFT_ALIGNMENT
        includeCover.maximumSize = Dimension(Int.MAX_VALUE, 32)
        includeCover.addActionListener { styleCoverToggle() }
        styleCoverToggle()
        p.add(includeCover)
        p.add(gap(8))

        p.add(RoundedPanel(11, Color(28, 47, 69), V2_GOLD).apply {
            layout = BorderLayout()
            maximumSize = Dimension(Int.MAX_VALUE, 285)
            preferredSize = Dimension(250, 285)
            border = EmptyBorder(9, 9, 9, 9)
            coverPreview.horizontalAlignment = SwingConstants.CENTER
            coverPreview.verticalAlignment = SwingConstants.CENTER
            add(coverPreview, BorderLayout.CENTER)
        })
        p.add(gap(10))
        p.add(controlButton("▧  Trocar capa") { changeCover() }.apply {
            maximumSize = Dimension(Int.MAX_VALUE, 44)
            preferredSize = Dimension(250, 44)
            alignmentX = LEFT_ALIGNMENT
        })
        p.add(Box.createVerticalGlue())
        p.add(greenButton("✔  GERAR PDF", "Exportar documento final") { exportPdf() })
        return p
    }'''
src = src[:right_start] + right + src[right_end:]

# Helpers visuais dourados.
helper_start = src.index('    private fun navButton(')
helper_end = src.index('\n    private fun fieldLabel(', helper_start)
helpers = r'''    private fun navButton(i:String,t:String,sub:String?,a:Color,active:Boolean=false,act:()->Unit):JComponent {
        val fill = if (active) Color(44, 57, 72) else Color(8, 19, 34)
        val line = if (active) V2_GOLD else Color(117, 89, 49)
        return StyledButton("", fill, V2_TEXT, line).apply {
            layout = BorderLayout(10, 0)
            maximumSize = Dimension(Int.MAX_VALUE, 64)
            preferredSize = Dimension(240, 64)
            minimumSize = Dimension(220, 58)
            alignmentX = LEFT_ALIGNMENT
            border = EmptyBorder(6, 8, 6, 8)
            toolTipText = if (sub == null) t else "$t - $sub"
            add(RoundedPanel(8, Color(a.red, a.green, a.blue, 28), a).apply {
                preferredSize = Dimension(45, 45)
                minimumSize = Dimension(45, 45)
                layout = BorderLayout()
                add(label(i, if (i == "PDF") 11f else 20f, Font.PLAIN, a).apply { horizontalAlignment = SwingConstants.CENTER }, BorderLayout.CENTER)
            }, BorderLayout.WEST)
            add(label(t, 18f, Font.PLAIN, V2_TEXT), BorderLayout.CENTER)
            addActionListener { act() }
        }
    }

    private fun blueButton(t:String,a:()->Unit)=StyledButton(t,Color(39,43,58),V2_GOLD,V2_GOLD).apply{font=font.deriveFont(Font.BOLD,17f);addActionListener{a()}}
    private fun controlButton(t:String,a:()->Unit)=StyledButton(t,Color(35,35,45),V2_TEXT,Color(90,76,63)).apply{preferredSize=Dimension(110,40);maximumSize=Dimension(110,40);font=font.deriveFont(Font.PLAIN,13f);addActionListener{a()}}
    private fun squareControl(t:String,a:()->Unit)=StyledButton(t,Color(35,35,45),V2_TEXT,Color(83,73,68)).apply{preferredSize=Dimension(54,40);maximumSize=Dimension(54,40);font=font.deriveFont(Font.PLAIN,19f);addActionListener{a()}}
    private fun greenButton(a:String,b:String,act:()->Unit):JComponent=StyledButton("",V2_YELLOW,Color(10,13,18),V2_GOLD).apply{layout=BorderLayout();maximumSize=Dimension(Int.MAX_VALUE,74);preferredSize=Dimension(250,74);alignmentX=LEFT_ALIGNMENT;add(JPanel().apply{layout=BoxLayout(this,BoxLayout.Y_AXIS);isOpaque=false;add(label(a,19f,Font.BOLD,Color(10,13,18)).apply{alignmentX=CENTER_ALIGNMENT});add(label(b,11.5f,Font.PLAIN,Color(61,42,16)).apply{alignmentX=CENTER_ALIGNMENT})},BorderLayout.CENTER);addActionListener{act()}}
    private fun styleModeToggle(b:JToggleButton,s:Boolean){b.isSelected=s;b.isFocusPainted=false;b.foreground=if(s)V2_GOLD else Color(110,111,119);b.background=Color(25,27,35);b.border=BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(if(s)V2_GOLD else Color(52,54,64)),EmptyBorder(9,14,9,14));b.addItemListener{b.foreground=if(b.isSelected)V2_GOLD else Color(110,111,119);b.border=BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(if(b.isSelected)V2_GOLD else Color(52,54,64)),EmptyBorder(9,14,9,14))}}
    private fun styleCoverToggle(){includeCover.text=if(includeCover.isSelected)"•  Incluir capa padrão" else "○  Incluir capa padrão";includeCover.foreground=if(includeCover.isSelected)Color(225,225,228)else V2_MUTED}'''
src = src[:helper_start] + helpers + src[helper_end:]

# Preview vazio: botão dourado e radar mais próximo da referência.
src = src.replace('g2.color=V2_BLUE;g2.fillRoundRect(cx-130,cy+140,260,54,12,12);g2.color=Color.WHITE;', 'g2.color=V2_YELLOW;g2.fillRoundRect(cx-145,cy+140,290,58,12,12);g2.color=Color(18,20,24);')
src = src.replace('drawCentered(g2,"▰  Selecionar arquivos",cy+174)', 'drawCentered(g2,"▰  Selecionar arquivos",cy+176)')

PDF.write_text(src, encoding='utf-8')
updated = PDF.read_text(encoding='utf-8')
for required in (
    'DEPARTAMENTO', 'DE IMPRENSA', 'VISUALIZAÇÃO DO DOCUMENTO', 'Banco de Opiniões 1304',
    'Capa e Exportação', 'GERAR PDF', 'Selecionar arquivos', 'Voltar ao Monitor',
    'Cortar', 'Redimensionar', 'Criar', 'Excluir', 'Ordenar', 'Miniaturas', 'Lista'
):
    assert required in updated, required
for functional in ('chooseAll()', 'choosePdfs()', 'startCrop()', 'showResizeInfo()', 'createBlankPage()', 'deleteSelected()', 'focusReorder()', 'changeCover()', 'exportPdf()', 'undo()', 'redo()', 'clearAll()'):
    assert functional in updated, functional
print('Layout naval/HUD do Editor PDF aplicado sem remover callbacks funcionais.')
