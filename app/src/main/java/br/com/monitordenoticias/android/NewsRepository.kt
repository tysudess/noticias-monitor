package br.com.monitordenoticias.android

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.text.Normalizer
import java.time.Instant
import java.time.format.DateTimeFormatter

class NewsRepository(private val db: NewsDb) {
    private val latestCollector = NewsLatestCollector()
    val defaultTerms = listOf(
        "Marinha do Brasil","Capitania dos Portos","Distrito Naval","NAM Atlântico",
        "Cisne Branco","Fragata Marinha do Brasil","Navio-Patrulha Marinha","Programa Nuclear da Marinha"
    )

    private data class SearchTask(
        val query: String,
        val term: String,
        val sourceLabel: String
    )

    suspend fun search(selectedSources: List<MediaSource> = emptyList(), searchAllSources: Boolean = true): SearchResult = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        performSearch(cutoff, System.currentTimeMillis(), selectedSources, searchAllSources, null)
    }

    suspend fun searchProgressive(
        selectedSources: List<MediaSource> = emptyList(),
        searchAllSources: Boolean = true,
        onUpdate: (NewsSearchUpdate) -> Unit
    ): SearchResult = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        performSearch(cutoff, System.currentTimeMillis(), selectedSources, searchAllSources, onUpdate)
    }

    suspend fun searchPeriod(from: Long, to: Long, selectedSources: List<MediaSource> = emptyList(), searchAllSources: Boolean = true): SearchResult = withContext(Dispatchers.IO) {
        performSearch(from, to, selectedSources, searchAllSources, null)
    }

    suspend fun searchPeriodProgressive(
        from: Long,
        to: Long,
        selectedSources: List<MediaSource> = emptyList(),
        searchAllSources: Boolean = true,
        onUpdate: (NewsSearchUpdate) -> Unit
    ): SearchResult = withContext(Dispatchers.IO) {
        performSearch(from, to, selectedSources, searchAllSources, onUpdate)
    }

    suspend fun searchBlockingCompatible(selectedSources: List<MediaSource> = emptyList(), searchAllSources: Boolean = true): SearchResult = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        performSearch(cutoff, System.currentTimeMillis(), selectedSources, searchAllSources, null)
    }

    suspend fun searchDemand(demand: Demand): DemandSearchResult = withContext(Dispatchers.IO) {
        performDemandSearch(demand)
    }

    suspend fun searchAllDemands(): DemandSweepResult = withContext(Dispatchers.IO) {
        val active = db.listDemands().filter { it.active }
        val allItems = mutableListOf<News>()
        var found = 0
        var fresh = 0
        var errors = 0
        active.forEach { demand ->
            val result = performDemandSearch(demand)
            found += result.foundCount
            fresh += result.newCount
            if (result.error != null) errors++
            allItems += result.items
        }
        DemandSweepResult(active.size, found, fresh, errors, allItems.distinctBy { it.link }.sortedByDescending { it.date })
    }

    private fun performDemandSearch(demand: Demand): DemandSearchResult {
        val checkedAt = System.currentTimeMillis()
        val query = demand.subject.trim()
        val fetched = fetchGoogleNews(query)
        if (fetched == null) {
            db.updateDemandStatus(demand.id, checkedAt, 0, 0, "Falha na consulta")
            return DemandSearchResult(demand, emptyList(), 0, 0, "Falha na consulta")
        }

        val cutoff = checkedAt - 24L * 60L * 60L * 1000L
        val matched = fetched
            .filter { it.date >= cutoff }
            .filter { news -> demandVehicleMatches(news.source, demand.vehicle) }
            .filter { news -> subjectMatches("${news.title} ${news.snippet}", demand.subject) }
            .distinctBy { it.link }
            .map { news ->
                news.copy(
                    important = true,
                    demand = true,
                    matchedTerm = demand.subject,
                    matchedDemand = "${demand.vehicle} • ${demand.subject}",
                    capturedAt = checkedAt
                )
            }
            .sortedByDescending { it.date }

        val history = db.listNews(limit = 2000)
        val byLink = history.associateBy { it.link }
        val byStory = history.associateBy { storyKey(it) }
        val stableMatched = matched.map { incoming ->
            val previous = byLink[incoming.link] ?: byStory[storyKey(incoming)]
            if (previous == null) incoming else mergeNews(
                previous,
                incoming.copy(link = previous.link, capturedAt = previous.capturedAt)
            )
        }.distinctBy { it.link }

        val inserted = db.insertNews(stableMatched)
        db.updateDemandStatus(demand.id, checkedAt, stableMatched.size, inserted.size, "")
        return DemandSearchResult(demand, stableMatched, stableMatched.size, inserted.size)
    }

    private fun performSearch(
        from: Long,
        to: Long,
        selectedSources: List<MediaSource>,
        searchAllSources: Boolean,
        onUpdate: ((NewsSearchUpdate) -> Unit)?
    ): SearchResult {
        val terms = db.listTerms().ifEmpty { defaultTerms }
        val demands = db.listDemands().filter { it.active }
        val startedAt = System.currentTimeMillis()
        val directSources = if (from >= startedAt - DIRECT_SCAN_MAX_WINDOW_MS && to >= startedAt - DIRECT_SCAN_RECENCY_TOLERANCE_MS) {
            latestCollector.supportedSources(selectedSources, searchAllSources)
        } else {
            emptyList()
        }
        val tasks = buildList {
            terms.forEach { term -> add(SearchTask(term, term, "Google Notícias")) }
            if (!searchAllSources && selectedSources.isNotEmpty() && selectedSources.size <= 24) {
                selectedSources.chunked(8).forEach { batch ->
                    val sourceClause = batch.joinToString(" OR ") { "\"${it.name}\"" }
                    val label = batch.joinToString(", ") { it.name }.take(70)
                    terms.forEach { term ->
                        add(SearchTask("\"$term\" ($sourceClause)", term, label))
                    }
                }
            }
        }

        // Snapshot do histórico ANTES desta busca. NOVO significa que a matéria
        // não existia antes da varredura atual. A identidade editorial por veículo +
        // título também protege contra URLs diferentes do Google News para a mesma matéria.
        val historyBeforeRun = db.listNews(limit = 2000)
        val historyByLink = historyBeforeRun.associateBy { it.link }
        val historyByStory = historyBeforeRun.associateBy { storyKey(it) }

        fun reuseHistoricalIdentity(incoming: News): News {
            val previous = historyByLink[incoming.link] ?: historyByStory[storyKey(incoming)]
            return if (previous == null) incoming else mergeNews(
                previous,
                incoming.copy(link = previous.link, capturedAt = previous.capturedAt)
            )
        }

        val collected = linkedMapOf<String, News>()
        val newLinks = linkedSetOf<String>()
        var errors = 0
        var completed = 0

        fun progress(source: String, query: String, active: Boolean = true): LiveSearchProgress = LiveSearchProgress(
            active = active,
            kind = "Notícias",
            startedAt = startedAt,
            finishedAt = if (active) 0L else System.currentTimeMillis(),
            completed = completed,
            total = tasks.size + directSources.size,
            currentSource = source,
            currentQuery = query,
            found = collected.size,
            newCount = newLinks.size,
            errors = errors
        )

        onUpdate?.invoke(NewsSearchUpdate(progress("Preparando", "")))

        tasks.forEach { task ->
            onUpdate?.invoke(NewsSearchUpdate(progress(task.sourceLabel, task.term)))
            val fetched = fetchGoogleNews(task.query)
            if (fetched == null) {
                errors++
            } else {
                val batch = fetched
                    .asSequence()
                    .filter { it.date in from..to }
                    .filter { news -> searchAllSources || selectedSources.any { selected -> sourceMatchesStrict(news.source, selected) } }
                    .map { base ->
                        val body = "${base.title} ${base.snippet}"
                        val actualTerms = terms.filter { subjectMatches(body, it) }
                        val matchedTerms = if (actualTerms.isNotEmpty()) actualTerms else listOf(task.term)
                        val demandMatch = demands.firstOrNull { d ->
                            demandVehicleMatches(base.source, d.vehicle) && subjectMatches(body, d.subject)
                        }
                        base.copy(
                            important = demandMatch != null,
                            demand = demandMatch != null,
                            matchedTerm = matchedTerms.distinct().joinToString(", "),
                            matchedDemand = demandMatch?.let { "${it.vehicle} • ${it.subject}" }.orEmpty(),
                            capturedAt = System.currentTimeMillis()
                        )
                    }
                    .distinctBy { it.link }
                    .map(::reuseHistoricalIdentity)
                    .distinctBy { it.link }
                    .toList()

                val mergedBatch = batch.map { incoming ->
                    val previous = collected[incoming.link]
                    if (previous == null) incoming else mergeNews(previous, incoming)
                }
                mergedBatch.forEach { collected[it.link] = it }

                val inserted = db.insertNews(mergedBatch)
                inserted.forEach { newLinks += it.link }
                if (mergedBatch.isNotEmpty()) {
                    onUpdate?.invoke(NewsSearchUpdate(progress(task.sourceLabel, task.term), mergedBatch))
                }
            }
            completed++
            onUpdate?.invoke(NewsSearchUpdate(progress(task.sourceLabel, task.term)))
        }

        directSources.forEach { source ->
            val label = "${source.name} • Últimas notícias"
            onUpdate?.invoke(NewsSearchUpdate(progress(label, "Todos os termos")))
            val outcome = latestCollector.collect(source, terms, demands, from, to, System.currentTimeMillis())
            if (!outcome.failed && outcome.items.isNotEmpty()) {
                val inserts = mutableListOf<News>()
                val updates = mutableListOf<News>()
                outcome.items.forEach { rawIncoming ->
                    val incoming = reuseHistoricalIdentity(rawIncoming)
                    val duplicate = collected.values.firstOrNull { storyKey(it) == storyKey(incoming) }
                    if (duplicate == null) {
                        collected[incoming.link] = incoming
                        inserts += incoming
                        updates += incoming
                    } else {
                        val merged = mergeNews(duplicate, incoming.copy(link = duplicate.link, source = duplicate.source))
                        collected[duplicate.link] = merged
                        updates += merged
                    }
                }
                val inserted = db.insertNews(inserts)
                inserted.forEach { newLinks += it.link }
                if (updates.isNotEmpty()) {
                    onUpdate?.invoke(NewsSearchUpdate(progress(label, "Todos os termos"), updates))
                }
            }
            completed++
            onUpdate?.invoke(NewsSearchUpdate(progress(label, "Todos os termos")))
        }

        val items = collected.values.sortedByDescending { it.date }
        val newDemandCount = items.count { it.link in newLinks && it.demand }
        onUpdate?.invoke(
            NewsSearchUpdate(
                progress("Concluído", "", active = false).copy(
                    completed = tasks.size + directSources.size,
                    found = items.size,
                    newCount = newLinks.size
                )
            )
        )
        return SearchResult(items, items.size, newLinks.size, newDemandCount, errors)
    }

    private fun storyKey(news: News): String {
        val sourceKey = normalize(news.source).replace(" noticias", "").replace(" jornal", "").trim()
        val titleKey = normalize(news.title)
        return "$sourceKey|$titleKey"
    }

    private fun mergeNews(previous: News, incoming: News): News {
        val terms = (previous.matchedTerm.split(',') + incoming.matchedTerm.split(','))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        return incoming.copy(
            important = previous.important || incoming.important,
            demand = previous.demand || incoming.demand,
            matchedTerm = terms.joinToString(", "),
            matchedDemand = incoming.matchedDemand.ifBlank { previous.matchedDemand },
            // Primeira captura é imutável: reencontrar o conteúdo nunca o torna NOVO.
            capturedAt = previous.capturedAt.takeIf { it > 0L } ?: incoming.capturedAt
        )
    }

    private fun demandVehicleMatches(actualSource: String, vehicle: String): Boolean {
        if (vehicle.isBlank()) return true
        val actual = normalize(actualSource)
        val actualKey = compact(actualSource)
        val hostKey = publisherHostKey(actualSource)
        val wanted = normalize(vehicle)
        val wantedKey = compact(vehicle)
        if (actual == wanted || actualKey == wantedKey || hostKey == wantedKey) return true
        val tokens = wanted.split(' ').filter { it.length >= 2 && it !in STOP_WORDS }
        return tokens.size >= 2 && wantedKey.length >= 5 && (actualKey.startsWith(wantedKey) || hostKey.startsWith(wantedKey))
    }

    private fun subjectMatches(text: String, subject: String): Boolean {
        val haystack = normalize(text)
        val wanted = normalize(subject)
        if (wanted.isBlank()) return true
        if (" $haystack ".contains(" $wanted ")) return true
        val hayTokens = haystack.split(' ').filter { it.isNotBlank() }.toSet()
        val wantedTokens = wanted.split(' ').filter { it.isNotBlank() }
        if (wantedTokens.size == 1) return wantedTokens.first() in hayTokens
        val tokens = wantedTokens.filter { it.length >= 3 && it !in STOP_WORDS }
        return tokens.isNotEmpty() && tokens.all { it in hayTokens }
    }

    private fun sourceMatchesStrict(actualSource: String, selected: MediaSource): Boolean {
        val actualNormalized = normalize(actualSource)
        val actualKey = compact(actualSource)
        val hostKey = publisherHostKey(actualSource)
        return (listOf(selected.name) + selected.aliases).any { candidate ->
            val candidateNormalized = normalize(candidate)
            val candidateKey = compact(candidate)
            if (candidateNormalized.isBlank() || candidateKey.isBlank()) false
            else if (actualNormalized == candidateNormalized || actualKey == candidateKey || hostKey == candidateKey) true
            else {
                val meaningfulTokens = candidateNormalized.split(' ').filter { it.length >= 2 && it !in STOP_WORDS }
                meaningfulTokens.size >= 2 && candidateKey.length >= 6 && (actualKey.startsWith(candidateKey) || hostKey.startsWith(candidateKey))
            }
        }
    }

    private fun publisherHostKey(value: String): String {
        val cleaned = value.trim().lowercase()
        return compact(if ('.' in cleaned) cleaned.substringBefore('.') else cleaned)
    }

    private fun compact(value: String): String = normalize(value).replace(" ", "")

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    private fun fetchGoogleNews(query: String): List<News>? = runCatching {
        val q = URLEncoder.encode(query, "UTF-8")
        val url = URL("https://news.google.com/rss/search?q=$q&hl=pt-BR&gl=BR&ceid=BR:pt-419")
        val con = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000; readTimeout = 10000; requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0 MonitorNoticiasAndroid/3.0")
        }
        try {
            if (con.responseCode !in 200..299) error("HTTP ${con.responseCode}")
            con.inputStream.use { input ->
                val parser = Xml.newPullParser().apply { setInput(input, "UTF-8") }
                val result = mutableListOf<News>()
                var event = parser.eventType
                var title = ""; var link = ""; var source = "Google Notícias"; var date = 0L; var desc = ""; var inItem = false
                while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    when (event) {
                        org.xmlpull.v1.XmlPullParser.START_TAG -> when (parser.name) {
                            "item" -> { inItem = true; title = ""; link = ""; source = "Google Notícias"; date = 0; desc = "" }
                            "title" -> if (inItem) title = parser.nextText()
                            "link" -> if (inItem) link = parser.nextText()
                            "pubDate" -> if (inItem) date = parseDate(parser.nextText())
                            "description" -> if (inItem) desc = parser.nextText().replace(Regex("<[^>]*>"), "").trim()
                            "source" -> if (inItem) source = parser.nextText()
                        }
                        org.xmlpull.v1.XmlPullParser.END_TAG -> if (parser.name == "item" && inItem) {
                            if (title.isNotBlank() && link.isNotBlank()) result += News(title=title.trim(), source=source.trim(), date=date, link=link.trim(), snippet=desc.take(500))
                            inItem = false
                        }
                    }
                    event = parser.next()
                }
                result
            }
        } finally { con.disconnect() }
    }.getOrNull()

    private fun parseDate(value: String): Long = runCatching { DateTimeFormatter.RFC_1123_DATE_TIME.parse(value, Instant::from).toEpochMilli() }
        .getOrDefault(System.currentTimeMillis())

    companion object {
        private const val DIRECT_SCAN_MAX_WINDOW_MS = 48L * 60L * 60L * 1000L
        private const val DIRECT_SCAN_RECENCY_TOLERANCE_MS = 2L * 60L * 60L * 1000L
        private val STOP_WORDS = setOf("de","do","da","dos","das","e","em","no","na","nos","nas","a","o","as","os")
    }
}
