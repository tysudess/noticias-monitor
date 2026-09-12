package br.com.monitordenoticias.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
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

private val V5Navy = Color(0xFF052D57)
private val V5NavyDark = Color(0xFF031F3E)
private val V5Ink = Color(0xFF0A1F4B)
private val V5Muted = Color(0xFF58739F)
private val V5Bg = Color(0xFFF3F8FE)
private val V5Border = Color(0xFFD5E4F3)
private val V5Blue = Color(0xFF087AF7)
private val V5Purple = Color(0xFF743AF3)
private val V5Orange = Color(0xFFFF820A)
private val V5Green = Color(0xFF08A86F)
private val V5Red = Color(0xFFD92F43)
private val V5Gold = Color(0xFFF2B715)
private val V5SoftBlue = Color(0xFFEAF4FF)

private enum class V5Section(val label: String, val subtitle: String, val icon: ImageVector) {
    HOME("Início", "Acompanhe notícias, vídeos, demandas e fontes em tempo real.", Icons.Default.Home),
    NEWS("Notícias", "Busca e acompanhamento de matérias com atualização contínua", Icons.Default.Article),
    VIDEOS("Vídeos", "Busca e acompanhamento de vídeos relevantes", Icons.Default.PlayCircle),
    DEMANDS("Demandas", "Assuntos prioritários acompanhados por veículo", Icons.Default.Assignment),
    SOURCES("Fontes", "Fontes nacionais, regionais e mídias especializadas", Icons.Default.Storage),
    HISTORY("Histórico", "Histórico local das buscas e resultados", Icons.Default.History),
    TERMS("Termos", "Termos independentes para notícias e vídeos", Icons.Default.Search),
    STOP("Parar buscas", "Interrompa buscas manuais em andamento", Icons.Default.StopCircle),
    SETTINGS("Configurações", "Automação, proxy, inicialização e operação do aplicativo", Icons.Default.Settings),
    PDF_EDITOR("Editor de PDF", "Monte, reorganize, recorte e exporte PDFs e imagens", Icons.Default.PictureAsPdf),
    EXTRACTOR("Extrator de Vídeos", "Baixe vídeos com o fluxo direto v3.0.1", Icons.Default.Download),
    VIDEO_EDITOR("Editor de Vídeo", "Edite sequências, cortes e exportações de vídeo", Icons.Default.Movie)
}

private enum class V5SourceTab { NEWS, VIDEOS, SPECIAL }
private data class V5Weather(val temperature: Int, val description: String)

fun main() = application {
    val trayState = rememberTrayState()
    val appIcon = painterResource("monitor-icon.svg")
    var visible by remember { mutableStateOf(true) }
    val controller = remember {
        DesktopControllerV5 { title, message ->
            runCatching { trayState.sendNotification(Notification(title, message)) }
        }
    }

    Tray(
        state = trayState,
        icon = appIcon,
        tooltip = "Monitor de Notícias v4.0.2",
        menu = {
            Item("Abrir", onClick = { visible = true })
            Separator()
            Item("Buscar notícias agora", onClick = { controller.searchNews() })
            Item("Buscar vídeos agora", onClick = { controller.searchVideos() })
            Item("Buscar demandas agora", onClick = { controller.searchAllDemands() })
            Item("Parar buscas", onClick = { controller.stopAllSearches() })
            Separator()
            Item("Sair", onClick = { controller.close(); exitApplication() })
        }
    )

    Window(
        visible = visible,
        onCloseRequest = { visible = false },
        title = "Monitor de Notícias - Windows Portable v4.0.2",
        icon = appIcon,
        state = rememberWindowState(width = 1600.dp, height = 960.dp)
    ) {
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = V5Blue,
                background = V5Bg,
                surface = Color.White,
                onSurface = V5Ink
            )
        ) {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, density.fontScale * 1.08f)
            ) {
                V5App(controller)
            }
        }
    }
}

@Composable
private fun V5App(c: DesktopControllerV5) {
    var section by remember { mutableStateOf(V5Section.HOME) }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(250)
            tick++
        }
    }

    if (section == V5Section.PDF_EDITOR || section == V5Section.VIDEO_EDITOR) {
        Column(Modifier.fillMaxSize().background(V5Bg)) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                V5Sidebar(section, { section = it }, c, tick)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    when (section) {
              V5Section.PDF_EDITOR -> PdfEditorScreenV2 { section = V5Section.HOME }
              V5Section.VIDEO_EDITOR -> VideoEditorScreen { section = V5Section.HOME }
              else -> Unit
          }
                }
            }
            V5Footer(c, tick)
        }
    } else {
        Column(Modifier.fillMaxSize().background(V5Bg)) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                V5Sidebar(section, { section = it }, c, tick)
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (section == V5Section.HOME) {
                        V5HomeHeader(c, { section = it }, tick)
                        V5Home(c, { section = it }, tick)
                    } else {
                        V5PageHeader(section, c, tick)
                        Box(
                            Modifier.weight(1f).fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 10.dp)
                        ) {
                            when (section) {
                                V5Section.NEWS -> V5NewsScreen(c, tick)
                                V5Section.VIDEOS -> V5VideosScreen(c, tick)
                                V5Section.DEMANDS -> V5DemandsScreen(c, tick)
                                V5Section.SOURCES -> V5SourcesScreen(c, tick)
                                V5Section.HISTORY -> V5HistoryScreen(c, tick)
                                V5Section.TERMS -> V5TermsScreen(c, tick)
                                V5Section.STOP -> V5StopScreen(c, tick)
                                V5Section.SETTINGS -> V5SettingsScreen(c, tick)
                                V5Section.EXTRACTOR -> ExtractorVideoScreen { section = V5Section.HOME }
                                else -> Unit
                            }
                        }
                    }
                }
            }
            V5Footer(c, tick)
        }
    }
}

@Composable
private fun V5Sidebar(selected: V5Section, onSelect: (V5Section) -> Unit, c: DesktopControllerV5, tick: Int) {
    @Suppress("UNUSED_VARIABLE") val redraw = tick
    Column(
        Modifier.width(258.dp).fillMaxHeight()
            .background(Brush.verticalGradient(listOf(V5NavyDark, V5Navy, Color(0xFF073A69))))
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFB631)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Newspaper, null, tint = V5Navy, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column {
                Text("MONITOR", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                Text("DE NOTÍCIAS", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                Text("Inteligência de mídia", color = Color(0xFFBCD1E9), fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(18.dp))
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            V5Section.entries.forEach { item ->
                val active = selected == item
                Row(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(10.dp))
                        .background(
                            if (active) Brush.horizontalGradient(listOf(Color(0xFF0B83F4), Color(0xFF185CC8)))
                            else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                        )
                        .clickable { onSelect(item) }
                        .padding(horizontal = 15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(item.icon, null, tint = if (active) Color.White else Color(0xFFC7DAEF), modifier = Modifier.size(23.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(item.label, color = Color.White, fontSize = 15.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                    when {
                        item == V5Section.NEWS && c.newNewsLinks.isNotEmpty() -> {
                            Spacer(Modifier.weight(1f)); V5CountBadge(c.newNewsLinks.size, V5Gold, V5Navy)
                        }
                        item == V5Section.VIDEOS && c.newVideoLinks.isNotEmpty() -> {
                            Spacer(Modifier.weight(1f)); V5CountBadge(c.newVideoLinks.size, V5Purple, Color.White)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Surface(
            color = Color(0x19000000),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF245481)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(Color(0xFF72E557)))
                    Spacer(Modifier.width(8.dp))
                    Text("Sistema operacional", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Text("Dados locais • modo portátil", color = Color(0xFFB9CEE6), fontSize = 11.sp)
                V5SideStatus(c.proxyStatusLabel, c.proxyReady || !c.proxyEnabled)
                V5SideStatus(if (c.automaticMonitoring) "Automação ativa" else "Automação pausada", c.automaticMonitoring)
                HorizontalDivider(color = Color(0xFF25517A))
                Text("Windows Portable v4.0.2", color = Color(0xFFC5D6E9), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun V5SideStatus(text: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Default.CheckCircle else Icons.Default.Warning,
            null,
            tint = if (ok) Color(0xFF20D08C) else V5Gold,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(7.dp))
        Text(text, color = if (ok) Color(0xFFD7E8F7) else Color(0xFFFFD985), fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun V5CountBadge(n: Int, bg: Color, fg: Color) {
    Surface(color = bg, shape = RoundedCornerShape(13.dp)) {
        Text(n.toString(), color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp))
    }
}

@Composable
private fun V5HomeHeader(c: DesktopControllerV5, onNavigate: (V5Section) -> Unit, tick: Int) {
    var weather by remember { mutableStateOf<V5Weather?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            weather = V5FetchWeather()
            delay(15 * 60_000L)
        }
    }
    val now = Date(System.currentTimeMillis() + tick * 0L)

    Row(
        Modifier.fillMaxWidth().height(94.dp).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Eco, null, tint = V5Green, modifier = Modifier.size(38.dp))
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text("Olá, bem-vindo! 👋", color = V5Ink, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text("Acompanhe notícias, vídeos, demandas e fontes em tempo real.", color = V5Muted, fontSize = 12.sp)
        }

        Surface(
            Modifier.width(430.dp).height(50.dp),
            color = Color.White,
            shape = RoundedCornerShape(13.dp),
            border = BorderStroke(1.dp, V5Border),
            shadowElevation = 4.dp
        ) {
            Row(Modifier.padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, tint = Color(0xFF174B8F), modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("Buscar notícias, vídeos, demandas ou fontes...", color = V5Muted, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.width(16.dp))
        Icon(Icons.Default.NotificationsNone, null, tint = Color(0xFF174B8F), modifier = Modifier.size(25.dp))
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF164C90)).clickable { onNavigate(V5Section.SETTINGS) },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Person, null, tint = Color.White) }
        Spacer(Modifier.width(12.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(112.dp)) {
            Text(SimpleDateFormat("dd MMM yyyy", Locale("pt", "BR")).format(now), color = V5Muted, fontSize = 8.sp)
            Text(SimpleDateFormat("HH:mm:ss", Locale("pt", "BR")).format(now), color = V5Ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }

        Surface(
            color = Color(0xFFF8FBFF),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, V5Border),
            modifier = Modifier.width(126.dp).height(62.dp)
        ) {
            Row(Modifier.padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WbSunny, null, tint = Color(0xFFFFB21B), modifier = Modifier.size(29.dp))
                Spacer(Modifier.width(7.dp))
                Column {
                    Text("Brasília - DF", color = V5Muted, fontSize = 8.sp)
                    Text(weather?.let { "${it.temperature}°C" } ?: "--°C", color = V5Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(weather?.description ?: "Atualizando...", color = V5Muted, fontSize = 7.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun V5PageHeader(section: V5Section, c: DesktopControllerV5, tick: Int) {
    val now = Date(System.currentTimeMillis() + tick * 0L)
    Row(
        Modifier.fillMaxWidth().height(82.dp).padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(4.dp).height(44.dp).clip(RoundedCornerShape(2.dp)).background(V5Gold))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("CENTRAL DE INTELIGÊNCIA DE MÍDIA", color = Color(0xFFB78900), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(section.label, color = V5Ink, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text(section.subtitle, color = V5Muted, fontSize = 12.sp)
        }
        V5StatusPill(c.proxyStatusLabel, if (c.proxyReady) V5Green else Color(0xFFC88700), if (c.proxyReady) Icons.Default.VerifiedUser else Icons.Default.Security)
        Spacer(Modifier.width(10.dp))
        V5StatusPill(if (c.automaticMonitoring) "Automação ativa" else "Automação pausada", if (c.automaticMonitoring) V5Green else V5Orange, Icons.Default.Schedule)
        Spacer(Modifier.width(18.dp))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(150.dp)) {
            Text(SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(now), color = V5Muted, fontSize = 9.sp)
            Text(SimpleDateFormat("HH:mm:ss", Locale("pt", "BR")).format(now), color = V5Ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun V5StatusPill(text: String, color: Color, icon: ImageVector) {
    Surface(color = color.copy(alpha = .08f), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, color.copy(alpha = .18f))) {
        Row(Modifier.padding(horizontal = 13.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
            Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun V5Home(c: DesktopControllerV5, onNavigate: (V5Section) -> Unit, tick: Int) {
    @Suppress("UNUSED_VARIABLE") val redraw = tick
    val activeProgress = when {
        c.newsBusy -> c.newsProgress
        c.videoBusy -> c.videoProgress
        else -> null
    }
    val pct = ((activeProgress?.fraction ?: if (c.newsBusy || c.videoBusy) 0f else 1f) * 100f)
        .roundToInt().coerceIn(0, 100)
    val todayVideos = c.videos.count { System.currentTimeMillis() - it.capturedAt < 24L * 60L * 60L * 1000L }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 18.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth().height(104.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            V5Metric("Notícias 24h", c.news.size.toString(), Icons.Default.Article, V5Blue, Modifier.weight(1f)) { onNavigate(V5Section.NEWS) }
            V5Metric("Vídeos", c.videos.size.toString(), Icons.Default.PlayCircle, V5Purple, Modifier.weight(1f)) { onNavigate(V5Section.VIDEOS) }
            V5Metric("Vídeos hoje", todayVideos.toString(), Icons.Default.Videocam, V5Green, Modifier.weight(1f)) { onNavigate(V5Section.VIDEOS) }
            V5Metric("Demandas", c.demands.count { it.active }.toString(), Icons.Default.Assignment, V5Orange, Modifier.weight(1f)) { onNavigate(V5Section.DEMANDS) }
            V5Metric("Fontes", SourceCatalog.all.size.toString(), Icons.Default.Storage, Color(0xFFD4085B), Modifier.weight(1f)) { onNavigate(V5Section.SOURCES) }
        }

        Row(Modifier.fillMaxWidth().height(270.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.weight(1.38f).fillMaxHeight().clip(RoundedCornerShape(15.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF082A52), Color(0xFF0A4E8C), Color(0xFF3288D5))))
            ) {
                Column(Modifier.fillMaxHeight().width(500.dp).padding(horizontal = 28.dp, vertical = 25.dp)) {
                    Text("CENTRAL DE INTELIGÊNCIA DE MÍDIA", color = Color(0xFFC9DDF1), fontSize = 11.sp, letterSpacing = 1.2.sp)
                    Spacer(Modifier.height(9.dp))
                    Text("Tudo o que importa\nem um só lugar.", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 34.sp)
                    Spacer(Modifier.height(9.dp))
                    Text("Buscas e resultados atualizados automaticamente,\nem tempo real.", color = Color(0xFFE8F1FB), fontSize = 12.sp, lineHeight = 18.sp)
                    Spacer(Modifier.weight(1f))

                    Surface(
                        color = Color(0xFF075B59),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF09B18E)),
                        modifier = Modifier.width(395.dp)
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(16.dp).clip(CircleShape).background(Color(0xFF31E89A)))
                                Spacer(Modifier.width(9.dp))
                                Text(
                                    when {
                                        c.newsBusy -> "Buscando notícias"
                                        c.videoBusy -> "Buscando vídeos"
                                        else -> "Status: Pronto"
                                    },
                                    color = Color(0xFF45F0AA),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.weight(1f))
                                Text("$pct%", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { pct / 100f },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
                                color = Color(0xFF31E89A),
                                trackColor = Color.White.copy(alpha = .16f)
                            )
                            if (activeProgress != null && (c.newsBusy || c.videoBusy)) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "${activeProgress.completed}/${activeProgress.total} etapas • ${activeProgress.currentSource.ifBlank { "Preparando" }}",
                                    color = Color(0xFFCDEDE2),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Column(
                    Modifier.align(Alignment.CenterEnd).padding(end = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.LaptopWindows, null, tint = Color.White.copy(alpha = .32f), modifier = Modifier.size(185.dp))
                    Text("Monitoramento integrado", color = Color.White.copy(alpha = .60f), fontSize = 11.sp)
                }
            }

            V5Card(Modifier.weight(1f).fillMaxHeight()) {
                Text("Ações rápidas", color = V5Ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text("Execute as principais rotinas sem sair do painel.", color = V5Muted, fontSize = 11.sp)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        V5Action("Buscar notícias", "Varredura manual", V5Blue, Icons.Default.Search, { c.searchNews() }, Modifier.weight(1f))
                        V5Action("Buscar demandas", "Demandas ativas", V5Orange, Icons.Default.Assignment, { c.searchAllDemands() }, Modifier.weight(1f))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        V5Action("Buscar vídeos", "Fontes selecionadas", V5Purple, Icons.Default.PlayCircle, { c.searchVideos() }, Modifier.weight(1f))
                        V5Action("Termos de busca", "Gerenciar palavras-chave", V5Green, Icons.Default.Search, { onNavigate(V5Section.TERMS) }, Modifier.weight(1f))
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            V5Card(Modifier.weight(1f).height(188.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, tint = V5Blue, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(9.dp))
                    Column {
                        Text("Agendamento automático", color = V5Ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                        Text("O sistema executa buscas automaticamente nos horários definidos.", color = V5Muted, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(13.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    V5MiniSchedule("Notícias", if (c.newsAutomatic) V5IntervalLabel(c.newsIntervalMinutes) else "pausado", V5Blue, Modifier.weight(1f))
                    V5MiniSchedule("Demandas", if (c.demandAutomatic) V5IntervalLabel(c.demandIntervalMinutes) else "pausado", V5Orange, Modifier.weight(1f))
                    V5MiniSchedule("Vídeos", if (c.videoAutomatic) c.videoScheduleTimes.sorted().joinToString(", ") else "pausado", V5Purple, Modifier.weight(1.35f))
                }
            }

            V5Card(Modifier.weight(1f).height(188.dp)) {
                Text("Resumo do dia", color = V5Ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                Text("Dados atuais disponíveis no aplicativo.", color = V5Muted, fontSize = 11.sp)
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    V5Summary("Notícias", c.news.size, V5Blue)
                    V5Summary("Vídeos", c.videos.size, V5Purple)
                    V5Summary("Demandas", c.demands.size, V5Orange)
                }
            }
        }

        Row(Modifier.fillMaxWidth().height(215.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            V5Card(Modifier.weight(1f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Storage, null, tint = V5Blue)
                    Spacer(Modifier.width(8.dp))
                    Text("Fontes em destaque", color = V5Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(10.dp))
                val selected = if (c.newsAllSources) SourceCatalog.all.take(5) else SourceCatalog.all.filter { it.id in c.selectedNewsSourceIds }.take(5)
                selected.forEach { s ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(V5Green))
                        Spacer(Modifier.width(8.dp))
                        Text(s.name, color = V5Ink, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(s.region, color = V5Muted, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (c.newsAllSources) "Todos os veículos: ligado" else "${c.selectedNewsSourceIds.size} fonte(s) selecionada(s)",
                    color = if (c.newsAllSources) V5Green else V5Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            V5Card(Modifier.weight(1f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, null, tint = V5Purple)
                    Spacer(Modifier.width(8.dp))
                    Text("Últimas atividades", color = V5Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(10.dp))
                V5ActivityLine("Notícias", c.status, V5Blue)
                V5ActivityLine("Vídeos", c.videoStatus, V5Purple)
                V5ActivityLine("Fontes instáveis", if (c.unstableVideoSources.isEmpty()) "Nenhuma" else c.unstableVideoSources.joinToString(", ") { it.sourceName }, if (c.unstableVideoSources.isEmpty()) V5Green else V5Orange)
                V5ActivityLine("Proxy", c.proxyStatusLabel, if (c.proxyReady) V5Green else V5Orange)
                V5ActivityLine("Automação", if (c.automaticMonitoring) "Ativa" else "Pausada", V5Green)
            }

            V5Card(Modifier.weight(1f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lightbulb, null, tint = V5Gold)
                    Spacer(Modifier.width(8.dp))
                    Text("Dicas e operação", color = V5Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(12.dp))
                Surface(color = V5SoftBlue, shape = RoundedCornerShape(11.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.Center) {
                        Text("Resultados mais precisos", color = V5Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Use termos específicos. Para notícias, ligue 'Todos os veículos' quando quiser incluir também fontes fora do catálogo padrão.",
                            color = V5Muted,
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun V5Metric(title: String, value: String, icon: ImageVector, accent: Color, modifier: Modifier, click: () -> Unit) {
    V5Card(modifier.clickable(onClick = click), padding = 14.dp) {
        Row(Modifier.fillMaxWidth().height(74.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(13.dp)).background(accent.copy(alpha = .13f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column {
                Text(title, color = V5Ink, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Text(value, color = V5Ink, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun V5Action(title: String, subtitle: String, accent: Color, icon: ImageVector, click: () -> Unit, modifier: Modifier) {
    Surface(modifier.clickable(onClick = click), color = accent, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Color.White.copy(alpha = .84f), fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun V5MiniSchedule(title: String, value: String, accent: Color, modifier: Modifier) {
    Surface(modifier.height(78.dp), color = Color(0xFFF7FAFE), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, V5Border)) {
        Column(Modifier.padding(horizontal = 11.dp, vertical = 10.dp)) {
            Text(title, color = V5Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(value, color = accent, fontSize = 11.sp, lineHeight = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun V5Summary(title: String, n: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(n.toString(), color = color, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text(title, color = V5Muted, fontSize = 10.sp)
    }
}

@Composable
private fun V5ActivityLine(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, color = V5Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(76.dp))
        Text(value, color = V5Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun V5NewsScreen(c: DesktopControllerV5, tick: Int) {
    var query by remember { mutableStateOf("") }
    var onlyDemands by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(false) }
    var startDate by remember { mutableStateOf(LocalDate.now().minusDays(1).toString()) }
    var startTime by remember { mutableStateOf("00:00") }
    var endDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var endTime by remember { mutableStateOf("23:59") }

    val list = remember(tick, query, onlyDemands) {
        c.news.filter { n ->
            (!onlyDemands || n.demand) &&
                (query.isBlank() || "${n.title} ${n.source} ${n.matchedTerm} ${n.matchedDemand}".contains(query, true))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        V5Card(Modifier.fillMaxWidth(), padding = 10.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                V5SearchBox(query, { query = it }, "Buscar nas notícias (título, fonte, termo...)", Modifier.weight(1f).height(42.dp))
                Spacer(Modifier.width(9.dp))
                Button(
                    onClick = { val p = c.periodLastHours(24); c.searchNews(p.first, p.second) },
                    enabled = !c.newsBusy,
                    modifier = Modifier.height(42.dp)
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(7.dp)); Text("Buscar últimas 24h", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = { onlyDemands = !onlyDemands }, modifier = Modifier.height(42.dp)) {
                    Icon(Icons.Default.FilterAlt, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp)); Text(if (onlyDemands) "Mostrar todas" else "Só demandas", fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                V5PeriodButton("Hoje") {
                    val end = System.currentTimeMillis()
                    val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    c.searchNews(start, end)
                }
                V5PeriodButton("24 horas") { val p = c.periodLastHours(24); c.searchNews(p.first, p.second) }
                V5PeriodButton("7 dias") { val p = c.periodLastHours(168); c.searchNews(p.first, p.second) }
                V5PeriodButton("30 dias") { val p = c.periodLastHours(720); c.searchNews(p.first, p.second) }
                V5PeriodButton(if (custom) "Fechar período personalizado" else "Período personalizado", selected = custom) { custom = !custom }
                Spacer(Modifier.weight(1f))
                Text("${list.size} matéria(s) na janela", color = V5Muted, fontSize = 10.sp)
            }

            if (custom) {
                Spacer(Modifier.height(8.dp))
                Surface(color = Color(0xFFF6F9FD), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, V5Border), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        V5LabeledField("Data inicial", startDate, { startDate = it }, "AAAA-MM-DD", Modifier.width(155.dp))
                        V5LabeledField("Hora inicial", startTime, { startTime = it.take(5) }, "00:00", Modifier.width(105.dp))
                        V5LabeledField("Data final", endDate, { endDate = it }, "AAAA-MM-DD", Modifier.width(155.dp))
                        V5LabeledField("Hora final", endTime, { endTime = it.take(5) }, "23:59", Modifier.width(105.dp))
                        Button(
                            onClick = { c.parsePeriod(startDate, startTime, endDate, endTime)?.let { c.searchNews(it.first, it.second) } },
                            enabled = !c.newsBusy,
                            modifier = Modifier.height(44.dp)
                        ) {
                            Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp)); Text("Buscar período", fontSize = 11.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        Text("Formato: AAAA-MM-DD", color = V5Muted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 12.dp))
                    }
                }
            }
        }

        V5ExecutionPanel("Notícias", c.newsBusy, c.newsProgress, c.status, c.newNewsLinks.size, c.lastNewsSearchDurationMs, tick)
        if (c.newsBusy) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = { c.stopNewsSearch() }) {
                    Icon(Icons.Default.StopCircle, null, tint = V5Red)
                    Spacer(Modifier.width(6.dp))
                    Text("Parar busca", color = V5Red, fontWeight = FontWeight.Bold)
                }
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Notícias encontradas", color = V5Ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f)); Text("${list.size} exibida(s)", color = V5Muted, fontSize = 10.sp)
        }

        if (list.isEmpty()) {
            V5EmptyState("Nenhuma notícia nesta visualização", "Execute uma busca ou altere os filtros para exibir resultados.", Icons.Default.Article, V5Blue, Modifier.weight(1f))
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(list, key = { it.link }) { V5NewsCard(it, it.link in c.newNewsLinks, c, showMatched = true) }
            }
        }
    }
}

@Composable
private fun V5VideosScreen(c: DesktopControllerV5, tick: Int) {
    var query by remember { mutableStateOf("") }
    var custom by remember { mutableStateOf(false) }
    var startDate by remember { mutableStateOf(LocalDate.now().minusDays(1).toString()) }
    var startTime by remember { mutableStateOf("00:00") }
    var endDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var endTime by remember { mutableStateOf("23:59") }

    val list = remember(tick, query) {
        c.videos.filter { query.isBlank() || "${it.title} ${it.sourceName} ${it.matchedTerm} ${it.matchedDemand}".contains(query, true) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        V5Card(Modifier.fillMaxWidth(), padding = 10.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                V5SearchBox(query, { query = it }, "Buscar nos vídeos (título, fonte, termo...)", Modifier.weight(1f).height(42.dp))
                Spacer(Modifier.width(9.dp))
                Button(onClick = { c.searchVideos() }, enabled = !c.videoBusy, modifier = Modifier.height(42.dp)) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(7.dp)); Text("Buscar vídeos agora", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                V5PeriodButton("24 horas") { val p = c.periodLastHours(24); c.searchVideos(p.first, p.second) }
                V5PeriodButton("7 dias") { val p = c.periodLastHours(168); c.searchVideos(p.first, p.second) }
                V5PeriodButton("30 dias") { val p = c.periodLastHours(720); c.searchVideos(p.first, p.second) }
                V5PeriodButton(if (custom) "Fechar período personalizado" else "Período personalizado", selected = custom) { custom = !custom }
                Spacer(Modifier.weight(1f)); Text("${list.size} vídeo(s)", color = V5Muted, fontSize = 11.sp)
            }

            if (custom) {
                Spacer(Modifier.height(8.dp))
                Surface(color = Color(0xFFF8F5FF), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, V5Purple.copy(alpha = .20f)), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        V5LabeledField("Data inicial", startDate, { startDate = it }, "AAAA-MM-DD", Modifier.width(155.dp))
                        V5LabeledField("Hora inicial", startTime, { startTime = it.take(5) }, "00:00", Modifier.width(105.dp))
                        V5LabeledField("Data final", endDate, { endDate = it }, "AAAA-MM-DD", Modifier.width(155.dp))
                        V5LabeledField("Hora final", endTime, { endTime = it.take(5) }, "23:59", Modifier.width(105.dp))
                        Button(
                            onClick = { c.parsePeriod(startDate, startTime, endDate, endTime)?.let { c.searchVideos(it.first, it.second) } },
                            enabled = !c.videoBusy,
                            modifier = Modifier.height(44.dp)
                        ) {
                            Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp)); Text("Buscar período", fontSize = 11.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        Text("Formato: AAAA-MM-DD", color = V5Muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 12.dp))
                    }
                }
            }
        }

        V5ExecutionPanel("Vídeos", c.videoBusy, c.videoProgress, c.videoStatus, c.newVideoLinks.size, c.lastVideoSearchDurationMs, tick)
        if (c.videoBusy) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = { c.stopVideoSearch() }) {
                    Icon(Icons.Default.StopCircle, null, tint = V5Red)
                    Spacer(Modifier.width(6.dp))
                    Text("Parar busca", color = V5Red, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (c.unstableVideoSources.isNotEmpty()) {
            V5UnstableVideoSourcesPanel(c.unstableVideoSources)
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Vídeos encontrados", color = V5Ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f)); Text("${list.size} exibido(s)", color = V5Muted, fontSize = 11.sp)
        }

        if (list.isEmpty()) {
            V5EmptyState("Nenhum vídeo nesta visualização", "Execute uma busca manual ou selecione outras fontes de vídeo.", Icons.Default.PlayCircle, V5Purple, Modifier.weight(1f))
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(list, key = { it.link }) { V5VideoCard(it, it.link in c.newVideoLinks, showMatched = true) }
            }
        }
    }
}

@Composable
private fun V5StopScreen(c: DesktopControllerV5, tick: Int) {
    @Suppress("UNUSED_VARIABLE") val redraw = tick
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        V5Card(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(54.dp).clip(CircleShape).background(V5Red.copy(alpha = .10f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.StopCircle, null, tint = V5Red, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Controle de buscas em andamento", color = V5Ink, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Interrompa uma busca manual sem fechar o aplicativo. A automação permanece configurada.", color = V5Muted, fontSize = 12.sp)
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            V5Card(Modifier.weight(1f)) {
                Text("Notícias e demandas", color = V5Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(if (c.newsBusy) c.status else "Nenhuma busca em andamento", color = V5Muted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { c.stopNewsSearch() }, enabled = c.newsBusy) {
                    Icon(Icons.Default.Stop, null)
                    Spacer(Modifier.width(7.dp))
                    Text("Parar notícias/demandas")
                }
            }
            V5Card(Modifier.weight(1f)) {
                Text("Vídeos", color = V5Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(if (c.videoBusy) c.videoStatus else "Nenhuma busca em andamento", color = V5Muted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { c.stopVideoSearch() }, enabled = c.videoBusy) {
                    Icon(Icons.Default.Stop, null)
                    Spacer(Modifier.width(7.dp))
                    Text("Parar vídeos")
                }
            }
        }

        V5Card(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Parar todas as buscas", color = V5Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("Use este comando quando quiser interromper simultaneamente notícia/demanda e vídeo.", color = V5Muted, fontSize = 11.sp)
                }
                Button(onClick = { c.stopAllSearches() }, enabled = c.newsBusy || c.videoBusy, colors = ButtonDefaults.buttonColors(containerColor = V5Red)) {
                    Icon(Icons.Default.StopCircle, null)
                    Spacer(Modifier.width(7.dp))
                    Text("Parar tudo", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun V5UnstableVideoSourcesPanel(issues: List<VideoSourceIssue>) {
    Surface(
        color = Color(0xFFFFF8EC),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, V5Gold.copy(alpha = .36f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.WarningAmber, null, tint = V5Orange, modifier = Modifier.size(21.dp))
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text("Fontes com instabilidade na última busca", color = V5Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    issues.take(4).joinToString("   •   ") { "${it.sourceName}: ${it.stage} (${it.failureCount})" },
                    color = Color(0xFF9A6500),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(10.dp))
            V5Tag("${issues.size} fonte(s)", V5Orange, 90.dp)
        }
    }
}

@Composable
private fun V5ExecutionPanel(kind: String, busy: Boolean, p: LiveSearchProgress, status: String, fresh: Int, duration: Long, tick: Int) {
    val fraction = if (busy) p.fraction.coerceIn(0f, 1f) else 1f
    val pct = (fraction * 100).roundToInt()
    val elapsed = if (busy && p.startedAt > 0) System.currentTimeMillis() - p.startedAt else duration
    @Suppress("UNUSED_VARIABLE") val redraw = tick

    V5Card(Modifier.fillMaxWidth(), padding = 9.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background((if (busy) V5Blue else V5Green).copy(alpha = .12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(if (busy) Icons.Default.Sync else Icons.Default.CheckCircle, null, tint = if (busy) V5Blue else V5Green, modifier = Modifier.size(25.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (busy) "$kind • busca em andamento" else "$kind • última execução concluída", color = V5Ink, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                Text(status, color = V5Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(10.dp))
            V5Stat("$pct%", "conclusão", V5Blue)
            V5Stat(p.found.toString(), "encontrados", V5Blue)
            V5Stat(fresh.toString(), "novos", V5Green)
            V5Stat(p.errors.toString(), "falhas", V5Red)
            V5Stat("${p.completed}/${p.total}", "etapas", Color(0xFF3562A0))
            V5Stat(V5Duration(elapsed), "tempo", Color(0xFF3562A0))
        }

        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp)),
            color = if (busy) V5Blue else V5Green,
            trackColor = Color(0xFFE4EDF7)
        )
        Spacer(Modifier.height(6.dp))
        Surface(
            color = if (busy) V5SoftBlue else Color(0xFFE9F8F1),
            shape = RoundedCornerShape(7.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (busy) {
                    "${p.currentSource.ifBlank { "Preparando..." }} • ${p.currentQuery.ifBlank { "Preparando consulta..." }}"
                } else {
                    "$fresh novo(s) nesta execução. A marcação Nova é recalculada a cada nova busca."
                },
                color = if (busy) V5Blue else V5Green,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun V5Stat(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(66.dp)) {
        Text(value, color = color, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(label, color = V5Muted, fontSize = 10.sp)
    }
}

@Composable
private fun V5NewsCard(n: News, isNew: Boolean, c: DesktopControllerV5, showMatched: Boolean) {
    val scope = rememberCoroutineScope()
    V5Card(Modifier.fillMaxWidth(), padding = 11.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFE7F2FF)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Article, null, tint = V5Blue, modifier = Modifier.size(27.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${n.source} • ${V5Date(n.date)}", color = V5Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (isNew) { Spacer(Modifier.width(8.dp)); V5NewBadge() }
                }
                Spacer(Modifier.height(4.dp))
                Text(n.title, color = V5Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
                if (n.snippet.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(V5CleanText(n.snippet), color = V5Muted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
                }
                if (showMatched && (n.matchedTerm.isNotBlank() || n.matchedDemand.isNotBlank())) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (n.matchedTerm.isNotBlank()) V5Tag("Termo: ${n.matchedTerm}", V5Blue, 285.dp)
                        if (n.matchedDemand.isNotBlank()) V5Tag("Demanda: ${n.matchedDemand}", V5Orange, 320.dp)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            V5LinkButtons(
                primaryLabel = "Abrir matéria",
                open = { scope.launch { V5Open(c.resolveVehicleUrl(n.link)) } },
                copy = { scope.launch { V5Copy(c.resolveVehicleUrl(n.link)) } },
                share = { scope.launch { val direct = c.resolveVehicleUrl(n.link); V5OpenWhatsApp(n.title, direct) } }
            )
        }
    }
}

@Composable
private fun V5VideoCard(v: VideoItem, isNew: Boolean, showMatched: Boolean) {
    V5Card(Modifier.fillMaxWidth(), padding = 11.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF0E8FF)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayCircle, null, tint = V5Purple, modifier = Modifier.size(29.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${v.sourceName} • ${V5Date(v.publishedAt)}", color = V5Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (isNew) { Spacer(Modifier.width(8.dp)); V5NewBadge() }
                }
                Spacer(Modifier.height(4.dp))
                Text(v.title, color = V5Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
                if (v.summary.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(V5CleanText(v.summary), color = V5Muted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
                }
                if (showMatched && (v.matchedTerm.isNotBlank() || v.matchedDemand.isNotBlank())) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (v.matchedTerm.isNotBlank()) V5Tag("Termo: ${v.matchedTerm}", V5Purple, 285.dp)
                        if (v.matchedDemand.isNotBlank()) V5Tag("Demanda: ${v.matchedDemand}", V5Orange, 320.dp)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            V5LinkButtons(
                primaryLabel = "Abrir vídeo",
                open = { V5Open(v.link) },
                copy = { V5Copy(v.link) },
                share = { V5OpenWhatsApp(v.title, v.link) }
            )
        }
    }
}

@Composable
private fun V5LinkButtons(primaryLabel: String, open: () -> Unit, copy: () -> Unit, share: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = open, modifier = Modifier.height(40.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
            Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text(primaryLabel, fontSize = 11.sp)
        }
        OutlinedButton(onClick = share, modifier = Modifier.height(40.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
            Icon(Icons.Default.Share, null, tint = V5Green, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("WhatsApp", fontSize = 11.sp, color = V5Green)
        }
        OutlinedButton(onClick = copy, modifier = Modifier.height(40.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Copiar link", fontSize = 11.sp)
        }
    }
}

@Composable
private fun V5NewBadge() {
    Surface(color = V5Blue, shape = RoundedCornerShape(10.dp)) {
        Text("Nova", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp))
    }
}

@Composable
private fun V5Tag(text: String, color: Color, maxWidth: androidx.compose.ui.unit.Dp) {
    Surface(color = color.copy(alpha = .13f), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, color.copy(alpha = .42f)), modifier = Modifier.widthIn(max = maxWidth)) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
    }
}

@Composable
private fun V5TermsScreen(c: DesktopControllerV5, tick: Int) {
    @Suppress("UNUSED_VARIABLE") val redraw = tick
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        V5TermCard("Termos de Notícias", "Usados na varredura de matérias", Icons.Default.Article, V5Blue, c.terms, { c.addTerm(it) }, { c.removeTerm(it) }, Modifier.weight(1f))
        V5TermCard("Termos de Vídeos", "Lista independente para vídeos", Icons.Default.PlayCircle, V5Purple, c.videoTerms, { c.addVideoTerm(it) }, { c.removeVideoTerm(it) }, Modifier.weight(1f))
    }
}

@Composable
private fun V5TermCard(title: String, subtitle: String, icon: ImageVector, accent: Color, values: List<String>, add: (String) -> Unit, remove: (String) -> Unit, modifier: Modifier) {
    var value by remember { mutableStateOf("") }
    V5Card(modifier.fillMaxHeight()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(62.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = .10f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(15.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = V5Ink, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text(subtitle, color = V5Muted, fontSize = 11.sp)
            }
            Surface(color = Color(0xFFE7F2FF), shape = RoundedCornerShape(10.dp)) {
                Text("${values.size} termo(s)", color = Color(0xFF315B91), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            V5SearchBox(value, { value = it }, "Novo termo", Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            Button(onClick = { add(value); value = "" }, enabled = value.isNotBlank(), modifier = Modifier.height(50.dp).width(145.dp)) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Adicionar", fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(values, key = { it }) { term ->
                Surface(color = Color(0xFFF2F6FB), shape = RoundedCornerShape(9.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFE1EEFC)), contentAlignment = Alignment.Center) {
                            Text("#", color = V5Blue, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(term, color = V5Ink, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { remove(term) }) { Icon(Icons.Default.DeleteOutline, null, tint = V5Red) }
                    }
                }
            }
        }
    }
}

@Composable
private fun V5SettingsScreen(c: DesktopControllerV5, tick: Int) {
    var proxyEnabled by remember { mutableStateOf(c.proxyEnabled) }
    var user by remember { mutableStateOf(c.proxyUsername) }
    var pass by remember { mutableStateOf(c.proxyPassword) }
    var proxyMessage by remember { mutableStateOf("") }
    var videoTime by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    @Suppress("UNUSED_VARIABLE") val redraw = tick

    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            V5Card(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(V5SoftBlue), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Lock, null, tint = V5Blue)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Configuração de proxy", color = V5Ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Servidor fixo ${c.proxyHost}:${c.proxyPort}. Informe usuário e senha para autenticação.", color = V5Muted, fontSize = 11.sp)
                    }
                    Text(if (proxyEnabled) "Ativado" else "Desativado", color = if (proxyEnabled) V5Green else V5Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp)); Switch(proxyEnabled, onCheckedChange = { proxyEnabled = it })
                }

                if (proxyEnabled && (user.isBlank() || pass.isBlank())) {
                    Spacer(Modifier.height(10.dp))
                    Surface(color = Color(0xFFFFE9EB), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, null, tint = V5Red, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Configure usuário e senha para liberar as buscas pelo proxy.", color = V5Red, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                    V5LabeledField("Usuário", user, { user = it }, "Usuário do proxy", Modifier.weight(1f))
                    V5LabeledField("Senha", pass, { pass = it }, "Senha do proxy", Modifier.weight(1f), password = true)
                    Button(
                        onClick = { c.saveProxy(proxyEnabled, "proxy-7db.mb", 6060, user, pass); proxyMessage = "Configuração salva e aplicada." },
                        modifier = Modifier.height(44.dp)
                    ) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(5.dp)); Text("Salvar e aplicar", fontSize = 11.sp) }
                    OutlinedButton(
                        onClick = { scope.launch { val r = c.testProxyConnection(); proxyMessage = r.second } },
                        modifier = Modifier.height(44.dp)
                    ) { Icon(Icons.Default.Wifi, null); Spacer(Modifier.width(5.dp)); Text("Testar conexão", fontSize = 11.sp) }
                }
                if (proxyMessage.isNotBlank()) {
                    Spacer(Modifier.height(7.dp))
                    Text(proxyMessage, color = if (proxyMessage.contains("sucesso", true) || proxyMessage.contains("salva", true)) V5Green else V5Muted, fontSize = 11.sp)
                }
            }
        }

        item {
            V5Card(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, tint = V5Blue, modifier = Modifier.size(31.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Buscas automáticas", color = V5Ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Notícias, Demandas e Vídeos funcionam de forma independente.", color = V5Muted, fontSize = 11.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Controle geral", color = V5Muted, fontSize = 11.sp)
                        Switch(c.automaticMonitoring, onCheckedChange = { c.automaticMonitoring = it })
                    }
                }
                Spacer(Modifier.height(8.dp)); HorizontalDivider(color = V5Border); Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Computer, null, tint = V5Blue)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Iniciar Monitor de Notícias com o Windows", color = V5Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("Inicialização automática ao entrar no sistema.", color = V5Muted, fontSize = 11.sp)
                    }
                    Switch(c.startWithWindows, onCheckedChange = { c.startWithWindows = it })
                }
            }
        }

        item {
            V5AutomationCard("Notícias", "Varredura automática das fontes selecionadas", Icons.Default.Article, V5Blue, c.newsAutomatic, { c.newsAutomatic = it }, c.newsIntervalMinutes, { c.newsIntervalMinutes = it }, { c.searchNews() }, c.lastNewsAutoAt, tick)
        }
        item {
            V5AutomationCard("Demandas", "Pesquisa automática de todas as demandas ativas", Icons.Default.Assignment, V5Gold, c.demandAutomatic, { c.demandAutomatic = it }, c.demandIntervalMinutes, { c.demandIntervalMinutes = it }, { c.searchAllDemands() }, c.lastDemandAutoAt, tick)
        }
        item {
            V5Card(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(V5Purple.copy(alpha = .10f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PlayCircle, null, tint = V5Purple)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Vídeos", color = V5Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Busca automática nos horários escolhidos; busca manual permanece disponível.", color = V5Muted, fontSize = 11.sp)
                    }
                    Text("Automático", color = V5Muted, fontSize = 11.sp)
                    Spacer(Modifier.width(7.dp)); Switch(c.videoAutomatic, onCheckedChange = { c.videoAutomatic = it })
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(onClick = { c.searchVideos() }, modifier = Modifier.height(42.dp)) {
                        Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(5.dp)); Text("Executar agora", fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(11.dp))
                Text("Horários programados", color = V5Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    c.videoScheduleTimes.sorted().forEach { t -> V5TimeChip(t) { c.videoScheduleTimes = c.videoScheduleTimes - t } }
                }
                Spacer(Modifier.height(11.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    V5LabeledField("Novo horário", videoTime, { videoTime = it.take(5) }, "HH:mm", Modifier.width(165.dp))
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            runCatching { java.time.LocalTime.parse(videoTime) }.onSuccess {
                                c.videoScheduleTimes = c.videoScheduleTimes + it.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                                videoTime = ""
                            }
                        },
                        enabled = videoTime.length == 5,
                        modifier = Modifier.height(44.dp)
                    ) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(5.dp)); Text("Adicionar horário", fontSize = 11.sp) }
                    Spacer(Modifier.weight(1f))
                    Text("Última automática: ${V5AutoTime(c.lastVideoAutoAt)}", color = V5Muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 12.dp))
                }
            }
        }
        item { Spacer(Modifier.height(6.dp)) }
    }
}

@Composable
private fun V5AutomationCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    enabled: Boolean,
    onEnabled: (Boolean) -> Unit,
    interval: Int,
    onInterval: (Int) -> Unit,
    runNow: () -> Unit,
    lastAt: Long,
    tick: Int
) {
    @Suppress("UNUSED_VARIABLE") val redraw = tick
    V5Card(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = .11f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = accent)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = V5Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    if (enabled) { Spacer(Modifier.width(8.dp)); V5Tag("automático ativo", V5Green, 120.dp) }
                }
                Text(subtitle, color = V5Muted, fontSize = 11.sp)
            }
            Text("Automático", color = V5Muted, fontSize = 11.sp)
            Spacer(Modifier.width(7.dp)); Switch(enabled, onCheckedChange = onEnabled)
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = runNow, modifier = Modifier.height(42.dp)) {
                Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(5.dp)); Text("Executar agora", fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row {
            Text("Última automática: ${V5AutoTime(lastAt)}", color = V5Muted, fontSize = 11.sp)
            Spacer(Modifier.width(30.dp))
            Text("Próxima execução: ${V5NextAuto(lastAt, interval)}", color = V5Muted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(9.dp)); HorizontalDivider(color = V5Border); Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Frequência", color = V5Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            listOf(15, 30, 45, 60, 120).forEach { m ->
                V5ChoiceButton(V5IntervalLabel(m), interval == m) { onInterval(m) }
            }
        }
    }
}

@Composable
private fun V5SourcesScreen(c: DesktopControllerV5, tick: Int) {
    var tab by remember { mutableStateOf(V5SourceTab.NEWS) }
    var query by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("Todas") }
    var state by remember { mutableStateOf("Todos") }

    val newsBase = if (tab == V5SourceTab.SPECIAL) SourceCatalog.specialized else SourceCatalog.all
    val newsList = remember(tick, region, state, query, tab) {
        newsBase.filter {
            (region == "Todas" || it.region == region) &&
                (state == "Todos" || it.state == state) &&
                (query.isBlank() || "${it.name} ${it.stateName} ${it.group} ${it.aliases.joinToString(" ")}".contains(query, true))
        }
    }

    val videoList = remember(tick, region, state, query) {
        DesktopVideoSources.all.filter {
            (region == "Todas" || it.region == region) &&
                (state == "Todos" || it.state == state) &&
                (query.isBlank() || "${it.name} ${it.group} ${it.aliases.joinToString(" ")}".contains(query, true))
        }
    }
    val visible = when (tab) {
        V5SourceTab.VIDEOS -> videoList.size
        V5SourceTab.NEWS, V5SourceTab.SPECIAL -> newsList.size
    }
    val allNewsMode = tab == V5SourceTab.NEWS && c.newsAllSources

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        V5Card(Modifier.fillMaxWidth(), padding = 10.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                V5TabButton("Notícias", tab == V5SourceTab.NEWS) {
                    tab = V5SourceTab.NEWS
                    region = "Todas"; state = "Todos"
                }
                Spacer(Modifier.width(7.dp))
                V5TabButton("Vídeos", tab == V5SourceTab.VIDEOS) {
                    tab = V5SourceTab.VIDEOS
                    region = "Todas"; state = "Todos"
                }
                Spacer(Modifier.width(7.dp))
                V5TabButton("Mídia especializada", tab == V5SourceTab.SPECIAL) {
                    tab = V5SourceTab.SPECIAL
                    region = "Todas"; state = "Todos"
                }
                Spacer(Modifier.weight(1f))
                V5SearchBox(query, { query = it }, "Pesquisar fonte...", Modifier.width(390.dp).height(42.dp))
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Região", color = V5Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(58.dp))
                SourceCatalog.regions.forEach { r ->
                    V5FilterButton(r, region == r) { region = r; state = "Todos" }
                    Spacer(Modifier.width(4.dp))
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Estado", color = V5Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(58.dp))
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    V5FilterButton("Todos", state == "Todos") { state = "Todos" }
                    Spacer(Modifier.width(4.dp))
                    SourceCatalog.states.filter { region == "Todas" || region == "Nacional" || it.third == region }.forEach { s ->
                        V5FilterButton(s.first, state == s.first) { state = s.first }
                        Spacer(Modifier.width(4.dp))
                    }
                }
            }

            when (tab) {
                V5SourceTab.NEWS -> {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = if (c.newsAllSources) Color(0xFFE5F8EF) else Color(0xFFF6F9FD),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (c.newsAllSources) V5Green.copy(alpha = .35f) else V5Border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(9.dp)).background((if (c.newsAllSources) V5Green else V5Muted).copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Public, null, tint = if (c.newsAllSources) V5Green else V5Muted)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("TODOS OS VEÍCULOS — SEM EXCEÇÃO", color = if (c.newsAllSources) V5Green else V5Ink, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                                Text("Ligado: aceita qualquer veículo encontrado, inclusive fora do catálogo padrão.", color = V5Muted, fontSize = 10.sp, lineHeight = 14.sp)
                            }
                            Text(if (c.newsAllSources) "LIGADO" else "DESLIGADO", color = if (c.newsAllSources) V5Green else V5Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp)); Switch(c.newsAllSources, onCheckedChange = { c.newsAllSources = it })
                        }
                    }
                }
                V5SourceTab.VIDEOS -> {
                    Spacer(Modifier.height(8.dp))
                    Surface(color = Color(0xFFF8F5FF), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, V5Purple.copy(alpha = .18f)), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.YouTube, null, tint = V5Red, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Fontes oficiais do YouTube incluem g1 e Domingo Espetacular, além dos canais já existentes.", color = V5Ink, fontSize = 11.sp)
                        }
                    }
                }
                V5SourceTab.SPECIAL -> {
                    Spacer(Modifier.height(8.dp))
                    Surface(color = Color(0xFFFFF8E8), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, V5Gold.copy(alpha = .28f)), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Verified, null, tint = Color(0xFFC88700), modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Mídia especializada: ${SourceCatalog.specialized.size} veículos focados em Defesa, Forças Armadas e assuntos navais.", color = V5Ink, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        V5Card(Modifier.fillMaxWidth(), padding = 9.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storage, null, tint = V5Blue, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(12.dp))
                Text("$visible fonte(s) visível(is)", color = V5Ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                if (allNewsMode) { Spacer(Modifier.width(10.dp)); V5Tag("seletor ignorado", V5Green, 130.dp) }
                Spacer(Modifier.weight(1f))
                Button(onClick = {
                    if (tab == V5SourceTab.VIDEOS) videoList.forEach { c.setVideoSource(it.id, true) }
                    else newsList.forEach { c.setNewsSource(it.id, true) }
                }) { Text("Selecionar visíveis", fontSize = 11.sp) }
                Spacer(Modifier.width(7.dp))
                OutlinedButton(onClick = {
                    if (tab == V5SourceTab.VIDEOS) videoList.forEach { c.setVideoSource(it.id, false) }
                    else newsList.forEach { c.setNewsSource(it.id, false) }
                }) { Text("Limpar visíveis", fontSize = 11.sp) }
                Spacer(Modifier.width(7.dp))
                OutlinedButton(onClick = {
                    when (tab) {
                        V5SourceTab.NEWS -> c.selectAllNewsSources()
                        V5SourceTab.VIDEOS -> c.selectAllVideoSources()
                        V5SourceTab.SPECIAL -> SourceCatalog.specialized.forEach { c.setNewsSource(it.id, true) }
                    }
                }) { Text("Todas", fontSize = 11.sp) }
                Spacer(Modifier.width(7.dp))
                OutlinedButton(onClick = {
                    when (tab) {
                        V5SourceTab.NEWS -> c.clearNewsSources()
                        V5SourceTab.VIDEOS -> c.clearVideoSources()
                        V5SourceTab.SPECIAL -> SourceCatalog.specialized.forEach { c.setNewsSource(it.id, false) }
                    }
                }) { Text("Nenhuma", fontSize = 11.sp) }
            }
        }

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            if (tab == V5SourceTab.VIDEOS) {
                items(videoList, key = { it.id }) { s ->
                    V5SourceRow(
                        s.name,
                        "${s.group} • ${s.region} • ${s.state.ifBlank { "BR" }}",
                        s.id in c.selectedVideoSourceIds,
                        true
                    ) { c.setVideoSource(s.id, it) }
                }
            } else {
                items(newsList, key = { it.id }) { s ->
                    V5SourceRow(
                        s.name,
                        "${s.group} • ${s.region} • ${s.state.ifBlank { "BR" }}",
                        allNewsMode || s.id in c.selectedNewsSourceIds,
                        !allNewsMode
                    ) { c.setNewsSource(s.id, it) }
                }
            }
        }
    }
}

@Composable
private fun V5TabButton(text: String, selected: Boolean, click: () -> Unit) {
    Surface(
        color = if (selected) Color(0xFFEDE7FF) else Color.White,
        shape = RoundedCornerShape(9.dp),
        border = BorderStroke(1.dp, if (selected) V5Purple.copy(alpha = .25f) else V5Border),
        modifier = Modifier.height(36.dp).clickable(onClick = click)
    ) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, color = V5Ink, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@Composable
private fun V5FilterButton(text: String, selected: Boolean, click: () -> Unit) {
    Surface(
        color = if (selected) Color(0xFFDCEBFF) else Color.White,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, V5Border),
        modifier = Modifier.height(34.dp).clickable(onClick = click)
    ) {
        Row(Modifier.padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, color = if (selected) V5Blue else V5Muted, fontSize = 10.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

@Composable
private fun V5SourceRow(name: String, detail: String, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    V5Card(Modifier.fillMaxWidth(), padding = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = change, enabled = enabled, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(7.dp))
            Box(Modifier.width(60.dp).height(34.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0B4C8C)), contentAlignment = Alignment.Center) {
                Text(name.filter { it.isLetterOrDigit() }.take(3).uppercase(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = V5Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp)); Text(detail, color = V5Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!enabled) V5Tag("modo todos", V5Green, 90.dp)
            Spacer(Modifier.width(6.dp)); Icon(Icons.Default.ChevronRight, null, tint = V5Blue)
        }
    }
}

@Composable
private fun V5DemandsScreen(c: DesktopControllerV5, tick: Int) {
    var vehicle by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    @Suppress("UNUSED_VARIABLE") val redraw = tick

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        V5Card(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                V5LabeledField("Veículo", vehicle, { vehicle = it }, "Selecione ou digite o veículo...", Modifier.weight(.9f))
                V5LabeledField("Assunto", subject, { subject = it }, "Digite o assunto da demanda...", Modifier.weight(1.2f))
                Button(
                    onClick = { c.addDemand(vehicle, subject); vehicle = ""; subject = "" },
                    enabled = vehicle.isNotBlank() && subject.isNotBlank(),
                    modifier = Modifier.height(44.dp)
                ) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(5.dp)); Text("Adicionar", fontSize = 11.sp) }
                OutlinedButton(onClick = { c.searchAllDemands() }, enabled = !c.newsBusy, modifier = Modifier.height(44.dp)) {
                    Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(5.dp)); Text("Buscar todas", fontSize = 11.sp)
                }
            }
        }

        Surface(color = Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, V5Border), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(17.dp).clip(CircleShape).background(if (c.newsBusy) V5Orange else V5Green))
                Spacer(Modifier.width(15.dp))
                Column {
                    Text(if (c.newsBusy) "Status: Buscando" else "Status: Pronto", color = if (c.newsBusy) V5Orange else V5Green, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(if (c.newsBusy) c.status else "Sistema disponível para consultar e gerenciar demandas.", color = V5Muted, fontSize = 11.sp)
                }
            }
        }

        if (c.demands.isEmpty()) {
            V5EmptyState("Nenhuma demanda carregada no momento", "Adicione uma nova demanda ou utilize os filtros para buscar demandas cadastradas.", Icons.Default.NotificationsActive, V5Orange, Modifier.weight(1f))
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(c.demands, key = { it.id }) { d ->
                    V5Card(Modifier.fillMaxWidth(), padding = 13.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(V5Orange.copy(alpha = .10f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Assignment, null, tint = V5Orange)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${d.vehicle} • ${d.subject}", color = V5Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("Última busca: ${V5AutoTime(d.lastCheckedAt)} • encontrados ${d.lastFoundCount} • novos ${d.lastNewCount}", color = V5Muted, fontSize = 11.sp)
                                if (d.lastError.isNotBlank()) Text(d.lastError, color = V5Red, fontSize = 11.sp)
                            }
                            OutlinedButton(onClick = { c.searchDemand(d) }) { Text("Buscar", fontSize = 11.sp) }
                            Spacer(Modifier.width(5.dp)); IconButton(onClick = { c.removeDemand(d.id) }) { Icon(Icons.Default.DeleteOutline, null, tint = V5Red) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun V5HistoryScreen(c: DesktopControllerV5, tick: Int) {
    var tab by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val redraw = tick

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        V5Card(Modifier.fillMaxWidth(), padding = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                V5ChoiceButton("Notícias", tab == 0) { tab = 0 }
                Spacer(Modifier.width(8.dp))
                V5ChoiceButton("Vídeos", tab == 1) { tab = 1 }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = { if (tab == 0) c.clearNewsHistory() else c.clearVideoHistory() }) {
                    Icon(Icons.Default.DeleteOutline, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("Limpar histórico", fontSize = 10.sp)
                }
            }
        }

        if (tab == 0) {
            val all = c.newsDb.listNews(2000)
            if (all.isEmpty()) V5EmptyState("Histórico de notícias vazio", "As matérias encontradas aparecerão aqui com o termo responsável pelo encontro.", Icons.Default.History, V5Blue, Modifier.weight(1f))
            else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(all, key = { it.link }) { V5NewsCard(it, false, c, showMatched = true) }
            }
        } else {
            val all = c.videoDb.listAll(2000)
            if (all.isEmpty()) V5EmptyState("Histórico de vídeos vazio", "Os vídeos encontrados aparecerão aqui com seus termos e demandas correspondentes.", Icons.Default.History, V5Purple, Modifier.weight(1f))
            else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(all, key = { it.link }) { V5VideoCard(it, false, showMatched = true) }
            }
        }
    }
}

@Composable
private fun V5SearchBox(value: String, change: (String) -> Unit, hint: String, modifier: Modifier) {
    Surface(modifier.height(50.dp), color = Color.White, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, V5Border)) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, null, tint = Color(0xFF43658C), modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = value,
                onValueChange = change,
                singleLine = true,
                modifier = Modifier.weight(1f),
                textStyle = LocalTextStyle.current.copy(color = V5Ink, fontSize = 12.sp),
                decorationBox = { inner ->
                    if (value.isBlank()) Text(hint, color = V5Muted, fontSize = 12.sp)
                    inner()
                }
            )
        }
    }
}

@Composable
private fun V5LabeledField(
    label: String,
    value: String,
    change: (String) -> Unit,
    hint: String,
    modifier: Modifier,
    password: Boolean = false
) {
    Column(modifier) {
        Text(label, color = V5Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Surface(color = Color.White, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, V5Border), modifier = Modifier.fillMaxWidth().height(44.dp)) {
            Row(Modifier.padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = value,
                    onValueChange = change,
                    singleLine = true,
                    visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                    modifier = Modifier.weight(1f),
                    textStyle = LocalTextStyle.current.copy(color = V5Ink, fontSize = 12.sp),
                    decorationBox = { inner ->
                        if (value.isBlank()) Text(hint, color = Color(0xFF91A5C1), fontSize = 12.sp)
                        inner()
                    }
                )
            }
        }
    }
}

@Composable
private fun V5PeriodButton(text: String, selected: Boolean = false, click: () -> Unit) {
    OutlinedButton(
        onClick = click,
        modifier = Modifier.height(38.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected) V5SoftBlue else Color.Transparent),
        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 4.dp)
    ) { Text(text, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) }
}

@Composable
private fun V5ChoiceButton(text: String, selected: Boolean, click: () -> Unit) {
    OutlinedButton(
        onClick = click,
        modifier = Modifier.height(38.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected) Color(0xFFE9E0FF) else Color.Transparent),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
    ) { Text(text, fontSize = 12.sp, color = V5Ink, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
}

@Composable
private fun V5TimeChip(text: String, remove: () -> Unit) {
    Surface(color = Color(0xFFEDE4FF), shape = RoundedCornerShape(9.dp), border = BorderStroke(1.dp, V5Purple.copy(alpha = .12f))) {
        Row(Modifier.padding(start = 11.dp, end = 5.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, color = V5Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(5.dp))
            Icon(Icons.Default.Close, null, tint = V5Muted, modifier = Modifier.size(15.dp).clickable(onClick = remove))
        }
    }
}

@Composable
private fun V5EmptyState(title: String, subtitle: String, icon: ImageVector, color: Color, modifier: Modifier) {
    V5Card(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 35.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(86.dp).clip(CircleShape).background(color.copy(alpha = .09f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(47.dp))
            }
            Spacer(Modifier.height(15.dp))
            Text(title, color = V5Ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(5.dp))
            Text(subtitle, color = V5Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun V5Card(modifier: Modifier = Modifier, padding: androidx.compose.ui.unit.Dp = 15.dp, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.shadow(4.dp, RoundedCornerShape(13.dp)),
        color = Color.White,
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, V5Border)
    ) {
        Column(Modifier.fillMaxWidth().padding(padding), content = content)
    }
}

@Composable
private fun V5Footer(c: DesktopControllerV5, tick: Int) {
    @Suppress("UNUSED_VARIABLE") val redraw = tick
    Row(
        Modifier.fillMaxWidth().height(42.dp).background(Color(0xFFF7FAFE)).padding(horizontal = 25.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Monitor de Notícias v4.0.2", color = V5Muted, fontSize = 10.sp)
        Spacer(Modifier.width(20.dp)); Text("Inteligência de mídia para melhores decisões", color = V5Muted, fontSize = 10.sp)
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (c.newsBusy || c.videoBusy) V5Orange else V5Green))
        Spacer(Modifier.width(7.dp))
        Text(if (c.newsBusy || c.videoBusy) "Busca em andamento" else "Sistema operacional", color = V5Muted, fontSize = 10.sp)
    }
}

private fun V5Open(url: String) {
    runCatching { if (AwtDesktop.isDesktopSupported()) AwtDesktop.getDesktop().browse(URI(url)) }
}

private fun V5Copy(text: String) {
    runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
}

private fun V5OpenWhatsApp(title: String, url: String) {
    val msg = URLEncoder.encode("$title\n$url", Charsets.UTF_8.name())
    V5Open("https://wa.me/?text=$msg")
}

private fun V5Date(ts: Long): String =
    if (ts <= 0) "--" else SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(ts))

private fun V5Duration(ms: Long): String {
    if (ms <= 0) return "--"
    val sec = ms / 1000
    return "%02d:%02d".format(sec / 60, sec % 60)
}

private fun V5AutoTime(ts: Long): String =
    if (ts <= 0) "—" else SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(Date(ts))

private fun V5NextAuto(last: Long, interval: Int): String =
    if (last <= 0) "na próxima verificação" else SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(Date(last + interval * 60_000L))

private fun V5IntervalLabel(minutes: Int): String = when (minutes) {
    60 -> "1 hora"
    120 -> "2 horas"
    else -> "$minutes min"
}

private fun V5CleanText(value: String): String = value
    .replace("&nbsp;", " ", ignoreCase = true)
    .replace("&#39;", "'", ignoreCase = true)
    .replace("&quot;", "\"", ignoreCase = true)
    .replace(Regex("\\s+"), " ")
    .trim()

private suspend fun V5FetchWeather(): V5Weather? = withContext(Dispatchers.IO) {
    runCatching {
        val raw = URL("https://api.open-meteo.com/v1/forecast?latitude=-15.7939&longitude=-47.8828&current=temperature_2m,weather_code&timezone=America%2FSao_Paulo").readText()
        val current = JSONObject(raw).getJSONObject("current")
        val temp = current.getDouble("temperature_2m").roundToInt()
        val code = current.optInt("weather_code", 0)
        V5Weather(
            temp,
            when (code) {
                0 -> "Tempo limpo"
                1, 2 -> "Parcialmente nublado"
                3 -> "Nublado"
                in 51..67 -> "Chuva"
                in 80..82 -> "Pancadas de chuva"
                in 95..99 -> "Trovoadas"
                else -> "Condição atual"
            }
        )
    }.getOrNull()
}



