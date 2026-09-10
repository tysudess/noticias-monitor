package br.com.monitordenoticias.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val V30Bg = Color(0xFF07101D)
private val V30Surface = Color(0xFF0E1A2A)
private val V30Surface2 = Color(0xFF132238)
private val V30Selected = Color(0xFF17365F)
private val V30Accent = Color(0xFF5EA2FF)
private val V30Mint = Color(0xFF39D6A2)
private val V30Amber = Color(0xFFFFB45E)
private val V30Purple = Color(0xFFA57BFF)
private val V30Red = Color(0xFFFF7777)
private val V30Text2 = Color(0xFFAEBBD0)
private val V30Divider = Color(0xFF21334A)

@Composable
fun V30Home(
    news: AppState,
    newsVm: MonitorViewModel,
    videos: VideoState,
    openVideos: () -> Unit
) {
    val now = System.currentTimeMillis()
    val news24h = news.news.count { it.date >= now - 24L * 60L * 60L * 1000L }
    val videoDemands = videos.items.count { it.demand }
    val demandHits = news.demands.count { it.lastFoundCount > 0 }
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(BackgroundMonitor.PREFS, Context.MODE_PRIVATE)
    val autoNewsStart = prefs.getLong(AutoRunLog.KEY_NEWS_ATTEMPT_AT, 0L)
    val autoNewsEnd = prefs.getLong(AutoRunLog.KEY_NEWS_COMPLETED_AT, 0L)
    val manualNewsStart = news.searchProgress.startedAt
    val manualNewsEnd = news.searchProgress.finishedAt.takeIf { it > 0L } ?: if (news.searchProgress.active) now else 0L
    val useManualNewsWindow = manualNewsStart > autoNewsStart
    val newNewsStart = if (useManualNewsWindow) manualNewsStart else autoNewsStart
    val newNewsEnd = if (useManualNewsWindow) manualNewsEnd else autoNewsEnd
    val visibleNewNews = news.news.count { v401InRun(it.capturedAt, newNewsStart, newNewsEnd) }
    val orderedNews = news.news.sortedWith(
        compareByDescending<News> { v401InRun(it.capturedAt, newNewsStart, newNewsEnd) }
            .thenByDescending { it.date }
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(
                color = V30Mint.copy(alpha = .08f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, V30Mint.copy(alpha = .22f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(V30Mint.copy(alpha = .12f)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Outlined.Radar, null, tint = V30Mint, modifier = Modifier.size(25.dp)) }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (news.searchProgress.active) "Busca de notícias em andamento" else "Monitoramento ativo", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (news.searchProgress.active) "Resultados aparecem assim que cada consulta termina" else "Notícias, demandas e vídeos em segundo plano",
                                color = V30Text2,
                                fontSize = 11.5.sp
                            )
                        }
                        FilledIconButton(onClick = newsVm::search, enabled = !news.busy, modifier = Modifier.size(44.dp)) {
                            Icon(if (news.busy) Icons.Outlined.HourglassTop else Icons.Outlined.Refresh, "Buscar notícias")
                        }
                    }
                    if (news.searchProgress.active) {
                        Spacer(Modifier.height(10.dp))
                        V30ProgressBody(news.searchProgress, V30Mint)
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                V30Metric("Notícias 24h", news24h.toString(), Icons.Outlined.Article, V30Accent, Modifier.weight(1f))
                V30Metric("Demandas encontradas", demandHits.toString(), Icons.Outlined.NotificationsActive, if (demandHits > 0) V30Amber else V30Text2, Modifier.weight(1f))
            }
        }

        item {
            Surface(
                color = V30Purple.copy(alpha = .08f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, V30Purple.copy(alpha = .25f)),
                modifier = Modifier.fillMaxWidth().clickable(onClick = openVideos)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(V30Purple.copy(alpha = .13f)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Outlined.SmartDisplay, null, tint = V30Purple, modifier = Modifier.size(25.dp)) }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Monitor de Vídeos", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (videos.searchProgress.active) "Buscando agora • resultados em tempo real" else "${videos.totalStored} vídeo(s) armazenado(s) • ${videos.capturedToday} novo(s) hoje",
                                color = V30Text2,
                                fontSize = 11.5.sp
                            )
                        }
                        Icon(Icons.Outlined.ChevronRight, null, tint = V30Purple)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        V30SmallStat("Encontrados", videos.totalStored.toString(), V30Purple, Modifier.weight(1f))
                        V30SmallStat("Hoje", videos.capturedToday.toString(), V30Mint, Modifier.weight(1f))
                        V30SmallStat("Demandas", videoDemands.toString(), V30Amber, Modifier.weight(1f))
                        V30SmallStat("Fontes", videos.selectedSourceIds.size.toString(), V30Accent, Modifier.weight(1f))
                    }
                    if (videos.searchProgress.active) {
                        Spacer(Modifier.height(10.dp))
                        V30ProgressBody(videos.searchProgress, V30Purple)
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Últimas notícias", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                if (visibleNewNews > 0) {
                    V30Badge(if (visibleNewNews == 1) "1 NOVA" else "$visibleNewNews NOVAS", V30Mint)
                    Spacer(Modifier.width(6.dp))
                }
                if (news.busy) Text("atualizando...", color = V30Mint, fontSize = 10.5.sp)
            }
        }
        if (news.news.isEmpty()) {
            item { V30Empty("Nenhuma notícia no escopo atual", "Faça uma busca ou ajuste suas fontes.") }
        } else {
            items(orderedNews.take(40), key = { it.link }) { V30NewsCard(it, newNewsStart, newNewsEnd) }
        }
    }
}

@Composable
fun V30Videos(s: VideoState, vm: VideoViewModel, openSources: () -> Unit) {
    var showPeriod by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var showUnstable by remember { mutableStateOf(false) }
    val from = v30ParseDateTime(s.periodStartDate, s.periodStartTime)
    val to = v30ParseDateTime(s.periodEndDate, s.periodEndTime)
    val validPeriod = from != null && to != null && from < to
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(BackgroundMonitor.PREFS, Context.MODE_PRIVATE)
    val autoVideoStart = prefs.getLong(VideoAutoRunLog.KEY_ATTEMPT_AT, 0L)
    val autoVideoEnd = prefs.getLong(VideoAutoRunLog.KEY_COMPLETED_AT, 0L)
    val manualVideoStart = s.searchProgress.startedAt
    val manualVideoEnd = s.searchProgress.finishedAt.takeIf { it > 0L } ?: if (s.searchProgress.active) System.currentTimeMillis() else 0L
    val useManualVideoWindow = manualVideoStart > autoVideoStart
    val newVideoStart = if (useManualVideoWindow) manualVideoStart else autoVideoStart
    val newVideoEnd = if (useManualVideoWindow) manualVideoEnd else autoVideoEnd
    val shown = when (s.filter) {
        VideoFilter.ALL -> s.items
        VideoFilter.RELEVANT -> s.items.filter { it.relevant }
        VideoFilter.DEMANDS -> s.items.filter { it.demand }
    }
    val visibleNewVideos = shown.count { v401InRun(it.capturedAt, newVideoStart, newVideoEnd) }
    val orderedShown = shown.sortedWith(
        compareByDescending<VideoItem> { v401InRun(it.capturedAt, newVideoStart, newVideoEnd) }
            .thenByDescending { it.publishedAt }
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(
                color = V30Purple.copy(alpha = .08f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, V30Purple.copy(alpha = .25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(V30Purple.copy(alpha = .13f)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Outlined.SmartDisplay, null, tint = V30Purple, modifier = Modifier.size(25.dp)) }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (s.busy) "Varredura em andamento" else "Monitor de Vídeos", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("${s.totalStored} armazenado(s) • ${s.capturedToday} novo(s) hoje • ${s.selectedSourceIds.size} fonte(s) ativa(s)", color = V30Text2, fontSize = 11.sp)
                        }
                        FilledIconButton(onClick = vm::searchNow, enabled = !s.busy, modifier = Modifier.size(44.dp)) {
                            Icon(if (s.busy) Icons.Outlined.HourglassTop else Icons.Outlined.Refresh, "Buscar vídeos")
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { confirmClear = true },
                        enabled = !s.busy && s.totalStored > 0,
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        border = BorderStroke(1.dp, V30Red.copy(alpha = .38f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = V30Red)
                    ) {
                        Icon(Icons.Outlined.DeleteSweep, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Limpar vídeos")
                    }
                    if (s.searchProgress.startedAt > 0L) {
                        Spacer(Modifier.height(10.dp))
                        V30ProgressBody(s.searchProgress, V30Purple)
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Text("Os cards entram nesta tela assim que cada vídeo é validado, sem esperar o fim da varredura.", color = V30Text2, fontSize = 10.5.sp)
                    }
                }
            }
        }

        if (!s.busy && s.unstableSources.isNotEmpty()) {
            item {
                OutlinedButton(
                    onClick = { showUnstable = true },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = V30Amber),
                    border = BorderStroke(1.dp, V30Amber.copy(alpha = .38f))
                ) {
                    Icon(Icons.Outlined.ErrorOutline, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Ver fontes instáveis (${s.unstableSources.size})")
                }
            }
        }

        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                V30Chip("Todos", s.filter == VideoFilter.ALL) { vm.setFilter(VideoFilter.ALL) }
                V30Chip("Relevantes", s.filter == VideoFilter.RELEVANT) { vm.setFilter(VideoFilter.RELEVANT) }
                V30Chip("Demandas", s.filter == VideoFilter.DEMANDS) { vm.setFilter(VideoFilter.DEMANDS) }
                V30Chip("Fontes (${s.selectedSourceIds.size})", false, openSources)
                V30Chip("Período", showPeriod) { showPeriod = !showPeriod }
            }
        }

        if (showPeriod) {
            item {
                Surface(color = V30Surface, shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, V30Divider), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(13.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.DateRange, null, tint = V30Purple)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Pesquisar vídeos por período", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("Os resultados também aparecem progressivamente.", color = V30Text2, fontSize = 10.5.sp)
                            }
                        }
                        Spacer(Modifier.height(9.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Hoje" to 0, "24 horas" to 1, "7 dias" to 7, "30 dias" to 30).forEach { (label, days) ->
                                V30Chip(label, false) { vm.applyPeriodPreset(days) }
                            }
                        }
                        Spacer(Modifier.height(9.dp))
                        Text("Início", color = V30Accent, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            OutlinedTextField(s.periodStartDate, vm::setPeriodStartDate, label = { Text("Data") }, singleLine = true, modifier = Modifier.weight(1.4f))
                            OutlinedTextField(s.periodStartTime, vm::setPeriodStartTime, label = { Text("Hora") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(7.dp))
                        Text("Fim", color = V30Mint, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            OutlinedTextField(s.periodEndDate, vm::setPeriodEndDate, label = { Text("Data") }, singleLine = true, modifier = Modifier.weight(1.4f))
                            OutlinedTextField(s.periodEndTime, vm::setPeriodEndTime, label = { Text("Hora") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(9.dp))
                        Button(onClick = vm::searchSavedPeriod, enabled = validPeriod && !s.busy, modifier = Modifier.fillMaxWidth().height(46.dp)) {
                            Icon(Icons.Outlined.Search, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(7.dp))
                            Text(if (s.busy) "Pesquisando..." else "Pesquisar vídeos no período")
                        }
                        if (!validPeriod) {
                            Spacer(Modifier.height(6.dp))
                            Text("Revise as datas e horários. Use dd/MM/aaaa e HH:mm.", color = V30Amber, fontSize = 10.5.sp)
                        }
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Vídeos encontrados", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                if (visibleNewVideos > 0) {
                    V30Badge(if (visibleNewVideos == 1) "1 NOVO" else "$visibleNewVideos NOVOS", V30Mint)
                    Spacer(Modifier.width(6.dp))
                }
                Text("${shown.size}", color = if (s.busy) V30Mint else V30Text2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (shown.isEmpty()) {
            item { V30Empty("Nenhum vídeo encontrado", "Use Buscar agora ou selecione as fontes desejadas na aba Fontes.") }
        } else {
            items(orderedShown, key = { it.link }) { V30VideoCard(it, newVideoStart, newVideoEnd) }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            icon = { Icon(Icons.Outlined.DeleteSweep, null, tint = V30Red) },
            title = { Text("Limpar vídeos?") },
            text = { Text("Isso apaga o histórico de vídeos detectados. Termos, Demandas e fontes selecionadas serão mantidos.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; vm.clearHistory() }) { Text("Limpar", color = V30Red) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
            containerColor = V30Surface2
        )
    }

    if (showUnstable) {
        AlertDialog(
            onDismissRequest = { showUnstable = false },
            icon = { Icon(Icons.Outlined.ErrorOutline, null, tint = V30Amber) },
            title = { Text("Fontes instáveis (${s.unstableSources.size})") },
            text = {
                Column(
                    Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Uma fonte entra aqui quando não conseguiu completar sua rota principal. O número de falhas e a etapa ajudam a identificar URLs fora do ar, bloqueios e timeouts.",
                        color = V30Text2,
                        fontSize = 10.8.sp
                    )
                    s.unstableSources.forEach { issue ->
                        Surface(
                            color = V30Amber.copy(alpha = .07f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, V30Amber.copy(alpha = .18f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(9.dp)) {
                                Text(issue.sourceName, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "${issue.failureCount} falha(s) • ${issue.stage}",
                                    color = V30Text2,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showUnstable = false }) { Text("Fechar") } },
            containerColor = V30Surface2
        )
    }
}

@Composable
fun V30SourcesHub(
    news: AppState,
    newsVm: MonitorViewModel,
    videos: VideoState,
    videoVm: VideoViewModel,
    selectedTab: Int,
    onTabChange: (Int) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            V30Segment("Notícias", Icons.Outlined.Newspaper, selectedTab == 0, Modifier.weight(1f)) { onTabChange(0) }
            V30Segment("Vídeos", Icons.Outlined.SmartDisplay, selectedTab == 1, Modifier.weight(1f)) { onTabChange(1) }
        }
        if (selectedTab == 0) {
            V30NewsSources(news, newsVm, Modifier.weight(1f))
        } else {
            V30VideoSources(videos, videoVm, Modifier.weight(1f))
        }
    }
}

@Composable
private fun V30NewsSources(s: AppState, vm: MonitorViewModel, modifier: Modifier) {
    var mode by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var region by remember { mutableStateOf(SourceCatalog.ALL_REGION) }
    var stateCode by remember { mutableStateOf("") }
    val base = if (mode == 0) SourceCatalog.national else SourceCatalog.byState
    val visible = base.filter { src ->
        val regionOk = mode == 0 || region == SourceCatalog.ALL_REGION || src.region == region
        val stateOk = mode == 0 || stateCode.isBlank() || src.state == stateCode
        val queryOk = query.isBlank() || (listOf(src.name, src.group, src.region, src.stateName, src.state) + src.aliases)
            .any { it.contains(query, ignoreCase = true) }
        regionOk && stateOk && queryOk
    }

    LazyColumn(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Surface(color = if (s.searchAllSources) V30Mint.copy(alpha = .08f) else V30Surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, if (s.searchAllSources) V30Mint.copy(alpha = .24f) else V30Divider)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.TravelExplore, null, tint = V30Mint)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Buscar em todos os veículos", fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        Text("Inclui portais fora do catálogo", color = V30Text2, fontSize = 10.5.sp)
                    }
                    Switch(checked = s.searchAllSources, onCheckedChange = vm::setSearchAllSources)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                V30Segment("Nacionais", Icons.Outlined.Public, mode == 0, Modifier.weight(1f)) { mode = 0; stateCode = ""; region = SourceCatalog.ALL_REGION }
                V30Segment("Estados", Icons.Outlined.Map, mode == 1, Modifier.weight(1f)) { mode = 1 }
            }
        }
        item { OutlinedTextField(query, { query = it }, label = { Text("Pesquisar veículo") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        if (mode == 1) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SourceCatalog.regions.filter { it != SourceCatalog.NATIONAL_REGION }.forEach { item -> V30Chip(item, region == item) { region = item; stateCode = "" } }
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    V30Chip("Todos", stateCode.isBlank()) { stateCode = "" }
                    SourceCatalog.states.filter { region == SourceCatalog.ALL_REGION || it.third == region }.forEach { triple ->
                        V30Chip(triple.first, stateCode == triple.first) { stateCode = triple.first }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${visible.size} fonte(s) visível(is)", color = V30Text2, fontSize = 11.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.setVisibleSources(visible.map { it.id }.toSet(), true) }) { Text("Selecionar") }
                TextButton(onClick = { vm.setVisibleSources(visible.map { it.id }.toSet(), false) }) { Text("Limpar") }
            }
        }
        items(visible, key = { "news-${it.id}" }) { source ->
            val selected = source.id in s.selectedSourceIds
            V30SourceCard(source.name, if (source.state.isBlank()) source.group else "${source.state} • ${source.region}", selected) {
                vm.setSourceSelected(source.id, !selected)
            }
        }
    }
}

@Composable
private fun V30VideoSources(s: VideoState, vm: VideoViewModel, modifier: Modifier) {
    var mode by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var region by remember { mutableStateOf(SourceCatalog.ALL_REGION) }
    var stateCode by remember { mutableStateOf("") }
    val base = if (mode == 0) VideoSourceCatalog.national else VideoSourceCatalog.regional
    val visible = base.filter { src ->
        val regionOk = mode == 0 || region == SourceCatalog.ALL_REGION || src.region == region
        val stateOk = mode == 0 || stateCode.isBlank() || src.state == stateCode
        val stateName = SourceCatalog.states.firstOrNull { it.first == src.state }?.second.orEmpty()
        val queryOk = query.isBlank() || (listOf(src.name, src.group, src.region, src.state, stateName) + src.aliases)
            .any { it.contains(query, ignoreCase = true) }
        regionOk && stateOk && queryOk
    }

    LazyColumn(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Surface(color = V30Purple.copy(alpha = .07f), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, V30Purple.copy(alpha = .20f))) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.VideoLibrary, null, tint = V30Purple)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Fontes de Vídeo", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("${s.selectedSourceIds.size} fonte(s) ativa(s) • TV, Globoplay, portais e YouTube oficial", color = V30Text2, fontSize = 10.5.sp)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                V30Segment("Nacionais", Icons.Outlined.Public, mode == 0, Modifier.weight(1f)) { mode = 0; stateCode = ""; region = SourceCatalog.ALL_REGION }
                V30Segment("Estados", Icons.Outlined.Map, mode == 1, Modifier.weight(1f)) { mode = 1 }
            }
        }
        item { OutlinedTextField(query, { query = it }, label = { Text("Pesquisar canal, telejornal ou fonte") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        if (mode == 1) {
            item {
                Text("Região", color = V30Text2, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SourceCatalog.regions.filter { it != SourceCatalog.NATIONAL_REGION }.forEach { item -> V30Chip(item, region == item) { region = item; stateCode = "" } }
                }
            }
            item {
                Text("Estado", color = V30Text2, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    V30Chip("Todos", stateCode.isBlank()) { stateCode = "" }
                    SourceCatalog.states.filter { region == SourceCatalog.ALL_REGION || it.third == region }.forEach { triple ->
                        V30Chip(triple.first, stateCode == triple.first) { stateCode = triple.first }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${visible.size} fonte(s) visível(is)", color = V30Text2, fontSize = 11.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.setSources(visible.map { it.id }.toSet(), true) }) { Text("Selecionar") }
                TextButton(onClick = { vm.setSources(visible.map { it.id }.toSet(), false) }) { Text("Limpar") }
            }
        }
        items(visible, key = { "video-${it.id}" }) { source ->
            val selected = source.id in s.selectedSourceIds
            val stateName = SourceCatalog.states.firstOrNull { it.first == source.state }?.second.orEmpty()
            val subtitle = if (source.state.isBlank()) source.group else "${source.state} • $stateName • ${source.region}"
            V30SourceCard(source.name, subtitle, selected) { vm.setSourceSelected(source.id, !selected) }
        }
    }
}

@Composable
fun V30CompactProgress(progress: LiveSearchProgress) {
    if (!progress.active) return
    Surface(color = V30Surface2, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            V30ProgressBody(progress, if (progress.kind.startsWith("Vídeos")) V30Purple else V30Mint)
        }
    }
}

@Composable
private fun V30ProgressBody(progress: LiveSearchProgress, color: Color) {
    val elapsed = v30ElapsedSeconds(progress)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Timer, null, tint = color, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        Text(v30Duration(elapsed), color = color, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.width(8.dp))
        Text(progress.kind.ifBlank { "Busca" }, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (progress.total > 0) Text("${progress.completed}/${progress.total}", color = V30Text2, fontSize = 10.5.sp)
    }
    Spacer(Modifier.height(6.dp))
    LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth().height(5.dp), color = color, trackColor = V30Divider)
    Spacer(Modifier.height(6.dp))
    Text("Agora: ${progress.currentSource.ifBlank { "preparando" }}", color = V30Text2, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    if (progress.currentQuery.isNotBlank()) {
        Text("Termo: ${progress.currentQuery}", color = V30Text2, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.height(4.dp))
    Text("${progress.found} encontrado(s) • ${progress.newCount} novo(s) • ${progress.errors} fonte(s) instável(is)", color = color, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun v30ElapsedSeconds(progress: LiveSearchProgress): Long {
    var now by remember(progress.startedAt) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(progress.active, progress.startedAt) {
        if (progress.active && progress.startedAt > 0) {
            while (true) {
                now = System.currentTimeMillis()
                delay(1000)
            }
        }
    }
    val end = if (progress.active) now else progress.finishedAt.takeIf { it > 0 } ?: now
    return if (progress.startedAt <= 0) 0 else ((end - progress.startedAt).coerceAtLeast(0L) / 1000L)
}

private fun v30Duration(seconds: Long): String = "%02d:%02d".format(seconds / 60, seconds % 60)

@Composable
private fun V30SourceCard(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) V30Selected else V30Surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (selected) V30Accent.copy(alpha = .45f) else V30Divider),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null, tint = if (selected) V30Accent else V30Text2, modifier = Modifier.size(21.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = V30Text2, fontSize = 10.3.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun V30VideoCard(item: VideoItem, newStart: Long, newEnd: Long) {
    val context = LocalContext.current
    val isNew = v401InRun(item.capturedAt, newStart, newEnd)
    val open = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.link))) }; Unit }
    Surface(color = V30Surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, V30Divider), modifier = Modifier.fillMaxWidth().clickable(onClick = open)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.PlayCircle, null, tint = V30Purple, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.sourceName, color = V30Purple, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(v30DateTime(item.publishedAt), color = V30Text2, fontSize = 10.5.sp)
                }
                if (isNew) {
                    V30Badge("NOVO", V30Mint)
                    Spacer(Modifier.width(6.dp))
                }
                Text("Link direto", color = V30Mint, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text(item.title, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (item.summary.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(item.summary, color = V30Text2, fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (item.relevant) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (item.demand) V30Badge("DEMANDA", V30Amber)
                    item.matchedTerm.split(',').map { it.trim() }.filter { it.isNotBlank() }.take(4).forEach { V30Badge(it, V30Accent) }
                }
            }
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedButton(onClick = open, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.SmartDisplay, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Abrir vídeo")
                }
                OutlinedButton(
                    onClick = { v30ShareWhatsApp(context, item.title, item.link) },
                    modifier = Modifier.weight(1f).height(50.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = V30Mint)
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
private fun V30NewsCard(n: News, newStart: Long, newEnd: Long) {
    val context = LocalContext.current
    val isNew = v401InRun(n.capturedAt, newStart, newEnd)
    Surface(color = V30Surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, V30Divider), modifier = Modifier.fillMaxWidth().clickable {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(n.link))) }
    }) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(n.source, color = V30Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (isNew) {
                    V30Badge("NOVO", V30Mint)
                    Spacer(Modifier.width(6.dp))
                }
                Text(v30DateTime(n.date), color = V30Text2, fontSize = 10.5.sp)
            }
            Spacer(Modifier.height(7.dp))
            Text(n.title, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (n.snippet.isNotBlank()) { Spacer(Modifier.height(5.dp)); Text(n.snippet, color = V30Text2, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            if (n.demand || n.matchedTerm.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (n.demand) V30Badge("DEMANDA", V30Amber)
                    if (n.matchedTerm.isNotBlank()) V30Badge(n.matchedTerm, V30Accent)
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
                    onClick = { v30ShareWhatsApp(context, n.title, n.link) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = V30Mint)
                ) {
                    Icon(Icons.Outlined.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("WhatsApp")
                }
            }
        }
    }
}

private fun v30ShareWhatsApp(context: Context, title: String, link: String) {
    val message = listOf(title.trim(), link.trim()).filter { it.isNotBlank() }.joinToString("\n")
    if (message.isBlank()) return

    fun shareIntent(packageName: String? = null) = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
        packageName?.let(::setPackage)
    }

    // Prioriza WhatsApp comum, depois WhatsApp Business. Se nenhum estiver instalado,
    // abre o seletor padrão do Android sem perder o título + link.
    if (runCatching { context.startActivity(shareIntent("com.whatsapp")) }.isSuccess) return
    if (runCatching { context.startActivity(shareIntent("com.whatsapp.w4b")) }.isSuccess) return
    runCatching {
        context.startActivity(Intent.createChooser(shareIntent(), "Compartilhar link"))
    }
}

@Composable
private fun V30Metric(label: String, value: String, icon: ImageVector, color: Color, modifier: Modifier) {
    Surface(color = V30Surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, V30Divider), modifier = modifier) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp)); Spacer(Modifier.weight(1f)); Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(6.dp)); Text(label, color = V30Text2, fontSize = 11.5.sp)
        }
    }
}

@Composable
private fun V30SmallStat(label: String, value: String, color: Color, modifier: Modifier) {
    Surface(color = color.copy(alpha = .08f), shape = RoundedCornerShape(10.dp), modifier = modifier) {
        Column(Modifier.padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = color, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Text(label, color = V30Text2, fontSize = 8.8.sp, maxLines = 1)
        }
    }
}

@Composable
private fun V30Segment(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(color = if (selected) V30Selected else V30Surface, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, if (selected) V30Accent.copy(alpha = .45f) else V30Divider), modifier = modifier.height(42.dp).clickable(onClick = onClick)) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (selected) V30Accent else V30Text2, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(label, color = if (selected) V30Accent else V30Text2, fontSize = 11.5.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@Composable
private fun V30Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(color = if (selected) V30Accent.copy(alpha = .14f) else V30Surface, shape = RoundedCornerShape(11.dp), border = BorderStroke(1.dp, if (selected) V30Accent.copy(alpha = .40f) else V30Divider), modifier = Modifier.height(34.dp).clickable(onClick = onClick)) {
        Box(Modifier.padding(horizontal = 11.dp), contentAlignment = Alignment.Center) { Text(label, color = if (selected) V30Accent else V30Text2, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) }
    }
}

@Composable
private fun V30Badge(label: String, color: Color) {
    Surface(color = color.copy(alpha = .12f), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, color.copy(alpha = .28f))) {
        Text(label, color = color, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun V30Empty(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.SearchOff, null, tint = V30Accent, modifier = Modifier.size(28.dp)); Spacer(Modifier.height(8.dp)); Text(title, fontWeight = FontWeight.Bold); Text(subtitle, color = V30Text2, fontSize = 11.sp)
    }
}

private fun v401InRun(capturedAt: Long, startedAt: Long, completedAt: Long): Boolean {
    if (capturedAt <= 0L || startedAt <= 0L) return false
    val safeEnd = completedAt.takeIf { it >= startedAt } ?: return false
    return capturedAt in startedAt..(safeEnd + 5_000L)
}

private fun v30DateTime(ms: Long): String = if (ms <= 0) "—" else SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(Date(ms))
private fun v30ParseDateTime(date: String, time: String): Long? = runCatching {
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).apply { isLenient = false }.parse("$date $time")?.time
}.getOrNull()
