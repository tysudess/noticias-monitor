package br.com.monitordenoticias.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val V28Bg = Color(0xFF07101D)
private val V28Surface = Color(0xFF0E1A2A)
private val V28Surface2 = Color(0xFF132238)
private val V28Selected = Color(0xFF17365F)
private val V28Accent = Color(0xFF5EA2FF)
private val V28Mint = Color(0xFF39D6A2)
private val V28Amber = Color(0xFFFFB45E)
private val V28Purple = Color(0xFFA57BFF)
private val V28Red = Color(0xFFFF7777)
private val V28Text = Color(0xFFF5F8FC)
private val V28Text2 = Color(0xFFAEBBD0)
private val V28Divider = Color(0xFF21334A)

private val V28Colors = darkColorScheme(
    primary = V28Accent,
    secondary = V28Mint,
    background = V28Bg,
    surface = V28Surface,
    surfaceVariant = V28Surface2,
    onPrimary = Color(0xFF061425),
    onBackground = V28Text,
    onSurface = V28Text,
    onSurfaceVariant = V28Text2
)

class MainActivityV28 : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch("android.permission.POST_NOTIFICATIONS")
        }
        val prefs = getSharedPreferences(BackgroundMonitor.PREFS, MODE_PRIVATE)
        BackgroundMonitor.scheduleAll(this, prefs.getInt("interval_minutes", 30).coerceAtLeast(15))
        VideoBackgroundMonitor.scheduleAll(this)

        setContent {
            MaterialTheme(colorScheme = V28Colors) {
                V28App()
            }
        }
    }
}

private enum class V28Section { HOME, VIDEOS, SOURCES, DEMANDS, PERIOD, HISTORY, TERMS, SETTINGS }
private data class V28NavItem(val section: V28Section, val label: String, val icon: ImageVector)

@Composable
private fun V28App(
    newsVm: MonitorViewModel = viewModel(),
    videoVm: VideoViewModel = viewModel()
) {
    val news by newsVm.state.collectAsState()
    val videos by videoVm.state.collectAsState()
    var section by remember { mutableStateOf(V28Section.HOME) }
    var showMore by remember { mutableStateOf(false) }
    var sourceTab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = V28Bg,
        topBar = { V28TopBar(section) },
        bottomBar = { V28BottomBar(section, onSection = { section = it }, onMore = { showMore = true }) }
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .background(V28Bg)
        ) {
            val status = if (section == V28Section.VIDEOS) videos.status else news.status
            if (status.isNotBlank() && status != "Pronto") V28StatusStrip(status)
            if (news.searchProgress.active && section != V28Section.HOME && section != V28Section.VIDEOS) V30CompactProgress(news.searchProgress)
            when (section) {
                V28Section.HOME -> V30Home(news, newsVm, videos) { section = V28Section.VIDEOS }
                V28Section.VIDEOS -> V30Videos(videos, videoVm) { sourceTab = 1; section = V28Section.SOURCES }
                V28Section.SOURCES -> V30SourcesHub(news, newsVm, videos, videoVm, sourceTab) { sourceTab = it }
                V28Section.DEMANDS -> V28Demands(news, newsVm)
                V28Section.PERIOD -> V28Period(news, newsVm)
                V28Section.HISTORY -> V28History(news, newsVm)
                V28Section.TERMS -> V28Terms(news, newsVm, videos, videoVm)
                V28Section.SETTINGS -> V28Settings(news, newsVm, videos)
            }
        }
    }

    if (showMore) {
        ModalBottomSheet(onDismissRequest = { showMore = false }, containerColor = V28Surface2) {
            Text("Mais opções", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            V28MoreItem(Icons.Outlined.DateRange, "Período", "Pesquisa por data e hora") { section = V28Section.PERIOD; showMore = false }
            V28MoreItem(Icons.Outlined.History, "Histórico", "Matérias armazenadas") { section = V28Section.HISTORY; showMore = false }
            V28MoreItem(Icons.Outlined.ManageSearch, "Termos", "Listas separadas de Notícias e Vídeos") { section = V28Section.TERMS; showMore = false }
            V28MoreItem(Icons.Outlined.Settings, "Configurações", "Automação, bateria e relatórios") { section = V28Section.SETTINGS; showMore = false }
            Spacer(Modifier.navigationBarsPadding().height(12.dp))
        }
    }
}

@Composable
private fun V28TopBar(section: V28Section) {
    val title = when (section) {
        V28Section.HOME -> "Monitor de Notícias"
        V28Section.VIDEOS -> "Monitor de Vídeos"
        V28Section.SOURCES -> "Fontes"
        V28Section.DEMANDS -> "Demandas"
        V28Section.PERIOD -> "Período"
        V28Section.HISTORY -> "Histórico"
        V28Section.TERMS -> "Termos"
        V28Section.SETTINGS -> "Configurações"
    }
    val subtitle = when (section) {
        V28Section.HOME -> "Inteligência de mídia em tempo real"
        V28Section.VIDEOS -> "TV, portais, YouTube e conteúdo audiovisual"
        V28Section.SOURCES -> "Notícias e fontes de vídeo"
        V28Section.DEMANDS -> "Alertas por veículo e assunto"
        V28Section.PERIOD -> "Defina o intervalo da pesquisa"
        V28Section.HISTORY -> "Arquivo das matérias capturadas"
        V28Section.TERMS -> "Termos separados para Notícias e Vídeos"
        V28Section.SETTINGS -> "Saúde, automação e preferências"
    }
    Surface(color = V28Bg, modifier = Modifier.statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(V28Accent.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                Icon(if (section == V28Section.VIDEOS) Icons.Outlined.PlayCircle else Icons.Outlined.Radar, null, tint = V28Accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.ExtraBold)
                Text(subtitle, color = V28Text2, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Surface(color = V28Accent.copy(alpha = .10f), shape = RoundedCornerShape(9.dp)) {
                Text(BuildConfig.VERSION_NAME, color = V28Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
            }
        }
    }
}

@Composable
private fun V28BottomBar(section: V28Section, onSection: (V28Section) -> Unit, onMore: () -> Unit) {
    val nav = listOf(
        V28NavItem(V28Section.HOME, "Início", Icons.Outlined.Home),
        V28NavItem(V28Section.VIDEOS, "Vídeos", Icons.Outlined.SmartDisplay),
        V28NavItem(V28Section.SOURCES, "Fontes", Icons.Outlined.Layers),
        V28NavItem(V28Section.DEMANDS, "Demandas", Icons.Outlined.NotificationsNone)
    )
    val moreSelected = section in setOf(V28Section.PERIOD, V28Section.HISTORY, V28Section.TERMS, V28Section.SETTINGS)
    Surface(color = Color(0xFF0A1422), tonalElevation = 8.dp, border = BorderStroke(1.dp, V28Divider.copy(alpha = .7f)), modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
            nav.forEach { item ->
                V28NavButton(item.label, item.icon, section == item.section, Modifier.weight(1f)) { onSection(item.section) }
            }
            V28NavButton("Mais", Icons.Outlined.MoreHoriz, moreSelected, Modifier.weight(1f), onMore)
        }
    }
}

@Composable
private fun V28NavButton(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.fillMaxHeight().clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(width = 34.dp, height = 26.dp).clip(RoundedCornerShape(10.dp)).background(if (selected) V28Accent.copy(alpha = .14f) else Color.Transparent), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = if (selected) V28Accent else V28Text2, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(label, color = if (selected) V28Accent else V28Text2, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun V28StatusStrip(text: String) {
    val warning = text.startsWith("⚠")
    Surface(color = if (warning) V28Amber.copy(alpha = .08f) else V28Accent.copy(alpha = .06f), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (warning) Icons.Outlined.WarningAmber else Icons.Outlined.Info, null, tint = if (warning) V28Amber else V28Accent, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(7.dp))
            Text(text.removePrefix("⚠ "), color = V28Text2, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun V28Home(s: AppState, vm: MonitorViewModel, openVideos: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(BackgroundMonitor.PREFS, 0)
    val lastNews = prefs.getLong(AutoRunLog.KEY_NEWS_COMPLETED_AT, 0L)
    val lastVideo = prefs.getLong(VideoAutoRunLog.KEY_COMPLETED_AT, 0L)
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Surface(color = V28Mint.copy(alpha = .08f), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, V28Mint.copy(alpha = .22f)), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(V28Mint.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Radar, null, tint = V28Mint, modifier = Modifier.size(25.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(V28Mint)); Spacer(Modifier.width(7.dp))
                            Text("Monitoramento ativo", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(if (lastNews > 0) "Notícias: ${v28DateTime(lastNews)}" else "Notícias: aguardando execução", color = V28Text2, fontSize = 11.5.sp)
                        Text(if (lastVideo > 0) "Vídeos: ${v28DateTime(lastVideo)}" else "Vídeos: aguardando execução", color = V28Text2, fontSize = 11.5.sp)
                    }
                    FilledIconButton(onClick = vm::search, enabled = !s.busy, modifier = Modifier.size(42.dp)) {
                        Icon(if (s.busy) Icons.Outlined.HourglassTop else Icons.Outlined.Refresh, "Buscar agora")
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                V28Metric("Notícias 24h", s.news.size.toString(), Icons.Outlined.Article, V28Accent, Modifier.weight(1f))
                V28Metric("Demandas", s.news.count { it.demand }.toString(), Icons.Outlined.NotificationsActive, V28Amber, Modifier.weight(1f))
            }
        }
        item {
            Surface(color = V28Purple.copy(alpha = .08f), shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, V28Purple.copy(alpha = .22f)), modifier = Modifier.fillMaxWidth().clickable(onClick = openVideos)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.SmartDisplay, null, tint = V28Purple, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Monitor de Vídeos", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Globoplay, R7/Record, CNN, SBT, Band e regionais", color = V28Text2, fontSize = 11.5.sp)
                    }
                    Icon(Icons.Outlined.ChevronRight, null, tint = V28Purple)
                }
            }
        }
        item { Text("Últimas notícias", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold) }
        if (s.news.isEmpty()) item { V28Empty("Nenhuma notícia no escopo atual", "Faça uma busca ou ajuste suas fontes.") }
        else items(s.news.take(30), key = { it.link }) { V28NewsCard(it) }
    }
}

@Composable
private fun V28Videos(s: VideoState, vm: VideoViewModel) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(BackgroundMonitor.PREFS, 0)
    val autoCompleted = prefs.getLong(VideoAutoRunLog.KEY_COMPLETED_AT, 0L)
    val autoNew = prefs.getInt(VideoAutoRunLog.KEY_NEW, 0)
    val autoRelevant = prefs.getInt(VideoAutoRunLog.KEY_NEW_RELEVANT, 0)
    var showSources by remember { mutableStateOf(false) }
    var showPeriod by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var sourceGroup by remember { mutableIntStateOf(0) }
    val periodFrom = v28ParseDateTime(s.periodStartDate, s.periodStartTime)
    val periodTo = v28ParseDateTime(s.periodEndDate, s.periodEndTime)
    val periodValid = periodFrom != null && periodTo != null && periodFrom < periodTo
    val shown = when (s.filter) {
        VideoFilter.ALL -> s.items
        VideoFilter.RELEVANT -> s.items.filter { it.relevant }
        VideoFilter.DEMANDS -> s.items.filter { it.demand }
    }
    val sourceList = if (sourceGroup == 0) VideoSourceCatalog.national else VideoSourceCatalog.regional

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Surface(color = V28Purple.copy(alpha = .08f), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, V28Purple.copy(alpha = .22f)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(V28Purple.copy(alpha = .13f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.SmartDisplay, null, tint = V28Purple, modifier = Modifier.size(25.dp))
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Varredura audiovisual", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(if (autoCompleted > 0) "Última automática: ${v28DateTime(autoCompleted)}" else "Aguardando primeira busca automática", color = V28Text2, fontSize = 11.5.sp)
                            if (autoCompleted > 0) Text("$autoNew novo(s) • $autoRelevant relevante(s)", color = V28Purple, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        FilledIconButton(onClick = vm::searchNow, enabled = !s.busy, modifier = Modifier.size(44.dp)) {
                            Icon(if (s.busy) Icons.Outlined.HourglassTop else Icons.Outlined.Refresh, "Buscar vídeos")
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Busca automática aproximadamente a cada 1 hora, inclusive com o app fechado.", color = V28Text2, fontSize = 11.sp)
                }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                V28Chip("Todos", s.filter == VideoFilter.ALL) { vm.setFilter(VideoFilter.ALL) }
                V28Chip("Relevantes", s.filter == VideoFilter.RELEVANT) { vm.setFilter(VideoFilter.RELEVANT) }
                V28Chip("Demandas", s.filter == VideoFilter.DEMANDS) { vm.setFilter(VideoFilter.DEMANDS) }
                V28Chip("Fontes (${s.selectedSourceIds.size})", showSources) { showSources = !showSources }
                V28Chip("Período", showPeriod) { showPeriod = !showPeriod }
                V28Chip("Limpar", false) { confirmClear = true }
            }
        }
        if (showPeriod) {
            item {
                Surface(color = V28Surface, shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, V28Divider), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(13.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.DateRange, null, tint = V28Purple, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Pesquisar vídeos por período", fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                Text("Mesmo intervalo de data e hora usado na busca de notícias", color = V28Text2, fontSize = 10.5.sp)
                            }
                        }
                        Spacer(Modifier.height(9.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Hoje" to 0, "24 horas" to 1, "7 dias" to 7, "30 dias" to 30).forEach { (label, days) ->
                                V28Chip(label, false) { vm.applyPeriodPreset(days) }
                            }
                        }
                        Spacer(Modifier.height(9.dp))
                        Text("Início", color = V28Accent, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(5.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            OutlinedTextField(s.periodStartDate, vm::setPeriodStartDate, label = { Text("Data") }, singleLine = true, modifier = Modifier.weight(1.45f))
                            OutlinedTextField(s.periodStartTime, vm::setPeriodStartTime, label = { Text("Hora") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(7.dp))
                        Text("Fim", color = V28Mint, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(5.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            OutlinedTextField(s.periodEndDate, vm::setPeriodEndDate, label = { Text("Data") }, singleLine = true, modifier = Modifier.weight(1.45f))
                            OutlinedTextField(s.periodEndTime, vm::setPeriodEndTime, label = { Text("Hora") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(9.dp))
                        Button(onClick = vm::searchSavedPeriod, enabled = periodValid && !s.busy, modifier = Modifier.fillMaxWidth().height(46.dp)) {
                            Icon(Icons.Outlined.Search, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(7.dp))
                            Text(if (s.busy) "Pesquisando..." else "Pesquisar vídeos no período")
                        }
                        if (!periodValid) {
                            Spacer(Modifier.height(6.dp))
                            Text("Revise as datas e horários. Use dd/MM/aaaa e HH:mm.", color = V28Amber, fontSize = 10.5.sp)
                        }
                    }
                }
            }
        }
        if (showSources) {
            item {
                Surface(color = V28Surface, shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, V28Divider), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            V28Segment("Nacionais", Icons.Outlined.Public, sourceGroup == 0, Modifier.weight(1f)) { sourceGroup = 0 }
                            V28Segment("Regionais", Icons.Outlined.Map, sourceGroup == 1, Modifier.weight(1f)) { sourceGroup = 1 }
                        }
                        Spacer(Modifier.height(9.dp))
                        sourceList.forEach { source ->
                            val selected = source.id in s.selectedSourceIds
                            Surface(color = if (selected) V28Selected else V28Surface2, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { vm.setSourceSelected(source.id, !selected) }) {
                                Row(Modifier.padding(horizontal = 11.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null, tint = if (selected) V28Accent else V28Text2, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(9.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(source.name, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text(if (source.state.isBlank()) source.group else "${source.state} • ${source.region}", color = V28Text2, fontSize = 10.5.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Vídeos detectados", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Text("${shown.size}", color = V28Text2, fontSize = 11.5.sp)
            }
        }
        if (shown.isEmpty()) item { V28Empty("Nenhum vídeo encontrado", "Use Buscar agora, pesquise um período ou aguarde a próxima varredura automática.") }
        else items(shown, key = { it.link }) { V28VideoCard(it) }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            icon = { Icon(Icons.Outlined.DeleteOutline, null, tint = V28Red) },
            title = { Text("Limpar vídeos?") },
            text = { Text("Isso apaga o histórico de vídeos detectados. As próximas varreduras poderão encontrá-los novamente.") },
            confirmButton = {
                TextButton(onClick = { vm.clearHistory(); confirmClear = false }) { Text("Limpar", color = V28Red) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
            containerColor = V28Surface2
        )
    }
}

@Composable
private fun V28VideoCard(item: VideoItem) {
    val context = LocalContext.current
    val openVideo = {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.link))) }
        Unit
    }
    Surface(
        color = V28Surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, V28Divider.copy(alpha = .7f)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = openVideo)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(V28Purple.copy(alpha = .13f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.PlayArrow, null, tint = V28Purple, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.sourceName, color = V28Purple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(v28DateTime(item.publishedAt), color = V28Text2, fontSize = 10.5.sp)
                }
                Surface(color = V28Mint.copy(alpha = .10f), shape = RoundedCornerShape(8.dp)) {
                    Row(Modifier.padding(horizontal = 7.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Verified, null, tint = V28Mint, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Link direto", color = V28Mint, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(9.dp))
            Text(item.title, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (item.summary.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(item.summary, color = V28Text2, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (item.relevant) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (item.demand) V28Badge("DEMANDA", V28Amber)
                    if (item.matchedTerm.isNotBlank()) V28Badge(item.matchedTerm, V28Accent)
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = openVideo, modifier = Modifier.fillMaxWidth().height(42.dp)) {
                Icon(Icons.Outlined.SmartDisplay, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text("Abrir vídeo")
                Spacer(Modifier.width(5.dp))
                Icon(Icons.Outlined.OpenInNew, null, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
private fun V28Sources(s: AppState, vm: MonitorViewModel) {
    var mode by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var region by remember { mutableStateOf(SourceCatalog.ALL_REGION) }
    var stateCode by remember { mutableStateOf("") }
    val base = if (mode == 0) SourceCatalog.national else SourceCatalog.byState
    val visible = base.filter { src ->
        val regionOk = mode == 0 || region == SourceCatalog.ALL_REGION || src.region == region
        val stateOk = mode == 0 || stateCode.isBlank() || src.state == stateCode
        val queryOk = query.isBlank() || (listOf(src.name, src.group, src.region, src.stateName, src.state) + src.aliases).any { it.contains(query, ignoreCase = true) }
        regionOk && stateOk && queryOk
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Surface(color = if (s.searchAllSources) V28Mint.copy(alpha = .08f) else V28Surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, if (s.searchAllSources) V28Mint.copy(alpha = .24f) else V28Divider)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.TravelExplore, null, tint = V28Mint)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Buscar em todos os veículos", fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        Text("Inclui portais fora do catálogo", color = V28Text2, fontSize = 10.5.sp)
                    }
                    Switch(checked = s.searchAllSources, onCheckedChange = vm::setSearchAllSources)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                V28Segment("Nacionais", Icons.Outlined.Newspaper, mode == 0, Modifier.weight(1f)) { mode = 0; stateCode = "" }
                V28Segment("Estados", Icons.Outlined.Map, mode == 1, Modifier.weight(1f)) { mode = 1 }
            }
        }
        item { OutlinedTextField(query, { query = it }, label = { Text("Pesquisar veículo ou grupo") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        if (mode == 1) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SourceCatalog.regions.filter { it != SourceCatalog.NATIONAL_REGION }.forEach { item -> V28Chip(item, region == item) { region = item; stateCode = "" } }
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    V28Chip("Todos", stateCode.isBlank()) { stateCode = "" }
                    SourceCatalog.states.filter { region == SourceCatalog.ALL_REGION || it.third == region }.forEach { triple -> V28Chip(triple.first, stateCode == triple.first) { stateCode = triple.first } }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${visible.size} fonte(s) visível(is)", color = V28Text2, fontSize = 11.5.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.setVisibleSources(visible.map { it.id }.toSet(), true) }) { Text("Selecionar") }
                TextButton(onClick = { vm.setVisibleSources(visible.map { it.id }.toSet(), false) }) { Text("Limpar") }
            }
        }
        items(visible, key = { it.id }) { source ->
            val selected = source.id in s.selectedSourceIds
            Surface(color = if (selected) V28Selected else V28Surface, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, if (selected) V28Accent.copy(alpha = .45f) else V28Divider), modifier = Modifier.fillMaxWidth().clickable { vm.setSourceSelected(source.id, !selected) }) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null, tint = if (selected) V28Accent else V28Text2)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(source.name, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        Text(if (source.state.isBlank()) source.group else "${source.state} • ${source.region}", color = V28Text2, fontSize = 10.5.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun V28Demands(s: AppState, vm: MonitorViewModel) {
    var vehicle by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var resultsDemand by remember { mutableStateOf<Demand?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item {
            Surface(color = V28Surface, shape = RoundedCornerShape(17.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Nova demanda", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(vehicle, { vehicle = it }, label = { Text("Veículo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(7.dp))
                    OutlinedTextField(subject, { subject = it }, label = { Text("Assunto") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(9.dp))
                    Button(onClick = { vm.addDemand(vehicle, subject); vehicle = ""; subject = "" }, enabled = vehicle.isNotBlank() && subject.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.AddAlert, null); Spacer(Modifier.width(7.dp)); Text("Adicionar demanda")
                    }
                }
            }
        }
        item {
            Button(onClick = vm::searchAllDemandsNow, enabled = !s.demandSearchBusy && s.demands.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.ManageSearch, null); Spacer(Modifier.width(7.dp)); Text(if (s.demandSearchBusy) "Pesquisando demandas..." else "Buscar demandas agora")
            }
        }
        item { Text("${s.demands.size} demanda(s)", color = V28Text2, fontSize = 11.5.sp) }
        if (s.demands.isEmpty()) item { V28Empty("Nenhuma demanda", "Adicione veículo e assunto para criar um alerta.") }
        else items(s.demands, key = { it.id }) { d ->
            val demandKey = "${d.vehicle} • ${d.subject}"
            val resultItems = s.history.filter { it.matchedDemand == demandKey }.sortedByDescending { it.date }
            Surface(color = V28Surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, V28Divider), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.NotificationsActive, null, tint = V28Amber, modifier = Modifier.size(21.dp)); Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.vehicle, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                            Text(d.subject, color = V28Text2, fontSize = 11.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { vm.searchDemandNow(d.id) }, enabled = s.demandBusyId == null && !s.demandSearchBusy) { Icon(Icons.Outlined.Search, "Buscar", tint = V28Accent) }
                        IconButton(onClick = { vm.removeDemand(d.id) }) { Icon(Icons.Outlined.DeleteOutline, "Excluir", tint = V28Red) }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = V28Divider)
                    Text(if (d.lastCheckedAt > 0) "Última busca: ${v28DateTime(d.lastCheckedAt)}" else "Ainda não pesquisada", color = V28Text2, fontSize = 10.8.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${d.lastFoundCount} resultado(s) • ${d.lastNewCount} novo(s)", color = if (d.lastNewCount > 0) V28Mint else V28Text2, fontSize = 10.8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (d.lastFoundCount > 0) {
                            TextButton(
                                onClick = { resultsDemand = d },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Outlined.OpenInNew, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(if (d.lastFoundCount == 1) "Ver resultado" else "Ver resultados", fontSize = 11.sp)
                            }
                        }
                    }
                    if (d.lastFoundCount > 0 && resultItems.isEmpty()) {
                        Text("Toque na lupa para atualizar e vincular os resultados desta demanda.", color = V28Amber, fontSize = 9.8.sp)
                    }
                    if (d.lastError.isNotBlank()) Text(d.lastError, color = V28Red, fontSize = 10.5.sp)
                }
            }
        }
    }

    resultsDemand?.let { demand ->
        val key = "${demand.vehicle} • ${demand.subject}"
        val matched = s.history.filter { it.matchedDemand == key }.sortedByDescending { it.date }
        ModalBottomSheet(onDismissRequest = { resultsDemand = null }, containerColor = V28Surface2) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(V28Amber.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.NotificationsActive, null, tint = V28Amber)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Resultados da demanda", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        Text("${demand.vehicle} • ${demand.subject}", color = V28Text2, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (matched.isEmpty()) {
                    V28Empty("Resultado ainda não vinculado", "Toque na lupa da demanda e abra novamente esta lista.")
                } else {
                    Text("${matched.size} matéria(s) armazenada(s) • toque para abrir", color = V28Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(matched, key = { it.link }) { news -> V28NewsCard(news) }
                    }
                }
                Spacer(Modifier.navigationBarsPadding().height(14.dp))
            }
        }
    }
}

@Composable
private fun V28Period(s: AppState, vm: MonitorViewModel) {
    val from = v28ParseDateTime(s.periodStartDate, s.periodStartTime)
    val to = v28ParseDateTime(s.periodEndDate, s.periodEndTime)
    val valid = from != null && to != null && from < to
    Box(Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 82.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("Hoje" to 0, "24 horas" to 1, "7 dias" to 7, "30 dias" to 30).forEach { (label, days) -> V28Chip(label, false) { vm.applyPeriodPreset(days) } }
                }
            }
            item {
                Surface(color = V28Surface, shape = RoundedCornerShape(17.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Início", color = V28Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(s.periodStartDate, vm::setPeriodStartDate, label = { Text("Data") }, singleLine = true, modifier = Modifier.weight(1.45f))
                            OutlinedTextField(s.periodStartTime, vm::setPeriodStartTime, label = { Text("Hora") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = V28Divider)
                        Text("Fim", color = V28Mint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(s.periodEndDate, vm::setPeriodEndDate, label = { Text("Data") }, singleLine = true, modifier = Modifier.weight(1.45f))
                            OutlinedTextField(s.periodEndTime, vm::setPeriodEndTime, label = { Text("Hora") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            item {
                Surface(color = if (valid) V28Mint.copy(alpha = .08f) else V28Amber.copy(alpha = .08f), shape = RoundedCornerShape(13.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (valid) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber, null, tint = if (valid) V28Mint else V28Amber)
                        Spacer(Modifier.width(8.dp)); Text(if (valid) "Período válido e salvo automaticamente" else "Revise as datas e horários", color = if (valid) V28Mint else V28Amber, fontSize = 11.5.sp)
                    }
                }
            }
            if (s.news.isNotEmpty()) {
                item { Text("Resultados", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                items(s.news, key = { it.link }) { V28NewsCard(it) }
            }
        }
        Surface(color = V28Bg, tonalElevation = 8.dp, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding()) {
            Button(onClick = vm::searchSavedPeriod, enabled = valid && !s.busy, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(50.dp)) {
                Icon(Icons.Outlined.Search, null); Spacer(Modifier.width(7.dp)); Text(if (s.busy) "Pesquisando..." else "Pesquisar período")
            }
        }
    }
}

@Composable
private fun V28History(s: AppState, vm: MonitorViewModel) {
    val context = LocalContext.current
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { v28ExportCsv(context, s.history) }, enabled = s.history.isNotEmpty(), modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.IosShare, null); Spacer(Modifier.width(6.dp)); Text("Exportar") }
                OutlinedButton(onClick = vm::clearHistory, enabled = s.history.isNotEmpty(), modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.DeleteOutline, null); Spacer(Modifier.width(6.dp)); Text("Limpar") }
            }
        }
        item { Text("${s.history.size} matéria(s)", color = V28Text2, fontSize = 11.5.sp) }
        if (s.history.isEmpty()) item { V28Empty("Histórico vazio", "As matérias encontradas aparecerão aqui.") }
        else items(s.history, key = { it.link }) { V28NewsCard(it) }
    }
}

@Composable
private fun V28Terms(s: AppState, vm: MonitorViewModel, videos: VideoState, videoVm: VideoViewModel) {
    var newsTerm by remember { mutableStateOf("") }
    var videoTerm by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("Termos de Notícias", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Text("Usados pelo Google Notícias e pelas varreduras diretas de Últimas notícias.", color = V28Text2, fontSize = 11.sp)
        }
        item {
            Surface(color = V28Surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(newsTerm, { newsTerm = it }, label = { Text("Novo termo de Notícias") }, singleLine = true, modifier = Modifier.weight(1f))
                    FilledIconButton(onClick = { if (newsTerm.isNotBlank()) { vm.addTerm(newsTerm); newsTerm = "" } }) {
                        Icon(Icons.Outlined.Add, "Adicionar termo de Notícias")
                    }
                }
            }
        }
        if (s.terms.isEmpty()) {
            item { V28Empty("Nenhum termo de Notícias", "Adicione um termo para acompanhar matérias.") }
        } else {
            items(s.terms, key = { "news-term-$it" }) { value ->
                Surface(color = V28Surface, shape = RoundedCornerShape(13.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Article, null, tint = V28Accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(9.dp))
                        Text(value, modifier = Modifier.weight(1f), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { vm.removeTerm(value) }) { Icon(Icons.Outlined.DeleteOutline, "Remover", tint = V28Red) }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = V28Divider)
            Spacer(Modifier.height(6.dp))
            Text("Termos de Vídeos", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = V28Purple)
            Text("Independentes dos termos de Notícias. Demandas continuam valendo para os dois monitores.", color = V28Text2, fontSize = 11.sp)
        }
        item {
            Surface(color = V28Purple.copy(alpha = .08f), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, V28Purple.copy(alpha = .22f)), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(videoTerm, { videoTerm = it }, label = { Text("Novo termo de Vídeos") }, singleLine = true, modifier = Modifier.weight(1f))
                    FilledIconButton(onClick = { if (videoTerm.isNotBlank()) { videoVm.addVideoTerm(videoTerm); videoTerm = "" } }) {
                        Icon(Icons.Outlined.Add, "Adicionar termo de Vídeos")
                    }
                }
            }
        }
        if (videos.videoTerms.isEmpty()) {
            item { V28Empty("Nenhum termo de Vídeos", "Os vídeos ainda podem ser encontrados pelas Demandas ativas.") }
        } else {
            items(videos.videoTerms, key = { "video-term-$it" }) { value ->
                Surface(color = V28Surface, shape = RoundedCornerShape(13.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.SmartDisplay, null, tint = V28Purple, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(9.dp))
                        Text(value, modifier = Modifier.weight(1f), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { videoVm.removeVideoTerm(value) }) { Icon(Icons.Outlined.DeleteOutline, "Remover", tint = V28Red) }
                    }
                }
            }
        }
        item {
            Text("Na primeira abertura da v4.0, os termos antigos são copiados para Vídeos uma única vez. Depois, as listas são totalmente independentes.", color = V28Text2, fontSize = 10.5.sp)
        }
    }
}

@Composable
private fun V28Settings(s: AppState, vm: MonitorViewModel, videos: VideoState) {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }
    val prefs = remember(refreshKey) { context.getSharedPreferences(BackgroundMonitor.PREFS, 0) }
    val newsAttempt = prefs.getLong(AutoRunLog.KEY_NEWS_ATTEMPT_AT, 0L)
    val newsCompleted = prefs.getLong(AutoRunLog.KEY_NEWS_COMPLETED_AT, 0L)
    val newsNewVisible = s.news.count { v401SettingsInRun(it.capturedAt, newsAttempt, newsCompleted) }
    val demandAttempt = prefs.getLong(AutoRunLog.KEY_DEMAND_ATTEMPT_AT, 0L)
    val demandCompleted = prefs.getLong(AutoRunLog.KEY_DEMAND_COMPLETED_AT, 0L)
    val videoAttempt = prefs.getLong(VideoAutoRunLog.KEY_ATTEMPT_AT, 0L)
    val videoCompleted = prefs.getLong(VideoAutoRunLog.KEY_COMPLETED_AT, 0L)
    val videoFound = prefs.getInt(VideoAutoRunLog.KEY_FOUND, 0)
    val videoNew = videos.items.count { v401SettingsInRun(it.capturedAt, videoAttempt, videoCompleted) }
    val videoRelevant = prefs.getInt(VideoAutoRunLog.KEY_NEW_RELEVANT, 0)
    val videoErrors = prefs.getInt(VideoAutoRunLog.KEY_ERRORS, 0)
    val videoError = prefs.getString(VideoAutoRunLog.KEY_ERROR_TEXT, "").orEmpty()
    val powerManager = context.getSystemService(PowerManager::class.java)
    val unrestricted = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    val latestAttempt = maxOf(newsAttempt, demandAttempt, videoAttempt)
    val stale = latestAttempt > 0 && System.currentTimeMillis() - latestAttempt > 2L * 60L * 60L * 1000L

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Surface(color = if (stale) V28Amber.copy(alpha = .09f) else V28Mint.copy(alpha = .08f), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, if (stale) V28Amber.copy(alpha = .25f) else V28Mint.copy(alpha = .22f)), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (stale) Icons.Outlined.WarningAmber else Icons.Outlined.VerifiedUser, null, tint = if (stale) V28Amber else V28Mint, modifier = Modifier.size(26.dp)); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (stale) "Atenção ao monitor" else "Monitor funcionando", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(if (latestAttempt > 0) "Último disparo: ${v28DateTime(latestAttempt)}" else "Aguardando primeira execução automática", color = V28Text2, fontSize = 11.5.sp)
                    }
                    IconButton(onClick = { refreshKey++ }) { Icon(Icons.Outlined.Refresh, "Atualizar") }
                }
            }
        }
        item { V28ReportCard("Notícias automáticas", Icons.Outlined.Article, V28Accent, newsAttempt, newsCompleted, "${prefs.getInt(AutoRunLog.KEY_NEWS_FOUND, 0)} resultado(s) • $newsNewVisible nova(s)", prefs.getString(AutoRunLog.KEY_NEWS_ERROR_TEXT, "").orEmpty()) }
        item { V28ReportCard("Demandas automáticas", Icons.Outlined.NotificationsActive, V28Mint, demandAttempt, demandCompleted, "${prefs.getInt(AutoRunLog.KEY_DEMAND_CHECKED, 0)} demanda(s) • ${prefs.getInt(AutoRunLog.KEY_DEMAND_NEW, 0)} nova(s)", prefs.getString(AutoRunLog.KEY_DEMAND_ERROR_TEXT, "").orEmpty()) }
        item { V28ReportCard("Vídeos automáticos", Icons.Outlined.SmartDisplay, V28Purple, videoAttempt, videoCompleted, "$videoFound detectado(s) • $videoNew novo(s) • $videoRelevant relevante(s) • $videoErrors falha(s)", videoError) }
        item {
            Surface(color = V28Surface, shape = RoundedCornerShape(17.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Intervalo de notícias", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Vídeos e demandas permanecem em aproximadamente 1 hora", color = V28Text2, fontSize = 11.sp)
                    Spacer(Modifier.height(9.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf(15, 30, 45, 60).forEach { minutes -> V28Chip("$minutes min", s.intervalMinutes == minutes) { vm.setInterval(minutes) } }
                    }
                }
            }
        }
        item {
            Surface(color = V28Surface, shape = RoundedCornerShape(17.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.BatterySaver, null, tint = if (unrestricted) V28Mint else V28Amber); Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Bateria e segundo plano", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(if (unrestricted) "Sem restrição de bateria detectada" else "O Android pode atrasar tarefas em repouso", color = V28Text2, fontSize = 11.5.sp)
                        }
                    }
                    Spacer(Modifier.height(9.dp))
                    OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.SettingsSuggest, null); Spacer(Modifier.width(7.dp)); Text("Abrir ajustes do aplicativo")
                    }
                }
            }
        }
        item {
            Surface(color = V28Accent.copy(alpha = .07f), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Monitor de Notícias ${BuildConfig.VERSION_NAME}", color = V28Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Monitoramento integrado de notícias, demandas e vídeos. ${videos.selectedSourceIds.size} fonte(s) de vídeo ativa(s).", color = V28Text2, fontSize = 11.5.sp, lineHeight = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun V28ReportCard(title: String, icon: ImageVector, color: Color, attemptAt: Long, completedAt: Long, summary: String, error: String) {
    Surface(color = V28Surface, shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, V28Divider), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(color.copy(alpha = .12f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = color, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.width(10.dp)); Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(if (error.isNotBlank()) Icons.Outlined.ErrorOutline else if (completedAt > 0) Icons.Outlined.CheckCircle else Icons.Outlined.Schedule, null, tint = if (error.isNotBlank()) V28Red else if (completedAt > 0) V28Mint else V28Text2)
            }
            Spacer(Modifier.height(8.dp))
            Text(if (attemptAt > 0) "Última tentativa: ${v28DateTime(attemptAt)}" else "Última tentativa: —", color = V28Text2, fontSize = 11.3.sp)
            Text(if (completedAt > 0) "Última conclusão: ${v28DateTime(completedAt)}" else "Última conclusão: —", color = V28Text2, fontSize = 11.3.sp)
            if (completedAt > 0) { Spacer(Modifier.height(4.dp)); Text(summary, color = color, fontSize = 11.3.sp, fontWeight = FontWeight.Bold) }
            if (error.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text(error, color = V28Red, fontSize = 10.8.sp) }
        }
    }
}

@Composable
private fun V28NewsCard(n: News) {
    val context = LocalContext.current
    Surface(color = V28Surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, V28Divider.copy(alpha = .7f)), modifier = Modifier.fillMaxWidth().clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(n.link))) } }) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(n.source, color = V28Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(v28DateTime(n.date), color = V28Text2, fontSize = 10.5.sp)
            }
            Spacer(Modifier.height(7.dp))
            Text(n.title, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            val snippet = v28Clean(n.snippet)
            if (snippet.isNotBlank()) { Spacer(Modifier.height(5.dp)); Text(snippet, color = V28Text2, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            if (n.demand || n.matchedTerm.isNotBlank()) {
                Spacer(Modifier.height(8.dp)); Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (n.demand) V28Badge("DEMANDA", V28Amber)
                    if (n.matchedTerm.isNotBlank()) V28Badge(n.matchedTerm, V28Accent)
                }
            }
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedButton(
                    onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(n.link))) } },
                    modifier = Modifier.weight(1f).height(50.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Outlined.OpenInNew, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Abrir notícia", maxLines = 1, fontSize = 10.8.sp)
                }
                OutlinedButton(
                    onClick = { v28ShareWhatsApp(context, n.title, n.link) },
                    modifier = Modifier.weight(1f).height(50.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = V28Mint)
                ) {
                    Icon(Icons.Outlined.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("WhatsApp", maxLines = 1, fontSize = 10.8.sp)
                }
            }
        }
    }
}

@Composable
private fun V28Metric(label: String, value: String, icon: ImageVector, color: Color, modifier: Modifier) {
    Surface(color = V28Surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, V28Divider), modifier = modifier) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp)); Spacer(Modifier.weight(1f)); Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(7.dp)); Text(label, color = V28Text2, fontSize = 11.5.sp)
        }
    }
}

@Composable
private fun V28Segment(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(color = if (selected) V28Selected else V28Surface, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, if (selected) V28Accent.copy(alpha = .45f) else V28Divider), modifier = modifier.height(42.dp).clickable(onClick = onClick)) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (selected) V28Accent else V28Text2, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(label, color = if (selected) V28Accent else V28Text2, fontSize = 11.5.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@Composable
private fun V28Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(color = if (selected) V28Accent.copy(alpha = .14f) else V28Surface, shape = RoundedCornerShape(11.dp), border = BorderStroke(1.dp, if (selected) V28Accent.copy(alpha = .40f) else V28Divider), modifier = Modifier.height(34.dp).clickable(onClick = onClick)) {
        Box(Modifier.padding(horizontal = 11.dp), contentAlignment = Alignment.Center) { Text(label, color = if (selected) V28Accent else V28Text2, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) }
    }
}

@Composable
private fun V28Badge(label: String, color: Color) {
    Surface(color = color.copy(alpha = .12f), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, color.copy(alpha = .28f))) {
        Text(label, color = color, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun V28Empty(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(V28Surface), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.SearchOff, null, tint = V28Accent, modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.height(10.dp)); Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(3.dp)); Text(subtitle, color = V28Text2, fontSize = 11.5.sp)
    }
}

@Composable
private fun V28MoreItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Bold) },
        supportingContent = { Text(subtitle, color = V28Text2) },
        leadingContent = { Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(V28Accent.copy(alpha = .12f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = V28Accent) } },
        trailingContent = { Icon(Icons.Outlined.ChevronRight, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

private fun v28ShareWhatsApp(context: android.content.Context, title: String, link: String) {
    val message = listOf(title.trim(), link.trim()).filter { it.isNotBlank() }.joinToString("\n")
    if (message.isBlank()) return

    fun shareIntent(packageName: String? = null) = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
        packageName?.let(::setPackage)
    }

    if (runCatching { context.startActivity(shareIntent("com.whatsapp")) }.isSuccess) return
    if (runCatching { context.startActivity(shareIntent("com.whatsapp.w4b")) }.isSuccess) return
    runCatching { context.startActivity(Intent.createChooser(shareIntent(), "Compartilhar link")) }
}


private fun v401SettingsInRun(capturedAt: Long, startedAt: Long, completedAt: Long): Boolean {
    if (capturedAt <= 0L || startedAt <= 0L || completedAt < startedAt) return false
    return capturedAt in startedAt..(completedAt + 5_000L)
}

private fun v28DateTime(ms: Long): String = if (ms <= 0) "—" else SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(Date(ms))
private fun v28ParseDateTime(date: String, time: String): Long? = runCatching { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).apply { isLenient = false }.parse("$date $time")?.time }.getOrNull()
private fun v28Clean(value: String): String = value.replace("&nbsp;", " ", ignoreCase = true).replace("&amp;", "&", ignoreCase = true).replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()

private fun v28ExportCsv(context: android.content.Context, items: List<News>) {
    if (items.isEmpty()) return
    val header = "data;fonte;titulo;link\n"
    val body = items.joinToString("\n") { item ->
        val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(item.date))
        listOf(date, item.source, item.title, item.link).joinToString(";") { field -> "\"${field.replace("\"", "\"\"")}\"" }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_SUBJECT, "Histórico - Monitor de Notícias")
        putExtra(Intent.EXTRA_TEXT, header + body)
    }
    context.startActivity(Intent.createChooser(intent, "Exportar histórico"))
}
