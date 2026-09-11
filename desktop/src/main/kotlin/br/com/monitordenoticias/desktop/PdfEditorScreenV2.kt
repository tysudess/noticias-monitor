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
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
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
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Base64
import java.util.Date
import java.util.UUID
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val V2_BG = Color(4, 19, 35)
private val V2_BG_2 = Color(7, 29, 51)
private val V2_BORDER = Color(43, 79, 112)
private val V2_BORDER_SOFT = Color(34, 63, 91)
private val V2_TEXT = Color(241, 246, 255)
private val V2_MUTED = Color(151, 177, 207)
private val V2_BLUE = Color(15, 127, 255)
private val V2_BLUE_2 = Color(0, 93, 236)
private val V2_GREEN = Color(14, 202, 103)
private val V2_YELLOW = Color(255, 188, 0)
private val V2_RED = Color(235, 57, 70)
private val V2_CYAN = Color(27, 207, 217)
private val V2_PURPLE = Color(130, 82, 255)

private enum class PdfV2ItemKind { IMAGE, PDF, BLANK }
private enum class PdfV2ExportQuality(val label: String, val dpi: Int) {
    HIGH("Alta (recomendado)", 450), MEDIUM("Média", 300), COMPACT("Compacta", 220);
    override fun toString(): String = label
}
private data class PdfV2Crop(val x: Double, val y: Double, val w: Double, val h: Double)
private data class PdfV2Page(val uid: String = UUID.randomUUID().toString(), val kind: PdfV2ItemKind, val path: String = "", val pageNo: Int? = null, var rotation: Int = 0, var flipX: Boolean = false, var crop: PdfV2Crop? = null) { fun copyDeep() = copy(crop = crop?.copy()) }
private data class PdfV2Snapshot(val pages: List<PdfV2Page>, val selected: Int)

@Composable
fun PdfEditorScreenV2(onExit: () -> Unit = {}) { SwingPanel(modifier = Modifier.fillMaxSize(), factory = { PdfEditorV2Panel(onExit) }) }

private class PdfEditorV2Panel(private val onExit: () -> Unit) : JPanel(BorderLayout()) {
    private val pages = mutableListOf<PdfV2Page>()
    private val undo = ArrayDeque<PdfV2Snapshot>()
    private val redo = ArrayDeque<PdfV2Snapshot>()
    private val thumbModel = DefaultListModel<PdfV2Page>()
    private val thumbList = JList(thumbModel)
    private val preview = PdfV2PreviewPanel()
    private val pageCount = JLabel("0 páginas")
    private val zoomLabel = JLabel("100%")
    private val includeCover = JToggleButton("●  Incluir capa padrão", true)
    private val coverPreview = JLabel("", SwingConstants.CENTER)
    private val titleField = JTextField()
    private val authorField = JTextField()
    private val quality = JComboBox(PdfV2ExportQuality.entries.toTypedArray())
    private val clockDate = JLabel()
    private val clockTime = JLabel()
    private val viewThumb = JToggleButton("▦  Miniaturas", true)
    private val viewList = JToggleButton("☷  Lista", false)
    private var zoom = 1.0
    private var customCover: File? = null
    private val portableDir = resolvePortableDir()
    private val dataDir = File(portableDir, "data")
    private val configFile = File(dataDir, "config.json")
    private val customCoverFile = File(dataDir, "capa_padrao_usuario.png")
    private val hdDefaultCoverFile = File(dataDir, "capa_padrao.png")

    init {
        background = V2_BG; border = EmptyBorder(0,0,0,0); isFocusable = true
        createPortableDirs(); loadConfig()
        add(buildWorkspace(), BorderLayout.CENTER)
        installKeyboardShortcuts(); installFileDrop(this); refreshAll(); Timer(1000) { updateClock() }.start(); updateClock()
    }

    private fun buildHeader(): JComponent {
        val header = object : JPanel(BorderLayout()) {
            override fun paintComponent(g: Graphics) {
                val g2 = g as Graphics2D
                g2.paint = GradientPaint(0f,0f,Color(8,41,76),width.toFloat(),0f,Color(4,21,39)); g2.fillRect(0,0,width,height)
                g2.color = Color(16,61,108,160); g2.fillPolygon(Polygon(intArrayOf(570,680,960,720),intArrayOf(0,0,height,height),4))
                g2.color = Color(11,84,161,150); g2.stroke = BasicStroke(1.2f); g2.drawLine(630,0,570,height)
            }
        }
        header.isOpaque=false; header.preferredSize=Dimension(1,88); header.border=BorderFactory.createMatteBorder(0,0,1,0,V2_BORDER_SOFT)
        val left=JPanel(FlowLayout(FlowLayout.LEFT,12,8)).apply{isOpaque=false}
        val pdfIcon=object:JPanel(){init{preferredSize=Dimension(58,68);isOpaque=false};override fun paintComponent(g:Graphics){val g2=g as Graphics2D;g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g2.color=Color(244,247,252);g2.fillRoundRect(6,4,42,58,8,8);g2.color=Color(31,132,255);g2.stroke=BasicStroke(2.4f);g2.drawRoundRect(6,4,42,58,8,8);g2.color=Color(238,57,61);g2.fillRoundRect(23,35,34,23,6,6);g2.color=Color.WHITE;g2.font=Font("SansSerif",Font.BOLD,12);g2.drawString("PDF",28,51)}}
        left.add(pdfIcon)
        left.add(JPanel().apply{layout=BoxLayout(this,BoxLayout.Y_AXIS);isOpaque=false;add(label("CENTRAL DE INTELIGÊNCIA DE MÍDIA",13f,Font.BOLD,Color(65,157,255)));add(label("Editor de PDF",27f,Font.BOLD,V2_TEXT));add(label("Monte, reorganize, recorte e exporte PDFs e imagens",13f,Font.PLAIN,V2_MUTED))})
        header.add(left,BorderLayout.WEST)
        val right=JPanel(FlowLayout(FlowLayout.RIGHT,10,18)).apply{isOpaque=false;border=EmptyBorder(0,0,0,10)}
        right.add(statusPill("⬟  Proxy desativado",V2_YELLOW));right.add(statusPill("◷  Automação ativa",V2_GREEN))
        right.add(JPanel().apply{layout=BoxLayout(this,BoxLayout.Y_AXIS);isOpaque=false;border=EmptyBorder(0,22,0,0);clockDate.foreground=Color(190,207,228);clockDate.font=clockDate.font.deriveFont(13f);clockDate.alignmentX=RIGHT_ALIGNMENT;clockTime.foreground=V2_TEXT;clockTime.font=clockTime.font.deriveFont(Font.BOLD,18f);clockTime.alignmentX=RIGHT_ALIGNMENT;add(clockDate);add(clockTime)})
        right.add(styledSquareButton("⚙") { onExit() })
        header.add(right,BorderLayout.EAST);return header
    }

    private fun buildWorkspace():JComponent=JPanel(BorderLayout(4,0)).apply{background=V2_BG;border=EmptyBorder(4,4,4,4);add(buildLeftPanel(),BorderLayout.WEST);add(buildCenterPanel(),BorderLayout.CENTER);add(buildRightPanel(),BorderLayout.EAST)}

    private fun buildLeftPanel():JComponent {
        val p=RoundedPanel(16,V2_BG_2,V2_BORDER_SOFT).apply{layout=BoxLayout(this,BoxLayout.Y_AXIS);border=EmptyBorder(7,5,7,5);preferredSize=Dimension(150,1);minimumSize=Dimension(146,1)}
        p.add(navButton("⇧","Arquivos","Img/PDF",V2_BLUE,true){chooseAll()});p.add(gap(6));p.add(navButton("PDF","PDF",null,Color(232,74,78)){choosePdfs()});p.add(gap(6));p.add(navButton("✂","Corte",null,V2_PURPLE){startCrop()});p.add(gap(6));p.add(navButton("▣","Remover",null,V2_RED){deleteSelected()});p.add(gap(6));p.add(navButton("⟳","Girar",null,Color(61,157,255)){showTransformMenu()});p.add(gap(6));p.add(navButton("▤","Criar",null,Color(84,153,230)){createBlankPage()});p.add(gap(6));p.add(navButton("▥","Excluir",null,V2_RED){deleteSelected()});p.add(gap(6));p.add(navButton("▧","Capa",null,Color(25,201,142)){changeCover()});p.add(gap(6));p.add(navButton("↕","Ordem",null,V2_CYAN){focusReorder()});p.add(gap(12))
        val drop=RoundedPanel(14,Color(7,27,47),Color(84,126,169)).apply{layout=BoxLayout(this,BoxLayout.Y_AXIS);maximumSize=Dimension(Int.MAX_VALUE,124);preferredSize=Dimension(138,124);border=EmptyBorder(7,5,7,5)}
        listOf(label("☁",24f,Font.BOLD,Color(153,195,239)),label("Arraste",11f,Font.PLAIN,Color(202,219,238)),label("PDF/imagem",11f,Font.PLAIN,Color(202,219,238)),label("ou selecione",9.5f,Font.PLAIN,V2_MUTED)).forEach{it.alignmentX=CENTER_ALIGNMENT;drop.add(it)}
        drop.add(gap(8));drop.add(blueButton("Selecionar"){chooseAll()}.apply{alignmentX=CENTER_ALIGNMENT;maximumSize=Dimension(128,32)});installFileDrop(drop);p.add(drop);return p
    }

    private fun buildCenterPanel():JComponent {
        val p = RoundedPanel(16, V2_BG_2, V2_BORDER_SOFT).apply {
            layout = BorderLayout(0, 8)
            border = EmptyBorder(9, 9, 9, 9)
        }

        val titleBar = JPanel(BorderLayout()).apply { isOpaque = false }
        titleBar.add(
            JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                add(label("▣  Visualização do documento", 16f, Font.BOLD, V2_TEXT))
                add(label("Adicione uma imagem ou PDF para começar a editar", 13f, Font.PLAIN, V2_MUTED))
            },
            BorderLayout.WEST
        )
        val modes = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply { isOpaque = false }
        styleModeToggle(viewThumb, true)
        styleModeToggle(viewList, false)
        ButtonGroup().apply { add(viewThumb); add(viewList) }
        viewThumb.addActionListener { setThumbMode(true) }
        viewList.addActionListener { setThumbMode(false) }
        modes.add(viewThumb)
        modes.add(viewList)
        titleBar.add(modes, BorderLayout.EAST)
        p.add(titleBar, BorderLayout.NORTH)

        val body = JPanel(BorderLayout(5, 0)).apply { isOpaque = false }

        val thumbs = RoundedPanel(12, Color(8, 28, 48), V2_BORDER_SOFT).apply {
            layout = BorderLayout(0, 8)
            border = EmptyBorder(8, 8, 8, 8)
            preferredSize = Dimension(96, 1)
            minimumSize = Dimension(90, 320)
        }
        val th = JPanel(BorderLayout()).apply { isOpaque = false }
        th.add(label("▦", 14f, Font.BOLD, V2_TEXT), BorderLayout.WEST)
        pageCount.foreground = Color(199, 218, 239)
        pageCount.font = pageCount.font.deriveFont(12f)
        th.add(pageCount, BorderLayout.SOUTH)
        thumbs.add(th, BorderLayout.NORTH)
        configureThumbList()
        thumbs.add(
            JScrollPane(thumbList).apply {
                border = null
                viewport.background = Color(8, 28, 48)
                horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
                verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            },
            BorderLayout.CENTER
        )
        body.add(thumbs, BorderLayout.EAST)

        val previewStack = JPanel(BorderLayout(0, 8)).apply { isOpaque = false }
        preview.background = Color(8, 26, 45)
        preview.border = BorderFactory.createDashedBorder(Color(86, 124, 165), 2f, 6f, 4f, false)
        preview.minimumSize = Dimension(760, 700)
        preview.onCrop = { c ->
            val idx = thumbList.selectedIndex
            if (idx in pages.indices) {
                pushUndo()
                pages[idx].crop = c
                redo.clear()
                refreshAll(selectIndex = idx)
            }
        }
        installFileDrop(preview)
        previewStack.add(preview, BorderLayout.CENTER)

        val controls = JPanel(FlowLayout(FlowLayout.CENTER, 10, 8)).apply { isOpaque = false }
        controls.add(squareControl("−") { setZoom(zoom - .15) })
        zoomLabel.foreground = V2_TEXT
        zoomLabel.font = zoomLabel.font.deriveFont(15f)
        zoomLabel.preferredSize = Dimension(50, 34)
        zoomLabel.horizontalAlignment = SwingConstants.CENTER
        controls.add(zoomLabel)
        controls.add(squareControl("+") { setZoom(zoom + .15) })
        controls.add(controlButton("⛶  Ajustar") { setZoom(1.0) })
        controls.add(Box.createHorizontalStrut(12))
        controls.add(squareControl("↶") { undo() })
        controls.add(squareControl("↷") { redo() })
        controls.add(controlButton("▱  Limpar") { clearAll() })
        previewStack.add(controls, BorderLayout.NORTH)
        body.add(previewStack, BorderLayout.CENTER)

        p.add(body, BorderLayout.CENTER)
        return p
    }

    private fun buildRightPanel():JComponent {
        val p=RoundedPanel(16,V2_BG_2,V2_BORDER_SOFT).apply{layout=BoxLayout(this,BoxLayout.Y_AXIS);border=EmptyBorder(10,8,10,8);preferredSize=Dimension(220,1);minimumSize=Dimension(214,1)};p.add(label("▣  Capa e Exportação",16f,Font.BOLD,V2_TEXT));p.add(gap(10));includeCover.foreground=Color(207,223,240);includeCover.background=V2_BG_2;includeCover.isBorderPainted=false;includeCover.isFocusPainted=false;includeCover.horizontalAlignment=SwingConstants.LEFT;includeCover.alignmentX=LEFT_ALIGNMENT;includeCover.maximumSize=Dimension(Int.MAX_VALUE,32);includeCover.addActionListener{styleCoverToggle()};styleCoverToggle();p.add(includeCover);p.add(gap(8))
        p.add(RoundedPanel(12,Color(26,48,72),V2_BORDER_SOFT).apply{layout=BorderLayout();maximumSize=Dimension(Int.MAX_VALUE,152);preferredSize=Dimension(198,152);border=EmptyBorder(7,7,7,7);coverPreview.horizontalAlignment=SwingConstants.CENTER;coverPreview.verticalAlignment=SwingConstants.CENTER;add(coverPreview,BorderLayout.CENTER)});p.add(gap(7));p.add(controlButton("▧  Trocar capa"){changeCover()}.apply{maximumSize=Dimension(Int.MAX_VALUE,40);alignmentX=LEFT_ALIGNMENT});p.add(gap(9));p.add(fieldLabel("✎","Título da capa"));styleTextField(titleField);p.add(titleField);p.add(gap(8));p.add(fieldLabel("●","Autor (opcional)"));styleTextField(authorField);p.add(authorField);p.add(gap(12));p.add(label("◉  Qualidade de exportação",13f,Font.PLAIN,V2_TEXT));quality.maximumSize=Dimension(Int.MAX_VALUE,40);quality.alignmentX=LEFT_ALIGNMENT;quality.background=Color(11,35,58);quality.foreground=V2_TEXT;p.add(quality);p.add(gap(10))
        p.add(RoundedPanel(10,Color(9,47,89),Color(14,104,199)).apply{layout=BorderLayout();maximumSize=Dimension(Int.MAX_VALUE,74);border=EmptyBorder(9,10,9,10);add(label("ⓘ",18f,Font.BOLD,Color(113,177,246)),BorderLayout.WEST);add(label("<html>PDF sem giro/recorte é mantido vetorial.<br>Páginas editadas são rasterizadas<br>conforme a qualidade escolhida.</html>",11f,Font.PLAIN,Color(194,216,240)),BorderLayout.CENTER)});p.add(Box.createVerticalGlue());p.add(greenButton("▣  GERAR PDF","Exportar documento final"){exportPdf()});return p
    }

    private fun buildFooter():JComponent=JPanel(BorderLayout()).apply{background=Color(4,19,35);preferredSize=Dimension(1,30);border=BorderFactory.createMatteBorder(1,0,0,0,V2_BORDER_SOFT);add(label("Central de Inteligência de Mídia   |   Editor de PDF",10.5f,Font.PLAIN,Color(161,185,211)).apply{border=EmptyBorder(0,18,0,0)},BorderLayout.WEST);add(label("ϟ  Mais produtividade",10.5f,Font.PLAIN,Color(170,196,224)).apply{border=EmptyBorder(0,0,0,18)},BorderLayout.EAST)}

    private fun configureThumbList(){thumbList.layoutOrientation=JList.VERTICAL;thumbList.visibleRowCount=-1;thumbList.fixedCellWidth=72;thumbList.fixedCellHeight=112;thumbList.background=Color(8,28,48);thumbList.selectionMode=ListSelectionModel.SINGLE_SELECTION;thumbList.cellRenderer=PdfV2ThumbnailRenderer();thumbList.dragEnabled=true;thumbList.dropMode=DropMode.INSERT;thumbList.transferHandler=PdfV2ReorderTransferHandler();thumbList.addListSelectionListener{if(!it.valueIsAdjusting)refreshPreview()}}
    private fun setThumbMode(t:Boolean){thumbList.layoutOrientation=JList.VERTICAL;thumbList.visibleRowCount=-1;if(t){thumbList.fixedCellWidth=72;thumbList.fixedCellHeight=112}else{thumbList.fixedCellWidth=-1;thumbList.fixedCellHeight=52};thumbList.revalidate();thumbList.repaint()}
    private fun chooseAll(){val fc=JFileChooser().apply{isMultiSelectionEnabled=true;fileFilter=FileNameExtensionFilter("PDF e imagens","pdf","jpg","jpeg","png","webp","bmp","tiff","tif")};if(fc.showOpenDialog(this)==JFileChooser.APPROVE_OPTION)importFiles(fc.selectedFiles.toList())}
    private fun choosePdfs(){val fc=JFileChooser().apply{isMultiSelectionEnabled=true;fileFilter=FileNameExtensionFilter("Arquivos PDF","pdf")};if(fc.showOpenDialog(this)==JFileChooser.APPROVE_OPTION)importFiles(fc.selectedFiles.toList())}
    private fun importFiles(files:List<File>){val valid=files.filter{it.exists()&&it.isFile};if(valid.isEmpty())return;pushUndo();var added=0;valid.forEach{f->try{when(f.extension.lowercase()){ "pdf"->Loader.loadPDF(f).use{doc->if(doc.numberOfPages==0)error("PDF sem páginas");repeat(doc.numberOfPages){i->pages+=PdfV2Page(kind=PdfV2ItemKind.PDF,path=f.absolutePath,pageNo=i);added++}};"jpg","jpeg","png","webp","bmp","tiff","tif"->{val img=ImageIO.read(f)?:error("Imagem inválida");img.flush();pages+=PdfV2Page(kind=PdfV2ItemKind.IMAGE,path=f.absolutePath);added++}}}catch(e:Exception){showError("Não foi possível importar ${f.name}: ${e.message}")}};if(added==0&&undo.isNotEmpty())undo.removeLast()else redo.clear();refreshAll(selectLast=true)}
    private fun createBlankPage(){pushUndo();pages+=PdfV2Page(kind=PdfV2ItemKind.BLANK);redo.clear();refreshAll(selectLast=true)}
    private fun startCrop(){
        val idx = thumbList.selectedIndex
        if(idx !in pages.indices){showError("Selecione uma página para recortar.");return}
        val p = pages[idx]
        preview.setPage(runCatching{renderTransformedSource(p,120)}.getOrNull(),zoom,p.crop)
        preview.cropMode=true
        preview.cursor=Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
        preview.requestFocusInWindow()
        preview.repaint()
    }
    private fun showTransformMenu(){val idx=thumbList.selectedIndex;if(idx !in pages.indices)return;val o=arrayOf("Girar 90° à esquerda","Girar 90° à direita","Espelhar horizontalmente","Cancelar");when(JOptionPane.showOptionDialog(this,"Escolha a transformação:","Girar e espelhar",JOptionPane.DEFAULT_OPTION,JOptionPane.PLAIN_MESSAGE,null,o,o[1])){0->rotateSelected(-90);1->rotateSelected(90);2->flipSelected()}}
    private fun rotateSelected(d:Int){val idx=thumbList.selectedIndex;if(idx !in pages.indices)return;pushUndo();pages[idx].rotation=((pages[idx].rotation+d)%360+360)%360;pages[idx].crop=null;redo.clear();refreshAll(selectIndex=idx)}
    private fun flipSelected(){val idx=thumbList.selectedIndex;if(idx !in pages.indices)return;pushUndo();pages[idx].flipX=!pages[idx].flipX;pages[idx].crop=null;redo.clear();refreshAll(selectIndex=idx)}
    private fun deleteSelected(){val idx=thumbList.selectedIndex;if(idx !in pages.indices)return;pushUndo();pages.removeAt(idx);redo.clear();refreshAll(selectIndex=if(pages.isEmpty())-1 else min(idx,pages.lastIndex))}
    private fun focusReorder(){thumbList.requestFocusInWindow();JOptionPane.showMessageDialog(this,"Arraste as miniaturas na coluna lateral de páginas para mudar a ordem.","Reordenar",JOptionPane.INFORMATION_MESSAGE)}
    private fun clearAll(){if(pages.isEmpty())return;if(JOptionPane.showConfirmDialog(this,"Remover todas as páginas do documento?","Limpar",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;pushUndo();pages.clear();redo.clear();refreshAll(selectIndex=-1)}
    private fun setZoom(v:Double){zoom=v.coerceIn(.50,3.0);zoomLabel.text="${(zoom*100).roundToInt()}%";refreshPreview()}
    private fun pushUndo(){undo.addLast(PdfV2Snapshot(pages.map{it.copyDeep()},thumbList.selectedIndex));while(undo.size>30)undo.removeFirst()}
    private fun undo(){val s=undo.removeLastOrNull()?:return;redo.addLast(PdfV2Snapshot(pages.map{it.copyDeep()},thumbList.selectedIndex));restoreSnapshot(s)}
    private fun redo(){val s=redo.removeLastOrNull()?:return;undo.addLast(PdfV2Snapshot(pages.map{it.copyDeep()},thumbList.selectedIndex));restoreSnapshot(s)}
    private fun restoreSnapshot(s:PdfV2Snapshot){pages.clear();pages.addAll(s.pages.map{it.copyDeep()});refreshAll(selectIndex=s.selected.coerceAtMost(pages.lastIndex))}
    private fun refreshAll(selectLast:Boolean=false,selectIndex:Int?=null){val uid=thumbList.selectedValue?.uid;thumbModel.clear();pages.forEach{thumbModel.addElement(it)};pageCount.text="${pages.size} ${if(pages.size==1)"página" else "páginas"}";val idx=when{selectIndex!=null->selectIndex;selectLast&&pages.isNotEmpty()->pages.lastIndex;uid!=null->pages.indexOfFirst{it.uid==uid};pages.isNotEmpty()->0;else->-1};if(idx in pages.indices)thumbList.selectedIndex=idx else thumbList.clearSelection();thumbList.repaint();refreshPreview();refreshCoverPreview()}
    private fun refreshPreview(){
        val idx=thumbList.selectedIndex
        if(idx !in pages.indices){preview.setPage(null,zoom,null);return}
        val p=pages[idx]
        val img=runCatching{if(p.crop!=null)renderFinalPage(p,120) else renderTransformedSource(p,120)}.getOrNull()
        preview.setPage(img,zoom,null)
    }

    private fun renderTransformedSource(page:PdfV2Page,dpi:Int):BufferedImage {val source=when(page.kind){PdfV2ItemKind.IMAGE->ImageIO.read(File(page.path))?:error("Imagem não suportada");PdfV2ItemKind.PDF->Loader.loadPDF(File(page.path)).use{doc->PDFRenderer(doc).renderImageWithDPI(page.pageNo?:0,dpi.toFloat())};PdfV2ItemKind.BLANK->BufferedImage(1240,1754,BufferedImage.TYPE_INT_RGB).also{img->val g=img.createGraphics();g.color=Color.WHITE;g.fillRect(0,0,img.width,img.height);g.dispose()}};var out=source;if(page.rotation!=0)out=rotateV2(out,page.rotation);if(page.flipX)out=flipHorizontalV2(out);return out}
    private fun renderFinalPage(page:PdfV2Page,dpi:Int):BufferedImage {
        var out=renderTransformedSource(page,dpi)
        page.crop?.let{c->
            val nx=c.x.coerceIn(0.0,1.0)
            val ny=c.y.coerceIn(0.0,1.0)
            val nw=c.w.coerceIn(0.0,1.0-nx)
            val nh=c.h.coerceIn(0.0,1.0-ny)
            val x1=Math.floor(nx*out.width).toInt().coerceIn(0,out.width-1)
            val y1=Math.floor(ny*out.height).toInt().coerceIn(0,out.height-1)
            val x2=Math.ceil((nx+nw)*out.width).toInt().coerceIn(x1+1,out.width)
            val y2=Math.ceil((ny+nh)*out.height).toInt().coerceIn(y1+1,out.height)
            out=copyV2(out.getSubimage(x1,y1,x2-x1,y2-y1))
        }
        return out
    }
    private fun createThumbnail(page:PdfV2Page):ImageIcon{val src=renderFinalPage(page,58);val s=min(56.0/src.width,84.0/src.height).coerceAtMost(1.0);return ImageIcon(src.getScaledInstance(max(1,(src.width*s).roundToInt()),max(1,(src.height*s).roundToInt()),Image.SCALE_SMOOTH))}
    private fun changeCover(){val fc=JFileChooser().apply{fileFilter=FileNameExtensionFilter("Imagem","jpg","jpeg","png","webp","bmp","tiff","tif")};if(fc.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;runCatching{val img=ImageIO.read(fc.selectedFile)?:error("Imagem inválida");ImageIO.write(img,"png",customCoverFile);customCover=customCoverFile;saveConfig();refreshCoverPreview()}.onFailure{showError("Não foi possível trocar a capa: ${it.message}")}}
    private fun embeddedDefaultCover():BufferedImage{return runCatching{val s=javaClass.getResourceAsStream("/pdf-default-cover.b64")?:error("Recurso da capa não encontrado");val encoded=s.bufferedReader(Charsets.UTF_8).use{it.readText()};val bytes=Base64.getDecoder().decode(encoded.trim());ImageIO.read(ByteArrayInputStream(bytes))?:error("Capa padrão inválida")}.getOrElse{BufferedImage(1245,2048,BufferedImage.TYPE_INT_RGB).also{img->val g=img.createGraphics();g.color=Color(28,28,28);g.fillRect(0,0,img.width,img.height);g.color=Color.WHITE;g.font=Font("SansSerif",Font.BOLD,90);g.drawString("RADAR DE NOTÍCIAS",120,700);g.drawString("MÍDIA IMPRESSA",190,1180);g.dispose()}}}
    private fun currentCoverImage():BufferedImage{val f=customCover;if(f!=null&&f.exists())return ImageIO.read(f)?:embeddedDefaultCover();if(hdDefaultCoverFile.exists())return ImageIO.read(hdDefaultCoverFile)?:embeddedDefaultCover();return embeddedDefaultCover()}
    private fun refreshCoverPreview(){val img=runCatching{currentCoverImage()}.getOrElse{embeddedDefaultCover()};val s=min(205.0/img.width,154.0/img.height);coverPreview.icon=ImageIcon(img.getScaledInstance(max(1,(img.width*s).roundToInt()),max(1,(img.height*s).roundToInt()),Image.SCALE_SMOOTH))}
    private fun exportPdf(){if(pages.isEmpty()&&!includeCover.isSelected){showError("Adicione ao menos uma página ou mantenha a capa ativada.");return};val fc=JFileChooser().apply{dialogTitle="Salvar PDF";selectedFile=File(if(includeCover.isSelected)"RADAR DE NOTICIAS - MIDIA IMPRESSA.pdf" else "documento.pdf");fileFilter=FileNameExtensionFilter("PDF","pdf")};if(fc.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;var outFile=fc.selectedFile;if(!outFile.name.lowercase().endsWith(".pdf"))outFile=File(outFile.parentFile,outFile.name+".pdf");val q=quality.selectedItem as? PdfV2ExportQuality?:PdfV2ExportQuality.HIGH;try{cursor=Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR);PDDocument().use{out->if(titleField.text.isNotBlank())out.documentInformation.title=titleField.text.trim();if(authorField.text.isNotBlank())out.documentInformation.author=authorField.text.trim();if(includeCover.isSelected)appendRaster(out,currentCoverImage(),q);pages.forEach{p->if(p.kind==PdfV2ItemKind.PDF&&p.rotation==0&&!p.flipX&&p.crop==null)appendVector(out,p)else appendRaster(out,renderFinalPage(p,q.dpi),q)};out.save(outFile)};val o=arrayOf("OK","Abrir pasta");val c=JOptionPane.showOptionDialog(this,"PDF gerado com sucesso!\n${outFile.absolutePath}","Exportação concluída",JOptionPane.DEFAULT_OPTION,JOptionPane.INFORMATION_MESSAGE,null,o,o[0]);if(c==1&&Desktop.isDesktopSupported())Desktop.getDesktop().open(outFile.parentFile)}catch(e:Exception){showError("Erro ao gerar PDF: ${e.message}")}finally{cursor=Cursor.getDefaultCursor()}}
    private fun appendVector(out:PDDocument,data:PdfV2Page){Loader.loadPDF(File(data.path)).use{src->val sp=src.getPage(data.pageNo?:0);val box=sp.cropBox?:sp.mediaBox;val pw=595.276f;val ph=pw*box.height/box.width;val target=PDPage(PDRectangle(pw,ph));out.addPage(target);val form=LayerUtility(out).importPageAsForm(src,data.pageNo?:0);val scale=min(pw/box.width,ph/box.height);val x=(pw-box.width*scale)/2f;val y=(ph-box.height*scale)/2f;PDPageContentStream(out,target).use{cs->cs.transform(Matrix.getTranslateInstance(x,y));cs.transform(Matrix.getScaleInstance(scale,scale));cs.drawForm(form)}}}
    private fun appendRaster(out:PDDocument,image:BufferedImage,q:PdfV2ExportQuality){val pw=595.276f;val ph=pw*image.height.toFloat()/image.width.toFloat();val page=PDPage(PDRectangle(pw,ph));out.addPage(page);val pd=LosslessFactory.createFromImage(out,ensureRgbV2(image));PDPageContentStream(out,page).use{cs->cs.drawImage(pd,0f,0f,pw,ph)}}
    private fun createPortableDirs(){runCatching{if(!dataDir.exists())dataDir.mkdirs()}}
    private fun loadConfig(){runCatching{if(!configFile.exists())return@runCatching;val o=JSONObject(configFile.readText(Charsets.UTF_8));val rel=o.optString("custom_cover","");if(rel.isNotBlank())File(portableDir,rel.replace('/',File.separatorChar)).takeIf{it.exists()}?.let{customCover=it}}}
    private fun saveConfig(){runCatching{val o=JSONObject();if(customCover!=null)o.put("custom_cover","data/capa_padrao_usuario.png");configFile.writeText(o.toString(2),Charsets.UTF_8)}}
    private fun resolvePortableDir():File{val a=System.getProperty("jpackage.app-path");if(!a.isNullOrBlank())File(a).takeIf{it.exists()}?.parentFile?.let{return it};return File(System.getProperty("user.dir")).absoluteFile}
    private fun installKeyboardShortcuts(){val im=getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);val am=actionMap;im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z,InputEvent.CTRL_DOWN_MASK),"undo");im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y,InputEvent.CTRL_DOWN_MASK),"redo");im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE,0),"delete");im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S,InputEvent.CTRL_DOWN_MASK),"save");am.put("undo",action{undo()});am.put("redo",action{redo()});am.put("delete",action{deleteSelected()});am.put("save",action{exportPdf()})}
    private fun action(b:()->Unit)=object:AbstractAction(){override fun actionPerformed(e:ActionEvent?)=b()}
    private fun installFileDrop(c:Component){DropTarget(c,object:DropTargetAdapter(){override fun drop(e:DropTargetDropEvent){try{e.acceptDrop(DnDConstants.ACTION_COPY);@Suppress("UNCHECKED_CAST") val fs=e.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>;importFiles(fs);e.dropComplete(true)}catch(x:Exception){e.dropComplete(false);showError("Falha ao receber arquivos: ${x.message}")}}})}
    private fun updateClock(){clockDate.text=java.text.SimpleDateFormat("dd/MM/yyyy").format(Date());clockTime.text=java.text.SimpleDateFormat("HH:mm:ss").format(Date())}
    private fun showError(m:String)=JOptionPane.showMessageDialog(this,m,"Editor de PDF",JOptionPane.ERROR_MESSAGE)
    private fun label(t:String,s:Float,st:Int,c:Color)=JLabel(t).apply{foreground=c;font=font.deriveFont(st,s)}
    private fun gap(h:Int)=Box.createVerticalStrut(h)
    private fun statusPill(t:String,a:Color):JComponent=RoundedPanel(12,Color(6,32,49),a).apply{layout=BorderLayout();preferredSize=Dimension(154,44);border=EmptyBorder(0,15,0,15);add(label(t,13f,Font.BOLD,a),BorderLayout.CENTER)}
    private fun styledSquareButton(t:String,a:()->Unit)=StyledButton(t,Color(17,42,68),V2_TEXT,V2_BORDER).apply{preferredSize=Dimension(44,44);font=font.deriveFont(Font.BOLD,18f);addActionListener{a()}}
    private fun navButton(i:String,t:String,sub:String?,a:Color,active:Boolean=false,act:()->Unit)=StyledButton("",if(active)Color(17,73,139)else Color(14,36,59),V2_TEXT,if(active)V2_BLUE else V2_BORDER_SOFT).apply{layout=BorderLayout(5,0);maximumSize=Dimension(Int.MAX_VALUE,if(sub==null)42 else 50);preferredSize=Dimension(138,if(sub==null)42 else 50);toolTipText=if(sub==null)t else "$t - $sub";add(RoundedPanel(8,Color(a.red,a.green,a.blue,35),a).apply{preferredSize=Dimension(30,30);layout=BorderLayout();add(label(i,if(i=="PDF")9.5f else 15.5f,Font.BOLD,a).apply{horizontalAlignment=SwingConstants.CENTER},BorderLayout.CENTER)},BorderLayout.WEST);add(JPanel().apply{layout=BoxLayout(this,BoxLayout.Y_AXIS);isOpaque=false;add(label(t,12.5f,Font.BOLD,V2_TEXT));if(sub!=null)add(label(sub,9.5f,Font.PLAIN,Color(156,190,224)))},BorderLayout.CENTER);addActionListener{act()}}
    private fun blueButton(t:String,a:()->Unit)=StyledButton(t,V2_BLUE_2,Color.WHITE,V2_BLUE).apply{font=font.deriveFont(Font.BOLD,13f);addActionListener{a()}}
    private fun controlButton(t:String,a:()->Unit)=StyledButton(t,Color(16,40,65),V2_TEXT,V2_BORDER_SOFT).apply{preferredSize=Dimension(96,36);font=font.deriveFont(Font.BOLD,11.5f);addActionListener{a()}}
    private fun squareControl(t:String,a:()->Unit)=StyledButton(t,Color(16,40,65),V2_TEXT,V2_BORDER_SOFT).apply{preferredSize=Dimension(42,36);font=font.deriveFont(Font.BOLD,16f);addActionListener{a()}}
    private fun greenButton(a:String,b:String,act:()->Unit):JComponent=StyledButton("",V2_GREEN,Color.WHITE,Color(29,229,123)).apply{layout=BorderLayout();maximumSize=Dimension(Int.MAX_VALUE,58);preferredSize=Dimension(202,58);alignmentX=LEFT_ALIGNMENT;add(JPanel().apply{layout=BoxLayout(this,BoxLayout.Y_AXIS);isOpaque=false;add(label(a,15f,Font.BOLD,Color.WHITE).apply{alignmentX=CENTER_ALIGNMENT});add(label(b,10.5f,Font.PLAIN,Color(225,255,238)).apply{alignmentX=CENTER_ALIGNMENT})},BorderLayout.CENTER);addActionListener{act()}}
    private fun styleModeToggle(b:JToggleButton,s:Boolean){b.isSelected=s;b.isFocusPainted=false;b.foreground=if(s)Color.WHITE else Color(200,216,234);b.background=if(s)V2_BLUE else Color(14,37,61);b.border=EmptyBorder(10,14,10,14);b.addItemListener{b.background=if(b.isSelected)V2_BLUE else Color(14,37,61);b.foreground=if(b.isSelected)Color.WHITE else Color(200,216,234)}}
    private fun styleCoverToggle(){includeCover.text=if(includeCover.isSelected)"●  Incluir capa padrão                         ●" else "○  Incluir capa padrão                         ○";includeCover.foreground=if(includeCover.isSelected)Color(210,228,246)else V2_MUTED}
    private fun fieldLabel(i:String,t:String):JComponent=JPanel(FlowLayout(FlowLayout.LEFT,0,0)).apply{isOpaque=false;maximumSize=Dimension(Int.MAX_VALUE,25);add(label("$i  $t",12f,Font.PLAIN,V2_TEXT))}
    private fun styleTextField(f:JTextField){f.maximumSize=Dimension(Int.MAX_VALUE,39);f.background=Color(10,33,55);f.foreground=V2_TEXT;f.caretColor=Color.WHITE;f.border=BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(V2_BORDER_SOFT),EmptyBorder(8,10,8,10))}

    private inner class PdfV2ThumbnailRenderer:DefaultListCellRenderer(){override fun getListCellRendererComponent(list:JList<*>?,value:Any?,index:Int,isSelected:Boolean,cellHasFocus:Boolean):Component{val l=super.getListCellRendererComponent(list,"",index,isSelected,cellHasFocus) as JLabel;val p=value as? PdfV2Page;l.horizontalAlignment=SwingConstants.CENTER;l.verticalAlignment=SwingConstants.CENTER;l.horizontalTextPosition=SwingConstants.CENTER;l.verticalTextPosition=SwingConstants.BOTTOM;l.text="${index+1}";l.foreground=V2_TEXT;l.background=if(isSelected)Color(18,79,139)else Color(11,35,58);l.isOpaque=true;l.border=BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(if(isSelected)V2_BLUE else V2_BORDER_SOFT),EmptyBorder(4,4,4,4));l.icon=p?.let{runCatching{createThumbnail(it)}.getOrNull()};return l}}
    private inner class PdfV2ReorderTransferHandler:TransferHandler(){private var source=-1;override fun getSourceActions(c:JComponent?)=MOVE;override fun createTransferable(c:JComponent?):java.awt.datatransfer.Transferable{source=thumbList.selectedIndex;return StringSelection(source.toString())};override fun canImport(s:TransferSupport)=s.isDrop&&s.isDataFlavorSupported(DataFlavor.stringFlavor);override fun importData(s:TransferSupport):Boolean{if(!canImport(s)||source !in pages.indices)return false;val dl=s.dropLocation as? JList.DropLocation?:return false;var target=dl.index.coerceIn(0,pages.size);if(target==source||target==source+1)return false;pushUndo();val item=pages.removeAt(source);if(target>source)target--;pages.add(target.coerceIn(0,pages.size),item);redo.clear();refreshAll(selectIndex=pages.indexOfFirst{it.uid==item.uid});return true}}
}

private class PdfV2PreviewPanel:JPanel(){var cropMode=false;var onCrop:((PdfV2Crop)->Unit)?=null;private var image:BufferedImage?=null;private var zoom=1.0;private var existingCrop:PdfV2Crop?=null;private var dragStart:Point?=null;private var dragEnd:Point?=null;private var imageRect=Rectangle();init{addMouseListener(object:MouseAdapter(){override fun mousePressed(e:MouseEvent){if(cropMode&&imageRect.contains(e.point)){dragStart=clamp(e.point);dragEnd=dragStart;repaint()}};override fun mouseReleased(e:MouseEvent){val s=dragStart?:return;val ee=clamp(e.point);dragEnd=ee;if(cropMode&&imageRect.width>0&&imageRect.height>0){val x1=min(s.x,ee.x).coerceIn(imageRect.x,imageRect.x+imageRect.width);val y1=min(s.y,ee.y).coerceIn(imageRect.y,imageRect.y+imageRect.height);val x2=max(s.x,ee.x).coerceIn(imageRect.x,imageRect.x+imageRect.width);val y2=max(s.y,ee.y).coerceIn(imageRect.y,imageRect.y+imageRect.height);if(x2-x1>4&&y2-y1>4){val nx1=(x1-imageRect.x).toDouble()/imageRect.width;val ny1=(y1-imageRect.y).toDouble()/imageRect.height;val nx2=(x2-imageRect.x).toDouble()/imageRect.width;val ny2=(y2-imageRect.y).toDouble()/imageRect.height;onCrop?.invoke(PdfV2Crop(nx1.coerceIn(0.0,1.0),ny1.coerceIn(0.0,1.0),(nx2-nx1).coerceIn(0.0,1.0),(ny2-ny1).coerceIn(0.0,1.0)))}};cropMode=false;cursor=Cursor.getDefaultCursor();dragStart=null;dragEnd=null;repaint()}});addMouseMotionListener(object:MouseMotionAdapter(){override fun mouseDragged(e:MouseEvent){if(cropMode&&dragStart!=null){dragEnd=clamp(e.point);repaint()}}})};fun setPage(img:BufferedImage?,z:Double,c:PdfV2Crop?){image=img;zoom=z;existingCrop=c;dragStart=null;dragEnd=null;repaint()};private fun clamp(p:Point)=Point(p.x.coerceIn(imageRect.x,imageRect.x+imageRect.width),p.y.coerceIn(imageRect.y,imageRect.y+imageRect.height));override fun paintComponent(g:Graphics){super.paintComponent(g);val g2=g as Graphics2D;g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);val img=image;if(img==null){val cx=width/2;val cy=height/2-25;g2.color=Color(27,117,225);g2.fillRoundRect(cx-125,cy-72,70,62,10,10);g2.color=Color(244,247,251);g2.fillRoundRect(cx-38,cy-86,76,82,9,9);g2.color=Color(140,88,255);g2.fillRoundRect(cx+56,cy-65,68,56,10,10);g2.color=Color(236,58,65);g2.fillRoundRect(cx-12,cy-46,52,27,6,6);g2.color=Color.WHITE;g2.font=Font("SansSerif",Font.BOLD,13);g2.drawString("PDF",cx-3,cy-27);g2.color=V2_TEXT;g2.font=Font("SansSerif",Font.BOLD,24);drawCentered(g2,"Arraste e solte seus arquivos aqui",cy+45);g2.color=V2_MUTED;g2.font=Font("SansSerif",Font.PLAIN,15);drawCentered(g2,"Suporte a PDFs e imagens (JPG, PNG, etc.)",cy+80);g2.color=Color(70,104,139);g2.drawLine(cx-170,cy+115,cx-25,cy+115);g2.drawLine(cx+25,cy+115,cx+170,cy+115);g2.color=V2_MUTED;g2.font=Font("SansSerif",Font.PLAIN,13);drawCentered(g2,"ou",cy+120);g2.color=V2_BLUE;g2.fillRoundRect(cx-130,cy+140,260,54,12,12);g2.color=Color.WHITE;g2.font=Font("SansSerif",Font.BOLD,15);drawCentered(g2,"▰  Selecionar arquivos",cy+174);return};g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);val fit=min((width-34).toDouble()/img.width,(height-34).toDouble()/img.height).coerceAtLeast(.03);val sc=fit*zoom;val w=max(1,(img.width*sc).roundToInt());val h=max(1,(img.height*sc).roundToInt());val x=(width-w)/2;val y=(height-h)/2;imageRect=Rectangle(x,y,w,h);g2.color=Color.WHITE;g2.fillRect(x-1,y-1,w+2,h+2);g2.drawImage(img,x,y,w,h,null);existingCrop?.let{c->val rx=x+(c.x*w).roundToInt();val ry=y+(c.y*h).roundToInt();val rw=max(1,(c.w*w).roundToInt());val rh=max(1,(c.h*h).roundToInt());g2.color=Color(0,0,0,105);g2.fillRect(x,y,w,max(0,ry-y));g2.fillRect(x,ry+rh,w,max(0,y+h-(ry+rh)));g2.fillRect(x,ry,max(0,rx-x),rh);g2.fillRect(rx+rw,ry,max(0,x+w-(rx+rw)),rh);g2.color=V2_BLUE;g2.stroke=BasicStroke(2.4f);g2.drawRect(rx,ry,rw,rh)};val a=dragStart;val b=dragEnd;if(a!=null&&b!=null){val rx=min(a.x,b.x);val ry=min(a.y,b.y);val rw=abs(a.x-b.x);val rh=abs(a.y-b.y);g2.color=Color(15,127,255,55);g2.fillRect(rx,ry,rw,rh);g2.color=V2_BLUE;g2.stroke=BasicStroke(2.5f);g2.drawRect(rx,ry,rw,rh)}};private fun drawCentered(g:Graphics2D,t:String,y:Int){g.drawString(t,(width-g.fontMetrics.stringWidth(t))/2,y)}}
private class RoundedPanel(private val radius:Int,private val fill:Color,private val line:Color?=null):JPanel(){init{isOpaque=false};override fun paintComponent(g:Graphics){val g2=g.create() as Graphics2D;g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g2.color=fill;g2.fillRoundRect(0,0,width-1,height-1,radius,radius);line?.let{g2.color=it;g2.stroke=BasicStroke(1f);g2.drawRoundRect(0,0,width-1,height-1,radius,radius)};g2.dispose();super.paintComponent(g)}}
private class StyledButton(text:String,private val fill:Color,fg:Color,private val line:Color):JButton(text){init{isOpaque=false;isContentAreaFilled=false;isBorderPainted=false;isFocusPainted=false;foreground=fg;border=EmptyBorder(8,12,8,12);cursor=Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)};override fun paintComponent(g:Graphics){val g2=g.create() as Graphics2D;g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g2.color=if(model.isRollover)fill.brighter() else fill;g2.fillRoundRect(0,0,width-1,height-1,12,12);g2.color=line;g2.drawRoundRect(0,0,width-1,height-1,12,12);g2.dispose();super.paintComponent(g)}}
private fun rotateV2(src:BufferedImage,degrees:Int):BufferedImage{val n=((degrees%360)+360)%360;if(n==0)return src;val swap=n==90||n==270;val w=if(swap)src.height else src.width;val h=if(swap)src.width else src.height;val dst=BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);val g=dst.createGraphics();g.color=Color.WHITE;g.fillRect(0,0,w,h);val tx=AffineTransform();when(n){90->{tx.translate(w.toDouble(),0.0);tx.rotate(Math.toRadians(90.0))};180->{tx.translate(w.toDouble(),h.toDouble());tx.rotate(Math.toRadians(180.0))};270->{tx.translate(0.0,h.toDouble());tx.rotate(Math.toRadians(270.0))}};g.drawImage(src,tx,null);g.dispose();return dst}
private fun flipHorizontalV2(src:BufferedImage):BufferedImage{val dst=BufferedImage(src.width,src.height,BufferedImage.TYPE_INT_RGB);val g=dst.createGraphics();g.color=Color.WHITE;g.fillRect(0,0,dst.width,dst.height);val tx=AffineTransform();tx.translate(src.width.toDouble(),0.0);tx.scale(-1.0,1.0);g.drawImage(src,tx,null);g.dispose();return dst}
private fun copyV2(src:BufferedImage):BufferedImage{val dst=BufferedImage(src.width,src.height,BufferedImage.TYPE_INT_RGB);val g=dst.createGraphics();g.color=Color.WHITE;g.fillRect(0,0,dst.width,dst.height);g.drawImage(src,0,0,null);g.dispose();return dst}
private fun ensureRgbV2(src:BufferedImage)=if(src.type==BufferedImage.TYPE_INT_RGB)src else copyV2(src)



