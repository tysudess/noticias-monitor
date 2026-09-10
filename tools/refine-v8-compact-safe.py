from pathlib import Path

BASELINE = "93697ca705c4ebd53a8b8955821da5b30290fe7c"
ROOT = Path('.')
DASH = ROOT / 'desktop/src/main/kotlin/br/com/monitordenoticias/desktop/DashboardV5Main.kt'
SOURCES = ROOT / 'app/src/main/java/br/com/monitordenoticias/android/SourceCatalog.kt'


def one(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)


def section(text: str, start: str, end: str, label: str) -> tuple[str, int, int]:
    i = text.find(start)
    if i < 0:
        raise SystemExit(f"{label}: start marker not found")
    j = text.find(end, i + len(start))
    if j < 0:
        raise SystemExit(f"{label}: end marker not found")
    return text[i:j], i, j


def transform_section(text: str, start: str, end: str, replacements: list[tuple[str, str]], label: str) -> str:
    body, i, j = section(text, start, end, label)
    for n, (old, new) in enumerate(replacements, start=1):
        if old not in body:
            raise SystemExit(f"{label} replacement {n}: marker not found: {old[:90]!r}")
        body = body.replace(old, new)
    return text[:i] + body + text[j:]


def replace_section(text: str, start: str, end: str, new_body: str, label: str) -> str:
    _, i, j = section(text, start, end, label)
    return text[:i] + new_body.rstrip() + "\n\n" + text[j:]


# ---------------------------------------------------------------------------
# Dashboard: deliberately limited to the requested V8 screens/components.
# ---------------------------------------------------------------------------
dash = DASH.read_text(encoding='utf-8')

# Compact the non-home page header while preserving its identity/status pills.
dash = transform_section(
    dash,
    '@Composable\nprivate fun V5PageHeader',
    '@Composable\nprivate fun V5StatusPill',
    [
        ('Modifier.fillMaxWidth().height(108.dp).padding(horizontal = 26.dp)', 'Modifier.fillMaxWidth().height(82.dp).padding(horizontal = 22.dp)'),
        ('Box(Modifier.width(4.dp).height(58.dp)', 'Box(Modifier.width(4.dp).height(44.dp)'),
        ('Spacer(Modifier.width(14.dp))', 'Spacer(Modifier.width(12.dp))'),
        ('fontSize = 28.sp', 'fontSize = 24.sp'),
        ('Spacer(Modifier.width(24.dp))', 'Spacer(Modifier.width(18.dp))'),
        ('modifier = Modifier.width(185.dp)', 'modifier = Modifier.width(150.dp)'),
        ('fontSize = 19.sp', 'fontSize = 17.sp'),
    ],
    'V5PageHeader'
)

# News: smaller controls/status footprint, more readable result cards, results start higher.
dash = transform_section(
    dash,
    '@Composable\nprivate fun V5NewsScreen',
    '@Composable\nprivate fun V5VideosScreen',
    [
        ('Column(verticalArrangement = Arrangement.spacedBy(12.dp))', 'Column(verticalArrangement = Arrangement.spacedBy(8.dp))'),
        ('V5Card(Modifier.fillMaxWidth()) {', 'V5Card(Modifier.fillMaxWidth(), padding = 10.dp) {'),
        ('V5SearchBox(query, { query = it }, "Buscar nas notícias (título, fonte, termo...)", Modifier.weight(1f))', 'V5SearchBox(query, { query = it }, "Buscar nas notícias (título, fonte, termo...)", Modifier.weight(1f).height(42.dp))'),
        ('Spacer(Modifier.width(12.dp))', 'Spacer(Modifier.width(9.dp))'),
        ('modifier = Modifier.height(50.dp)', 'modifier = Modifier.height(42.dp)'),
        ('OutlinedButton(onClick = { onlyDemands = !onlyDemands }, modifier = Modifier.height(50.dp))', 'OutlinedButton(onClick = { onlyDemands = !onlyDemands }, modifier = Modifier.height(42.dp))'),
        ('Spacer(Modifier.height(11.dp))', 'Spacer(Modifier.height(7.dp))'),
        ('if (custom) {\n                Spacer(Modifier.height(12.dp))', 'if (custom) {\n                Spacer(Modifier.height(8.dp))'),
        ('Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Bottom', 'Row(Modifier.padding(10.dp), verticalAlignment = Alignment.Bottom'),
        ('LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp))', 'LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp))'),
    ],
    'V5NewsScreen'
)

# Videos: same compact treatment and remove the duplicate instability warning.
dash = transform_section(
    dash,
    '@Composable\nprivate fun V5VideosScreen',
    '@Composable\nprivate fun V5StopScreen',
    [
        ('Column(verticalArrangement = Arrangement.spacedBy(12.dp))', 'Column(verticalArrangement = Arrangement.spacedBy(8.dp))'),
        ('V5Card(Modifier.fillMaxWidth()) {', 'V5Card(Modifier.fillMaxWidth(), padding = 10.dp) {'),
        ('V5SearchBox(query, { query = it }, "Buscar nos vídeos (título, fonte, termo...)", Modifier.weight(1f))', 'V5SearchBox(query, { query = it }, "Buscar nos vídeos (título, fonte, termo...)", Modifier.weight(1f).height(42.dp))'),
        ('Spacer(Modifier.width(12.dp))', 'Spacer(Modifier.width(9.dp))'),
        ('modifier = Modifier.height(50.dp)', 'modifier = Modifier.height(42.dp)'),
        ('Spacer(Modifier.height(11.dp))', 'Spacer(Modifier.height(7.dp))'),
        ('if (custom) {\n                Spacer(Modifier.height(12.dp))', 'if (custom) {\n                Spacer(Modifier.height(8.dp))'),
        ('Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Bottom', 'Row(Modifier.padding(10.dp), verticalAlignment = Alignment.Bottom'),
        ('LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp))', 'LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp))'),
    ],
    'V5VideosScreen'
)

duplicate_warning = '''        if (c.unstableVideoSources.isNotEmpty()) {
            Surface(color = Color(0xFFFFF6E8), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, V5Gold.copy(alpha = .35f)), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WarningAmber, null, tint = Color(0xFFC88700), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${c.unstableVideoSources.size} fonte(s) apresentaram instabilidade na última busca. As demais fontes continuaram normalmente.",
                        color = Color(0xFF8A5D00), fontSize = 11.sp
                    )
                }
            }
        }

'''
if duplicate_warning not in dash:
    raise SystemExit('V5VideosScreen duplicate instability warning marker not found')
dash = dash.replace(duplicate_warning, '', 1)

# Compact instability display: one useful row instead of a tall second panel.
compact_instability = '''@Composable
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
}'''
dash = replace_section(
    dash,
    '@Composable\nprivate fun V5UnstableVideoSourcesPanel',
    '@Composable\nprivate fun V5ExecutionPanel',
    compact_instability,
    'V5UnstableVideoSourcesPanel'
)

# Last-execution panel: preserve every metric but remove excess vertical height.
compact_execution = '''@Composable
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
}'''
dash = replace_section(
    dash,
    '@Composable\nprivate fun V5ExecutionPanel',
    '@Composable\nprivate fun V5Stat',
    compact_execution,
    'V5ExecutionPanel'
)

# Metric labels remain readable after the compact execution panel.
dash = transform_section(
    dash,
    '@Composable\nprivate fun V5Stat',
    '@Composable\nprivate fun V5NewsCard',
    [
        ('modifier = Modifier.width(72.dp)', 'modifier = Modifier.width(66.dp)'),
        ('fontSize = 14.sp', 'fontSize = 13.sp'),
        ('fontSize = 8.sp', 'fontSize = 10.sp'),
    ],
    'V5Stat'
)

# News card hierarchy: larger title/description while using less blank padding.
dash = transform_section(
    dash,
    '@Composable\nprivate fun V5NewsCard',
    '@Composable\nprivate fun V5VideoCard',
    [
        ('padding = 14.dp', 'padding = 11.dp'),
        ('Modifier.size(58.dp)', 'Modifier.size(50.dp)'),
        ('modifier = Modifier.size(31.dp)', 'modifier = Modifier.size(27.dp)'),
        ('Spacer(Modifier.width(14.dp))', 'Spacer(Modifier.width(11.dp))'),
        ('fontSize = 9.sp', 'fontSize = 10.sp'),
        ('fontSize = 13.sp', 'fontSize = 14.sp'),
        ('fontSize = 10.sp, maxLines = 2', 'fontSize = 11.sp, maxLines = 2'),
        ('lineHeight = 14.sp', 'lineHeight = 15.sp'),
    ],
    'V5NewsCard'
)

# Video card gets the same visual hierarchy as news.
dash = transform_section(
    dash,
    '@Composable\nprivate fun V5VideoCard',
    '@Composable\nprivate fun V5LinkButtons',
    [
        ('padding = 14.dp', 'padding = 11.dp'),
        ('Modifier.size(58.dp)', 'Modifier.size(50.dp)'),
        ('modifier = Modifier.size(33.dp)', 'modifier = Modifier.size(29.dp)'),
        ('Spacer(Modifier.width(14.dp))', 'Spacer(Modifier.width(11.dp))'),
        ('fontSize = 9.sp', 'fontSize = 10.sp'),
        ('fontSize = 13.sp', 'fontSize = 14.sp'),
        ('fontSize = 10.sp, maxLines = 2', 'fontSize = 11.sp, maxLines = 2'),
        ('lineHeight = 14.sp', 'lineHeight = 15.sp'),
    ],
    'V5VideoCard'
)

# Tags/terms gain contrast without changing dimensions significantly.
dash = transform_section(
    dash,
    '@Composable\nprivate fun V5Tag',
    '@Composable\nprivate fun V5TermsScreen',
    [
        ('color.copy(alpha = .08f)', 'color.copy(alpha = .13f)'),
        ('color.copy(alpha = .25f)', 'color.copy(alpha = .42f)'),
        ('fontWeight = FontWeight.Medium', 'fontWeight = FontWeight.SemiBold'),
    ],
    'V5Tag'
)

# Sources: compact only this screen and its source-specific controls.
dash = transform_section(
    dash,
    '@Composable\nprivate fun V5SourcesScreen',
    '@Composable\nprivate fun V5TabButton',
    [
        ('Column(verticalArrangement = Arrangement.spacedBy(10.dp))', 'Column(verticalArrangement = Arrangement.spacedBy(7.dp))'),
        ('V5Card(Modifier.fillMaxWidth()) {', 'V5Card(Modifier.fillMaxWidth(), padding = 10.dp) {'),
        ('V5SearchBox(query, { query = it }, "Pesquisar fonte...", Modifier.width(400.dp))', 'V5SearchBox(query, { query = it }, "Pesquisar fonte...", Modifier.width(390.dp).height(42.dp))'),
        ('Spacer(Modifier.height(12.dp))\n            Row(verticalAlignment = Alignment.CenterVertically) {\n                Text("Região"', 'Spacer(Modifier.height(8.dp))\n            Row(verticalAlignment = Alignment.CenterVertically) {\n                Text("Região"'),
        ('modifier = Modifier.width(65.dp)', 'modifier = Modifier.width(58.dp)'),
        ('Spacer(Modifier.width(5.dp))', 'Spacer(Modifier.width(4.dp))'),
        ('Spacer(Modifier.height(9.dp))', 'Spacer(Modifier.height(6.dp))'),
        ('Spacer(Modifier.height(12.dp))\n                    Surface(', 'Spacer(Modifier.height(8.dp))\n                    Surface('),
        ('Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)', 'Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp)'),
        ('V5Card(Modifier.fillMaxWidth(), padding = 13.dp)', 'V5Card(Modifier.fillMaxWidth(), padding = 9.dp)'),
        ('modifier = Modifier.size(31.dp)', 'modifier = Modifier.size(26.dp)'),
        ('LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp))', 'LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp))'),
    ],
    'V5SourcesScreen'
)

# Source-specific buttons/rows only.
dash = transform_section(
    dash,
    '@Composable\nprivate fun V5TabButton',
    '@Composable\nprivate fun V5DemandsScreen',
    [
        ('modifier = Modifier.height(44.dp).clickable(onClick = click)', 'modifier = Modifier.height(36.dp).clickable(onClick = click)'),
        ('modifier = Modifier.height(38.dp).clickable(onClick = click)', 'modifier = Modifier.height(34.dp).clickable(onClick = click)'),
        ('V5Card(Modifier.fillMaxWidth(), padding = 11.dp)', 'V5Card(Modifier.fillMaxWidth(), padding = 8.dp)'),
        ('Checkbox(checked = checked, onCheckedChange = change, enabled = enabled)', 'Checkbox(checked = checked, onCheckedChange = change, enabled = enabled, modifier = Modifier.size(36.dp))'),
        ('Spacer(Modifier.width(9.dp))', 'Spacer(Modifier.width(7.dp))'),
        ('Modifier.width(68.dp).height(38.dp)', 'Modifier.width(60.dp).height(34.dp)'),
        ('Spacer(Modifier.width(15.dp))', 'Spacer(Modifier.width(12.dp))'),
        ('Spacer(Modifier.width(8.dp)); Icon(Icons.Default.ChevronRight', 'Spacer(Modifier.width(6.dp)); Icon(Icons.Default.ChevronRight'),
    ],
    'source controls'
)

DASH.write_text(dash, encoding='utf-8')


# ---------------------------------------------------------------------------
# Add only the requested specialized source to the known-good V8 catalog.
# ---------------------------------------------------------------------------
sources = SOURCES.read_text(encoding='utf-8')
if 'especializada-defesa-aerea-naval' in sources:
    raise SystemExit('Defesa Aérea & Naval already present; refusing duplicate migration')

anchor = '''        MediaSource(
            id = "especializada-agencia-marinha",
            name = "Agência Marinha de Notícias",'''
new_source = '''        MediaSource(
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
        MediaSource(
            id = "especializada-agencia-marinha",
            name = "Agência Marinha de Notícias",'''
sources = one(sources, anchor, new_source, 'Defesa Aérea & Naval source')
SOURCES.write_text(sources, encoding='utf-8')

print('Safe compact refinement applied on the known-good V8 baseline.')
