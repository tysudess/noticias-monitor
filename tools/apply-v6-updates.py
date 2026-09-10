from pathlib import Path
import re
import struct

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


def write(path, text):
    p = ROOT / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding="utf-8", newline="\n")


def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f"pattern not found: {label}")
    return text.replace(old, new, 1)


# ---------------------------------------------------------------------------
# Controller: cancelamento real das buscas e migração das novas fontes YouTube
# ---------------------------------------------------------------------------
controller_path = "desktop/src/main/kotlin/br/com/monitordenoticias/desktop/DesktopControllerV5.kt"
c = read(controller_path)

if "desktop_video_sources_v6_migrated" not in c:
    c = replace_once(
        c,
        "    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)\n",
        "    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)\n"
        "    @Volatile private var newsJob: Job? = null\n"
        "    @Volatile private var videoJob: Job? = null\n",
        "controller jobs",
    )

    c = replace_once(
        c,
        "    init {\n        applyProxySettings()\n",
        "    init {\n        migrateDesktopVideoSources()\n        applyProxySettings()\n",
        "controller init migration",
    )

    marker = "    fun saveProxy(enabled: Boolean, host: String, port: Int, username: String, password: String) {"
    migration = '''    private fun migrateDesktopVideoSources() {
        val migrationKey = "desktop_video_sources_v6_migrated"
        if (prefs.getBoolean(migrationKey, false)) return
        selectedVideoSourceIds = selectedVideoSourceIds + DesktopVideoSources.extras.map { it.id }
        prefs.edit().putBoolean(migrationKey, true).apply()
    }

'''
    c = replace_once(c, marker, migration + marker, "controller migration function")

    c = replace_once(
        c,
        "    fun searchNews(from: Long? = null, to: Long? = null) {\n        if (newsBusy) return\n        scope.launch {\n",
        "    fun searchNews(from: Long? = null, to: Long? = null) {\n        if (newsBusy) return\n        val job = scope.launch(start = CoroutineStart.LAZY) {\n",
        "searchNews launch",
    )
    c = replace_once(
        c,
        "            } catch (t: Throwable) {\n                status = \"Falha na busca de notícias: ${t.message ?: t.javaClass.simpleName}\"\n",
        "            } catch (_: CancellationException) {\n                status = \"⏹ Busca de notícias interrompida pelo usuário.\"\n"
        "            } catch (t: Throwable) {\n                status = \"Falha na busca de notícias: ${t.message ?: t.javaClass.simpleName}\"\n",
        "searchNews cancellation",
    )
    c = replace_once(
        c,
        "        }\n    }\n\n    fun searchDemand(demand: Demand) {",
        "        }\n        newsJob = job\n        job.start()\n    }\n\n    fun searchDemand(demand: Demand) {",
        "searchNews start",
    )

    c = replace_once(
        c,
        "    fun searchDemand(demand: Demand) {\n        if (newsBusy) return\n        scope.launch {\n",
        "    fun searchDemand(demand: Demand) {\n        if (newsBusy) return\n        val job = scope.launch(start = CoroutineStart.LAZY) {\n",
        "searchDemand launch",
    )
    c = replace_once(
        c,
        "            } catch (t: Throwable) {\n                status = \"Falha na demanda: ${t.message ?: t.javaClass.simpleName}\"\n",
        "            } catch (_: CancellationException) {\n                status = \"⏹ Busca de demanda interrompida pelo usuário.\"\n"
        "            } catch (t: Throwable) {\n                status = \"Falha na demanda: ${t.message ?: t.javaClass.simpleName}\"\n",
        "searchDemand cancellation",
    )
    c = replace_once(
        c,
        "        }\n    }\n\n    fun searchAllDemands() {",
        "        }\n        newsJob = job\n        job.start()\n    }\n\n    fun searchAllDemands() {",
        "searchDemand start",
    )

    c = replace_once(
        c,
        "    fun searchAllDemands() {\n        if (newsBusy) return\n        scope.launch {\n",
        "    fun searchAllDemands() {\n        if (newsBusy) return\n        val job = scope.launch(start = CoroutineStart.LAZY) {\n",
        "searchAllDemands launch",
    )
    c = replace_once(
        c,
        "            } catch (t: Throwable) {\n                status = \"Falha nas demandas: ${t.message ?: t.javaClass.simpleName}\"\n",
        "            } catch (_: CancellationException) {\n                status = \"⏹ Busca de demandas interrompida pelo usuário.\"\n"
        "            } catch (t: Throwable) {\n                status = \"Falha nas demandas: ${t.message ?: t.javaClass.simpleName}\"\n",
        "searchAllDemands cancellation",
    )
    c = replace_once(
        c,
        "        }\n    }\n\n    fun searchVideos(from: Long? = null, to: Long? = null) {",
        "        }\n        newsJob = job\n        job.start()\n    }\n\n    fun searchVideos(from: Long? = null, to: Long? = null) {",
        "searchAllDemands start",
    )

    c = replace_once(
        c,
        "    fun searchVideos(from: Long? = null, to: Long? = null) {\n        if (videoBusy) return\n        scope.launch {\n",
        "    fun searchVideos(from: Long? = null, to: Long? = null) {\n        if (videoBusy) return\n        val job = scope.launch(start = CoroutineStart.LAZY) {\n",
        "searchVideos launch",
    )
    c = replace_once(
        c,
        "            } catch (t: Throwable) {\n                videoStatus = \"Falha na busca de vídeos: ${t.message ?: t.javaClass.simpleName}\"\n",
        "            } catch (_: CancellationException) {\n                videoStatus = \"⏹ Busca de vídeos interrompida pelo usuário.\"\n"
        "            } catch (t: Throwable) {\n                videoStatus = \"Falha na busca de vídeos: ${t.message ?: t.javaClass.simpleName}\"\n",
        "searchVideos cancellation",
    )
    c = replace_once(
        c,
        "        }\n    }\n\n    fun addTerm(value: String) {",
        "        }\n        videoJob = job\n        job.start()\n    }\n\n"
        "    fun stopNewsSearch() {\n"
        "        val job = newsJob\n"
        "        if (job?.isActive == true) {\n"
        "            status = \"⏹ Interrompendo busca de notícias/demandas...\"\n"
        "            job.cancel(CancellationException(\"Interrompida pelo usuário\"))\n"
        "        }\n"
        "    }\n\n"
        "    fun stopVideoSearch() {\n"
        "        val job = videoJob\n"
        "        if (job?.isActive == true) {\n"
        "            videoStatus = \"⏹ Interrompendo busca de vídeos...\"\n"
        "            job.cancel(CancellationException(\"Interrompida pelo usuário\"))\n"
        "        }\n"
        "    }\n\n"
        "    fun stopAllSearches() {\n"
        "        stopNewsSearch()\n"
        "        stopVideoSearch()\n"
        "    }\n\n"
        "    fun addTerm(value: String) {",
        "searchVideos start + stop methods",
    )

write(controller_path, c)


# ---------------------------------------------------------------------------
# YouTube: varrer primeiro a aba /videos do canal e usar RSS apenas como apoio.
# Também adicionamos checkpoints de cancelamento entre fontes/consultas.
# ---------------------------------------------------------------------------
repo_path = "app/src/main/java/br/com/monitordenoticias/android/VideoRepository.kt"
r = read(repo_path)

if "parseYoutubeVideosTab" not in r:
    r = replace_once(
        r,
        "import kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.withContext\n",
        "import kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.currentCoroutineContext\nimport kotlinx.coroutines.ensureActive\nimport kotlinx.coroutines.withContext\n",
        "coroutine imports",
    )

    r = replace_once(
        r,
        "            plan.forEach { (source, specs) ->\n",
        "            plan.forEach { (source, specs) ->\n                currentCoroutineContext().ensureActive()\n",
        "cancel checkpoint source",
    )
    r = replace_once(
        r,
        "                specs.forEach specLoop@ { spec ->\n",
        "                specs.forEach specLoop@ { spec ->\n                    currentCoroutineContext().ensureActive()\n",
        "cancel checkpoint spec",
    )

    youtube_impl = r'''    private fun fetchYoutube(source: VideoSource, capturedAt: Long): List<VideoItem> {
        val handle = source.youtubeHandle.removePrefix("@").trim()
        val knownChannelId = when (source.id) {
            "youtube-g1" -> "UCaGmdJSSiR7fkh2A-c6emsA"
            "youtube-domingo-espetacular" -> "UCP-Vg2PcmLiWpEdvMI1R35w"
            else -> ""
        }
        val videosUrl = when {
            knownChannelId.isNotBlank() -> "https://www.youtube.com/channel/$knownChannelId/videos"
            handle.isNotBlank() -> "https://www.youtube.com/@$handle/videos"
            source.landingUrl.contains("/videos", ignoreCase = true) -> source.landingUrl
            else -> source.landingUrl.trimEnd('/') + "/videos"
        }

        // Caminho principal: a própria aba VÍDEOS do canal. Isso evita depender
        // exclusivamente do feed RSS e segue exatamente a navegação que o usuário faria.
        val channelPage = Jsoup.connect(videosUrl)
            .userAgent(BROWSER_USER_AGENT)
            .header("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.7")
            .referrer("https://www.youtube.com/")
            .timeout(18_000)
            .maxBodySize(MAX_HTML_BODY_BYTES)
            .followRedirects(true)
            .get()
            .html()

        val tabItems = parseYoutubeVideosTab(source, channelPage, capturedAt)

        // RSS fica como apoio para datas/descrições e como fallback quando o HTML
        // da aba muda. Para g1 e Domingo Espetacular usamos IDs oficiais conhecidos,
        // eliminando a dependência de descobrir o channelId no HTML.
        val channelId = knownChannelId.ifBlank {
            YOUTUBE_CHANNEL_ID_REGEXES.asSequence()
                .mapNotNull { regex -> regex.find(channelPage)?.groupValues?.getOrNull(1) }
                .firstOrNull { it.startsWith("UC") }
                .orEmpty()
        }

        val feedItems = if (channelId.isNotBlank()) {
            runCatching {
                val feed = Jsoup.connect("https://www.youtube.com/feeds/videos.xml?channel_id=$channelId")
                    .userAgent(BROWSER_USER_AGENT)
                    .timeout(16_000)
                    .parser(Parser.xmlParser())
                    .get()

                feed.select("entry").mapNotNull { entry ->
                    val title = cleanText(entry.selectFirst("title")?.text().orEmpty())
                    val link = canonicalizeUrl(entry.selectFirst("link[href]")?.attr("href").orEmpty())
                    if (!usefulTitle(title) || !isYoutubeVideoUrl(link)) return@mapNotNull null

                    val published = runCatching {
                        Instant.parse(entry.selectFirst("published")?.text().orEmpty()).toEpochMilli()
                    }.getOrDefault(capturedAt)
                    val description = sequenceOf(
                        entry.getElementsByTag("media:description").firstOrNull()?.text().orEmpty(),
                        entry.selectFirst("description")?.text().orEmpty()
                    ).map(::cleanText).firstOrNull { it.isNotBlank() }.orEmpty()

                    VideoItem(
                        title = title.take(220),
                        sourceId = source.id,
                        sourceName = source.name,
                        publishedAt = published,
                        link = link,
                        summary = listOf(description, "Canal oficial • ${source.group}")
                            .filter { it.isNotBlank() }.distinct().joinToString(" • ").take(1000),
                        capturedAt = capturedAt
                    )
                }
            }.getOrDefault(emptyList())
        } else emptyList()

        // Preferimos os itens do feed quando o mesmo vídeo também foi achado na aba,
        // pois o feed traz a data exata. Vídeos adicionais da aba entram em seguida.
        return (feedItems + tabItems)
            .distinctBy { canonicalKey(it.link) }
            .take(MAX_YOUTUBE_ITEMS_PER_SCAN)
    }

    private fun parseYoutubeVideosTab(source: VideoSource, html: String, capturedAt: Long): List<VideoItem> {
        if (html.isBlank()) return emptyList()
        val out = linkedMapOf<String, VideoItem>()
        val idRegex = Regex("\\\"videoId\\\":\\\"([0-9A-Za-z_-]{6,})\\\"")
        val runTitleRegex = Regex("\\\"title\\\"\\s*:\\s*\\{\\s*\\\"runs\\\"\\s*:\\s*\\[\\s*\\{\\s*\\\"text\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"")
        val simpleTitleRegex = Regex("\\\"title\\\"\\s*:\\s*\\{\\s*\\\"simpleText\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"")
        val publishedRegex = Regex("\\\"publishedTimeText\\\"\\s*:\\s*\\{\\s*\\\"simpleText\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"")

        idRegex.findAll(html).take(MAX_YOUTUBE_ITEMS_PER_SCAN * 5).forEach { match ->
            val videoId = match.groupValues[1]
            if (videoId.isBlank()) return@forEach
            val from = match.range.first
            val to = (from + 4200).coerceAtMost(html.length)
            val block = html.substring(from, to)
            val titleRaw = runTitleRegex.find(block)?.groupValues?.getOrNull(1)
                ?: simpleTitleRegex.find(block)?.groupValues?.getOrNull(1)
                ?: return@forEach
            val title = cleanJsonText(titleRaw)
            if (!usefulTitle(title)) return@forEach
            val publishedText = publishedRegex.find(block)?.groupValues?.getOrNull(1)?.let(::cleanJsonText).orEmpty()
            val publishedAt = parseYoutubeRelativeTime(publishedText, capturedAt)
            val link = canonicalizeUrl("https://www.youtube.com/watch?v=$videoId")
            out.putIfAbsent(
                canonicalKey(link),
                VideoItem(
                    title = title.take(220),
                    sourceId = source.id,
                    sourceName = source.name,
                    publishedAt = publishedAt,
                    link = link,
                    summary = "Canal oficial • ${source.group} • Aba Vídeos",
                    capturedAt = capturedAt
                )
            )
        }
        return out.values.take(MAX_YOUTUBE_ITEMS_PER_SCAN)
    }

    private fun parseYoutubeRelativeTime(raw: String, capturedAt: Long): Long {
        val text = normalize(raw)
        val n = Regex("(?:ha )?(\\d+)").find(text)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: return capturedAt
        val millis = when {
            "minuto" in text || "minute" in text -> n * 60_000L
            "hora" in text || "hour" in text -> n * 60L * 60_000L
            "dia" in text || "day" in text -> n * 24L * 60L * 60_000L
            "semana" in text || "week" in text -> n * 7L * 24L * 60L * 60_000L
            "mes" in text || "month" in text -> n * 30L * 24L * 60L * 60_000L
            "ano" in text || "year" in text -> n * 365L * 24L * 60L * 60_000L
            else -> 0L
        }
        return (capturedAt - millis).coerceAtLeast(1L)
    }

'''

    pattern = re.compile(r"    private fun fetchYoutube\(source: VideoSource, capturedAt: Long\): List<VideoItem> \{.*?\n    private fun isSpecificVideoUrl", re.S)
    if not pattern.search(r):
        raise SystemExit("pattern not found: fetchYoutube function")
    r = pattern.sub(youtube_impl + "    private fun isSpecificVideoUrl", r, count=1)

write(repo_path, r)


# ---------------------------------------------------------------------------
# Fontes extras: URLs oficiais por channel ID, sempre apontando para /videos.
# ---------------------------------------------------------------------------
sources_path = "desktop/src/main/kotlin/br/com/monitordenoticias/desktop/DesktopVideoSources.kt"
s = read(sources_path)
s = s.replace(
    'landingUrl = "https://www.youtube.com/@g1/videos"',
    'landingUrl = "https://www.youtube.com/channel/UCaGmdJSSiR7fkh2A-c6emsA/videos"'
)
s = s.replace(
    'landingUrl = "https://www.youtube.com/@domingoespetacular/videos"',
    'landingUrl = "https://www.youtube.com/channel/UCP-Vg2PcmLiWpEdvMI1R35w/videos"'
)
write(sources_path, s)


# ---------------------------------------------------------------------------
# UI: fonte global discretamente maior, aba Parar buscas, botão inline e painel
# das fontes instáveis. Não removemos nenhuma funcionalidade existente.
# ---------------------------------------------------------------------------
ui_path = "desktop/src/main/kotlin/br/com/monitordenoticias/desktop/DashboardV5Main.kt"
u = read(ui_path)

if "STOP(\"Parar buscas\"" not in u:
    u = replace_once(
        u,
        "import androidx.compose.ui.graphics.vector.rememberVectorPainter\n",
        "import androidx.compose.ui.graphics.vector.rememberVectorPainter\nimport androidx.compose.ui.platform.LocalDensity\n",
        "LocalDensity import",
    )
    u = replace_once(
        u,
        "import androidx.compose.ui.unit.dp\n",
        "import androidx.compose.ui.unit.Density\nimport androidx.compose.ui.unit.dp\n",
        "Density import",
    )
    u = replace_once(
        u,
        "    TERMS(\"Termos\", \"Termos independentes para notícias e vídeos\", Icons.Default.Search),\n    SETTINGS",
        "    TERMS(\"Termos\", \"Termos independentes para notícias e vídeos\", Icons.Default.Search),\n"
        "    STOP(\"Parar buscas\", \"Interrompa buscas manuais em andamento\", Icons.Default.StopCircle),\n"
        "    SETTINGS",
        "STOP enum",
    )
    u = replace_once(
        u,
        "            Item(\"Buscar demandas agora\", onClick = { controller.searchAllDemands() })\n            Separator()\n",
        "            Item(\"Buscar demandas agora\", onClick = { controller.searchAllDemands() })\n"
        "            Item(\"Parar buscas\", onClick = { controller.stopAllSearches() })\n"
        "            Separator()\n",
        "tray stop",
    )
    u = replace_once(
        u,
        "        ) {\n            V5App(controller)\n        }\n",
        "        ) {\n            val density = LocalDensity.current\n"
        "            CompositionLocalProvider(\n"
        "                LocalDensity provides Density(density.density, density.fontScale * 1.08f)\n"
        "            ) {\n"
        "                V5App(controller)\n"
        "            }\n"
        "        }\n",
        "global font scale",
    )
    u = replace_once(
        u,
        "                            V5Section.TERMS -> V5TermsScreen(c, tick)\n                            V5Section.SETTINGS -> V5SettingsScreen(c, tick)\n",
        "                            V5Section.TERMS -> V5TermsScreen(c, tick)\n"
        "                            V5Section.STOP -> V5StopScreen(c, tick)\n"
        "                            V5Section.SETTINGS -> V5SettingsScreen(c, tick)\n",
        "STOP navigation",
    )

    news_exec = '        V5ExecutionPanel("Notícias", c.newsBusy, c.newsProgress, c.status, c.newNewsLinks.size, c.lastNewsSearchDurationMs, tick)\n'
    if news_exec in u:
        u = u.replace(
            news_exec,
            news_exec + '''        if (c.newsBusy) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = { c.stopNewsSearch() }) {
                    Icon(Icons.Default.StopCircle, null, tint = V5Red)
                    Spacer(Modifier.width(6.dp))
                    Text("Parar busca", color = V5Red, fontWeight = FontWeight.Bold)
                }
            }
        }
''',
            1,
        )

    video_exec = '        V5ExecutionPanel("Vídeos", c.videoBusy, c.videoProgress, c.videoStatus, c.newVideoLinks.size, c.lastVideoSearchDurationMs, tick)\n'
    if video_exec in u:
        u = u.replace(
            video_exec,
            video_exec + '''        if (c.videoBusy) {
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
''',
            1,
        )

    u = u.replace(
        '                V5ActivityLine("Vídeos", c.videoStatus, V5Purple)\n',
        '                V5ActivityLine("Vídeos", c.videoStatus, V5Purple)\n'
        '                V5ActivityLine("Fontes instáveis", if (c.unstableVideoSources.isEmpty()) "Nenhuma" else c.unstableVideoSources.joinToString(", ") { it.sourceName }, if (c.unstableVideoSources.isEmpty()) V5Green else V5Orange)\n',
        1,
    )

    insert_before = "@Composable\nprivate fun V5ExecutionPanel("
    stop_and_unstable = r'''@Composable
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
    V5Card(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.WarningAmber, null, tint = V5Orange, modifier = Modifier.size(23.dp))
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text("Fontes que apresentaram instabilidade", color = V5Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Text("A lista corresponde à última busca de vídeos e não remove a fonte da seleção.", color = V5Muted, fontSize = 10.sp)
            }
            Text("${issues.size} fonte(s)", color = V5Orange, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(9.dp))
        issues.take(10).forEach { issue ->
            Surface(color = Color(0xFFFFF7E8), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Row(Modifier.padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(issue.sourceName, color = V5Ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${issue.stage} • ${issue.failureCount} falha(s)", color = V5Orange, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

'''
    if insert_before not in u:
        raise SystemExit("pattern not found: V5ExecutionPanel insertion")
    u = u.replace(insert_before, stop_and_unstable + insert_before, 1)

write(ui_path, u)


# ---------------------------------------------------------------------------
# Ícone nativo do Windows: desenho próprio do Monitor de Notícias.
# Gera um ICO 256x256 sem dependências externas.
# ---------------------------------------------------------------------------
def build_icon(size=256):
    navy = (8, 48, 88, 255)
    gold = (255, 183, 49, 255)
    white = (244, 249, 255, 255)
    blue = (21, 102, 181, 255)
    transparent = (0, 0, 0, 0)
    pixels = []
    radius = int(size * 0.18)
    for y in range(size):
        row = []
        for x in range(size):
            # Rounded navy tile.
            inside = True
            for cx, cy in ((radius, radius), (size-radius-1, radius), (radius, size-radius-1), (size-radius-1, size-radius-1)):
                if (x < radius if cx == radius else x >= size-radius) and (y < radius if cy == radius else y >= size-radius):
                    if (x-cx)**2 + (y-cy)**2 > radius**2:
                        inside = False
            color = navy if inside else transparent
            # Gold newspaper body.
            if int(size*.20) <= x <= int(size*.80) and int(size*.22) <= y <= int(size*.78):
                color = gold
            # Blue header line.
            if int(size*.27) <= x <= int(size*.73) and int(size*.31) <= y <= int(size*.39):
                color = blue
            # White story block and lines.
            if int(size*.28) <= x <= int(size*.44) and int(size*.46) <= y <= int(size*.64):
                color = white
            if int(size*.50) <= x <= int(size*.71) and int(size*.47) <= y <= int(size*.51):
                color = white
            if int(size*.50) <= x <= int(size*.71) and int(size*.56) <= y <= int(size*.60):
                color = white
            if int(size*.50) <= x <= int(size*.66) and int(size*.65) <= y <= int(size*.69):
                color = white
            row.append(color)
        pixels.append(row)

    # ICO DIB: BGRA bottom-up + 1-bit AND mask.
    xor = bytearray()
    for row in reversed(pixels):
        for rr, gg, bb, aa in row:
            xor += bytes((bb, gg, rr, aa))
    mask_row = ((size + 31) // 32) * 4
    and_mask = bytes(mask_row * size)
    dib = struct.pack("<IIIHHIIIIII", 40, size, size * 2, 1, 32, 0, len(xor), 0, 0, 0, 0) + xor + and_mask
    icon_dir = struct.pack("<HHH", 0, 1, 1)
    w = 0 if size >= 256 else size
    h = 0 if size >= 256 else size
    entry = struct.pack("<BBBBHHII", w, h, 0, 0, 1, 32, len(dib), 6 + 16)
    return icon_dir + entry + dib

icon_path = ROOT / "desktop/src/main/resources/monitor-icon.ico"
icon_path.parent.mkdir(parents=True, exist_ok=True)
icon_path.write_bytes(build_icon())

svg_path = ROOT / "desktop/src/main/resources/monitor-icon.svg"
svg_path.write_text('''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256">
<rect width="256" height="256" rx="46" fill="#083058"/>
<rect x="51" y="56" width="154" height="144" rx="10" fill="#FFB731"/>
<rect x="69" y="79" width="118" height="21" rx="4" fill="#1566B5"/>
<rect x="72" y="118" width="42" height="48" rx="5" fill="#F4F9FF"/>
<rect x="128" y="120" width="54" height="10" rx="5" fill="#F4F9FF"/>
<rect x="128" y="143" width="54" height="10" rx="5" fill="#F4F9FF"/>
<rect x="128" y="166" width="42" height="10" rx="5" fill="#F4F9FF"/>
</svg>\n''', encoding="utf-8")

build_path = "desktop/build.gradle.kts"
b = read(build_path)
if "monitor-icon.ico" not in b:
    b = replace_once(
        b,
        "            windows {\n                menuGroup = \"Monitor de Notícias\"\n",
        "            windows {\n                iconFile.set(project.file(\"src/main/resources/monitor-icon.ico\"))\n"
        "                menuGroup = \"Monitor de Notícias\"\n",
        "native icon",
    )
write(build_path, b)

print("V6 updates applied successfully")
