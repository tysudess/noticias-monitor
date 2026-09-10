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

private val AppBg = Color(0xFF0C0E13)
private val SurfaceDark = Color(0xFF151820)
private val SurfaceRaised = Color(0xFF1B1F29)
private val SurfaceSelected = Color(0xFF1D2940)
private val Accent = Color(0xFF6EA8FE)
private val Mint = Color(0xFF5BD8B2)
private val Amber = Color(0xFFFFB86B)
private val RedSoft = Color(0xFFFF7777)
private val TextPrimary = Color(0xFFF4F6FA)
private val TextSecondary = Color(0xFFAEB4C2)
private val Divider = Color(0xFF262A35)

private val AppColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF10213B),
    secondary = Mint,
    background = AppBg,
    surface = SurfaceDark,
    surfaceVariant = SurfaceRaised,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary
)

class MainActivity : ComponentActivity() {
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch("android.permission.POST_NOTIFICATIONS")
        }
        setContent {
            MaterialTheme(colorScheme = AppColors) {
                MonitorApp()
            }
        }
    }
}

private data class AppTab(val label: String, val icon: ImageVector)

@Composable
fun MonitorApp(vm: MonitorViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val tabs = remember {
        listOf(
            AppTab("Notícias", Icons.Outlined.Article),
            AppTab("Período", Icons.Outlined.DateRange),
            AppTab("Demandas", Icons.Outlined.NotificationsActive),
            AppTab("Histórico", Icons.Outlined.History),
            AppTab("Termos", Icons.Outlined.ManageSearch),
            AppTab("Fontes", Icons.Outlined.Public),
            AppTab("Config.", Icons.Outlined.Settings)
        )
    }

    Scaffold(
        containerColor = AppBg,
        topBar = {
            CompactAppHeader(
                tabs = tabs,
                selected = state.selectedTab,
                onSelect = vm::setTab
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .background(AppBg)
        ) {
            if (state.status.isNotBlank() && state.status != "Pronto") {
                StatusStrip(state.status)
            }
            when (state.selectedTab) {
                0 -> NewsScreen(state, vm)
                1 -> PeriodScreen(state, vm)
                2 -> DemandScreen(state, vm)
                3 -> HistoryScreen(state, vm)
                4 -> TermsScreen(state, vm)
                5 -> SourcesScreen(state, vm)
                else -> SettingsScreen(state, vm)
            }
        }
    }
}

@Composable
private fun CompactAppHeader(
    tabs: List<AppTab>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Surface(
        color = AppBg,
        tonalElevation = 0.dp,
        modifier = Modifier.statusBarsPadding()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Accent.copy(alpha = .12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Radar, null, tint = Accent, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "Monitor de Notícias",
                    fontSize = 15.5.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "2.4.0",
                    color = Accent,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tabs.forEachIndexed { index, tab ->
                    val active = selected == index
                    Surface(
                        color = if (active) SurfaceSelected else Color.Transparent,
                        shape = RoundedCornerShape(10.dp),
                        border = if (active) BorderStroke(1.dp, Accent.copy(alpha = .30f)) else null,
                        modifier = Modifier
                            .height(31.dp)
                            .clickable { onSelect(index) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                tab.icon,
                                tab.label,
                                tint = if (active) Accent else TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                tab.label,
                                fontSize = 9.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = if (active) TextPrimary else TextSecondary
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = Divider)
        }
    }
}

@Composable
private fun StatusStrip(text: String) {
    val warning = text.startsWith("⚠")
    Surface(
        color = if (warning) Amber.copy(alpha = .08f) else Accent.copy(alpha = .05f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (warning) Icons.Outlined.WarningAmber else Icons.Outlined.Info,
                null,
                tint = if (warning) Amber else Accent,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text.removePrefix("⚠ "),
                color = TextSecondary,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, lineHeight = 18.sp, fontWeight = FontWeight.ExtraBold)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, color = TextSecondary, fontSize = 9.5.sp, lineHeight = 12.sp)
            }
        }
    }
}

@Composable
fun NewsScreen(s: AppState, vm: MonitorViewModel) {
    val shown = if (s.showOnlyDemands) s.news.filter { it.demand } else s.news

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionTitle("Últimas 24 horas")
                Text(
                    if (s.searchAllSources) "Escopo: qualquer veículo" else "Escopo: ${s.selectedSourceIds.size} fonte(s) selecionada(s)",
                    color = TextSecondary,
                    fontSize = 9.sp
                )
            }
            Button(
                onClick = vm::search,
                enabled = !s.busy,
                shape = RoundedCornerShape(11.dp),
                contentPadding = PaddingValues(horizontal = 11.dp, vertical = 7.dp)
            ) {
                Icon(
                    if (s.busy) Icons.Outlined.HourglassTop else Icons.Outlined.Refresh,
                    null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(if (s.busy) "Buscando" else "Buscar", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MetricStrip("Notícias", s.news.size.toString(), Accent, Modifier.weight(1f))
            MetricStrip("Demandas", s.news.count { it.demand }.toString(), Amber, Modifier.weight(1f))
            MetricStrip("Fontes", s.news.map { it.source }.distinct().size.toString(), Mint, Modifier.weight(1f))
        }

        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            MiniChoiceChip("Todas", !s.showOnlyDemands) { vm.setDemandFilter(false) }
            MiniChoiceChip("Demandas", s.showOnlyDemands) { vm.setDemandFilter(true) }
            Spacer(Modifier.weight(1f))
            s.lastUpdatedAt?.let {
                Text("${timeOnly(it)}", color = TextSecondary, fontSize = 8.5.sp)
            }
        }

        Spacer(Modifier.height(6.dp))
        NewsList(shown)
    }
}

@Composable
private fun MetricStrip(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(color = SurfaceDark, shape = RoundedCornerShape(11.dp), modifier = modifier) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(value, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            Spacer(Modifier.width(5.dp))
            Text(label, color = TextSecondary, fontSize = 8.5.sp)
        }
    }
}

@Composable
fun NewsList(items: List<News>) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState("Nenhuma notícia no escopo atual", "Faça uma nova busca ou altere as fontes.")
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            items(items, key = { it.link }) { NewsCard(it) }
        }
    }
}

@Composable
private fun NewsCard(n: News) {
    val context = LocalContext.current
    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, Divider.copy(alpha = .65f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(n.link)))
            }
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    n.source,
                    color = Accent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(dateText(n.date), color = TextSecondary, fontSize = 8.sp)
            }
            Spacer(Modifier.height(3.dp))
            Text(
                n.title,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (n.snippet.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    cleanSnippet(n.snippet),
                    color = TextSecondary,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (n.demand || n.matchedTerm.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (n.demand) TinyBadge("DEMANDA", Amber)
                    if (n.matchedTerm.isNotBlank()) TinyBadge(n.matchedTerm, Accent)
                }
            }
        }
    }
}

@Composable
private fun TinyBadge(text: String, color: Color) {
    Surface(color = color.copy(alpha = .10f), shape = RoundedCornerShape(7.dp)) {
        Text(
            text,
            color = color,
            fontSize = 7.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PeriodScreen(s: AppState, vm: MonitorViewModel) {
    val fromMs = parseDateTimeUi(s.periodStartDate, s.periodStartTime)
    val toMs = parseDateTimeUi(s.periodEndDate, s.periodEndTime)
    val valid = fromMs != null && toMs != null && fromMs < toMs

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 70.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            item { SectionTitle("Período", "Data e hora da pesquisa") }
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    MiniActionChip("Hoje", Icons.Outlined.Today) { vm.applyPeriodPreset(0) }
                    MiniActionChip("24 horas", Icons.Outlined.Schedule) { vm.applyPeriodPreset(1) }
                    MiniActionChip("7 dias", Icons.Outlined.DateRange) { vm.applyPeriodPreset(7) }
                    MiniActionChip("30 dias", Icons.Outlined.CalendarMonth) { vm.applyPeriodPreset(30) }
                }
            }
            item {
                Surface(color = SurfaceDark, shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        CompactDateTimeRow(
                            title = "Início",
                            accent = Accent,
                            date = s.periodStartDate,
                            time = s.periodStartTime,
                            onDate = vm::setPeriodStartDate,
                            onTime = vm::setPeriodStartTime
                        )
                        HorizontalDivider(Modifier.padding(vertical = 7.dp), color = Divider)
                        CompactDateTimeRow(
                            title = "Fim",
                            accent = Mint,
                            date = s.periodEndDate,
                            time = s.periodEndTime,
                            onDate = vm::setPeriodEndDate,
                            onTime = vm::setPeriodEndTime
                        )
                    }
                }
            }
            item {
                Surface(
                    color = if (valid) Mint.copy(alpha = .07f) else Amber.copy(alpha = .08f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (valid) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
                            null,
                            tint = if (valid) Mint else Amber,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (valid) "Período válido e salvo" else "Revise as datas e horários",
                            color = if (valid) Mint else Amber,
                            fontSize = 9.sp
                        )
                    }
                }
            }
            if (s.news.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Resultados", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("${s.news.size} matéria(s)", color = TextSecondary, fontSize = 9.sp)
                    }
                }
                items(s.news, key = { it.link }) { NewsCard(it) }
            }
        }

        StickyAction(
            label = if (s.busy) "Pesquisando..." else "Pesquisar período",
            icon = Icons.Outlined.Search,
            enabled = valid && !s.busy,
            onClick = vm::searchSavedPeriod
        )
    }
}

@Composable
private fun CompactDateTimeRow(
    title: String,
    accent: Color,
    date: String,
    time: String,
    onDate: (String) -> Unit,
    onTime: (String) -> Unit
) {
    Text(title, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = date,
            onValueChange = onDate,
            label = { Text("Data", fontSize = 8.5.sp) },
            placeholder = { Text("dd/MM/aaaa", fontSize = 9.5.sp) },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 11.5.sp),
            modifier = Modifier.weight(1.55f)
        )
        OutlinedTextField(
            value = time,
            onValueChange = onTime,
            label = { Text("Hora", fontSize = 8.5.sp) },
            placeholder = { Text("HH:mm", fontSize = 9.5.sp) },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 11.5.sp),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun DemandScreen(s: AppState, vm: MonitorViewModel) {
    var vehicle by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        item { SectionTitle("Demandas", "Alertas por veículo e assunto") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = vehicle,
                    onValueChange = { vehicle = it },
                    label = { Text("Veículo", fontSize = 8.5.sp) },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 11.5.sp),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Assunto", fontSize = 8.5.sp) },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 11.5.sp),
                    modifier = Modifier.weight(1f)
                )
                FilledIconButton(
                    onClick = {
                        vm.addDemand(vehicle, subject)
                        vehicle = ""
                        subject = ""
                    },
                    enabled = vehicle.isNotBlank() && subject.isNotBlank(),
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(Icons.Outlined.Add, "Adicionar", modifier = Modifier.size(18.dp))
                }
            }
        }
        if (s.demands.isEmpty()) {
            item { EmptyState("Nenhuma demanda", "Adicione veículo e assunto para criar um alerta.") }
        } else {
            items(s.demands, key = { it.id }) { d ->
                Surface(color = SurfaceDark, shape = RoundedCornerShape(12.dp)) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.NotificationsActive, null, tint = Amber, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(7.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.vehicle, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                            Text(d.subject, color = TextSecondary, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { vm.removeDemand(d.id) }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Outlined.DeleteOutline, "Excluir", tint = RedSoft, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(s: AppState, vm: MonitorViewModel) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Histórico", "${s.history.size} matéria(s)")
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CompactButton("Exportar", Icons.Outlined.IosShare, Modifier.weight(1f), s.history.isNotEmpty()) {
                exportCsv(context, s.history)
            }
            CompactButton("Limpar", Icons.Outlined.DeleteOutline, Modifier.weight(1f), s.history.isNotEmpty()) {
                vm.clearHistory()
            }
        }
        Spacer(Modifier.height(6.dp))
        NewsList(s.history)
    }
}

@Composable
fun TermsScreen(s: AppState, vm: MonitorViewModel) {
    var term by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item { SectionTitle("Termos", "Palavras usadas no monitoramento") }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = term,
                    onValueChange = { term = it },
                    label = { Text("Novo termo", fontSize = 8.5.sp) },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 11.5.sp),
                    modifier = Modifier.weight(1f)
                )
                FilledIconButton(
                    onClick = { vm.addTerm(term); term = "" },
                    enabled = term.isNotBlank(),
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(Icons.Outlined.Add, "Adicionar", modifier = Modifier.size(18.dp))
                }
            }
        }
        items(s.terms) { termItem ->
            Surface(color = SurfaceDark, shape = RoundedCornerShape(11.dp)) {
                Row(
                    Modifier.padding(start = 10.dp, end = 3.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Search, null, tint = Accent, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(termItem, fontSize = 10.5.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { vm.removeTerm(termItem) }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Outlined.Close, "Excluir", tint = TextSecondary, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SourcesScreen(s: AppState, vm: MonitorViewModel) {
    var section by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var region by remember { mutableStateOf(SourceCatalog.ALL_REGION) }
    var state by remember { mutableStateOf("") }

    val base = when (section) {
        0 -> SourceCatalog.national
        1 -> SourceCatalog.byState
        else -> emptyList()
    }
    val normalizedQuery = query.trim()
    val visible = base.filter { src ->
        val regionOk = section != 1 || region == SourceCatalog.ALL_REGION || src.region == region
        val stateOk = section != 1 || state.isBlank() || src.state == state
        val queryOk = normalizedQuery.isBlank() || listOf(
            src.name, src.group, src.region, src.stateName, src.state
        ).plus(src.aliases).any { it.contains(normalizedQuery, ignoreCase = true) }
        regionOk && stateOk && queryOk
    }
    val visibleIds = visible.map { it.id }.toSet()
    val selectedVisible = visibleIds.count { it in s.selectedSourceIds }
    val availableStates = SourceCatalog.states.filter {
        region == SourceCatalog.ALL_REGION || it.third == region
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 7.dp, bottom = 70.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Surface(
                    color = if (s.searchAllSources) Mint.copy(alpha = .07f) else SurfaceDark,
                    shape = RoundedCornerShape(11.dp),
                    border = BorderStroke(1.dp, if (s.searchAllSources) Mint.copy(alpha = .25f) else Divider)
                ) {
                    Row(
                        Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Language, null, tint = Mint, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Buscar em todos os veículos", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            Text("Inclui portais locais e menores", color = TextSecondary, fontSize = 8.5.sp)
                        }
                        Switch(
                            checked = s.searchAllSources,
                            onCheckedChange = vm::setSearchAllSources,
                            modifier = Modifier.size(width = 46.dp, height = 28.dp)
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    CompactSegment("Nacionais", Icons.Outlined.Newspaper, section == 0, Modifier.weight(1f)) {
                        section = 0
                        state = ""
                    }
                    CompactSegment("Estados", Icons.Outlined.Map, section == 1, Modifier.weight(1f)) {
                        section = 1
                    }
                    CompactSegment("Qualquer", Icons.Outlined.TravelExplore, section == 2, Modifier.weight(1f)) {
                        section = 2
                        vm.setSearchAllSources(true)
                    }
                }
            }

            if (section == 2) {
                item {
                    Surface(color = SurfaceDark, shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.TravelExplore, null, tint = Accent, modifier = Modifier.size(19.dp))
                            Spacer(Modifier.width(7.dp))
                            Text(
                                "Pesquisa aberta em qualquer veículo indexado.",
                                color = TextSecondary,
                                fontSize = 9.5.sp
                            )
                        }
                    }
                }
            } else {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        leadingIcon = { Icon(Icons.Outlined.Search, null, modifier = Modifier.size(17.dp)) },
                        trailingIcon = {
                            if (query.isNotBlank()) {
                                IconButton(onClick = { query = "" }, modifier = Modifier.size(30.dp)) {
                                    Icon(Icons.Outlined.Close, "Limpar", modifier = Modifier.size(15.dp))
                                }
                            }
                        },
                        placeholder = { Text("Pesquisar veículo", fontSize = 10.sp) },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 11.sp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (section == 1) {
                    item {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            SourceCatalog.regions.forEach { item ->
                                MiniChoiceChip(item, region == item) {
                                    region = item
                                    state = ""
                                }
                            }
                        }
                    }
                    item {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            MiniChoiceChip("Todos", state.isBlank()) { state = "" }
                            availableStates.forEach { item ->
                                MiniChoiceChip(item.first, state == item.first) { state = item.first }
                            }
                        }
                    }
                }

                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${visible.size} veículos • $selectedVisible selecionados",
                            color = TextSecondary,
                            fontSize = 8.8.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { vm.setVisibleSources(visibleIds, true) },
                            contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)
                        ) {
                            Text("Selecionar", fontSize = 8.8.sp)
                        }
                        TextButton(
                            onClick = { vm.setVisibleSources(visibleIds, false) },
                            contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)
                        ) {
                            Text("Limpar", fontSize = 8.8.sp)
                        }
                    }
                }

                if (visible.isEmpty()) {
                    item { EmptyState("Nenhuma fonte encontrada", "Altere a pesquisa ou os filtros.") }
                } else {
                    items(visible, key = { it.id }) { source ->
                        CompactSourceCard(
                            source = source,
                            selected = source.id in s.selectedSourceIds,
                            onToggle = { vm.setSourceSelected(source.id, it) }
                        )
                    }
                }
            }
        }

        Surface(
            color = AppBg,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (s.searchAllSources) "Busca aberta" else "${s.selectedSourceIds.size} fonte(s)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (s.searchAllSources) "qualquer veículo" else "selecionada(s)",
                        color = TextSecondary,
                        fontSize = 8.sp
                    )
                }
                Button(
                    onClick = vm::search,
                    enabled = !s.busy && (s.searchAllSources || s.selectedSourceIds.isNotEmpty()),
                    shape = RoundedCornerShape(11.dp),
                    contentPadding = PaddingValues(horizontal = 13.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Outlined.Search, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(if (s.busy) "Buscando" else "Buscar agora", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CompactSourceCard(
    source: MediaSource,
    selected: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        color = if (selected) SurfaceSelected else SurfaceDark,
        shape = RoundedCornerShape(11.dp),
        border = BorderStroke(1.dp, if (selected) Accent.copy(alpha = .38f) else Divider),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!selected) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) Accent.copy(alpha = .14f) else SurfaceRaised),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (source.region == SourceCatalog.NATIONAL_REGION) Icons.Outlined.Newspaper else Icons.Outlined.LocationOn,
                    null,
                    tint = if (selected) Accent else TextSecondary,
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(Modifier.width(7.dp))
            Column(Modifier.weight(1f)) {
                Text(source.name, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (source.region == SourceCatalog.NATIONAL_REGION) "Nacional" else "${source.state} • ${source.region}",
                    color = TextSecondary,
                    fontSize = 8.sp,
                    maxLines = 1
                )
            }
            Checkbox(
                checked = selected,
                onCheckedChange = onToggle,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Composable
private fun CompactSegment(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.height(32.dp).clickable(onClick = onClick),
        color = if (selected) SurfaceSelected else SurfaceDark,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (selected) Accent.copy(alpha = .38f) else Divider)
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (selected) Accent else TextSecondary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                label,
                fontSize = 8.8.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Accent else TextSecondary
            )
        }
    }
}

@Composable
fun SettingsScreen(s: AppState, vm: MonitorViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        item { SectionTitle("Configurações", "Ajustes do monitoramento") }
        item {
            Surface(color = SurfaceDark, shape = RoundedCornerShape(13.dp)) {
                Column(Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Schedule, null, tint = Accent, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(7.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Intervalo automático", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("Atual: ${s.intervalMinutes} min", color = TextSecondary, fontSize = 8.5.sp)
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        listOf(15, 30, 45, 60).forEach { minutes ->
                            MiniChoiceChip("$minutes min", s.intervalMinutes == minutes) { vm.setInterval(minutes) }
                        }
                    }
                }
            }
        }
        item {
            Surface(color = SurfaceDark, shape = RoundedCornerShape(13.dp)) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Source, null, tint = Mint, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Fontes", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (s.searchAllSources) "Qualquer veículo" else "${s.selectedSourceIds.size} selecionada(s)",
                            color = TextSecondary,
                            fontSize = 8.5.sp
                        )
                    }
                }
            }
        }
        item {
            Surface(color = Accent.copy(alpha = .06f), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(10.dp)) {
                    Text("Monitor de Notícias 2.4.0", color = Accent, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    Text("Layout compacto e filtro de fontes reforçado.", color = TextSecondary, fontSize = 8.5.sp)
                }
            }
        }
    }
}

@Composable
private fun MiniChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) Accent.copy(alpha = .13f) else SurfaceDark,
        shape = RoundedCornerShape(9.dp),
        border = BorderStroke(1.dp, if (selected) Accent.copy(alpha = .35f) else Divider),
        modifier = Modifier.height(27.dp).clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(
                label,
                color = if (selected) Accent else TextSecondary,
                fontSize = 8.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun MiniActionChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(9.dp),
        border = BorderStroke(1.dp, Divider),
        modifier = Modifier.height(30.dp).clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 8.8.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun CompactButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(36.dp),
        contentPadding = PaddingValues(horizontal = 7.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 9.sp)
    }
}

@Composable
private fun StickyAction(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = AppBg,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp).fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(icon, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(SurfaceDark),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.SearchOff, null, tint = Accent, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(title, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, color = TextSecondary, fontSize = 8.8.sp)
    }
}

private fun cleanSnippet(value: String): String = value
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")
    .replace(Regex("\\s+"), " ")
    .trim()

fun parseDateTimeUi(date: String, time: String): Long? = runCatching {
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).apply { isLenient = false }
        .parse("$date $time")?.time
}.getOrNull()

fun dateText(ms: Long): String =
    SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(Date(ms))

fun timeOnly(ms: Long): String =
    SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date(ms))

fun exportCsv(context: android.content.Context, items: List<News>) {
    fun clean(value: String) = value.replace(";", ",").replace("\n", " ")
    val fullDate = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
    val csv = buildString {
        append("Publicação;Captura;Veículo;Título;Termo;Demanda;Link\n")
        items.forEach {
            append(fullDate.format(Date(it.date))).append(';')
                .append(fullDate.format(Date(it.capturedAt))).append(';')
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
