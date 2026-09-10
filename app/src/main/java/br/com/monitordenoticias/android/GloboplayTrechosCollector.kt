package br.com.monitordenoticias.android

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import java.net.URLEncoder
import java.text.Normalizer
import java.time.Instant
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

/**
 * Coleta recente do Globoplay a partir da página real do programa/telejornal.
 *
 * Estratégia:
 * 1. descobre dinamicamente a URL /<programa>/t/<token>/ a partir da busca do Globoplay;
 * 2. acessa /cenas/, que corresponde à aba "Trechos";
 * 3. extrai somente links diretos /v/<id> com o título do card;
 * 4. se a busca não expuser a página do programa, usa até dois vídeos como sementes
 *    para descobrir o /t/<token>/ e tenta novamente.
 *
 * O cruzamento com Termos e Demandas continua no VideoRepository, localmente.
 */
class GloboplayTrechosCollector {

    fun collect(
        source: VideoSource,
        capturedAt: Long,
        onError: () -> Unit
    ): List<VideoItem> {
        if (source.searchPrefix.isBlank()) return emptyList()

        val programPages = linkedMapOf<String, Int>()

        // Rotas estáveis confirmadas entram antes da busca dinâmica do Globoplay.
        KNOWN_PROGRAM_PAGES[source.id]?.let { programPages[it] = Int.MAX_VALUE }

        // Se a fonte já for uma página /programa/t/<token>, use-a diretamente.
        normalizeProgramPage(source.landingUrl)?.let { programPages[it] = Int.MAX_VALUE - 1 }

        // Landings históricas /v/<id> servem apenas como sementes para descobrir
        // a página estável do programa. O vídeo antigo não vira resultado recorrente.
        if (programPages.isEmpty() && normalizeDirectVideoUrl(source.landingUrl) != null) {
            val seedDoc = runCatching { fetchDocument(source.landingUrl) }
                .onFailure { onError() }
                .getOrNull()
            if (seedDoc != null) {
                extractProgramPages(source, seedDoc, source.landingUrl)
                    .forEach { (url, score) ->
                        programPages[url] = maxOf(programPages[url] ?: Int.MIN_VALUE, score)
                    }
            }
        }

        // A busca geral fica como último recurso de descoberta.
        if (programPages.isEmpty()) {
            val discoveryUrl = buildDiscoveryUrl(source)
            val discoveryDoc = runCatching { fetchDocument(discoveryUrl) }
                .onFailure { onError() }
                .getOrNull()

            if (discoveryDoc != null) {
                extractProgramPages(source, discoveryDoc, discoveryUrl)
                    .forEach { (url, score) ->
                        programPages[url] = maxOf(programPages[url] ?: Int.MIN_VALUE, score)
                    }

                if (programPages.isEmpty()) {
                    extractDirectVideoLinks(discoveryDoc, discoveryUrl)
                        .take(MAX_SEED_VIDEOS)
                        .forEach { seed ->
                            val seedDoc = runCatching { fetchDocument(seed) }
                                .onFailure { onError() }
                                .getOrNull()
                                ?: return@forEach
                            extractProgramPages(source, seedDoc, seed)
                                .forEach { (url, score) ->
                                    programPages[url] = maxOf(programPages[url] ?: Int.MIN_VALUE, score)
                                }
                        }
                }
            }
        }

        if (programPages.isEmpty()) return emptyList()

        val out = linkedMapOf<String, VideoItem>()
        programPages.entries
            .sortedByDescending { it.value }
            .take(MAX_PROGRAM_PAGES_PER_SOURCE)
            .forEach { (programPage, _) ->
                val scenesUrl = programPage.trimEnd('/') + "/cenas/"
                val scenesDoc = runCatching { fetchDocument(scenesUrl) }
                    .onFailure { onError() }
                    .getOrNull()
                    ?: return@forEach

                extractTrechos(source, scenesDoc, scenesUrl, capturedAt).forEach { item ->
                    out.putIfAbsent(canonicalKey(item.link), item)
                }
            }

        return out.values.take(MAX_TRECHOS_PER_SOURCE)
    }

    private fun buildDiscoveryUrl(source: VideoSource): String {
        if (source.searchUrlTemplate.isBlank()) return source.landingUrl

        val stateQualifier = source.state
            .trim()
            .takeIf { it.length == 2 && !it.equals("BR", ignoreCase = true) }
            .orEmpty()
        val query = listOf(source.searchPrefix.trim(), stateQualifier)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        return source.searchUrlTemplate.replace("{query}", URLEncoder.encode(query, "UTF-8"))
    }

    private fun fetchDocument(url: String): Document = Jsoup.connect(url)
        .userAgent(USER_AGENT)
        .header("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.7")
        .referrer("https://www.google.com/")
        .timeout(REQUEST_TIMEOUT_MS)
        .maxBodySize(MAX_HTML_BODY_BYTES)
        .followRedirects(true)
        .get()

    private fun extractProgramPages(
        source: VideoSource,
        doc: Document,
        baseUrl: String
    ): List<Pair<String, Int>> {
        val found = linkedMapOf<String, Int>()

        doc.select("a[href]").forEach { anchor ->
            val href = anchor.absUrl("href").ifBlank { resolveUrl(baseUrl, anchor.attr("href")) }
            val programPage = normalizeProgramPage(href) ?: return@forEach
            val score = programPageScore(source, programPage, anchor.text())
            if (score > 0) found[programPage] = maxOf(found[programPage] ?: Int.MIN_VALUE, score)
        }

        val html = normalizeEmbedded(doc.html())
        PROGRAM_LINK_REGEX.findAll(html).take(MAX_PROGRAM_LINKS_IN_HTML).forEach { match ->
            val programPage = "https://globoplay.globo.com/${match.groupValues[1]}/t/${match.groupValues[2]}"
            val score = programPageScore(source, programPage, "")
            if (score > 0) found[programPage] = maxOf(found[programPage] ?: Int.MIN_VALUE, score)
        }

        return found.entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }
    }

    private fun extractDirectVideoLinks(doc: Document, baseUrl: String): List<String> {
        val out = linkedSetOf<String>()
        doc.select("a[href]").forEach { anchor ->
            val absolute = anchor.absUrl("href").ifBlank { resolveUrl(baseUrl, anchor.attr("href")) }
            normalizeDirectVideoUrl(absolute)?.let(out::add)
        }

        val html = normalizeEmbedded(doc.html())
        VIDEO_LINK_REGEX.findAll(html).take(MAX_VIDEO_LINKS_IN_HTML).forEach { match ->
            out += "https://globoplay.globo.com/v/${match.groupValues[1]}"
        }
        return out.toList()
    }

    private fun extractTrechos(
        source: VideoSource,
        doc: Document,
        pageUrl: String,
        capturedAt: Long
    ): List<VideoItem> {
        val out = linkedMapOf<String, VideoItem>()

        fun addCandidate(
            direct: String,
            rawTitle: String,
            rawSummary: String,
            publishedAt: Long = 0L
        ) {
            val cleanedTitle = cleanTrechoTitle(rawTitle, source.searchPrefix)
            val title = cleanedTitle.takeIf(::usefulTitle)
                ?: "Trecho recente • ${source.searchPrefix.ifBlank { source.name }}"
            val contextual = cleanText(rawSummary)
            val summary = listOf(source.searchPrefix, contextual)
                .filter { it.isNotBlank() && normalize(it) != normalize(title) }
                .distinctBy(::normalize)
                .joinToString(" • ")
                .take(900)

            val candidate = VideoItem(
                title = title.take(220),
                sourceId = source.id,
                sourceName = source.name,
                publishedAt = publishedAt,
                link = direct,
                summary = summary,
                capturedAt = capturedAt
            )
            val key = canonicalKey(direct)
            val previous = out[key]
            val candidateScore = candidateQuality(candidate)
            if (previous == null || candidateScore > candidateQuality(previous)) {
                out[key] = candidate
            }
        }

        // Caminho 1: cards presentes diretamente no HTML. Os cabeçalhos da
        // aba Trechos (ex.: "Hoje, 07/09/2026") delimitam a data dos links abaixo.
        // Uma data sem horário representa um intervalo possível. Usamos o limite
        // superior desse intervalo, mas nunca além do momento da captura. Assim
        // "hoje" não vira 23:59 no futuro e um dia parcialmente dentro das últimas
        // 24h continua elegível até a página /v/<id> fornecer um horário preciso.
        var sectionPublishedAt: Long? = null
        doc.select("h1,h2,h3,h4,a[href]").forEach { element ->
            if (element.tagName().startsWith("h", ignoreCase = true)) {
                sectionPublishedAt = parseSectionDateUpperBound(element.text(), capturedAt)
                return@forEach
            }

            val anchor = element
            val absolute = anchor.absUrl("href").ifBlank { resolveUrl(pageUrl, anchor.attr("href")) }
            val direct = normalizeDirectVideoUrl(absolute) ?: return@forEach

            val rawTitle = sequenceOf(
                anchor.attr("aria-label"),
                anchor.attr("title"),
                anchor.selectFirst("img[alt]")?.attr("alt").orEmpty(),
                anchor.text()
            ).map(::cleanText).firstOrNull { it.isNotBlank() }.orEmpty()

            val parentText = cleanText(anchor.parent()?.text().orEmpty())
            addCandidate(direct, rawTitle, parentText, sectionPublishedAt ?: 0L)
        }

        // Caminho 2: o Globoplay frequentemente injeta Trechos via JSON/JavaScript.
        // O Jsoup não executa JavaScript, então os cards podem não existir como <a>.
        // Extraímos o /v/<id> do HTML bruto e procuramos título/descrição próximos
        // ao mesmo ID dentro do JSON serializado.
        val html = normalizeEmbedded(doc.html())
        VIDEO_LINK_REGEX.findAll(html).take(MAX_VIDEO_LINKS_IN_HTML).forEach { match ->
            val direct = "https://globoplay.globo.com/v/${match.groupValues[1]}"
            val contextStart = (match.range.first - EMBEDDED_CONTEXT_WINDOW).coerceAtLeast(0)
            val contextEnd = (match.range.last + 1 + EMBEDDED_CONTEXT_WINDOW).coerceAtMost(html.length)
            val context = html.substring(contextStart, contextEnd)
            val center = match.range.first - contextStart

            val embeddedTitle = nearestJsonValue(context, center, EMBEDDED_TITLE_REGEX, 6, 260)
                .takeUnless { normalize(it) == normalize(source.searchPrefix) }
                .orEmpty()
            val embeddedSummary = nearestJsonValue(context, center, EMBEDDED_SUMMARY_REGEX, 8, 900)
            val embeddedPublishedAt = nearestEmbeddedPublishedAt(context, center, capturedAt)
                ?: nearestSectionDateUpperBound(context, center, capturedAt)
                ?: 0L
            addCandidate(direct, embeddedTitle, embeddedSummary, embeddedPublishedAt)
        }

        return out.values.take(MAX_TRECHOS_PER_SOURCE)
    }

    private fun candidateQuality(item: VideoItem): Int {
        var score = 0
        if (!item.title.startsWith("Trecho recente •", ignoreCase = true)) score += 30
        if (item.summary.isNotBlank()) score += minOf(item.summary.length / 30, 20)
        if (item.publishedAt > 0L && item.publishedAt != item.capturedAt) score += 8
        return score
    }

    private fun nearestJsonValue(
        context: String,
        center: Int,
        regex: Regex,
        minLength: Int,
        maxLength: Int
    ): String {
        return regex.findAll(context)
            .mapNotNull { match ->
                val value = cleanJsonText(match.groupValues[1])
                if (value.length !in minLength..maxLength) return@mapNotNull null
                if (value.startsWith("http://", true) || value.startsWith("https://", true)) return@mapNotNull null
                if (value.contains("/v/")) return@mapNotNull null
                value to abs(match.range.first - center)
            }
            .minByOrNull { it.second }
            ?.first
            .orEmpty()
    }

    private fun nearestEmbeddedPublishedAt(
        context: String,
        center: Int,
        capturedAt: Long
    ): Long? = EMBEDDED_PUBLISHED_AT_REGEX.findAll(context)
        .mapNotNull { match ->
            parsePrecisePublishedAt(match.groupValues[1], capturedAt)?.let { publishedAt ->
                publishedAt to abs(match.range.first - center)
            }
        }
        .minByOrNull { it.second }
        ?.first

    private fun parsePrecisePublishedAt(value: String, capturedAt: Long): Long? {
        val parsed = runCatching { Instant.parse(value.trim()).toEpochMilli() }.getOrNull() ?: return null
        return parsed.takeIf { it in 1..capturedAt }
    }

    private fun nearestSectionDateUpperBound(
        context: String,
        center: Int,
        capturedAt: Long
    ): Long? = SECTION_DATE_REGEX.findAll(context)
        .mapNotNull { match ->
            parseSectionDateUpperBound(match.value, capturedAt)?.let { publishedAt ->
                publishedAt to abs(match.range.first - center)
            }
        }
        .minByOrNull { it.second }
        ?.first

    private fun parseSectionDateUpperBound(value: String, capturedAt: Long): Long? {
        val rawDate = SECTION_DATE_REGEX.find(value)?.groupValues?.getOrNull(1).orEmpty()
        if (rawDate.isBlank()) return null
        val parsed = runCatching {
            SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).apply { isLenient = false }.parse(rawDate)
        }.getOrNull() ?: return null

        val startOfDay = Calendar.getInstance().apply {
            time = parsed
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        if (startOfDay > capturedAt) return null

        val endOfDay = Calendar.getInstance().apply {
            time = parsed
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
        return minOf(endOfDay, capturedAt)
    }

    private fun cleanJsonText(value: String): String {
        var decoded = normalizeEmbedded(value)
            .replace("\\n", " ")
            .replace("\\r", " ")
            .replace("\\t", " ")
            .replace("\\\\", "\\")
        decoded = UNICODE_ESCAPE_REGEX.replace(decoded) { match ->
            match.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: match.value
        }
        return cleanText(decoded)
            .replace("&quot;", "\"", ignoreCase = true)
            .replace("&#39;", "'", ignoreCase = true)
    }

    private fun normalizeProgramPage(value: String): String? {
        if (value.isBlank()) return null
        val normalized = normalizeEmbedded(value)
        val match = PROGRAM_LINK_REGEX.find(normalized) ?: return null
        return "https://globoplay.globo.com/${match.groupValues[1]}/t/${match.groupValues[2]}"
    }

    private fun normalizeDirectVideoUrl(value: String): String? {
        if (value.isBlank()) return null
        val normalized = normalizeEmbedded(value)
        val match = VIDEO_LINK_REGEX.find(normalized) ?: return null
        return "https://globoplay.globo.com/v/${match.groupValues[1]}"
    }

    private fun programPageScore(source: VideoSource, url: String, label: String): Int {
        val candidate = normalize("$url $label")
        val desired = normalize(source.searchPrefix)
        if (desired.isBlank()) return 0

        val candidateTokens = candidate.split(' ').filter { it.isNotBlank() }.toSet()
        val desiredTokens = desired.split(' ')
            .filter { it.length >= 2 && it !in STOP_WORDS }
        if (desiredTokens.isEmpty()) return 0

        var score = desiredTokens.count { it in candidateTokens } * 8
        if (desiredTokens.all { it in candidateTokens }) score += 60
        if (candidate.contains(desired)) score += 30

        val state = normalize(source.state)
        if (state.length == 2 && state in candidateTokens) score += 6

        source.aliases.take(8).forEach { alias ->
            val aliasTokens = normalize(alias).split(' ')
                .filter { it.length >= 3 && it !in STOP_WORDS && it !in GENERIC_ALIAS_TOKENS }
            if (aliasTokens.isNotEmpty() && aliasTokens.all { it in candidateTokens }) score += 4
        }
        return score
    }

    private fun cleanTrechoTitle(value: String, program: String): String {
        var title = cleanText(value).replace(DURATION_PREFIX_REGEX, "").trim(' ', '-', '•', '|')
        if (program.isNotBlank() && title.endsWith(program, ignoreCase = true)) {
            title = title.dropLast(program.length).trim(' ', '-', '•', '|')
        }
        return title
    }

    private fun usefulTitle(value: String): Boolean {
        if (value.length < 6) return false
        val normalized = normalize(value)
        return normalized !in GENERIC_TITLES &&
            !normalized.startsWith("edicao de") &&
            !normalized.startsWith("mais vistos")
    }

    private fun canonicalKey(value: String): String = value.lowercase().trim().trimEnd('/')

    private fun resolveUrl(base: String, value: String): String {
        if (value.isBlank()) return ""
        return runCatching { URI(base).resolve(value).toString() }.getOrDefault(value)
    }

    private fun cleanText(value: String): String = value
        .replace("&nbsp;", " ", ignoreCase = true)
        .replace("&amp;", "&", ignoreCase = true)
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun normalizeEmbedded(value: String): String = value
        .replace("\\/", "/")
        .replace("\\u002F", "/", ignoreCase = true)
        .replace("\\u003A", ":", ignoreCase = true)
        .replace("\\u0026", "&", ignoreCase = true)
        .replace("\\u003D", "=", ignoreCase = true)
        .replace("\\\"", "\"")

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Mobile Safari/537.36"
        private const val REQUEST_TIMEOUT_MS = 14_000
        private const val MAX_HTML_BODY_BYTES = 8 * 1024 * 1024
        private const val MAX_PROGRAM_PAGES_PER_SOURCE = 2

        private val KNOWN_PROGRAM_PAGES = mapOf(
            "globoplay-bom-dia-brasil" to "https://globoplay.globo.com/bom-dia-brasil/t/6Qg1RywhG5",
            "globoplay-hora-1" to "https://globoplay.globo.com/hora-1/t/s1Zp7Mf9Mn",
            "globoplay-jornal-hoje" to "https://globoplay.globo.com/jornal-hoje/t/w7R6S8ssrm",
            "globoplay-jornal-nacional" to "https://globoplay.globo.com/jornal-nacional/t/QgkQnhBNnR",
            "globoplay-jornal-da-globo" to "https://globoplay.globo.com/jornal-da-globo/t/N6jszcBg6m",
            "globoplay-rj1" to "https://globoplay.globo.com/rj1/t/hcSthQ56JW",
            "globoplay-rj2" to "https://globoplay.globo.com/rj2/t/x5SwXtgSZn",
            "globoplay-sp1" to "https://globoplay.globo.com/sp1/t/MvdbFs2kN8",
            "globoplay-sp2" to "https://globoplay.globo.com/sp2/t/xbFtFNTP81",
            "globoplay-bom-dia-es" to "https://globoplay.globo.com/bom-dia-es/t/DLBLDnCVGs",
            "globoplay-tj1-tapajos" to "https://globoplay.globo.com/jornal-tapajos-1a-edicao/t/hTwfdtmDCQ"
        )
        private const val MAX_SEED_VIDEOS = 2
        private const val MAX_TRECHOS_PER_SOURCE = 48
        private const val MAX_PROGRAM_LINKS_IN_HTML = 80
        private const val MAX_VIDEO_LINKS_IN_HTML = 120
        private const val EMBEDDED_CONTEXT_WINDOW = 1800

        private val PROGRAM_LINK_REGEX = Regex(
            "(?:https?://globoplay\\.globo\\.com)?/([a-z0-9-]+)/t/([A-Za-z0-9_-]{6,})/?",
            RegexOption.IGNORE_CASE
        )
        private val VIDEO_LINK_REGEX = Regex(
            "(?:https?://globoplay\\.globo\\.com)?/v/([0-9]{5,})(?:/|\\?|$)",
            RegexOption.IGNORE_CASE
        )
        private val DURATION_PREFIX_REGEX = Regex(
            "^(?:\\d+\\s*(?:h|min|seg|s)\\s*)+",
            RegexOption.IGNORE_CASE
        )
        private val SECTION_DATE_REGEX = Regex("\\b(\\d{2}/\\d{2}/\\d{4})\\b")
        private val EMBEDDED_PUBLISHED_AT_REGEX = Regex(
            "\\\"(?:datePublished|uploadDate|dateCreated|publishedAt|publicationDate|publishedDate)\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"",
            RegexOption.IGNORE_CASE
        )
        private val EMBEDDED_TITLE_REGEX = Regex(
            "\\\"(?:title|headline|name|label|episodeTitle)\\\"\\s*:\\s*\\\"([^\\\"]{2,500})\\\"",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val EMBEDDED_SUMMARY_REGEX = Regex(
            "\\\"(?:description|seoDescription|summary|caption|synopsis)\\\"\\s*:\\s*\\\"([^\\\"]{2,1200})\\\"",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val UNICODE_ESCAPE_REGEX = Regex("\\\\u([0-9a-fA-F]{4})")
        private val STOP_WORDS = setOf("de", "do", "da", "dos", "das", "e", "em", "no", "na", "nos", "nas", "a", "o", "as", "os")
        private val GENERIC_ALIAS_TOKENS = setOf("globo", "globoplay", "telejornal", "regional", "jornalismo")
        private val GENERIC_TITLES = setOf(
            "videos", "video", "trechos", "edicoes", "mais videos", "ver mais", "mostrar mais",
            "assistir agora", "detalhes", "similares", "extras"
        )
    }
}
