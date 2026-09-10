package br.com.monitordenoticias.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.*
import br.com.monitordenoticias.android.*
import kotlinx.coroutines.delay
import java.awt.Desktop as AwtDesktop
import java.net.URI
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.max

private val Navy = Color(0xFF062B55)
private val NavyDark = Color(0xFF031D3A)
private val Ink = Color(0xFF0B1F4B)
private val Muted = Color(0xFF5A75A7)
private val CanvasBg = Color(0xFFF2F7FE)
private val CardBorder = Color(0xFFDCE8F8)
private val Blue = Color(0xFF0879F9)
private val Blue2 = Color(0xFF2B92FF)
private val Purple = Color(0xFF7538F4)
private val Orange = Color(0xFFFF820B)
private val Green = Color(0xFF07AA72)
private val Pink = Color(0xFFD70659)
private val Yellow = Color(0xFFFFB52E)

private enum class ReferenceSection(val label: String, val icon: ImageVector) {
    HOME("Início", Icons.Default.Home),
    NEWS("Notícias", Icons.Default.Article),
    VIDEOS("Vídeos", Icons.Default.PlayCircle),
    DEMANDS("Demandas", Icons.Default.Assignment),
    SOURCES("Fontes", Icons.Default.Storage),
    HISTORY("Histórico", Icons.Default.History),
    TERMS("Termos de busca", Icons.Default.Search),
    SETTINGS("Configurações", Icons.Default.Settings)
}

fun main() = application {
    val trayState = rememberTrayState()
    var windowVisible by remember { mutableStateOf(true) }
    val controller = remember {
        DesktopController { title, message ->
            runCatching { trayState.sendNotification(Notification(title, message)) }
        }
    }
    val trayIcon = rememberVectorPainter(Icons.Default.Newspaper)

    Tray(
        state = trayState,
        icon = trayIcon,
        tooltip = "Monitor de Notícias v4.0.2",
        menu = {
            Item("Abrir Monitor de Notícias", onClick = { windowVisible = true })
            Separator()
            Item("Buscar notícias agora", onClick = { controller.searchNews() })
            Item("Buscar vídeos agora", onClick = { controller.searchVideos() })
            Item("Buscar demandas agora", onClick = { controller.searchAllDemands() })
            Separator()
            Item("Sair", onClick = { controller.close(); exitApplication() })
        }
    )

    Window(
        visible = windowVisible,
        onCloseRequest = { windowVisible = false },
        title = "Monitor de Notícias - Windows Portable v4.0.2",
        state = rememberWindowState(width = 1600.dp, height = 960.dp)
    ) {
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = Blue,
                onPrimary = Color.White,
                surface = Color.White,
                background = CanvasBg,
                onSurface = Ink,
                onBackground = Ink
            )
        ) {
            ReferenceApp(controller)
        }
    }
}

@Composable
private fun ReferenceApp(c: DesktopController) {
    var section by remember { mutableStateOf(ReferenceSection.HOME) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            tick++
        }
    }
    @Suppress("UNUSED_VARIABLE") val redraw = tick

    Column(Modifier.fillMaxSize().background(CanvasBg)) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            ReferenceSidebar(section, { section = it }, c)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                ReferenceHeader(section)
                if (section == ReferenceSection.HOME) {
                    ReferenceHome(c, onNavigate = { section = it })
                } else {
                    Box(Modifier.weight(1f).fillMaxWidth().padding(18.dp)) {
                        when (section) {
                            ReferenceSection.NEWS -> NewsScreen(c)
                            ReferenceSection.VIDEOS -> VideosScreen(c)
                            ReferenceSection.DEMANDS -> DemandsScreen(c)
                            ReferenceSection.SOURCES -> SourcesScreen(c)
                            ReferenceSection.HISTORY -> HistoryScreen(c)
                            ReferenceSection.TERMS -> TermsScreen(c)
                            ReferenceSection.SETTINGS -> SettingsScreen(c)
                            ReferenceSection.HOME -> Unit
                        }
                    }
                }
            }
        }
        ReferenceFooter()
    }
}

@Composable
private fun ReferenceSidebar(
    selected: ReferenceSection,
    onSelect: (ReferenceSection) -> Unit,
    c: DesktopController
) {
    Column(
        Modifier.width(255.dp).fillMaxHeight().background(
            Brush.verticalGradient(listOf(NavyDark, Navy, Color(0xFF073664)))
        ).padding(horizontal = 18.dp, vertical = 24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)).background(
                    Brush.linearGradient(listOf(Color(0xFFFFC756), Color(0xFFFFA522)))
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Newspaper, null, tint = Navy, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("MONITOR", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                Text("DE NOTÍCIAS", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(2.dp))
                Text("Inteligência de mídia", color = Color(0xFFB8CCE7), fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(26.dp))
        ReferenceSection.entries.forEach { item ->
            val active = selected == item
            Row(
                Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(11.dp))
                    .background(
                        if (active) Brush.horizontalGradient(listOf(Color(0xFF0B86FA), Color(0xFF1958C4)))
                        else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                    )
                    .clickable { onSelect(item) }.padding(horizontal = 17.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(item.icon, null, tint = if (active) Color.White else Color(0xFFC8DBF2), modifier = Modifier.size(25.dp))
                Spacer(Modifier.width(16.dp))
                Text(item.label, color = Color.White, fontSize = 16.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
            }
            Spacer(Modifier.height(7.dp))
        }

        Spacer(Modifier.weight(1f))
        Surface(
            color = Color(0x19000000),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF20517F)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(16.dp).clip(CircleShape).background(Color(0xFF7CE85E)))
                    Spacer(Modifier.width(9.dp))
                    Text("Sistema operacional", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Text("Dados locais • modo portátil", color = Color(0xFFB6CAE3), fontSize = 12.sp)
                StatusLine("Proxy autenticado")
                StatusLine(if (c.automaticMonitoring) "Sistema pronto" else "Monitoramento pausado")
                HorizontalDivider(color = Color(0xFF1E4E79))
                Text("Windows Portable v4.0.2", color = Color(0xFFC4D5E9), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun StatusLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF15C786)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color(0xFFD5E4F5), fontSize = 12.sp)
    }
}

@Composable
private fun ReferenceHeader(section: ReferenceSection) {
    val now = Date()
    val dateText = SimpleDateFormat("EEEE, dd 'de' MMMM 'de' yyyy", Locale("pt", "BR")).format(now)
    val timeText = SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(now)
    Row(
        Modifier.fillMaxWidth().height(95.dp).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Eco, null, tint = Green, modifier = Modifier.size(38.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    if (section == ReferenceSection.HOME) "Olá, bem-vindo! 👋" else section.label,
                    color = Ink,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    if (section == ReferenceSection.HOME) "Acompanhe notícias, vídeos, demandas e fontes em tempo real."
                    else "Monitor de Notícias • Windows Portable",
                    color = Muted,
                    fontSize = 13.sp
                )
            }
        }

        Surface(
            modifier = Modifier.width(455.dp).height(48.dp).shadow(7.dp, RoundedCornerShape(13.dp)),
            color = Color.White,
            shape = RoundedCornerShape(13.dp),
            border = BorderStroke(1.dp, Color(0xFFD8E6F8))
        ) {
            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, tint = Color(0xFF164B93), modifier = Modifier.size(25.dp))
                Spacer(Modifier.width(12.dp))
                Text("Buscar notícias, vídeos, demandas ou fontes...", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Surface(color = Color(0xFFEAF2FC), shape = RoundedCornerShape(9.dp)) {
                    Text("Ctrl + K", color = Color(0xFF34537C), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
                }
            }
        }

        Spacer(Modifier.width(22.dp))
        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.NotificationsNone, null, tint = Color(0xFF184D91), modifier = Modifier.size(27.dp))
            Box(Modifier.align(Alignment.TopEnd).offset((-1).dp, 2.dp).size(9.dp).clip(CircleShape).background(Color(0xFFE81F31)))
        }
        Spacer(Modifier.width(14.dp))
        Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF164B8C)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(27.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(104.dp)) {
            Text(dateText, color = Muted, fontSize = 8.sp, maxLines = 2)
            Text(timeText, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.width(9.dp))
        Surface(
            color = Color(0xFFF7FBFF),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFFE1ECF9)),
            modifier = Modifier.width(126.dp).height(60.dp)
        ) {
            Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WbSunny, null, tint = Color(0xFFFFB21A), modifier = Modifier.size(29.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("São Paulo - SP", color = Muted, fontSize = 8.sp)
                    Text("24°C", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Tempo limpo", color = Muted, fontSize = 8.sp)
                }
            }
        }
    }
}

@Composable
private fun ReferenceHome(c: DesktopController, onNavigate: (ReferenceSection) -> Unit) {
    val now = System.currentTimeMillis()
    val todayVideos = c.videos.count { now - it.capturedAt < 24L * 60 * 60 * 1000 }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 0.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth().height(102.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DashboardMetric("Notícias 24h", c.news.size.toString(), "na janela atual", Icons.Default.Article, Blue, Color(0xFFD9EBFF), Modifier.weight(1f))
            DashboardMetric("Vídeos armazenados", c.videos.size.toString(), "relevantes na base", Icons.Default.PlayCircleFilled, Purple, Color(0xFFECE0FF), Modifier.weight(1f))
            DashboardMetric("Vídeos hoje", todayVideos.toString(), "capturados hoje", Icons.Default.Videocam, Green, Color(0xFFD3F8EC), Modifier.weight(1f))
            DashboardMetric("Demandas", c.demands.count { it.active }.toString(), "${c.demands.count { it.active }} ativas", Icons.Default.Assignment, Orange, Color(0xFFFFEDD1), Modifier.weight(1f))
            DashboardMetric("Fontes", (SourceCatalog.all.size + VideoSourceCatalog.all.size).toString(), "8 especializadas", Icons.Default.Storage, Pink, Color(0xFFFFDDEA), Modifier.weight(1f), positive = true)
        }

        Row(Modifier.fillMaxWidth().height(244.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IntelligenceHero(c, Modifier.weight(1.38f))
            QuickActions(c, onNavigate, Modifier.weight(1f))
        }

        Row(Modifier.fillMaxWidth().height(158.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ScheduleCard(c, Modifier.weight(0.94f))
            DaySummaryCard(c, Modifier.weight(1.06f))
        }

        Row(Modifier.fillMaxWidth().height(220.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            RelevantSourcesCard(Modifier.weight(0.92f))
            LatestActivitiesCard(c, Modifier.weight(0.88f))
            TipsCard(Modifier.weight(0.80f))
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun DashboardMetric(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    iconBg: Color,
    modifier: Modifier,
    positive: Boolean = false
) {
    DashboardCard(modifier) {
        Row(Modifier.fillMaxSize().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(58.dp).clip(RoundedCornerShape(13.dp)).background(iconBg), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(value, color = Ink, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 28.sp)
                Text(subtitle, color = Muted, fontSize = 10.sp, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (positive) "↗ +2%" else "— 0%", color = if (positive) Green else Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                MiniSparkline(accent)
            }
        }
    }
}

@Composable
private fun MiniSparkline(accent: Color) {
    Canvas(Modifier.width(54.dp).height(26.dp)) {
        val pts = listOf(0.0f to .75f, .18f to .68f, .36f to .72f, .56f to .49f, .72f to .36f, .86f to .47f, 1f to .18f)
        val p = Path()
        pts.forEachIndexed { index, pair ->
            val x = pair.first * size.width
            val y = pair.second * size.height
            if (index == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        drawPath(p, accent, style = Stroke(width = 2.2f))
    }
}

@Composable
private fun DashboardCard(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Surface(
        modifier = modifier.shadow(8.dp, RoundedCornerShape(14.dp)),
        color = Color.White,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Box(Modifier.fillMaxSize(), content = content)
    }
}

@Composable
private fun IntelligenceHero(c: DesktopController, modifier: Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(14.dp)).background(
            Brush.linearGradient(listOf(Color(0xFF07284F), Color(0xFF0A4C86), Color(0xFF2F85D4)))
        )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color(0x152CB6FF), radius = size.width * .34f, center = Offset(size.width * .92f, size.height * .12f))
            drawCircle(Color(0x121CC0D8), radius = size.width * .30f, center = Offset(size.width * .75f, size.height * 1.08f))
        }
        Column(Modifier.fillMaxHeight().width(410.dp).padding(28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("CENTRAL DE INTELIGÊNCIA DE MÍDIA", color = Color(0xFFC4D8F1), fontSize = 11.sp, letterSpacing = 1.sp)
            Text("Tudo o que importa\nem um só lugar.", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 33.sp)
            Text("Buscas e resultados atualizados automaticamente,\nem tempo real.", color = Color(0xFFE6F0FB), fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.weight(1f))
            Surface(color = Color(0xFF074F55), shape = RoundedCornerShape(11.dp), border = BorderStroke(1.dp, Color(0xFF0A8A7B))) {
                Row(Modifier.width(325.dp).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(17.dp).clip(CircleShape).background(Color(0xFF27E58D)))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Status: ${if (c.automaticMonitoring) "Pronto" else "Pausado"}", color = Color(0xFF2AE795), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Monitoramento ativo e funcionando normalmente.", color = Color(0xFFB8D7E2), fontSize = 10.sp)
                    }
                }
            }
        }
        HeroIllustration(Modifier.align(Alignment.BottomEnd).width(360.dp).height(215.dp))
    }
}

@Composable
private fun HeroIllustration(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawOval(Color(0x33000000), topLeft = Offset(w * .15f, h * .84f), size = Size(w * .75f, h * .10f))
        drawRoundRect(Color(0xFF082844), topLeft = Offset(w * .30f, h * .18f), size = Size(w * .43f, h * .58f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f))
        drawRoundRect(Color(0xFF4D91D2), topLeft = Offset(w * .325f, h * .22f), size = Size(w * .38f, h * .49f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f))
        drawRoundRect(Color(0xFFD9EEFF), topLeft = Offset(w * .35f, h * .27f), size = Size(w * .33f, h * .38f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f, 5f))
        repeat(3) { row ->
            drawRoundRect(Color(0xFF8AC1EC), topLeft = Offset(w * (.37f + row * .095f), h * .32f), size = Size(w * .07f, h * .10f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f))
            drawLine(Color(0xFF9EBEDA), Offset(w * (.37f + row * .095f), h * .47f), Offset(w * (.44f + row * .095f), h * .47f), 4f)
            drawLine(Color(0xFFB1C9DE), Offset(w * (.37f + row * .095f), h * .51f), Offset(w * (.44f + row * .095f), h * .51f), 4f)
        }
        drawRoundRect(Color(0xFF1C527C), topLeft = Offset(w * .23f, h * .75f), size = Size(w * .57f, h * .075f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f, 5f))
        drawRoundRect(Color(0xFF2E76A9), topLeft = Offset(w * .15f, h * .82f), size = Size(w * .73f, h * .055f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f))
        drawRoundRect(Color(0xFFF0F4F7), topLeft = Offset(w * .80f, h * .62f), size = Size(w * .10f, h * .21f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f))
        drawOval(Color(0xFFE6EDF4), topLeft = Offset(w * .79f, h * .60f), size = Size(w * .12f, h * .05f))
        drawLine(Color(0xFF5AB883), Offset(w * .85f, h * .61f), Offset(w * .86f, h * .32f), 5f)
        repeat(5) { i ->
            drawOval(Color(if (i % 2 == 0) 0xFF35B27C else 0xFF2A8D66), topLeft = Offset(w * (.78f + (i % 3) * .04f), h * (.33f + i * .035f)), size = Size(w * .09f, h * .08f))
        }
        drawRoundRect(Color(0xFF1B5F8D), topLeft = Offset(w * .08f, h * .66f), size = Size(w * .09f, h * .17f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f))
        repeat(4) { i ->
            drawOval(Color(if (i % 2 == 0) 0xFF1D8F70 else 0xFF2AA276), topLeft = Offset(w * (.04f + i * .028f), h * (.44f + i * .04f)), size = Size(w * .12f, h * .11f))
        }
    }
}

@Composable
private fun QuickActions(c: DesktopController, onNavigate: (ReferenceSection) -> Unit, modifier: Modifier) {
    DashboardCard(modifier) {
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bolt, null, tint = Blue, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Ações rápidas", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Execute tarefas prioritárias com um clique.", color = Muted, fontSize = 10.sp)
                }
                Spacer(Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, Color(0xFFD7E5F6))) {
                    Row(Modifier.clickable { onNavigate(ReferenceSection.SETTINGS) }.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, null, tint = Ink, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Personalizar", color = Ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickActionButton("Buscar notícias", "Iniciar varredura agora", Icons.Default.Search, listOf(Color(0xFF0587FF), Color(0xFF0D66E9)), { c.searchNews() }, Modifier.weight(1f))
                    QuickActionButton("Buscar demandas", "Consultar demandas ativas", Icons.Default.Assignment, listOf(Color(0xFFFF850A), Color(0xFFFF9918)), { c.searchAllDemands() }, Modifier.weight(1f))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickActionButton("Buscar vídeos", "Pesquisar novos vídeos", Icons.Default.PlayCircleFilled, listOf(Color(0xFF6A30F3), Color(0xFF9147F8)), { c.searchVideos() }, Modifier.weight(1f))
                    QuickActionButton("Termos de busca", "Gerenciar palavras-chave", Icons.Default.Search, listOf(Color(0xFF06AA70), Color(0xFF16C590)), { onNavigate(ReferenceSection.TERMS) }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    colors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Brush.horizontalGradient(colors)).clickable(onClick = onClick).padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color.White.copy(alpha = .90f), fontSize = 10.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ScheduleCard(c: DesktopController, modifier: Modifier) {
    DashboardCard(modifier) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, null, tint = Blue, modifier = Modifier.size(31.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Agendamento automático", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    Text("O sistema executa buscas automaticamente nos horários definidos.", color = Muted, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                ScheduleMini("Notícias", "a cada ${c.newsIntervalMinutes} min", Icons.Default.Article, Blue, Color(0xFFDCEEFF), Modifier.weight(1f))
                ScheduleMini("Demandas", "a cada 60 min", Icons.Default.Assignment, Orange, Color(0xFFFFEBD6), Modifier.weight(1f))
                ScheduleMini("Vídeos", "08:00, 12:00, 15:00, 19:00, 21:00", Icons.Default.PlayCircleFilled, Purple, Color(0xFFECE0FF), Modifier.weight(1.25f))
            }
        }
    }
}

@Composable
private fun ScheduleMini(title: String, value: String, icon: ImageVector, accent: Color, bg: Color, modifier: Modifier) {
    Surface(modifier = modifier.fillMaxHeight(), color = Color(0xFFF8FBFF), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Color(0xFFE5EEF9))) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(43.dp).clip(RoundedCornerShape(10.dp)).background(bg), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(value, color = if (title == "Vídeos") Muted else Ink, fontSize = if (title == "Vídeos") 8.sp else 11.sp, fontWeight = if (title == "Vídeos") FontWeight.Normal else FontWeight.SemiBold, maxLines = 2)
            }
        }
    }
}

@Composable
private fun DaySummaryCard(c: DesktopController, modifier: Modifier) {
    DashboardCard(modifier) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BarChart, null, tint = Blue, modifier = Modifier.size(27.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Resumo do dia", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Panorama geral das últimas 24 horas.", color = Muted, fontSize = 10.sp)
                }
                Spacer(Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(10.dp), color = Color.White, border = BorderStroke(1.dp, Color(0xFFD7E5F5))) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Últimas 24 horas", color = Ink, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.ExpandMore, null, tint = Ink, modifier = Modifier.size(17.dp))
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
            Row(Modifier.weight(1f)) {
                DayChart(Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.width(90.dp).padding(top = 19.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LegendDot(Blue, "Notícias")
                    LegendDot(Purple, "Vídeos")
                    LegendDot(Orange, "Demandas")
                }
            }
        }
    }
}

@Composable
private fun DayChart(modifier: Modifier) {
    Canvas(modifier.padding(start = 14.dp, top = 4.dp, end = 8.dp)) {
        val left = 26f
        val top = 7f
        val right = size.width - 8f
        val bottom = size.height - 18f
        repeat(5) { i ->
            val y = top + (bottom - top) * i / 4f
            drawLine(Color(0xFFE4EDF8), Offset(left, y), Offset(right, y), 1f)
        }
        repeat(9) { i ->
            val x = left + (right - left) * i / 8f
            drawLine(Color(0xFFEAF1F9), Offset(x, top), Offset(x, bottom), 1f)
        }
        fun drawSeries(values: List<Float>, color: Color) {
            val p = Path()
            values.forEachIndexed { i, v ->
                val x = left + (right - left) * i / (values.size - 1).toFloat()
                val y = bottom - (bottom - top) * v
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                drawCircle(color, 2.8f, Offset(x, y))
            }
            drawPath(p, color, style = Stroke(2.1f))
        }
        drawSeries(listOf(.18f,.28f,.37f,.31f,.24f,.17f,.12f,.28f,.38f,.49f,.66f,.48f,.32f,.34f,.37f,.33f,.27f,.32f,.35f,.58f,.72f,.43f,.25f,.15f), Blue)
        drawSeries(listOf(.03f,.05f,.08f,.06f,.04f,.05f,.03f,.04f,.05f,.06f,.08f,.07f,.09f,.06f,.05f,.07f,.06f,.05f,.04f,.06f,.08f,.05f,.04f,.03f), Purple)
        drawSeries(listOf(.01f,.02f,.02f,.04f,.09f,.04f,.02f,.03f,.02f,.03f,.05f,.04f,.02f,.03f,.04f,.03f,.02f,.03f,.02f,.03f,.04f,.03f,.02f,.01f), Orange)
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(7.dp))
        Text(text, color = Muted, fontSize = 9.sp)
    }
}

@Composable
private fun RelevantSourcesCard(modifier: Modifier) {
    val sources = listOf("Agência Brasil" to "1.245", "CNN Brasil" to "982", "UOL" to "876", "Folha de S.Paulo" to "754", "Estadão" to "689")
    DashboardCard(modifier) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storage, null, tint = Blue, modifier = Modifier.size(27.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Fontes mais relevantes", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Suas principais fontes monitoradas.", color = Muted, fontSize = 9.sp)
                }
                Spacer(Modifier.weight(1f))
                Text("Ver todas", color = Blue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            sources.forEachIndexed { index, pair ->
                Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(Color(0xFFE4EFFB)), contentAlignment = Alignment.Center) {
                        Text("${index + 1}", color = Color(0xFF345B8B), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(9.dp))
                    Box(Modifier.size(19.dp).clip(RoundedCornerShape(4.dp)).background(if (index == 1) Color(0xFFB41018) else Color(0xFF0C4E90)), contentAlignment = Alignment.Center) {
                        Text(if (index == 1) "CN" else "●", color = Color.White, fontSize = if (index == 1) 7.sp else 8.sp)
                    }
                    Spacer(Modifier.width(9.dp))
                    Text(pair.first, color = Ink, fontSize = 10.sp, modifier = Modifier.weight(1f))
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Green))
                    Spacer(Modifier.width(5.dp))
                    Text("Ativa", color = Green, fontSize = 9.sp)
                    Spacer(Modifier.width(16.dp))
                    Text(pair.second, color = Ink, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
                if (index < sources.lastIndex) HorizontalDivider(color = Color(0xFFE8EFF8))
            }
        }
    }
}

@Composable
private fun LatestActivitiesCard(c: DesktopController, modifier: Modifier) {
    val time = SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date())
    DashboardCard(modifier) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, null, tint = Blue, modifier = Modifier.size(27.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Últimas atividades", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Histórico recente de ações no sistema.", color = Muted, fontSize = 9.sp)
                }
                Spacer(Modifier.weight(1f))
                Text("Ver histórico", color = Blue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            ActivityLine(Icons.Default.CheckCircle, Green, "Sistema iniciado", "Monitor de Notícias v4.0.2", time)
            ActivityLine(Icons.Default.Settings, Color(0xFF1B629B), "Configuração carregada", "Proxy autenticado", time)
            ActivityLine(Icons.Default.Schedule, Orange, "Agendamento ativo", "Próxima busca em ${c.newsIntervalMinutes} min", time)
            ActivityLine(Icons.Default.Search, Blue, "Busca de fontes concluída", "${SourceCatalog.all.size + VideoSourceCatalog.all.size} fontes verificadas", time)
        }
    }
}

@Composable
private fun ActivityLine(icon: ImageVector, accent: Color, title: String, subtitle: String, time: String) {
    Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(23.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = 9.sp, maxLines = 1)
        }
        Text(time, color = Muted, fontSize = 9.sp)
    }
}

@Composable
private fun TipsCard(modifier: Modifier) {
    DashboardCard(modifier) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lightbulb, null, tint = Yellow, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Text("Dicas e novidades", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.weight(1f))
                Surface(shape = CircleShape, border = BorderStroke(1.dp, Color(0xFFDCE7F5)), color = Color.White) {
                    Icon(Icons.Default.ChevronLeft, null, tint = Ink, modifier = Modifier.padding(5.dp).size(15.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text("1/3", color = Muted, fontSize = 10.sp)
                Spacer(Modifier.width(12.dp))
                Surface(shape = CircleShape, border = BorderStroke(1.dp, Color(0xFFDCE7F5)), color = Color.White) {
                    Icon(Icons.Default.ChevronRight, null, tint = Ink, modifier = Modifier.padding(5.dp).size(15.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Surface(Modifier.weight(1f).fillMaxWidth(), color = Color(0xFFEAF4FF), shape = RoundedCornerShape(13.dp)) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(58.dp).clip(CircleShape).background(Color(0xFFD4E9FF)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.School, null, tint = Blue, modifier = Modifier.size(34.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Use termos de busca específicos", color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(5.dp))
                        Text("Quanto mais específicos os termos, mais\nrelevantes serão os resultados.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Box(Modifier.size(11.dp).clip(CircleShape).background(Blue)); Spacer(Modifier.width(14.dp))
                Box(Modifier.size(11.dp).clip(CircleShape).background(Color(0xFFB9CBE2))); Spacer(Modifier.width(14.dp))
                Box(Modifier.size(11.dp).clip(CircleShape).background(Color(0xFFB9CBE2)))
            }
        }
    }
}

@Composable
private fun ReferenceFooter() {
    Row(
        Modifier.fillMaxWidth().height(44.dp).background(Color(0xFFF8FBFF)).border(1.dp, Color(0xFFD9E7F7)).padding(horizontal = 26.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Monitor de Notícias v4.0.2", color = Color(0xFF4C6D9F), fontSize = 11.sp)
        Spacer(Modifier.width(18.dp))
        Text("|", color = Color(0xFFB3C4DB), fontSize = 14.sp)
        Spacer(Modifier.width(18.dp))
        Text("Inteligência de mídia para melhores decisões", color = Color(0xFF4C6D9F), fontSize = 11.sp)
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(10.dp).clip(CircleShape).background(Green))
        Spacer(Modifier.width(9.dp))
        Text("Sistema operacional", color = Color(0xFF264D83), fontSize = 10.sp)
        Spacer(Modifier.width(22.dp))
        Text("|", color = Color(0xFFB3C4DB), fontSize = 14.sp)
        Spacer(Modifier.width(22.dp))
        Text("Feito para quem acompanha o mundo.", color = Color(0xFF4C6D9F), fontSize = 10.sp)
    }
}

@Composable
private fun PanelCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.shadow(6.dp, RoundedCornerShape(14.dp)), color = Color.White, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, CardBorder)) {
        Column(Modifier.fillMaxSize().padding(16.dp), content = content)
    }
}

@Composable
private fun NewsScreen(c: DesktopController) {
    var onlyDemands by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PanelCard(Modifier.fillMaxWidth().height(82.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { c.searchNews() }, enabled = !c.newsBusy) { Text("Buscar últimas 24h") }
                FilterChip(selected = onlyDemands, onClick = { onlyDemands = !onlyDemands }, label = { Text("Só Demandas") })
                OutlinedTextField(query, { query = it }, label = { Text("Filtrar") }, singleLine = true, modifier = Modifier.width(300.dp))
                Spacer(Modifier.weight(1f))
                Text(c.status, color = Muted, fontSize = 11.sp, maxLines = 2)
            }
        }
        ProgressPanel("Notícias", c.newsProgress)
        val items = c.news.filter { (!onlyDemands || it.demand) && (query.isBlank() || "${it.title} ${it.source} ${it.matchedTerm} ${it.matchedDemand}".contains(query, true)) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(items, key = { it.link }) { NewsCard(it) }
        }
    }
}

@Composable
private fun NewsCard(n: News) {
    Surface(Modifier.fillMaxWidth().clickable { openUrl(n.link) }, color = Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, CardBorder)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text(n.title, color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (System.currentTimeMillis() - n.capturedAt < 24 * 60 * 60 * 1000L) SuggestionChip(onClick = {}, label = { Text("NOVO") })
            }
            Text("${n.source} • ${formatDate(n.date)}", color = Muted, fontSize = 11.sp)
            if (n.snippet.isNotBlank()) Text(n.snippet, color = Color(0xFF314D75), fontSize = 12.sp, maxLines = 2)
            if (n.matchedTerm.isNotBlank()) Text("Termo: ${n.matchedTerm}", color = Muted, fontSize = 10.sp)
            if (n.matchedDemand.isNotBlank()) Text("Demanda: ${n.matchedDemand}", color = Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun VideosScreen(c: DesktopController) {
    var filter by remember { mutableStateOf(VideoFilter.ALL) }
    var query by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PanelCard(Modifier.fillMaxWidth().height(82.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { c.searchVideos() }, enabled = !c.videoBusy) { Text("Buscar vídeos") }
                VideoFilter.entries.forEach { f ->
                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(when (f) { VideoFilter.ALL -> "Todos"; VideoFilter.RELEVANT -> "Relevantes"; VideoFilter.DEMANDS -> "Demandas" }) })
                }
                OutlinedTextField(query, { query = it }, label = { Text("Filtrar") }, singleLine = true, modifier = Modifier.width(260.dp))
                Spacer(Modifier.weight(1f))
                Text(c.videoStatus, color = Muted, fontSize = 11.sp, maxLines = 2)
            }
        }
        ProgressPanel("Vídeos", c.videoProgress)
        val items = c.videos.filter {
            (filter == VideoFilter.ALL || filter == VideoFilter.RELEVANT && it.relevant || filter == VideoFilter.DEMANDS && it.demand) &&
                (query.isBlank() || "${it.title} ${it.sourceName} ${it.summary} ${it.matchedTerm} ${it.matchedDemand}".contains(query, true))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(items, key = { it.link }) { VideoCard(it) }
        }
    }
}

@Composable
private fun VideoCard(v: VideoItem) {
    Surface(Modifier.fillMaxWidth().clickable { openUrl(v.link) }, color = Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, CardBorder)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Icon(Icons.Default.PlayCircle, null, tint = Purple)
                Spacer(Modifier.width(8.dp))
                Text(v.title, color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 2)
                if (System.currentTimeMillis() - v.capturedAt < 24 * 60 * 60 * 1000L) SuggestionChip(onClick = {}, label = { Text("NOVO") })
            }
            Text("${v.sourceName} • ${formatDate(v.publishedAt)}", color = Muted, fontSize = 11.sp)
            if (v.summary.isNotBlank()) Text(v.summary, color = Color(0xFF314D75), fontSize = 12.sp, maxLines = 2)
            if (v.matchedTerm.isNotBlank()) Text("Termo: ${v.matchedTerm}", color = Muted, fontSize = 10.sp)
            if (v.matchedDemand.isNotBlank()) Text("Demanda: ${v.matchedDemand}", color = Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun TermsScreen(c: DesktopController) {
    var newsTerm by remember { mutableStateOf("") }
    var videoTerm by remember { mutableStateOf("") }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
        TermColumn("Termos de Notícias", c.terms, newsTerm, { newsTerm = it }, { c.addTerm(newsTerm); newsTerm = "" }, c::removeTerm, Modifier.weight(1f))
        TermColumn("Termos de Vídeos", c.videoTerms, videoTerm, { videoTerm = it }, { c.addVideoTerm(videoTerm); videoTerm = "" }, c::removeVideoTerm, Modifier.weight(1f))
    }
}

@Composable
private fun TermColumn(title: String, items: List<String>, value: String, onValue: (String) -> Unit, onAdd: () -> Unit, onRemove: (String) -> Unit, modifier: Modifier) {
    PanelCard(modifier.fillMaxHeight()) {
        Text(title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value, onValue, label = { Text("Novo termo") }, singleLine = true, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Button(onClick = onAdd, enabled = value.isNotBlank()) { Text("Adicionar") }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(items) { term ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(term, color = Ink, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onRemove(term) }) { Icon(Icons.Default.Delete, null) }
                }
                HorizontalDivider(color = Color(0xFFE7EFF9))
            }
        }
    }
}

@Composable
private fun DemandsScreen(c: DesktopController) {
    var vehicle by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PanelCard(Modifier.fillMaxWidth().height(96.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(vehicle, { vehicle = it }, label = { Text("Veículo") }, singleLine = true, modifier = Modifier.width(260.dp))
                OutlinedTextField(subject, { subject = it }, label = { Text("Assunto") }, singleLine = true, modifier = Modifier.weight(1f))
                Button(onClick = { c.addDemand(vehicle, subject); vehicle = ""; subject = "" }, enabled = vehicle.isNotBlank() && subject.isNotBlank()) { Text("Adicionar") }
                OutlinedButton(onClick = { c.searchAllDemands() }, enabled = !c.newsBusy) { Text("Buscar todas") }
            }
        }
        Text(c.status, color = Muted, fontSize = 11.sp)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(c.demands, key = { it.id }) { d ->
                Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, CardBorder)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${d.vehicle} • ${d.subject}", color = Ink, fontWeight = FontWeight.SemiBold)
                            Text("Última busca: ${if (d.lastCheckedAt > 0) formatDate(d.lastCheckedAt) else "nunca"} • encontrados ${d.lastFoundCount} • novos ${d.lastNewCount}", color = Muted, fontSize = 11.sp)
                            if (d.lastError.isNotBlank()) Text(d.lastError, color = Color(0xFFB2343B), fontSize = 10.sp)
                        }
                        OutlinedButton(onClick = { c.searchDemand(d) }, enabled = !c.newsBusy) { Text("Buscar") }
                        IconButton(onClick = { c.removeDemand(d.id) }) { Icon(Icons.Default.Delete, null) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourcesScreen(c: DesktopController) {
    var videos by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PanelCard(Modifier.fillMaxWidth().height(90.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = !videos, onClick = { videos = false }, label = { Text("Notícias") })
                FilterChip(selected = videos, onClick = { videos = true }, label = { Text("Vídeos") })
                OutlinedTextField(query, { query = it }, label = { Text("Filtrar fonte") }, singleLine = true, modifier = Modifier.width(300.dp))
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = { if (videos) c.selectAllVideoSources() else c.selectAllNewsSources() }) { Text("Selecionar todas") }
                OutlinedButton(onClick = { if (videos) c.clearVideoSources() else c.clearNewsSources() }) { Text("Limpar") }
            }
        }
        val list = if (videos) {
            VideoSourceCatalog.all.filter { query.isBlank() || "${it.name} ${it.group} ${it.state}".contains(query, true) }.map { Triple(it.id, it.name, "${it.group}${if (it.state.isNotBlank()) " • ${it.state}" else ""}") }
        } else {
            SourceCatalog.all.filter { query.isBlank() || "${it.name} ${it.group} ${it.stateName}".contains(query, true) }.map { Triple(it.id, it.name, "${it.group} • ${it.state}") }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(list, key = { it.first }) { s ->
                val checked = if (videos) s.first in c.selectedVideoSourceIds else c.newsAllSources || s.first in c.selectedNewsSourceIds
                Row(Modifier.fillMaxWidth().clickable { if (videos) c.setVideoSource(s.first, !checked) else c.setNewsSource(s.first, !checked) }.padding(vertical = 6.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked, onCheckedChange = { v -> if (videos) c.setVideoSource(s.first, v) else c.setNewsSource(s.first, v) })
                    Column { Text(s.second, color = Ink); Text(s.third, color = Muted, fontSize = 10.sp) }
                }
                HorizontalDivider(color = Color(0xFFE7EFF9))
            }
        }
    }
}

@Composable
private fun HistoryScreen(c: DesktopController) {
    var tab by remember { mutableStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PanelCard(Modifier.fillMaxWidth().height(72.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Notícias (${c.newsDb.listNews(2000).size})") })
                FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Vídeos (${c.videoDb.listAll(2000).size})") })
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = { if (tab == 0) c.clearNewsHistory() else c.clearVideoHistory() }) {
                    Icon(Icons.Default.Delete, null); Spacer(Modifier.width(4.dp)); Text("Limpar histórico")
                }
            }
        }
        if (tab == 0) {
            val all = c.newsDb.listNews(2000)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) { items(all, key = { it.link }) { NewsCard(it) } }
        } else {
            val all = c.videoDb.listAll(2000)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) { items(all, key = { it.link }) { VideoCard(it) } }
        }
    }
}

@Composable
private fun SettingsScreen(c: DesktopController) {
    var auto by remember { mutableStateOf(c.automaticMonitoring) }
    var interval by remember { mutableIntStateOf(c.newsIntervalMinutes) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            PanelCard(Modifier.fillMaxWidth().height(190.dp)) {
                Text("Monitoramento automático", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(auto, onCheckedChange = { auto = it; c.automaticMonitoring = it })
                    Spacer(Modifier.width(10.dp)); Text(if (auto) "Ativo — funciona enquanto o app estiver na bandeja" else "Pausado", color = Ink)
                }
                Spacer(Modifier.height(10.dp))
                Text("Intervalo automático de notícias", color = Muted, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 60, 120).forEach { m -> FilterChip(selected = interval == m, onClick = { interval = m; c.newsIntervalMinutes = m }, label = { Text("$m min") }) }
                }
            }
        }
        item {
            PanelCard(Modifier.fillMaxWidth().height(150.dp)) {
                Text("Dados portáteis", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Banco, termos, demandas, histórico e preferências ficam na pasta:", color = Muted)
                Spacer(Modifier.height(8.dp))
                SelectionContainer { Text(c.context.filesDir.absolutePath, color = Ink) }
            }
        }
        item {
            PanelCard(Modifier.fillMaxWidth().height(170.dp)) {
                Text("Compatibilidade funcional v4.0.2", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("O Windows usa o mesmo motor de Notícias/Vídeos da versão Android: catálogos, Globoplay, Jarvis, YouTube, telejornais regionais, cruzamento local de Termos/Demandas, histórico, deduplicação e regra de NOVO por primeira captura.", color = Color(0xFF314D75), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ProgressPanel(title: String, p: LiveSearchProgress) {
    if (!p.active && p.startedAt == 0L) return
    Surface(color = Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, CardBorder)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row { Text("$title • ${if (p.active) "buscando" else "concluído"}", color = Ink, fontWeight = FontWeight.SemiBold); Spacer(Modifier.weight(1f)); Text("${p.completed}/${p.total}", color = Muted) }
            LinearProgressIndicator(progress = { p.fraction }, modifier = Modifier.fillMaxWidth())
            Text("${p.currentSource}${if (p.currentQuery.isNotBlank()) " • ${p.currentQuery}" else ""}", color = Muted, fontSize = 11.sp)
            val end = if (p.active) System.currentTimeMillis() else p.finishedAt
            val sec = ((end - p.startedAt).coerceAtLeast(0L) / 1000)
            Text("Tempo ${sec / 60}:${(sec % 60).toString().padStart(2, '0')} • encontrados ${p.found} • novos ${p.newCount} • falhas ${p.errors}", color = Muted, fontSize = 10.sp)
        }
    }
}

private fun openUrl(url: String) {
    runCatching { if (AwtDesktop.isDesktopSupported()) AwtDesktop.getDesktop().browse(URI(url)) }
}

private fun formatDate(ms: Long): String = if (ms <= 0L) "—" else SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(ms))
