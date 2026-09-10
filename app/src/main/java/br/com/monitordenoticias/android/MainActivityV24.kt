package br.com.monitordenoticias.android

import android.content.Intent
import android.os.Bundle
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

private val V24Bg = Color(0xFF0B0D12)
private val V24Surface = Color(0xFF151820)
private val V24Surface2 = Color(0xFF1B1F29)
private val V24Selected = Color(0xFF1D2940)
private val V24Accent = Color(0xFF6EA8FE)
private val V24Mint = Color(0xFF5BD8B2)
private val V24Amber = Color(0xFFFFB86B)
private val V24Text = Color(0xFFF4F6FA)
private val V24Muted = Color(0xFFAEB4C2)
private val V24Divider = Color(0xFF272C37)

private val V24Colors = darkColorScheme(
    primary = V24Accent,
    onPrimary = Color(0xFF10213B),
    secondary = V24Mint,
    background = V24Bg,
    surface = V24Surface,
    surfaceVariant = V24Surface2,
    onBackground = V24Text,
    onSurface = V24Text,
    onSurfaceVariant = V24Muted
)

class MainActivityV24 : ComponentActivity() {
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch("android.permission.POST_NOTIFICATIONS")
        }
        setContent {
            MaterialTheme(colorScheme = V24Colors) {
                MonitorAppV24()
            }
        }
    }
}

private data class V24Tab(val label: String, val icon: ImageVector)

@Composable
private fun MonitorAppV24(vm: MonitorViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val tabs = remember {
        listOf(
            V24Tab("Notícias", Icons.Outlined.Article),
            V24Tab("Período", Icons.Outlined.DateRange),
            V24Tab("Demandas", Icons.Outlined.NotificationsActive),
            V24Tab("Histórico", Icons.Outlined.History),
            V24Tab("Termos", Icons.Outlined.ManageSearch),
            V24Tab("Fontes", Icons.Outlined.Public),
            V24Tab("Config.", Icons.Outlined.Settings)
        )
    }

    Scaffold(
        containerColor = V24Bg,
        topBar = {
            Column(
                Modifier
                    .background(V24Bg)
                    .statusBarsPadding()
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(V24Accent.copy(alpha = .12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Radar, null, tint = V24Accent, modifier = Modifier.size(17.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Monitor de Notícias",
                        fontSize = 15.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text("2.4.0", color = V24Accent, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val active = state.selectedTab == index
                        Surface(
                            color = if (active) V24Selected else Color.Transparent,
                            shape = RoundedCornerShape(9.dp),
                            border = if (active) BorderStroke(1.dp, V24Accent.copy(alpha = .32f)) else null,
                            modifier = Modifier.clickable { vm.setTab(index) }
                        ) {
                            Row(
                                Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    tab.icon,
                                    tab.label,
                                    tint = if (active) V24Accent else V24Muted,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    tab.label,
                                    fontSize = 9.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    color = if (active) V24Text else V24Muted
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = V24Divider)
            }
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .background(V24Bg)
        ) {
            if (state.status.isNotBlank() && state.status != "Pronto") {
                V24Status(state.status)
            }
            when (state.selectedTab) {
                0 -> V24NewsScreen(state, vm)
                1 -> V24PeriodScreen(state, vm)
                2 -> V24DemandScreen(state, vm)
                3 -> V24HistoryScreen(state, vm)
                4 -> V24TermsScreen(state, vm)
                5 -> V24SourcesScreen(state, vm)
                else -> V24SettingsScreen(state, vm)
            }
        }
    }
}

@Composable
private fun V24Status(text: String) {
    val warning = text.startsWith("⚠")
    Surface(
        color = if (warning) V24Amber.copy(alpha = .08f) else V24Accent.copy(alpha = .05f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (warning) Icons.Outlined.WarningAmber else Icons.Outlined.Info,
                null,
                tint = if (warning) V24Amber else V24Accent,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text.removePrefix("⚠ "),
                color = V24Muted,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun V24Title(title: String, subtitle: String? = null) {
    Column {
        Text(title, fontSize = 16.sp, lineHeight = 18.sp, fontWeight = FontWeight.ExtraBold)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, color = V24Muted, fontSize = 9.sp, lineHeight = 11.sp)
        }
    }
}

@Composable
private fun V24NewsScreen(s: AppState, vm: MonitorViewModel) {
    val shown = if (s.showOnlyDemands) s.news.filter { it.demand } else s.news

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                V24Title("Últimas 24 horas")
                Text(
                    if (s.searchAllSources) "Qualquer veículo" else "${s.selectedSourceIds.size} fonte(s) selecionada(s)",
                    color = V24Muted,
                    fontSize = 8.8.sp
                )
            }
            Button(
                onClick = vm::search,
                enabled = !s.busy,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (s.busy) "Buscando" else "Buscar", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            V24Metric("Notícias", s.news.size, V24Accent, Modifier.weight(1f))
            V24Metric("Demandas", s.news.count { it.demand }, V24Amber, Modifier.weight(1f))
            V24Metric("Fontes", s.news.map { it.source }.distinct().size, V24Mint, Modifier.weight(1f))
        }

        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            V24Chip("Todas", !s.showOnlyDemands) { vm.setDemandFilter(false) }
            V24Chip("Demandas", s.showOnlyDemands) { vm.setDemandFilter(true) }
            Spacer(Modifier.weight(1f))
            s.lastUpdatedAt?.let { Text(v24Time(it), color = V24Muted, fontSize = 8.sp) }
        }

        Spacer(Modifier.height(6.dp))
        V24NewsList(shown)
    }
}

@Composable
private fun V24Metric(label: String, value: Int, color: Color, modifier: Modifier) {
    Surface(color = V24Surface, shape = RoundedCornerShape(10.dp), modifier = modifier) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(value.toString(), color = color, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(4.dp))
            Text(label, color = V24Muted, fontSize = 8.sp)
        }
    }
}

@Composable
private fun V24NewsList(news: List<News>) {
    if (news.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            V24Empty("Nenhuma notícia no escopo atual", "Busque novamente ou altere as fontes.")
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            items(news, key = { it.link }) { V24NewsCard(it) }
        }
    }
}

@Composable
private fun V24NewsCard(news: News) {
    val context = LocalContext.current
    Surface(
        color = V24Surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, V24Divider.copy(alpha = .7f)),
        modifier = Modifier.fillMaxWidth().clickable {
            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(news.link)))
        }
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    news.source,
                    color = V24Accent,
                    fontSize = 8.8.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(v24Date(news.date), color = V24Muted, fontSize = 7.8.sp)
            }
            Spacer(Modifier.height(3.dp))
            Text(
                news.title,
                fontSize = 11.5.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (news.snippet.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    v24Clean(news.snippet),
                    color = V24Muted,
                    fontSize = 8.8.sp,
                    lineHeight = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (news.demand || news.matchedTerm.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (news.demand) V24Badge("DEMANDA", V24Amber)
                    if (news.matchedTerm.isNotBlank()) V24Badge(news.matchedTerm, V24Accent)
                }
            }
        }
    }
}

@Composable
private fun V24Badge(text: String, color: Color) {
    Surface(color = color.copy(alpha = .10f), shape = RoundedCornerShape(6.dp)) {
        Text(
            text,
            color = color,
            fontSize = 7.2.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun V24PeriodScreen(s: AppState, vm: MonitorViewModel) {
    val start = v24Parse(s.periodStartDate, s.periodStartTime)
    val end = v24Parse(s.periodEndDate, s.periodEndTime)
    val valid = start != null && end != null && start < end

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 7.dp, bottom = 70.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item { V24Title("Período", "Data e hora da pesquisa") }
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    V24ActionChip("Hoje", Icons.Outlined.Today) { vm.applyPeriodPreset(0) }
                    V24ActionChip("24 horas", Icons.Outlined.Schedule) { vm.applyPeriodPreset(1) }
                    V24ActionChip("7 dias", Icons.Outlined.DateRange) { vm.applyPeriodPreset(7) }
                    V24ActionChip("30 dias", Icons.Outlined.CalendarMonth) { vm.applyPeriodPreset(30) }
                }
            }
            item {
                Surface(color = V24Surface, shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(9.dp)) {
                        V24DateTimeRow("Início", V24Accent, s.periodStartDate, s.periodStartTime, vm::setPeriodStartDate, vm::setPeriodStartTime)
                        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = V24Divider)
                        V24DateTimeRow("Fim", V24Mint, s.periodEndDate, s.periodEndTime, vm::setPeriodEndDate, vm::setPeriodEndTime)
                    }
                }
            }
            item {
                Surface(
                    color = if (valid) V24Mint.copy(alpha = .07f) else V24Amber.copy(alpha = .08f),
                    shape = RoundedCornerShape(9.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (valid) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
                            null,
                            tint = if (valid) V24Mint else V24Amber,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            if (valid) "Período válido e salvo" else "Revise data e hora",
                            color = if (valid) V24Mint else V24Amber,
                            fontSize = 8.8.sp
                        )
                    }
                }
            }
            if (s.news.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Resultados", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("${s.news.size} matéria(s)", color = V24Muted, fontSize = 8.5.sp)
                    }
                }
                items(s.news, key = { it.link }) { V24NewsCard(it) }
            }
        }

        V24BottomButton(
            text = if (s.busy) "Pesquisando..." else "Pesquisar período",
            icon = Icons.Outlined.Search,
            enabled = valid && !s.busy,
            modifier = Modifier.align(Alignment.BottomCenter),
            onClick = vm::searchSavedPeriod
        )
    }
}

@Composable
private fun V24DateTimeRow(
    title: String,
    color: Color,
    date: String,
    time: String,
    onDate: (String) -> Unit,
    onTime: (String) -> Unit
) {
    Text(title, color = color, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(3.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = date,
            onValueChange = onDate,
            label = { Text("Data", fontSize = 8.sp) },
            placeholder = { Text("dd/MM/aaaa", fontSize = 9.sp) },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 11.sp),
            modifier = Modifier.weight(1.55f)
        )
        OutlinedTextField(
            value = time,
            onValueChange = onTime,
            label = { Text("Hora", fontSize = 8.sp) },
            placeholder = { Text("HH:mm", fontSize = 9.sp) },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 11.sp),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun V24SourcesScreen(s: AppState, vm: MonitorViewModel) {
    var section by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var region by remember { mutableStateOf(SourceCatalog.ALL_REGION) }
    var state by remember { mutableStateOf("") }

    val base = when (section) {
        0 -> SourceCatalog.national
        1 -> SourceCatalog.byState
        else -> emptyList()
    }
    val visible = base.filter { source ->
        val regionOk = section != 1 || region == SourceCatalog.ALL_REGION || source.region == region
        val stateOk = section != 1 || state.isBlank() || source.state == state
        val queryOk = query.isBlank() || listOf(source.name, source.group, source.region, source.stateName, source.state)
            .plus(source.aliases)
            .any { it.contains(query.trim(), ignoreCase = true) }
        regionOk && stateOk && queryOk
    }
    val visibleIds = visible.map { it.id }.toSet()
    val selectedVisible = visibleIds.count { it in s.selectedSourceIds }
    val states = SourceCatalog.states.filter { region == SourceCatalog.ALL_REGION || it.third == region }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 70.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            item {
                Surface(
                    color = if (s.searchAllSources) V24Mint.copy(alpha = .07f) else V24Surface,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (s.searchAllSources) V24Mint.copy(alpha = .25f) else V24Divider)
                ) {
                    Row(Modifier.padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Language, null, tint = V24Mint, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Buscar em todos os veículos", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("Inclui portais locais e menores", color = V24Muted, fontSize = 8.sp)
                        }
                        Switch(checked = s.searchAllSources, onCheckedChange = vm::setSearchAllSources)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    V24Segment("Nacionais", Icons.Outlined.Newspaper, section == 0, Modifier.weight(1f)) {
                        section = 0; state = ""
                    }
                    V24Segment("Estados", Icons.Outlined.Map, section == 1, Modifier.weight(1f)) {
                        section = 1
                    }
                    V24Segment("Qualquer", Icons.Outlined.TravelExplore, section == 2, Modifier.weight(1f)) {
                        section = 2; vm.setSearchAllSources(true)
                    }
                }
            }

            if (section == 2) {
                item {
                    Surface(color = V24Surface, shape = RoundedCornerShape(10.dp)) {
                        Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.TravelExplore, null, tint = V24Accent, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Pesquisa aberta em qualquer veículo indexado.", color = V24Muted, fontSize = 8.8.sp)
                        }
                    }
                }
            } else {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Pesquisar veículo", fontSize = 9.5.sp) },
                        leadingIcon = { Icon(Icons.Outlined.Search, null, modifier = Modifier.size(16.dp)) },
                        trailingIcon = {
                            if (query.isNotBlank()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Outlined.Close, "Limpar", modifier = Modifier.size(15.dp))
                                }
                            }
                        },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 10.5.sp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (section == 1) {
                    item {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            SourceCatalog.regions.forEach { item ->
                                V24Chip(item, region == item) { region = item; state = "" }
                            }
                        }
                    }
                    item {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            V24Chip("Todos", state.isBlank()) { state = "" }
                            states.forEach { item -> V24Chip(item.first, state == item.first) { state = item.first } }
                        }
                    }
                }

                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${visible.size} veículos • $selectedVisible selecionados", color = V24Muted, fontSize = 8.5.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.setVisibleSources(visibleIds, true) }, contentPadding = PaddingValues(horizontal = 5.dp)) {
                            Text("Selecionar", fontSize = 8.5.sp)
                        }
                        TextButton(onClick = { vm.setVisibleSources(visibleIds, false) }, contentPadding = PaddingValues(horizontal = 5.dp)) {
                            Text("Limpar", fontSize = 8.5.sp)
                        }
                    }
                }

                if (visible.isEmpty()) {
                    item { V24Empty("Nenhuma fonte encontrada", "Altere a pesquisa ou os filtros.") }
                } else {
                    items(visible, key = { it.id }) { source ->
                        V24SourceCard(source, source.id in s.selectedSourceIds) {
                            vm.setSourceSelected(source.id, it)
                        }
                    }
                }
            }
        }

        Surface(
            color = V24Bg,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (s.searchAllSources) "Busca aberta" else "${s.selectedSourceIds.size} fonte(s)", fontSize = 9.8.sp, fontWeight = FontWeight.Bold)
                    Text(if (s.searchAllSources) "qualquer veículo" else "selecionada(s)", color = V24Muted, fontSize = 7.8.sp)
                }
                Button(
                    onClick = vm::search,
                    enabled = !s.busy && (s.searchAllSources || s.selectedSourceIds.isNotEmpty()),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Outlined.Search, null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(if (s.busy) "Buscando" else "Buscar agora", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun V24SourceCard(source: MediaSource, selected: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        color = if (selected) V24Selected else V24Surface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (selected) V24Accent.copy(alpha = .38f) else V24Divider),
        modifier = Modifier.fillMaxWidth().clickable { onChange(!selected) }
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(27.dp).clip(RoundedCornerShape(8.dp)).background(if (selected) V24Accent.copy(alpha = .14f) else V24Surface2),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (source.region == SourceCatalog.NATIONAL_REGION) Icons.Outlined.Newspaper else Icons.Outlined.LocationOn,
                    null,
                    tint = if (selected) V24Accent else V24Muted,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(Modifier.width(7.dp))
            Column(Modifier.weight(1f)) {
                Text(source.name, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (source.region == SourceCatalog.NATIONAL_REGION) "Nacional" else "${source.state} • ${source.region}",
                    color = V24Muted,
                    fontSize = 7.8.sp,
                    maxLines = 1
                )
            }
            Checkbox(checked = selected, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun V24Segment(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        color = if (selected) V24Selected else V24Surface,
        shape = RoundedCornerShape(9.dp),
        border = BorderStroke(1.dp, if (selected) V24Accent.copy(alpha = .35f) else V24Divider),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 7.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (selected) V24Accent else V24Muted, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 8.5.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) V24Accent else V24Muted)
        }
    }
}

@Composable
private fun V24DemandScreen(s: AppState, vm: MonitorViewModel) {
    var vehicle by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item { V24Title("Demandas", "Alertas por veículo e assunto") }
        item {
            OutlinedTextField(vehicle, { vehicle = it }, label = { Text("Veículo", fontSize = 8.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.5.sp), modifier = Modifier.fillMaxWidth())
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(subject, { subject = it }, label = { Text("Assunto", fontSize = 8.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.5.sp), modifier = Modifier.weight(1f))
                FilledIconButton(onClick = { vm.addDemand(vehicle, subject); vehicle = ""; subject = "" }, enabled = vehicle.isNotBlank() && subject.isNotBlank()) {
                    Icon(Icons.Outlined.Add, "Adicionar")
                }
            }
        }
        items(s.demands, key = { it.id }) { demand ->
            Surface(color = V24Surface, shape = RoundedCornerShape(10.dp)) {
                Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.NotificationsActive, null, tint = V24Amber, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) {
                        Text(demand.vehicle, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        Text(demand.subject, color = V24Muted, fontSize = 8.3.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { vm.removeDemand(demand.id) }) { Icon(Icons.Outlined.DeleteOutline, "Excluir", modifier = Modifier.size(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun V24HistoryScreen(s: AppState, vm: MonitorViewModel) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Spacer(Modifier.height(7.dp))
        V24Title("Histórico", "${s.history.size} matéria(s)")
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            OutlinedButton(onClick = { v24Export(context, s.history) }, enabled = s.history.isNotEmpty(), modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.IosShare, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Exportar", fontSize = 8.8.sp)
            }
            OutlinedButton(onClick = vm::clearHistory, enabled = s.history.isNotEmpty(), modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.DeleteOutline, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Limpar", fontSize = 8.8.sp)
            }
        }
        Spacer(Modifier.height(5.dp))
        V24NewsList(s.history)
    }
}

@Composable
private fun V24TermsScreen(s: AppState, vm: MonitorViewModel) {
    var term by remember { mutableStateOf("") }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        item { V24Title("Termos", "Palavras usadas no monitoramento") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(term, { term = it }, label = { Text("Novo termo", fontSize = 8.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.5.sp), modifier = Modifier.weight(1f))
                FilledIconButton(onClick = { vm.addTerm(term); term = "" }, enabled = term.isNotBlank()) { Icon(Icons.Outlined.Add, "Adicionar") }
            }
        }
        items(s.terms) { item ->
            Surface(color = V24Surface, shape = RoundedCornerShape(9.dp)) {
                Row(Modifier.padding(start = 9.dp, end = 2.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Search, null, tint = V24Accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(item, fontSize = 9.8.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { vm.removeTerm(item) }) { Icon(Icons.Outlined.Close, "Excluir", modifier = Modifier.size(15.dp)) }
                }
            }
        }
    }
}

@Composable
private fun V24SettingsScreen(s: AppState, vm: MonitorViewModel) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item { V24Title("Configurações", "Ajustes do monitoramento") }
        item {
            Surface(color = V24Surface, shape = RoundedCornerShape(11.dp)) {
                Column(Modifier.padding(9.dp)) {
                    Text("Intervalo automático", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    Text("Atual: ${s.intervalMinutes} min", color = V24Muted, fontSize = 8.2.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(15, 30, 45, 60).forEach { minutes -> V24Chip("$minutes min", s.intervalMinutes == minutes) { vm.setInterval(minutes) } }
                    }
                }
            }
        }
        item {
            Surface(color = V24Surface, shape = RoundedCornerShape(11.dp)) {
                Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Source, null, tint = V24Mint, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text("Fontes", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        Text(if (s.searchAllSources) "Qualquer veículo" else "${s.selectedSourceIds.size} selecionada(s)", color = V24Muted, fontSize = 8.2.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun V24Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) V24Accent.copy(alpha = .13f) else V24Surface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (selected) V24Accent.copy(alpha = .35f) else V24Divider),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(label, color = if (selected) V24Accent else V24Muted, fontSize = 8.2.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp))
    }
}

@Composable
private fun V24ActionChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(color = V24Surface, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, V24Divider), modifier = Modifier.clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 7.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = V24Accent, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(4.dp)); Text(label, fontSize = 8.5.sp)
        }
    }
}

@Composable
private fun V24BottomButton(text: String, icon: ImageVector, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(color = V24Bg, modifier = modifier.fillMaxWidth().navigationBarsPadding()) {
        Button(onClick = onClick, enabled = enabled, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp).fillMaxWidth(), shape = RoundedCornerShape(11.dp)) {
            Icon(icon, null, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(5.dp)); Text(text, fontSize = 9.8.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun V24Empty(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(V24Surface), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.SearchOff, null, tint = V24Accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(5.dp))
        Text(title, fontSize = 10.8.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = V24Muted, fontSize = 8.4.sp)
    }
}

private fun v24Parse(date: String, time: String): Long? = runCatching {
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).apply { isLenient = false }.parse("$date $time")?.time
}.getOrNull()

private fun v24Date(ms: Long): String = SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(Date(ms))
private fun v24Time(ms: Long): String = SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date(ms))
private fun v24Clean(value: String): String = value.replace("&nbsp;", " ").replace("&amp;", "&").replace(Regex("\\s+"), " ").trim()

private fun v24Export(context: android.content.Context, items: List<News>) {
    fun clean(value: String) = value.replace(";", ",").replace("\n", " ")
    val format = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
    val csv = buildString {
        append("Publicação;Captura;Veículo;Título;Termo;Demanda;Link\n")
        items.forEach {
            append(format.format(Date(it.date))).append(';')
                .append(format.format(Date(it.capturedAt))).append(';')
                .append(clean(it.source)).append(';')
                .append(clean(it.title)).append(';')
                .append(clean(it.matchedTerm)).append(';')
                .append(clean(it.matchedDemand)).append(';')
                .append(it.link).append('\n')
        }
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_TEXT, csv)
    }
    context.startActivity(Intent.createChooser(send, "Exportar CSV"))
}
