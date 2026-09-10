package br.com.monitordenoticias.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.*
import br.com.monitordenoticias.android.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.awt.Desktop as AwtDesktop
import java.net.URI
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val Navy = Color(0xFF062B55)
private val NavyDark = Color(0xFF031D3A)
private val Ink = Color(0xFF0B1F4B)
private val Muted = Color(0xFF5A75A7)
private val CanvasBg = Color(0xFFF2F7FE)
private val CardBorder = Color(0xFFDCE8F8)
private val Blue = Color(0xFF0879F9)
private val Purple = Color(0xFF7538F4)
private val Orange = Color(0xFFFF820B)
private val Green = Color(0xFF07AA72)
private val Pink = Color(0xFFD70659)
private val Yellow = Color(0xFFFFB52E)
private val Gold = Color(0xFFF1B514)

private enum class Section(val label: String, val icon: ImageVector, val subtitle: String) {
    HOME("Início", Icons.Default.Home, "Acompanhe notícias, vídeos, demandas e fontes em tempo real."),
    NEWS("Notícias", Icons.Default.Article, "Resultados capturados nas fontes monitoradas"),
    VIDEOS("Vídeos", Icons.Default.PlayCircle, "Vídeos relevantes localizados nas fontes monitoradas"),
    DEMANDS("Demandas", Icons.Default.NotificationsActive, "Assuntos prioritários acompanhados por veículo"),
    SOURCES("Fontes", Icons.Default.Storage, "Fontes nacionais, regionais e mídias especializadas"),
    HISTORY("Histórico", Icons.Default.History, "Histórico local de notícias e vídeos capturados"),
    TERMS("Termos de busca", Icons.Default.Search, "Palavras-chave utilizadas no monitoramento"),
    SETTINGS("Configurações", Icons.Default.Settings, "Preferências, automação, dados locais e proxy")
}

private enum class SourceTab { NEWS, VIDEOS, SPECIAL }
private data class WeatherInfo(val temperature: Int, val description: String)
private data class SearchHit(val title: String, val subtitle: String, val action: () -> Unit)

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
            App(controller)
        }
    }
}

@Composable
private fun App(c: DesktopController) {
    var section by remember { mutableStateOf(Section.HOME) }
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
            Sidebar(section, { section = it }, c)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (section == Section.HOME) {
                    HomeHeader(c, onNavigate = { section = it })
                    HomeScreen(c, onNavigate = { section = it })
                } else {
                    PageHeader(section, c)
                    Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
                        when (section) {
                            Section.NEWS -> NewsScreen(c)
                            Section.VIDEOS -> VideosScreen(c)
                            Section.DEMANDS -> DemandsScreen(c)
                            Section.SOURCES -> SourcesScreen(c)
                            Section.HISTORY -> HistoryScreen(c)
                            Section.TERMS -> TermsScreen(c)
                            Section.SETTINGS -> SettingsScreen(c)
                            Section.HOME -> Unit
                        }
                    }
                }
            }
        }
        Footer(c)
    }
}

@Composable
private fun Sidebar(selected: Section, onSelect: (Section) -> Unit, c: DesktopController) {
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
                Text("Inteligência de mídia", color = Color(0xFFB8CCE7), fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(26.dp))
        Section.entries.forEach { item ->
            val active = selected == item
            Row(
                Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(11.dp))
                    .background(if (active) Brush.horizontalGradient(listOf(Color(0xFF0B86FA), Color(0xFF1958C4))) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)))
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
                SidebarStatus(c.proxyStatusLabel, c.proxyReady || !c.proxyEnabled)
                SidebarStatus(if (c.automaticMonitoring) "Automação ativa" else "Automação pausada", c.automaticMonitoring)
                HorizontalDivider(color = Color(0xFF1E4E79))
                Text("Windows Portable v4.0.2", color = Color(0xFFC4D5E9), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SidebarStatus(text: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(if (ok) Color(0xFF15C786) else Yellow), contentAlignment = Alignment.Center) {
            Icon(if (ok) Icons.Default.Check else Icons.Default.PriorityHigh, null, tint = Color.White, modifier = Modifier.size(12.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(text, color = if (ok) Color(0xFFD5E4F5) else Color(0xFFFFD982), fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun HomeHeader(c: DesktopController, onNavigate: (Section) -> Unit) {
    val now = Date()
    val dateText = SimpleDateFormat("EEE, dd 'de' MMM 'de' yyyy", Locale("pt", "BR")).format(now)
    val timeText = SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(now)
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var weather by remember { mutableStateOf<WeatherInfo?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            weather = fetchBrasiliaWeather()
            delay(15L * 60L * 1000L)
        }
    }

    val q = query.trim()
    val hits = if (q.length < 2) emptyList() else buildList {
        c.news.filter { "${it.title} ${it.source}".contains(q, true) }.take(3).forEach { n -> add(SearchHit(n.title, "Notícia • ${n.source}") { openUrl(n.link) }) }
        c.videos.filter { "${it.title} ${it.sourceName}".contains(q, true) }.take(2).forEach { v -> add(SearchHit(v.title, "Vídeo • ${v.sourceName}") { openUrl(v.link) }) }
        c.demands.filter { "${it.vehicle} ${it.subject}".contains(q, true) }.take(2).forEach { d -> add(SearchHit("${d.vehicle} • ${d.subject}", "Demanda") { onNavigate(Section.DEMANDS) }) }
        SourceCatalog.all.filter { "${it.name} ${it.stateName}".contains(q, true) }.take(2).forEach { s -> add(SearchHit(s.name, "Fonte • ${s.group}") { onNavigate(Section.SOURCES) }) }
    }.take(8)

    Row(Modifier.fillMaxWidth().height(95.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Eco, null, tint = Green, modifier = Modifier.size(38.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Olá, bem-vindo! 👋", color = Ink, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                Text("Acompanhe notícias, vídeos, demandas e fontes em tempo real.", color = Muted, fontSize = 13.sp)
            }
        }

        Box {
            Surface(
                modifier = Modifier.width(455.dp).height(48.dp).shadow(7.dp, RoundedCornerShape(13.dp)),
                color = Color.White, shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, Color(0xFFD8E6F8))
            ) {
                Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, null, tint = Color(0xFF164B93), modifier = Modifier.size(25.dp))
                    Spacer(Modifier.width(12.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it; expanded = it.isNotBlank() },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(color = Ink, fontSize = 12.sp),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (query.isBlank()) Text("Buscar notícias, vídeos, demandas ou fontes...", color = Muted, fontSize = 12.sp)
                            inner()
                        }
                    )
                    Surface(color = Color(0xFFEAF2FC), shape = RoundedCornerShape(9.dp)) {
                        Text("buscar", color = Color(0xFF34537C), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
                    }
                }
            }
            DropdownMenu(expanded = expanded && q.length >= 2, onDismissRequest = { expanded = false }, modifier = Modifier.width(455.dp)) {
                if (hits.isEmpty()) DropdownMenuItem(text = { Text("Nenhum resultado encontrado") }, onClick = { expanded = false })
                else hits.forEach { hit ->
                    DropdownMenuItem(
                        text = { Column { Text(hit.title, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(hit.subtitle, color = Muted, fontSize = 10.sp) } },
                        onClick = { expanded = false; hit.action() }
                    )
                }
            }
        }

        Spacer(Modifier.width(20.dp))
        Icon(Icons.Default.NotificationsNone, null, tint = Color(0xFF184D91), modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(14.dp))
        Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF164B8C)).clickable { onNavigate(Section.SETTINGS) }, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(27.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(108.dp)) {
            Text(dateText, color = Muted, fontSize = 8.sp, maxLines = 2)
            Text(timeText, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.width(9.dp))
        Surface(color = Color(0xFFF7FBFF), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFFE1ECF9)), modifier = Modifier.width(126.dp).height(60.dp)) {
            Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WbSunny, null, tint = Color(0xFFFFB21A), modifier = Modifier.size(29.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Brasília - DF", color = Muted, fontSize = 8.sp)
                    Text(weather?.let { "${it.temperature}°C" } ?: "--°C", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(weather?.description ?: "Atualizando...", color = Muted, fontSize = 8.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun PageHeader(section: Section, c: DesktopController) {
    val now = Date()
    val dateText = SimpleDateFormat("EEEE, dd 'de' MMMM 'de'", Locale("pt", "BR")).format(now)
    val timeText = SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(now)
    Row(Modifier.fillMaxWidth().height(115.dp).padding(horizontal = 26.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(58.dp).clip(RoundedCornerShape(2.dp)).background(Gold))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("CENTRAL DE INTELIGÊNCIA DE MÍDIA", color = Color(0xFFBF8A00), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(section.label, color = Ink, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 30.sp)
            Text(section.subtitle, color = Muted, fontSize = 13.sp)
        }
        StatusPill(c.proxyStatusLabel, if (c.proxyReady) Green else Color(0xFFC98500), if (c.proxyReady) Icons.Default.VerifiedUser else Icons.Default.Security)
        Spacer(Modifier.width(12.dp))
        StatusPill(if (c.automaticMonitoring) "Automação ativa" else "Automação pausada", if (c.automaticMonitoring) Green else Orange, if (c.automaticMonitoring) Icons.Default.Radar else Icons.Default.PauseCircle)
        Spacer(Modifier.width(28.dp))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(180.dp)) {
            Text(dateText, color = Muted, fontSize = 10.sp)
            Text(timeText, color = Ink, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun StatusPill(text: String, accent: Color, icon: ImageVector) {
    Surface(color = accent.copy(alpha = .08f), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, accent.copy(alpha = .12f))) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun HomeScreen(c: DesktopController, onNavigate: (Section) -> Unit) {
    val now = System.currentTimeMillis()
    val todayVideos = c.videos.count { now - it.capturedAt < 24L * 60L * 60L * 1000L }
    val selectedSources = (if (c.newsAllSources) SourceCatalog.all.size else c.selectedNewsSourceIds.size) + c.selectedVideoSourceIds.size

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth().height(102.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Metric("Notícias 24h", c.news.size.toString(), "na janela atual", Icons.Default.Article, Blue, Color(0xFFD9EBFF), Modifier.weight(1f)) { onNavigate(Section.NEWS) }
            Metric("Vídeos", c.videos.size.toString(), "relevantes na base", Icons.Default.PlayCircleFilled, Purple, Color(0xFFECE0FF), Modifier.weight(1f)) { onNavigate(Section.VIDEOS) }
            Metric("Vídeos hoje", todayVideos.toString(), "capturados hoje", Icons.Default.Videocam, Green, Color(0xFFD3F8EC), Modifier.weight(1f)) { onNavigate(Section.VIDEOS) }
            Metric("Demandas", c.demands.count { it.active }.toString(), "${c.demands.count { it.active }} ativas", Icons.Default.Assignment, Orange, Color(0xFFFFEDD1), Modifier.weight(1f)) { onNavigate(Section.DEMANDS) }
            Metric("Fontes", (SourceCatalog.all.size + VideoSourceCatalog.all.size).toString(), "$selectedSources selecionadas", Icons.Default.Storage, Pink, Color(0xFFFFDDEA), Modifier.weight(1f)) { onNavigate(Section.SOURCES) }
        }

        Row(Modifier.fillMaxWidth().height(244.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Hero(c, Modifier.weight(1.38f))
            QuickActions(c, onNavigate, Modifier.weight(1f))
        }

        Row(Modifier.fillMaxWidth().height(158.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            DashboardCard(Modifier.weight(.94f)) {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, null, tint = Blue, modifier = Modifier.size(30.dp)); Spacer(Modifier.width(10.dp))
                        Column { Text("Agendamento automático", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold); Text("O sistema executa buscas automaticamente nos horários definidos.", color = Muted, fontSize = 10.sp) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        ScheduleMini("Notícias", "a cada ${c.newsIntervalMinutes} min", Icons.Default.Article, Blue, Color(0xFFDCEEFF), Modifier.weight(1f))
                        ScheduleMini("Demandas", "a cada 60 min", Icons.Default.Assignment, Orange, Color(0xFFFFEBD6), Modifier.weight(1f))
                        ScheduleMini("Vídeos", "08:00, 12:00, 15:00, 19:00, 21:00", Icons.Default.PlayCircleFilled, Purple, Color(0xFFECE0FF), Modifier.weight(1.25f))
                    }
                }
            }
            DashboardCard(Modifier.weight(1.06f)) {
                Column(Modifier.fillMaxSize().padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BarChart, null, tint = Blue, modifier = Modifier.size(27.dp)); Spacer(Modifier.width(10.dp))
                        Column { Text("Resumo do dia", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold); Text("Panorama real de notícias, vídeos e demandas.", color = Muted, fontSize = 10.sp) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        SummaryValue("Notícias", c.news.size, Blue)
                        SummaryValue("Vídeos", c.videos.size, Purple)
                        SummaryValue("Demandas", c.demands.count { it.active }, Orange)
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().height(205.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            RelevantSources(c, onNavigate, Modifier.weight(.92f))
            DashboardCard(Modifier.weight(.88f)) {
                Column(Modifier.fillMaxSize().padding(14.dp)) {
                    Text("Últimas atividades", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(10.dp))
                    Activity("Sistema operacional", "Dados locais carregados", Green)
                    Activity("Notícias", c.status, Blue)
                    Activity("Vídeos", c.videoStatus, Purple)
                    Activity("Proxy", c.proxyStatusLabel, if (c.proxyReady) Green else Orange)
                }
            }
            DashboardCard(Modifier.weight(.80f)) {
                Column(Modifier.fillMaxSize().padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Lightbulb, null, tint = Yellow); Spacer(Modifier.width(8.dp)); Text("Dicas e novidades", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold) }
                    Spacer(Modifier.height(12.dp))
                    Surface(Modifier.weight(1f).fillMaxWidth(), color = Color(0xFFEAF4FF), shape = RoundedCornerShape(13.dp)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.Center) {
                            Text("Use termos de busca específicos", color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp)); Text("Quanto mais específicos os termos, mais relevantes serão os resultados.", color = Muted, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun Metric(title: String, value: String, subtitle: String, icon: ImageVector, accent: Color, bg: Color, modifier: Modifier, onClick: () -> Unit) {
    DashboardCard(modifier.clickable(onClick = onClick)) {
        Row(Modifier.fillMaxSize().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(58.dp).clip(RoundedCornerShape(13.dp)).background(bg), contentAlignment = Alignment.Center) { Icon(icon, null, tint = accent, modifier = Modifier.size(32.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) { Text(title, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold); Text(value, color = Ink, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold); Text(subtitle, color = Muted, fontSize = 10.sp, maxLines = 1) }
            Spark(accent)
        }
    }
}

@Composable
private fun Spark(accent: Color) {
    Canvas(Modifier.width(54.dp).height(26.dp)) {
        val pts = listOf(.78f, .67f, .72f, .50f, .37f, .48f, .18f)
        val p = Path()
        pts.forEachIndexed { i, v -> val x = i * size.width / (pts.size - 1); val y = v * size.height; if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }
        drawPath(p, accent, style = Stroke(2.2f))
    }
}

@Composable
private fun DashboardCard(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Surface(modifier = modifier.shadow(7.dp, RoundedCornerShape(14.dp)), color = Color.White, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, CardBorder)) { Box(Modifier.fillMaxSize(), content = content) }
}

@Composable
private fun Hero(c: DesktopController, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Color(0xFF07284F), Color(0xFF0A4C86), Color(0xFF2F85D4))))) {
        Column(Modifier.fillMaxHeight().width(440.dp).padding(28.dp)) {
            Text("CENTRAL DE INTELIGÊNCIA DE MÍDIA", color = Color(0xFFC4D8F1), fontSize = 11.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            Text("Tudo o que importa\nem um só lugar.", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 33.sp)
            Spacer(Modifier.height(8.dp)); Text("Buscas e resultados atualizados automaticamente,\nem tempo real.", color = Color(0xFFE6F0FB), fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Surface(color = Color(0xFF074F55), shape = RoundedCornerShape(11.dp), border = BorderStroke(1.dp, Color(0xFF0A8A7B))) {
                Row(Modifier.width(330.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(17.dp).clip(CircleShape).background(Color(0xFF27E58D))); Spacer(Modifier.width(10.dp))
                    Column { Text("Status: ${if (c.automaticMonitoring) "Pronto" else "Pausado"}", color = Color(0xFF2AE795), fontSize = 12.sp, fontWeight = FontWeight.Bold); Text(c.proxyStatusLabel, color = Color(0xFFB8D7E2), fontSize = 10.sp) }
                }
            }
        }
        Icon(Icons.Default.LaptopWindows, null, tint = Color.White.copy(alpha = .30f), modifier = Modifier.align(Alignment.CenterEnd).padding(end = 60.dp).size(190.dp))
    }
}

@Composable
private fun QuickActions(c: DesktopController, onNavigate: (Section) -> Unit, modifier: Modifier) {
    DashboardCard(modifier) {
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bolt, null, tint = Blue, modifier = Modifier.size(30.dp)); Spacer(Modifier.width(8.dp))
                Column { Text("Ações rápidas", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold); Text("Execute tarefas prioritárias com um clique.", color = Muted, fontSize = 10.sp) }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = { onNavigate(Section.SETTINGS) }) { Icon(Icons.Default.Settings, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("Personalizar") }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionButton("Buscar notícias", "Iniciar varredura agora", Icons.Default.Search, Blue, { c.searchNews() }, Modifier.weight(1f))
                    ActionButton("Buscar demandas", "Consultar demandas ativas", Icons.Default.Assignment, Orange, { c.searchAllDemands() }, Modifier.weight(1f))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionButton("Buscar vídeos", "Pesquisar novos vídeos", Icons.Default.PlayCircleFilled, Purple, { c.searchVideos() }, Modifier.weight(1f))
                    ActionButton("Termos de busca", "Gerenciar palavras-chave", Icons.Default.Search, Green, { onNavigate(Section.TERMS) }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ActionButton(title: String, subtitle: String, icon: ImageVector, accent: Color, onClick: () -> Unit, modifier: Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(accent).clickable(onClick = onClick).padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(29.dp)); Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) { Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = Color.White.copy(alpha = .9f), fontSize = 10.sp) }
        Icon(Icons.Default.ChevronRight, null, tint = Color.White)
    }
}

@Composable
private fun ScheduleMini(title: String, value: String, icon: ImageVector, accent: Color, bg: Color, modifier: Modifier) {
    Surface(modifier = modifier.fillMaxHeight(), color = Color(0xFFF8FBFF), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Color(0xFFE5EEF9))) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(43.dp).clip(RoundedCornerShape(10.dp)).background(bg), contentAlignment = Alignment.Center) { Icon(icon, null, tint = accent, modifier = Modifier.size(25.dp)) }
            Spacer(Modifier.width(10.dp)); Column { Text(title, color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold); Text(value, color = Muted, fontSize = if (title == "Vídeos") 8.sp else 11.sp, maxLines = 2) }
        }
    }
}

@Composable
private fun SummaryValue(label: String, value: Int, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(value.toString(), color = accent, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold); Text(label, color = Muted, fontSize = 10.sp) }
}

@Composable
private fun Activity(title: String, subtitle: String, accent: Color) {
    Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(accent)); Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) { Text(title, color = Ink, fontSize = 10.sp, fontWeight = FontWeight.SemiBold); Text(subtitle, color = Muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun RelevantSources(c: DesktopController, onNavigate: (Section) -> Unit, modifier: Modifier) {
    val ranking = c.news.groupingBy { it.source }.eachCount().entries.sortedByDescending { it.value }.take(5)
    DashboardCard(modifier) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storage, null, tint = Blue); Spacer(Modifier.width(8.dp)); Text("Fontes mais relevantes", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.weight(1f)); Text("Ver todas", color = Blue, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onNavigate(Section.SOURCES) })
            }
            Spacer(Modifier.height(8.dp))
            if (ranking.isEmpty()) Text("Nenhum ranking disponível ainda.", color = Muted, fontSize = 11.sp)
            ranking.forEachIndexed { index, e ->
                Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}", color = Muted, fontSize = 10.sp, modifier = Modifier.width(22.dp)); Text(e.key, color = Ink, fontSize = 10.sp, modifier = Modifier.weight(1f)); Box(Modifier.size(6.dp).clip(CircleShape).background(Green)); Spacer(Modifier.width(6.dp)); Text(e.value.toString(), color = Ink, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun NewsScreen(c: DesktopController) {
    var query by remember { mutableStateOf("") }
    var demandsOnly by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PanelCard(Modifier.fillMaxWidth().height(82.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { c.searchNews() }, enabled = !c.newsBusy) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(5.dp)); Text("Buscar últimas 24h") }
                FilterChip(selected = demandsOnly, onClick = { demandsOnly = !demandsOnly }, label = { Text("Só demandas") })
                OutlinedTextField(query, { query = it }, placeholder = { Text("Filtrar notícias...") }, singleLine = true, modifier = Modifier.width(320.dp))
                Spacer(Modifier.weight(1f)); Text(c.status, color = Muted, fontSize = 11.sp, maxLines = 2)
            }
        }
        ProgressPanel("Notícias", c.newsProgress)
        val items = c.news.filter { (!demandsOnly || it.demand) && (query.isBlank() || "${it.title} ${it.source}".contains(query, true)) }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(items, key = { it.link }) { n -> NewsCard(n) } }
    }
}

@Composable
private fun NewsCard(n: News) {
    Surface(Modifier.fillMaxWidth().clickable { openUrl(n.link) }, color = Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, CardBorder)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(n.title, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${n.source} • ${formatDate(n.date)}", color = Muted, fontSize = 11.sp)
            if (n.snippet.isNotBlank()) Text(n.snippet, color = Color(0xFF314D75), fontSize = 12.sp, maxLines = 2)
        }
    }
}

@Composable
private fun VideosScreen(c: DesktopController) {
    var query by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PanelCard(Modifier.fillMaxWidth().height(82.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { c.searchVideos() }, enabled = !c.videoBusy) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(5.dp)); Text("Buscar vídeos") }
                OutlinedTextField(query, { query = it }, placeholder = { Text("Filtrar vídeos...") }, singleLine = true, modifier = Modifier.width(340.dp))
                Spacer(Modifier.weight(1f)); Text(c.videoStatus, color = Muted, fontSize = 11.sp, maxLines = 2)
            }
        }
        ProgressPanel("Vídeos", c.videoProgress)
        val items = c.videos.filter { query.isBlank() || "${it.title} ${it.sourceName}".contains(query, true) }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(items, key = { it.link }) { v -> VideoCard(v) } }
    }
}

@Composable
private fun VideoCard(v: VideoItem) {
    Surface(Modifier.fillMaxWidth().clickable { openUrl(v.link) }, color = Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, CardBorder)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PlayCircleFilled, null, tint = Purple, modifier = Modifier.size(30.dp)); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(v.title, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 2); Text("${v.sourceName} • ${formatDate(v.publishedAt)}", color = Muted, fontSize = 11.sp); if (v.summary.isNotBlank()) Text(v.summary, color = Color(0xFF314D75), fontSize = 11.sp, maxLines = 2) }
        }
    }
}

@Composable
private fun SourcesScreen(c: DesktopController) {
    var tab by remember { mutableStateOf(SourceTab.NEWS) }
    var query by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("Todas") }
    var state by remember { mutableStateOf("Todos") }

    val specialSources = VideoSourceCatalog.all.take(8)
    val newsVisible = SourceCatalog.all.filter {
        (region == "Todas" || it.region == region) &&
            (state == "Todos" || it.state == state) &&
            (query.isBlank() || "${it.name} ${it.group} ${it.stateName} ${it.region}".contains(query, true))
    }
    val videoBase = if (tab == SourceTab.SPECIAL) specialSources else VideoSourceCatalog.all
    val videoVisible = videoBase.filter {
        (region == "Todas" || it.region == region || (region == "Nacional" && it.state.isBlank())) &&
            (state == "Todos" || it.state == state) &&
            (query.isBlank() || "${it.name} ${it.group} ${it.state} ${it.region}".contains(query, true))
    }
    val visibleCount = if (tab == SourceTab.NEWS) newsVisible.size else videoVisible.size

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PanelCard(Modifier.fillMaxWidth().height(195.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SourceTabButton("Notícias", Icons.Default.Article, tab == SourceTab.NEWS) { tab = SourceTab.NEWS; region = "Todas"; state = "Todos" }
                Spacer(Modifier.width(8.dp)); SourceTabButton("Vídeos", Icons.Default.PlayCircleFilled, tab == SourceTab.VIDEOS) { tab = SourceTab.VIDEOS; region = "Todas"; state = "Todos" }
                Spacer(Modifier.width(8.dp)); SourceTabButton("Mídias especializadas (8)", Icons.Default.Verified, tab == SourceTab.SPECIAL) { tab = SourceTab.SPECIAL; region = "Todas"; state = "Todos" }
                Spacer(Modifier.weight(1f))
                Surface(Modifier.width(410.dp).height(48.dp), color = Color.White, shape = RoundedCornerShape(11.dp), border = BorderStroke(1.dp, CardBorder)) {
                    Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, null, tint = Color(0xFF174B8B)); Spacer(Modifier.width(10.dp))
                        BasicTextField(query, { query = it }, singleLine = true, modifier = Modifier.weight(1f), textStyle = LocalTextStyle.current.copy(color = Ink, fontSize = 12.sp), decorationBox = { inner -> if (query.isBlank()) Text("Pesquisar fonte...", color = Muted, fontSize = 12.sp); inner() })
                        Surface(color = Color(0xFFF0F5FC), shape = RoundedCornerShape(7.dp)) { Text("Ctrl + K", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Região", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(70.dp))
                SourceCatalog.regions.forEach { r -> FilterPill(r, region == r) { region = r; state = "Todos" }; Spacer(Modifier.width(7.dp)) }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Estado", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(70.dp))
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    FilterPill("Todos", state == "Todos") { state = "Todos" }; Spacer(Modifier.width(7.dp))
                    SourceCatalog.states.filter { region == "Todas" || region == "Nacional" || it.third == region }.forEach { s -> FilterPill(s.first, state == s.first) { state = s.first }; Spacer(Modifier.width(7.dp)) }
                }
            }
        }

        PanelCard(Modifier.fillMaxWidth().height(82.dp)) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(Color(0xFFE6F2FF)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Storage, null, tint = Blue, modifier = Modifier.size(31.dp)) }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("$visibleCount fonte(s) visível(is)", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    Text(if (tab == SourceTab.NEWS && c.newsAllSources) "Busca em todos os veículos ativa — inclui também as mídias especializadas Windows." else "Selecione as fontes que participarão das próximas buscas.", color = Muted, fontSize = 10.sp)
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = {
                    if (tab == SourceTab.NEWS) newsVisible.forEach { c.setNewsSource(it.id, true) }
                    else videoVisible.forEach { c.setVideoSource(it.id, true) }
                }) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(5.dp)); Text("Selecionar visíveis") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    if (tab == SourceTab.NEWS) newsVisible.forEach { c.setNewsSource(it.id, false) }
                    else videoVisible.forEach { c.setVideoSource(it.id, false) }
                }) { Icon(Icons.Default.DeleteOutline, null); Spacer(Modifier.width(5.dp)); Text("Limpar visíveis") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { if (tab == SourceTab.NEWS) c.selectAllNewsSources() else c.selectAllVideoSources() }) { Icon(Icons.Default.List, null); Spacer(Modifier.width(4.dp)); Text("Todas") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { if (tab == SourceTab.NEWS) c.clearNewsSources() else c.clearVideoSources() }) { Icon(Icons.Default.Block, null); Spacer(Modifier.width(4.dp)); Text("Nenhuma") }
            }
        }

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            if (tab == SourceTab.NEWS) {
                items(newsVisible, key = { it.id }) { source ->
                    val checked = c.newsAllSources || source.id in c.selectedNewsSourceIds
                    SourceRow(source.name, "${source.group}  •  ${if (source.region == "Nacional") "Nacional  •  BR" else "${source.region}  •  ${source.state}"}", checked, source.name.take(2).uppercase()) { c.setNewsSource(source.id, it) }
                }
            } else {
                items(videoVisible, key = { it.id }) { source ->
                    val checked = source.id in c.selectedVideoSourceIds
                    SourceRow(source.name, "${source.group}  •  ${source.region}${if (source.state.isNotBlank()) "  •  ${source.state}" else ""}", checked, source.name.take(2).uppercase()) { c.setVideoSource(source.id, it) }
                }
            }
        }
    }
}

@Composable
private fun SourceTabButton(text: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Surface(color = if (selected) Color(0xFFEDE8FF) else Color.White, shape = RoundedCornerShape(9.dp), border = BorderStroke(1.dp, if (selected) Color(0xFFD9CCFF) else CardBorder)) {
        Row(Modifier.clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (selected) Blue else Color(0xFF155A9C), modifier = Modifier.size(21.dp)); Spacer(Modifier.width(7.dp)); Text(text, color = Ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(color = if (selected) Color(0xFFDCEBFF) else Color.White, shape = RoundedCornerShape(9.dp), border = BorderStroke(1.dp, if (selected) Color(0xFFBFDAFF) else CardBorder)) {
        Text(text, color = if (selected) Color(0xFF0A65C7) else Muted, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 15.dp, vertical = 9.dp))
    }
}

@Composable
private fun SourceRow(name: String, detail: String, checked: Boolean, initials: String, onChecked: (Boolean) -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, CardBorder), shadowElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().height(59.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = onChecked)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.width(66.dp).height(38.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0B4B8B)), contentAlignment = Alignment.Center) { Text(initials, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) { Text(name, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold); Text(detail, color = Muted, fontSize = 10.sp) }
            Icon(Icons.Default.ChevronRight, null, tint = Blue)
        }
    }
}

@Composable
private fun DemandsScreen(c: DesktopController) {
    var vehicle by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PanelCard(Modifier.fillMaxWidth().height(105.dp)) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    vehicle, { vehicle = it },
                    label = { Text("Veículo") },
                    placeholder = { Text("Selecione ou digite o veículo...") },
                    leadingIcon = { Icon(Icons.Default.DirectionsCar, null) },
                    trailingIcon = { Icon(Icons.Default.ExpandMore, null) },
                    singleLine = true,
                    modifier = Modifier.weight(.95f)
                )
                OutlinedTextField(
                    subject, { subject = it },
                    label = { Text("Assunto") },
                    placeholder = { Text("Digite o assunto da demanda...") },
                    leadingIcon = { Icon(Icons.Default.Article, null) },
                    singleLine = true,
                    modifier = Modifier.weight(1.25f)
                )
                Button(onClick = { c.addDemand(vehicle, subject); vehicle = ""; subject = "" }, enabled = vehicle.isNotBlank() && subject.isNotBlank(), modifier = Modifier.height(55.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Adicionar") }
                OutlinedButton(onClick = { c.searchAllDemands() }, enabled = !c.newsBusy, modifier = Modifier.height(55.dp)) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Buscar todas") }
            }
        }

        Surface(Modifier.fillMaxWidth().height(72.dp), color = Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, CardBorder)) {
            Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(18.dp).clip(CircleShape).background(if (c.newsBusy) Orange else Green)); Spacer(Modifier.width(18.dp))
                Column { Text(if (c.newsBusy) "Status: Buscando" else "Status: Pronto", color = if (c.newsBusy) Orange else Color(0xFF0B8B55), fontSize = 14.sp, fontWeight = FontWeight.Bold); Text(if (c.newsBusy) c.status else "Sistema disponível para consultar e gerenciar demandas.", color = Muted, fontSize = 11.sp) }
            }
        }

        if (c.demands.isEmpty()) {
            Surface(Modifier.weight(1f).fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, CardBorder)) {
                Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.weight(.35f))
                    Box(Modifier.size(185.dp).clip(CircleShape).background(Color(0xFFF0F6FF)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.NotificationsActive, null, tint = Color(0xFF83B9F3), modifier = Modifier.size(94.dp))
                        Box(Modifier.align(Alignment.TopEnd).offset((-18).dp, 22.dp).size(55.dp).clip(CircleShape).background(Color(0xFFFFF3C6)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Notifications, null, tint = Gold, modifier = Modifier.size(30.dp)) }
                    }
                    Spacer(Modifier.height(24.dp))
                    Text("Nenhuma demanda carregada no momento", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(8.dp))
                    Text("Adicione uma nova demanda ou utilize os filtros para buscar\ndemandas já cadastradas.", color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { }, modifier = Modifier.height(50.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(7.dp)); Text("Adicionar demanda") }
                        Button(onClick = { c.searchAllDemands() }, enabled = !c.newsBusy, modifier = Modifier.height(50.dp)) { Icon(Icons.Default.Search, null); Spacer(Modifier.width(7.dp)); Text("Buscar todas") }
                    }
                    Spacer(Modifier.weight(.35f))
                    HorizontalDivider(color = Color(0xFFE8EFF8))
                    Spacer(Modifier.height(13.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.LightbulbOutline, null, tint = Blue); Spacer(Modifier.width(8.dp)); Text("Dica", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.width(12.dp)); Text("As demandas ajudam a acompanhar temas e assuntos prioritários nos veículos monitorados.", color = Muted, fontSize = 11.sp) }
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(c.demands, key = { it.id }) { d ->
                    Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, CardBorder)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(42.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFFE8F2FF)), contentAlignment = Alignment.Center) { Icon(Icons.Default.NotificationsActive, null, tint = Blue) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${d.vehicle} • ${d.subject}", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("Última busca: ${if (d.lastCheckedAt > 0) formatDate(d.lastCheckedAt) else "nunca"} • encontrados ${d.lastFoundCount} • novos ${d.lastNewCount}", color = Muted, fontSize = 10.sp)
                                if (d.lastError.isNotBlank()) Text(d.lastError, color = Color(0xFFB2343B), fontSize = 10.sp)
                            }
                            OutlinedButton(onClick = { c.searchDemand(d) }, enabled = !c.newsBusy) { Icon(Icons.Default.Search, null); Spacer(Modifier.width(5.dp)); Text("Buscar") }
                            IconButton(onClick = { c.removeDemand(d.id) }) { Icon(Icons.Default.DeleteOutline, null, tint = Color(0xFFB2343B)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TermsScreen(c: DesktopController) {
    var newsTerm by remember { mutableStateOf("") }
    var videoTerm by remember { mutableStateOf("") }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        TermPanel("Termos de Notícias", c.terms, newsTerm, { newsTerm = it }, { c.addTerm(newsTerm); newsTerm = "" }, c::removeTerm, Modifier.weight(1f))
        TermPanel("Termos de Vídeos", c.videoTerms, videoTerm, { videoTerm = it }, { c.addVideoTerm(videoTerm); videoTerm = "" }, c::removeVideoTerm, Modifier.weight(1f))
    }
}

@Composable
private fun TermPanel(title: String, items: List<String>, value: String, onValue: (String) -> Unit, onAdd: () -> Unit, onRemove: (String) -> Unit, modifier: Modifier) {
    PanelCard(modifier.fillMaxHeight()) {
        Text(title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(value, onValue, placeholder = { Text("Novo termo") }, singleLine = true, modifier = Modifier.weight(1f)); Spacer(Modifier.width(8.dp)); Button(onClick = onAdd, enabled = value.isNotBlank()) { Text("Adicionar") } }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f)) { items(items) { term -> Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) { Text(term, color = Ink, modifier = Modifier.weight(1f)); IconButton(onClick = { onRemove(term) }) { Icon(Icons.Default.DeleteOutline, null) } }; HorizontalDivider(color = Color(0xFFE7EFF9)) } }
    }
}

@Composable
private fun HistoryScreen(c: DesktopController) {
    var tab by remember { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PanelCard(Modifier.fillMaxWidth().height(74.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Notícias (${c.newsDb.listNews(2000).size})") }); Spacer(Modifier.width(8.dp)); FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Vídeos (${c.videoDb.listAll(2000).size})") })
                Spacer(Modifier.weight(1f)); OutlinedButton(onClick = { if (tab == 0) c.clearNewsHistory() else c.clearVideoHistory() }) { Icon(Icons.Default.DeleteOutline, null); Spacer(Modifier.width(5.dp)); Text("Limpar histórico") }
            }
        }
        if (tab == 0) { val all = c.newsDb.listNews(2000); LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(all, key = { it.link }) { NewsCard(it) } } }
        else { val all = c.videoDb.listAll(2000); LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(all, key = { it.link }) { VideoCard(it) } } }
    }
}

@Composable
private fun SettingsScreen(c: DesktopController) {
    var auto by remember { mutableStateOf(c.automaticMonitoring) }
    var interval by remember { mutableIntStateOf(c.newsIntervalMinutes) }
    var proxyEnabled by remember { mutableStateOf(c.proxyEnabled) }
    var proxyHost by remember { mutableStateOf(c.proxyHost) }
    var proxyPort by remember { mutableStateOf(c.proxyPort.toString()) }
    var proxyUser by remember { mutableStateOf(c.proxyUsername) }
    var proxyPass by remember { mutableStateOf(c.proxyPassword) }
    var savedMessage by remember { mutableStateOf("") }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            PanelCard(Modifier.fillMaxWidth().height(170.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Radar, null, tint = Green); Spacer(Modifier.width(8.dp)); Text("Monitoramento automático", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold) }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) { Switch(auto, onCheckedChange = { auto = it; c.automaticMonitoring = it }); Spacer(Modifier.width(10.dp)); Text(if (auto) "Ativo — continua funcionando na bandeja do Windows" else "Pausado", color = Ink) }
                Spacer(Modifier.height(10.dp)); Text("Intervalo automático de notícias", color = Muted, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(15, 30, 60, 120).forEach { m -> FilterChip(selected = interval == m, onClick = { interval = m; c.newsIntervalMinutes = m }, label = { Text("$m min") }) } }
            }
        }

        item {
            PanelCard(Modifier.fillMaxWidth().height(335.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFFFFF1D3)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Security, null, tint = Color(0xFFC98500)) }
                    Spacer(Modifier.width(12.dp))
                    Column { Text("Proxy autenticado", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold); Text("Configure o proxy utilizado pelas buscas HTTP/HTTPS do monitor.", color = Muted, fontSize = 11.sp) }
                    Spacer(Modifier.weight(1f)); Switch(proxyEnabled, onCheckedChange = { proxyEnabled = it })
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(proxyHost, { proxyHost = it }, label = { Text("Servidor") }, placeholder = { Text("proxy-7db.mb") }, singleLine = true, modifier = Modifier.weight(1.35f))
                    OutlinedTextField(proxyPort, { proxyPort = it.filter(Char::isDigit).take(5) }, label = { Text("Porta") }, placeholder = { Text("6060") }, singleLine = true, modifier = Modifier.weight(.65f))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(proxyUser, { proxyUser = it }, label = { Text("Usuário") }, singleLine = true, leadingIcon = { Icon(Icons.Default.Person, null) }, modifier = Modifier.weight(1f))
                    OutlinedTextField(proxyPass, { proxyPass = it }, label = { Text("Senha") }, singleLine = true, leadingIcon = { Icon(Icons.Default.Lock, null) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = if (c.proxyReady) Color(0xFFE8F9F1) else Color(0xFFFFF5DE), shape = RoundedCornerShape(9.dp)) { Text(c.proxyStatusLabel, color = if (c.proxyReady) Color(0xFF0B8B55) else Color(0xFFB17600), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) }
                    Spacer(Modifier.weight(1f))
                    if (savedMessage.isNotBlank()) { Text(savedMessage, color = Green, fontSize = 11.sp); Spacer(Modifier.width(12.dp)) }
                    Button(onClick = {
                        val port = proxyPort.toIntOrNull() ?: 6060
                        c.saveProxy(proxyEnabled, proxyHost.ifBlank { "proxy-7db.mb" }, port, proxyUser, proxyPass)
                        proxyHost = c.proxyHost; proxyPort = c.proxyPort.toString(); savedMessage = "Configuração salva e aplicada"
                    }) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(6.dp)); Text("Salvar e aplicar") }
                }
                Spacer(Modifier.height(8.dp)); Text("Servidor padrão solicitado: proxy-7db.mb:6060. O usuário e a senha ficam salvos nos dados locais do aplicativo para autenticação automática.", color = Muted, fontSize = 10.sp)
            }
        }

        item {
            PanelCard(Modifier.fillMaxWidth().height(135.dp)) {
                Text("Dados portáteis", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp)); Text("Banco, termos, demandas, histórico e preferências ficam na pasta:", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp)); Surface(color = Color(0xFFF6F9FE), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, CardBorder)) { Text(c.context.filesDir.absolutePath, color = Ink, fontSize = 11.sp, modifier = Modifier.padding(10.dp)) }
            }
        }
    }
}

@Composable
private fun PanelCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.shadow(5.dp, RoundedCornerShape(14.dp)), color = Color.White, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, CardBorder)) { Column(Modifier.fillMaxSize().padding(16.dp), content = content) }
}

@Composable
private fun ProgressPanel(title: String, p: LiveSearchProgress) {
    if (!p.active && p.startedAt == 0L) return
    Surface(color = Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, CardBorder)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row { Text("$title • ${if (p.active) "buscando" else "concluído"}", color = Ink, fontWeight = FontWeight.SemiBold); Spacer(Modifier.weight(1f)); Text("${p.completed}/${p.total}", color = Muted) }
            Spacer(Modifier.height(6.dp)); LinearProgressIndicator(progress = { p.fraction }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(5.dp)); Text("${p.currentSource}${if (p.currentQuery.isNotBlank()) " • ${p.currentQuery}" else ""}", color = Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun Footer(c: DesktopController) {
    Row(Modifier.fillMaxWidth().height(44.dp).background(Color(0xFFF8FBFF)).border(1.dp, Color(0xFFD9E7F7)).padding(horizontal = 26.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Monitor de Notícias v4.0.2", color = Color(0xFF4C6D9F), fontSize = 11.sp); Spacer(Modifier.width(18.dp)); Text("|", color = Color(0xFFB3C4DB)); Spacer(Modifier.width(18.dp)); Text("Inteligência de mídia para melhores decisões", color = Color(0xFF4C6D9F), fontSize = 11.sp)
        Spacer(Modifier.weight(1f)); Box(Modifier.size(10.dp).clip(CircleShape).background(Green)); Spacer(Modifier.width(8.dp)); Text("Sistema operacional", color = Color(0xFF264D83), fontSize = 10.sp); Spacer(Modifier.width(22.dp)); Text("|", color = Color(0xFFB3C4DB)); Spacer(Modifier.width(22.dp)); Text(c.proxyStatusLabel, color = Color(0xFF4C6D9F), fontSize = 10.sp)
    }
}

private suspend fun fetchBrasiliaWeather(): WeatherInfo? = withContext(Dispatchers.IO) {
    runCatching {
        val text = URL("https://api.open-meteo.com/v1/forecast?latitude=-15.7939&longitude=-47.8828&current=temperature_2m,weather_code&timezone=America%2FSao_Paulo").readText()
        val current = JSONObject(text).getJSONObject("current")
        WeatherInfo(current.getDouble("temperature_2m").roundToInt(), weatherDescription(current.getInt("weather_code")))
    }.getOrNull()
}

private fun weatherDescription(code: Int): String = when (code) {
    0 -> "Tempo limpo"
    1, 2 -> "Parcialmente nublado"
    3 -> "Nublado"
    45, 48 -> "Neblina"
    51, 53, 55, 56, 57 -> "Garoa"
    61, 63, 65, 66, 67, 80, 81, 82 -> "Chuva"
    71, 73, 75, 77, 85, 86 -> "Neve"
    95, 96, 99 -> "Trovoadas"
    else -> "Condição atual"
}

private fun openUrl(url: String) {
    runCatching { if (AwtDesktop.isDesktopSupported()) AwtDesktop.getDesktop().browse(URI(url)) }
}

private fun formatDate(ms: Long): String = if (ms <= 0L) "—" else SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(ms))
