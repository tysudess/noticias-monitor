package br.com.monitordenoticias.desktop

import android.content.Context
import br.com.monitordenoticias.android.*
import kotlinx.coroutines.*
import org.jsoup.Jsoup
import java.net.Authenticator
import java.net.PasswordAuthentication
import java.net.URI
import java.net.URLDecoder
import java.security.Security
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Controlador da interface Desktop V5.
 * Mantém a lógica funcional da V4 e acrescenta as novas fontes oficiais do
 * YouTube do g1 e do Domingo Espetacular ao fluxo normal e automático.
 */
class DesktopControllerV5(
    val context: Context = Context(),
    private val notify: (String, String) -> Unit = { _, _ -> }
) : AutoCloseable {
    private val prefs = context.getSharedPreferences(BackgroundMonitor.PREFS, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var newsJob: Job? = null
    @Volatile private var videoJob: Job? = null
    val newsDb = NewsDb(context)
    val videoDb = VideoDb(context)
    private val newsRepository = NewsRepository(newsDb)
    private val videoRepository = VideoRepository(context, videoDb)

    @Volatile var news: List<News> = emptyList()
    @Volatile var videos: List<VideoItem> = emptyList()
    @Volatile var terms: List<String> = emptyList()
    @Volatile var videoTerms: List<String> = emptyList()
    @Volatile var demands: List<Demand> = emptyList()
    @Volatile var newsProgress: LiveSearchProgress = LiveSearchProgress()
    @Volatile var videoProgress: LiveSearchProgress = LiveSearchProgress()
    @Volatile var newsBusy = false
    @Volatile var videoBusy = false
    @Volatile var status = "Pronto"
    @Volatile var videoStatus = "Pronto"
    @Volatile var unstableVideoSources: List<VideoSourceIssue> = emptyList()
    @Volatile var newNewsLinks: Set<String> = emptySet()
        private set
    @Volatile var newVideoLinks: Set<String> = emptySet()
        private set
    @Volatile var lastNewsSearchDurationMs: Long = 0L
        private set
    @Volatile var lastVideoSearchDurationMs: Long = 0L
        private set

    var newsAllSources: Boolean
        get() = prefs.getBoolean("desktop_news_all_sources", true)
        set(v) { prefs.edit().putBoolean("desktop_news_all_sources", v).apply() }

    var selectedNewsSourceIds: Set<String>
        get() = prefs.getStringSet("desktop_news_source_ids", emptySet()).orEmpty()
        set(v) { prefs.edit().putStringSet("desktop_news_source_ids", v).apply() }

    var selectedVideoSourceIds: Set<String>
        get() = prefs.getStringSet("desktop_video_source_ids", DesktopVideoSources.defaultIds).orEmpty()
        set(v) { prefs.edit().putStringSet("desktop_video_source_ids", v).apply() }

    var automaticMonitoring: Boolean
        get() = prefs.getBoolean("desktop_automatic_monitoring", true)
        set(v) { prefs.edit().putBoolean("desktop_automatic_monitoring", v).apply() }

    var newsAutomatic: Boolean
        get() = prefs.getBoolean("desktop_news_automatic", true)
        set(v) { prefs.edit().putBoolean("desktop_news_automatic", v).apply() }

    var demandAutomatic: Boolean
        get() = prefs.getBoolean("desktop_demand_automatic", true)
        set(v) { prefs.edit().putBoolean("desktop_demand_automatic", v).apply() }

    var videoAutomatic: Boolean
        get() = prefs.getBoolean("desktop_video_automatic", true)
        set(v) { prefs.edit().putBoolean("desktop_video_automatic", v).apply() }

    var newsIntervalMinutes: Int
        get() = prefs.getInt("desktop_news_interval", 30).coerceAtLeast(15)
        set(v) { prefs.edit().putInt("desktop_news_interval", v.coerceAtLeast(15)).apply() }

    var demandIntervalMinutes: Int
        get() = prefs.getInt("desktop_demand_interval", 60).coerceAtLeast(15)
        set(v) { prefs.edit().putInt("desktop_demand_interval", v.coerceAtLeast(15)).apply() }

    var videoScheduleTimes: Set<String>
        get() = prefs.getStringSet("desktop_video_schedule_times", setOf("08:00", "12:00", "15:00", "19:00", "21:00")).orEmpty()
            .filter { runCatching { LocalTime.parse(it) }.isSuccess }.toSet()
        set(v) {
            val clean = v.mapNotNull { raw ->
                runCatching { LocalTime.parse(raw.trim()).format(DateTimeFormatter.ofPattern("HH:mm")) }.getOrNull()
            }.toSet()
            prefs.edit().putStringSet("desktop_video_schedule_times", clean).apply()
        }

    val lastNewsAutoAt: Long get() = prefs.getLong("desktop_auto_news_at", 0L)
    val lastDemandAutoAt: Long get() = prefs.getLong("desktop_auto_demands_at", 0L)
    val lastVideoAutoAt: Long get() = prefs.getLong("desktop_auto_video_at", 0L)

    var startWithWindows: Boolean
        get() = prefs.getBoolean("desktop_start_with_windows", false)
        set(v) {
            prefs.edit().putBoolean("desktop_start_with_windows", v).apply()
            configureWindowsStartup(v)
        }

    var proxyEnabled: Boolean
        get() = prefs.getBoolean("desktop_proxy_enabled", false)
        set(v) { prefs.edit().putBoolean("desktop_proxy_enabled", v).apply(); applyProxySettings() }

    var proxyHost: String
        get() = prefs.getString("desktop_proxy_host", "proxy-7db.mb").orEmpty().ifBlank { "proxy-7db.mb" }
        set(v) { prefs.edit().putString("desktop_proxy_host", v.trim()).apply() }

    var proxyPort: Int
        get() = prefs.getInt("desktop_proxy_port", 6060).coerceIn(1, 65535)
        set(v) { prefs.edit().putInt("desktop_proxy_port", v.coerceIn(1, 65535)).apply() }

    var proxyUsername: String
        get() = prefs.getString("desktop_proxy_username", "").orEmpty()
        set(v) { prefs.edit().putString("desktop_proxy_username", v).apply() }

    var proxyPassword: String
        get() = prefs.getString("desktop_proxy_password", "").orEmpty()
        set(v) { prefs.edit().putString("desktop_proxy_password", v).apply() }

    val proxyReady: Boolean
        get() = proxyEnabled && proxyHost.isNotBlank() && proxyPort in 1..65535 && proxyUsername.isNotBlank() && proxyPassword.isNotBlank()

    val proxyStatusLabel: String
        get() = when {
            !proxyEnabled -> "Proxy desativado"
            proxyReady -> "Proxy pronto"
            else -> "Proxy requer configuração"
        }

    init {
        migrateDesktopVideoSources()
        applyProxySettings()
        refresh()
        scope.launch { automationLoop() }
    }

    private fun migrateDesktopVideoSources() {
        val migrationKey = "desktop_video_sources_v6_migrated"
        if (prefs.getBoolean(migrationKey, false)) return
        selectedVideoSourceIds = selectedVideoSourceIds + DesktopVideoSources.extras.map { it.id }
        prefs.edit().putBoolean(migrationKey, true).apply()
    }

    fun saveProxy(enabled: Boolean, host: String, port: Int, username: String, password: String) {
        prefs.edit()
            .putBoolean("desktop_proxy_enabled", enabled)
            .putString("desktop_proxy_host", host.trim().ifBlank { "proxy-7db.mb" })
            .putInt("desktop_proxy_port", port.coerceIn(1, 65535))
            .putString("desktop_proxy_username", username.trim())
            .putString("desktop_proxy_password", password)
            .apply()
        applyProxySettings()
    }

    fun applyProxySettings() {
        val enabled = prefs.getBoolean("desktop_proxy_enabled", false)
        val host = prefs.getString("desktop_proxy_host", "proxy-7db.mb").orEmpty().ifBlank { "proxy-7db.mb" }
        val port = prefs.getInt("desktop_proxy_port", 6060).coerceIn(1, 65535)
        val user = prefs.getString("desktop_proxy_username", "").orEmpty()
        val pass = prefs.getString("desktop_proxy_password", "").orEmpty()

        if (!enabled) {
            listOf(
                "http.proxyHost", "http.proxyPort", "https.proxyHost", "https.proxyPort",
                "http.proxyUser", "http.proxyPassword", "https.proxyUser", "https.proxyPassword"
            ).forEach(System::clearProperty)
            Authenticator.setDefault(null)
            return
        }

        System.setProperty("http.proxyHost", host)
        System.setProperty("http.proxyPort", port.toString())
        System.setProperty("https.proxyHost", host)
        System.setProperty("https.proxyPort", port.toString())
        System.setProperty("http.nonProxyHosts", "localhost|127.*|[::1]")
        if (user.isNotBlank()) {
            System.setProperty("http.proxyUser", user)
            System.setProperty("https.proxyUser", user)
        }
        if (pass.isNotBlank()) {
            System.setProperty("http.proxyPassword", pass)
            System.setProperty("https.proxyPassword", pass)
        }
        runCatching { Security.setProperty("jdk.http.auth.tunneling.disabledSchemes", "") }
        runCatching { Security.setProperty("jdk.http.auth.proxying.disabledSchemes", "") }
        Authenticator.setDefault(
            if (user.isNotBlank() && pass.isNotBlank()) object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication? =
                    if (requestorType == RequestorType.PROXY) PasswordAuthentication(user, pass.toCharArray()) else null
            } else null
        )
    }

    suspend fun testProxyConnection(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (!proxyEnabled) return@withContext false to "Ative o proxy antes de testar."
        if (!proxyReady) return@withContext false to "Informe usuário e senha do proxy."
        runCatching {
            applyProxySettings()
            val response = Jsoup.connect("https://www.google.com/generate_204")
                .userAgent("Mozilla/5.0 MonitorDeNoticias/4.0.2")
                .timeout(12000)
                .ignoreHttpErrors(true)
                .execute()
            if (response.statusCode() in 200..399) true to "Conexão pelo proxy realizada com sucesso."
            else false to "Proxy respondeu HTTP ${response.statusCode()}."
        }.getOrElse { false to "Falha no proxy: ${it.message ?: it.javaClass.simpleName}" }
    }

    /** Resolve o wrapper do Google Notícias antes de abrir, copiar ou compartilhar. */
    suspend fun resolveVehicleUrl(link: String): String = withContext(Dispatchers.IO) {
        if (!isGoogleNewsLink(link)) return@withContext link

        queryParameter(link, "url")?.let { decoded ->
            if (decoded.startsWith("http") && !isGoogleNewsLink(decoded)) return@withContext decoded
        }

        val decoded = runCatching { GoogleNewsUrlResolver.resolve(link) }.getOrNull()
        if (!decoded.isNullOrBlank() && decoded.startsWith("http") && !isGoogleNewsLink(decoded)) {
            return@withContext decoded
        }

        runCatching {
            val response = Jsoup.connect(link)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/125 Safari/537.36")
                .timeout(12000)
                .followRedirects(true)
                .ignoreHttpErrors(true)
                .execute()
            val redirected = response.url().toString()
            if (!isGoogleNewsLink(redirected)) return@runCatching redirected
            val doc = response.parse()
            val candidates = buildList {
                doc.selectFirst("link[rel=canonical]")?.absUrl("href")?.takeIf { it.isNotBlank() }?.let(::add)
                doc.selectFirst("meta[property=og:url]")?.attr("content")?.takeIf { it.isNotBlank() }?.let(::add)
                doc.select("a[href]").map { it.absUrl("href") }.filter { it.startsWith("http") }.forEach(::add)
            }
            candidates.firstOrNull { !isGoogleNewsLink(it) && !it.contains("google.com", true) } ?: link
        }.getOrDefault(link)
    }

    private fun isGoogleNewsLink(link: String): Boolean = runCatching {
        URI(link).host.orEmpty().contains("news.google.com", true)
    }.getOrDefault(link.contains("news.google.com", true))

    private fun queryParameter(link: String, key: String): String? = runCatching {
        URI(link).rawQuery?.split('&')?.firstOrNull { it.substringBefore('=') == key }
            ?.substringAfter('=', "")?.let { URLDecoder.decode(it, Charsets.UTF_8.name()) }
    }.getOrNull()

    fun refresh() {
        news = newsDb.listRecent(24, 1000)
        videos = videoDb.listRecent(7, 1500)
        terms = newsDb.listTerms()
        demands = newsDb.listDemands()
        videoTerms = VideoTermStore.load(context, terms)
    }

    private fun selectedNewsSources(): List<MediaSource> =
        if (newsAllSources) emptyList() else SourceCatalog.selected(selectedNewsSourceIds)

    private fun selectedVideoSources(): List<VideoSource> = DesktopVideoSources.selected(selectedVideoSourceIds)

    fun searchNews(from: Long? = null, to: Long? = null) {
        if (newsBusy) return
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val started = System.currentTimeMillis()
            val before = newsDb.listNews(5000).map { it.link }.toSet()
            newNewsLinks = emptySet()
            newsBusy = true
            status = if (from == null) "Buscando notícias..." else "Buscando notícias no período..."
            try {
                val result = if (from == null || to == null) {
                    newsRepository.searchProgressive(selectedNewsSources(), newsAllSources) { update ->
                        newsProgress = update.progress
                        if (update.items.isNotEmpty()) news = mergeNewsForUi(news, update.items)
                    }
                } else {
                    newsRepository.searchPeriodProgressive(from, to, selectedNewsSources(), newsAllSources) { update ->
                        newsProgress = update.progress
                        if (update.items.isNotEmpty()) news = mergeNewsForUi(news, update.items)
                    }
                }
                refresh()
                newNewsLinks = newsDb.listNews(5000).asSequence().map { it.link }.filter { it !in before }.toSet()
                status = "✓ ${result.newCount} nova(s) notícia(s) • ${result.newDemandCount} demanda(s) • ${result.errors} falha(s)"
                if (result.newCount + result.newDemandCount > 0) notify("Monitor de Notícias", status)
            } catch (_: CancellationException) {
                status = "⏹ Busca de notícias interrompida pelo usuário."
            } catch (t: Throwable) {
                status = "Falha na busca de notícias: ${t.message ?: t.javaClass.simpleName}"
            } finally {
                lastNewsSearchDurationMs = System.currentTimeMillis() - started
                newsBusy = false
            }
        }
        newsJob = job
        job.start()
    }

    fun searchDemand(demand: Demand) {
        if (newsBusy) return
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val before = newsDb.listNews(5000).map { it.link }.toSet()
            newNewsLinks = emptySet()
            newsBusy = true
            status = "Buscando demanda: ${demand.vehicle} • ${demand.subject}"
            try {
                val result = newsRepository.searchDemand(demand)
                refresh()
                newNewsLinks = newsDb.listNews(5000).asSequence().map { it.link }.filter { it !in before }.toSet()
                status = "✓ Demanda: ${result.foundCount} resultado(s), ${result.newCount} novo(s)"
                if (result.newCount > 0) notify("Nova demanda encontrada", "${demand.vehicle} • ${demand.subject}: ${result.newCount}")
            } catch (_: CancellationException) {
                status = "⏹ Busca de demanda interrompida pelo usuário."
            } catch (t: Throwable) {
                status = "Falha na demanda: ${t.message ?: t.javaClass.simpleName}"
            } finally {
                newsBusy = false
            }
        }
        newsJob = job
        job.start()
    }

    fun searchAllDemands() {
        if (newsBusy) return
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val before = newsDb.listNews(5000).map { it.link }.toSet()
            newNewsLinks = emptySet()
            newsBusy = true
            status = "Buscando todas as demandas..."
            try {
                val result = newsRepository.searchAllDemands()
                refresh()
                newNewsLinks = newsDb.listNews(5000).asSequence().map { it.link }.filter { it !in before }.toSet()
                status = "✓ ${result.checkedCount} demanda(s) • ${result.foundCount} resultado(s) • ${result.newCount} novo(s)"
                if (result.newCount > 0) notify("Demandas", "${result.newCount} novo(s) resultado(s)")
            } catch (_: CancellationException) {
                status = "⏹ Busca de demandas interrompida pelo usuário."
            } catch (t: Throwable) {
                status = "Falha nas demandas: ${t.message ?: t.javaClass.simpleName}"
            } finally {
                newsBusy = false
            }
        }
        newsJob = job
        job.start()
    }

    fun searchVideos(from: Long? = null, to: Long? = null) {
        if (videoBusy) return
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val started = System.currentTimeMillis()
            val before = videoDb.listAll(5000).map { it.link }.toSet()
            newVideoLinks = emptySet()
            videoBusy = true
            videoStatus = if (from == null) "Buscando vídeos..." else "Buscando vídeos no período..."
            try {
                videoDb.removeInvalidListingEntries()
                videoDb.repairStoredMatches()
                val sources = selectedVideoSources()
                val result = if (from == null || to == null) {
                    videoRepository.searchProgressive(sources) { update ->
                        videoProgress = update.progress
                        if (update.items.isNotEmpty()) videos = mergeVideosForUi(videos, update.items)
                    }
                } else {
                    videoRepository.searchPeriodProgressive(sources, from, to) { update ->
                        videoProgress = update.progress
                        if (update.items.isNotEmpty()) videos = mergeVideosForUi(videos, update.items)
                    }
                }
                unstableVideoSources = result.unstableSources
                refresh()
                newVideoLinks = videoDb.listAll(5000).asSequence().map { it.link }.filter { it !in before }.toSet()
                videoStatus = "✓ ${result.relevantCount} relevante(s) • ${result.newRelevantCount} novo(s) • ${result.errors} fonte(s) instável(is)"
                if (result.newRelevantCount > 0) notify("Novos vídeos", "${result.newRelevantCount} vídeo(s) relevante(s)")
            } catch (_: CancellationException) {
                videoStatus = "⏹ Busca de vídeos interrompida pelo usuário."
            } catch (t: Throwable) {
                videoStatus = "Falha na busca de vídeos: ${t.message ?: t.javaClass.simpleName}"
            } finally {
                lastVideoSearchDurationMs = System.currentTimeMillis() - started
                videoBusy = false
            }
        }
        videoJob = job
        job.start()
    }

    fun stopNewsSearch() {
        val job = newsJob
        if (job?.isActive == true) {
            status = "⏹ Interrompendo busca de notícias/demandas..."
            job.cancel(CancellationException("Interrompida pelo usuário"))
        }
    }

    fun stopVideoSearch() {
        val job = videoJob
        if (job?.isActive == true) {
            videoStatus = "⏹ Interrompendo busca de vídeos..."
            job.cancel(CancellationException("Interrompida pelo usuário"))
        }
    }

    fun stopAllSearches() {
        stopNewsSearch()
        stopVideoSearch()
    }

    fun addTerm(value: String) {
        if (value.isNotBlank()) {
            newsDb.addTerm(value.trim())
            refresh()
        }
    }

    fun removeTerm(value: String) {
        newsDb.removeTerm(value)
        refresh()
    }

    fun addVideoTerm(value: String) {
        if (value.isNotBlank()) {
            VideoTermStore.add(context, value.trim(), terms)
            refresh()
        }
    }

    fun removeVideoTerm(value: String) {
        VideoTermStore.remove(context, value, terms)
        refresh()
    }

    fun addDemand(vehicle: String, subject: String) {
        if (vehicle.isNotBlank() && subject.isNotBlank()) {
            newsDb.addDemand(vehicle.trim(), subject.trim())
            refresh()
        }
    }

    fun removeDemand(id: Long) {
        newsDb.removeDemand(id)
        refresh()
    }

    fun clearNewsHistory() {
        newsDb.clearHistory()
        newNewsLinks = emptySet()
        refresh()
    }

    fun clearVideoHistory() {
        videoDb.clear()
        newVideoLinks = emptySet()
        refresh()
    }

    fun setNewsSource(id: String, selected: Boolean) {
        val next = selectedNewsSourceIds.toMutableSet()
        if (selected) next += id else next -= id
        selectedNewsSourceIds = next
        newsAllSources = false
    }

    fun setVideoSource(id: String, selected: Boolean) {
        val next = selectedVideoSourceIds.toMutableSet()
        if (selected) next += id else next -= id
        selectedVideoSourceIds = next
    }

    fun selectAllNewsSources() {
        newsAllSources = false
        selectedNewsSourceIds = SourceCatalog.all.map { it.id }.toSet()
    }

    fun clearNewsSources() {
        newsAllSources = false
        selectedNewsSourceIds = emptySet()
    }

    fun selectAllVideoSources() {
        selectedVideoSourceIds = DesktopVideoSources.all.map { it.id }.toSet()
    }

    fun clearVideoSources() {
        selectedVideoSourceIds = emptySet()
    }

    fun parsePeriod(startDate: String, startTime: String, endDate: String, endTime: String): Pair<Long, Long>? = runCatching {
        val zone = ZoneId.systemDefault()
        val start = LocalDateTime.of(LocalDate.parse(startDate), LocalTime.parse(startTime.ifBlank { "00:00" }))
            .atZone(zone).toInstant().toEpochMilli()
        val end = LocalDateTime.of(LocalDate.parse(endDate), LocalTime.parse(endTime.ifBlank { "23:59" }))
            .atZone(zone).toInstant().toEpochMilli()
        require(end >= start)
        start to end
    }.getOrNull()

    fun periodLastHours(hours: Int): Pair<Long, Long> {
        val end = System.currentTimeMillis()
        return (end - hours * 60L * 60L * 1000L) to end
    }

    private suspend fun automationLoop() {
        while (currentCoroutineContext().isActive) {
            if (automaticMonitoring) {
                val now = System.currentTimeMillis()
                val newsDue = newsAutomatic && now - lastNewsAutoAt >= newsIntervalMinutes * 60_000L
                val demandDue = demandAutomatic && now - lastDemandAutoAt >= demandIntervalMinutes * 60_000L
                if (!newsBusy && newsDue) {
                    prefs.edit().putLong("desktop_auto_news_at", now).apply()
                    searchNews()
                } else if (!newsBusy && demandDue) {
                    prefs.edit().putLong("desktop_auto_demands_at", now).apply()
                    searchAllDemands()
                }

                if (videoAutomatic && !videoBusy) {
                    val dt = LocalDateTime.now()
                    val slot = dt.format(DateTimeFormatter.ofPattern("HH:mm"))
                    if (slot in videoScheduleTimes) {
                        val key = dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm"))
                        if (prefs.getString("desktop_auto_video_slot", "") != key) {
                            prefs.edit().putString("desktop_auto_video_slot", key).putLong("desktop_auto_video_at", now).apply()
                            searchVideos()
                        }
                    }
                }
            }
            delay(30_000L)
        }
    }

    private fun configureWindowsStartup(enabled: Boolean) {
        if (!System.getProperty("os.name", "").contains("Windows", true)) return
        runCatching {
            val command = ProcessHandle.current().info().command().orElse("")
            if (command.isBlank()) return@runCatching
            val args = if (enabled) {
                listOf(
                    "reg", "add", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
                    "/v", "MonitorDeNoticias", "/t", "REG_SZ", "/d", "\"$command\"", "/f"
                )
            } else {
                listOf(
                    "reg", "delete", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
                    "/v", "MonitorDeNoticias", "/f"
                )
            }
            ProcessBuilder(args).redirectErrorStream(true).start().waitFor()
        }
    }

    private fun mergeNewsForUi(old: List<News>, fresh: List<News>): List<News> =
        (fresh + old).distinctBy { it.link }
            .sortedWith(compareByDescending<News> { it.capturedAt }.thenByDescending { it.date })
            .take(1500)

    private fun mergeVideosForUi(old: List<VideoItem>, fresh: List<VideoItem>): List<VideoItem> =
        (fresh + old).distinctBy { it.link }
            .sortedWith(compareByDescending<VideoItem> { it.capturedAt }.thenByDescending { it.publishedAt })
            .take(2000)

    override fun close() {
        scope.cancel()
        newsDb.close()
        videoDb.close()
    }
}
