package br.com.monitordenoticias.android

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val V29Surface = Color(0xFF0E1A2A)
private val V29Surface2 = Color(0xFF132238)
private val V29Selected = Color(0xFF17365F)
private val V29Accent = Color(0xFF5EA2FF)
private val V29Mint = Color(0xFF39D6A2)
private val V29Amber = Color(0xFFFFB45E)
private val V29Purple = Color(0xFFA57BFF)
private val V29Red = Color(0xFFFF7777)
private val V29Text2 = Color(0xFFAEBBD0)
private val V29Divider = Color(0xFF21334A)

@Composable
fun V29Videos(s: VideoState, vm: VideoViewModel) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(BackgroundMonitor.PREFS, 0)
    val autoCompleted = prefs.getLong(VideoAutoRunLog.KEY_COMPLETED_AT, 0L)
    val autoNew = prefs.getInt(VideoAutoRunLog.KEY_NEW, 0)
    val autoRelevant = prefs.getInt(VideoAutoRunLog.KEY_NEW_RELEVANT, 0)

    var showSources by remember { mutableStateOf(false) }
    var showPeriod by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var sourceMode by remember { mutableIntStateOf(0) }
    var sourceQuery by remember { mutableStateOf("") }
    var sourceRegion by remember { mutableStateOf(SourceCatalog.ALL_REGION) }
    var sourceState by remember { mutableStateOf("") }

    val periodFrom = parseV29DateTime(s.periodStartDate, s.periodStartTime)
    val periodTo = parseV29DateTime(s.periodEndDate, s.periodEndTime)
    val validPeriod = periodFrom != null && periodTo != null && periodFrom < periodTo

    val shown = when (s.filter) {
        VideoFilter.ALL -> s.items
        VideoFilter.RELEVANT -> s.items.filter { it.relevant }
        VideoFilter.DEMANDS -> s.items.filter { it.demand }
    }

    val sourceBase = if (sourceMode == 0) VideoSourceCatalog.national else VideoSourceCatalog.regional
    val visibleSources = sourceBase.filter { src ->
        val regionOk = sourceMode == 0 || sourceRegion == SourceCatalog.ALL_REGION || src.region == sourceRegion
        val stateOk = sourceMode == 0 || sourceState.isBlank() || src.state == sourceState
        val stateName = SourceCatalog.states.firstOrNull { it.first == src.state }?.second.orEmpty()
        val queryOk = sourceQuery.isBlank() ||
            (listOf(src.name, src.group, src.region, src.state, stateName) + src.aliases)
                .any { it.contains(sourceQuery, ignoreCase = true) }
        regionOk && stateOk && queryOk
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(
                color = V29Purple.copy(alpha = .08f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, V29Purple.copy(alpha = .22f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(V29Purple.copy(alpha = .13f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.SmartDisplay, null, tint = V29Purple, modifier = Modifier.size(25.dp))
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Varredura audiovisual", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (autoCompleted > 0) "Última automática: ${v29DateTime(autoCompleted)}" else "Aguardando primeira busca automática",
                                color = V29Text2,
                                fontSize = 11.5.sp
                            )
                            if (autoCompleted > 0) {
                                Text("$autoNew novo(s) • $autoRelevant relevante(s)", color = V29Purple, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        FilledIconButton(onClick = vm::searchNow, enabled = !s.busy, modifier = Modifier.size(44.dp)) {
                            Icon(if (s.busy) Icons.Outlined.HourglassTop else Icons.Outlined.Refresh, "Buscar vídeos")
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Busca automática aproximadamente a cada 1 hora, inclusive com o app fechado.", color = V29Text2, fontSize = 11.sp)
                }
            }
        }

        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                V29Chip("Todos", s.filter == VideoFilter.ALL) { vm.setFilter(VideoFilter.ALL) }
                V29Chip("Relevantes", s.filter == VideoFilter.RELEVANT) { vm.setFilter(VideoFilter.RELEVANT) }
                V29Chip("Demandas", s.filter == VideoFilter.DEMANDS) { vm.setFilter(VideoFilter.DEMANDS) }
                V29Chip("Fontes (${s.selectedSourceIds.size})", showSources) { showSources = !showSources }
                V29Chip("Período", showPeriod) { showPeriod = !showPeriod }
                V29Chip("Limpar", false) { confirmClear = true }
            }
        }

        if (showPeriod) {
            item {
                Surface(color = V29Surface, shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, V29Divider), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(13.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.DateRange, null, tint = V29Purple, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Pesquisar vídeos por período", fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                Text("Data e hora independentes da pesquisa de notícias", color = V29Text2, fontSize = 10.5.sp)
                            }
                        }
                        Spacer(Modifier.height(9.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Hoje" to 0, "24 horas" to 1, "7 dias" to 7, "30 dias" to 30).forEach { (label, days) ->
                                V29Chip(label, false) { vm.applyPeriodPreset(days) }
                            }
                        }
                        Spacer(Modifier.height(9.dp))
                        Text("Início", color = V29Accent, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(5.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            OutlinedTextField(s.periodStartDate, vm::setPeriodStartDate, label = { Text("Data") }, singleLine = true, modifier = Modifier.weight(1.45f))
                            OutlinedTextField(s.periodStartTime, vm::setPeriodStartTime, label = { Text("Hora") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(7.dp))
                        Text("Fim", color = V29Mint, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(5.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            OutlinedTextField(s.periodEndDate, vm::setPeriodEndDate, label = { Text("Data") }, singleLine = true, modifier = Modifier.weight(1.45f))
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
                            Text("Revise as datas e horários. Use dd/MM/aaaa e HH:mm.", color = V29Amber, fontSize = 10.5.sp)
                        }
                    }
                }
            }
        }

        if (showSources) {
            item {
                Surface(color = V29Surface, shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, V29Divider), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Fontes de vídeo", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Escolha canais nacionais ou filtre telejornais regionais por Região e UF.", color = V29Text2, fontSize = 10.5.sp)
                        Spacer(Modifier.height(9.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            V29Segment("Nacionais", Icons.Outlined.Public, sourceMode == 0, Modifier.weight(1f)) {
                                sourceMode = 0
                                sourceState = ""
                                sourceRegion = SourceCatalog.ALL_REGION
                            }
                            V29Segment("Estados", Icons.Outlined.Map, sourceMode == 1, Modifier.weight(1f)) {
                                sourceMode = 1
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            sourceQuery,
                            { sourceQuery = it },
                            label = { Text("Pesquisar canal, telejornal ou fonte") },
                            leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (sourceMode == 1) {
                            Spacer(Modifier.height(8.dp))
                            Text("Região", color = V29Text2, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(5.dp))
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                SourceCatalog.regions.filter { it != SourceCatalog.NATIONAL_REGION }.forEach { item ->
                                    V29Chip(item, sourceRegion == item) {
                                        sourceRegion = item
                                        sourceState = ""
                                    }
                                }
                            }
                            Spacer(Modifier.height(7.dp))
                            Text("Estado", color = V29Text2, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(5.dp))
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                V29Chip("Todos", sourceState.isBlank()) { sourceState = "" }
                                SourceCatalog.states
                                    .filter { sourceRegion == SourceCatalog.ALL_REGION || it.third == sourceRegion }
                                    .forEach { triple ->
                                        V29Chip(triple.first, sourceState == triple.first) { sourceState = triple.first }
                                    }
                            }
                        }
                        Spacer(Modifier.height(7.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${visibleSources.size} fonte(s) visível(is)", color = V29Text2, fontSize = 10.8.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = { vm.setSources(visibleSources.map { it.id }.toSet(), true) }) {
                                Text("Selecionar", fontSize = 10.8.sp)
                            }
                            TextButton(onClick = { vm.setSources(visibleSources.map { it.id }.toSet(), false) }) {
                                Text("Limpar", fontSize = 10.8.sp)
                            }
                        }
                    }
                }
            }

            items(visibleSources, key = { "video-source-${it.id}" }) { source ->
                val selected = source.id in s.selectedSourceIds
                Surface(
                    color = if (selected) V29Selected else V29Surface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, if (selected) V29Accent.copy(alpha = .45f) else V29Divider),
                    modifier = Modifier.fillMaxWidth().clickable { vm.setSourceSelected(source.id, !selected) }
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                            null,
                            tint = if (selected) V29Accent else V29Text2,
                            modifier = Modifier.size(21.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(source.name, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            val stateName = SourceCatalog.states.firstOrNull { it.first == source.state }?.second.orEmpty()
                            Text(
                                if (source.state.isBlank() || source.state == "BR") source.group else "${source.state} • $stateName • ${source.region}",
                                color = V29Text2,
                                fontSize = 10.3.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Vídeos detectados", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Text("${shown.size}", color = V29Text2, fontSize = 11.5.sp)
            }
        }

        if (shown.isEmpty()) {
            item { V29Empty("Nenhum vídeo encontrado", "Use Buscar agora, pesquise um período ou aguarde a próxima varredura automática.") }
        } else {
            items(shown, key = { it.link }) { V29VideoCard(it) }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            icon = { Icon(Icons.Outlined.DeleteSweep, null, tint = V29Red) },
            title = { Text("Limpar vídeos?") },
            text = { Text("Isso apaga o histórico de vídeos detectados. Termos, Demandas e fontes selecionadas serão mantidos.") },
            confirmButton = {
                TextButton(onClick = { vm.clearHistory(); confirmClear = false }) { Text("Limpar", color = V29Red) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
            containerColor = V29Surface2
        )
    }
}

@Composable
private fun V29VideoCard(item: VideoItem) {
    val context = LocalContext.current
    val openVideo = {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.link))) }
        Unit
    }
    Surface(
        color = V29Surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, V29Divider.copy(alpha = .7f)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = openVideo)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(V29Purple.copy(alpha = .13f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.PlayArrow, null, tint = V29Purple, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.sourceName, color = V29Purple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(v29DateTime(item.publishedAt), color = V29Text2, fontSize = 10.5.sp)
                }
                Surface(color = V29Mint.copy(alpha = .10f), shape = RoundedCornerShape(8.dp)) {
                    Row(Modifier.padding(horizontal = 7.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Verified, null, tint = V29Mint, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Link direto", color = V29Mint, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(9.dp))
            Text(item.title, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (item.summary.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(item.summary, color = V29Text2, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (item.relevant) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (item.demand) V29Badge("DEMANDA", V29Amber)
                    if (item.matchedTerm.isNotBlank()) V29Badge(item.matchedTerm, V29Accent)
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
private fun V29Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) V29Accent.copy(alpha = .14f) else V29Surface,
        shape = RoundedCornerShape(11.dp),
        border = BorderStroke(1.dp, if (selected) V29Accent.copy(alpha = .40f) else V29Divider),
        modifier = Modifier.height(34.dp).clickable(onClick = onClick)
    ) {
        Box(Modifier.padding(horizontal = 11.dp), contentAlignment = Alignment.Center) {
            Text(label, color = if (selected) V29Accent else V29Text2, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@Composable
private fun V29Segment(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        color = if (selected) V29Selected else V29Surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (selected) V29Accent.copy(alpha = .45f) else V29Divider),
        modifier = modifier.height(42.dp).clickable(onClick = onClick)
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (selected) V29Accent else V29Text2, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = if (selected) V29Accent else V29Text2, fontSize = 11.5.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@Composable
private fun V29Badge(label: String, color: Color) {
    Surface(color = color.copy(alpha = .12f), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, color.copy(alpha = .28f))) {
        Text(label, color = color, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun V29Empty(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.SearchOff, null, tint = V29Accent, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(8.dp))
        Text(title, fontWeight = FontWeight.Bold)
        Text(subtitle, color = V29Text2, fontSize = 11.sp)
    }
}

private fun v29DateTime(ms: Long): String =
    if (ms <= 0) "—" else SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(Date(ms))

private fun parseV29DateTime(date: String, time: String): Long? = runCatching {
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).apply { isLenient = false }.parse("$date $time")?.time
}.getOrNull()
