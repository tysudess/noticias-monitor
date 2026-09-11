from pathlib import Path

path = Path('desktop/src/main/kotlin/br/com/monitordenoticias/desktop/PdfEditorScreenV2.kt')
text = path.read_text(encoding='utf-8')

old_workspace = 'private fun buildWorkspace():JComponent=JPanel(BorderLayout(4,0)).apply{background=V2_BG;border=EmptyBorder(4,4,4,4);add(buildLeftPanel(),BorderLayout.WEST);add(buildCenterPanel(),BorderLayout.CENTER);add(buildRightPanel(),BorderLayout.EAST)}'
new_workspace = 'private fun buildWorkspace():JComponent=JPanel(BorderLayout(4,0)).apply{background=V2_BG;border=EmptyBorder(4,4,4,4);add(buildLeftPanel(),BorderLayout.WEST);add(buildCenterPanel(),BorderLayout.CENTER)}'
text = text.replace(old_workspace, new_workspace)

start = text.index('    private fun buildLeftPanel():JComponent {')
end = text.index('    private fun buildCenterPanel():JComponent {')
left = r'''    private fun buildLeftPanel():JComponent {
        val p = RoundedPanel(16, V2_BG_2, V2_BORDER_SOFT).apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = EmptyBorder(8, 7, 8, 7)
            preferredSize = Dimension(166, 1)
            minimumSize = Dimension(162, 1)
        }

        fun addNav(component: JComponent, gapAfter: Int = 3) {
            component.alignmentX = LEFT_ALIGNMENT
            p.add(component)
            if (gapAfter > 0) p.add(gap(gapAfter))
        }

        addNav(navButton("⇧", "Arquivos", "Img / PDF", V2_BLUE, true) { chooseAll() }, 5)
        addNav(navButton("PDF", "PDF", null, Color(232, 74, 78)) { choosePdfs() })
        addNav(navButton("✂", "Cortar", null, V2_PURPLE) { startCrop() })
        addNav(navButton("▣", "Redimensionar", null, V2_RED) { showResizeInfo() })
        addNav(navButton("⟳", "Girar", null, Color(61, 157, 255)) { showTransformMenu() })
        addNav(navButton("▤", "Criar", null, Color(84, 153, 230)) { createBlankPage() })
        addNav(navButton("▥", "Excluir", null, V2_RED) { deleteSelected() })
        addNav(navButton("▧", "Capa", null, Color(25, 201, 142)) { changeCover() })
        addNav(navButton("↕", "Ordenar", null, V2_CYAN) { focusReorder() }, 10)

        val drop = object : JPanel() {
            init {
                isOpaque = false
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                maximumSize = Dimension(Int.MAX_VALUE, 166)
                preferredSize = Dimension(150, 166)
                border = EmptyBorder(13, 8, 9, 8)
                alignmentX = LEFT_ALIGNMENT
            }
            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.paint = GradientPaint(0f, 0f, Color(29, 49, 70), 0f, height.toFloat(), Color(12, 34, 56))
                g2.fillRoundRect(0, 0, width - 1, height - 1, 14, 14)
                g2.color = Color(117, 154, 192)
                g2.stroke = BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, floatArrayOf(4f, 4f), 0f)
                g2.drawRoundRect(0, 0, width - 1, height - 1, 14, 14)
                g2.dispose()
                super.paintComponent(g)
            }
        }
        listOf(
            label("☁", 25f, Font.BOLD, Color(205, 231, 255)),
            label("Arraste", 11.5f, Font.PLAIN, Color(205, 219, 236)),
            label("PDF/imagem", 11.5f, Font.PLAIN, Color(205, 219, 236)),
            label("ou selecione", 10f, Font.PLAIN, V2_MUTED)
        ).forEach { it.alignmentX = CENTER_ALIGNMENT; drop.add(it) }
        drop.add(gap(9))
        drop.add(blueButton("Selecionar") { chooseAll() }.apply {
            alignmentX = CENTER_ALIGNMENT
            maximumSize = Dimension(138, 38)
            preferredSize = Dimension(138, 38)
            font = font.deriveFont(Font.BOLD, 14f)
        })
        installFileDrop(drop)
        p.add(drop)
        p.add(Box.createVerticalGlue())
        return p
    }

    private fun showResizeInfo() {
        val idx = thumbList.selectedIndex
        if (idx !in pages.indices) {
            showError("Selecione uma página para redimensionar.")
            return
        }
        val options = arrayOf("75%", "90%", "100%", "110%", "125%")
        val selected = JOptionPane.showInputDialog(this, "Escolha a escala de visualização da página:", "Redimensionar", JOptionPane.PLAIN_MESSAGE, null, options, "100%") as? String ?: return
        val factor = selected.removeSuffix("%").toDoubleOrNull()?.div(100.0) ?: 1.0
        setZoom(factor)
    }

'''
text = text[:start] + left + text[end:]

nav_start = text.index('    private fun navButton(')
nav_end = text.index('    private fun blueButton(', nav_start)
nav = r'''    private fun navButton(i:String,t:String,sub:String?,a:Color,active:Boolean=false,act:()->Unit):JComponent {
        val fill = if (active) Color(16, 76, 154) else V2_BG_2
        val line = if (active) V2_BLUE else V2_BG_2
        return StyledButton("", fill, V2_TEXT, line).apply {
            layout = BorderLayout(9, 0)
            maximumSize = Dimension(Int.MAX_VALUE, if (active) 58 else 52)
            preferredSize = Dimension(150, if (active) 58 else 52)
            minimumSize = Dimension(150, if (active) 58 else 52)
            alignmentX = LEFT_ALIGNMENT
            border = EmptyBorder(5, 8, 5, 8)
            toolTipText = if (sub == null) t else "$t - $sub"
            add(RoundedPanel(8, Color(a.red, a.green, a.blue, 35), a).apply {
                preferredSize = Dimension(36, 36)
                minimumSize = Dimension(36, 36)
                layout = BorderLayout()
                add(label(i, if (i == "PDF") 10f else 17f, Font.BOLD, a).apply {
                    horizontalAlignment = SwingConstants.CENTER
                }, BorderLayout.CENTER)
            }, BorderLayout.WEST)
            add(JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                add(label(t, 14.5f, Font.BOLD, V2_TEXT))
                if (sub != null) add(label(sub, 10.5f, Font.PLAIN, Color(156, 190, 224)))
            }, BorderLayout.CENTER)
            addActionListener { act() }
        }
    }

'''
text = text[:nav_start] + nav + text[nav_end:]

old_control = 'controls.add(controlButton("▱  Limpar") { clearAll() })'
new_control = '''controls.add(controlButton("▱  Limpar") { clearAll() })
        controls.add(Box.createHorizontalStrut(8))
        controls.add(StyledButton("GERAR PDF", V2_GREEN, Color.WHITE, Color(29,229,123)).apply {
            preferredSize = Dimension(112,36)
            font = font.deriveFont(Font.BOLD,12.5f)
            addActionListener { exportPdf() }
        })'''
text = text.replace(old_control, new_control)

path.write_text(text, encoding='utf-8')
