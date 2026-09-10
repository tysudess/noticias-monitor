package br.com.monitordenoticias.android

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

/**
 * Descoberta robusta de vídeos recentes do Globoplay.
 *
 * A aba /cenas/ pode depender de JavaScript e chegar vazia ao Jsoup. Já a página
 * principal do programa expõe Edições recentes e cada página /v/<id> de uma
 * edição expõe os Trechos daquela edição no HTML. Por isso o caminho preferido é:
 * programa -> edição recente -> trechos da edição.
 */
class GloboplayEditionCollector {

    private data class EditionRef(
        val url: String,
        val publishedAt: Long,
        val dateKey: String
    )

    fun collect(
        source: VideoSource,
        capturedAt: Long,
        from: Long,
        to: Long,
        onError: () -> Unit
    ): List<VideoItem> {
        if (source.searchPrefix.isBlank()) return emptyList()

        val programPages = linkedSetOf<String>()
        val seedDocs = mutableListOf<Pair<Document, String>>()

        KNOWN_PROGRAM_PAGES[source.id]?.let(programPages::add)
        normalizeProgramPage(source.landingUrl)?.let(programPages::add)

        normalizeDirectVideoUrl(source.landingUrl)?.let { direct ->
            fetchDocumentOrNull(direct, onError)?.let { doc ->
                seedDocs += doc to direct
                extractProgramPages(source, doc, direct).forEach(programPages::add)
            }
        }

        if (programPages.isEmpty()) {
            buildDiscoveryUrls(source).forEach { discoveryUrl ->
                if (programPages.isNotEmpty()) return@forEach
                val discoveryDoc = fetchDocumentOrNull(discoveryUrl, onError) ?: return@forEach
                extractProgramPages(source, discoveryDoc, discoveryUrl).forEach(programPages::add)

                if (programPages.isEmpty()) {
                    extractDirectVideoLinks(discoveryDoc, discoveryUrl)
                        .take(seedLimitFor(source))
                        .forEach { direct ->
                            val doc = fetchDocumentOrNull(direct, onError) ?: return@forEach
                            seedDocs += doc to direct
                            extractProgramPages(source, doc, direct).forEach(programPages::add)
                        }
                }
            }
        }

        val editions = linkedMapOf<String, EditionRef>()

        seedDocs.forEach { (doc, url) ->
            extractEditionRefs(doc, url, capturedAt, from, to).forEach { edition ->
                editions.putIfAbsent(canonicalKey(edition.url), edition)
            }
        }

        programPages.take(programPageLimitFor(source)).forEach { programPage ->
            val doc = fetchDocumentOrNull(programPage, onError) ?: return@forEach
            extractEditionRefs(doc, programPage, capturedAt, from, to).forEach { edition ->
                editions.putIfAbsent(canonicalKey(edition.url), edition)
            }
        }

        if (editions.isEmpty()) return emptyList()

        val out = linkedMapOf<String, VideoItem>()
        editions.values
            .sortedByDescending { it.publishedAt }
            .take(editionLimitFor(source))
            .forEach { edition ->
                val doc = fetchDocumentOrNull(edition.url, onError) ?: return@forEach
                extractTrechosFromEdition(source, doc, edition, capturedAt).forEach { item ->
                    out.putIfAbsent(canonicalKey(item.link), item)
                }
            }

        return out.values.take(MAX_TRECHOS_PER_SOURCE)
    }

    private fun fetchDocumentOrNull(url: String, onError: () -> Unit): Document? =
        runCatching { fetchDocument(url) }.getOrNull()

    private fun fetchDocument(url: String): Document = Jsoup.connect(url)
        .userAgent(USER_AGENT)
        .header("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.7")
        .referrer("https://www.google.com/")
        .timeout(REQUEST_TIMEOUT_MS)
        .maxBodySize(MAX_HTML_BODY_BYTES)
        .followRedirects(true)
        .get()

    private fun buildDiscoveryUrls(source: VideoSource): List<String> {
        if (source.searchUrlTemplate.isBlank()) return listOf(source.landingUrl)
        val state = source.state.trim().takeIf { it.length == 2 && !it.equals("BR", true) }.orEmpty()
        val primary = normalize(source.searchPrefix)
        val queries = buildList {
            if (source.searchPrefix.isNotBlank()) add(source.searchPrefix.trim())
            source.aliases
                .filter { alias ->
                    val normalized = normalize(alias)
                    normalized.isNotBlank() &&
                        normalized != primary &&
                        normalized !in DISCOVERY_GENERIC_ALIASES
                }
                .take(MAX_DISCOVERY_ALIASES)
                .forEach(::add)
        }.distinctBy(::normalize)

        return queries.map { program ->
            val query = listOf(program, state).filter { it.isNotBlank() }.joinToString(" ")
            source.searchUrlTemplate.replace("{query}", URLEncoder.encode(query, "UTF-8"))
        }.distinct()
    }

    private fun extractProgramPages(source: VideoSource, doc: Document, baseUrl: String): List<String> {
        val scored = linkedMapOf<String, Int>()

        doc.select("a[href]").forEach { anchor ->
            val href = anchor.absUrl("href").ifBlank { resolveUrl(baseUrl, anchor.attr("href")) }
            val page = normalizeProgramPage(href) ?: return@forEach
            val score = programPageScore(source, page, nearbyText(anchor))
            if (score > 0) scored[page] = maxOf(scored[page] ?: Int.MIN_VALUE, score)
        }

        val html = normalizeEmbedded(doc.html())
        PROGRAM_LINK_REGEX.findAll(html).take(MAX_PROGRAM_LINKS_IN_HTML).forEach { match ->
            val page = "https://globoplay.globo.com/${match.groupValues[1]}/t/${match.groupValues[2]}"
            val score = programPageScore(source, page, "")
            if (score > 0) scored[page] = maxOf(scored[page] ?: Int.MIN_VALUE, score)
        }

        return scored.entries.sortedByDescending { it.value }.map { it.key }
    }

    private fun extractEditionRefs(
        doc: Document,
        baseUrl: String,
        capturedAt: Long,
        from: Long,
        to: Long
    ): List<EditionRef> {
        val out = linkedMapOf<String, EditionRef>()

        doc.select("a[href]").forEach { anchor ->
            val absolute = anchor.absUrl("href").ifBlank { resolveUrl(baseUrl, anchor.attr("href")) }
            val direct = normalizeDirectVideoUrl(absolute) ?: return@forEach
            val context = nearbyText(anchor)
            if (!looksLikeEdition(context)) return@forEach
            val interval = parseDateInterval(context, capturedAt) ?: return@forEach
            if (interval.second < from || interval.first > to) return@forEach

            val publishedAt = minOf(interval.second, to, capturedAt)
            val key = canonicalKey(direct)
            out[key] = EditionRef(direct, publishedAt, interval.third)
        }

        val html = normalizeEmbedded(doc.html())
        VIDEO_LINK_REGEX.findAll(html).take(MAX_VIDEO_LINKS_IN_HTML).forEach { match ->
            val start = (match.range.first - EMBEDDED_CONTEXT_WINDOW).coerceAtLeast(0)
            val end = (match.range.last + 1 + EMBEDDED_CONTEXT_WINDOW).coerceAtMost(html.length)
            val context = html.substring(start, end)
            if (!looksLikeEdition(context)) return@forEach
            val interval = nearestDateInterval(context, match.range.first - start, capturedAt) ?: return@forEach
            if (interval.second < from || interval.first > to) return@forEach

            val direct = "https://globoplay.globo.com/v/${match.groupValues[1]}"
            val key = canonicalKey(direct)
            out.putIfAbsent(key, EditionRef(direct, minOf(interval.second, to, capturedAt), interval.third))
        }

        return out.values.sortedByDescending { it.publishedAt }
    }

    private fun extractTrechosFromEdition(
        source: VideoSource,
        doc: Document,
        edition: EditionRef,
        capturedAt: Long
    ): List<VideoItem> {
        val out = linkedMapOf<String, VideoItem>()
        val detectedProgram = detectProgramName(doc)
        val candidateSourceName = if (isBroadSweep(source) && detectedProgram.isNotBlank()) {
            "Globoplay • $detectedProgram"
        } else {
            source.name
        }

        fun addCandidate(direct: String, rawTitle: String, rawSummary: String) {
            if (canonicalKey(direct) == canonicalKey(edition.url)) return
            val title = cleanTrechoTitle(rawTitle, source.searchPrefix)
            if (!usefulTitle(title) || looksLikeEdition(title)) return

            val summary = cleanText(rawSummary)
                .takeIf { it.isNotBlank() && normalize(it) != normalize(title) }
                .orEmpty()
                .take(900)

            val item = VideoItem(
                title = title.take(220),
                sourceId = source.id,
                sourceName = candidateSourceName,
                publishedAt = edition.publishedAt,
                link = direct,
                summary = summary,
                capturedAt = capturedAt
            )
            out.putIfAbsent(canonicalKey(direct), item)
        }

        doc.select("a[href]").forEach { anchor ->
            val absolute = anchor.absUrl("href").ifBlank { resolveUrl(edition.url, anchor.attr("href")) }
            val direct = normalizeDirectVideoUrl(absolute) ?: return@forEach
            val context = nearbyText(anchor)
            val title = bestAnchorTitle(anchor, context)
            addCandidate(direct, title, context.removePrefix(title).trim())
        }

        val html = normalizeEmbedded(doc.html())
        VIDEO_LINK_REGEX.findAll(html).take(MAX_VIDEO_LINKS_IN_HTML).forEach { match ->
            val direct = "https://globoplay.globo.com/v/${match.groupValues[1]}"
            if (canonicalKey(direct) == canonicalKey(edition.url)) return@forEach

            val start = (match.range.first - EMBEDDED_CONTEXT_WINDOW).coerceAtLeast(0)
            val end = (match.range.last + 1 + EMBEDDED_CONTEXT_WINDOW).coerceAtMost(html.length)
            val context = html.substring(start, end)
            val center = match.range.first - start
            val title = nearestJsonValue(context, center, EMBEDDED_TITLE_REGEX, 6, 260)
            val summary = nearestJsonValue(context, center, EMBEDDED_SUMMARY_REGEX, 8, 900)
            if (title.isNotBlank()) addCandidate(direct, title, summary)
        }

        return out.values.take(MAX_TRECHOS_PER_SOURCE)
    }

    private fun bestAnchorTitle(anchor: Element, context: String): String {
        val candidates = sequenceOf(
            anchor.attr("aria-label"),
            anchor.attr("title"),
            anchor.selectFirst("img[alt]")?.attr("alt").orEmpty(),
            anchor.selectFirst("h1,h2,h3,h4")?.text().orEmpty(),
            anchor.parent()?.selectFirst("h1,h2,h3,h4")?.text().orEmpty(),
            anchor.nextElementSibling()?.text().orEmpty(),
            anchor.parent()?.nextElementSibling()?.text().orEmpty(),
            anchor.text(),
            context
        ).map(::cleanText)

        return candidates.firstOrNull { usefulTitle(cleanTrechoTitle(it, "")) && !looksLikeEdition(it) }
            ?.let { cleanTrechoTitle(it, "") }
            .orEmpty()
    }

    private fun nearbyText(anchor: Element): String {
        val parts = linkedSetOf<String>()
        fun add(value: String?) {
            val cleaned = cleanText(value.orEmpty())
            if (cleaned.isNotBlank() && cleaned.length <= 1200) parts += cleaned
        }

        add(anchor.attr("aria-label"))
        add(anchor.attr("title"))
        add(anchor.selectFirst("img[alt]")?.attr("alt"))
        add(anchor.text())
        add(anchor.parent()?.text())
        add(anchor.nextElementSibling()?.text())
        add(anchor.previousElementSibling()?.text())
        add(anchor.parent()?.nextElementSibling()?.text())
        add(anchor.parent()?.previousElementSibling()?.text())
        return parts.joinToString(" • ").take(1600)
    }

    private fun detectProgramName(doc: Document): String {
        val h1 = cleanText(doc.selectFirst("h1")?.text().orEmpty())
        if (h1.isBlank()) return ""
        return h1.substringBefore('.').trim().takeIf { it.length in 2..80 }.orEmpty()
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

    private fun nearestJsonValue(
        context: String,
        center: Int,
        regex: Regex,
        minLength: Int,
        maxLength: Int
    ): String = regex.findAll(context)
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

    private fun nearestDateInterval(context: String, center: Int, capturedAt: Long): Triple<Long, Long, String>? =
        DATE_REGEX.findAll(context)
            .mapNotNull { match ->
                parseDateInterval(match.value, capturedAt)?.let { interval ->
                    interval to abs(match.range.first - center)
                }
            }
            .minByOrNull { it.second }
            ?.first

    private fun parseDateInterval(value: String, capturedAt: Long): Triple<Long, Long, String>? {
        val match = DATE_REGEX.find(value) ?: return null
        val day = match.groupValues[1].toIntOrNull() ?: return null
        val month = match.groupValues[2].toIntOrNull() ?: return null
        val year = match.groupValues[3].toIntOrNull() ?: return null
        val raw = "%02d/%02d/%04d".format(day, month, year)
        val parsed = runCatching {
            SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).apply { isLenient = false }.parse(raw)
        }.getOrNull() ?: return null

        val start = Calendar.getInstance().apply {
            time = parsed
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        if (start > capturedAt) return null

        val end = Calendar.getInstance().apply {
            time = parsed
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
        return Triple(start, minOf(end, capturedAt), raw)
    }

    private fun looksLikeEdition(value: String): Boolean {
        val n = normalize(value)
        val hasDate = DATE_REGEX.containsMatchIn(value)
        if (!hasDate) return false
        return n.contains("edicao") || n.contains("integra") || n.contains("programa de hoje")
    }

    private fun cleanTrechoTitle(value: String, program: String): String {
        var title = cleanText(value)
            .replace(DURATION_PREFIX_REGEX, "")
            .trim(' ', '-', '•', '|')
        if (program.isNotBlank() && title.endsWith(program, ignoreCase = true)) {
            title = title.dropLast(program.length).trim(' ', '-', '•', '|')
        }
        return title
    }

    private fun usefulTitle(value: String): Boolean {
        if (value.length < 6) return false
        val n = normalize(value)
        if (n in GENERIC_TITLES) return false
        if (n.startsWith("mais vistos")) return false
        if (n.startsWith("trecho recente")) return false
        return true
    }

    private fun programPageScore(source: VideoSource, url: String, label: String): Int {
        val candidate = normalize("$url $label")
        val desired = normalize(source.searchPrefix)
        if (desired.isBlank()) return 0

        val candidateTokens = candidate.split(' ').filter { it.isNotBlank() }.toSet()
        val desiredTokens = desired.split(' ').filter { it.length >= 2 && it !in STOP_WORDS }
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

    private fun isBroadSweep(source: VideoSource): Boolean =
        source.id.startsWith("globoplay-regionais-")

    private fun programPageLimitFor(source: VideoSource): Int = if (isBroadSweep(source)) 6 else 2
    private fun editionLimitFor(source: VideoSource): Int = if (isBroadSweep(source)) 6 else 2
    private fun seedLimitFor(source: VideoSource): Int = if (isBroadSweep(source)) 6 else 2

    private fun normalizeProgramPage(value: String): String? {
        val match = PROGRAM_LINK_REGEX.find(normalizeEmbedded(value)) ?: return null
        return "https://globoplay.globo.com/${match.groupValues[1]}/t/${match.groupValues[2]}"
    }

    private fun normalizeDirectVideoUrl(value: String): String? {
        val match = VIDEO_LINK_REGEX.find(normalizeEmbedded(value)) ?: return null
        return "https://globoplay.globo.com/v/${match.groupValues[1]}"
    }

    private fun canonicalKey(value: String): String = value.lowercase().trim().trimEnd('/')

    private fun resolveUrl(base: String, value: String): String {
        if (value.isBlank()) return ""
        return runCatching { URI(base).resolve(value).toString() }.getOrDefault(value)
    }

    private fun cleanJsonText(value: String): String = cleanText(
        normalizeEmbedded(value)
            .replace("\\n", " ")
            .replace("\\r", " ")
            .replace("\\t", " ")
    )

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
        private const val MAX_PROGRAM_LINKS_IN_HTML = 100
        private const val MAX_VIDEO_LINKS_IN_HTML = 180
        private const val MAX_TRECHOS_PER_SOURCE = 96
        private const val MAX_DISCOVERY_ALIASES = 2
        private const val EMBEDDED_CONTEXT_WINDOW = 1800

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
            "globoplay-tj1-tapajos" to "https://globoplay.globo.com/jornal-tapajos-1a-edicao/t/hTwfdtmDCQ",
            "globoplay-mg1" to "https://globoplay.globo.com/mg1/t/W7MJbpNcVy",
            "globoplay-mg2" to "https://globoplay.globo.com/mg2/t/5PvVyhyLW1",
            "globoplay-bom-dia-minas" to "https://globoplay.globo.com/bom-dia-minas/t/N22TfC8fcB",
            "globoplay-pi1" to "https://globoplay.globo.com/pitv-1a-edicao/t/M5f6Kxnmxd",
            "globoplay-pi2" to "https://globoplay.globo.com/pitv-2a-edicao/t/LgVGXnBRsd",
            "globoplay-bom-dia-piaui" to "https://globoplay.globo.com/bom-dia-piaui/t/pPhwC5YTNK",
            "globoplay-df1" to "https://globoplay.globo.com/df1/t/jcbtdbRMHz",
            "globoplay-df2" to "https://globoplay.globo.com/df2/t/qP1JkMxfCw",
            "globoplay-ne1" to "https://globoplay.globo.com/ne1/t/MHH2V957Mn"
        )

        private val PROGRAM_LINK_REGEX = Regex(
            "(?:https?://globoplay\\.globo\\.com)?/([a-z0-9-]+)/t/([A-Za-z0-9_-]{6,})/?",
            RegexOption.IGNORE_CASE
        )
        private val VIDEO_LINK_REGEX = Regex(
            "(?:https?://globoplay\\.globo\\.com)?/v/([0-9]{5,})(?:/|\\?|$)",
            RegexOption.IGNORE_CASE
        )
        private val DATE_REGEX = Regex("\\b(\\d{1,2})(?:[º°])?/(\\d{2})/(\\d{4})\\b")
        private val DURATION_PREFIX_REGEX = Regex(
            "^(?:\\d+\\s*(?:h|min|seg|s)\\s*)+",
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
        private val STOP_WORDS = setOf("de", "do", "da", "dos", "das", "e", "em", "no", "na", "nos", "nas", "a", "o", "as", "os")
        private val GENERIC_ALIAS_TOKENS = setOf("globo", "globoplay", "telejornal", "regional", "jornalismo")
        private val DISCOVERY_GENERIC_ALIASES = setOf("globo", "globoplay", "tv globo")
        private val GENERIC_TITLES = setOf(
            "videos", "video", "trechos", "edicoes", "mais videos", "ver mais", "mostrar mais",
            "assistir agora", "detalhes", "similares", "extras"
        )
    }
}
