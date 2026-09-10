from pathlib import Path

ROOT = Path('.')


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


def replace_section(text: str, start: str, end: str, new_body: str, label: str) -> str:
    i = text.find(start)
    if i < 0:
        raise SystemExit(f"{label}: start marker not found")
    j = text.find(end, i + len(start))
    if j < 0:
        raise SystemExit(f"{label}: end marker not found")
    return text[:i] + new_body.rstrip() + "\n\n" + text[j:]


def transform_section(text: str, start: str, end: str, replacements: list[tuple[str, str]], label: str) -> str:
    i = text.find(start)
    if i < 0:
        raise SystemExit(f"{label}: start marker not found")
    j = text.find(end, i + len(start))
    if j < 0:
        raise SystemExit(f"{label}: end marker not found")
    section = text[i:j]
    for old, new in replacements:
        section = section.replace(old, new)
    return text[:i] + section + text[j:]


# ---------------------------------------------------------------------------
# Shared default terms: used by News DB, NewsRepository and VideoTermStore.
# ---------------------------------------------------------------------------
default_terms_path = ROOT / 'app/src/main/java/br/com/monitordenoticias/android/DefaultTerms.kt'
default_terms_path.write_text('''package br.com.monitordenoticias.android

/**
 * Vocabulário inicial do Monitor de Notícias.
 *
 * Esta lista é aplicada na criação do banco e, uma única vez, nas instalações
 * existentes quando a versão que introduziu estes padrões é aberta.
 */
val DEFAULT_MONITOR_TERMS: List<String> = listOf(
    "Marinha do Brasil",
    "Capitania dos Portos",
    "Distrito Naval",
    "NAM Atlântico",
    "Cisne Branco",
    "Fragata Marinha do Brasil",
    "Navio-Patrulha Marinha",
    "Programa Nuclear da Marinha",
    "CAPITANIA FLUVIAL",
    "MARINHA",
    "EXÉRCITO",
    "STM",
    "FAB",
    "FORÇA AÉREA BRASILEIRA",
    "FORÇAS ARMADAS",
    "FRAGATA",
    "SUBMARINO",
    "MAIOR NAVIO DA AMÉRICA LATINA",
    "MANCHAS DE ÓLEO",
    "MILITARES",
    "MILITAR",
    "MINISTRO DA DEFESA",
    "MINISTÉRIO DA DEFESA",
    "PROSUB",
    "ENGEPRON"
).distinctBy { it.lowercase() }
''', encoding='utf-8')


# ---------------------------------------------------------------------------
# News database: v4 migrates new defaults into existing portable installs.
# ---------------------------------------------------------------------------
news_db_path = ROOT / 'app/src/main/java/br/com/monitordenoticias/android/NewsDb.kt'
news_db = news_db_path.read_text(encoding='utf-8')
news_db = replace_once(
    news_db,
    'class NewsDb(context: Context) : SQLiteOpenHelper(context, "news.db", null, 3)',
    'class NewsDb(context: Context) : SQLiteOpenHelper(context, "news.db", null, 4)',
    'NewsDb version'
)
news_db = replace_once(
    news_db,
    '        val defaults = listOf("Marinha do Brasil","Capitania dos Portos","Distrito Naval","NAM Atlântico","Cisne Branco","Fragata Marinha do Brasil","Navio-Patrulha Marinha","Programa Nuclear da Marinha")\n        defaults.forEach { db.execSQL("INSERT OR IGNORE INTO terms(term) VALUES(?)", arrayOf(it)) }',
    '        DEFAULT_MONITOR_TERMS.forEach { db.execSQL("INSERT OR IGNORE INTO terms(term) VALUES(?)", arrayOf(it)) }',
    'NewsDb create defaults'
)
news_db = replace_once(
    news_db,
    '''        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE demands ADD COLUMN last_checked_at INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE demands ADD COLUMN last_found_count INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE demands ADD COLUMN last_new_count INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE demands ADD COLUMN last_error TEXT DEFAULT ''")
        }
    }''',
    '''        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE demands ADD COLUMN last_checked_at INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE demands ADD COLUMN last_found_count INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE demands ADD COLUMN last_new_count INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE demands ADD COLUMN last_error TEXT DEFAULT ''")
        }
        if (oldVersion < 4) {
            DEFAULT_MONITOR_TERMS.forEach {
                db.execSQL("INSERT OR IGNORE INTO terms(term) VALUES(?)", arrayOf(it))
            }
        }
    }''',
    'NewsDb v4 migration'
)
news_db_path.write_text(news_db, encoding='utf-8')


# ---------------------------------------------------------------------------
# NewsRepository fallback defaults use the same canonical list.
# ---------------------------------------------------------------------------
news_repo_path = ROOT / 'app/src/main/java/br/com/monitordenoticias/android/NewsRepository.kt'
news_repo = news_repo_path.read_text(encoding='utf-8')
news_repo = replace_once(
    news_repo,
    '''    val defaultTerms = listOf(
        "Marinha do Brasil","Capitania dos Portos","Distrito Naval","NAM Atlântico",
        "Cisne Branco","Fragata Marinha do Brasil","Navio-Patrulha Marinha","Programa Nuclear da Marinha"
    )''',
    '    val defaultTerms = DEFAULT_MONITOR_TERMS',
    'NewsRepository defaults'
)
news_repo_path.write_text(news_repo, encoding='utf-8')


# ---------------------------------------------------------------------------
# Video terms: merge the new default vocabulary exactly once for old installs.
# Users can still edit/remove terms normally after this migration.
# ---------------------------------------------------------------------------
video_terms_path = ROOT / 'app/src/main/java/br/com/monitordenoticias/android/VideoTermStore.kt'
video_terms_path.write_text('''package br.com.monitordenoticias.android

import android.content.Context

/**
 * Termos de vídeo independentes dos termos de notícias.
 *
 * Na primeira execução copiamos os termos iniciais e, na migração V7, incluímos
 * uma única vez o novo vocabulário padrão solicitado para notícias e vídeos.
 */
object VideoTermStore {
    private const val KEY_TERMS = "video_terms_v400"
    private const val KEY_INITIALIZED = "video_terms_v400_initialized"
    private const val KEY_DEFAULTS_V7_MIGRATED = "video_terms_v7_defaults_migrated"

    fun load(context: Context, seedTerms: List<String>): List<String> {
        val prefs = context.getSharedPreferences(BackgroundMonitor.PREFS, 0)
        if (!prefs.getBoolean(KEY_INITIALIZED, false)) {
            val seed = clean(seedTerms + DEFAULT_MONITOR_TERMS)
            prefs.edit()
                .putStringSet(KEY_TERMS, seed.toSet())
                .putBoolean(KEY_INITIALIZED, true)
                .putBoolean(KEY_DEFAULTS_V7_MIGRATED, true)
                .apply()
            return seed
        }

        val current = clean(prefs.getStringSet(KEY_TERMS, emptySet()).orEmpty().toList())
        if (!prefs.getBoolean(KEY_DEFAULTS_V7_MIGRATED, false)) {
            val merged = clean(current + DEFAULT_MONITOR_TERMS)
            prefs.edit()
                .putStringSet(KEY_TERMS, merged.toSet())
                .putBoolean(KEY_DEFAULTS_V7_MIGRATED, true)
                .apply()
            return merged
        }
        return current
    }

    fun add(context: Context, value: String, seedTerms: List<String>): List<String> {
        val current = load(context, seedTerms).toMutableList()
        val cleanValue = value.trim()
        if (cleanValue.isNotBlank() && current.none { it.equals(cleanValue, ignoreCase = true) }) {
            current += cleanValue
        }
        return save(context, current)
    }

    fun remove(context: Context, value: String, seedTerms: List<String>): List<String> {
        val next = load(context, seedTerms).filterNot { it.equals(value.trim(), ignoreCase = true) }
        return save(context, next)
    }

    private fun save(context: Context, values: List<String>): List<String> {
        val cleanValues = clean(values)
        context.getSharedPreferences(BackgroundMonitor.PREFS, 0)
            .edit()
            .putStringSet(KEY_TERMS, cleanValues.toSet())
            .putBoolean(KEY_INITIALIZED, true)
            .putBoolean(KEY_DEFAULTS_V7_MIGRATED, true)
            .apply()
        return cleanValues
    }

    private fun clean(values: List<String>): List<String> = values
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
        .sortedWith(String.CASE_INSENSITIVE_ORDER)
}
''', encoding='utf-8')


# ---------------------------------------------------------------------------
# Restore the 8 specialized sources. 121 general + 8 specialized = 129,
# matching the dashboard/source design target.
# ---------------------------------------------------------------------------
source_path = ROOT / 'app/src/main/java/br/com/monitordenoticias/android/SourceCatalog.kt'
source = source_path.read_text(encoding='utf-8')
specialized_block = '''    val specialized = listOf(
        MediaSource(
            id = "especializada-defesatv",
            name = "DefesaTV",
            region = NATIONAL_REGION,
            state = "BR",
            stateName = "Brasil",
            group = "Mídia especializada",
            aliases = listOf("Defesa TV", "defesa.tv.br")
        ),
        MediaSource(
            id = "especializada-poder-naval",
            name = "Poder Naval",
            region = NATIONAL_REGION,
            state = "BR",
            stateName = "Brasil",
            group = "Mídia especializada",
            aliases = listOf("PoderNaval", "naval.com.br")
        ),
        MediaSource(
            id = "especializada-agencia-marinha",
            name = "Agência Marinha de Notícias",
            region = NATIONAL_REGION,
            state = "BR",
            stateName = "Brasil",
            group = "Mídia especializada",
            aliases = listOf("Agência Marinha", "agencia.marinha.mil.br")
        ),
        MediaSource(
            id = "especializada-forcas-terrestres",
            name = "Forças Terrestres",
            region = NATIONAL_REGION,
            state = "BR",
            stateName = "Brasil",
            group = "Mídia especializada",
            aliases = listOf("Forcas Terrestres", "forte.jor.br")
        ),
        MediaSource(
            id = "especializada-poder-aereo",
            name = "Poder Aéreo",
            region = NATIONAL_REGION,
            state = "BR",
            stateName = "Brasil",
            group = "Mídia especializada",
            aliases = listOf("Poder Aereo", "aereo.jor.br")
        ),
        MediaSource(
            id = "especializada-defesanet",
            name = "DefesaNet",
            region = NATIONAL_REGION,
            state = "BR",
            stateName = "Brasil",
            group = "Mídia especializada",
            aliases = listOf("Defesa Net", "defesanet.com.br")
        ),
        MediaSource(
            id = "especializada-tecnologia-defesa",
            name = "Tecnologia & Defesa",
            region = NATIONAL_REGION,
            state = "BR",
            stateName = "Brasil",
            group = "Mídia especializada",
            aliases = listOf("Tecnologia e Defesa", "tecnodefesa.com.br")
        ),
        MediaSource(
            id = "especializada-agencia-forca-aerea",
            name = "Agência Força Aérea",
            region = NATIONAL_REGION,
            state = "BR",
            stateName = "Brasil",
            group = "Mídia especializada",
            aliases = listOf("Agência FAB", "Agencia Forca Aerea", "fab.mil.br")
        )
    )

'''
source = replace_once(
    source,
    '    val all: List<MediaSource> = (national + byState).distinctBy { it.id }',
    specialized_block + '    val all: List<MediaSource> = (national + byState + specialized).distinctBy { it.id }',
    'SourceCatalog specialized sources'
)
source_path.write_text(source, encoding='utf-8')


# ---------------------------------------------------------------------------
# Desktop UI refinements.
# ---------------------------------------------------------------------------
dash_path = ROOT / 'desktop/src/main/kotlin/br/com/monitordenoticias/desktop/DashboardV5Main.kt'
dash = dash_path.read_text(encoding='utf-8')

dash = replace_once(
    dash,
    'SOURCES("Fontes", "Fontes nacionais, regionais, programas e canais oficiais", Icons.Default.Storage),',
    'SOURCES("Fontes", "Fontes nacionais, regionais e mídias especializadas", Icons.Default.Storage),',
    'Sources subtitle'
)

sidebar_new = '''@Composable
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
}'''
dash = replace_section(
    dash,
    '@Composable\nprivate fun V5Sidebar',
    '@Composable\nprivate fun V5SideStatus',
    sidebar_new,
    'Sidebar'
)

# Keep the chosen News typography scale globally, but improve the tiny sidebar status/badge text.
dash = transform_section(
    dash,
    '@Composable\nprivate fun V5SideStatus',
    '@Composable\nprivate fun V5HomeHeader',
    [('fontSize = 10.sp', 'fontSize = 11.sp')],
    'Sidebar supporting typography'
)

# Home readability: raise only the smallest labels and give schedules enough room.
dash = transform_section(
    dash,
    '@Composable\nprivate fun V5Home(',
    '@Composable\nprivate fun V5Metric',
    [
        ('fontSize = 8.sp', 'fontSize = 10.sp'),
        ('fontSize = 9.sp', 'fontSize = 10.sp'),
        ('fontSize = 10.sp', 'fontSize = 11.sp'),
        ('V5Card(Modifier.weight(1f).height(160.dp))', 'V5Card(Modifier.weight(1f).height(188.dp))'),
        ('Text("Rotinas independentes e configuráveis."', 'Text("O sistema executa buscas automaticamente nos horários definidos."')
    ],
    'Home readability'
)

# Quick actions now use the same readable scale as the News screen.
dash = transform_section(
    dash,
    '@Composable\nprivate fun V5Action',
    '@Composable\nprivate fun V5MiniSchedule',
    [('fontSize = 11.sp', 'fontSize = 12.sp'), ('fontSize = 8.sp', 'fontSize = 10.sp')],
    'Quick action typography'
)

mini_schedule_new = '''@Composable
private fun V5MiniSchedule(title: String, value: String, accent: Color, modifier: Modifier) {
    Surface(modifier.height(78.dp), color = Color(0xFFF7FAFE), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, V5Border)) {
        Column(Modifier.padding(horizontal = 11.dp, vertical = 10.dp)) {
            Text(title, color = V5Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(value, color = accent, fontSize = 11.sp, lineHeight = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}'''
dash = replace_section(
    dash,
    '@Composable\nprivate fun V5MiniSchedule',
    '@Composable\nprivate fun V5Summary',
    mini_schedule_new,
    'Mini schedule'
)

dash = transform_section(
    dash,
    '@Composable\nprivate fun V5ActivityLine',
    '@Composable\nprivate fun V5NewsScreen',
    [('fontSize = 9.sp', 'fontSize = 10.sp'), ('Modifier.width(66.dp)', 'Modifier.width(76.dp)')],
    'Activity typography'
)

# Video page had more tiny labels than the News page; align the lower bound.
dash = transform_section(
    dash,
    '@Composable\nprivate fun V5VideosScreen',
    '@Composable\nprivate fun V5StopScreen',
    [('fontSize = 8.sp', 'fontSize = 10.sp'), ('fontSize = 9.sp', 'fontSize = 10.sp'), ('fontSize = 10.sp', 'fontSize = 11.sp')],
    'Video screen typography'
)

sources_new = '''@Composable
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

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        V5Card(Modifier.fillMaxWidth()) {
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
                V5SearchBox(query, { query = it }, "Pesquisar fonte...", Modifier.width(400.dp))
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Região", color = V5Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(65.dp))
                SourceCatalog.regions.forEach { r ->
                    V5FilterButton(r, region == r) { region = r; state = "Todos" }
                    Spacer(Modifier.width(5.dp))
                }
            }

            Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Estado", color = V5Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(65.dp))
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    V5FilterButton("Todos", state == "Todos") { state = "Todos" }
                    Spacer(Modifier.width(5.dp))
                    SourceCatalog.states.filter { region == "Todas" || region == "Nacional" || it.third == region }.forEach { s ->
                        V5FilterButton(s.first, state == s.first) { state = s.first }
                        Spacer(Modifier.width(5.dp))
                    }
                }
            }

            when (tab) {
                V5SourceTab.NEWS -> {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        color = if (c.newsAllSources) Color(0xFFE5F8EF) else Color(0xFFF6F9FD),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (c.newsAllSources) V5Green.copy(alpha = .35f) else V5Border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    Spacer(Modifier.height(12.dp))
                    Surface(color = Color(0xFFF8F5FF), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, V5Purple.copy(alpha = .18f)), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.YouTube, null, tint = V5Red, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Fontes oficiais do YouTube incluem g1 e Domingo Espetacular, além dos canais já existentes.", color = V5Ink, fontSize = 11.sp)
                        }
                    }
                }
                V5SourceTab.SPECIAL -> {
                    Spacer(Modifier.height(12.dp))
                    Surface(color = Color(0xFFFFF8E8), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, V5Gold.copy(alpha = .28f)), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Verified, null, tint = Color(0xFFC88700), modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Mídia especializada: 8 veículos focados em Defesa, Forças Armadas e assuntos navais.", color = V5Ink, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        V5Card(Modifier.fillMaxWidth(), padding = 13.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storage, null, tint = V5Blue, modifier = Modifier.size(31.dp))
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

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
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
}'''
dash = replace_section(
    dash,
    '@Composable\nprivate fun V5SourcesScreen',
    '@Composable\nprivate fun V5TabButton',
    sources_new,
    'Sources screen'
)

# Source controls should visually match the News tab instead of using micro-text.
dash = transform_section(
    dash,
    '@Composable\nprivate fun V5TabButton',
    '@Composable\nprivate fun V5DemandsScreen',
    [
        ('Modifier.height(40.dp)', 'Modifier.height(44.dp)'),
        ('fontSize = 10.sp', 'fontSize = 12.sp'),
        ('Modifier.height(35.dp)', 'Modifier.height(38.dp)'),
        ('fontSize = 9.sp', 'fontSize = 10.sp'),
        ('fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1', 'fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1'),
        ('fontSize = 9.sp, maxLines = 1', 'fontSize = 10.sp, maxLines = 1')
    ],
    'Source component typography'
)

# Demands/settings had several labels smaller than the News page.
dash = transform_section(
    dash,
    '@Composable\nprivate fun V5DemandsScreen',
    '@Composable\nprivate fun V5HistoryScreen',
    [('fontSize = 8.sp', 'fontSize = 10.sp'), ('fontSize = 9.sp', 'fontSize = 10.sp'), ('fontSize = 10.sp', 'fontSize = 11.sp')],
    'Demands typography'
)

dash = transform_section(
    dash,
    '@Composable\nprivate fun V5SettingsScreen',
    '@Composable\nprivate fun V5AutomationCard',
    [('fontSize = 9.sp', 'fontSize = 10.sp'), ('fontSize = 10.sp', 'fontSize = 11.sp')],
    'Settings typography'
)

dash = transform_section(
    dash,
    '@Composable\nprivate fun V5AutomationCard',
    '@Composable\nprivate fun V5SourcesScreen',
    [('fontSize = 9.sp', 'fontSize = 10.sp'), ('fontSize = 10.sp', 'fontSize = 11.sp')],
    'Automation card typography'
)

# Shared form/filter components are used across the program; align them to 11/12sp.
dash = transform_section(
    dash,
    '@Composable\nprivate fun V5SearchBox',
    '@Composable\nprivate fun V5EmptyState',
    [
        ('fontSize = 9.sp', 'fontSize = 10.sp'),
        ('fontSize = 10.sp', 'fontSize = 11.sp'),
        ('fontSize = 11.sp', 'fontSize = 12.sp'),
        ('Modifier.height(36.dp)', 'Modifier.height(38.dp)')
    ],
    'Shared controls typography'
)

# Badges/tags/link controls no longer use 8/9sp text.
dash = transform_section(
    dash,
    '@Composable\nprivate fun V5LinkButtons',
    '@Composable\nprivate fun V5TermsScreen',
    [('fontSize = 8.sp', 'fontSize = 10.sp'), ('fontSize = 9.sp', 'fontSize = 10.sp'), ('fontSize = 10.sp', 'fontSize = 11.sp')],
    'Links and tags typography'
)

# Footer needs a little more vertical room after typography normalization.
dash = transform_section(
    dash,
    '@Composable\nprivate fun V5Footer',
    'private fun V5Open(',
    [('height(40.dp)', 'height(42.dp)'), ('fontSize = 9.sp', 'fontSize = 10.sp')],
    'Footer readability'
)

dash_path.write_text(dash, encoding='utf-8')

print('V7 refinement applied successfully.')
