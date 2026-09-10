from pathlib import Path

ROOT = Path('.')
DASH = ROOT / 'desktop/src/main/kotlin/br/com/monitordenoticias/desktop/DashboardV5Main.kt'
SOURCES = ROOT / 'app/src/main/java/br/com/monitordenoticias/android/SourceCatalog.kt'


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 match, found {count}')
    return text.replace(old, new, 1)


def replace_section(text: str, start: str, end: str, body: str, label: str) -> str:
    i = text.find(start)
    if i < 0:
        raise SystemExit(f'{label}: start marker not found')
    j = text.find(end, i + len(start))
    if j < 0:
        raise SystemExit(f'{label}: end marker not found')
    return text[:i] + body.rstrip() + '\n\n' + text[j:]


# ---------------------------------------------------------------------------
# Specialized source requested by the user.
# ---------------------------------------------------------------------------
source = SOURCES.read_text(encoding='utf-8')
insert_marker = '''        MediaSource(
            id = "especializada-agencia-marinha",'''
new_specialized = '''        MediaSource(
            id = "especializada-defesa-aerea-naval",
            name = "Defesa Aérea & Naval",
            region = NATIONAL_REGION,
            state = "BR",
            stateName = "Brasil",
            group = "Mídia especializada",
            aliases = listOf(
                "Defesa Aérea e Naval",
                "Defesa Aerea e Naval",
                "DAN",
                "defesaaereanaval.com.br"
            )
        ),
'''
if 'especializada-defesa-aerea-naval' not in source:
    source = replace_once(source, insert_marker, new_specialized + insert_marker, 'specialized Defesa Aérea & Naval')
SOURCES.write_text(source, encoding='utf-8')


# ---------------------------------------------------------------------------
# Desktop: compact result-first layout + functional global search.
# ---------------------------------------------------------------------------
dash = DASH.read_text(encoding='utf-8')

# Global-search model.
model_marker = 'private data class V5Weather(val temperature: Int, val description: String)'
model_replacement = '''private data class V5Weather(val temperature: Int, val description: String)
private data class V5GlobalHit(
    val label: String,
    val detail: String,
    val section: V5Section,
    val link: String? = null,
    val resolveNewsLink: Boolean = false
)'''
dash = replace_once(dash, model_marker, model_replacement, 'global hit model')

# Slightly tighter content margins so lists gain useful vertical space.
dash = replace_once(
    dash,
    '.padding(horizontal = 18.dp, vertical = 10.dp)',
    '.padding(horizontal = 16.dp, vertical = 6.dp)',
    'page content padding'
)

home_header = r'''@Composable
private fun V5HomeHeader(c: DesktopControllerV5, onNavigate: (V5Section) -> Unit, tick: Int) {
    var weather by remember { mutableStateOf<V5Weather?>(null) }
    var globalQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        while (true) {
            weather = V5FetchWeather()
            delay(15 * 60_000L)
        }
    }
    val now = Date(System.currentTimeMillis() + tick * 0L)
    val hits = remember(tick, globalQuery) { V5GlobalSearchHits(c, globalQuery) }

    fun openHit(hit: V5GlobalHit) {
        globalQuery = ""
        when {
            hit.link != null && hit.resolveNewsLink -> scope.launch { V5Open(c.resolveVehicleUrl(hit.link)) }
            hit.link != null -> V5Open(hit.link)
            else -> onNavigate(hit.section)
        }
    }

    Row(
        Modifier.fillMaxWidth().height(86.dp).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Eco, null, tint = V5Green, modifier = Modifier.size(34.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Olá, bem-vindo! 👋", color = V5Ink, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text("Acompanhe notícias, vídeos, demandas e fontes em tempo real.", color = V5Muted, fontSize = 12.sp)
        }

        Box(Modifier.width(440.dp)) {
            Surface(
                Modifier.fillMaxWidth().height(46.dp),
                color = Color.White,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, V5Border),
                shadowElevation = 4.dp
            ) {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, null, tint = Color(0xFF174B8F), modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(9.dp))
                    BasicTextField(
                        value = globalQuery,
                        onValueChange = { globalQuery = it },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        textStyle = LocalTextStyle.current.copy(color = V5Ink, fontSize = 12.sp),
                        decorationBox = { inner ->
                            if (globalQuery.isBlank()) Text("Buscar em todo o monitor...", color = V5Muted, fontSize = 12.sp)
                            inner()
                        }
                    )
                    if (globalQuery.isNotBlank()) {
                        IconButton(onClick = { globalQuery = "" }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Close, null, tint = V5Muted, modifier = Modifier.size(17.dp))
                        }
                    }
                }
            }
            DropdownMenu(
                expanded = globalQuery.trim().length >= 2,
                onDismissRequest = { globalQuery = "" },
                modifier = Modifier.width(440.dp).heightIn(max = 420.dp)
            ) {
                if (hits.isEmpty()) {
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("Nenhum resultado encontrado", color = V5Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text("Pesquise por título, fonte, demanda ou termo.", color = V5Muted, fontSize = 10.sp)
                            }
                        },
                        onClick = {}
                    )
                } else {
                    hits.forEach { hit ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(hit.label, color = V5Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(hit.detail, color = V5Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            },
                            leadingIcon = { Icon(hit.section.icon, null, tint = V5Blue, modifier = Modifier.size(19.dp)) },
                            onClick = { openHit(hit) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(14.dp))
        Icon(Icons.Default.NotificationsNone, null, tint = Color(0xFF174B8F), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF164C90)).clickable { onNavigate(V5Section.SETTINGS) },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Person, null, tint = Color.White) }
        Spacer(Modifier.width(10.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(105.dp)) {
            Text(SimpleDateFormat("dd MMM yyyy", Locale("pt", "BR")).format(now), color = V5Muted, fontSize = 9.sp)
            Text(SimpleDateFormat("HH:mm:ss", Locale("pt", "BR")).format(now), color = V5Ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }

        Surface(
            color = Color(0xFFF8FBFF),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, V5Border),
            modifier = Modifier.width(122.dp).height(58.dp)
        ) {
            Row(Modifier.padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WbSunny, null, tint = Color(0xFFFFB21B), modifier = Modifier.size(27.dp))
                Spacer(Modifier.width(7.dp))
                Column {
                    Text("Brasília - DF", color = V5Muted, fontSize = 9.sp)
                    Text(weather?.let { "${it.temperature}°C" } ?: "--°C", color = V5Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(weather?.description ?: "Atualizando...", color = V5Muted, fontSize = 8.sp, maxLines = 1)
                }
            }
        }
    }
}

private fun V5GlobalSearchHits(c: DesktopControllerV5, raw: String): List<V5GlobalHit> {
    val q = raw.trim()
    if (q.length < 2) return emptyList()
    val hits = mutableListOf<V5GlobalHit>()

    c.news.asSequence()
        .filter { "${it.title} ${it.source} ${it.matchedTerm} ${it.matchedDemand}".contains(q, true) }
        .take(5)
        .forEach { hits += V5GlobalHit(it.title, "Notícia • ${it.source}", V5Section.NEWS, it.link, resolveNewsLink = true) }

    c.videos.asSequence()
        .filter { "${it.title} ${it.sourceName} ${it.matchedTerm} ${it.matchedDemand}".contains(q, true) }
        .take(4)
        .forEach { hits += V5GlobalHit(it.title, "Vídeo • ${it.sourceName}", V5Section.VIDEOS, it.link) }

    c.demands.asSequence()
        .filter { "${it.vehicle} ${it.subject}".contains(q, true) }
        .take(3)
        .forEach { hits += V5GlobalHit("${it.vehicle} • ${it.subject}", "Demanda cadastrada", V5Section.DEMANDS) }

    SourceCatalog.all.asSequence()
        .filter { "${it.name} ${it.stateName} ${it.group} ${it.aliases.joinToString(" ")}".contains(q, true) }
        .take(4)
        .forEach { hits += V5GlobalHit(it.name, "Fonte • ${it.group}", V5Section.SOURCES) }

    (c.terms + c.videoTerms).distinctBy { it.lowercase() }.asSequence()
        .filter { it.contains(q, true) }
        .take(3)
        .forEach { hits += V5GlobalHit(it, "Termo de busca", V5Section.TERMS) }

    return hits.take(14)
}'''
dash = replace_section(
    dash,
    '@Composable\nprivate fun V5HomeHeader',
    '@Composable\nprivate fun V5PageHeader',
    home_header,
    'home header global search'
)

page_header = r'''@Composable
private fun V5PageHeader(section: V5Section, c: DesktopControllerV5, tick: Int) {
    val now = Date(System.currentTimeMillis() + tick * 0L)
    Row(
        Modifier.fillMaxWidth().height(84.dp).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(4.dp).height(48.dp).clip(RoundedCornerShape(2.dp)).background(V5Gold))
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text("CENTRAL DE INTELIGÊNCIA DE MÍDIA", color = Color(0xFFB78900), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
            Text(section.label, color = V5Ink, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 27.sp)
            Text(section.subtitle, color = V5Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        V5StatusPill(c.proxyStatusLabel, if (c.proxyReady) V5Green else Color(0xFFC88700), if (c.proxyReady) Icons.Default.VerifiedUser else Icons.Default.Security)
        Spacer(Modifier.width(8.dp))
        V5StatusPill(if (c.automaticMonitoring) "Automação ativa" else "Automação pausada", if (c.automaticMonitoring) V5Green else V5Orange, Icons.Default.Schedule)
        Spacer(Modifier.width(18.dp))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(150.dp)) {
            Text(SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(now), color = V5Muted, fontSize = 9.sp)
            Text(SimpleDateFormat("HH:mm:ss", Locale("pt", "BR")).format(now), color = V5Ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}'''
dash = replace_section(dash, '@Composable\nprivate fun V5PageHeader', '@Composable\nprivate fun V5StatusPill', page_header, 'compact page header')

# Status pills lose a little vertical padding in the compact header.
dash = replace_once(
    dash,
    'Row(Modifier.padding(horizontal = 13.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically)',
    'Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically)',
    'compact status pills'
)

news_screen = r'''@Composable
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

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        V5Card(Modifier.fillMaxWidth(), padding = 10.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                V5SearchBox(query, { query = it }, "Buscar nas notícias (título, fonte, termo...)", Modifier.weight(1f))
                Spacer(Modifier.width(9.dp))
                Button(
                    onClick = { val p = c.periodLastHours(24); c.searchNews(p.first, p.second) },
                    enabled = !c.newsBusy,
                    modifier = Modifier.height(44.dp)
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(6.dp)); Text("Buscar últimas 24h", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { onlyDemands = !onlyDemands }, modifier = Modifier.height(44.dp)) {
                    Icon(Icons.Default.FilterAlt, null, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(5.dp)); Text(if (onlyDemands) "Mostrar todas" else "Só demandas", fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                V5PeriodButton("Hoje") {
                    val end = System.currentTimeMillis()
                    val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    c.searchNews(start, end)
                }
                V5PeriodButton("24 horas") { val p = c.periodLastHours(24); c.searchNews(p.first, p.second) }
                V5PeriodButton("7 dias") { val p = c.periodLastHours(168); c.searchNews(p.first, p.second) }
                V5PeriodButton("30 dias") { val p = c.periodLastHours(720); c.searchNews(p.first, p.second) }
                V5PeriodButton(if (custom) "Fechar período" else "Período personalizado", selected = custom) { custom = !custom }
                Spacer(Modifier.weight(1f))
                Text("${list.size} matéria(s)", color = V5Muted, fontSize = 11.sp)
            }

            if (custom) {
                Spacer(Modifier.height(8.dp))
                Surface(color = Color(0xFFF6F9FD), shape = RoundedCornerShape(9.dp), border = BorderStroke(1.dp, V5Border), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(9.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        V5LabeledField("Data inicial", startDate, { startDate = it }, "AAAA-MM-DD", Modifier.width(145.dp))
                        V5LabeledField("Hora", startTime, { startTime = it.take(5) }, "00:00", Modifier.width(88.dp))
                        V5LabeledField("Data final", endDate, { endDate = it }, "AAAA-MM-DD", Modifier.width(145.dp))
                        V5LabeledField("Hora", endTime, { endTime = it.take(5) }, "23:59", Modifier.width(88.dp))
                        Button(
                            onClick = { c.parsePeriod(startDate, startTime, endDate, endTime)?.let { c.searchNews(it.first, it.second) } },
                            enabled = !c.newsBusy,
                            modifier = Modifier.height(44.dp)
                        ) {
                            Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp)); Text("Buscar período", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        V5ExecutionPanel("Notícias", c.newsBusy, c.newsProgress, c.status, c.newNewsLinks.size, c.lastNewsSearchDurationMs, tick)
        if (c.newsBusy) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = { c.stopNewsSearch() }, modifier = Modifier.height(36.dp)) {
                    Icon(Icons.Default.StopCircle, null, tint = V5Red, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp)); Text("Parar busca", color = V5Red, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Notícias encontradas", color = V5Ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f)); Text("${list.size} exibida(s)", color = V5Muted, fontSize = 11.sp)
        }

        if (list.isEmpty()) {
            V5EmptyState("Nenhuma notícia nesta visualização", "Execute uma busca ou altere os filtros para exibir resultados.", Icons.Default.Article, V5Blue, Modifier.weight(1f))
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(list, key = { it.link }) { V5NewsCard(it, it.link in c.newNewsLinks, c, showMatched = true) }
            }
        }
    }
}'''
dash = replace_section(dash, '@Composable\nprivate fun V5NewsScreen', '@Composable\nprivate fun V5VideosScreen', news_screen, 'compact news screen')

videos_screen = r'''@Composable
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

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        V5Card(Modifier.fillMaxWidth(), padding = 10.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                V5SearchBox(query, { query = it }, "Buscar nos vídeos (título, fonte, termo...)", Modifier.weight(1f))
                Spacer(Modifier.width(9.dp))
                Button(onClick = { c.searchVideos() }, enabled = !c.videoBusy, modifier = Modifier.height(44.dp)) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(6.dp)); Text("Buscar vídeos agora", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                V5PeriodButton("24 horas") { val p = c.periodLastHours(24); c.searchVideos(p.first, p.second) }
                V5PeriodButton("7 dias") { val p = c.periodLastHours(168); c.searchVideos(p.first, p.second) }
                V5PeriodButton("30 dias") { val p = c.periodLastHours(720); c.searchVideos(p.first, p.second) }
                V5PeriodButton(if (custom) "Fechar período" else "Período personalizado", selected = custom) { custom = !custom }
                Spacer(Modifier.weight(1f)); Text("${list.size} vídeo(s)", color = V5Muted, fontSize = 11.sp)
            }

            if (custom) {
                Spacer(Modifier.height(8.dp))
                Surface(color = Color(0xFFF8F5FF), shape = RoundedCornerShape(9.dp), border = BorderStroke(1.dp, V5Purple.copy(alpha = .20f)), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(9.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        V5LabeledField("Data inicial", startDate, { startDate = it }, "AAAA-MM-DD", Modifier.width(145.dp))
                        V5LabeledField("Hora", startTime, { startTime = it.take(5) }, "00:00", Modifier.width(88.dp))
                        V5LabeledField("Data final", endDate, { endDate = it }, "AAAA-MM-DD", Modifier.width(145.dp))
                        V5LabeledField("Hora", endTime, { endTime = it.take(5) }, "23:59", Modifier.width(88.dp))
                        Button(
                            onClick = { c.parsePeriod(startDate, startTime, endDate, endTime)?.let { c.searchVideos(it.first, it.second) } },
                            enabled = !c.videoBusy,
                            modifier = Modifier.height(44.dp)
                        ) {
                            Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp)); Text("Buscar período", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        V5ExecutionPanel("Vídeos", c.videoBusy, c.videoProgress, c.videoStatus, c.newVideoLinks.size, c.lastVideoSearchDurationMs, tick)
        if (c.videoBusy) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = { c.stopVideoSearch() }, modifier = Modifier.height(36.dp)) {
                    Icon(Icons.Default.StopCircle, null, tint = V5Red, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp)); Text("Parar busca", color = V5Red, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }
        if (c.unstableVideoSources.isNotEmpty()) {
            V5UnstableVideoSourcesPanel(c.unstableVideoSources)
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Vídeos encontrados", color = V5Ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
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
}'''
dash = replace_section(dash, '@Composable\nprivate fun V5VideosScreen', '@Composable\nprivate fun V5StopScreen', videos_screen, 'compact videos screen')

unstable_panel = r'''@Composable
private fun V5UnstableVideoSourcesPanel(issues: List<VideoSourceIssue>) {
    Surface(
        color = Color(0xFFFFF8EC),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, V5Gold.copy(alpha = .36f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
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
}'''
dash = replace_section(dash, '@Composable\nprivate fun V5UnstableVideoSourcesPanel', '@Composable\nprivate fun V5ExecutionPanel', unstable_panel, 'compact unstable panel')

execution_panel = r'''@Composable
private fun V5ExecutionPanel(kind: String, busy: Boolean, p: LiveSearchProgress, status: String, fresh: Int, duration: Long, tick: Int) {
    val fraction = if (busy) p.fraction.coerceIn(0f, 1f) else 1f
    val pct = (fraction * 100).roundToInt()
    val elapsed = if (busy && p.startedAt > 0) System.currentTimeMillis() - p.startedAt else duration
    @Suppress("UNUSED_VARIABLE") val redraw = tick

    V5Card(Modifier.fillMaxWidth(), padding = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background((if (busy) V5Blue else V5Green).copy(alpha = .12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(if (busy) Icons.Default.Sync else Icons.Default.CheckCircle, null, tint = if (busy) V5Blue else V5Green, modifier = Modifier.size(25.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (busy) "$kind • busca em andamento" else "$kind • última execução concluída", color = V5Ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
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

        Spacer(Modifier.height(7.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp)),
            color = if (busy) V5Blue else V5Green,
            trackColor = Color(0xFFE4EDF7)
        )
        Spacer(Modifier.height(7.dp))
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
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun V5Stat(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
        Text(value, color = color, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(label, color = V5Muted, fontSize = 9.sp, maxLines = 1)
    }
}'''
dash = replace_section(dash, '@Composable\nprivate fun V5ExecutionPanel', '@Composable\nprivate fun V5NewsCard', execution_panel, 'compact execution panel')

# Result cards: slightly tighter, but title/description remain readable.
dash = dash.replace('V5Card(Modifier.fillMaxWidth(), padding = 14.dp) {\n        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically)', 'V5Card(Modifier.fillMaxWidth(), padding = 11.dp) {\n        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically)', 2)
dash = dash.replace('Modifier.size(58.dp).clip(RoundedCornerShape(12.dp))', 'Modifier.size(50.dp).clip(RoundedCornerShape(11.dp))', 2)
dash = dash.replace('modifier = Modifier.size(31.dp)', 'modifier = Modifier.size(27.dp)', 1)
dash = dash.replace('modifier = Modifier.size(33.dp)', 'modifier = Modifier.size(29.dp)', 1)
dash = dash.replace('Spacer(Modifier.width(14.dp))\n            Column(Modifier.weight(1f))', 'Spacer(Modifier.width(11.dp))\n            Column(Modifier.weight(1f))', 2)
dash = dash.replace('color = V5Muted, fontSize = 9.sp, maxLines = 1', 'color = V5Muted, fontSize = 10.sp, maxLines = 1', 2)
dash = dash.replace('fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2', 'fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2', 2)
dash = dash.replace('fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp', 'fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp', 2)

sources_screen = r'''@Composable
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
    val visible = if (tab == V5SourceTab.VIDEOS) videoList.size else newsList.size
    val allNewsMode = tab == V5SourceTab.NEWS && c.newsAllSources

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        V5Card(Modifier.fillMaxWidth(), padding = 10.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                V5TabButton("Notícias", tab == V5SourceTab.NEWS) { tab = V5SourceTab.NEWS; region = "Todas"; state = "Todos" }
                Spacer(Modifier.width(6.dp))
                V5TabButton("Vídeos", tab == V5SourceTab.VIDEOS) { tab = V5SourceTab.VIDEOS; region = "Todas"; state = "Todos" }
                Spacer(Modifier.width(6.dp))
                V5TabButton("Mídia especializada", tab == V5SourceTab.SPECIAL) { tab = V5SourceTab.SPECIAL; region = "Todas"; state = "Todos" }
                Spacer(Modifier.weight(1f))
                V5SearchBox(query, { query = it }, "Pesquisar fonte...", Modifier.width(390.dp))
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

            Spacer(Modifier.height(8.dp))
            when (tab) {
                V5SourceTab.NEWS -> Surface(
                    color = if (c.newsAllSources) Color(0xFFE5F8EF) else Color(0xFFF6F9FD),
                    shape = RoundedCornerShape(9.dp),
                    border = BorderStroke(1.dp, if (c.newsAllSources) V5Green.copy(alpha = .35f) else V5Border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Public, null, tint = if (c.newsAllSources) V5Green else V5Muted, modifier = Modifier.size(21.dp))
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text("TODOS OS VEÍCULOS — SEM EXCEÇÃO", color = if (c.newsAllSources) V5Green else V5Ink, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Ligado: inclui também veículos fora do catálogo.", color = V5Muted, fontSize = 10.sp)
                        }
                        Text(if (c.newsAllSources) "LIGADO" else "DESLIGADO", color = if (c.newsAllSources) V5Green else V5Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(6.dp)); Switch(c.newsAllSources, onCheckedChange = { c.newsAllSources = it })
                    }
                }
                V5SourceTab.VIDEOS -> Surface(color = Color(0xFFF8F5FF), shape = RoundedCornerShape(9.dp), border = BorderStroke(1.dp, V5Purple.copy(alpha = .18f)), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.YouTube, null, tint = V5Red, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp)); Text("Canais oficiais e páginas de vídeo, incluindo g1 e Domingo Espetacular.", color = V5Ink, fontSize = 10.sp)
                    }
                }
                V5SourceTab.SPECIAL -> Surface(color = Color(0xFFFFF8E8), shape = RoundedCornerShape(9.dp), border = BorderStroke(1.dp, V5Gold.copy(alpha = .28f)), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Verified, null, tint = Color(0xFFC88700), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp)); Text("${SourceCatalog.specialized.size} fontes especializadas em Defesa, Forças Armadas, aviação e assuntos navais.", color = V5Ink, fontSize = 10.sp)
                    }
                }
            }
        }

        V5Card(Modifier.fillMaxWidth(), padding = 9.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storage, null, tint = V5Blue, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(9.dp))
                Text("$visible fonte(s) visível(is)", color = V5Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                if (allNewsMode) { Spacer(Modifier.width(8.dp)); V5Tag("seletor ignorado", V5Green, 125.dp) }
                Spacer(Modifier.weight(1f))
                Button(onClick = {
                    if (tab == V5SourceTab.VIDEOS) videoList.forEach { c.setVideoSource(it.id, true) }
                    else newsList.forEach { c.setNewsSource(it.id, true) }
                }, modifier = Modifier.height(36.dp), contentPadding = PaddingValues(horizontal = 12.dp)) { Text("Selecionar visíveis", fontSize = 10.sp) }
                Spacer(Modifier.width(5.dp))
                OutlinedButton(onClick = {
                    if (tab == V5SourceTab.VIDEOS) videoList.forEach { c.setVideoSource(it.id, false) }
                    else newsList.forEach { c.setNewsSource(it.id, false) }
                }, modifier = Modifier.height(36.dp), contentPadding = PaddingValues(horizontal = 12.dp)) { Text("Limpar visíveis", fontSize = 10.sp) }
                Spacer(Modifier.width(5.dp))
                OutlinedButton(onClick = {
                    when (tab) {
                        V5SourceTab.NEWS -> c.selectAllNewsSources()
                        V5SourceTab.VIDEOS -> c.selectAllVideoSources()
                        V5SourceTab.SPECIAL -> SourceCatalog.specialized.forEach { c.setNewsSource(it.id, true) }
                    }
                }, modifier = Modifier.height(36.dp), contentPadding = PaddingValues(horizontal = 12.dp)) { Text("Todas", fontSize = 10.sp) }
                Spacer(Modifier.width(5.dp))
                OutlinedButton(onClick = {
                    when (tab) {
                        V5SourceTab.NEWS -> c.clearNewsSources()
                        V5SourceTab.VIDEOS -> c.clearVideoSources()
                        V5SourceTab.SPECIAL -> SourceCatalog.specialized.forEach { c.setNewsSource(it.id, false) }
                    }
                }, modifier = Modifier.height(36.dp), contentPadding = PaddingValues(horizontal = 12.dp)) { Text("Nenhuma", fontSize = 10.sp) }
            }
        }

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            if (tab == V5SourceTab.VIDEOS) {
                items(videoList, key = { it.id }) { s ->
                    V5SourceRow(s.name, "${s.group} • ${s.region} • ${s.state.ifBlank { "BR" }}", s.id in c.selectedVideoSourceIds, true) { c.setVideoSource(s.id, it) }
                }
            } else {
                items(newsList, key = { it.id }) { s ->
                    V5SourceRow(s.name, "${s.group} • ${s.region} • ${s.state.ifBlank { "BR" }}", allNewsMode || s.id in c.selectedNewsSourceIds, !allNewsMode) { c.setNewsSource(s.id, it) }
                }
            }
        }
    }
}'''
dash = replace_section(dash, '@Composable\nprivate fun V5SourcesScreen', '@Composable\nprivate fun V5TabButton', sources_screen, 'compact sources screen')

# Compact source controls and rows, without making text smaller than readable.
dash = dash.replace('modifier = Modifier.height(44.dp).clickable(onClick = click)', 'modifier = Modifier.height(38.dp).clickable(onClick = click)', 1)
dash = dash.replace('Modifier.height(38.dp).clickable(onClick = click)', 'Modifier.height(32.dp).clickable(onClick = click)', 1)
dash = dash.replace('Row(Modifier.padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically)', 'Row(Modifier.padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically)', 1)

source_row = r'''@Composable
private fun V5SourceRow(name: String, detail: String, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    V5Card(Modifier.fillMaxWidth(), padding = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = change, enabled = enabled, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(7.dp))
            Box(Modifier.width(60.dp).height(34.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0B4C8C)), contentAlignment = Alignment.Center) {
                Text(name.filter { it.isLetterOrDigit() }.take(3).uppercase(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = V5Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, color = V5Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!enabled) V5Tag("modo todos", V5Green, 90.dp)
            Spacer(Modifier.width(6.dp)); Icon(Icons.Default.ChevronRight, null, tint = V5Blue, modifier = Modifier.size(20.dp))
        }
    }
}'''
dash = replace_section(dash, '@Composable\nprivate fun V5SourceRow', '@Composable\nprivate fun V5DemandsScreen', source_row, 'compact source rows')

# Shared search and period controls: less vertical chrome, same 12sp text.
dash = replace_once(dash, 'Surface(modifier.height(50.dp), color = Color.White', 'Surface(modifier.height(44.dp), color = Color.White', 'search box height')
dash = replace_once(dash, 'modifier = Modifier.height(38.dp),\n        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected) V5SoftBlue else Color.Transparent)', 'modifier = Modifier.height(34.dp),\n        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected) V5SoftBlue else Color.Transparent)', 'period button height')

DASH.write_text(dash, encoding='utf-8')
print('V9 compact-result refinements applied.')
