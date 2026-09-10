package br.com.monitordenoticias.android

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import java.net.URI
import java.net.URLEncoder
import java.text.Normalizer
import java.time.Instant

class VideoRepository(
    private val context: Context,
    private val db: VideoDb
) {
    private val globoplayTrechosCollector = GloboplayTrechosCollector()
    private val globoplayEditionCollector = GloboplayEditionCollector()
    private val globoplayJarvisCollector = GloboplayJarvisCollector()

    private data class QuerySpec(
        val query: String,
        val term: String = "",
        val demand: Demand? = null
    )

    suspend fun search(sources: List<VideoSource>): VideoSearchResult =
        searchInternal(sources, null, null, null)

    suspend fun searchProgressive(
        sources: List<VideoSource>,
        onUpdate: (VideoSearchUpdate) -> Unit
    ): VideoSearchResult = searchInternal(sources, null, null, onUpdate)

    suspend fun searchPeriod(sources: List<VideoSource>, from: Long, to: Long): VideoSearchResult =
        searchInternal(sources, from, to, null)

    suspend fun searchPeriodProgressive(
        sources: List<VideoSource>,
        from: Long,
        to: Long,
        onUpdate: (VideoSearchUpdate) -> Unit
    ): VideoSearchResult = searchInternal(sources, from, to, onUpdate)

    private suspend fun searchInternal(
        sources: List<VideoSource>,
        from: Long?,
        to: Long?,
        onUpdate: ((VideoSearchUpdate) -> Unit)?
    ): VideoSearchResult = withContext(Dispatchers.IO) {
        val newsDb = NewsDb(context)
        try {
            val newsTermsForMigration = newsDb.listTerms().ifEmpty { DEFAULT_TERMS }
            val terms = VideoTermStore.load(context, newsTermsForMigration)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinctBy(::normalize)
            val demands = newsDb.listDemands().filter { it.active }
            val capturedAt = System.currentTimeMillis()
            val startedAt = capturedAt
            // A busca normal e a automática trabalham sempre com uma janela móvel de 24h.
            // Pesquisas de período continuam usando from/to explícitos.
            val effectiveTo = to ?: capturedAt
            val effectiveFrom = from ?: (effectiveTo - DEFAULT_VIDEO_WINDOW_MS)
            val collected = linkedMapOf<String, VideoItem>()
            val newKeys = linkedSetOf<String>()
            val unstableSourceIds = linkedSetOf<String>()
            val unstableDetails = linkedMapOf<String, VideoSourceIssue>()
            var errors = 0
            var completed = 0

            fun markUnstable(source: VideoSource, failureCount: Int, stage: String) {
                unstableSourceIds += source.id
                unstableDetails[source.id] = VideoSourceIssue(
                    sourceId = source.id,
                    sourceName = source.name,
                    failureCount = failureCount.coerceAtLeast(1),
                    stage = stage.ifBlank { "HTTP/rede" }
                )
                errors = unstableSourceIds.size
            }

            // Globoplay, YouTube e páginas estáveis de programas fazem UMA coleta por
            // fonte/canal. Termos e Demandas são cruzados localmente depois. Portais sem
            // uma listagem recente confiável preservam a busca tradicional por termo.
            val plan = sources.associateWith { source ->
                if (isSourceScanMode(source)) {
                    listOf(QuerySpec(query = ""))
                } else {
                    buildList {
                        terms.forEach { term -> add(QuerySpec(query = term, term = term)) }
                        demands
                            .filter { demand -> sourceMatchesDemand(source, demand.vehicle) }
                            .forEach { demand -> add(QuerySpec(query = demand.subject, demand = demand)) }
                    }.distinctBy { spec -> "${normalize(spec.query)}|${spec.term}|${spec.demand?.id ?: 0L}" }
                }
            }
            val total = plan.values.sumOf { it.size }

            fun progress(source: String, query: String, active: Boolean = true): LiveSearchProgress = LiveSearchProgress(
                active = active,
                kind = if (from != null || to != null) "Vídeos • Período" else "Vídeos",
                startedAt = startedAt,
                finishedAt = if (active) 0L else System.currentTimeMillis(),
                completed = completed,
                total = total,
                currentSource = source,
                currentQuery = query,
                found = collected.size,
                newCount = newKeys.size,
                errors = errors
            )

            onUpdate?.invoke(VideoSearchUpdate(progress("Preparando", "")))

            // JH/JN e alguns nacionais recebem os Trechos recentes apenas depois que
            // o JavaScript do Globoplay roda. Fazemos uma busca Jarvis GLOBAL por
            // Termo/Demanda (nunca fonte x termo) e depois distribuímos localmente
            // os vídeos pelo nome/originProgramId do telejornal.
            val nationalGloboplaySources = sources.filter { it.id in CORE_NATIONAL_GLOBOPLAY_IDS }
            val jarvisQueries = buildList {
                addAll(terms)
                demands
                    .filter { demand -> nationalGloboplaySources.any { source -> sourceMatchesDemand(source, demand.vehicle) } }
                    .forEach { demand -> add(demand.subject) }
            }.map { it.trim() }
                .filter { it.isNotBlank() }
                .distinctBy(::normalize)

            val jarvisOutcome = if (nationalGloboplaySources.isNotEmpty() && jarvisQueries.isNotEmpty()) {
                onUpdate?.invoke(
                    VideoSearchUpdate(progress("Globoplay nacionais", "Jarvis • busca global de Termos/Demandas"))
                )
                runCatching {
                    globoplayJarvisCollector.collect(jarvisQueries, nationalGloboplaySources, capturedAt)
                }.getOrElse {
                    GloboplayJarvisCollector.Outcome(emptyMap(), failedQueries = jarvisQueries.size)
                }
            } else {
                GloboplayJarvisCollector.Outcome(emptyMap(), failedQueries = 0)
            }

            plan.forEach { (source, specs) ->
                val resolvedCache = mutableMapOf<String, VideoItem?>()
                var sourceRequestFailures = 0
                val sourceFailureStages = linkedMapOf<String, Int>()

                fun sourceError(stage: String = "HTTP/rede") {
                    sourceRequestFailures++
                    sourceFailureStages[stage] = (sourceFailureStages[stage] ?: 0) + 1
                }

                fun markCurrentSourceUnstable() {
                    val primaryStage = sourceFailureStages.maxByOrNull { it.value }?.key ?: "HTTP/rede"
                    markUnstable(source, sourceRequestFailures, primaryStage)
                }

                val sourceScanCandidates = if (isSourceScanMode(source)) {
                    val nativeCandidates = collectRecentBySource(
                        source,
                        capturedAt,
                        effectiveFrom,
                        effectiveTo
                    ) { stage -> sourceError(stage) }
                    if (source.id in CORE_NATIONAL_GLOBOPLAY_IDS) {
                        (jarvisOutcome.candidatesBySourceId[source.id].orEmpty() + nativeCandidates)
                            .distinctBy { canonicalKey(it.link) }
                    } else {
                        nativeCandidates
                    }
                } else {
                    emptyList()
                }
                // Uma tentativa auxiliar pode falhar e outro caminho da mesma fonte funcionar.
                // Só classificamos uma fonte de varredura como instável quando ela realmente
                // não conseguiu entregar candidatos e houve falha de rede/HTTP.
                if (isSourceScanMode(source) && sourceScanCandidates.isEmpty() && sourceRequestFailures > 0) {
                    val actionableScanFailure = when {
                        source.youtubeHandle.isNotBlank() -> sourceRequestFailures >= 2
                        isGloboplaySource(source) -> sourceRequestFailures >= 2
                        source.id in VideoSourceCatalog.portalProgramScanIds && source.searchPrefix.isNotBlank() ->
                            sourceFailureStages.containsKey("Portal • página do programa") &&
                                sourceFailureStages.containsKey("Portal • busca fallback")
                        else -> sourceFailureStages.containsKey("Portal • página do programa")
                    }
                    if (actionableScanFailure) markCurrentSourceUnstable()
                }

                fun resolve(item: VideoItem): VideoItem? {
                    if (isYoutubeUrl(item.link)) {
                        return if (isYoutubeVideoUrl(item.link)) item.copy(link = canonicalizeUrl(item.link)) else null
                    }
                    val cacheKey = canonicalKey(item.link)
                    if (resolvedCache.containsKey(cacheKey)) return resolvedCache[cacheKey]

                    val globoplay = isGloboplaySource(source)
                    val resolved = runCatching { resolveDirectVideoPage(source, item, capturedAt) }
                        .onFailure {
                            // Em Trechos, abrir a página individual é enriquecimento opcional.
                            // O card /cenas/ já fornece título e link direto válidos.
                            if (!globoplay) sourceError("Abrir página do vídeo")
                        }
                        .getOrNull()
                    val fallback = item.takeIf {
                        globoplay && usefulTitle(it.title) && isSpecificVideoUrl(source, it.link)
                    }?.copy(link = canonicalizeUrl(item.link))
                    val finalItem = resolved ?: fallback
                    resolvedCache[cacheKey] = finalItem
                    return finalItem
                }

                specs.forEach specLoop@ { spec ->
                    val scanMode = isSourceScanMode(source)
                    if (!scanMode && sourceRequestFailures >= MAX_REQUEST_FAILURES_PER_SOURCE) {
                        markCurrentSourceUnstable()
                        completed++
                        onUpdate?.invoke(
                            VideoSearchUpdate(
                                progress(source.name, "Fonte instável • consultas restantes ignoradas")
                            )
                        )
                        return@specLoop
                    }
                    val progressQuery = when {
                        !scanMode -> spec.query
                        source.id in CORE_NATIONAL_GLOBOPLAY_IDS -> "Edições + Trechos + Jarvis • cruzamento local"
                        else -> "Vídeos recentes • cruzamento local"
                    }
                    onUpdate?.invoke(VideoSearchUpdate(progress(source.name, progressQuery)))

                    val rawCandidates = if (scanMode) {
                        sourceScanCandidates
                    } else {
                        collectCandidatesForQuery(source, spec.query, capturedAt) { sourceError("Busca por termo") }
                    }

                    val prioritized = if (isGloboplaySource(source)) {
                        prioritizeGloboplayCandidates(rawCandidates, source, terms, demands)
                    } else {
                        rawCandidates
                    }

                    var globoplayDeepFallbacks = 0
                    prioritized.asSequence()
                        .take(resolveLimitFor(source))
                        .forEach { raw ->
                            // Trechos vindos de uma Edição já possuem título, link direto e data
                            // suficiente para o cruzamento local. Se o Termo/Demanda aparece no card,
                            // não abrimos /v/<id> de novo. A leitura profunda fica reservada aos cards
                            // sem match superficial, onde o assunto pode estar só em descrição/tags.
                            val globoplay = scanMode && isGloboplaySource(source)
                            val shallowBody = "${raw.title} ${raw.summary}"
                            val shallowTermMatch = globoplay && terms.any { phraseMatches(shallowBody, it) }
                            val shallowDemandMatch = globoplay && demands.any { demand ->
                                sourceMatchesDemand(source, demand.vehicle) &&
                                    phraseMatches(shallowBody, demand.subject)
                            }

                            if (globoplay && !shallowTermMatch && !shallowDemandMatch) {
                                if (globoplayDeepFallbacks >= deepFallbackLimitFor(source)) return@forEach
                                globoplayDeepFallbacks++
                            }

                            val item = if (
                                globoplay &&
                                (shallowTermMatch || shallowDemandMatch) &&
                                raw.publishedAt > 0L &&
                                usefulTitle(raw.title) &&
                                isSpecificVideoUrl(source, raw.link)
                            ) {
                                raw.copy(link = canonicalizeUrl(raw.link))
                            } else {
                                resolve(raw) ?: return@forEach
                            }
                            val body = "${item.title} ${item.summary}"
                            if (!inPeriod(item, effectiveFrom, effectiveTo)) return@forEach
                            if (!isDirectResult(item)) return@forEach

                            val actualMatchedTerms = terms.filter { phraseMatches(body, it) }
                            val matchedDemands = demands.filter { demand ->
                                sourceMatchesDemand(source, demand.vehicle) && phraseMatches(body, demand.subject)
                            }

                            // No modo tradicional, ainda respeitamos a consulta que trouxe
                            // o candidato; no modo de varredura a relevância é inteiramente local.
                            if (!scanMode && !phraseMatches(body, spec.query)) return@forEach

                            val key = canonicalKey(item.link)
                            val previous = collected[key]
                            val matchedTerm = when {
                                actualMatchedTerms.isNotEmpty() -> actualMatchedTerms.joinToString(", ")
                                spec.term.isNotBlank() && phraseMatches(body, spec.term) -> spec.term
                                else -> previous?.matchedTerm.orEmpty()
                            }
                            val matchedDemand = matchedDemands.firstOrNull()?.let {
                                "${it.vehicle} • ${it.subject}"
                            } ?: when {
                                spec.demand != null && phraseMatches(body, spec.demand.subject) ->
                                    "${spec.demand.vehicle} • ${spec.demand.subject}"
                                else -> previous?.matchedDemand.orEmpty()
                            }

                            val candidate = item.copy(
                                link = canonicalizeUrl(item.link),
                                matchedTerm = matchedTerm,
                                matchedDemand = matchedDemand,
                                capturedAt = capturedAt
                            )
                            if (!candidate.relevant) return@forEach

                            val merged = if (previous == null) candidate else mergeVideo(previous, candidate)
                            collected[key] = merged
                            val inserted = db.insert(listOf(merged))
                            if (inserted.isNotEmpty()) newKeys += key

                            // Resultado entra na UI imediatamente; cronômetro e progresso
                            // continuam avançando enquanto as demais fontes são coletadas.
                            onUpdate?.invoke(
                                VideoSearchUpdate(
                                    progress(source.name, progressQuery),
                                    listOf(merged)
                                )
                            )
                        }

                    completed++
                    onUpdate?.invoke(VideoSearchUpdate(progress(source.name, progressQuery)))
                }
            }

            val items = collected.values
                .filter { it.relevant && isDirectResult(it) }
                .filter { item -> inPeriod(item, effectiveFrom, effectiveTo) }
                .distinctBy { canonicalKey(it.link) }
                .sortedByDescending { it.publishedAt }

            onUpdate?.invoke(
                VideoSearchUpdate(
                    progress("Concluído", "", active = false).copy(
                        completed = total,
                        found = items.size,
                        newCount = newKeys.size
                    )
                )
            )

            VideoSearchResult(
                items = items,
                foundCount = items.size,
                newCount = newKeys.size,
                relevantCount = items.size,
                newRelevantCount = newKeys.size,
                errors = errors,
                unstableSources = unstableDetails.values.sortedBy { it.sourceName.lowercase() }
            )
        } finally {
            newsDb.close()
        }
    }

    private fun collectRecentBySource(
        source: VideoSource,
        capturedAt: Long,
        from: Long,
        to: Long,
        onError: (String) -> Unit
    ): List<VideoItem> {
        if (source.youtubeHandle.isNotBlank()) {
            val feed = runCatching { fetchYoutube(source, capturedAt) }
                .onFailure { onError("YouTube • feed/canal") }
                .getOrDefault(emptyList())
            if (feed.isNotEmpty()) return feed.distinctBy { canonicalKey(it.link) }

            // Fallback somente se o feed oficial não puder ser obtido.
            return runCatching { fetchWebsite(source, capturedAt) }
                .onFailure { onError("YouTube • página") }
                .getOrDefault(emptyList())
                .distinctBy { canonicalKey(it.link) }
        }

        if (isGloboplaySource(source)) {
            // Regionais normalmente expõem Trechos dentro da própria Edição. JH/JN e
            // outros nacionais podem expor apenas "Mais Vídeos" na Edição enquanto os
            // Trechos reais ficam em /cenas/. Nos cinco nacionais, portanto, combinamos
            // os dois grafos em vez de encerrar no primeiro que devolver qualquer link.
            val editionTrechos = globoplayEditionCollector.collect(source, capturedAt, from, to) {
                onError("Globoplay • Edições")
            }
            if (source.id in CORE_NATIONAL_GLOBOPLAY_IDS) {
                val sceneTrechos = globoplayTrechosCollector.collect(source, capturedAt) {
                    onError("Globoplay • Trechos")
                }
                val combined = (editionTrechos + sceneTrechos)
                    .distinctBy { canonicalKey(it.link) }
                if (combined.isNotEmpty()) return combined
            } else {
                if (editionTrechos.isNotEmpty()) {
                    return editionTrechos.distinctBy { canonicalKey(it.link) }
                }
                val trechos = globoplayTrechosCollector.collect(source, capturedAt) {
                    onError("Globoplay • Trechos")
                }
                if (trechos.isNotEmpty()) {
                    return trechos.distinctBy { canonicalKey(it.link) }
                }
            }

            val primary = when {
                source.searchPrefix.isNotBlank() && source.searchUrlTemplate.isNotBlank() ->
                    runCatching { fetchSearchWebsite(source, "", capturedAt) }
                        .onFailure { onError("Globoplay • busca fallback") }
                        .getOrDefault(emptyList())

                !isDirectGloboplayVideoUrl(source.landingUrl) ->
                    runCatching { fetchWebsite(source, capturedAt) }
                        .onFailure { onError("Globoplay • página fallback") }
                        .getOrDefault(emptyList())

                source.searchUrlTemplate.isNotBlank() ->
                    runCatching { fetchSearchWebsite(source, "", capturedAt) }
                        .onFailure { onError("Globoplay • busca fallback") }
                        .getOrDefault(emptyList())

                else -> emptyList()
            }

            if (primary.isNotEmpty()) return primary.distinctBy { canonicalKey(it.link) }

            // Landings antigas que apontam diretamente para um /v/<id> não são usadas
            // como varredura: evitamos revisitar um vídeo histórico a cada execução.
            if (!isDirectGloboplayVideoUrl(source.landingUrl)) {
                return runCatching { fetchWebsite(source, capturedAt) }
                    .onFailure { onError("Globoplay • página fallback") }
                    .getOrDefault(emptyList())
                    .distinctBy { canonicalKey(it.link) }
            }
            return emptyList()
        }

        if (source.id in VideoSourceCatalog.portalProgramScanIds) {
            val landing = runCatching { fetchWebsite(source, capturedAt) }
                .onFailure { onError("Portal • página do programa") }
                .getOrDefault(emptyList())
                .distinctBy { canonicalKey(it.link) }
            if (landing.isNotEmpty()) return landing

            if (source.searchUrlTemplate.isNotBlank() && source.searchPrefix.isNotBlank()) {
                return runCatching { fetchSearchWebsite(source, "", capturedAt) }
                    .onFailure { onError("Portal • busca fallback") }
                    .getOrDefault(emptyList())
                    .distinctBy { canonicalKey(it.link) }
            }
            return emptyList()
        }

        return runCatching { fetchWebsite(source, capturedAt) }
            .onFailure { onError("Portal • página") }
            .getOrDefault(emptyList())
            .distinctBy { canonicalKey(it.link) }
    }

    private fun collectCandidatesForQuery(
        source: VideoSource,
        query: String,
        capturedAt: Long,
        onError: () -> Unit
    ): List<VideoItem> {
        val searched = if (source.searchUrlTemplate.isNotBlank()) {
            runCatching { fetchSearchWebsite(source, query, capturedAt) }
                .onFailure { onError() }
                .getOrDefault(emptyList())
        } else {
            emptyList()
        }

        val filtered = searched.filter { item -> phraseMatches("${item.title} ${item.summary}", query) }
        if (filtered.isNotEmpty()) return filtered.distinctBy { canonicalKey(it.link) }

        val fallback = runCatching { fetchWebsite(source, capturedAt) }
            .onFailure { onError() }
            .getOrDefault(emptyList())
        return fallback
            .filter { item -> phraseMatches("${item.title} ${item.summary}", query) }
            .distinctBy { canonicalKey(it.link) }
    }

    private fun prioritizeGloboplayCandidates(
        candidates: List<VideoItem>,
        source: VideoSource,
        terms: List<String>,
        demands: List<Demand>
    ): List<VideoItem> {
        if (candidates.size <= 1) return candidates
        return candidates.withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<VideoItem>> { indexed ->
                    val body = "${indexed.value.title} ${indexed.value.summary}"
                    terms.any { phraseMatches(body, it) } ||
                        demands.any { d -> sourceMatchesDemand(source, d.vehicle) && phraseMatches(body, d.subject) }
                }.thenBy { it.index }
            )
            .map { it.value }
    }

    private fun resolveLimitFor(source: VideoSource): Int = when {
        source.youtubeHandle.isNotBlank() -> MAX_YOUTUBE_ITEMS_PER_SCAN
        source.id in CORE_NATIONAL_GLOBOPLAY_IDS -> MAX_GLOBOPLAY_NATIONAL_ITEMS_PER_SCAN
        source.id == "video-globoplay-jornalismo" -> MAX_GLOBOPLAY_GENERAL_ITEMS_PER_SCAN
        source.id in VideoSourceCatalog.globoplayRegionalSweepIds -> MAX_GLOBOPLAY_GENERAL_ITEMS_PER_SCAN
        isGloboplaySource(source) -> MAX_GLOBOPLAY_ITEMS_PER_SCAN
        source.id in VideoSourceCatalog.portalProgramScanIds -> MAX_PORTAL_PROGRAM_ITEMS_PER_SCAN
        else -> MAX_RESOLVED_PER_QUERY
    }

    private fun deepFallbackLimitFor(source: VideoSource): Int = when {
        // Nos cinco telejornais nacionais, uma menção importante pode existir apenas
        // na descrição/tags da página individual. O orçamento cobre todos os trechos
        // usuais de uma edição recente sem transformar todas as regionais em varredura profunda.
        source.id in CORE_NATIONAL_GLOBOPLAY_IDS -> MAX_GLOBOPLAY_DEEP_FALLBACK_NATIONAL
        source.id == "video-globoplay-jornalismo" -> MAX_GLOBOPLAY_DEEP_FALLBACK_GENERAL
        source.id in VideoSourceCatalog.globoplayRegionalSweepIds -> MAX_GLOBOPLAY_DEEP_FALLBACK_GENERAL
        isGloboplaySource(source) -> MAX_GLOBOPLAY_DEEP_FALLBACK_PER_SOURCE
        else -> 0
    }

    private fun mergeVideo(previous: VideoItem, incoming: VideoItem): VideoItem {
        val terms = (previous.matchedTerm.split(',') + incoming.matchedTerm.split(','))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        return incoming.copy(
            matchedTerm = terms.joinToString(", "),
            matchedDemand = incoming.matchedDemand.ifBlank { previous.matchedDemand },
            capturedAt = maxOf(previous.capturedAt, incoming.capturedAt)
        )
    }

    private fun fetchSearchWebsite(source: VideoSource, query: String, capturedAt: Long): List<VideoItem> {
        val effectiveQuery = listOf(source.searchPrefix.trim(), query.trim())
            .filter { it.isNotBlank() }
            .joinToString(" ")
        val encoded = URLEncoder.encode(effectiveQuery, "UTF-8")
        val url = source.searchUrlTemplate.replace("{query}", encoded)
        return fetchPageLinks(source, url, capturedAt, "Resultado em ${source.name}")
    }

    private fun fetchWebsite(source: VideoSource, capturedAt: Long): List<VideoItem> =
        fetchPageLinks(source, source.landingUrl, capturedAt, source.group)

    private fun fetchPageLinks(
        source: VideoSource,
        pageUrl: String,
        capturedAt: Long,
        fallbackSummary: String
    ): List<VideoItem> {
        val doc = Jsoup.connect(pageUrl)
            .userAgent(BROWSER_USER_AGENT)
            .header("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.7")
            .referrer("https://www.google.com/")
            .timeout(14_000)
            .maxBodySize(MAX_HTML_BODY_BYTES)
            .followRedirects(true)
            .get()

        val sourceHost = URI(source.landingUrl).host.orEmpty().removePrefix("www.")
        val out = linkedMapOf<String, VideoItem>()

        doc.select("a[href]").forEach { anchor ->
            val absolute = canonicalizeUrl(anchor.absUrl("href").trim())
            if (absolute.isBlank() || samePage(absolute, source.landingUrl) || samePage(absolute, pageUrl)) return@forEach
            val uri = runCatching { URI(absolute) }.getOrNull() ?: return@forEach
            val host = uri.host.orEmpty().removePrefix("www.")
            if (host.isBlank() || !(host == sourceHost || host.endsWith(".$sourceHost") || sourceHost.endsWith(".$host"))) return@forEach
            if (!isSpecificVideoUrl(source, absolute)) return@forEach

            val title = sequenceOf(
                anchor.attr("aria-label"),
                anchor.attr("title"),
                anchor.selectFirst("img[alt]")?.attr("alt").orEmpty(),
                anchor.text()
            ).map(::cleanText).firstOrNull(::usefulTitle).orEmpty()

            val parentText = cleanText(anchor.parent()?.text().orEmpty())
            val summary = when {
                parentText.isBlank() || normalize(parentText) == normalize(title) -> fallbackSummary
                parentText.length > 480 -> fallbackSummary
                else -> parentText.removePrefix(title).trim().ifBlank { fallbackSummary }
            }.take(420)

            val candidateTitle = title.ifBlank { "Vídeo recente • ${source.name}" }
            out.putIfAbsent(
                canonicalKey(absolute),
                VideoItem(
                    title = candidateTitle.take(220),
                    sourceId = source.id,
                    sourceName = source.name,
                    publishedAt = if (isGloboplaySource(source)) 0L else capturedAt,
                    link = absolute,
                    summary = summary,
                    capturedAt = capturedAt
                )
            )
        }

        if (isGloboplaySource(source)) {
            // O Globoplay frequentemente injeta os cards via JSON/JavaScript. Nesses
            // casos o /v/<id> pode não existir como <a href> no HTML parseado. Extraímos
            // também os links presentes nos scripts, inclusive no formato escapado \/v\/. 
            val html = normalizeEmbeddedScriptText(doc.html(), decodeQuotes = false)
            GLOBOPLAY_VIDEO_LINK_REGEX.findAll(html).take(MAX_GLOBOPLAY_LINKS_DISCOVERED).forEach { match ->
                val id = match.groupValues[1]
                if (id.isBlank()) return@forEach
                val direct = "https://globoplay.globo.com/v/$id"
                out.putIfAbsent(
                    canonicalKey(direct),
                    VideoItem(
                        title = "Vídeo recente • ${source.name}",
                        sourceId = source.id,
                        sourceName = source.name,
                        publishedAt = 0L,
                        link = direct,
                        summary = fallbackSummary,
                        capturedAt = capturedAt
                    )
                )
            }
        }

        return out.values.take(if (isGloboplaySource(source)) MAX_GLOBOPLAY_LINKS_DISCOVERED else 40)
    }

    private fun resolveDirectVideoPage(source: VideoSource, candidate: VideoItem, capturedAt: Long): VideoItem? {
        if (!isSpecificVideoUrl(source, candidate.link)) return null

        val doc = Jsoup.connect(candidate.link)
            .userAgent(BROWSER_USER_AGENT)
            .header("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.7")
            .referrer(source.landingUrl)
            .timeout(14_000)
            .maxBodySize(MAX_HTML_BODY_BYTES)
            .followRedirects(true)
            .get()

        val canonical = resolveCanonicalUrl(doc, candidate.link)
        if (!isSpecificVideoUrl(source, canonical)) return null

        val title = sequenceOf(
            doc.selectFirst("meta[property=og:title]")?.attr("content").orEmpty(),
            doc.selectFirst("meta[name=twitter:title]")?.attr("content").orEmpty(),
            doc.selectFirst("meta[itemprop=name]")?.attr("content").orEmpty(),
            doc.selectFirst("h1")?.text().orEmpty(),
            candidate.title,
            doc.title()
        ).map(::cleanText).firstOrNull(::usefulTitle).orEmpty()
        if (!usefulTitle(title)) return null

        val description = sequenceOf(
            doc.selectFirst("meta[property=og:description]")?.attr("content").orEmpty(),
            doc.selectFirst("meta[name=twitter:description]")?.attr("content").orEmpty(),
            doc.selectFirst("meta[name=description]")?.attr("content").orEmpty(),
            doc.selectFirst("meta[itemprop=description]")?.attr("content").orEmpty(),
            candidate.summary
        ).map(::cleanText).firstOrNull { it.isNotBlank() && !isGenericSummary(it) }.orEmpty()

        val metadataTerms = extractMetadataTerms(doc)
        val structuredText = extractStructuredText(doc)
        val enrichedSummary = buildList {
            if (description.isNotBlank()) add(description)
            if (metadataTerms.isNotEmpty()) add("Metadados: ${metadataTerms.take(24).joinToString(", ")}")
            structuredText.take(6).forEach { value ->
                if (value.isNotBlank() && normalize(value) != normalize(description)) add(value)
            }
        }.distinctBy(::normalize)
            .joinToString(" • ")
            .take(MAX_ENRICHED_SUMMARY_LENGTH)

        val publishedAt = parsePublishedAt(doc, capturedAt) ?: candidate.publishedAt.takeIf { it > 0 } ?: 0L
        val hasVideoSignal = pageHasVideoSignal(doc)
        if (!isSpecificVideoUrl(source, canonical) && !hasVideoSignal) return null

        return candidate.copy(
            title = title.take(220),
            publishedAt = publishedAt,
            link = canonicalizeUrl(canonical),
            summary = enrichedSummary
        )
    }

    /**
     * O assunto de um trecho do Globoplay pode aparecer apenas em keywords/tags ou
     * dentro do JSON estruturado. Esses valores passam a integrar o texto localmente
     * comparado com TODOS os Termos e Demandas.
     */
    private fun extractMetadataTerms(doc: Document): List<String> {
        val values = linkedSetOf<String>()

        doc.select(
            "meta[name=keywords], meta[itemprop=keywords], meta[property=article:tag], " +
                "meta[property=video:tag], meta[name=news_keywords]"
        ).forEach { meta ->
            splitMetadata(meta.attr("content")).forEach(values::add)
        }

        doc.select("a[href*=/tag/], a[href*=/tags/], [data-tag], [data-tags], [data-keywords]")
            .take(60)
            .forEach { element ->
                splitMetadata(element.attr("data-tag")).forEach(values::add)
                splitMetadata(element.attr("data-tags")).forEach(values::add)
                splitMetadata(element.attr("data-keywords")).forEach(values::add)
                val text = cleanText(element.text())
                if (text.length in 2..100) values += text
            }

        doc.select("script[type=application/ld+json], script").take(100).forEach { script ->
            val text = normalizeEmbeddedScriptText(script.data().ifBlank { script.html() })
            JSON_METADATA_REGEX.findAll(text).take(30).forEach { match ->
                val raw = match.groupValues.drop(1).firstOrNull { it.isNotBlank() }.orEmpty()
                QUOTED_VALUE_REGEX.findAll(raw).forEach { q ->
                    val item = cleanJsonText(q.groupValues[1])
                    if (item.length in 2..100) values += item
                }
                splitMetadata(raw.replace("\\\"", "\"")).forEach(values::add)
            }
        }

        return values
            .map(::cleanText)
            .filter { it.length in 2..100 && normalize(it) !in GENERIC_METADATA }
            .distinctBy(::normalize)
            .take(60)
    }

    private fun extractStructuredText(doc: Document): List<String> {
        val values = linkedSetOf<String>()
        doc.select("script[type=application/ld+json], script").take(100).forEach { script ->
            val text = normalizeEmbeddedScriptText(script.data().ifBlank { script.html() })
            JSON_TEXT_REGEX.findAll(text).take(30).forEach { match ->
                val value = cleanJsonText(match.groupValues[1])
                if (value.length in 8..700 && !isGenericSummary(value)) values += value
            }
        }
        return values.distinctBy(::normalize).take(12)
    }

    private fun splitMetadata(value: String): List<String> = value
        .replace("[", " ")
        .replace("]", " ")
        .replace("{", " ")
        .replace("}", " ")
        .split(',', ';', '|')
        .map { cleanJsonText(it.trim(' ', '\"', '\'')) }
        .filter { it.length in 2..100 }

    private fun normalizeEmbeddedScriptText(value: String, decodeQuotes: Boolean = true): String {
        var normalized = value
            .replace("\\/", "/")
            .replace("\\u002F", "/", ignoreCase = true)
            .replace("\\u003A", ":", ignoreCase = true)
            .replace("\\u0026", "&", ignoreCase = true)
            .replace("\\u003D", "=", ignoreCase = true)
        if (decodeQuotes) normalized = normalized.replace("\\\"", "\"")
        return normalized
    }

    private fun cleanJsonText(value: String): String = cleanText(
        normalizeEmbeddedScriptText(value)
            .replace("\\n", " ")
            .replace("\\r", " ")
            .replace("\\t", " ")
            .replace("\\/", "/")
            .replace("\\\"", "\"")
            .replace("\\u0026", "&", ignoreCase = true)
    )

    private fun resolveCanonicalUrl(doc: Document, fallback: String): String {
        val candidates = listOf(
            doc.selectFirst("link[rel=canonical]")?.absUrl("href").orEmpty(),
            doc.selectFirst("meta[property=og:url]")?.attr("content").orEmpty(),
            fallback
        )
        return candidates.asSequence()
            .map { resolveUrl(fallback, it) }
            .map(::canonicalizeUrl)
            .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
            .orEmpty()
    }

    private fun resolveUrl(base: String, value: String): String {
        if (value.isBlank()) return ""
        return runCatching { URI(base).resolve(value).toString() }.getOrDefault(value)
    }

    private fun pageHasVideoSignal(doc: Document): Boolean {
        if (doc.selectFirst(
                "video, meta[property=og:video], meta[property=og:video:url], " +
                    "iframe[src*=youtube], iframe[src*=player]"
            ) != null
        ) return true

        return doc.select("script").take(60).any { script ->
            val text = normalizeEmbeddedScriptText(script.data().ifBlank { script.html() })
            text.contains("VideoObject", ignoreCase = true) ||
                text.contains("contentUrl", ignoreCase = true) ||
                text.contains("embedUrl", ignoreCase = true) ||
                text.contains("videoId", ignoreCase = true)
        }
    }

    private fun parsePublishedAt(doc: Document, capturedAt: Long): Long? {
        val values = mutableListOf<String>()
        values += listOf(
            doc.selectFirst("meta[property=article:published_time]")?.attr("content").orEmpty(),
            doc.selectFirst("meta[property=og:published_time]")?.attr("content").orEmpty(),
            doc.selectFirst("meta[name=date]")?.attr("content").orEmpty(),
            doc.selectFirst("meta[itemprop=datePublished]")?.attr("content").orEmpty(),
            doc.selectFirst("time[datetime]")?.attr("datetime").orEmpty()
        ).filter { it.isNotBlank() }

        doc.select("script[type=application/ld+json], script").take(80).forEach { script ->
            val text = normalizeEmbeddedScriptText(script.data().ifBlank { script.html() })
            JSON_DATE_REGEX.findAll(text).take(6).forEach { match ->
                val date = match.groupValues[1]
                if (date.isNotBlank()) values += date
            }
        }

        values.distinct().forEach { value ->
            val parsed = runCatching { Instant.parse(value.trim()).toEpochMilli() }.getOrNull()
            if (parsed != null && parsed in 1..capturedAt) return parsed
        }
        return null
    }

    private fun fetchYoutube(source: VideoSource, capturedAt: Long): List<VideoItem> {
        val handle = source.youtubeHandle.removePrefix("@")
        val channelPage = Jsoup.connect("https://www.youtube.com/@$handle/videos")
            .userAgent("Mozilla/5.0 (Linux; Android 14) MonitorNoticias/3.0.7")
            .timeout(14_000)
            .get()
            .html()

        val channelId = YOUTUBE_CHANNEL_ID_REGEXES.asSequence()
            .mapNotNull { regex -> regex.find(channelPage)?.groupValues?.getOrNull(1) }
            .firstOrNull { it.startsWith("UC") }
            ?: return emptyList()

        val feed = Jsoup.connect("https://www.youtube.com/feeds/videos.xml?channel_id=$channelId")
            .userAgent("Mozilla/5.0 MonitorNoticias/3.0.7")
            .timeout(14_000)
            .parser(Parser.xmlParser())
            .get()

        return feed.select("entry").mapNotNull { entry ->
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

            val summary = listOf(description, "Canal oficial • ${source.group}")
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" • ")
                .take(1000)

            VideoItem(
                title = title.take(220),
                sourceId = source.id,
                sourceName = source.name,
                publishedAt = published,
                link = link,
                summary = summary,
                capturedAt = capturedAt
            )
        }.take(MAX_YOUTUBE_ITEMS_PER_SCAN)
    }

    private fun isSpecificVideoUrl(source: VideoSource, url: String): Boolean {
        if (url.isBlank()) return false
        if (isYoutubeUrl(url)) return isYoutubeVideoUrl(url)
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val path = uri.path.orEmpty()
        val normalizedPath = path.lowercase().trimEnd('/')
        if (normalizedPath.isBlank() || normalizedPath == "/") return false
        if (GENERIC_PATHS.any { normalizedPath == it || normalizedPath.endsWith(it) }) return false
        if (normalizedPath.contains("/busca") || normalizedPath.contains("/search")) return false

        return when {
            isGloboplaySource(source) -> GLOBOPLAY_DIRECT_PATH_REGEX.containsMatchIn(path)
            source.id == "video-r7-record" -> hasSpecificSuffix(path, "/videos/") || hasSpecificSuffix(path, "/video/")
            source.id.startsWith("video-r7-") -> source.linkHints.any { hint -> hasSpecificSuffix(path, hint) }
            source.id == "video-sbt-news" -> hasSpecificSuffix(path, "/videos/")
            source.id == "video-cnn-brasil" -> hasSpecificSuffix(path, "/videos/") || hasSpecificSuffix(path, "/video/")
            source.id.startsWith("video-band") -> hasSpecificSuffix(path, "/videos/")
            else -> source.linkHints.any { hint -> hasSpecificSuffix(path, hint) }
        }
    }

    private fun isGloboplaySource(source: VideoSource): Boolean =
        source.landingUrl.contains("globoplay.globo.com", ignoreCase = true) ||
            source.searchUrlTemplate.contains("globoplay.globo.com", ignoreCase = true) ||
            source.id.startsWith("globoplay-") || source.id.startsWith("video-globoplay")

    private fun isDirectGloboplayVideoUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host.orEmpty().lowercase()
        return host.endsWith("globoplay.globo.com") && GLOBOPLAY_DIRECT_PATH_REGEX.containsMatchIn(uri.path.orEmpty())
    }

    private fun isSourceScanMode(source: VideoSource): Boolean =
        isGloboplaySource(source) || source.youtubeHandle.isNotBlank() ||
            source.id in VideoSourceCatalog.portalProgramScanIds

    private fun hasSpecificSuffix(path: String, marker: String): Boolean {
        val index = path.indexOf(marker, ignoreCase = true)
        if (index < 0) return false
        val suffix = path.substring(index + marker.length).trim('/')
        if (suffix.length < 4) return false
        val normalizedSuffix = normalize(suffix)
        return normalizedSuffix.isNotBlank() && normalizedSuffix !in GENERIC_SLUGS
    }

    private fun isDirectResult(item: VideoItem): Boolean {
        if (!usefulTitle(item.title)) return false
        if (isYoutubeUrl(item.link)) return isYoutubeVideoUrl(item.link)
        val source = VideoSourceCatalog.byId[item.sourceId] ?: return false
        return isSpecificVideoUrl(source, item.link)
    }

    private fun isYoutubeUrl(url: String): Boolean {
        val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        return host == "youtu.be" || host.endsWith("youtube.com")
    }

    private fun isYoutubeVideoUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host.orEmpty().lowercase()
        val path = uri.path.orEmpty().trim('/')
        if (host == "youtu.be") return path.substringBefore('/').length >= 6
        if (!host.endsWith("youtube.com")) return false

        if (uri.path.equals("/watch", ignoreCase = true)) {
            val videoId = uri.rawQuery.orEmpty()
                .split('&')
                .firstOrNull { it.startsWith("v=") }
                ?.substringAfter("v=")
                .orEmpty()
            return videoId.length >= 6
        }

        val parts = path.split('/').filter { it.isNotBlank() }
        return parts.size >= 2 && parts.first().lowercase() in setOf("shorts", "live") && parts[1].length >= 6
    }

    private fun canonicalizeUrl(value: String): String {
        if (value.isBlank()) return ""
        return runCatching {
            val uri = URI(value.trim())
            val host = uri.host.orEmpty().lowercase()
            if (host == "youtu.be" || host.endsWith("youtube.com")) {
                val query = uri.rawQuery.orEmpty()
                val videoId = when {
                    host == "youtu.be" -> uri.path.orEmpty().trim('/').substringBefore('/')
                    uri.path.equals("/watch", ignoreCase = true) ->
                        query.split('&').firstOrNull { it.startsWith("v=") }?.substringAfter("v=").orEmpty()
                    else -> {
                        val parts = uri.path.orEmpty().trim('/').split('/').filter { it.isNotBlank() }
                        if (parts.size >= 2 && parts.first().lowercase() in setOf("shorts", "live")) parts[1] else ""
                    }
                }
                if (videoId.isNotBlank()) "https://www.youtube.com/watch?v=$videoId" else value.trim()
            } else {
                val path = uri.path.orEmpty().ifBlank { "/" }
                URI(uri.scheme ?: "https", uri.userInfo, uri.host, uri.port, path, null, null)
                    .toString()
                    .trimEnd('/')
            }
        }.getOrDefault(value.trim())
    }

    private fun canonicalKey(value: String): String = canonicalizeUrl(value)
        .lowercase()
        .trimEnd('/')

    private fun samePage(first: String, second: String): Boolean = canonicalKey(first) == canonicalKey(second)

    private fun sourceMatchesDemand(source: VideoSource, vehicle: String): Boolean {
        if (vehicle.isBlank()) return true
        val wanted = normalize(vehicle)
        val wantedCompact = compact(vehicle)
        val candidates = listOf(source.name, source.group) + source.aliases
        return candidates.any { candidate ->
            val actual = normalize(candidate)
            val actualCompact = compact(candidate)
            actual == wanted || actualCompact == wantedCompact ||
                (wantedCompact.length >= 4 && actualCompact.contains(wantedCompact)) ||
                (actualCompact.length >= 4 && wantedCompact.contains(actualCompact))
        }
    }

    private fun phraseMatches(text: String, phrase: String): Boolean {
        val haystack = normalize(text)
        val wanted = normalize(phrase)
        if (wanted.isBlank()) return true
        if (matchesSeptember7Event(haystack, wanted)) return true

        val hayTokens = haystack.split(' ').filter { it.isNotBlank() }.toSet()
        val wantedTokens = wanted.split(' ').filter { it.isNotBlank() }
        if (wantedTokens.isEmpty()) return false
        if (wantedTokens.size == 1) return hayTokens.any { tokenEquivalent(it, wantedTokens.first()) }
        if (" $haystack ".contains(" $wanted ")) return true

        val meaningful = wantedTokens.filter { it.length >= 3 && it !in STOP_WORDS }
        return meaningful.isNotEmpty() && meaningful.all { wantedToken ->
            hayTokens.any { actualToken -> tokenEquivalent(actualToken, wantedToken) }
        }
    }

    private fun matchesSeptember7Event(haystack: String, wanted: String): Boolean {
        val wantedTokens = wanted.split(' ').filter { it.isNotBlank() }.toSet()
        if ("7" !in wantedTokens || "setembro" !in wantedTokens) return false
        if (wantedTokens.none { it in SEPTEMBER_7_EVENT_TOKENS }) return false

        val hayTokens = haystack.split(' ').filter { it.isNotBlank() }.toSet()
        return "7" in hayTokens &&
            "setembro" in hayTokens &&
            hayTokens.any { it in SEPTEMBER_7_EVENT_TOKENS }
    }

    private fun tokenEquivalent(actual: String, wanted: String): Boolean {
        if (actual == wanted) return true
        if (actual.length < 5 || wanted.length < 5) return false
        return actual in inflectionVariants(wanted) || wanted in inflectionVariants(actual)
    }

    private fun inflectionVariants(token: String): Set<String> = buildSet {
        add(token)
        when {
            token.endsWith("r") -> add(token + "es")
            token.endsWith("l") -> add(token.dropLast(1) + "is")
            token.endsWith("m") -> add(token.dropLast(1) + "ns")
            token.endsWith("ao") -> {
                add(token.dropLast(2) + "oes")
                add(token.dropLast(2) + "aes")
                add(token.dropLast(2) + "aos")
            }
            !token.endsWith("s") -> add(token + "s")
        }
        when {
            token.endsWith("res") && token.length > 5 -> add(token.dropLast(2))
            token.endsWith("is") && token.length > 5 -> add(token.dropLast(2) + "l")
            token.endsWith("ns") && token.length > 5 -> add(token.dropLast(2) + "m")
            token.endsWith("s") && token.length > 5 -> add(token.dropLast(1))
        }
    }

    private fun inPeriod(item: VideoItem, from: Long?, to: Long?): Boolean {
        if (from != null && item.publishedAt < from) return false
        if (to != null && item.publishedAt > to) return false
        return true
    }

    private fun usefulTitle(value: String): Boolean {
        if (value.length < 8) return false
        val normalized = normalize(value)
        if (normalized in GENERIC_TITLES) return false
        if (normalized.startsWith("todos os videos")) return false
        if (normalized.startsWith("ultimos videos")) return false
        if (normalized.startsWith("mais videos")) return false
        return true
    }

    private fun isGenericSummary(value: String): Boolean {
        val normalized = normalize(value)
        return normalized.startsWith("busca por") || normalized in GENERIC_TITLES
    }

    private fun cleanText(value: String): String = value
        .replace("&nbsp;", " ", ignoreCase = true)
        .replace("&amp;", "&", ignoreCase = true)
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun compact(value: String): String = normalize(value).replace(" ", "")

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    companion object {
        private const val DEFAULT_VIDEO_WINDOW_MS = 24L * 60L * 60L * 1000L
        private const val MAX_REQUEST_FAILURES_PER_SOURCE = 3
        private const val BROWSER_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Mobile Safari/537.36"
        private const val MAX_HTML_BODY_BYTES = 8 * 1024 * 1024
        private const val MAX_RESOLVED_PER_QUERY = 8
        private const val MAX_GLOBOPLAY_ITEMS_PER_SCAN = 40
        private const val MAX_GLOBOPLAY_GENERAL_ITEMS_PER_SCAN = 48
        private const val MAX_GLOBOPLAY_NATIONAL_ITEMS_PER_SCAN = 72
        private const val MAX_GLOBOPLAY_DEEP_FALLBACK_PER_SOURCE = 12
        private const val MAX_GLOBOPLAY_DEEP_FALLBACK_GENERAL = 14
        private const val MAX_GLOBOPLAY_DEEP_FALLBACK_NATIONAL = 72
        private const val MAX_GLOBOPLAY_LINKS_DISCOVERED = 80
        private const val MAX_YOUTUBE_ITEMS_PER_SCAN = 40
        private const val MAX_PORTAL_PROGRAM_ITEMS_PER_SCAN = 40
        private const val MAX_ENRICHED_SUMMARY_LENGTH = 1800

        private val GLOBOPLAY_DIRECT_PATH_REGEX = Regex("/v/[0-9]+/?$", RegexOption.IGNORE_CASE)
        private val GLOBOPLAY_VIDEO_LINK_REGEX = Regex(
            "(?:https?://globoplay\\.globo\\.com)?/v/([0-9]{5,})/?",
            RegexOption.IGNORE_CASE
        )
        private val JSON_METADATA_REGEX = Regex(
            "\\\"(?:keywords|tags|tag|subjects?|topics?|categor(?:y|ies))\\\"\\s*:\\s*(?:\\\"([^\\\"]+)\\\"|\\[([^]]+)])",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val JSON_TEXT_REGEX = Regex(
            "\\\"(?:description|seoDescription|summary|headline|alternativeHeadline|caption|articleBody)\\\"\\s*:\\s*\\\"([^\\\"]{2,700})\\\"",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val JSON_DATE_REGEX = Regex(
            "\\\"(?:datePublished|uploadDate|dateCreated|publishedAt|publicationDate|publishedDate)\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"",
            RegexOption.IGNORE_CASE
        )
        private val QUOTED_VALUE_REGEX = Regex("\\\"([^\\\"]{2,100})\\\"")
        private val YOUTUBE_CHANNEL_ID_REGEXES = listOf(
            Regex("\\\"channelId\\\":\\\"(UC[0-9A-Za-z_-]{20,})\\\""),
            Regex("\\\"externalId\\\":\\\"(UC[0-9A-Za-z_-]{20,})\\\""),
            Regex("\\\"browseId\\\":\\\"(UC[0-9A-Za-z_-]{20,})\\\"")
        )

        private val CORE_NATIONAL_GLOBOPLAY_IDS = setOf(
            "globoplay-bom-dia-brasil",
            "globoplay-hora-1",
            "globoplay-jornal-hoje",
            "globoplay-jornal-nacional",
            "globoplay-jornal-da-globo",
            "globoplay-fantastico"
        )
        private val SEPTEMBER_7_EVENT_TOKENS = setOf(
            "desfile", "desfiles", "comemoracao", "comemoracoes", "independencia"
        )
        private val STOP_WORDS = setOf("de", "do", "da", "dos", "das", "e", "em", "no", "na", "nos", "nas", "a", "o", "as", "os")
        private val GENERIC_TITLES = setOf(
            "videos", "video", "todos os videos", "todos videos", "ultimos videos", "mais videos",
            "ver videos", "ver todos os videos", "ao vivo", "assistir ao vivo", "carregar mais", "ver mais", "ver tudo"
        )
        private val GENERIC_METADATA = setOf("video", "videos", "globoplay", "globo", "jornalismo", "noticias", "noticia")
        private val GENERIC_SLUGS = setOf("videos", "video", "ao vivo", "todos os videos", "ultimos videos")
        private val GENERIC_PATHS = setOf(
            "/videos", "/video", "/ao-vivo", "/busca", "/search", "/categorias/jornalismo"
        )
        private val DEFAULT_TERMS = listOf(
            "Marinha do Brasil", "Capitania dos Portos", "Distrito Naval", "NAM Atlântico",
            "Cisne Branco", "Fragata Marinha do Brasil", "Navio-Patrulha Marinha", "Programa Nuclear da Marinha"
        )
    }
}
