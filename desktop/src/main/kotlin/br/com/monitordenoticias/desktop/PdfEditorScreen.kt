package br.com.monitordenoticias.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import org.apache.pdfbox.Loader
import org.apache.pdfbox.multipdf.LayerUtility
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.util.Matrix
import org.json.JSONObject
import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.awt.event.*
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val PDF_BG = Color(22, 27, 34)
private val PDF_PANEL = Color(30, 37, 46)
private val PDF_PANEL_2 = Color(38, 46, 57)
private val PDF_TEXT = Color(236, 241, 247)
private val PDF_MUTED = Color(157, 170, 185)
private val PDF_BLUE = Color(35, 128, 255)
private val PDF_RED = Color(210, 68, 83)
private val PDF_BORDER = Color(65, 76, 90)

private enum class PdfItemKind { IMAGE, PDF }
private enum class ExportQuality(val label: String, val dpi: Int) {
    HIGH("Alta - 300 DPI", 300),
    MEDIUM("Média - 220 DPI", 220),
    COMPACT("Compacta - 160 DPI", 160)
}

private data class NormalizedCrop(val x: Double, val y: Double, val w: Double, val h: Double)
private data class PdfPageData(
    val uid: String = UUID.randomUUID().toString(),
    val kind: PdfItemKind,
    val path: String,
    val pageNo: Int? = null,
    var rotation: Int = 0,
    var crop: NormalizedCrop? = null
) {
    fun copyDeep() = copy(crop = crop?.copy())
}

private data class EditorSnapshot(val pages: List<PdfPageData>, val selected: Int)

@Composable
fun PdfEditorScreen() {
    SwingPanel(
        modifier = Modifier.fillMaxSize(),
        factory = { PdfEditorPanel() }
    )
}

private class PdfEditorPanel : JPanel(BorderLayout()) {
    private val pages = mutableListOf<PdfPageData>()
    private val undo = ArrayDeque<EditorSnapshot>()
    private val thumbnailModel = DefaultListModel<PdfPageData>()
    private val thumbnailList = JList(thumbnailModel)
    private val preview = PreviewPanel()
    private val status = JLabel("0 páginas")
    private val zoomLabel = JLabel("100%")
    private val includeCover = JCheckBox("Incluir capa padrão", true)
    private val coverPreview = JLabel("Capa padrão", SwingConstants.CENTER)
    private val quality = JComboBox(ExportQuality.entries.toTypedArray())
    private var zoom = 1.0
    private var customCover: File? = null
    private val portableDir = resolvePortableDir()
    private val dataDir = File(portableDir, "data")
    private val logDir = File(portableDir, "logs")
    private val configFile = File(dataDir, "config.json")
    private val customCoverFile = File(dataDir, "capa_padrao_usuario.png")

    init {
        background = PDF_BG
        border = EmptyBorder(0, 0, 0, 0)
        isFocusable = true
        createPortableDirs()
        loadConfig()
        add(buildLeftPanel(), BorderLayout.WEST)
        add(buildCenterPanel(), BorderLayout.CENTER)
        add(buildRightPanel(), BorderLayout.EAST)
        installKeyboardShortcuts()
        installFileDrop(this)
        refreshAll()
    }

    private fun buildLeftPanel(): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.background = PDF_PANEL
        panel.border = EmptyBorder(16, 14, 16, 14)
        panel.preferredSize = Dimension(220, 1)

        panel.add(title("EDITOR DE PDF", 18f))
        panel.add(Box.createVerticalStrut(12))
        panel.add(button("Adicionar imagem") { chooseImages() })
        panel.add(Box.createVerticalStrut(7))
        panel.add(button("Adicionar PDF") { choosePdfs() })
        panel.add(Box.createVerticalStrut(7))
        panel.add(button("Selecionar corte") { startCrop() })
        panel.add(Box.createVerticalStrut(7))
        panel.add(button("Remover recorte") { removeCrop() })
        panel.add(Box.createVerticalStrut(7))
        panel.add(button("Girar à esquerda") { rotateSelected(-90) })
        panel.add(Box.createVerticalStrut(7))
        panel.add(button("Girar à direita") { rotateSelected(90) })
        panel.add(Box.createVerticalStrut(7))
        panel.add(button("Excluir página", danger = true) { deleteSelected() })
        panel.add(Box.createVerticalStrut(7))
        panel.add(button("Desfazer  Ctrl+Z") { undo() })
        panel.add(Box.createVerticalStrut(18))

        val drop = JLabel("<html><center>Arraste imagens<br>ou PDFs aqui</center></html>", SwingConstants.CENTER)
        drop.foreground = PDF_MUTED
        drop.background = PDF_PANEL_2
        drop.isOpaque = true
        drop.border = BorderFactory.createDashedBorder(PDF_BORDER, 2f, 5f, 3f, false)
        drop.maximumSize = Dimension(Int.MAX_VALUE, 150)
        drop.preferredSize = Dimension(190, 150)
        installFileDrop(drop)
        panel.add(drop)
        panel.add(Box.createVerticalGlue())
        return panel
    }

    private fun buildCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 8))
        panel.background = PDF_BG
        panel.border = EmptyBorder(14, 14, 14, 14)

        preview.background = Color(14, 18, 23)
        preview.border = BorderFactory.createLineBorder(PDF_BORDER)
        preview.onCrop = { crop ->
            val idx = thumbnailList.selectedIndex
            if (idx >= 0) {
                pushUndo()
                pages[idx].crop = crop
                refreshAll()
            }
        }
        panel.add(preview, BorderLayout.CENTER)

        val bottom = JPanel(BorderLayout())
        bottom.background = PDF_BG
        val zoomBar = JPanel(FlowLayout(FlowLayout.CENTER, 7, 4))
        zoomBar.background = PDF_BG
        zoomBar.add(smallButton("−") { setZoom(zoom - 0.15) })
        zoomLabel.foreground = PDF_TEXT
        zoomLabel.preferredSize = Dimension(58, 28)
        zoomLabel.horizontalAlignment = SwingConstants.CENTER
        zoomBar.add(zoomLabel)
        zoomBar.add(smallButton("+") { setZoom(zoom + 0.15) })
        zoomBar.add(smallButton("Ajustar") { setZoom(1.0) })
        bottom.add(zoomBar, BorderLayout.NORTH)

        val thumbsPanel = JPanel(BorderLayout())
        thumbsPanel.background = PDF_PANEL
        thumbsPanel.border = EmptyBorder(8, 8, 8, 8)
        val info = JPanel(BorderLayout())
        info.background = PDF_PANEL
        val reorderHint = JLabel("Arraste para reordenar")
        reorderHint.foreground = PDF_MUTED
        status.foreground = PDF_TEXT
        info.add(reorderHint, BorderLayout.WEST)
        info.add(status, BorderLayout.EAST)
        thumbsPanel.add(info, BorderLayout.NORTH)

        thumbnailList.layoutOrientation = JList.HORIZONTAL_WRAP
        thumbnailList.visibleRowCount = 1
        thumbnailList.fixedCellWidth = 118
        thumbnailList.fixedCellHeight = 142
        thumbnailList.background = PDF_PANEL
        thumbnailList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        thumbnailList.cellRenderer = ThumbnailRenderer()
        thumbnailList.dragEnabled = true
        thumbnailList.dropMode = DropMode.INSERT
        thumbnailList.transferHandler = PageReorderTransferHandler()
        thumbnailList.addListSelectionListener {
            if (!it.valueIsAdjusting) refreshPreview()
        }
        val scroller = JScrollPane(thumbnailList)
        scroller.border = null
        scroller.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS
        scroller.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
        scroller.preferredSize = Dimension(600, 175)
        thumbsPanel.add(scroller, BorderLayout.CENTER)
        bottom.add(thumbsPanel, BorderLayout.CENTER)

        panel.add(bottom, BorderLayout.SOUTH)
        return panel
    }

    private fun buildRightPanel(): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.background = PDF_PANEL
        panel.border = EmptyBorder(16, 14, 16, 14)
        panel.preferredSize = Dimension(245, 1)

        panel.add(title("CAPA E EXPORTAÇÃO", 16f))
        panel.add(Box.createVerticalStrut(14))
        includeCover.foreground = PDF_TEXT
        includeCover.background = PDF_PANEL
        includeCover.isFocusPainted = false
        includeCover.alignmentX = LEFT_ALIGNMENT
        panel.add(includeCover)
        panel.add(Box.createVerticalStrut(9))

        coverPreview.background = Color(245, 245, 245)
        coverPreview.foreground = Color(45, 45, 45)
        coverPreview.isOpaque = true
        coverPreview.border = BorderFactory.createLineBorder(PDF_BORDER)
        coverPreview.minimumSize = Dimension(205, 130)
        coverPreview.maximumSize = Dimension(205, 160)
        coverPreview.preferredSize = Dimension(205, 150)
        coverPreview.alignmentX = LEFT_ALIGNMENT
        panel.add(coverPreview)
        panel.add(Box.createVerticalStrut(8))
        panel.add(button("Trocar capa") { changeCover() })
        panel.add(Box.createVerticalStrut(6))
        panel.add(button("Restaurar") { restoreCover() })
        panel.add(Box.createVerticalStrut(18))

        val qLabel = JLabel("Qualidade")
        qLabel.foreground = PDF_TEXT
        qLabel.alignmentX = LEFT_ALIGNMENT
        panel.add(qLabel)
        panel.add(Box.createVerticalStrut(5))
        quality.maximumSize = Dimension(Int.MAX_VALUE, 34)
        quality.alignmentX = LEFT_ALIGNMENT
        panel.add(quality)
        panel.add(Box.createVerticalStrut(12))

        val note = JLabel("<html>PDF sem giro/recorte é mantido vetorial.<br>Páginas editadas são renderizadas conforme a qualidade escolhida.</html>")
        note.foreground = PDF_MUTED
        note.font = note.font.deriveFont(11f)
        note.alignmentX = LEFT_ALIGNMENT
        panel.add(note)
        panel.add(Box.createVerticalGlue())

        val export = JButton("GERAR PDF")
        export.background = PDF_BLUE
        export.foreground = Color.WHITE
        export.font = export.font.deriveFont(Font.BOLD, 16f)
        export.isFocusPainted = false
        export.maximumSize = Dimension(Int.MAX_VALUE, 48)
        export.alignmentX = LEFT_ALIGNMENT
        export.addActionListener { exportPdf() }
        panel.add(export)
        return panel
    }

    private fun title(text: String, size: Float): JLabel = JLabel(text).apply {
        foreground = PDF_TEXT
        font = font.deriveFont(Font.BOLD, size)
        alignmentX = LEFT_ALIGNMENT
    }

    private fun button(text: String, danger: Boolean = false, action: () -> Unit): JButton = JButton(text).apply {
        background = if (danger) PDF_RED else PDF_PANEL_2
        foreground = Color.WHITE
        isFocusPainted = false
        horizontalAlignment = SwingConstants.LEFT
        maximumSize = Dimension(Int.MAX_VALUE, 38)
        alignmentX = LEFT_ALIGNMENT
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(if (danger) PDF_RED else PDF_BORDER),
            EmptyBorder(8, 10, 8, 10)
        )
        addActionListener { action() }
    }

    private fun smallButton(text: String, action: () -> Unit): JButton = JButton(text).apply {
        background = PDF_PANEL_2
        foreground = PDF_TEXT
        isFocusPainted = false
        addActionListener { action() }
    }

    private fun chooseImages() {
        val fc = JFileChooser()
        fc.isMultiSelectionEnabled = true
        fc.fileFilter = FileNameExtensionFilter("Imagens", "jpg", "jpeg", "png", "webp", "bmp", "tiff", "tif")
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) importFiles(fc.selectedFiles.toList())
    }

    private fun choosePdfs() {
        val fc = JFileChooser()
        fc.isMultiSelectionEnabled = true
        fc.fileFilter = FileNameExtensionFilter("Arquivos PDF", "pdf")
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) importFiles(fc.selectedFiles.toList())
    }

    private fun importFiles(files: List<File>) {
        if (files.isEmpty()) return
        val valid = files.filter { it.exists() && it.isFile }
        if (valid.isEmpty()) return
        pushUndo()
        var added = 0
        for (f in valid) {
            try {
                when (f.extension.lowercase()) {
                    "pdf" -> Loader.loadPDF(f).use { doc ->
                        if (doc.numberOfPages == 0) throw IllegalArgumentException("PDF sem páginas")
                        for (i in 0 until doc.numberOfPages) {
                            pages += PdfPageData(kind = PdfItemKind.PDF, path = f.absolutePath, pageNo = i)
                            added++
                        }
                    }
                    "jpg", "jpeg", "png", "webp", "bmp", "tiff", "tif" -> {
                        val image = ImageIO.read(f) ?: throw IllegalArgumentException("Imagem inválida")
                        image.flush()
                        pages += PdfPageData(kind = PdfItemKind.IMAGE, path = f.absolutePath)
                        added++
                    }
                }
            } catch (e: Exception) {
                showError("Não foi possível importar ${f.name}: ${e.message}")
            }
        }
        if (added == 0 && undo.isNotEmpty()) undo.removeLast()
        refreshAll(selectLast = true)
    }

    private fun startCrop() {
        val idx = thumbnailList.selectedIndex
        if (idx < 0) return
        preview.cropMode = true
        preview.cursor = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
    }

    private fun removeCrop() {
        val idx = thumbnailList.selectedIndex
        if (idx < 0 || pages[idx].crop == null) return
        pushUndo()
        pages[idx].crop = null
        refreshAll()
    }

    private fun rotateSelected(delta: Int) {
        val idx = thumbnailList.selectedIndex
        if (idx < 0) return
        pushUndo()
        val p = pages[idx]
        if (p.crop != null) p.crop = null
        p.rotation = ((p.rotation + delta) % 360 + 360) % 360
        refreshAll()
    }

    private fun deleteSelected() {
        val idx = thumbnailList.selectedIndex
        if (idx < 0) return
        pushUndo()
        pages.removeAt(idx)
        val newIndex = when {
            pages.isEmpty() -> -1
            idx < pages.size -> idx
            else -> pages.lastIndex
        }
        refreshAll(selectIndex = newIndex)
    }

    private fun setZoom(value: Double) {
        zoom = value.coerceIn(0.55, 3.0)
        zoomLabel.text = "${(zoom * 100).roundToInt()}%"
        refreshPreview()
    }

    private fun pushUndo() {
        undo.addLast(EditorSnapshot(pages.map { it.copyDeep() }, thumbnailList.selectedIndex))
        while (undo.size > 30) undo.removeFirst()
    }

    private fun undo() {
        val snap = undo.removeLastOrNull() ?: return
        pages.clear()
        pages.addAll(snap.pages.map { it.copyDeep() })
        refreshAll(selectIndex = snap.selected.coerceAtMost(pages.lastIndex))
    }

    private fun refreshAll(selectLast: Boolean = false, selectIndex: Int? = null) {
        val currentUid = thumbnailList.selectedValue?.uid
        thumbnailModel.clear()
        pages.forEach { thumbnailModel.addElement(it) }
        status.text = "${pages.size} ${if (pages.size == 1) "página" else "páginas"}"
        val idx = when {
            selectIndex != null -> selectIndex
            selectLast && pages.isNotEmpty() -> pages.lastIndex
            currentUid != null -> pages.indexOfFirst { it.uid == currentUid }
            pages.isNotEmpty() -> 0
            else -> -1
        }
        if (idx in pages.indices) thumbnailList.selectedIndex = idx else thumbnailList.clearSelection()
        thumbnailList.repaint()
        refreshPreview()
        refreshCoverPreview()
    }

    private fun refreshPreview() {
        val idx = thumbnailList.selectedIndex
        val image = if (idx in pages.indices) runCatching { renderPage(pages[idx], 115) }.getOrNull() else null
        preview.setImage(image, zoom)
    }

    private fun renderPage(page: PdfPageData, dpi: Int): BufferedImage {
        val source = when (page.kind) {
            PdfItemKind.IMAGE -> ImageIO.read(File(page.path)) ?: error("Imagem não suportada")
            PdfItemKind.PDF -> Loader.loadPDF(File(page.path)).use { doc ->
                PDFRenderer(doc).renderImageWithDPI(page.pageNo ?: 0, dpi.toFloat())
            }
        }
        var out = source
        page.crop?.let { c ->
            val x = (c.x * out.width).roundToInt().coerceIn(0, out.width - 1)
            val y = (c.y * out.height).roundToInt().coerceIn(0, out.height - 1)
            val w = (c.w * out.width).roundToInt().coerceIn(1, out.width - x)
            val h = (c.h * out.height).roundToInt().coerceIn(1, out.height - y)
            out = copyImage(out.getSubimage(x, y, w, h))
        }
        if (page.rotation != 0) out = rotateImage(out, page.rotation)
        return out
    }

    private fun createThumbnail(page: PdfPageData): ImageIcon {
        val src = renderPage(page, 60)
        val scale = min(100.0 / src.width, 105.0 / src.height).coerceAtMost(1.0)
        val w = max(1, (src.width * scale).roundToInt())
        val h = max(1, (src.height * scale).roundToInt())
        val scaled = src.getScaledInstance(w, h, Image.SCALE_SMOOTH)
        return ImageIcon(scaled)
    }

    private fun changeCover() {
        val fc = JFileChooser()
        fc.fileFilter = FileNameExtensionFilter("Imagem", "jpg", "jpeg", "png", "webp", "bmp", "tiff", "tif")
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return
        try {
            val image = ImageIO.read(fc.selectedFile) ?: error("Imagem inválida")
            ImageIO.write(image, "png", customCoverFile)
            customCover = customCoverFile
            saveConfig()
            refreshCoverPreview()
        } catch (e: Exception) {
            showError("Não foi possível trocar a capa: ${e.message}")
        }
    }

    private fun restoreCover() {
        customCover = null
        if (customCoverFile.exists()) customCoverFile.delete()
        saveConfig()
        refreshCoverPreview()
    }

    private fun defaultCoverImage(): BufferedImage {
        val img = BufferedImage(1240, 1754, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color(5, 45, 87)
        g.fillRect(0, 0, img.width, img.height)
        g.color = Color(255, 182, 49)
        g.fillRect(90, 120, 20, 1510)
        g.color = Color.WHITE
        g.font = Font("SansSerif", Font.BOLD, 82)
        g.drawString("RADAR DE NOTÍCIAS", 150, 360)
        g.font = Font("SansSerif", Font.PLAIN, 38)
        g.color = Color(205, 220, 238)
        g.drawString("Monitor de Notícias", 154, 430)
        g.dispose()
        return img
    }

    private fun currentCoverImage(): BufferedImage {
        val f = customCover
        return if (f != null && f.exists()) ImageIO.read(f) ?: defaultCoverImage() else defaultCoverImage()
    }

    private fun refreshCoverPreview() {
        val img = runCatching { currentCoverImage() }.getOrElse { defaultCoverImage() }
        val scale = min(195.0 / img.width, 135.0 / img.height)
        val w = max(1, (img.width * scale).roundToInt())
        val h = max(1, (img.height * scale).roundToInt())
        coverPreview.icon = ImageIcon(img.getScaledInstance(w, h, Image.SCALE_SMOOTH))
        coverPreview.text = ""
    }

    private fun exportPdf() {
        if (pages.isEmpty() && !includeCover.isSelected) {
            showError("Adicione ao menos uma página ou mantenha a capa ativada.")
            return
        }
        val fc = JFileChooser()
        fc.dialogTitle = "Salvar PDF"
        fc.selectedFile = File(if (includeCover.isSelected) "RADAR DE NOTICIAS.pdf" else "documento.pdf")
        fc.fileFilter = FileNameExtensionFilter("PDF", "pdf")
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return
        var outFile = fc.selectedFile
        if (!outFile.name.lowercase().endsWith(".pdf")) outFile = File(outFile.parentFile, outFile.name + ".pdf")

        val q = quality.selectedItem as? ExportQuality ?: ExportQuality.HIGH
        try {
            cursor = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR)
            PDDocument().use { out ->
                if (includeCover.isSelected) appendRasterImage(out, currentCoverImage(), q)
                pages.forEach { p ->
                    if (p.kind == PdfItemKind.PDF && p.rotation == 0 && p.crop == null) appendVectorPdfPage(out, p)
                    else appendRasterImage(out, renderPage(p, q.dpi), q)
                }
                out.save(outFile)
            }
            val options = arrayOf("OK", "Abrir pasta")
            val choice = JOptionPane.showOptionDialog(
                this,
                "PDF gerado com sucesso!\n${outFile.absolutePath}",
                "Exportação concluída",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.INFORMATION_MESSAGE,
                null,
                options,
                options[0]
            )
            if (choice == 1) Desktop.getDesktop().open(outFile.parentFile)
        } catch (e: Exception) {
            showError("Erro ao gerar PDF: ${e.message}")
        } finally {
            cursor = Cursor.getDefaultCursor()
        }
    }

    private fun appendVectorPdfPage(out: PDDocument, data: PdfPageData) {
        Loader.loadPDF(File(data.path)).use { src ->
            val srcPage = src.getPage(data.pageNo ?: 0)
            val box = srcPage.cropBox ?: srcPage.mediaBox
            val margin = 2f
            val pageWidth = 595.276f
            val contentW = box.width
            val contentH = box.height
            val pageHeight = pageWidth * contentH / contentW
            val target = PDPage(PDRectangle(pageWidth, pageHeight))
            out.addPage(target)
            val form = LayerUtility(out).importPageAsForm(src, data.pageNo ?: 0)
            val availW = pageWidth - margin * 2
            val availH = pageHeight - margin * 2
            val scale = min(availW / contentW, availH / contentH)
            val x = (pageWidth - contentW * scale) / 2f
            val y = (pageHeight - contentH * scale) / 2f
            PDPageContentStream(out, target).use { cs ->
                cs.transform(Matrix.getTranslateInstance(x, y))
                cs.transform(Matrix.getScaleInstance(scale, scale))
                cs.drawForm(form)
            }
        }
    }

    private fun appendRasterImage(out: PDDocument, image: BufferedImage, q: ExportQuality) {
        val margin = 2f
        val pageWidth = 595.276f
        val pageHeight = pageWidth * image.height.toFloat() / image.width.toFloat()
        val page = PDPage(PDRectangle(pageWidth, pageHeight))
        out.addPage(page)
        val rgb = ensureRgb(image)
        val pdImage = JPEGFactory.createFromImage(out, rgb, 0.97f, q.dpi)
        val availW = pageWidth - margin * 2
        val availH = pageHeight - margin * 2
        val scale = min(availW / image.width, availH / image.height)
        val drawW = image.width * scale
        val drawH = image.height * scale
        PDPageContentStream(out, page).use { cs ->
            cs.drawImage(pdImage, (pageWidth - drawW) / 2f, (pageHeight - drawH) / 2f, drawW, drawH)
        }
    }

    private fun createPortableDirs() {
        try {
            if (!dataDir.exists() && !dataDir.mkdirs()) error("Não foi possível criar ${dataDir.absolutePath}")
            if (!logDir.exists()) logDir.mkdirs()
            val probe = File(dataDir, ".write-test")
            probe.writeText("ok")
            probe.delete()
        } catch (e: Exception) {
            SwingUtilities.invokeLater {
                showError("O programa está em uma pasta sem permissão de gravação. Mova a versão portable para uma pasta onde seja possível salvar arquivos.")
            }
        }
    }

    private fun loadConfig() {
        runCatching {
            if (!configFile.exists()) return@runCatching
            val obj = JSONObject(configFile.readText(Charsets.UTF_8))
            val rel = obj.optString("custom_cover", "")
            if (rel.isNotBlank()) {
                val f = File(portableDir, rel.replace('/', File.separatorChar))
                if (f.exists()) customCover = f
            }
        }
    }

    private fun saveConfig() {
        runCatching {
            val obj = JSONObject()
            if (customCover != null) obj.put("custom_cover", "data/capa_padrao_usuario.png")
            configFile.writeText(obj.toString(2), Charsets.UTF_8)
        }.onFailure { showError("Não foi possível salvar a configuração da capa: ${it.message}") }
    }

    private fun resolvePortableDir(): File {
        val appPath = System.getProperty("jpackage.app-path")
        if (!appPath.isNullOrBlank()) {
            val exe = File(appPath)
            if (exe.exists()) return exe.parentFile ?: File(System.getProperty("user.dir"))
        }
        return File(System.getProperty("user.dir")).absoluteFile
    }

    private fun installKeyboardShortcuts() {
        val im = getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        val am = actionMap
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "undo")
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete")
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), "save")
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK), "openPdf")
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK), "fit")
        am.put("undo", swingAction { undo() })
        am.put("delete", swingAction { deleteSelected() })
        am.put("save", swingAction { exportPdf() })
        am.put("openPdf", swingAction { choosePdfs() })
        am.put("fit", swingAction { setZoom(1.0) })
    }

    private fun swingAction(block: () -> Unit) = object : AbstractAction() {
        override fun actionPerformed(e: ActionEvent?) = block()
    }

    private fun installFileDrop(component: Component) {
        DropTarget(component, object : DropTargetAdapter() {
            override fun drop(dtde: DropTargetDropEvent) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY)
                    @Suppress("UNCHECKED_CAST")
                    val files = dtde.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                    importFiles(files)
                    dtde.dropComplete(true)
                } catch (e: Exception) {
                    dtde.dropComplete(false)
                    showError("Falha ao receber arquivos: ${e.message}")
                }
            }
        })
    }

    private fun showError(message: String) {
        JOptionPane.showMessageDialog(this, message, "Editor de PDF", JOptionPane.ERROR_MESSAGE)
    }

    private inner class ThumbnailRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
            val label = super.getListCellRendererComponent(list, "", index, isSelected, cellHasFocus) as JLabel
            val p = value as? PdfPageData
            label.horizontalAlignment = SwingConstants.CENTER
            label.verticalAlignment = SwingConstants.CENTER
            label.horizontalTextPosition = SwingConstants.CENTER
            label.verticalTextPosition = SwingConstants.BOTTOM
            label.text = "Página ${index + 1}"
            label.foreground = PDF_TEXT
            label.background = if (isSelected) Color(18, 88, 168) else PDF_PANEL_2
            label.isOpaque = true
            label.border = BorderFactory.createEmptyBorder(6, 6, 6, 6)
            label.icon = p?.let { runCatching { createThumbnail(it) }.getOrNull() }
            return label
        }
    }

    private inner class PageReorderTransferHandler : TransferHandler() {
        private var sourceIndex = -1
        override fun getSourceActions(c: JComponent?): Int = MOVE
        override fun createTransferable(c: JComponent?): java.awt.datatransfer.Transferable {
            sourceIndex = thumbnailList.selectedIndex
            return StringSelection(sourceIndex.toString())
        }
        override fun canImport(support: TransferSupport): Boolean = support.isDrop && support.isDataFlavorSupported(DataFlavor.stringFlavor)
        override fun importData(support: TransferSupport): Boolean {
            if (!canImport(support) || sourceIndex !in pages.indices) return false
            val dl = support.dropLocation as? JList.DropLocation ?: return false
            var target = dl.index.coerceIn(0, pages.size)
            if (target == sourceIndex || target == sourceIndex + 1) return false
            pushUndo()
            val item = pages.removeAt(sourceIndex)
            if (target > sourceIndex) target--
            pages.add(target.coerceIn(0, pages.size), item)
            refreshAll(selectIndex = pages.indexOfFirst { it.uid == item.uid })
            return true
        }
    }
}

private class PreviewPanel : JPanel() {
    var cropMode: Boolean = false
    var onCrop: ((NormalizedCrop) -> Unit)? = null
    private var image: BufferedImage? = null
    private var zoom = 1.0
    private var dragStart: Point? = null
    private var dragEnd: Point? = null
    private var imageRect = Rectangle()

    init {
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (cropMode && imageRect.contains(e.point)) {
                    dragStart = clamp(e.point)
                    dragEnd = dragStart
                    repaint()
                }
            }
            override fun mouseReleased(e: MouseEvent) {
                val start = dragStart ?: return
                val end = clamp(e.point)
                dragEnd = end
                if (cropMode && imageRect.width > 0 && imageRect.height > 0) {
                    val x1 = min(start.x, end.x).coerceIn(imageRect.x, imageRect.x + imageRect.width)
                    val y1 = min(start.y, end.y).coerceIn(imageRect.y, imageRect.y + imageRect.height)
                    val x2 = max(start.x, end.x).coerceIn(imageRect.x, imageRect.x + imageRect.width)
                    val y2 = max(start.y, end.y).coerceIn(imageRect.y, imageRect.y + imageRect.height)
                    if (x2 - x1 > 4 && y2 - y1 > 4) {
                        onCrop?.invoke(
                            NormalizedCrop(
                                (x1 - imageRect.x).toDouble() / imageRect.width,
                                (y1 - imageRect.y).toDouble() / imageRect.height,
                                (x2 - x1).toDouble() / imageRect.width,
                                (y2 - y1).toDouble() / imageRect.height
                            )
                        )
                    }
                }
                cropMode = false
                cursor = Cursor.getDefaultCursor()
                dragStart = null
                dragEnd = null
                repaint()
            }
        })
        addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseDragged(e: MouseEvent) {
                if (cropMode && dragStart != null) {
                    dragEnd = clamp(e.point)
                    repaint()
                }
            }
        })
    }

    fun setImage(img: BufferedImage?, zoom: Double) {
        image = img
        this.zoom = zoom
        dragStart = null
        dragEnd = null
        repaint()
    }

    private fun clamp(p: Point): Point = Point(
        p.x.coerceIn(imageRect.x, imageRect.x + imageRect.width),
        p.y.coerceIn(imageRect.y, imageRect.y + imageRect.height)
    )

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val img = image ?: run {
            g.color = PDF_MUTED
            g.font = g.font.deriveFont(18f)
            g.drawString("Adicione uma imagem ou PDF para começar", 30, 50)
            return
        }
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        val fit = min((width - 30).toDouble() / img.width, (height - 30).toDouble() / img.height).coerceAtLeast(0.05)
        val scale = fit * zoom
        val w = max(1, (img.width * scale).roundToInt())
        val h = max(1, (img.height * scale).roundToInt())
        val x = (width - w) / 2
        val y = (height - h) / 2
        imageRect = Rectangle(x, y, w, h)
        g2.color = Color.WHITE
        g2.fillRect(x - 1, y - 1, w + 2, h + 2)
        g2.drawImage(img, x, y, w, h, null)
        val a = dragStart
        val b = dragEnd
        if (a != null && b != null) {
            val rx = min(a.x, b.x)
            val ry = min(a.y, b.y)
            val rw = kotlin.math.abs(a.x - b.x)
            val rh = kotlin.math.abs(a.y - b.y)
            g2.color = Color(35, 128, 255, 55)
            g2.fillRect(rx, ry, rw, rh)
            g2.color = PDF_BLUE
            g2.stroke = BasicStroke(2f)
            g2.drawRect(rx, ry, rw, rh)
        }
    }
}

private fun rotateImage(src: BufferedImage, degrees: Int): BufferedImage {
    val normalized = ((degrees % 360) + 360) % 360
    if (normalized == 0) return src
    val swap = normalized == 90 || normalized == 270
    val w = if (swap) src.height else src.width
    val h = if (swap) src.width else src.height
    val dst = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    val g = dst.createGraphics()
    g.color = Color.WHITE
    g.fillRect(0, 0, w, h)
    val tx = AffineTransform()
    when (normalized) {
        90 -> { tx.translate(w.toDouble(), 0.0); tx.rotate(Math.toRadians(90.0)) }
        180 -> { tx.translate(w.toDouble(), h.toDouble()); tx.rotate(Math.toRadians(180.0)) }
        270 -> { tx.translate(0.0, h.toDouble()); tx.rotate(Math.toRadians(270.0)) }
    }
    g.drawImage(src, tx, null)
    g.dispose()
    return dst
}

private fun copyImage(src: BufferedImage): BufferedImage {
    val dst = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_RGB)
    val g = dst.createGraphics()
    g.color = Color.WHITE
    g.fillRect(0, 0, dst.width, dst.height)
    g.drawImage(src, 0, 0, null)
    g.dispose()
    return dst
}

private fun ensureRgb(src: BufferedImage): BufferedImage = if (src.type == BufferedImage.TYPE_INT_RGB) src else copyImage(src)
