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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.awt.Desktop as AwtDesktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI
import java.net.URLEncoder
import java.net.URL
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val Navy = Color(0xFF052D57)
private val NavyDark = Color(0xFF031F3E)
private val Ink = Color(0xFF0A1F4B)
private val Muted = Color(0xFF6079A5)
private val Bg = Color(0xFFF3F8FE)
private val Border = Color(0xFFD7E5F4)
private val Blue = Color(0xFF087AF7)
private val Purple = Color(0xFF743AF3)
private val Orange = Color(0xFFFF820A)
private val Green = Color(0xFF08A86F)
private val Red = Color(0xFFD92F43)
private val Gold = Color(0xFFF2B715)

private enum class Section(val label: String, val subtitle: String, val icon: ImageVector) {
    HOME("Início", "Acompanhe notícias, vídeos, demandas e fontes em tempo real.", Icons.Default.Home),
    NEWS("Notícias", "Busca e acompanhamento de matérias com atualização contínua", Icons.Default.Article),
    VIDEOS("Vídeos", "Busca e acompanhamento de vídeos relevantes", Icons.Default.PlayCircle),
    DEMANDS("Demandas", "Assuntos prioritários acompanhados por veículo", Icons.Default.Assignment),
    SOURCES("Fontes", "Fontes nacionais, regionais e mídias especializadas", Icons.Default.Storage),
    HISTORY("Histórico", "Histórico local das buscas e resultados", Icons.Default.History),
    TERMS("Termos", "Termos independentes para notícias e vídeos", Icons.Default.Search),
    SETTINGS("Configurações", "Automação, proxy, inicialização e operação do aplicativo", Icons.Default.Settings)
}
private enum class SourceTab { NEWS, VIDEOS, SPECIAL }
private data class Weather(val temperature: Int, val description: String)

fun main() = application {
    val trayState = rememberTrayState()
    var visible by remember { mutableStateOf(true) }
    val controller = remember { DesktopController { title, message -> runCatching { trayState.sendNotification(Notification(title, message)) } } }
    Tray(
        state = trayState,
        icon = rememberVectorPainter(Icons.Default.Newspaper),
        tooltip = "Monitor de Notícias v4.0.2",
        menu = {
            Item("Abrir", onClick = { visible = true })
            Separator()
            Item("Buscar notícias agora", onClick = { controller.searchNews() })
            Item("Buscar vídeos agora", onClick = { controller.searchVideos() })
            Item("Buscar demandas agora", onClick = { controller.searchAllDemands() })
            Separator()
            Item("Sair", onClick = { controller.close(); exitApplication() })
        }
    )
    Window(
        visible = visible,
        onCloseRequest = { visible = false },
        title = "Monitor de Notícias - Windows Portable v4.0.2",
        state = rememberWindowState(width = 1600.dp, height = 960.dp)
    ) {
        MaterialTheme(colorScheme = lightColorScheme(primary = Blue, background = Bg, surface = Color.White, onSurface = Ink)) {
            App(controller)
        }
    }
}

@Composable
private fun App(c: DesktopController) {
    var section by remember { mutableStateOf(Section.HOME) }
    var liveTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(300)
            liveTick++
        }
    }
    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Sidebar(section, { section = it }, c, liveTick)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (section == Section.HOME) {
                    HomeHeader(c, { section = it }, liveTick)
                    Home(c, { section = it }, liveTick)
                } else {
                    PageHeader(section, c, liveTick)
                    Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
                        when (section) {
                            Section.NEWS -> NewsScreen(c, liveTick)
                            Section.VIDEOS -> VideosScreen(c, liveTick)
                            Section.DEMANDS -> DemandsScreen(c, liveTick)
                            Section.SOURCES -> SourcesScreen(c, liveTick)
                            Section.HISTORY -> HistoryScreen(c, liveTick)
                            Section.TERMS -> TermsScreen(c, liveTick)
                            Section.SETTINGS -> SettingsScreen(c, liveTick)
                            else -> Unit
                        }
                    }
                }
            }
        }
        Footer(c, liveTick)
    }
}

@Composable
private fun Sidebar(selected: Section, onSelect: (Section) -> Unit, c: DesktopController, liveTick: Int) {
    val pulse = liveTick % 2
    Column(
        Modifier.width(260.dp).fillMaxHeight()
            .background(Brush.verticalGradient(listOf(NavyDark, Navy, Color(0xFF073A69))))
            .padding(horizontal = 18.dp, vertical = 22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFB631)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Newspaper, null, tint = Navy, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column {
                Text("MONITOR", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text("DE NOTÍCIAS", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text("Inteligência de mídia", color = Color(0xFFBCD1E9), fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(25.dp))
        Section.entries.forEach { item ->
            val active = selected == item
            Row(
                Modifier.fillMaxWidth().height(49.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (active) Brush.horizontalGradient(listOf(Color(0xFF0B83F4), Color(0xFF185CC8))) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)))
                    .clickable { onSelect(item) }.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(item.icon, null, tint = if (active) Color.White else Color(0xFFC7DAEF), modifier = Modifier.size(23.dp))
                Spacer(Modifier.width(15.dp)); Text(item.label, color = Color.White, fontSize = 15.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                if (item == Section.NEWS && c.newNewsLinks.isNotEmpty()) { Spacer(Modifier.weight(1f)); CountBadge(c.newNewsLinks.size, Gold, Navy) }
                if (item == Section.VIDEOS && c.newVideoLinks.isNotEmpty()) { Spacer(Modifier.weight(1f)); CountBadge(c.newVideoLinks.size, Purple, Color.White) }
            }
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.weight(1f))
        Surface(color = Color(0x19000000), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color(0xFF245481)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(if (pulse >= 0) Color(0xFF72E557) else Color(0xFF72E557)))
                    Spacer(Modifier.width(8.dp)); Text("Sistema operacional", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Text("Dados locais • modo portátil", color = Color(0xFFB9CEE6), fontSize = 11.sp)
                SideStatus(c.proxyStatusLabel, c.proxyReady || !c.proxyEnabled)
                SideStatus(if (c.automaticMonitoring) "Automação ativa" else "Automação pausada", c.automaticMonitoring)
                HorizontalDivider(color = Color(0xFF25517A)); Text("Windows Portable v4.0.2", color = Color(0xFFC5D6E9), fontSize = 11.sp)
            }
        }
    }
}

@Composable private fun SideStatus(text: String, ok: Boolean) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.Warning, null, tint = if (ok) Color(0xFF20D08C) else Gold, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(7.dp)); Text(text, color = if (ok) Color(0xFFD7E8F7) else Color(0xFFFFD985), fontSize = 10.sp, maxLines = 1) } }
@Composable private fun CountBadge(n: Int, bg: Color, fg: Color) { Surface(color = bg, shape = RoundedCornerShape(12.dp)) { Text(n.toString(), color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) } }

@Composable
private fun HomeHeader(c: DesktopController, onNavigate: (Section) -> Unit, liveTick: Int) {
    var weather by remember { mutableStateOf<Weather?>(null) }
    LaunchedEffect(Unit) { while (true) { weather = fetchWeather(); delay(15 * 60_000L) } }
    val now = Date(System.currentTimeMillis() + (liveTick % 2) * 0L)
    Row(Modifier.fillMaxWidth().height(94.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Eco, null, tint = Green, modifier = Modifier.size(38.dp)); Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) { Text("Olá, bem-vindo! 👋", color = Ink, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold); Text("Acompanhe notícias, vídeos, demandas e fontes em tempo real.", color = Muted, fontSize = 12.sp) }
        Surface(Modifier.width(430.dp).height(48.dp), color = Color.White, shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, Border), shadowElevation = 4.dp) {
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Search, null, tint = Color(0xFF174B8F)); Spacer(Modifier.width(10.dp)); Text("Buscar notícias, vídeos, demandas ou fontes...", color = Muted, fontSize = 11.sp) }
        }
        Spacer(Modifier.width(16.dp)); Icon(Icons.Default.NotificationsNone, null, tint = Color(0xFF174B8F), modifier = Modifier.size(25.dp)); Spacer(Modifier.width(12.dp))
        Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF164C90)).clickable { onNavigate(Section.SETTINGS) }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, tint = Color.White) }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(112.dp)) { Text(SimpleDateFormat("dd MMM yyyy", Locale("pt", "BR")).format(now), color = Muted, fontSize = 8.sp); Text(SimpleDateFormat("HH:mm:ss", Locale("pt", "BR")).format(now), color = Ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold) }
        Surface(color = Color(0xFFF8FBFF), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Border), modifier = Modifier.width(126.dp).height(60.dp)) {
            Row(Modifier.padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.WbSunny, null, tint = Color(0xFFFFB21B), modifier = Modifier.size(28.dp)); Spacer(Modifier.width(7.dp)); Column { Text("Brasília - DF", color = Muted, fontSize = 8.sp); Text(weather?.let { "${it.temperature}°C" } ?: "--°C", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold); Text(weather?.description ?: "Atualizando...", color = Muted, fontSize = 7.sp, maxLines = 1) } }
        }
    }
}

@Composable
private fun PageHeader(section: Section, c: DesktopController, liveTick: Int) {
    val now = Date(System.currentTimeMillis() + (liveTick % 2) * 0L)
    Row(Modifier.fillMaxWidth().height(112.dp).padding(horizontal = 25.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(57.dp).clip(RoundedCornerShape(2.dp)).background(Gold)); Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) { Text("CENTRAL DE INTELIGÊNCIA DE MÍDIA", color = Color(0xFFB78900), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp); Text(section.label, color = Ink, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold); Text(section.subtitle, color = Muted, fontSize = 12.sp) }
        StatusPill(c.proxyStatusLabel, if (c.proxyReady) Green else Color(0xFFC88700), if (c.proxyReady) Icons.Default.VerifiedUser else Icons.Default.Security)
        Spacer(Modifier.width(10.dp)); StatusPill(if (c.automaticMonitoring) "Automação ativa" else "Automação pausada", if (c.automaticMonitoring) Green else Orange, Icons.Default.Schedule)
        Spacer(Modifier.width(26.dp)); Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(180.dp)) { Text(SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(now), color = Muted, fontSize = 9.sp); Text(SimpleDateFormat("HH:mm:ss", Locale("pt", "BR")).format(now), color = Ink, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold) }
    }
}
@Composable private fun StatusPill(text: String, color: Color, icon: ImageVector) { Surface(color = color.copy(alpha = .08f), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, color.copy(alpha = .16f))) { Row(Modifier.padding(horizontal = 13.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = color, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)); Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) } } }

@Composable
private fun Home(c: DesktopController, onNavigate: (Section) -> Unit, liveTick: Int) {
    val activeProgress = when { c.newsBusy -> c.newsProgress; c.videoBusy -> c.videoProgress; else -> null }
    val pct = ((activeProgress?.fraction ?: if (c.newsBusy || c.videoBusy) 0f else 1f) * 100f).roundToInt().coerceIn(0, 100)
    val now = System.currentTimeMillis() + (liveTick % 2) * 0L
    val todayVideos = c.videos.count { now - it.capturedAt < 24L * 60L * 60L * 1000L }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Row(Modifier.fillMaxWidth().height(98.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Metric("Notícias 24h", c.news.size.toString(), Icons.Default.Article, Blue, Modifier.weight(1f)) { onNavigate(Section.NEWS) }
            Metric("Vídeos", c.videos.size.toString(), Icons.Default.PlayCircle, Purple, Modifier.weight(1f)) { onNavigate(Section.VIDEOS) }
            Metric("Vídeos hoje", todayVideos.toString(), Icons.Default.Videocam, Green, Modifier.weight(1f)) { onNavigate(Section.VIDEOS) }
            Metric("Demandas", c.demands.count { it.active }.toString(), Icons.Default.Assignment, Orange, Modifier.weight(1f)) { onNavigate(Section.DEMANDS) }
            Metric("Fontes", SourceCatalog.all.size.toString(), Icons.Default.Storage, Color(0xFFD4085B), Modifier.weight(1f)) { onNavigate(Section.SOURCES) }
        }
        Row(Modifier.fillMaxWidth().height(245.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.weight(1.38f).fillMaxHeight().clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Color(0xFF082A52), Color(0xFF0B4F8B), Color(0xFF3187D4))))) {
                Column(Modifier.fillMaxHeight().width(475.dp).padding(28.dp)) {
                    Text("CENTRAL DE INTELIGÊNCIA DE MÍDIA", color = Color(0xFFC6D8EC), fontSize = 10.sp, letterSpacing = 1.sp)
                    Spacer(Modifier.height(8.dp)); Text("Tudo o que importa\nem um só lugar.", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 33.sp)
                    Spacer(Modifier.height(8.dp)); Text("Buscas e resultados atualizados automaticamente,\nem tempo real.", color = Color(0xFFE8F1FB), fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    Surface(color = Color(0xFF075B59), shape = RoundedCornerShape(11.dp), border = BorderStroke(1.dp, Color(0xFF09A788))) {
                        Column(Modifier.width(365.dp).padding(horizontal = 13.dp, vertical = 9.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(16.dp).clip(CircleShape).background(Color(0xFF31E89A))); Spacer(Modifier.width(9.dp)); Text(if (c.newsBusy) "Buscando notícias • $pct%" else if (c.videoBusy) "Buscando vídeos • $pct%" else "Status: Pronto • 100%", color = Color(0xFF31E89A), fontSize = 11.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); Text("$pct%", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.height(7.dp)); LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth().height(5.dp), color = Color(0xFF31E89A), trackColor = Color.White.copy(alpha = .16f))
                        }
                    }
                }
                Icon(Icons.Default.LaptopWindows, null, tint = Color.White.copy(alpha = .27f), modifier = Modifier.align(Alignment.CenterEnd).padding(end = 55.dp).size(190.dp))
            }
            Panel(Modifier.weight(1f).fillMaxHeight()) {
                Text("Ações rápidas", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(12.dp))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) { Action("Buscar notícias", Blue, Icons.Default.Search, { c.searchNews() }, Modifier.weight(1f)); Action("Buscar demandas", Orange, Icons.Default.Assignment, { c.searchAllDemands() }, Modifier.weight(1f)) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) { Action("Buscar vídeos", Purple, Icons.Default.PlayCircle, { c.searchVideos() }, Modifier.weight(1f)); Action("Termos de busca", Green, Icons.Default.Search, { onNavigate(Section.TERMS) }, Modifier.weight(1f)) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(155.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Panel(Modifier.weight(1f)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Schedule, null, tint = Blue); Spacer(Modifier.width(8.dp)); Text("Agendamento automático", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }; Spacer(Modifier.height(14.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { MiniSchedule("Notícias", if (c.newsAutomatic) "${c.newsIntervalMinutes} min" else "pausado", Blue, Modifier.weight(1f)); MiniSchedule("Demandas", if (c.demandAutomatic) "${c.demandIntervalMinutes} min" else "pausado", Orange, Modifier.weight(1f)); MiniSchedule("Vídeos", if (c.videoAutomatic) c.videoScheduleTimes.sorted().joinToString(", ") else "pausado", Purple, Modifier.weight(1.3f)) } }
            Panel(Modifier.weight(1f)) { Text("Resumo do dia", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(12.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { Summary("Notícias", c.news.size, Blue); Summary("Vídeos", c.videos.size, Purple); Summary("Demandas", c.demands.size, Orange) } }
        }
        Row(Modifier.fillMaxWidth().height(205.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Panel(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Storage, null, tint = Blue); Spacer(Modifier.width(8.dp)); Text("Fontes em destaque", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold) }
                Spacer(Modifier.height(10.dp)); val chosen = if (c.newsAllSources) SourceCatalog.all.take(5) else SourceCatalog.all.filter { it.id in c.selectedNewsSourceIds }.take(5)
                chosen.forEach { s -> Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(7.dp).clip(CircleShape).background(Green)); Spacer(Modifier.width(8.dp)); Text(s.name, color = Ink, fontSize = 10.sp, modifier = Modifier.weight(1f)); Text(s.region, color = Muted, fontSize = 8.sp) } }
                Spacer(Modifier.weight(1f)); Text(if (c.newsAllSources) "Modo sem filtro: todos os veículos" else "${c.selectedNewsSourceIds.size} fonte(s) selecionada(s)", color = if (c.newsAllSources) Green else Muted, fontSize = 9.sp)
            }
            Panel(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.History, null, tint = Purple); Spacer(Modifier.width(8.dp)); Text("Últimas atividades", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold) }
                Spacer(Modifier.height(10.dp)); ActivityLine("Notícias", c.status, Blue); ActivityLine("Vídeos", c.videoStatus, Purple); ActivityLine("Proxy", c.proxyStatusLabel, if (c.proxyReady) Green else Orange); ActivityLine("Automação", if (c.automaticMonitoring) "Ativa" else "Pausada", Green)
            }
            Panel(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Lightbulb, null, tint = Gold); Spacer(Modifier.width(8.dp)); Text("Dicas e operação", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold) }
                Spacer(Modifier.height(12.dp)); Surface(color = Color(0xFFEAF4FF), shape = RoundedCornerShape(11.dp), modifier = Modifier.fillMaxWidth().weight(1f)) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.Center) { Text("Resultados mais precisos", color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(5.dp)); Text("Use termos específicos e mantenha 'todos os veículos' ligado quando quiser varrer também fontes fora do catálogo padrão.", color = Muted, fontSize = 9.sp) } }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable private fun Metric(title: String, value: String, icon: ImageVector, accent: Color, modifier: Modifier, click: () -> Unit) { Panel(modifier.clickable(onClick = click)) { Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = .13f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = accent, modifier = Modifier.size(29.dp)) }; Spacer(Modifier.width(12.dp)); Column { Text(title, color = Ink, fontSize = 11.sp); Text(value, color = Ink, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold) } } } }
@Composable private fun Action(text: String, accent: Color, icon: ImageVector, click: () -> Unit, modifier: Modifier) { Surface(modifier.clickable(onClick = click), color = accent, shape = RoundedCornerShape(12.dp)) { Row(Modifier.fillMaxSize().padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Color.White); Spacer(Modifier.width(10.dp)); Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) } } }
@Composable private fun MiniSchedule(title: String, value: String, accent: Color, modifier: Modifier) { Surface(modifier, color = Color(0xFFF7FAFE), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Border)) { Column(Modifier.padding(12.dp)) { Text(title, color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold); Text(value, color = accent, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) } } }
@Composable private fun Summary(title: String, n: Int, color: Color) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(n.toString(), color = color, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold); Text(title, color = Muted, fontSize = 10.sp) } }
@Composable private fun ActivityLine(label: String, value: String, color: Color) { Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).clip(CircleShape).background(color)); Spacer(Modifier.width(8.dp)); Text(label, color = Ink, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(65.dp)); Text(value, color = Muted, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)) } }

@Composable
private fun NewsScreen(c: DesktopController, liveTick: Int) {
    var query by remember { mutableStateOf("") }
    var onlyDemands by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(false) }
    var startDate by remember { mutableStateOf(LocalDate.now().minusDays(1).toString()) }
    var endDate by remember { mutableStateOf(LocalDate.now().toString()) }
    val list = remember(liveTick, query, onlyDemands) { c.news.filter { n -> (!onlyDemands || n.demand) && (query.isBlank() || "${n.title} ${n.source} ${n.matchedTerm} ${n.matchedDemand}".contains(query, true)) } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel(Modifier.fillMaxWidth().height(if (custom) 194.dp else 146.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SearchBox(query, { query = it }, "Buscar nas notícias (título, fonte, termo...)", Modifier.weight(1f)); Spacer(Modifier.width(12.dp))
                Button(onClick = { val p = c.periodLastHours(24); c.searchNews(p.first, p.second) }, enabled = !c.newsBusy, modifier = Modifier.height(48.dp)) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Buscar últimas 24h") }
                Spacer(Modifier.width(10.dp)); OutlinedButton(onClick = { onlyDemands = !onlyDemands }, modifier = Modifier.height(48.dp)) { Icon(Icons.Default.FilterAlt, null); Spacer(Modifier.width(5.dp)); Text(if (onlyDemands) "Todas" else "Só demandas") }
            }
            Spacer(Modifier.height(10.dp)); Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PeriodButton("Hoje") { val end = System.currentTimeMillis(); val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(); c.searchNews(start, end) }
                PeriodButton("24 horas") { val p = c.periodLastHours(24); c.searchNews(p.first, p.second) }
                PeriodButton("7 dias") { val p = c.periodLastHours(168); c.searchNews(p.first, p.second) }
                PeriodButton("30 dias") { val p = c.periodLastHours(720); c.searchNews(p.first, p.second) }
                PeriodButton(if (custom) "Fechar período" else "Período personalizado") { custom = !custom }
                Spacer(Modifier.weight(1f)); Text("${list.size} matéria(s) na janela", color = Muted, fontSize = 10.sp)
            }
            if (custom) { Spacer(Modifier.height(10.dp)); Row(verticalAlignment = Alignment.CenterVertically) { CompactField(startDate, { startDate = it }, "Data inicial", Modifier.width(175.dp)); Spacer(Modifier.width(8.dp)); CompactField(endDate, { endDate = it }, "Data final", Modifier.width(175.dp)); Spacer(Modifier.width(8.dp)); Button(onClick = { c.parsePeriod(startDate, "00:00", endDate, "23:59")?.let { c.searchNews(it.first, it.second) } }, enabled = !c.newsBusy, modifier = Modifier.height(42.dp)) { Text("Buscar período") } } }
        }
        ExecutionPanel("Notícias", c.newsBusy, c.newsProgress, c.status, c.newNewsLinks.size, c.lastNewsSearchDurationMs, liveTick)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Notícias encontradas", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.weight(1f)); Text("${list.size} exibida(s)", color = Muted, fontSize = 10.sp) }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(list, key = { it.link }) { NewsCard(it, it.link in c.newNewsLinks, c) } }
    }
}

@Composable
private fun VideosScreen(c: DesktopController, liveTick: Int) {
    var query by remember { mutableStateOf("") }
    var custom by remember { mutableStateOf(false) }
    var startDate by remember { mutableStateOf(LocalDate.now().minusDays(1).toString()) }
    var endDate by remember { mutableStateOf(LocalDate.now().toString()) }
    val list = remember(liveTick, query) { c.videos.filter { query.isBlank() || "${it.title} ${it.sourceName} ${it.matchedTerm} ${it.matchedDemand}".contains(query, true) } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel(Modifier.fillMaxWidth().height(if (custom) 194.dp else 146.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { SearchBox(query, { query = it }, "Buscar nos vídeos (título, fonte, termo...)", Modifier.weight(1f)); Spacer(Modifier.width(12.dp)); Button(onClick = { c.searchVideos() }, enabled = !c.videoBusy, modifier = Modifier.height(48.dp)) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Buscar vídeos agora") } }
            Spacer(Modifier.height(10.dp)); Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PeriodButton("24 horas") { val p = c.periodLastHours(24); c.searchVideos(p.first, p.second) }; PeriodButton("7 dias") { val p = c.periodLastHours(168); c.searchVideos(p.first, p.second) }; PeriodButton("30 dias") { val p = c.periodLastHours(720); c.searchVideos(p.first, p.second) }; PeriodButton(if (custom) "Fechar período" else "Período personalizado") { custom = !custom }; Spacer(Modifier.weight(1f)); Text("${list.size} vídeo(s)", color = Muted, fontSize = 10.sp)
            }
            if (custom) { Spacer(Modifier.height(10.dp)); Row(verticalAlignment = Alignment.CenterVertically) { CompactField(startDate, { startDate = it }, "Data inicial", Modifier.width(175.dp)); Spacer(Modifier.width(8.dp)); CompactField(endDate, { endDate = it }, "Data final", Modifier.width(175.dp)); Spacer(Modifier.width(8.dp)); Button(onClick = { c.parsePeriod(startDate, "00:00", endDate, "23:59")?.let { c.searchVideos(it.first, it.second) } }, enabled = !c.videoBusy, modifier = Modifier.height(42.dp)) { Text("Buscar período") } } }
        }
        ExecutionPanel("Vídeos", c.videoBusy, c.videoProgress, c.videoStatus, c.newVideoLinks.size, c.lastVideoSearchDurationMs, liveTick)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Vídeos encontrados", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.weight(1f)); Text("${list.size} exibido(s)", color = Muted, fontSize = 10.sp) }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(list, key = { it.link }) { VideoCard(it, it.link in c.newVideoLinks) } }
    }
}

@Composable
private fun ExecutionPanel(kind: String, busy: Boolean, p: LiveSearchProgress, status: String, fresh: Int, duration: Long, liveTick: Int) {
    val fraction = if (busy) p.fraction.coerceIn(0f, 1f) else 1f
    val pct = (fraction * 100).roundToInt()
    val elapsed = if (busy && p.startedAt > 0L) System.currentTimeMillis() - p.startedAt + (liveTick % 2) * 0L else duration
    Panel(Modifier.fillMaxWidth().height(148.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background((if (busy) Blue else Green).copy(alpha = .12f)), contentAlignment = Alignment.Center) { Icon(if (busy) Icons.Default.Sync else Icons.Default.CheckCircle, null, tint = if (busy) Blue else Green, modifier = Modifier.size(29.dp)) }
            Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(if (busy) "$kind • busca em andamento" else "$kind • última execução concluída", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold); Text(status, color = Muted, fontSize = 10.sp, maxLines = 1); Spacer(Modifier.height(8.dp)); LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(6.dp), color = if (busy) Blue else Green, trackColor = Color(0xFFE4EDF7)) }
            Spacer(Modifier.width(12.dp)); Stat("$pct%", "conclusão", Blue); Stat(p.found.toString(), "encontrados", Blue); Stat(fresh.toString(), "novos", Green); Stat(p.errors.toString(), "falhas", Red); Stat("${p.completed}/${p.total}", "etapas", Color(0xFF3562A0)); Stat(durationText(elapsed), "tempo", Color(0xFF3562A0))
        }
        Spacer(Modifier.height(11.dp)); Surface(color = if (busy) Color(0xFFEAF4FF) else Color(0xFFE9F8F1), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(35.dp)) { Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) { Text(if (busy) "Fonte atual: ${p.currentSource.ifBlank { "iniciando..." }}   •   Consulta: ${p.currentQuery.ifBlank { "preparando..." }}" else "$fresh novo(s) nesta execução. As marcações Nova são recalculadas a cada nova busca.", color = if (busy) Blue else Green, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
    }
}
@Composable private fun Stat(value: String, label: String, color: Color) { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(69.dp)) { Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold); Text(label, color = Muted, fontSize = 8.sp) } }

@Composable
private fun NewsCard(n: News, isNew: Boolean, c: DesktopController) {
    val scope = rememberCoroutineScope()
    Panel(Modifier.fillMaxWidth().height(136.dp)) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(58.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFE7F2FF)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Article, null, tint = Blue, modifier = Modifier.size(31.dp)) }
            Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("${n.source} • ${dateText(n.date)}", color = Muted, fontSize = 9.sp); if (isNew) { Spacer(Modifier.width(8.dp)); NewBadge() } }
                Text(n.title, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (n.snippet.isNotBlank()) Text(n.snippet.replace("&nbsp;", " "), color = Muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp)); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { if (n.matchedTerm.isNotBlank()) Tag("Termo: ${n.matchedTerm}", Blue); if (n.matchedDemand.isNotBlank()) Tag("Demanda: ${n.matchedDemand}", Orange) }
            }
            Spacer(Modifier.width(10.dp)); LinkButtons(
                openLabel = "Abrir matéria",
                open = { scope.launch { val direct = GoogleNewsUrlResolver.resolve(n.link); openUrl(direct) } },
                copy = { scope.launch { val direct = GoogleNewsUrlResolver.resolve(n.link); copyText(direct); c.status = if (direct != n.link) "Link direto do veículo copiado." else "Link copiado; não foi possível decodificar o redirecionamento do Google." } },
                share = { scope.launch { val direct = GoogleNewsUrlResolver.resolve(n.link); openWhatsApp(n.title, direct) } }
            )
        }
    }
}

@Composable
private fun VideoCard(v: VideoItem, isNew: Boolean) {
    Panel(Modifier.fillMaxWidth().height(132.dp)) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(58.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF0E8FF)), contentAlignment = Alignment.Center) { Icon(Icons.Default.PlayCircle, null, tint = Purple, modifier = Modifier.size(33.dp)) }
            Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("${v.sourceName} • ${dateText(v.publishedAt)}", color = Muted, fontSize = 9.sp); if (isNew) { Spacer(Modifier.width(8.dp)); NewBadge() } }
                Text(v.title, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (v.summary.isNotBlank()) Text(v.summary.replace("&nbsp;", " "), color = Muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp)); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { if (v.matchedTerm.isNotBlank()) Tag("Termo: ${v.matchedTerm}", Purple); if (v.matchedDemand.isNotBlank()) Tag("Demanda: ${v.matchedDemand}", Orange) }
            }
            Spacer(Modifier.width(10.dp)); LinkButtons("Abrir vídeo", { openUrl(v.link) }, { copyText(v.link) }, { openWhatsApp(v.title, v.link) })
        }
    }
}

@Composable private fun NewBadge() { Surface(color = Blue, shape = RoundedCornerShape(10.dp)) { Text("Nova", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)) } }
@Composable private fun Tag(text: String, accent: Color) { Surface(color = accent.copy(alpha = .10f), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, accent.copy(alpha = .18f))) { Text(text, color = accent, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp).widthIn(max = 290.dp)) } }
@Composable private fun LinkButtons(openLabel: String, open: () -> Unit, copy: () -> Unit, share: () -> Unit) { Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { SmallOutline(openLabel, Icons.Default.OpenInNew, Blue, open); SmallOutline("WhatsApp", Icons.Default.Share, Green, share); SmallOutline("Copiar link", Icons.Default.ContentCopy, Blue, copy) } }
@Composable private fun SmallOutline(text: String, icon: ImageVector, color: Color, click: () -> Unit) { Surface(shape = RoundedCornerShape(22.dp), color = Color.White, border = BorderStroke(1.dp, Color(0xFF6B6871)), modifier = Modifier.height(39.dp).clickable(onClick = click)) { Row(Modifier.padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = color, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(5.dp)); Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) } } }

@Composable
private fun TermsScreen(c: DesktopController, liveTick: Int) {
    val t = liveTick
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        TermCard("Termos de Notícias", "Usados na varredura de matérias", Icons.Default.Article, Blue, c.terms.toList(), { c.addTerm(it) }, { c.removeTerm(it) }, Modifier.weight(1f), t)
        TermCard("Termos de Vídeos", "Lista independente para vídeos", Icons.Default.PlayCircle, Purple, c.videoTerms.toList(), { c.addVideoTerm(it) }, { c.removeVideoTerm(it) }, Modifier.weight(1f), t)
    }
}
@Composable private fun TermCard(title: String, subtitle: String, icon: ImageVector, accent: Color, items: List<String>, add: (String) -> Unit, remove: (String) -> Unit, modifier: Modifier, liveTick: Int) {
    var value by remember { mutableStateOf("") }; val count = items.size + liveTick * 0
    Panel(modifier.fillMaxHeight()) {
        Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = .10f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = accent, modifier = Modifier.size(30.dp)) }; Spacer(Modifier.width(15.dp)); Column(Modifier.weight(1f)) { Text(title, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold); Text(subtitle, color = Muted, fontSize = 12.sp) }; Surface(color = Color(0xFFE7F2FF), shape = RoundedCornerShape(10.dp)) { Text("$count termo(s)", color = Color(0xFF315B91), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) } }
        Spacer(Modifier.height(18.dp)); Row(verticalAlignment = Alignment.CenterVertically) { SearchBox(value, { value = it }, "Novo termo", Modifier.weight(1f)); Spacer(Modifier.width(10.dp)); Button(onClick = { add(value); value = "" }, enabled = value.isNotBlank(), modifier = Modifier.height(48.dp).width(145.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(7.dp)); Text("Adicionar") } }
        Spacer(Modifier.height(14.dp)); LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) { items(items, key = { it }) { term -> Surface(color = Color(0xFFF2F6FB), shape = RoundedCornerShape(9.dp), modifier = Modifier.fillMaxWidth().height(56.dp)) { Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFE1EEFC)), contentAlignment = Alignment.Center) { Text("#", color = accent, fontSize = 17.sp, fontWeight = FontWeight.Bold) }; Spacer(Modifier.width(14.dp)); Text(term, color = Ink, fontSize = 13.sp, modifier = Modifier.weight(1f)); IconButton(onClick = { remove(term) }) { Icon(Icons.Default.DeleteOutline, null, tint = Red) } } } } }
    }
}

@Composable
private fun SettingsScreen(c: DesktopController, liveTick: Int) {
    var proxyEnabled by remember { mutableStateOf(c.proxyEnabled) }; var user by remember { mutableStateOf(c.proxyUsername) }; var pass by remember { mutableStateOf(c.proxyPassword) }; var proxyMessage by remember { mutableStateOf("") }; var videoTime by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope(); val listState = rememberLazyListState()
    LaunchedEffect(Unit) { listState.scrollToItem(0) }
    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Panel(Modifier.fillMaxWidth().height(210.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(45.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFE7F2FF)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Lock, null, tint = Blue) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Configuração de proxy", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold); Text("Configure usuário e senha para liberar as buscas pelo proxy.", color = Muted, fontSize = 10.sp) }; Text("Ativar", color = Muted, fontSize = 9.sp); Switch(proxyEnabled, onCheckedChange = { proxyEnabled = it }); Spacer(Modifier.width(10.dp)); Surface(color = Color(0xFFF5F8FC), shape = RoundedCornerShape(9.dp)) { Text("${c.proxyHost}:${c.proxyPort}", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(10.dp)) } }
                if (proxyEnabled && (user.isBlank() || pass.isBlank())) { Spacer(Modifier.height(8.dp)); Surface(color = Color(0xFFFFE9EB), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(34.dp)) { Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Error, null, tint = Red, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(8.dp)); Text("Configure usuário e senha para liberar as buscas pelo proxy.", color = Red, fontSize = 10.sp) } } }
                Spacer(Modifier.height(8.dp)); Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) { CompactField(user, { user = it }, "Usuário", Modifier.weight(1f)); CompactPassword(pass, { pass = it }, "Senha", Modifier.weight(1f)); Button(onClick = { c.saveProxy(proxyEnabled, "proxy-7db.mb", 6060, user, pass); proxyMessage = "Configuração salva e aplicada." }, modifier = Modifier.height(44.dp)) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(5.dp)); Text("Salvar e aplicar") }; OutlinedButton(onClick = { scope.launch { val r = c.testProxyConnection(); proxyMessage = r.second } }, modifier = Modifier.height(44.dp)) { Icon(Icons.Default.Wifi, null); Spacer(Modifier.width(5.dp)); Text("Testar conexão") } }
                Spacer(Modifier.height(5.dp)); Text(proxyMessage, color = if (proxyMessage.contains("sucesso", true) || proxyMessage.contains("salva", true)) Green else Muted, fontSize = 9.sp)
            }
        }
        item {
            Panel(Modifier.fillMaxWidth().height(118.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Schedule, null, tint = Blue, modifier = Modifier.size(31.dp)); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Buscas automáticas", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold); Text("Controle Notícias, Demandas e Vídeos de forma independente.", color = Muted, fontSize = 10.sp) }; Column(horizontalAlignment = Alignment.End) { Text("Controle geral", color = Muted, fontSize = 9.sp); Switch(c.automaticMonitoring, onCheckedChange = { c.automaticMonitoring = it }) } }
                Spacer(Modifier.height(7.dp)); HorizontalDivider(color = Border); Spacer(Modifier.height(7.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Computer, null, tint = Blue); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Iniciar Monitor de Notícias com o Windows", color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold); Text("Inicialização automática ao entrar no sistema.", color = Muted, fontSize = 9.sp) }; Switch(c.startWithWindows, onCheckedChange = { c.startWithWindows = it }) }
            }
        }
        item { AutomationCard("Notícias", "Varredura automática das fontes de notícias selecionadas", Icons.Default.Article, Blue, c.newsAutomatic, { c.newsAutomatic = it }, c.newsIntervalMinutes, { c.newsIntervalMinutes = it }, { c.searchNews() }, c.lastNewsAutoAt, liveTick) }
        item { AutomationCard("Demandas", "Pesquisa automática de todas as demandas ativas", Icons.Default.Assignment, Gold, c.demandAutomatic, { c.demandAutomatic = it }, c.demandIntervalMinutes, { c.demandIntervalMinutes = it }, { c.searchAllDemands() }, c.lastDemandAutoAt, liveTick) }
        item {
            Panel(Modifier.fillMaxWidth().height(222.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(Purple.copy(alpha = .10f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.PlayCircle, null, tint = Purple) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Vídeos", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold); Text("Busca automática nos horários escolhidos", color = Muted, fontSize = 10.sp) }; Text("Automático", color = Muted, fontSize = 9.sp); Spacer(Modifier.width(7.dp)); Switch(c.videoAutomatic, onCheckedChange = { c.videoAutomatic = it }); Spacer(Modifier.width(12.dp)); OutlinedButton(onClick = { c.searchVideos() }, modifier = Modifier.height(42.dp)) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(5.dp)); Text("Executar agora") } }
                Spacer(Modifier.height(10.dp)); Text("Horários programados", color = Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp)); Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) { c.videoScheduleTimes.sorted().forEach { t -> TimeChip(t) { c.videoScheduleTimes = c.videoScheduleTimes - t } } }
                Spacer(Modifier.height(10.dp)); Row(verticalAlignment = Alignment.CenterVertically) { CompactField(videoTime, { videoTime = it.take(5) }, "Novo horário HH:mm", Modifier.width(190.dp)); Spacer(Modifier.width(8.dp)); Button(onClick = { runCatching { java.time.LocalTime.parse(videoTime) }.onSuccess { c.videoScheduleTimes = c.videoScheduleTimes + it.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")); videoTime = "" } }, enabled = videoTime.length == 5, modifier = Modifier.height(42.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(5.dp)); Text("Adicionar horário") }; Spacer(Modifier.weight(1f)); Text("Última automática: ${autoTime(c.lastVideoAutoAt + liveTick * 0L)}", color = Muted, fontSize = 9.sp) }
            }
        }
        item { Spacer(Modifier.height(6.dp)) }
    }
}

@Composable
private fun AutomationCard(title: String, subtitle: String, icon: ImageVector, accent: Color, enabled: Boolean, onEnabled: (Boolean) -> Unit, interval: Int, onInterval: (Int) -> Unit, runNow: () -> Unit, lastAt: Long, liveTick: Int) {
    Panel(Modifier.fillMaxWidth().height(178.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = .11f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = accent) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold); if (enabled) { Spacer(Modifier.width(8.dp)); Tag("automático ativo", Green) } }; Text(subtitle, color = Muted, fontSize = 10.sp) }; Text("Automático", color = Muted, fontSize = 9.sp); Spacer(Modifier.width(7.dp)); Switch(enabled, onCheckedChange = onEnabled); Spacer(Modifier.width(12.dp)); OutlinedButton(onClick = runNow, modifier = Modifier.height(42.dp)) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(5.dp)); Text("Executar agora") } }
        Spacer(Modifier.height(9.dp)); Row { Text("Última automática: ${autoTime(lastAt + liveTick * 0L)}", color = Muted, fontSize = 9.sp); Spacer(Modifier.width(30.dp)); Text("Próxima execução: ${nextAuto(lastAt, interval)}", color = Muted, fontSize = 9.sp) }
        Spacer(Modifier.height(8.dp)); HorizontalDivider(color = Border); Spacer(Modifier.height(8.dp)); Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text("Frequência", color = Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold); listOf(15, 30, 45, 60, 120).forEach { m -> ChoiceButton(if (m < 60) "$m min" else if (m == 60) "1 hora" else "2 horas", interval == m) { onInterval(m) } } }
    }
}

@Composable
private fun SourcesScreen(c: DesktopController, liveTick: Int) {
    var tab by remember { mutableStateOf(SourceTab.NEWS) }; var query by remember { mutableStateOf("") }; var region by remember { mutableStateOf("Todas") }; var state by remember { mutableStateOf("Todos") }
    val newsList = remember(liveTick, region, state, query) { SourceCatalog.all.filter { (region == "Todas" || it.region == region) && (state == "Todos" || it.state == state) && (query.isBlank() || "${it.name} ${it.stateName} ${it.group}".contains(query, true)) } }
    val videoBase = if (tab == SourceTab.SPECIAL) VideoSourceCatalog.all.take(8) else VideoSourceCatalog.all
    val videoList = remember(liveTick, region, state, query, tab) { videoBase.filter { (region == "Todas" || it.region == region) && (state == "Todos" || it.state == state) && (query.isBlank() || "${it.name} ${it.group}".contains(query, true)) } }
    val visible = if (tab == SourceTab.NEWS) newsList.size else videoList.size
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Panel(Modifier.fillMaxWidth().height(258.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { TabButton("Notícias", tab == SourceTab.NEWS) { tab = SourceTab.NEWS }; Spacer(Modifier.width(7.dp)); TabButton("Vídeos", tab == SourceTab.VIDEOS) { tab = SourceTab.VIDEOS }; Spacer(Modifier.width(7.dp)); TabButton("Mídias especializadas (8)", tab == SourceTab.SPECIAL) { tab = SourceTab.SPECIAL }; Spacer(Modifier.weight(1f)); SearchBox(query, { query = it }, "Pesquisar fonte...", Modifier.width(390.dp)) }
            Spacer(Modifier.height(12.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Text("Região", color = Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(65.dp)); SourceCatalog.regions.forEach { r -> FilterButton(r, region == r) { region = r; state = "Todos" }; Spacer(Modifier.width(5.dp)) } }
            Spacer(Modifier.height(9.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Text("Estado", color = Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(65.dp)); Row(Modifier.horizontalScroll(rememberScrollState())) { FilterButton("Todos", state == "Todos") { state = "Todos" }; Spacer(Modifier.width(5.dp)); SourceCatalog.states.filter { region == "Todas" || region == "Nacional" || it.third == region }.forEach { s -> FilterButton(s.first, state == s.first) { state = s.first }; Spacer(Modifier.width(5.dp)) } } }
            Spacer(Modifier.height(12.dp)); Surface(color = if (c.newsAllSources) Color(0xFFE5F8EF) else Color(0xFFF6F9FD), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, if (c.newsAllSources) Green.copy(alpha = .35f) else Border), modifier = Modifier.fillMaxWidth().height(68.dp)) {
                Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)).background((if (c.newsAllSources) Green else Muted).copy(alpha = .12f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Public, null, tint = if (c.newsAllSources) Green else Muted) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("TODOS OS VEÍCULOS — SEM EXCEÇÃO", color = if (c.newsAllSources) Green else Ink, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold); Text("Ligado: ignora a seleção abaixo e aceita resultados de qualquer veículo encontrado pelo Google Notícias, inclusive fora do catálogo padrão.", color = Muted, fontSize = 9.sp, maxLines = 2) }; Text(if (c.newsAllSources) "LIGADO" else "DESLIGADO", color = if (c.newsAllSources) Green else Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.width(8.dp)); Switch(c.newsAllSources, onCheckedChange = { c.newsAllSources = it }) }
            }
        }
        Panel(Modifier.fillMaxWidth().height(78.dp)) { Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Storage, null, tint = Blue, modifier = Modifier.size(31.dp)); Spacer(Modifier.width(12.dp)); Text("$visible fonte(s) visível(is)", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold); if (tab == SourceTab.NEWS && c.newsAllSources) { Spacer(Modifier.width(10.dp)); Tag("seletor ignorado", Green) }; Spacer(Modifier.weight(1f)); Button(onClick = { if (tab == SourceTab.NEWS) newsList.forEach { c.setNewsSource(it.id, true) } else videoList.forEach { c.setVideoSource(it.id, true) } }) { Text("Selecionar visíveis") }; Spacer(Modifier.width(7.dp)); OutlinedButton(onClick = { if (tab == SourceTab.NEWS) newsList.forEach { c.setNewsSource(it.id, false) } else videoList.forEach { c.setVideoSource(it.id, false) } }) { Text("Limpar visíveis") }; Spacer(Modifier.width(7.dp)); OutlinedButton(onClick = { if (tab == SourceTab.NEWS) c.selectAllNewsSources() else c.selectAllVideoSources() }) { Text("Todas da lista") }; Spacer(Modifier.width(7.dp)); OutlinedButton(onClick = { if (tab == SourceTab.NEWS) c.clearNewsSources() else c.clearVideoSources() }) { Text("Nenhuma") } } }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            if (tab == SourceTab.NEWS) items(newsList, key = { it.id }) { s -> SourceRow(s.name, "${s.group} • ${s.region} • ${s.state.ifBlank { "BR" }}", c.newsAllSources || s.id in c.selectedNewsSourceIds, !c.newsAllSources) { c.setNewsSource(s.id, it) } }
            else items(videoList, key = { it.id }) { s -> SourceRow(s.name, "${s.group} • ${s.region} • ${s.state}", s.id in c.selectedVideoSourceIds, true) { c.setVideoSource(s.id, it) } }
        }
    }
}

@Composable private fun TabButton(text: String, selected: Boolean, click: () -> Unit) { Surface(color = if (selected) Color(0xFFEDE7FF) else Color.White, shape = RoundedCornerShape(9.dp), border = BorderStroke(1.dp, Border), modifier = Modifier.height(38.dp).clickable(onClick = click)) { Row(Modifier.padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically) { Text(text, color = Ink, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) } } }
@Composable private fun FilterButton(text: String, selected: Boolean, click: () -> Unit) { Surface(color = if (selected) Color(0xFFDCEBFF) else Color.White, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Border), modifier = Modifier.height(34.dp).clickable(onClick = click)) { Row(Modifier.padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically) { Text(text, color = if (selected) Blue else Muted, fontSize = 9.sp) } } }
@Composable private fun SourceRow(name: String, detail: String, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) { Panel(Modifier.fillMaxWidth().height(67.dp)) { Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, onCheckedChange = change, enabled = enabled); Spacer(Modifier.width(10.dp)); Box(Modifier.width(64.dp).height(36.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0B4C8C)), contentAlignment = Alignment.Center) { Text(name.take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Bold) }; Spacer(Modifier.width(16.dp)); Column(Modifier.weight(1f)) { Text(name, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold); Text(detail, color = Muted, fontSize = 9.sp) }; if (!enabled) Tag("todos os veículos ativo", Green) else Icon(Icons.Default.ChevronRight, null, tint = Blue) } } }

@Composable
private fun DemandsScreen(c: DesktopController, liveTick: Int) {
    var vehicle by remember { mutableStateOf("") }; var subject by remember { mutableStateOf("") }; val count = c.demands.size + liveTick * 0
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel(Modifier.fillMaxWidth().height(105.dp)) { Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(vehicle, { vehicle = it }, label = { Text("Veículo") }, placeholder = { Text("Selecione ou digite o veículo...") }, singleLine = true, modifier = Modifier.weight(.9f)); OutlinedTextField(subject, { subject = it }, label = { Text("Assunto") }, placeholder = { Text("Digite o assunto da demanda...") }, singleLine = true, modifier = Modifier.weight(1.2f)); Button(onClick = { c.addDemand(vehicle, subject); vehicle = ""; subject = "" }, enabled = vehicle.isNotBlank() && subject.isNotBlank(), modifier = Modifier.height(54.dp)) { Icon(Icons.Default.Add, null); Text("Adicionar") }; OutlinedButton(onClick = { c.searchAllDemands() }, enabled = !c.newsBusy, modifier = Modifier.height(54.dp)) { Icon(Icons.Default.Refresh, null); Text("Buscar todas") } } }
        Surface(color = Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Border), modifier = Modifier.fillMaxWidth().height(66.dp)) { Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(17.dp).clip(CircleShape).background(if (c.newsBusy) Orange else Green)); Spacer(Modifier.width(15.dp)); Column { Text(if (c.newsBusy) "Status: Buscando" else "Status: Pronto", color = if (c.newsBusy) Orange else Green, fontSize = 13.sp, fontWeight = FontWeight.Bold); Text(if (c.newsBusy) c.status else "$count demanda(s) cadastrada(s). Sistema disponível.", color = Muted, fontSize = 10.sp) } } }
        if (c.demands.isEmpty()) Panel(Modifier.weight(1f).fillMaxWidth()) { Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(Icons.Default.NotificationsActive, null, tint = Color(0xFF8BBEF3), modifier = Modifier.size(100.dp)); Spacer(Modifier.height(18.dp)); Text("Nenhuma demanda carregada no momento", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold); Text("Adicione uma nova demanda ou use Buscar todas.", color = Muted, fontSize = 13.sp) } }
        else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(c.demands.toList(), key = { it.id }) { d -> Panel(Modifier.fillMaxWidth().height(82.dp)) { Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Assignment, null, tint = Orange); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("${d.vehicle} • ${d.subject}", color = Ink, fontWeight = FontWeight.Bold); Text("Última busca: ${autoTime(d.lastCheckedAt)} • encontrados ${d.lastFoundCount} • novos ${d.lastNewCount}", color = Muted, fontSize = 9.sp) }; OutlinedButton(onClick = { c.searchDemand(d) }) { Text("Buscar") }; IconButton(onClick = { c.removeDemand(d.id) }) { Icon(Icons.Default.DeleteOutline, null, tint = Red) } } } } }
    }
}

@Composable
private fun HistoryScreen(c: DesktopController, liveTick: Int) {
    var tab by remember { mutableIntStateOf(0) }; val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Panel(Modifier.fillMaxWidth().height(70.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { ChoiceButton("Notícias", tab == 0) { tab = 0 }; Spacer(Modifier.width(8.dp)); ChoiceButton("Vídeos", tab == 1) { tab = 1 }; Spacer(Modifier.weight(1f)); Text("O histórico mostra explicitamente o termo que encontrou cada resultado.", color = Muted, fontSize = 9.sp); Spacer(Modifier.width(12.dp)); OutlinedButton(onClick = { if (tab == 0) c.clearNewsHistory() else c.clearVideoHistory() }) { Icon(Icons.Default.DeleteOutline, null); Spacer(Modifier.width(5.dp)); Text("Limpar histórico") } } }
        if (tab == 0) {
            val all = remember(liveTick) { c.newsDb.listNews(2000) }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(all, key = { it.link }) { n -> HistoryNewsCard(n, c, scope) } }
        } else {
            val all = remember(liveTick) { c.videoDb.listAll(2000) }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(all, key = { it.link }) { v -> HistoryVideoCard(v) } }
        }
    }
}

@Composable private fun HistoryNewsCard(n: News, c: DesktopController, scope: kotlinx.coroutines.CoroutineScope) {
    Panel(Modifier.fillMaxWidth().height(138.dp)) { Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(58.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFE7F2FF)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Article, null, tint = Blue, modifier = Modifier.size(31.dp)) }; Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text("${n.source} • ${dateText(n.date)}", color = Muted, fontSize = 9.sp); Text(n.title, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis); Spacer(Modifier.height(6.dp)); Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { Tag(if (n.matchedTerm.isBlank()) "Termo: não registrado" else "Termo encontrado: ${n.matchedTerm}", Blue); if (n.matchedDemand.isNotBlank()) Tag("Demanda: ${n.matchedDemand}", Orange) } }; Spacer(Modifier.width(10.dp)); LinkButtons("Abrir matéria", { scope.launch { openUrl(GoogleNewsUrlResolver.resolve(n.link)) } }, { scope.launch { val direct = GoogleNewsUrlResolver.resolve(n.link); copyText(direct); c.status = "Link do histórico copiado." } }, { scope.launch { val direct = GoogleNewsUrlResolver.resolve(n.link); openWhatsApp(n.title, direct) } }) } }
}
@Composable private fun HistoryVideoCard(v: VideoItem) { Panel(Modifier.fillMaxWidth().height(134.dp)) { Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(58.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF0E8FF)), contentAlignment = Alignment.Center) { Icon(Icons.Default.PlayCircle, null, tint = Purple, modifier = Modifier.size(33.dp)) }; Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text("${v.sourceName} • ${dateText(v.publishedAt)}", color = Muted, fontSize = 9.sp); Text(v.title, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis); Spacer(Modifier.height(6.dp)); Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { Tag(if (v.matchedTerm.isBlank()) "Termo: não registrado" else "Termo encontrado: ${v.matchedTerm}", Purple); if (v.matchedDemand.isNotBlank()) Tag("Demanda: ${v.matchedDemand}", Orange) } }; Spacer(Modifier.width(10.dp)); LinkButtons("Abrir vídeo", { openUrl(v.link) }, { copyText(v.link) }, { openWhatsApp(v.title, v.link) }) } } }

@Composable private fun SearchBox(value: String, change: (String) -> Unit, hint: String, modifier: Modifier) { Surface(modifier.height(48.dp), color = Color.White, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Border)) { Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Search, null, tint = Color(0xFF43658C)); Spacer(Modifier.width(10.dp)); BasicTextField(value, change, singleLine = true, modifier = Modifier.weight(1f), textStyle = LocalTextStyle.current.copy(color = Ink, fontSize = 11.sp), decorationBox = { inner -> if (value.isBlank()) Text(hint, color = Muted, fontSize = 11.sp); inner() }) } } }
@Composable private fun CompactField(value: String, change: (String) -> Unit, hint: String, modifier: Modifier) { OutlinedTextField(value, change, placeholder = { Text(hint, fontSize = 9.sp) }, singleLine = true, modifier = modifier.height(44.dp), textStyle = LocalTextStyle.current.copy(fontSize = 10.sp)) }
@Composable private fun CompactPassword(value: String, change: (String) -> Unit, hint: String, modifier: Modifier) { OutlinedTextField(value, change, placeholder = { Text(hint, fontSize = 9.sp) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = modifier.height(44.dp), textStyle = LocalTextStyle.current.copy(fontSize = 10.sp)) }
@Composable private fun PeriodButton(text: String, click: () -> Unit) { Surface(color = Color.White, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Color(0xFF7A7780)), modifier = Modifier.height(34.dp).clickable(onClick = click)) { Row(Modifier.padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically) { Text(text, color = Ink, fontSize = 9.sp, fontWeight = FontWeight.SemiBold) } } }
@Composable private fun ChoiceButton(text: String, selected: Boolean, click: () -> Unit) { Surface(color = if (selected) Color(0xFFE8DDFB) else Color.White, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, if (selected) Purple.copy(alpha = .25f) else Color(0xFF7A7780)), modifier = Modifier.height(34.dp).clickable(onClick = click)) { Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) { Text(text, color = Ink, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) } } }
@Composable private fun TimeChip(text: String, remove: () -> Unit) { Surface(color = Color(0xFFE9DEFB), shape = RoundedCornerShape(9.dp), modifier = Modifier.height(34.dp)) { Row(Modifier.padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically) { Text(text, color = Ink, fontSize = 10.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.width(7.dp)); Icon(Icons.Default.Close, null, tint = Ink, modifier = Modifier.size(14.dp).clickable(onClick = remove)) } } }
@Composable private fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) { Surface(modifier.shadow(4.dp, RoundedCornerShape(13.dp)), color = Color.White, shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, Border)) { Column(Modifier.fillMaxSize().padding(15.dp), content = content) } }
@Composable private fun Footer(c: DesktopController, liveTick: Int) { val busy = c.newsBusy || c.videoBusy || liveTick < -1; Row(Modifier.fillMaxWidth().height(40.dp).background(Color(0xFFF7FAFE)).padding(horizontal = 25.dp), verticalAlignment = Alignment.CenterVertically) { Text("Monitor de Notícias v4.0.2", color = Muted, fontSize = 9.sp); Spacer(Modifier.width(20.dp)); Text("Inteligência de mídia para melhores decisões", color = Muted, fontSize = 9.sp); Spacer(Modifier.weight(1f)); Box(Modifier.size(10.dp).clip(CircleShape).background(if (busy) Orange else Green)); Spacer(Modifier.width(7.dp)); Text(if (busy) "Busca em andamento" else "Sistema operacional", color = Muted, fontSize = 9.sp) } }

private fun openUrl(url: String) { runCatching { if (AwtDesktop.isDesktopSupported()) AwtDesktop.getDesktop().browse(URI(url)) } }
private fun copyText(text: String) { runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) } }
private fun openWhatsApp(title: String, url: String) { val msg = URLEncoder.encode("$title\n$url", Charsets.UTF_8.name()); openUrl("https://wa.me/?text=$msg") }
private fun dateText(ts: Long): String = if (ts <= 0) "--" else SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(ts))
private fun durationText(ms: Long): String { if (ms <= 0) return "--"; val sec = ms / 1000; return "%02d:%02d".format(sec / 60, sec % 60) }
private fun autoTime(ts: Long): String = if (ts <= 0) "—" else SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(Date(ts))
private fun nextAuto(last: Long, interval: Int): String = if (last <= 0) "na próxima verificação" else SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(Date(last + interval * 60_000L))

private suspend fun fetchWeather(): Weather? = withContext(Dispatchers.IO) {
    runCatching {
        val raw = URL("https://api.open-meteo.com/v1/forecast?latitude=-15.7939&longitude=-47.8828&current=temperature_2m,weather_code&timezone=America%2FSao_Paulo").readText()
        val current = JSONObject(raw).getJSONObject("current"); val temp = current.getDouble("temperature_2m").roundToInt(); val code = current.optInt("weather_code", 0)
        Weather(temp, when (code) { 0 -> "Tempo limpo"; 1, 2 -> "Parcialmente nublado"; 3 -> "Nublado"; in 51..67 -> "Chuva"; in 80..82 -> "Pancadas de chuva"; in 95..99 -> "Trovoadas"; else -> "Condição atual" })
    }.getOrNull()
}
