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

private val V3Navy = Color(0xFF052D57)
private val V3NavyDark = Color(0xFF031F3E)
private val V3Ink = Color(0xFF0A1F4B)
private val V3Muted = Color(0xFF6079A5)
private val V3Bg = Color(0xFFF3F8FE)
private val V3Border = Color(0xFFDCE8F6)
private val V3Blue = Color(0xFF087AF7)
private val V3Purple = Color(0xFF743AF3)
private val V3Orange = Color(0xFFFF820A)
private val V3Green = Color(0xFF08A86F)
private val V3Red = Color(0xFFD92F43)
private val V3Gold = Color(0xFFF2B715)

private enum class V3Section(val label: String, val subtitle: String, val icon: ImageVector) {
    HOME("Início", "Acompanhe notícias, vídeos, demandas e fontes em tempo real.", Icons.Default.Home),
    NEWS("Notícias", "Busca e acompanhamento de matérias com atualização contínua", Icons.Default.Article),
    VIDEOS("Vídeos", "Busca e acompanhamento de vídeos relevantes", Icons.Default.PlayCircle),
    DEMANDS("Demandas", "Assuntos prioritários acompanhados por veículo", Icons.Default.Assignment),
    SOURCES("Fontes", "Fontes nacionais, regionais e mídias especializadas", Icons.Default.Storage),
    HISTORY("Histórico", "Histórico local das buscas e resultados", Icons.Default.History),
    TERMS("Termos", "Termos independentes para inteligência de notícias e vídeos", Icons.Default.Search),
    SETTINGS("Configurações", "Automação, proxy, inicialização e operação do aplicativo", Icons.Default.Settings)
}

private enum class V3SourceTab { NEWS, VIDEOS, SPECIAL }
private data class V3Weather(val temperature: Int, val description: String)

fun main() = application {
    val trayState = rememberTrayState()
    var visible by remember { mutableStateOf(true) }
    val controller = remember {
        DesktopController { title, message -> runCatching { trayState.sendNotification(Notification(title, message)) } }
    }
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
        MaterialTheme(colorScheme = lightColorScheme(primary = V3Blue, background = V3Bg, surface = Color.White, onSurface = V3Ink)) {
            V3App(controller)
        }
    }
}

@Composable
private fun V3App(c: DesktopController) {
    var section by remember { mutableStateOf(V3Section.HOME) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(500); tick++ } }
    @Suppress("UNUSED_VARIABLE") val redraw = tick
    Column(Modifier.fillMaxSize().background(V3Bg)) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            V3Sidebar(section, { section = it }, c)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (section == V3Section.HOME) {
                    V3HomeHeader(c) { section = it }
                    V3Home(c) { section = it }
                } else {
                    V3PageHeader(section, c)
                    Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
                        when (section) {
                            V3Section.NEWS -> V3News(c)
                            V3Section.VIDEOS -> V3Videos(c)
                            V3Section.DEMANDS -> V3Demands(c)
                            V3Section.SOURCES -> V3Sources(c)
                            V3Section.HISTORY -> V3History(c)
                            V3Section.TERMS -> V3Terms(c)
                            V3Section.SETTINGS -> V3Settings(c)
                            else -> Unit
                        }
                    }
                }
            }
        }
        V3Footer(c)
    }
}

@Composable
private fun V3Sidebar(selected: V3Section, onSelect: (V3Section) -> Unit, c: DesktopController) {
    Column(
        Modifier.width(255.dp).fillMaxHeight()
            .background(Brush.verticalGradient(listOf(V3NavyDark, V3Navy, Color(0xFF073A69))))
            .padding(horizontal = 18.dp, vertical = 22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFB631)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Newspaper, null, tint = V3Navy, modifier = Modifier.size(33.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column {
                Text("MONITOR", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text("DE NOTÍCIAS", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text("Inteligência de mídia", color = Color(0xFFBCD1E9), fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(25.dp))
        V3Section.entries.forEach { item ->
            val active = selected == item
            Row(
                Modifier.fillMaxWidth().height(49.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (active) Brush.horizontalGradient(listOf(Color(0xFF0B83F4), Color(0xFF185CC8))) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)))
                    .clickable { onSelect(item) }.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(item.icon, null, tint = if (active) Color.White else Color(0xFFC7DAEF), modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(15.dp))
                Text(item.label, color = Color.White, fontSize = 15.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                if (item == V3Section.NEWS && c.newNewsLinks.isNotEmpty()) {
                    Spacer(Modifier.weight(1f)); V3CountBadge(c.newNewsLinks.size, V3Gold, V3Navy)
                }
                if (item == V3Section.VIDEOS && c.newVideoLinks.isNotEmpty()) {
                    Spacer(Modifier.weight(1f)); V3CountBadge(c.newVideoLinks.size, V3Purple, Color.White)
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.weight(1f))
        Surface(color = Color(0x19000000), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color(0xFF245481)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(Color(0xFF72E557)))
                    Spacer(Modifier.width(8.dp)); Text("Sistema operacional", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Text("Dados locais • modo portátil", color = Color(0xFFB9CEE6), fontSize = 11.sp)
                V3SideStatus(c.proxyStatusLabel, c.proxyReady || !c.proxyEnabled)
                V3SideStatus(if (c.automaticMonitoring) "Automação ativa" else "Automação pausada", c.automaticMonitoring)
                HorizontalDivider(color = Color(0xFF25517A))
                Text("Windows Portable v4.0.2", color = Color(0xFFC5D6E9), fontSize = 11.sp)
            }
        }
    }
}

@Composable private fun V3SideStatus(text: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.Warning, null, tint = if (ok) Color(0xFF20D08C) else V3Gold, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(7.dp)); Text(text, color = if (ok) Color(0xFFD7E8F7) else Color(0xFFFFD985), fontSize = 10.sp, maxLines = 1)
    }
}

@Composable private fun V3CountBadge(n: Int, bg: Color, fg: Color) {
    Surface(color = bg, shape = RoundedCornerShape(12.dp)) { Text(n.toString(), color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) }
}

@Composable
private fun V3HomeHeader(c: DesktopController, onNavigate: (V3Section) -> Unit) {
    var weather by remember { mutableStateOf<V3Weather?>(null) }
    LaunchedEffect(Unit) { while (true) { weather = v3FetchWeather(); delay(15 * 60_000L) } }
    val now = Date()
    Row(Modifier.fillMaxWidth().height(94.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Eco, null, tint = V3Green, modifier = Modifier.size(38.dp)); Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text("Olá, bem-vindo! 👋", color = V3Ink, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
            Text("Acompanhe notícias, vídeos, demandas e fontes em tempo real.", color = V3Muted, fontSize = 12.sp)
        }
        Surface(Modifier.width(440.dp).height(48.dp), color = Color.White, shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, V3Border), shadowElevation = 5.dp) {
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, tint = Color(0xFF174B8F)); Spacer(Modifier.width(10.dp)); Text("Buscar notícias, vídeos, demandas ou fontes...", color = V3Muted, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.width(18.dp)); Icon(Icons.Default.NotificationsNone, null, tint = Color(0xFF174B8F), modifier = Modifier.size(26.dp)); Spacer(Modifier.width(12.dp))
        Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF164C90)).clickable { onNavigate(V3Section.SETTINGS) }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, tint = Color.White) }
        Spacer(Modifier.width(13.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(115.dp)) {
            Text(SimpleDateFormat("EEE, dd MMM yyyy", Locale("pt", "BR")).format(now), color = V3Muted, fontSize = 8.sp)
            Text(SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(now), color = V3Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        }
        Surface(color = Color(0xFFF8FBFF), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, V3Border), modifier = Modifier.width(126.dp).height(60.dp)) {
            Row(Modifier.padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WbSunny, null, tint = Color(0xFFFFB21B), modifier = Modifier.size(28.dp)); Spacer(Modifier.width(7.dp))
                Column { Text("Brasília - DF", color = V3Muted, fontSize = 8.sp); Text(weather?.let { "${it.temperature}°C" } ?: "--°C", color = V3Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold); Text(weather?.description ?: "Atualizando...", color = V3Muted, fontSize = 7.sp, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun V3PageHeader(section: V3Section, c: DesktopController) {
    val now = Date()
    Row(Modifier.fillMaxWidth().height(112.dp).padding(horizontal = 25.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(57.dp).clip(RoundedCornerShape(2.dp)).background(V3Gold)); Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("CENTRAL DE INTELIGÊNCIA DE MÍDIA", color = Color(0xFFB78900), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(section.label, color = V3Ink, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
            Text(section.subtitle, color = V3Muted, fontSize = 12.sp)
        }
        V3StatusPill(c.proxyStatusLabel, if (c.proxyReady) V3Green else Color(0xFFC88700), if (c.proxyReady) Icons.Default.VerifiedUser else Icons.Default.Security)
        Spacer(Modifier.width(10.dp)); V3StatusPill(if (c.automaticMonitoring) "Automação ativa" else "Automação pausada", if (c.automaticMonitoring) V3Green else V3Orange, Icons.Default.Schedule)
        Spacer(Modifier.width(26.dp)); Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(185.dp)) {
            Text(SimpleDateFormat("EEEE, dd 'de' MMMM 'de' yyyy", Locale("pt", "BR")).format(now), color = V3Muted, fontSize = 9.sp, maxLines = 1)
            Text(SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(now), color = V3Ink, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable private fun V3StatusPill(text: String, color: Color, icon: ImageVector) {
    Surface(color = color.copy(alpha = .08f), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, color.copy(alpha = .12f))) {
        Row(Modifier.padding(horizontal = 13.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = color, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)); Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun V3Home(c: DesktopController, onNavigate: (V3Section) -> Unit) {
    val activeProgress = when { c.newsBusy -> c.newsProgress; c.videoBusy -> c.videoProgress; else -> null }
    val pct = ((activeProgress?.fraction ?: if (c.newsBusy || c.videoBusy) 0f else 1f) * 100f).roundToInt().coerceIn(0, 100)
    val todayVideos = c.videos.count { System.currentTimeMillis() - it.capturedAt < 24 * 60 * 60_000L }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Row(Modifier.fillMaxWidth().height(98.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            V3Metric("Notícias 24h", c.news.size.toString(), Icons.Default.Article, V3Blue, Modifier.weight(1f)) { onNavigate(V3Section.NEWS) }
            V3Metric("Vídeos", c.videos.size.toString(), Icons.Default.PlayCircle, V3Purple, Modifier.weight(1f)) { onNavigate(V3Section.VIDEOS) }
            V3Metric("Vídeos hoje", todayVideos.toString(), Icons.Default.Videocam, V3Green, Modifier.weight(1f)) { onNavigate(V3Section.VIDEOS) }
            V3Metric("Demandas", c.demands.count { it.active }.toString(), Icons.Default.Assignment, V3Orange, Modifier.weight(1f)) { onNavigate(V3Section.DEMANDS) }
            V3Metric("Fontes", SourceCatalog.all.size.toString(), Icons.Default.Storage, Color(0xFFD4085B), Modifier.weight(1f)) { onNavigate(V3Section.SOURCES) }
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(16.dp).clip(CircleShape).background(Color(0xFF31E89A))); Spacer(Modifier.width(9.dp))
                                Text(if (c.newsBusy) "Buscando notícias • $pct%" else if (c.videoBusy) "Buscando vídeos • $pct%" else "Status: Pronto • 100%", color = Color(0xFF31E89A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f)); Text("$pct%", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(7.dp)); LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth().height(5.dp), color = Color(0xFF31E89A), trackColor = Color.White.copy(alpha = .16f))
                        }
                    }
                }
                Icon(Icons.Default.LaptopWindows, null, tint = Color.White.copy(alpha = .27f), modifier = Modifier.align(Alignment.CenterEnd).padding(end = 55.dp).size(190.dp))
            }
            V3Panel(Modifier.weight(1f).fillMaxHeight()) {
                Text("Ações rápidas", color = V3Ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(12.dp))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        V3Action("Buscar notícias", V3Blue, Icons.Default.Search, { c.searchNews() }, Modifier.weight(1f)); V3Action("Buscar demandas", V3Orange, Icons.Default.Assignment, { c.searchAllDemands() }, Modifier.weight(1f))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        V3Action("Buscar vídeos", V3Purple, Icons.Default.PlayCircle, { c.searchVideos() }, Modifier.weight(1f)); V3Action("Termos de busca", V3Green, Icons.Default.Search, { onNavigate(V3Section.TERMS) }, Modifier.weight(1f))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(155.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            V3Panel(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Schedule, null, tint = V3Blue); Spacer(Modifier.width(8.dp)); Text("Agendamento automático", color = V3Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
                Spacer(Modifier.height(14.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    V3MiniSchedule("Notícias", if (c.newsAutomatic) "${c.newsIntervalMinutes} min" else "pausado", V3Blue, Modifier.weight(1f))
                    V3MiniSchedule("Demandas", if (c.demandAutomatic) "${c.demandIntervalMinutes} min" else "pausado", V3Orange, Modifier.weight(1f))
                    V3MiniSchedule("Vídeos", if (c.videoAutomatic) c.videoScheduleTimes.sorted().joinToString(", ") else "pausado", V3Purple, Modifier.weight(1.3f))
                }
            }
            V3Panel(Modifier.weight(1f)) {
                Text("Resumo do dia", color = V3Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { V3Summary("Notícias", c.news.size, V3Blue); V3Summary("Vídeos", c.videos.size, V3Purple); V3Summary("Demandas", c.demands.size, V3Orange) }
            }
        }
    }
}

@Composable private fun V3Metric(title: String, value: String, icon: ImageVector, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    V3Panel(modifier.clickable(onClick = onClick)) { Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = .13f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = accent, modifier = Modifier.size(29.dp)) }; Spacer(Modifier.width(12.dp)); Column { Text(title, color = V3Ink, fontSize = 11.sp); Text(value, color = V3Ink, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold) } } }
}
@Composable private fun V3Action(text: String, accent: Color, icon: ImageVector, onClick: () -> Unit, modifier: Modifier) { Surface(modifier.clickable(onClick = onClick), color = accent, shape = RoundedCornerShape(12.dp)) { Row(Modifier.fillMaxSize().padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Color.White); Spacer(Modifier.width(10.dp)); Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) } } }
@Composable private fun V3MiniSchedule(title: String, value: String, accent: Color, modifier: Modifier) { Surface(modifier, color = Color(0xFFF7FAFE), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, V3Border)) { Column(Modifier.padding(12.dp)) { Text(title, color = V3Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold); Text(value, color = accent, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) } } }
@Composable private fun V3Summary(title: String, n: Int, color: Color) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(n.toString(), color = color, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold); Text(title, color = V3Muted, fontSize = 10.sp) } }

@Composable
private fun V3News(c: DesktopController) {
    var query by remember { mutableStateOf("") }
    var onlyDemands by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(false) }
    var startDate by remember { mutableStateOf(LocalDate.now().minusDays(1).toString()) }
    var endDate by remember { mutableStateOf(LocalDate.now().toString()) }
    val list = c.news.filter { n -> (!onlyDemands || n.demand) && (query.isBlank() || "${n.title} ${n.source} ${n.matchedTerm} ${n.matchedDemand}".contains(query, true)) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        V3Panel(Modifier.fillMaxWidth().height(if (custom) 150.dp else 108.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                V3SearchBox(query, { query = it }, "Buscar nas notícias (título, fonte, termo...)", Modifier.weight(1f)); Spacer(Modifier.width(12.dp))
                Button(onClick = { val p = c.periodLastHours(24); c.searchNews(p.first, p.second) }, enabled = !c.newsBusy, modifier = Modifier.height(48.dp)) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Buscar últimas 24h") }
                Spacer(Modifier.width(10.dp)); OutlinedButton(onClick = { onlyDemands = !onlyDemands }, modifier = Modifier.height(48.dp)) { Icon(Icons.Default.FilterAlt, null); Spacer(Modifier.width(5.dp)); Text(if (onlyDemands) "Todas" else "Só demandas") }
            }
            Spacer(Modifier.height(10.dp)); Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                V3PeriodButton("Hoje") { val end = System.currentTimeMillis(); val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(); c.searchNews(start, end) }
                V3PeriodButton("24 horas") { val p = c.periodLastHours(24); c.searchNews(p.first, p.second) }
                V3PeriodButton("7 dias") { val p = c.periodLastHours(24 * 7); c.searchNews(p.first, p.second) }
                V3PeriodButton("30 dias") { val p = c.periodLastHours(24 * 30); c.searchNews(p.first, p.second) }
                V3PeriodButton("Período personalizado") { custom = !custom }
                Spacer(Modifier.weight(1f)); Text("${list.size} matéria(s) na janela", color = V3Muted, fontSize = 10.sp)
            }
            if (custom) { Spacer(Modifier.height(8.dp)); Row(verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(startDate, { startDate = it }, label = { Text("Data inicial") }, singleLine = true, modifier = Modifier.width(180.dp)); Spacer(Modifier.width(8.dp)); OutlinedTextField(endDate, { endDate = it }, label = { Text("Data final") }, singleLine = true, modifier = Modifier.width(180.dp)); Spacer(Modifier.width(8.dp)); Button(onClick = { c.parsePeriod(startDate, "00:00", endDate, "23:59")?.let { c.searchNews(it.first, it.second) } }, enabled = !c.newsBusy) { Text("Buscar") } } }
        }
        V3ExecutionPanel("Notícias", c.newsBusy, c.newsProgress, c.status, c.newNewsLinks.size, c.lastNewsSearchDurationMs)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Notícias encontradas", color = V3Ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.weight(1f)); Text("${list.size} exibida(s)", color = V3Muted, fontSize = 10.sp) }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(list, key = { it.link }) { V3NewsCard(it, it.link in c.newNewsLinks, c) } }
    }
}

@Composable
private fun V3Videos(c: DesktopController) {
    var query by remember { mutableStateOf("") }
    val list = c.videos.filter { query.isBlank() || "${it.title} ${it.sourceName} ${it.matchedTerm} ${it.matchedDemand}".contains(query, true) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        V3Panel(Modifier.fillMaxWidth().height(108.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { V3SearchBox(query, { query = it }, "Buscar nos vídeos (título, fonte, termo...)", Modifier.weight(1f)); Spacer(Modifier.width(12.dp)); Button(onClick = { c.searchVideos() }, enabled = !c.videoBusy, modifier = Modifier.height(48.dp)) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Buscar vídeos agora") } }
            Spacer(Modifier.height(10.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { V3PeriodButton("24 horas") { val p = c.periodLastHours(24); c.searchVideos(p.first, p.second) }; V3PeriodButton("7 dias") { val p = c.periodLastHours(168); c.searchVideos(p.first, p.second) }; V3PeriodButton("30 dias") { val p = c.periodLastHours(720); c.searchVideos(p.first, p.second) }; Spacer(Modifier.weight(1f)); Text("${list.size} vídeo(s)", color = V3Muted, fontSize = 10.sp) }
        }
        V3ExecutionPanel("Vídeos", c.videoBusy, c.videoProgress, c.videoStatus, c.newVideoLinks.size, c.lastVideoSearchDurationMs)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(list, key = { it.link }) { V3VideoCard(it, it.link in c.newVideoLinks) } }
    }
}

@Composable
private fun V3ExecutionPanel(kind: String, busy: Boolean, p: LiveSearchProgress, status: String, fresh: Int, duration: Long) {
    val fraction = if (busy) p.fraction.coerceIn(0f, 1f) else 1f
    val pct = (fraction * 100).roundToInt()
    V3Panel(Modifier.fillMaxWidth().height(132.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background((if (busy) V3Blue else V3Green).copy(alpha = .12f)), contentAlignment = Alignment.Center) { Icon(if (busy) Icons.Default.Sync else Icons.Default.CheckCircle, null, tint = if (busy) V3Blue else V3Green, modifier = Modifier.size(29.dp)) }
            Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(if (busy) "$kind • busca em andamento" else "$kind • última execução concluída", color = V3Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold); Text(status, color = V3Muted, fontSize = 10.sp, maxLines = 1); Spacer(Modifier.height(8.dp)); LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(5.dp), color = if (busy) V3Blue else V3Green, trackColor = Color(0xFFE4EDF7)) }
            Spacer(Modifier.width(14.dp)); V3Stat("$pct%", "conclusão", V3Blue); V3Stat(p.found.toString(), "encontrados", V3Blue); V3Stat(fresh.toString(), "novos", V3Green); V3Stat(p.errors.toString(), "falhas", V3Red); V3Stat("${p.completed}/${p.total}", "etapas", Color(0xFF3562A0)); V3Stat(v3Duration(duration), "tempo", Color(0xFF3562A0))
        }
        Spacer(Modifier.height(10.dp)); Surface(color = if (busy) Color(0xFFEAF4FF) else Color(0xFFE9F8F1), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Fonte atual: ${p.currentSource.ifBlank { "iniciando..." }} • Consulta: ${p.currentQuery.ifBlank { "preparando..." }}" else "$fresh novo(s) nesta execução. Ao iniciar outra busca, a marcação Nova é recalculada.", color = if (busy) V3Blue else V3Green, fontSize = 10.sp, modifier = Modifier.padding(9.dp)) }
    }
}

@Composable private fun V3Stat(value: String, label: String, color: Color) { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) { Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold); Text(label, color = V3Muted, fontSize = 8.sp) } }

@Composable
private fun V3NewsCard(n: News, isNew: Boolean, c: DesktopController) {
    val scope = rememberCoroutineScope()
    V3Panel(Modifier.fillMaxWidth().height(122.dp)) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(58.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFE7F2FF)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Article, null, tint = V3Blue, modifier = Modifier.size(31.dp)) }
            Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("${n.source} • ${v3Date(n.date)}", color = V3Muted, fontSize = 9.sp); if (isNew) { Spacer(Modifier.width(8.dp)); V3NewBadge() } }
                Text(n.title, color = V3Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (n.snippet.isNotBlank()) Text(n.snippet, color = V3Muted, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { if (n.matchedTerm.isNotBlank()) V3Chip(n.matchedTerm); if (n.matchedDemand.isNotBlank()) V3Chip(n.matchedDemand) }
            }
            Spacer(Modifier.width(10.dp)); V3LinkButtons(
                open = { scope.launch { v3Open(c.resolveVehicleUrl(n.link)) } },
                copy = { scope.launch { v3Copy(c.resolveVehicleUrl(n.link)) } },
                share = { scope.launch { val direct = c.resolveVehicleUrl(n.link); v3OpenWhatsApp(n.title, direct) } }
            )
        }
    }
}

@Composable
private fun V3VideoCard(v: VideoItem, isNew: Boolean) {
    V3Panel(Modifier.fillMaxWidth().height(118.dp)) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(58.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF0E8FF)), contentAlignment = Alignment.Center) { Icon(Icons.Default.PlayCircle, null, tint = V3Purple, modifier = Modifier.size(33.dp)) }
            Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("${v.sourceName} • ${v3Date(v.publishedAt)}", color = V3Muted, fontSize = 9.sp); if (isNew) { Spacer(Modifier.width(8.dp)); V3NewBadge() } }
                Text(v.title, color = V3Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (v.summary.isNotBlank()) Text(v.summary, color = V3Muted, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(10.dp)); V3LinkButtons(open = { v3Open(v.link) }, copy = { v3Copy(v.link) }, share = { v3OpenWhatsApp(v.title, v.link) })
        }
    }
}

@Composable private fun V3NewBadge() { Surface(color = V3Blue, shape = RoundedCornerShape(10.dp)) { Text("Nova", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)) } }
@Composable private fun V3Chip(text: String) { Surface(color = Color(0xFFEAF1FB), shape = RoundedCornerShape(8.dp)) { Text(text, color = Color(0xFF48658E), fontSize = 8.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) } }
@Composable private fun V3LinkButtons(open: () -> Unit, copy: () -> Unit, share: () -> Unit) { Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { OutlinedButton(onClick = open) { Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Abrir matéria") }; OutlinedButton(onClick = share) { Icon(Icons.Default.Share, null, tint = V3Green, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("WhatsApp") }; OutlinedButton(onClick = copy) { Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Copiar link") } } }

@Composable
private fun V3Terms(c: DesktopController) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        V3TermCard("Termos de Notícias", "Usados na varredura de matérias", Icons.Default.Article, V3Blue, c.terms, { c.addTerm(it) }, { c.removeTerm(it) }, Modifier.weight(1f))
        V3TermCard("Termos de Vídeos", "Lista independente para vídeos", Icons.Default.PlayCircle, V3Purple, c.videoTerms, { c.addVideoTerm(it) }, { c.removeVideoTerm(it) }, Modifier.weight(1f))
    }
}

@Composable
private fun V3TermCard(title: String, subtitle: String, icon: ImageVector, accent: Color, items: List<String>, add: (String) -> Unit, remove: (String) -> Unit, modifier: Modifier) {
    var value by remember { mutableStateOf("") }
    V3Panel(modifier.fillMaxHeight()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = .10f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = accent, modifier = Modifier.size(30.dp)) }
            Spacer(Modifier.width(15.dp)); Column(Modifier.weight(1f)) { Text(title, color = V3Ink, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold); Text(subtitle, color = V3Muted, fontSize = 12.sp) }
            Surface(color = Color(0xFFE7F2FF), shape = RoundedCornerShape(10.dp)) { Text("${items.size} termo(s)", color = Color(0xFF315B91), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) }
        }
        Spacer(Modifier.height(18.dp)); Row(verticalAlignment = Alignment.CenterVertically) {
            V3SearchBox(value, { value = it }, "Novo termo", Modifier.weight(1f)); Spacer(Modifier.width(10.dp)); Button(onClick = { add(value); value = "" }, enabled = value.isNotBlank(), modifier = Modifier.height(50.dp).width(150.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(7.dp)); Text("Adicionar") }
        }
        Spacer(Modifier.height(14.dp)); LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(items, key = { it }) { term ->
                Surface(color = Color(0xFFF2F6FB), shape = RoundedCornerShape(9.dp), modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFE1EEFC)), contentAlignment = Alignment.Center) { Text("#", color = V3Blue, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(14.dp)); Text(term, color = V3Ink, fontSize = 13.sp, modifier = Modifier.weight(1f)); IconButton(onClick = { remove(term) }) { Icon(Icons.Default.DeleteOutline, null, tint = V3Red) }
                    }
                }
            }
        }
    }
}

@Composable
private fun V3Settings(c: DesktopController) {
    var proxyEnabled by remember { mutableStateOf(c.proxyEnabled) }; var user by remember { mutableStateOf(c.proxyUsername) }; var pass by remember { mutableStateOf(c.proxyPassword) }; var proxyMessage by remember { mutableStateOf("") }; val scope = rememberCoroutineScope()
    var videoTime by remember { mutableStateOf("") }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            V3Panel(Modifier.fillMaxWidth().height(205.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(45.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFE7F2FF)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Lock, null, tint = V3Blue) }
                    Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Configuração de proxy", color = V3Ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold); Text("Configure usuário e senha para liberar as buscas pelo proxy.", color = V3Muted, fontSize = 10.sp) }
                    Switch(proxyEnabled, onCheckedChange = { proxyEnabled = it }); Spacer(Modifier.width(10.dp)); Surface(color = Color(0xFFF5F8FC), shape = RoundedCornerShape(9.dp)) { Text("${c.proxyHost}:${c.proxyPort}", color = V3Muted, fontSize = 10.sp, modifier = Modifier.padding(10.dp)) }
                }
                if (proxyEnabled && (user.isBlank() || pass.isBlank())) { Spacer(Modifier.height(10.dp)); Surface(color = Color(0xFFFFE9EB), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Error, null, tint = V3Red, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Configure usuário e senha para liberar as buscas pelo proxy.", color = V3Red, fontSize = 10.sp) } } }
                Spacer(Modifier.height(10.dp)); Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(user, { user = it }, placeholder = { Text("Usuário") }, leadingIcon = { Icon(Icons.Default.Person, null) }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(pass, { pass = it }, placeholder = { Text("Senha") }, leadingIcon = { Icon(Icons.Default.Lock, null) }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.weight(1f))
                    Button(onClick = { c.saveProxy(proxyEnabled, "proxy-7db.mb", 6060, user, pass); proxyMessage = "Salvo" }, modifier = Modifier.height(56.dp)) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(5.dp)); Text("Salvar e aplicar") }
                    OutlinedButton(onClick = { scope.launch { val r = c.testProxyConnection(); proxyMessage = r.second } }, modifier = Modifier.height(56.dp)) { Icon(Icons.Default.Wifi, null); Spacer(Modifier.width(5.dp)); Text("Testar conexão") }
                }
                if (proxyMessage.isNotBlank()) Text(proxyMessage, color = if (proxyMessage.contains("sucesso", true) || proxyMessage == "Salvo") V3Green else V3Muted, fontSize = 9.sp)
            }
        }
        item {
            V3Panel(Modifier.fillMaxWidth().height(110.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, tint = V3Blue, modifier = Modifier.size(31.dp)); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Buscas automáticas", color = V3Ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold); Text("Controle Notícias, Demandas e Vídeos de forma independente.", color = V3Muted, fontSize = 10.sp) }
                    Column(horizontalAlignment = Alignment.End) { Text("Controle geral", color = V3Muted, fontSize = 9.sp); Switch(c.automaticMonitoring, onCheckedChange = { c.automaticMonitoring = it }) }
                }
                HorizontalDivider(color = V3Border); Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Computer, null, tint = V3Blue); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Iniciar Monitor de Notícias com o Windows", color = V3Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold); Text("O aplicativo será iniciado automaticamente ao entrar no sistema.", color = V3Muted, fontSize = 9.sp) }; Switch(c.startWithWindows, onCheckedChange = { c.startWithWindows = it }) }
            }
        }
        item { V3AutomationCard("Notícias", "Varredura automática das fontes de notícias selecionadas", Icons.Default.Article, V3Blue, c.newsAutomatic, { c.newsAutomatic = it }, c.newsIntervalMinutes, { c.newsIntervalMinutes = it }, { c.searchNews() }, c.lastNewsAutoAt) }
        item { V3AutomationCard("Demandas", "Pesquisa automática de todas as demandas ativas", Icons.Default.Assignment, V3Gold, c.demandAutomatic, { c.demandAutomatic = it }, c.demandIntervalMinutes, { c.demandIntervalMinutes = it }, { c.searchAllDemands() }, c.lastDemandAutoAt) }
        item {
            V3Panel(Modifier.fillMaxWidth().height(205.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(V3Purple.copy(alpha = .10f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.PlayCircle, null, tint = V3Purple) }
                    Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Vídeos", color = V3Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold); Text("Busca automática nos horários escolhidos", color = V3Muted, fontSize = 10.sp) }
                    Text("Automático", color = V3Muted, fontSize = 9.sp); Spacer(Modifier.width(7.dp)); Switch(c.videoAutomatic, onCheckedChange = { c.videoAutomatic = it }); Spacer(Modifier.width(12.dp)); OutlinedButton(onClick = { c.searchVideos() }) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(5.dp)); Text("Executar agora") }
                }
                Spacer(Modifier.height(9.dp)); Text("Horários", color = V3Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    c.videoScheduleTimes.sorted().forEach { t -> InputChip(selected = true, onClick = { c.videoScheduleTimes = c.videoScheduleTimes - t }, label = { Text(t) }, trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp)) }) }
                }
                Spacer(Modifier.height(8.dp)); Row(verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(videoTime, { videoTime = it.take(5) }, label = { Text("Novo horário HH:mm") }, singleLine = true, modifier = Modifier.width(190.dp)); Spacer(Modifier.width(8.dp)); Button(onClick = { runCatching { java.time.LocalTime.parse(videoTime) }.onSuccess { c.videoScheduleTimes = c.videoScheduleTimes + it.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")); videoTime = "" } }, enabled = videoTime.length == 5) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(5.dp)); Text("Adicionar horário") }; Spacer(Modifier.weight(1f)); Text("Última automática: ${v3AutoTime(c.lastVideoAutoAt)}", color = V3Muted, fontSize = 9.sp) }
            }
        }
    }
}

@Composable
private fun V3AutomationCard(title: String, subtitle: String, icon: ImageVector, accent: Color, enabled: Boolean, onEnabled: (Boolean) -> Unit, interval: Int, onInterval: (Int) -> Unit, runNow: () -> Unit, lastAt: Long) {
    V3Panel(Modifier.fillMaxWidth().height(185.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = .11f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = accent) }
            Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(title, color = V3Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold); if (enabled) { Spacer(Modifier.width(8.dp)); Surface(color = Color(0xFFE7F8EF), shape = RoundedCornerShape(9.dp)) { Text("automático ativo", color = V3Green, fontSize = 8.sp, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)) } } }; Text(subtitle, color = V3Muted, fontSize = 10.sp) }
            Text("Automático", color = V3Muted, fontSize = 9.sp); Spacer(Modifier.width(7.dp)); Switch(enabled, onCheckedChange = onEnabled); Spacer(Modifier.width(12.dp)); OutlinedButton(onClick = runNow) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(5.dp)); Text("Executar agora") }
        }
        Spacer(Modifier.height(10.dp)); Row { Text("Última automática: ${v3AutoTime(lastAt)}", color = V3Muted, fontSize = 9.sp); Spacer(Modifier.width(30.dp)); Text("Próxima execução: ${v3NextAuto(lastAt, interval)}", color = V3Muted, fontSize = 9.sp) }
        Spacer(Modifier.height(8.dp)); HorizontalDivider(color = V3Border); Spacer(Modifier.height(8.dp)); Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text("Frequência", color = V3Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold); listOf(15,30,45,60,120).forEach { m -> FilterChip(selected = interval == m, onClick = { onInterval(m) }, label = { Text(if (m < 60) "$m min" else if (m == 60) "1 hora" else "2 horas") }) } }
    }
}

@Composable
private fun V3Sources(c: DesktopController) {
    var tab by remember { mutableStateOf(V3SourceTab.NEWS) }; var query by remember { mutableStateOf("") }; var region by remember { mutableStateOf("Todas") }; var state by remember { mutableStateOf("Todos") }
    val newsList = SourceCatalog.all.filter { (region == "Todas" || it.region == region) && (state == "Todos" || it.state == state) && (query.isBlank() || "${it.name} ${it.stateName} ${it.group}".contains(query, true)) }
    val videoBase = if (tab == V3SourceTab.SPECIAL) VideoSourceCatalog.all.take(8) else VideoSourceCatalog.all
    val videoList = videoBase.filter { (region == "Todas" || it.region == region) && (state == "Todos" || it.state == state) && (query.isBlank() || "${it.name} ${it.group}".contains(query, true)) }
    val visible = if (tab == V3SourceTab.NEWS) newsList.size else videoList.size
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        V3Panel(Modifier.fillMaxWidth().height(200.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { V3Tab("Notícias", tab == V3SourceTab.NEWS) { tab = V3SourceTab.NEWS }; Spacer(Modifier.width(7.dp)); V3Tab("Vídeos", tab == V3SourceTab.VIDEOS) { tab = V3SourceTab.VIDEOS }; Spacer(Modifier.width(7.dp)); V3Tab("Mídias especializadas (8)", tab == V3SourceTab.SPECIAL) { tab = V3SourceTab.SPECIAL }; Spacer(Modifier.weight(1f)); V3SearchBox(query, { query = it }, "Pesquisar fonte...", Modifier.width(390.dp)) }
            Spacer(Modifier.height(12.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Text("Região", color = V3Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(65.dp)); SourceCatalog.regions.forEach { r -> V3Filter(r, region == r) { region = r; state = "Todos" }; Spacer(Modifier.width(5.dp)) } }
            Spacer(Modifier.height(9.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Text("Estado", color = V3Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(65.dp)); Row(Modifier.horizontalScroll(rememberScrollState())) { V3Filter("Todos", state == "Todos") { state = "Todos" }; Spacer(Modifier.width(5.dp)); SourceCatalog.states.filter { region == "Todas" || region == "Nacional" || it.third == region }.forEach { s -> V3Filter(s.first, state == s.first) { state = s.first }; Spacer(Modifier.width(5.dp)) } } }
            Spacer(Modifier.height(10.dp)); Surface(color = if (c.newsAllSources) Color(0xFFE8F8F1) else Color(0xFFF7FAFE), shape = RoundedCornerShape(9.dp), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Public, null, tint = if (c.newsAllSources) V3Green else V3Muted); Spacer(Modifier.width(8.dp)); Column(Modifier.weight(1f)) { Text("Buscar em todos os veículos", color = V3Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold); Text("Quando ligado, a busca de notícias ignora o seletor de fontes.", color = V3Muted, fontSize = 8.sp) }; Switch(c.newsAllSources, onCheckedChange = { c.newsAllSources = it }) } }
        }
        V3Panel(Modifier.fillMaxWidth().height(78.dp)) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Storage, null, tint = V3Blue, modifier = Modifier.size(31.dp)); Spacer(Modifier.width(12.dp)); Text("$visible fonte(s) visível(is)", color = V3Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.weight(1f)); Button(onClick = { if (tab == V3SourceTab.NEWS) newsList.forEach { c.setNewsSource(it.id, true) } else videoList.forEach { c.setVideoSource(it.id, true) } }) { Text("Selecionar visíveis") }; Spacer(Modifier.width(7.dp)); OutlinedButton(onClick = { if (tab == V3SourceTab.NEWS) newsList.forEach { c.setNewsSource(it.id, false) } else videoList.forEach { c.setVideoSource(it.id, false) } }) { Text("Limpar visíveis") }; Spacer(Modifier.width(7.dp)); OutlinedButton(onClick = { if (tab == V3SourceTab.NEWS) c.selectAllNewsSources() else c.selectAllVideoSources() }) { Text("Todas") }; Spacer(Modifier.width(7.dp)); OutlinedButton(onClick = { if (tab == V3SourceTab.NEWS) c.clearNewsSources() else c.clearVideoSources() }) { Text("Nenhuma") } }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            if (tab == V3SourceTab.NEWS) items(newsList, key = { it.id }) { s -> V3SourceRow(s.name, "${s.group} • ${s.region} • ${s.state.ifBlank { "BR" }}", c.newsAllSources || s.id in c.selectedNewsSourceIds) { c.setNewsSource(s.id, it) } }
            else items(videoList, key = { it.id }) { s -> V3SourceRow(s.name, "${s.group} • ${s.region} • ${s.state}", s.id in c.selectedVideoSourceIds) { c.setVideoSource(s.id, it) } }
        }
    }
}

@Composable private fun V3Tab(text: String, selected: Boolean, click: () -> Unit) { Surface(color = if (selected) Color(0xFFEDE7FF) else Color.White, shape = RoundedCornerShape(9.dp), border = BorderStroke(1.dp, V3Border)) { Text(text, color = V3Ink, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.clickable(onClick = click).padding(horizontal = 13.dp, vertical = 9.dp)) } }
@Composable private fun V3Filter(text: String, selected: Boolean, click: () -> Unit) { Surface(color = if (selected) Color(0xFFDCEBFF) else Color.White, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, V3Border)) { Text(text, color = if (selected) V3Blue else V3Muted, fontSize = 9.sp, modifier = Modifier.clickable(onClick = click).padding(horizontal = 12.dp, vertical = 7.dp)) } }
@Composable private fun V3SourceRow(name: String, detail: String, checked: Boolean, change: (Boolean) -> Unit) { V3Panel(Modifier.fillMaxWidth().height(61.dp)) { Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, onCheckedChange = change); Spacer(Modifier.width(10.dp)); Box(Modifier.width(64.dp).height(36.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0B4C8C)), contentAlignment = Alignment.Center) { Text(name.take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Bold) }; Spacer(Modifier.width(16.dp)); Column(Modifier.weight(1f)) { Text(name, color = V3Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold); Text(detail, color = V3Muted, fontSize = 9.sp) }; Icon(Icons.Default.ChevronRight, null, tint = V3Blue) } } }

@Composable
private fun V3Demands(c: DesktopController) {
    var vehicle by remember { mutableStateOf("") }; var subject by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        V3Panel(Modifier.fillMaxWidth().height(105.dp)) { Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(vehicle, { vehicle = it }, label = { Text("Veículo") }, placeholder = { Text("Selecione ou digite o veículo...") }, singleLine = true, modifier = Modifier.weight(.9f)); OutlinedTextField(subject, { subject = it }, label = { Text("Assunto") }, placeholder = { Text("Digite o assunto da demanda...") }, singleLine = true, modifier = Modifier.weight(1.2f)); Button(onClick = { c.addDemand(vehicle, subject); vehicle = ""; subject = "" }, enabled = vehicle.isNotBlank() && subject.isNotBlank(), modifier = Modifier.height(54.dp)) { Icon(Icons.Default.Add, null); Text("Adicionar") }; OutlinedButton(onClick = { c.searchAllDemands() }, enabled = !c.newsBusy, modifier = Modifier.height(54.dp)) { Icon(Icons.Default.Refresh, null); Text("Buscar todas") } } }
        Surface(color = Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, V3Border), modifier = Modifier.fillMaxWidth().height(66.dp)) { Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(17.dp).clip(CircleShape).background(if (c.newsBusy) V3Orange else V3Green)); Spacer(Modifier.width(15.dp)); Column { Text(if (c.newsBusy) "Status: Buscando" else "Status: Pronto", color = if (c.newsBusy) V3Orange else V3Green, fontSize = 13.sp, fontWeight = FontWeight.Bold); Text(if (c.newsBusy) c.status else "Sistema disponível para consultar e gerenciar demandas.", color = V3Muted, fontSize = 10.sp) } } }
        if (c.demands.isEmpty()) V3Panel(Modifier.weight(1f).fillMaxWidth()) { Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(Icons.Default.NotificationsActive, null, tint = Color(0xFF8BBEF3), modifier = Modifier.size(110.dp)); Spacer(Modifier.height(20.dp)); Text("Nenhuma demanda carregada no momento", color = V3Ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold); Text("Adicione uma nova demanda ou utilize os filtros para buscar demandas já cadastradas.", color = V3Muted, fontSize = 13.sp); Spacer(Modifier.height(20.dp)); Button(onClick = { c.searchAllDemands() }) { Icon(Icons.Default.Search, null); Spacer(Modifier.width(6.dp)); Text("Buscar todas") } } }
        else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(c.demands, key = { it.id }) { d -> V3Panel(Modifier.fillMaxWidth().height(82.dp)) { Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Assignment, null, tint = V3Orange); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("${d.vehicle} • ${d.subject}", color = V3Ink, fontWeight = FontWeight.Bold); Text("Última busca: ${v3AutoTime(d.lastCheckedAt)} • encontrados ${d.lastFoundCount} • novos ${d.lastNewCount}", color = V3Muted, fontSize = 9.sp) }; OutlinedButton(onClick = { c.searchDemand(d) }) { Text("Buscar") }; IconButton(onClick = { c.removeDemand(d.id) }) { Icon(Icons.Default.DeleteOutline, null, tint = V3Red) } } } } }
    }
}

@Composable
private fun V3History(c: DesktopController) {
    var tab by remember { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        V3Panel(Modifier.fillMaxWidth().height(70.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Notícias") }); Spacer(Modifier.width(8.dp)); FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Vídeos") }); Spacer(Modifier.weight(1f)); OutlinedButton(onClick = { if (tab == 0) c.clearNewsHistory() else c.clearVideoHistory() }) { Icon(Icons.Default.DeleteOutline, null); Text("Limpar histórico") } } }
        if (tab == 0) { val all = c.newsDb.listNews(2000); LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(all, key = { it.link }) { V3NewsCard(it, false, c) } } }
        else { val all = c.videoDb.listAll(2000); LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(all, key = { it.link }) { V3VideoCard(it, false) } } }
    }
}

@Composable private fun V3SearchBox(value: String, change: (String) -> Unit, hint: String, modifier: Modifier) { Surface(modifier.height(48.dp), color = Color.White, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, V3Border)) { Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Search, null, tint = Color(0xFF43658C)); Spacer(Modifier.width(10.dp)); BasicTextField(value, change, singleLine = true, modifier = Modifier.weight(1f), textStyle = LocalTextStyle.current.copy(color = V3Ink, fontSize = 11.sp), decorationBox = { inner -> if (value.isBlank()) Text(hint, color = V3Muted, fontSize = 11.sp); inner() }) } } }
@Composable private fun V3PeriodButton(text: String, click: () -> Unit) { OutlinedButton(onClick = click, contentPadding = PaddingValues(horizontal = 13.dp, vertical = 6.dp)) { Text(text, fontSize = 9.sp) } }
@Composable private fun V3Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) { Surface(modifier.shadow(4.dp, RoundedCornerShape(13.dp)), color = Color.White, shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, V3Border)) { Column(Modifier.fillMaxSize().padding(15.dp), content = content) } }
@Composable private fun V3Footer(c: DesktopController) { Row(Modifier.fillMaxWidth().height(40.dp).background(Color(0xFFF7FAFE)).padding(horizontal = 25.dp), verticalAlignment = Alignment.CenterVertically) { Text("Monitor de Notícias v4.0.2", color = V3Muted, fontSize = 9.sp); Spacer(Modifier.width(20.dp)); Text("Inteligência de mídia para melhores decisões", color = V3Muted, fontSize = 9.sp); Spacer(Modifier.weight(1f)); Box(Modifier.size(10.dp).clip(CircleShape).background(if (c.newsBusy || c.videoBusy) V3Orange else V3Green)); Spacer(Modifier.width(7.dp)); Text(if (c.newsBusy || c.videoBusy) "Busca em andamento" else "Sistema operacional", color = V3Muted, fontSize = 9.sp) } }

private fun v3Open(url: String) { runCatching { if (AwtDesktop.isDesktopSupported()) AwtDesktop.getDesktop().browse(URI(url)) } }
private fun v3Copy(text: String) { runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) } }
private fun v3OpenWhatsApp(title: String, url: String) { val msg = URLEncoder.encode("$title\n$url", Charsets.UTF_8.name()); v3Open("https://wa.me/?text=$msg") }
private fun v3Date(ts: Long): String = if (ts <= 0) "--" else SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(ts))
private fun v3Duration(ms: Long): String { if (ms <= 0) return "--"; val sec = ms / 1000; return "%02d:%02d".format(sec / 60, sec % 60) }
private fun v3AutoTime(ts: Long): String = if (ts <= 0) "—" else SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(Date(ts))
private fun v3NextAuto(last: Long, interval: Int): String = if (last <= 0) "na próxima verificação" else SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(Date(last + interval * 60_000L))

private suspend fun v3FetchWeather(): V3Weather? = withContext(Dispatchers.IO) {
    runCatching {
        val raw = URL("https://api.open-meteo.com/v1/forecast?latitude=-15.7939&longitude=-47.8828&current=temperature_2m,weather_code&timezone=America%2FSao_Paulo").readText()
        val current = JSONObject(raw).getJSONObject("current")
        val temp = current.getDouble("temperature_2m").roundToInt(); val code = current.optInt("weather_code", 0)
        V3Weather(temp, when (code) { 0 -> "Tempo limpo"; 1,2 -> "Parcialmente nublado"; 3 -> "Nublado"; in 51..67 -> "Chuva"; in 80..82 -> "Pancadas de chuva"; in 95..99 -> "Trovoadas"; else -> "Condição atual" })
    }.getOrNull()
}
