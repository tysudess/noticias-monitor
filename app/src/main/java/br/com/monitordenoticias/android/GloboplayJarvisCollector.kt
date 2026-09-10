package br.com.monitordenoticias.android

import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.time.Instant

/**
 * Camada complementar para os telejornais nacionais do Globoplay.
 *
 * Algumas páginas /cenas/ são preenchidas apenas depois que o JavaScript roda.
 * O Jarvis é o backend usado pelo próprio Globoplay e expõe a busca de vídeos com
 * título, descrição, programa e id direto. As consultas são globais por Termo/Demanda
 * e os resultados são distribuídos localmente por telejornal.
 */
class GloboplayJarvisCollector {

    data class Outcome(
        val candidatesBySourceId: Map<String, List<VideoItem>>,
        val failedQueries: Int
    )

    fun collect(
        rawQueries: List<String>,
        nationalSources: List<VideoSource>,
        capturedAt: Long
    ): Outcome {
        if (nationalSources.isEmpty()) return Outcome(emptyMap(), 0)

        val queries = expandQueries(rawQueries)
        val buckets = nationalSources.associate { it.id to linkedMapOf<String, VideoItem>() }.toMutableMap()
        var failedQueries = 0

        queries.forEach { query ->
            val response = fetchWithRetry(query) ?: run {
                failedQueries++
                return@forEach
            }

            val resources = response
                .optJSONObject("data")
                ?.optJSONObject("search")
                ?.optJSONObject("videos")
                ?.optJSONArray("resources")
                ?: return@forEach

            for (index in 0 until resources.length()) {
                val video = resources.optJSONObject(index) ?: continue
                val videoId = video.optString("id").trim()
                val headline = video.optString("headline").trim()
                val description = video.optString("description").trim()
                val title = video.optJSONObject("title")
                val programName = title?.optString("headline").orEmpty().trim()
                val originProgramId = title?.optString("originProgramId").orEmpty().trim()

                if (videoId.length < 5 || headline.length < 8 || programName.isBlank()) continue
                val source = nationalSources.firstOrNull { sourceMatchesProgram(it, programName) } ?: continue

                val summary = buildList {
                    if (description.isNotBlank()) add(description)
                    add("Programa: $programName")
                    if (originProgramId.isNotBlank()) add("Programa ID: $originProgramId")
                    add("Descoberto via Globoplay Jarvis")
                }.distinct().joinToString(" • ").take(1400)

                val item = VideoItem(
                    title = headline.take(220),
                    sourceId = source.id,
                    sourceName = source.name,
                    publishedAt = 0L,
                    link = "https://globoplay.globo.com/v/$videoId/",
                    summary = summary,
                    capturedAt = capturedAt
                )
                buckets.getValue(source.id).putIfAbsent(videoId, item)
            }
        }

        // Só abrimos a página direta dos candidatos que efetivamente casam com algum
        // Termo/Demanda original. Isso fornece uma data verificável sem multiplicar
        // requisições para os 24 resultados de toda consulta Jarvis.
        var enrichmentBudget = MAX_DATE_ENRICHMENTS_PER_SCAN
        val enrichedBuckets = buckets.mapValues { (_, itemsById) ->
            itemsById.map { (videoId, item) ->
                if (enrichmentBudget <= 0 || !matchesAnyRawQuery(item, rawQueries)) {
                    item
                } else {
                    enrichmentBudget--
                    val publishedAt = runCatching {
                        fetchTargetVideoPublishedAt(videoId, item.link, capturedAt)
                    }.getOrNull() ?: 0L
                    if (publishedAt > 0L) item.copy(publishedAt = publishedAt) else item
                }
            }
        }

        return Outcome(
            candidatesBySourceId = enrichedBuckets,
            failedQueries = failedQueries
        )
    }

    private fun matchesAnyRawQuery(item: VideoItem, rawQueries: List<String>): Boolean {
        val body = "${item.title} ${item.summary}"
        return rawQueries.any { query ->
            query.isNotBlank() && VideoMatchPolicy.phraseMatches(body, query)
        }
    }

    /**
     * A página /v/<id> contém o vídeo atual e também vídeos relacionados. Não podemos
     * usar a primeira datePublished do HTML. Localizamos o ID do vídeo alvo e escolhemos
     * a data válida mais próxima desse ID. Foi este bug que fazia JH 14938964 herdar
     * uma data de 2018 em vez de 07/09/2026.
     */
    private fun fetchTargetVideoPublishedAt(videoId: String, url: String, capturedAt: Long): Long? {
        val doc = Jsoup.connect(url)
            .userAgent(BROWSER_USER_AGENT)
            .header("Accept-Language", "pt-BR,pt;q=0.9")
            .referrer("https://globoplay.globo.com/")
            .timeout(DIRECT_PAGE_TIMEOUT_MS)
            .maxBodySize(MAX_DIRECT_HTML_BYTES)
            .followRedirects(true)
            .get()

        fun parseDate(value: String): Long? = runCatching {
            Instant.parse(value.trim()).toEpochMilli()
        }.getOrNull()?.takeIf { it in 1..capturedAt }

        // Primeiro tentamos metadados inequívocos da própria página.
        listOf(
            doc.selectFirst("meta[property=article:published_time]")?.attr("content").orEmpty(),
            doc.selectFirst("meta[property=og:published_time]")?.attr("content").orEmpty(),
            doc.selectFirst("meta[itemprop=datePublished]")?.attr("content").orEmpty()
        ).filter { it.isNotBlank() }.forEach { value ->
            parseDate(value)?.let { return it }
        }

        var bestDate: Long? = null
        var bestDistance = Int.MAX_VALUE
        doc.select("script[type=application/ld+json], script").take(120).forEach { script ->
            val scriptText = normalizeEmbeddedScriptText(script.data().ifBlank { script.html() })
            val markerPositions = linkedSetOf<Int>()
            TARGET_ID_PATTERNS(videoId).forEach { marker ->
                var from = 0
                while (from < scriptText.length && markerPositions.size < 24) {
                    val position = scriptText.indexOf(marker, from, ignoreCase = true)
                    if (position < 0) break
                    markerPositions += position
                    from = position + marker.length
                }
            }
            if (markerPositions.isEmpty()) return@forEach

            DATE_REGEX.findAll(scriptText).take(160).forEach { match ->
                val parsed = parseDate(match.groupValues[1]) ?: return@forEach
                val distance = markerPositions.minOf { marker -> kotlin.math.abs(match.range.first - marker) }
                if (distance <= TARGET_DATE_CONTEXT_CHARS && distance < bestDistance) {
                    bestDistance = distance
                    bestDate = parsed
                }
            }
        }
        return bestDate
    }

    private fun fetchWithRetry(query: String): JSONObject? {
        repeat(MAX_ATTEMPTS) { attempt ->
            val result = runCatching { fetch(query) }.getOrNull()
            if (result != null && result.optJSONArray("errors") == null) return result
            if (attempt + 1 < MAX_ATTEMPTS) Thread.sleep(RETRY_DELAY_MS)
        }
        return null
    }

    private fun fetch(query: String): JSONObject {
        val payload = JSONObject()
            .put("operationName", "MonitorGloboplaySearch")
            .put(
                "variables",
                JSONObject()
                    .put("q", query)
                    .put("page", 1)
            )
            .put("query", SEARCH_DOCUMENT)

        val connection = (URL(JARVIS_ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            useCaches = false
            instanceFollowRedirects = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9")
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("x-platform-id", "web")
            setRequestProperty("x-device-id", "desktop")
            setRequestProperty("x-client-version", "2024.12-5")
        }

        try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299 || body.isBlank()) {
                throw IOException("Jarvis HTTP $status")
            }
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun sourceMatchesProgram(source: VideoSource, programName: String): Boolean {
        val program = normalize(programName)
        if (program.isBlank()) return false
        val candidates = listOf(source.name, source.searchPrefix) + source.aliases
        return candidates
            .map(::normalize)
            .filter { it.length >= 4 }
            .any { candidate ->
                program == candidate ||
                    program.startsWith("$candidate ") ||
                    candidate.startsWith("$program ")
            }
    }

    private fun expandQueries(rawQueries: List<String>): List<String> {
        val out = linkedMapOf<String, String>()

        fun add(value: String) {
            val cleaned = value.replace(Regex("\\s+"), " ").trim()
            val key = normalize(cleaned)
            if (cleaned.length >= 3 && key.isNotBlank()) out.putIfAbsent(key, cleaned)
        }

        rawQueries.forEach { raw ->
            val normalized = normalize(raw)
            if (normalized.isBlank()) return@forEach
            val tokens = normalized.split(' ').filter { it.isNotBlank() }
            val tokenSet = tokens.toSet()
            val september7 = "7" in tokenSet && "setembro" in tokenSet

            if (september7) {
                add("7 setembro")
                add("desfiles 7 setembro")
                add("desfiles 7 setembro pais")
                add("comemoracoes 7 setembro")
                add("independencia 7 setembro")
            } else {
                add(raw)
                val compact = tokens.filterNot { it in QUERY_STOP_WORDS }.joinToString(" ")
                if (compact.isNotBlank() && compact != normalized) add(compact)

                if (tokens.size == 1 && normalized.endsWith("es") && normalized.length >= 7) {
                    add(normalized.dropLast(2))
                } else if (tokens.size == 1 && normalized.endsWith("s") && normalized.length >= 6) {
                    add(normalized.dropLast(1))
                }
            }
        }

        return out.values.take(MAX_QUERIES_PER_SCAN)
    }

    private fun normalizeEmbeddedScriptText(value: String): String = value
        .replace("\\/", "/")
        .replace("\\u002F", "/", ignoreCase = true)
        .replace("\\u003A", ":", ignoreCase = true)
        .replace("\\u0026", "&", ignoreCase = true)
        .replace("\\u003D", "=", ignoreCase = true)
        .replace("\\\"", "\"")

    private fun normalize(value: String): String = Normalizer.normalize(
        value.lowercase(),
        Normalizer.Form.NFD
    )
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    companion object {
        private const val JARVIS_ENDPOINT = "https://cloud-jarvis.globo.com/graphql"
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 12_000
        private const val DIRECT_PAGE_TIMEOUT_MS = 12_000
        private const val MAX_DIRECT_HTML_BYTES = 4 * 1024 * 1024
        private const val RETRY_DELAY_MS = 350L
        private const val MAX_ATTEMPTS = 2
        private const val MAX_QUERIES_PER_SCAN = 28
        private const val MAX_DATE_ENRICHMENTS_PER_SCAN = 48
        private const val TARGET_DATE_CONTEXT_CHARS = 4_000
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) MonitorNoticias/3.0.10"
        private const val BROWSER_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Mobile Safari/537.36"

        private fun TARGET_ID_PATTERNS(videoId: String) = listOf(
            "\"id\":$videoId",
            "\"id\":\"$videoId\"",
            "/v/$videoId/"
        )

        private val DATE_REGEX = Regex(
            "\\\"(?:datePublished|uploadDate|dateCreated|publishedAt|publicationDate|publishedDate|exhibitedAt)\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"",
            RegexOption.IGNORE_CASE
        )

        private val QUERY_STOP_WORDS = setOf(
            "de", "do", "da", "dos", "das", "e", "em", "no", "na", "nos", "nas", "a", "o", "as", "os"
        )

        private const val SEARCH_DOCUMENT =
            "query MonitorGloboplaySearch(\$q:String!,\$page:Int) { " +
                "search { videos(query:\$q,page:\$page) { " +
                "page nextPage total hasNextPage resources { " +
                "id headline description duration title { headline originProgramId } " +
                "} } } }"
    }
}
