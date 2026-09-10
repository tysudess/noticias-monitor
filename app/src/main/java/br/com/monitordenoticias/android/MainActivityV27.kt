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

private val V27Bg = Color(0xFF07101D)
private val V27Surface = Color(0xFF0E1A2A)
private val V27Surface2 = Color(0xFF132238)
private val V27Selected = Color(0xFF17365F)
private val V27Accent = Color(0xFF5EA2FF)
private val V27Mint = Color(0xFF39D6A2)
private val V27Amber = Color(0xFFFFB45E)
private val V27Purple = Color(0xFFA57BFF)
private val V27Red = Color(0xFFFF7777)
private val V27Text = Color(0xFFF5F8FC)
private val V27Text2 = Color(0xFFAEBBD0)
private val V27Divider = Color(0xFF21334A)

private val V27Colors = darkColorScheme(
    primary = V27Accent,
    secondary = V27Mint,
    background = V27Bg,
    surface = V27Surface,
    surfaceVariant = V27Surface2,
    onPrimary = Color(0xFF061425),
    onBackground = V27Text,
    onSurface = V27Text,
    onSurfaceVariant = V27Text2
)

class MainActivityV27 : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch("android.permission.POST_NOTIFICATIONS")
        }
        val prefs = getSharedPreferences(BackgroundMonitor.PREFS, MODE_PRIVATE)
        BackgroundMonitor.scheduleAll(this, prefs.getInt("interval_minutes", 30).coerceAtLeast(15))

        setContent {
            MaterialTheme(colorScheme = V27Colors) {
                MonitorAppV27()
            }
        }
    }
}

private data class V27NavItem(val tab: Int, val label: String, val icon: ImageVector)

@Composable
private fun MonitorAppV27(vm: MonitorViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var showMore by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = V27Bg,
        topBar = { V27TopBar(state.selectedTab) },
        bottomBar = {
            V27BottomBar(
                selectedTab = state.selectedTab,
                onTab = vm::setTab,
                onMore = { showMore = true }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .background(V27Bg)
        ) {
            if (state.status.isNotBlank() && state.status != "Pronto") {
                V27StatusStrip(state.status)
            }
            when (state.selectedTab) {
                0 -> V27Home(state, vm)
                1 -> V27Period(state, vm)
                2 -> V27Demands(state, vm)
                3 -> V27History(state, vm)
                4 -> V27Terms(state, vm)
                5 -> V27Sources(state, vm)
                else -> V27Settings(state, vm)
            }
        }
    }

    if (showMore) {
        ModalBottomSheet(
            onDismissRequest = { showMore = false },
            containerColor = V27Surface2
        ) {
            Text(
                "Mais opções",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            V27MoreItem(Icons.Outlined.History, "Histórico", "Todas as matérias armazenadas") {
                vm.setTab(3); showMore = false
            }
            V27MoreItem(Icons.Outlined.ManageSearch, "Termos", "Palavras monitoradas") {
                vm.setTab(4); showMore = false
            }
            V27MoreItem(Icons.Outlined.Settings, "Configurações", "Segundo plano, intervalo e saúde do monitor") {
                vm.setTab(6); showMore = false
            }
            Spacer(Modifier.navigationBarsPadding().height(12.dp))
        }
    }
}

@Composable
private fun V27TopBar(tab: Int) {
    val title = when (tab) {
        0 -> "Monitor de Notícias"
        1 -> "Período"
        2 -> "Demandas"
        3 -> "Histórico"
        4 -> "Termos"
        5 -> "Fontes"
        else -> "Configurações"
    }
    val subtitle = when (tab) {
        0 -> "Painel inteligente de monitoramento"
        1 -> "Defina o intervalo da pesquisa"
        2 -> "Alertas por veículo e assunto"
        3 -> "Arquivo das matérias capturadas"
        4 -> "Palavras usadas no monitoramento"
        5 -> "Escolha os veículos para monitorar"
        else -> "Saúde, automação e preferências"
    }

    Surface(color = V27Bg, tonalElevation = 0.dp, modifier = Modifier.statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(V27Accent.copy(alpha = .12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Radar, null, tint = V27Accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.ExtraBold)
                Text(subtitle, color = V27Text2, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Surface(color = V27Accent.copy(alpha = .10f), shape = RoundedCornerShape(9.dp)) {
                Text("2.7.0", color = V27Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
            }
        }
    }
}

@Composable
private fun V27BottomBar(selectedTab: Int, onTab: (Int) -> Unit, onMore: () -> Unit) {
    val nav = listOf(
        V27NavItem(0, "Início", Icons.Outlined.Home),
        V27NavItem(5, "Fontes", Icons.Outlined.Layers),
        V27NavItem(1, "Período", Icons.Outlined.DateRange),
        V27NavItem(2, "Demandas", Icons.Outlined.NotificationsNone)
    )
    val moreSelected = selectedTab in setOf(3, 4, 6)

    Surface(
        color = Color(0xFF0A1422),
        tonalElevation = 8.dp,
        border = BorderStroke(1.dp, V27Divider.copy(alpha = .7f)),
        modifier = Modifier.fillMaxWidth().navigationBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
            nav.forEach { item ->
                V27NavButton(item.label, item.icon, selectedTab == item.tab, Modifier.weight(1f)) { onTab(item.tab) }
            }
            V27NavButton("Mais", Icons.Outlined.MoreHoriz, moreSelected, Modifier.weight(1f), onMore)
        }
    }
}

@Composable
private fun V27NavButton(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.fillMaxHeight().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(width = 34.dp, height = 26.dp).clip(RoundedCornerShape(10.dp)).background(if (selected) V27Accent.copy(alpha = .14f) else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, tint = if (selected) V27Accent else V27Text2, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(label, color = if (selected) V27Accent else V27Text2, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun V27StatusStrip(text: String) {
    val warning = text.startsWith("⚠")
    Surface(color = if (warning) V27Amber.copy(alpha = .08f) else V27Accent.copy(alpha = .06f), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (warning) Icons.Outlined.WarningAmber else Icons.Outlined.Info, null, tint = if (warning) V27Amber else V27Accent, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(7.dp))
            Text(text.removePrefix("⚠ "), color = V27Text2, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun V27Home(s: AppState, vm: MonitorViewModel) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(BackgroundMonitor.PREFS, 0)
    val lastAuto = prefs.getLong(AutoRunLog.KEY_NEWS_COMPLETED_AT, 0L)
    val shown = if (s.showOnlyDemands) s.news.filter { it.demand } else s.news
    val sourceCount = s.news.map { it.source }.distinct().size

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, top = 10.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(
                color = V27Mint.copy(alpha = .08f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, V27Mint.copy(alpha = .22f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(V27Mint.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Radar, null, tint = V27Mint, modifier = Modifier.size(25.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(V27Mint))
                            Spacer(Modifier.width(7.dp))
                            Text("Monitoramento ativo", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            if (lastAuto > 0L) "Última busca automática: ${v27DateTime(lastAuto)}" else "Aguardando a primeira busca automática",
                            color = V27Text2,
                            fontSize = 11.5.sp
                        )
                    }
                    FilledIconButton(onClick = vm::search, enabled = !s.busy, modifier = Modifier.size(42.dp)) {
                        Icon(if (s.busy) Icons.Outlined.HourglassTop else Icons.Outlined.Refresh, "Buscar agora", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                V27MetricCard("Notícias 24h", s.news.size.toString(), Icons.Outlined.Article, V27Accent, Modifier.weight(1f))
                V27MetricCard("Demandas", s.news.count { it.demand }.toString(), Icons.Outlined.NotificationsActive, V27Amber, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                V27MetricCard("Fontes encontradas", sourceCount.toString(), Icons.Outlined.Public, V27Mint, Modifier.weight(1f))
                V27MetricCard("Última atualização", s.lastUpdatedAt?.let { v27Time(it) } ?: "—", Icons.Outlined.Schedule, V27Purple, Modifier.weight(1f))
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                V27Chip("Todas", !s.showOnlyDemands) { vm.setDemandFilter(false) }
                Spacer(Modifier.width(7.dp))
                V27Chip("Demandas", s.showOnlyDemands) { vm.setDemandFilter(true) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { vm.setTab(5) }) {
                    Icon(Icons.Outlined.Tune, "Filtros e fontes", tint = V27Text2)
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Últimas notícias", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Text("${shown.size} matéria(s)", color = V27Text2, fontSize = 11.sp)
            }
        }

        if (shown.isEmpty()) {
            item { V27Empty("Nenhuma notícia no escopo atual", "Faça uma nova busca ou ajuste suas fontes.") }
        } else {
            items(shown, key = { it.link }) { news -> V27NewsCard(news) }
        }
    }
}

@Composable
private fun V27MetricCard(label: String, value: String, icon: ImageVector, color: Color, modifier: Modifier) {
    Surface(color = V27Surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, V27Divider.copy(alpha = .7f)), modifier = modifier) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(8.dp))
            Text(label, color = V27Text2, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun V27NewsCard(n: News) {
    val context = LocalContext.current
    Surface(
        color = V27Surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, V27Divider.copy(alpha = .7f)),
        modifier = Modifier.fillMaxWidth().clickable {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(n.link))) }
        }
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(n.source, color = V27Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(v27NewsDate(n.date), color = V27Text2, fontSize = 10.5.sp)
                Spacer(Modifier.width(5.dp))
                Icon(Icons.Outlined.BookmarkBorder, null, tint = V27Text2, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(n.title, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (n.snippet.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(v27CleanSnippet(n.snippet), color = V27Text2, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (n.demand || n.matchedTerm.isNotBlank()) {
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (n.demand) V27Badge("DEMANDA", V27Amber)
                    if (n.matchedTerm.isNotBlank()) V27Badge(n.matchedTerm, V27Accent)
                }
            }
        }
    }
}

@Composable
private fun V27Period(s: AppState, vm: MonitorViewModel) {
    val from = v27ParseDateTime(s.periodStartDate, s.periodStartTime)
    val to = v27ParseDateTime(s.periodEndDate, s.periodEndTime)
    val valid = from != null && to != null && from < to

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, top = 10.dp, bottom = 84.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    V27QuickAction("Hoje", Icons.Outlined.Today) { vm.applyPeriodPreset(0) }
                    V27QuickAction("24 horas", Icons.Outlined.Schedule) { vm.applyPeriodPreset(1) }
                    V27QuickAction("7 dias", Icons.Outlined.DateRange) { vm.applyPeriodPreset(7) }
                    V27QuickAction("30 dias", Icons.Outlined.CalendarMonth) { vm.applyPeriodPreset(30) }
                }
            }
            item {
                V27DateTimeCard("Início", V27Accent, s.periodStartDate, s.periodStartTime, vm::setPeriodStartDate, vm::setPeriodStartTime)
            }
            item {
                V27DateTimeCard("Fim", V27Mint, s.periodEndDate, s.periodEndTime, vm::setPeriodEndDate, vm::setPeriodEndTime)
            }
            item {
                Surface(color = if (valid) V27Mint.copy(alpha = .08f) else V27Amber.copy(alpha = .08f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (valid) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber, null, tint = if (valid) V27Mint else V27Amber)
                        Spacer(Modifier.width(9.dp))
                        Column {
                            Text(if (valid) "Período válido" else "Revise o período", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(if (valid) "Datas e horários são salvos automaticamente." else "O início precisa ser anterior ao fim.", color = V27Text2, fontSize = 11.sp)
                        }
                    }
                }
            }
            if (s.news.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Resultados", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                        Text("${s.news.size} matéria(s)", color = V27Text2, fontSize = 11.sp)
                    }
                }
                items(s.news, key = { it.link }) { V27NewsCard(it) }
            }
        }

        Surface(color = V27Bg, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding()) {
            Button(
                onClick = vm::searchSavedPeriod,
                enabled = valid && !s.busy,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(15.dp)
            ) {
                Icon(Icons.Outlined.Search, null)
                Spacer(Modifier.width(8.dp))
                Text(if (s.busy) "Pesquisando..." else "Pesquisar período", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun V27DateTimeCard(title: String, color: Color, date: String, time: String, onDate: (String) -> Unit, onTime: (String) -> Unit) {
    Surface(color = V27Surface, shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, V27Divider.copy(alpha = .75f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = date,
                    onValueChange = onDate,
                    label = { Text("Data") },
                    placeholder = { Text("dd/MM/aaaa") },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                    modifier = Modifier.weight(1.35f)
                )
                OutlinedTextField(
                    value = time,
                    onValueChange = onTime,
                    label = { Text("Hora") },
                    placeholder = { Text("HH:mm") },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                    modifier = Modifier.weight(.85f)
                )
            }
        }
    }
}

@Composable
private fun V27Sources(s: AppState, vm: MonitorViewModel) {
    var section by remember { mutableIntStateOf(if (s.searchAllSources) 2 else 0) }
    var query by remember { mutableStateOf("") }
    var region by remember { mutableStateOf(SourceCatalog.ALL_REGION) }
    var state by remember { mutableStateOf("") }

    val base = when (section) {
        0 -> SourceCatalog.national
        1 -> SourceCatalog.byState
        else -> emptyList()
    }
    val normalized = query.trim()
    val visible = base.filter { src ->
        val regionOk = section != 1 || region == SourceCatalog.ALL_REGION || src.region == region
        val stateOk = section != 1 || state.isBlank() || src.state == state
        val queryOk = normalized.isBlank() || listOf(src.name, src.group, src.region, src.stateName, src.state).plus(src.aliases).any { it.contains(normalized, ignoreCase = true) }
        regionOk && stateOk && queryOk
    }
    val visibleIds = visible.map { it.id }.toSet()
    val selectedVisible = visibleIds.count { it in s.selectedSourceIds }
    val availableStates = SourceCatalog.states.filter { region == SourceCatalog.ALL_REGION || it.third == region }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, top = 10.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            item {
                Surface(
                    color = if (s.searchAllSources) V27Mint.copy(alpha = .08f) else V27Surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, if (s.searchAllSources) V27Mint.copy(alpha = .25f) else V27Divider),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Language, null, tint = V27Mint, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Buscar em todos os veículos", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Inclui veículos locais e portais menores", color = V27Text2, fontSize = 11.sp)
                        }
                        Switch(checked = s.searchAllSources, onCheckedChange = { enabled ->
                            vm.setSearchAllSources(enabled)
                            if (enabled) section = 2
                        })
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    V27Segment("Nacionais", Icons.Outlined.Newspaper, section == 0, Modifier.weight(1f)) { section = 0; state = "" }
                    V27Segment("Região", Icons.Outlined.Map, section == 1, Modifier.weight(1f)) { section = 1 }
                    V27Segment("Qualquer", Icons.Outlined.TravelExplore, section == 2, Modifier.weight(1f)) { section = 2; vm.setSearchAllSources(true) }
                }
            }

            if (section == 2) {
                item {
                    Surface(color = V27Surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.TravelExplore, null, tint = V27Accent, modifier = Modifier.size(34.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Pesquisa aberta", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("A busca poderá retornar qualquer veículo indexado.", color = V27Text2, fontSize = 12.sp)
                        }
                    }
                }
            } else {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Limpar") } },
                        placeholder = { Text("Pesquisar veículo ou grupo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (section == 1) {
                    item {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SourceCatalog.regions.filter { it != SourceCatalog.NATIONAL_REGION }.forEach { item ->
                                V27Chip(item, region == item) { region = item; state = "" }
                            }
                        }
                    }
                    item {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            V27Chip("Todos", state.isBlank()) { state = "" }
                            availableStates.forEach { item -> V27Chip(item.first, state == item.first) { state = item.first } }
                        }
                    }
                }

                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${visible.size} veículos", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("$selectedVisible selecionado(s) nesta lista", color = V27Text2, fontSize = 11.sp)
                        }
                        TextButton(onClick = { vm.setVisibleSources(visibleIds, true) }) { Text("Selecionar todas") }
                        TextButton(onClick = { vm.setVisibleSources(visibleIds, false) }) { Text("Limpar") }
                    }
                }

                if (visible.isEmpty()) {
                    item { V27Empty("Nenhuma fonte encontrada", "Altere sua pesquisa ou os filtros.") }
                } else {
                    items(visible, key = { it.id }) { source ->
                        V27SourceCard(source, source.id in s.selectedSourceIds) { vm.setSourceSelected(source.id, it) }
                    }
                }
            }
        }

        Surface(color = V27Bg, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (s.searchAllSources) "Busca aberta" else "${s.selectedSourceIds.size} fonte(s)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(if (s.searchAllSources) "qualquer veículo" else "selecionada(s)", color = V27Text2, fontSize = 10.5.sp)
                }
                Button(
                    onClick = vm::search,
                    enabled = !s.busy && (s.searchAllSources || s.selectedSourceIds.isNotEmpty()),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 13.dp)
                ) {
                    Icon(Icons.Outlined.Search, null, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(if (s.busy) "Buscando" else "Buscar agora", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun V27SourceCard(source: MediaSource, selected: Boolean, onToggle: (Boolean) -> Unit) {
    Surface(
        color = if (selected) V27Selected else V27Surface,
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, if (selected) V27Accent.copy(alpha = .45f) else V27Divider.copy(alpha = .75f)),
        modifier = Modifier.fillMaxWidth().clickable { onToggle(!selected) }
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(if (selected) V27Accent.copy(alpha = .14f) else V27Surface2), contentAlignment = Alignment.Center) {
                Icon(if (source.region == SourceCatalog.NATIONAL_REGION) Icons.Outlined.Newspaper else Icons.Outlined.LocationOn, null, tint = if (selected) V27Accent else V27Text2, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(source.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (source.region == SourceCatalog.NATIONAL_REGION) "Nacional" else "${source.state} • ${source.region}", color = V27Text2, fontSize = 11.sp)
            }
            Checkbox(checked = selected, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun V27Demands(s: AppState, vm: MonitorViewModel) {
    var vehicle by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    val lastRun = s.demands.maxOfOrNull { it.lastCheckedAt } ?: 0L
    val checked = s.demands.count { it.lastCheckedAt > 0 }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, top = 10.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(color = V27Surface, shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, V27Divider.copy(alpha = .7f)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(V27Amber.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.NotificationsActive, null, tint = V27Amber)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${s.demands.size} demanda(s) ativa(s)", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("$checked já verificadas", color = V27Text2, fontSize = 11.5.sp)
                        }
                        Button(onClick = vm::searchAllDemandsNow, enabled = !s.demandSearchBusy && s.demands.isNotEmpty(), shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (s.demandSearchBusy) "Buscando" else "Buscar todas", fontSize = 12.sp)
                        }
                    }
                    if (lastRun > 0) {
                        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = V27Divider)
                        Text("Última varredura: ${v27DateTime(lastRun)}", color = V27Text2, fontSize = 11.5.sp)
                    }
                }
            }
        }

        item {
            Surface(color = V27Surface, shape = RoundedCornerShape(17.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Nova demanda", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(vehicle, { vehicle = it }, label = { Text("Veículo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(subject, { subject = it }, label = { Text("Assunto") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { vm.addDemand(vehicle, subject); vehicle = ""; subject = "" },
                        enabled = vehicle.isNotBlank() && subject.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.Add, null)
                        Spacer(Modifier.width(7.dp))
                        Text("Adicionar demanda")
                    }
                }
            }
        }

        if (s.demands.isEmpty()) {
            item { V27Empty("Nenhuma demanda cadastrada", "Adicione um veículo e um assunto para monitorar.") }
        } else {
            items(s.demands, key = { it.id }) { d -> V27DemandCard(d, s, vm) }
        }
    }
}

@Composable
private fun V27DemandCard(d: Demand, s: AppState, vm: MonitorViewModel) {
    val busy = s.demandSearchBusy || s.demandBusyId == d.id
    val color = when {
        busy -> V27Accent
        d.lastError.isNotBlank() -> V27Red
        d.lastCheckedAt > 0 -> V27Mint
        else -> V27Text2
    }
    Surface(color = V27Surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, V27Divider.copy(alpha = .7f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(V27Amber.copy(alpha = .11f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.NotificationsActive, null, tint = V27Amber, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.vehicle, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(d.subject, color = V27Text2, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { vm.searchDemandNow(d.id) }, enabled = !busy) { Icon(Icons.Outlined.Search, "Buscar", tint = V27Accent) }
                IconButton(onClick = { vm.removeDemand(d.id) }, enabled = !busy) { Icon(Icons.Outlined.DeleteOutline, "Excluir", tint = V27Text2) }
            }
            HorizontalDivider(Modifier.padding(vertical = 9.dp), color = V27Divider)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when {
                        busy -> Icons.Outlined.HourglassTop
                        d.lastError.isNotBlank() -> Icons.Outlined.ErrorOutline
                        d.lastCheckedAt > 0 -> Icons.Outlined.CheckCircle
                        else -> Icons.Outlined.Schedule
                    }, null, tint = color, modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    when {
                        busy -> "Pesquisando agora..."
                        d.lastError.isNotBlank() -> "Falha em ${v27DateTime(d.lastCheckedAt)}"
                        d.lastCheckedAt > 0 -> "${v27DateTime(d.lastCheckedAt)} • ${d.lastFoundCount} resultado(s) • ${d.lastNewCount} nova(s)"
                        else -> "Ainda não pesquisada"
                    },
                    color = color,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun V27History(s: AppState, vm: MonitorViewModel) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, top = 10.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${s.history.size} matéria(s) armazenada(s)", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Seu arquivo completo de monitoramento", color = V27Text2, fontSize = 11.5.sp)
                }
                OutlinedButton(onClick = { v27ExportCsv(context, s.history) }, enabled = s.history.isNotEmpty()) {
                    Icon(Icons.Outlined.IosShare, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("Exportar")
                }
                Spacer(Modifier.width(6.dp))
                FilledTonalIconButton(onClick = vm::clearHistory, enabled = s.history.isNotEmpty()) { Icon(Icons.Outlined.DeleteOutline, "Limpar") }
            }
        }
        if (s.history.isEmpty()) {
            item { V27Empty("Histórico vazio", "As matérias encontradas aparecerão aqui.") }
        } else {
            items(s.history, key = { it.link }) { V27NewsCard(it) }
        }
    }
}

@Composable
private fun V27Terms(s: AppState, vm: MonitorViewModel) {
    var term by remember { mutableStateOf("") }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, top = 10.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item {
            Surface(color = V27Surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Adicionar termo", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(9.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(term, { term = it }, label = { Text("Novo termo") }, singleLine = true, modifier = Modifier.weight(1f))
                        FilledIconButton(onClick = { vm.addTerm(term); term = "" }, enabled = term.isNotBlank(), modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Outlined.Add, "Adicionar")
                        }
                    }
                }
            }
        }
        item {
            Text("${s.terms.size} termo(s) monitorado(s)", color = V27Text2, fontSize = 12.sp)
        }
        if (s.terms.isEmpty()) {
            item { V27Empty("Nenhum termo", "Adicione palavras para iniciar o monitoramento.") }
        } else {
            items(s.terms) { item ->
                Surface(color = V27Surface, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, V27Divider.copy(alpha = .7f))) {
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Search, null, tint = V27Accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(item, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.removeTerm(item) }) { Icon(Icons.Outlined.Close, "Excluir", tint = V27Text2) }
                    }
                }
            }
        }
    }
}

@Composable
private fun V27Settings(s: AppState, vm: MonitorViewModel) {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }
    val prefs = remember(refreshKey) { context.getSharedPreferences(BackgroundMonitor.PREFS, 0) }
    val newsAttempt = remember(refreshKey) { prefs.getLong(AutoRunLog.KEY_NEWS_ATTEMPT_AT, 0L) }
    val newsCompleted = remember(refreshKey) { prefs.getLong(AutoRunLog.KEY_NEWS_COMPLETED_AT, 0L) }
    val newsFound = remember(refreshKey) { prefs.getInt(AutoRunLog.KEY_NEWS_FOUND, 0) }
    val newsNew = remember(refreshKey) { prefs.getInt(AutoRunLog.KEY_NEWS_NEW, 0) }
    val newsErrors = remember(refreshKey) { prefs.getInt(AutoRunLog.KEY_NEWS_ERRORS, 0) }
    val newsError = remember(refreshKey) { prefs.getString(AutoRunLog.KEY_NEWS_ERROR_TEXT, "").orEmpty() }
    val demandAttempt = remember(refreshKey) { prefs.getLong(AutoRunLog.KEY_DEMAND_ATTEMPT_AT, 0L) }
    val demandCompleted = remember(refreshKey) { prefs.getLong(AutoRunLog.KEY_DEMAND_COMPLETED_AT, 0L) }
    val demandChecked = remember(refreshKey) { prefs.getInt(AutoRunLog.KEY_DEMAND_CHECKED, 0) }
    val demandFound = remember(refreshKey) { prefs.getInt(AutoRunLog.KEY_DEMAND_FOUND, 0) }
    val demandNew = remember(refreshKey) { prefs.getInt(AutoRunLog.KEY_DEMAND_NEW, 0) }
    val demandErrors = remember(refreshKey) { prefs.getInt(AutoRunLog.KEY_DEMAND_ERRORS, 0) }
    val demandError = remember(refreshKey) { prefs.getString(AutoRunLog.KEY_DEMAND_ERROR_TEXT, "").orEmpty() }
    val nextHeartbeat = remember(refreshKey) { prefs.getLong(AutoRunLog.KEY_NEXT_HEARTBEAT_AT, 0L) }
    val powerManager = remember { context.getSystemService(PowerManager::class.java) }
    val unrestricted = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    val latestAttempt = maxOf(newsAttempt, demandAttempt)
    val stale = latestAttempt > 0 && System.currentTimeMillis() - latestAttempt > 2L * 60L * 60L * 1000L

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, top = 10.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(
                color = if (stale) V27Amber.copy(alpha = .09f) else V27Mint.copy(alpha = .08f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, if (stale) V27Amber.copy(alpha = .26f) else V27Mint.copy(alpha = .22f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background((if (stale) V27Amber else V27Mint).copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                        Icon(if (stale) Icons.Outlined.WarningAmber else Icons.Outlined.VerifiedUser, null, tint = if (stale) V27Amber else V27Mint, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (stale) "Atenção ao monitor" else "Monitor funcionando", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (latestAttempt == 0L) "Ainda aguardando a primeira execução automática" else "Último disparo: ${v27DateTime(latestAttempt)}",
                            color = V27Text2,
                            fontSize = 11.5.sp
                        )
                        if (nextHeartbeat > 0) Text("Próximo controle: ${v27DateTime(nextHeartbeat)}", color = V27Text2, fontSize = 10.5.sp)
                    }
                    IconButton(onClick = { refreshKey++ }) { Icon(Icons.Outlined.Refresh, "Atualizar") }
                }
            }
        }

        item { V27ReportCard("Notícias automáticas", Icons.Outlined.Article, V27Accent, newsAttempt, newsCompleted, "$newsFound resultado(s) • $newsNew nova(s) • $newsErrors falha(s)", newsError) }
        item { V27ReportCard("Demandas automáticas", Icons.Outlined.NotificationsActive, V27Mint, demandAttempt, demandCompleted, "$demandChecked demanda(s) • $demandFound resultado(s) • $demandNew nova(s) • $demandErrors falha(s)", demandError) }

        item {
            Surface(color = V27Surface, shape = RoundedCornerShape(17.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Schedule, null, tint = V27Accent)
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Intervalo de notícias", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Atual: ${s.intervalMinutes} minutos", color = V27Text2, fontSize = 11.5.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf(15, 30, 45, 60).forEach { minutes -> V27Chip("$minutes min", s.intervalMinutes == minutes) { vm.setInterval(minutes) } }
                    }
                }
            }
        }

        item {
            Surface(color = V27Surface, shape = RoundedCornerShape(17.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.BatterySaver, null, tint = if (unrestricted) V27Mint else V27Amber)
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Bateria e segundo plano", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(if (unrestricted) "Sem restrição de bateria detectada" else "O Samsung pode atrasar tarefas em repouso", color = V27Text2, fontSize = 11.5.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.SettingsSuggest, null)
                        Spacer(Modifier.width(7.dp))
                        Text("Abrir ajustes do aplicativo")
                    }
                }
            }
        }

        item {
            Surface(color = V27Surface, shape = RoundedCornerShape(17.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Public, null, tint = V27Mint)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Fontes", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(if (s.searchAllSources) "Qualquer veículo" else "${s.selectedSourceIds.size} selecionada(s)", color = V27Text2, fontSize = 11.5.sp)
                    }
                    Icon(Icons.Outlined.ChevronRight, null, tint = V27Text2)
                }
            }
        }

        item {
            Surface(color = V27Accent.copy(alpha = .07f), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Monitor de Notícias 2.7.0", color = V27Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Nova experiência visual profissional, navegação inferior segura e dashboard de monitoramento.", color = V27Text2, fontSize = 11.5.sp, lineHeight = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun V27ReportCard(title: String, icon: ImageVector, color: Color, attemptAt: Long, completedAt: Long, summary: String, error: String) {
    Surface(color = V27Surface, shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, V27Divider.copy(alpha = .7f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(color.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(if (error.isNotBlank()) Icons.Outlined.ErrorOutline else if (completedAt > 0) Icons.Outlined.CheckCircle else Icons.Outlined.Schedule, null, tint = if (error.isNotBlank()) V27Red else if (completedAt > 0) V27Mint else V27Text2)
            }
            Spacer(Modifier.height(10.dp))
            Text(if (attemptAt > 0) "Última tentativa: ${v27DateTime(attemptAt)}" else "Última tentativa: —", color = V27Text2, fontSize = 11.5.sp)
            Text(if (completedAt > 0) "Última conclusão: ${v27DateTime(completedAt)}" else "Última conclusão: —", color = V27Text2, fontSize = 11.5.sp)
            if (completedAt > 0) {
                Spacer(Modifier.height(5.dp))
                Text(summary, color = color, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
            if (error.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(error, color = V27Red, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun V27Segment(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        color = if (selected) V27Selected else V27Surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (selected) V27Accent.copy(alpha = .45f) else V27Divider),
        modifier = modifier.height(42.dp).clickable(onClick = onClick)
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (selected) V27Accent else V27Text2, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = if (selected) V27Accent else V27Text2, fontSize = 11.5.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@Composable
private fun V27Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) V27Accent.copy(alpha = .14f) else V27Surface,
        shape = RoundedCornerShape(11.dp),
        border = BorderStroke(1.dp, if (selected) V27Accent.copy(alpha = .40f) else V27Divider),
        modifier = Modifier.height(34.dp).clickable(onClick = onClick)
    ) {
        Box(Modifier.padding(horizontal = 11.dp), contentAlignment = Alignment.Center) {
            Text(label, color = if (selected) V27Accent else V27Text2, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@Composable
private fun V27QuickAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(color = V27Surface, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, V27Divider), modifier = Modifier.height(40.dp).clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = V27Accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun V27Badge(label: String, color: Color) {
    Surface(color = color.copy(alpha = .12f), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, color.copy(alpha = .28f))) {
        Text(label, color = color, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun V27Empty(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(V27Surface), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.SearchOff, null, tint = V27Accent, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(subtitle, color = V27Text2, fontSize = 11.5.sp)
    }
}

@Composable
private fun V27MoreItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Bold) },
        supportingContent = { Text(subtitle, color = V27Text2) },
        leadingContent = {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(V27Accent.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = V27Accent)
            }
        },
        trailingContent = { Icon(Icons.Outlined.ChevronRight, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

private fun v27DateTime(ms: Long): String = if (ms <= 0) "—" else SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(Date(ms))
private fun v27Time(ms: Long): String = SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date(ms))
private fun v27NewsDate(ms: Long): String = SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(Date(ms))
private fun v27ParseDateTime(date: String, time: String): Long? = runCatching {
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).apply { isLenient = false }.parse("$date $time")?.time
}.getOrNull()

private fun v27CleanSnippet(value: String): String = value
    .replace("&nbsp;", " ", ignoreCase = true)
    .replace("&amp;", "&", ignoreCase = true)
    .replace(Regex("<[^>]+>"), " ")
    .replace(Regex("\\s+"), " ")
    .trim()

private fun v27ExportCsv(context: android.content.Context, items: List<News>) {
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
