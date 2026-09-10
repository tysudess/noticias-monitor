package br.com.monitordenoticias.android

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import java.text.Normalizer
import java.time.Instant
import java.time.OffsetDateTime

class NewsLatestCollector {

    data class Outcome(
        val items: List<News>,
        val failed: Boolean = false
    )

    private data class Route(
        val url: String,
        val hosts: Set<String>
    )

    private data class Candidate(
        val title: String,
        val snippet: String,
        val link: String
    )

    fun supportedSources(selectedSources: List<MediaSource>, searchAllSources: Boolean): List<MediaSource> {
        val scope = if (searchAllSources) SourceCatalog.national else selectedSources
        return scope.filter { ROUTES.containsKey(it.id) }.distinctBy { it.id }
    }

    fun collect(
        source: MediaSource,
        terms: List<String>,
        demands: List<Demand>,
        from: Long,
        to: Long,
        capturedAt: Long
    ): Outcome {
        val route = ROUTES[source.id] ?: return Outcome(emptyList())
        val listing = runCatching { fetch(route.url) }.getOrElse { return Outcome(emptyList(), failed = true) }

        val candidates = linkedMapOf<String, Candidate>()
        listing.select("a[href]").forEach { anchor ->
            val link = absoluteUrl(route.url, anchor.absUrl("href").ifBlank { anchor.attr("href") }) ?: return@forEach
            val host = runCatching { URI(link).host.orEmpty().lowercase().removePrefix("www.") }.getOrDefault("")
            if (host.isBlank() || route.hosts.none { host == it || host.endsWith(".$it") }) return@forEach
            if (samePage(link, route.url) || !looksLikeArticle(link)) return@forEach

            val title = sequenceOf(
                anchor.attr("aria-label"),
                anchor.attr("title"),
                anchor.selectFirst("img[alt]")?.attr("alt").orEmpty(),
                anchor.text()
            ).map(::clean).firstOrNull(::usefulTitle).orEmpty()
            if (!usefulTitle(title)) return@forEach

            val parent = clean(anchor.parent()?.text().orEmpty())
            val snippet = parent.removePrefix(title).trim().take(700)
            val body = "$title $snippet"
            if (!matchesAny(body, terms, source, demands)) return@forEach

            candidates.putIfAbsent(canonical(link), Candidate(title, snippet, link))
        }

        val items = candidates.values.take(MAX_MATCHED_PER_SOURCE).mapNotNull { candidate ->
            val article = runCatching { fetch(candidate.link) }.getOrNull()
            val title = article?.let(::extractTitle)?.takeIf(::usefulTitle) ?: candidate.title
            val snippet = article?.let(::extractDescription)?.takeIf { it.isNotBlank() } ?: candidate.snippet
            val body = "$title $snippet"
            val matchedTerms = terms.filter { subjectMatches(body, it) }
            val demand = demands.firstOrNull { demand ->
                vehicleMatches(source, demand.vehicle) && subjectMatches(body, demand.subject)
            }
            if (matchedTerms.isEmpty() && demand == null) return@mapNotNull null

            val publishedAt = article?.let { parsePublishedAt(it, capturedAt) } ?: capturedAt
            if (publishedAt !in from..to) return@mapNotNull null

            News(
                title = title.take(260),
                source = source.name,
                date = publishedAt,
                link = candidate.link,
                snippet = snippet.take(700),
                important = demand != null,
                demand = demand != null,
                matchedTerm = matchedTerms.distinct().joinToString(", "),
                matchedDemand = demand?.let { "${it.vehicle} • ${it.subject}" }.orEmpty(),
                capturedAt = capturedAt
            )
        }.distinctBy { canonical(it.link) }
            .sortedByDescending { it.date }

        return Outcome(items)
    }

    private fun fetch(url: String): Document = Jsoup.connect(url)
        .userAgent(USER_AGENT)
        .header("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.7")
        .referrer("https://www.google.com/")
        .timeout(REQUEST_TIMEOUT_MS)
        .maxBodySize(MAX_BODY_BYTES)
        .followRedirects(true)
        .get()

    private fun extractTitle(doc: Document): String = sequenceOf(
        doc.selectFirst("meta[property=og:title]")?.attr("content").orEmpty(),
        doc.selectFirst("meta[name=twitter:title]")?.attr("content").orEmpty(),
        doc.selectFirst("h1")?.text().orEmpty(),
        doc.title()
    ).map(::clean).firstOrNull(::usefulTitle).orEmpty()

    private fun extractDescription(doc: Document): String = sequenceOf(
        doc.selectFirst("meta[property=og:description]")?.attr("content").orEmpty(),
        doc.selectFirst("meta[name=twitter:description]")?.attr("content").orEmpty(),
        doc.selectFirst("meta[name=description]")?.attr("content").orEmpty()
    ).map(::clean).firstOrNull { it.isNotBlank() }.orEmpty()

    private fun parsePublishedAt(doc: Document, capturedAt: Long): Long? {
        val values = buildList {
            doc.select("meta[property=article:published_time], meta[name=date], meta[itemprop=datePublished], time[datetime]")
                .forEach { element ->
                    add(element.attr("content"))
                    add(element.attr("datetime"))
                }
            DATE_PUBLISHED_REGEX.findAll(doc.html()).take(8).forEach { add(it.groupValues[1]) }
        }
        return values.asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull(::parseInstant)
            .firstOrNull { it in 1..(capturedAt + FUTURE_TOLERANCE_MS) }
            ?.coerceAtMost(capturedAt)
    }

    private fun parseInstant(value: String): Long? =
        runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()

    private fun matchesAny(body: String, terms: List<String>, source: MediaSource, demands: List<Demand>): Boolean =
        terms.any { subjectMatches(body, it) } || demands.any { demand ->
            vehicleMatches(source, demand.vehicle) && subjectMatches(body, demand.subject)
        }

    private fun vehicleMatches(source: MediaSource, vehicle: String): Boolean {
        if (vehicle.isBlank()) return true
        val wanted = normalize(vehicle)
        return (listOf(source.name) + source.aliases).any { alias ->
            val actual = normalize(alias)
            actual == wanted || actual.replace(" ", "") == wanted.replace(" ", "") ||
                (wanted.length >= 5 && (actual.startsWith(wanted) || wanted.startsWith(actual)))
        }
    }

    private fun subjectMatches(text: String, subject: String): Boolean {
        val haystack = normalize(text)
        val wanted = normalize(subject)
        if (wanted.isBlank()) return true
        if (" $haystack ".contains(" $wanted ")) return true
        val hayTokens = haystack.split(' ').filter { it.isNotBlank() }.toSet()
        val wantedTokens = wanted.split(' ').filter { it.isNotBlank() }
        if (wantedTokens.size == 1) return wantedTokens.first() in hayTokens
        val meaningful = wantedTokens.filter { it.length >= 3 && it !in STOP_WORDS }
        return meaningful.isNotEmpty() && meaningful.all { it in hayTokens }
    }

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    private fun clean(value: String): String = value.replace(Regex("\\s+"), " ").trim()

    private fun usefulTitle(value: String): Boolean {
        val clean = clean(value)
        if (clean.length < 18) return false
        val normalized = normalize(clean)
        if (normalized in GENERIC_TITLES) return false
        return clean.count { it.isLetterOrDigit() } >= 12
    }

    private fun looksLikeArticle(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val path = uri.path.orEmpty().lowercase()
        if (path.isBlank() || path == "/") return false
        if (path.contains("/ultimas-noticias") || path.contains("/busca") || path.contains("/login") || path.contains("/assine")) return false
        return path.count { it == '/' } >= 2 || path.endsWith(".html") || path.endsWith(".shtml") || path.endsWith(".ghtml")
    }

    private fun absoluteUrl(base: String, value: String): String? = runCatching {
        val resolved = URI(base).resolve(value.trim()).toString()
        if (resolved.startsWith("http://") || resolved.startsWith("https://")) resolved else null
    }.getOrNull()

    private fun samePage(a: String, b: String): Boolean = canonical(a) == canonical(b)

    private fun canonical(value: String): String = runCatching {
        val uri = URI(value)
        val scheme = uri.scheme?.lowercase() ?: "https"
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return@runCatching value.substringBefore('#').substringBefore('?')
        val path = uri.path.orEmpty().trimEnd('/').ifBlank { "/" }
        "$scheme://$host$path"
    }.getOrDefault(value.substringBefore('#').substringBefore('?').trimEnd('/'))

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36 MonitorNoticias/4.0"
        private const val REQUEST_TIMEOUT_MS = 12_000
        private const val MAX_BODY_BYTES = 3_500_000
        private const val MAX_MATCHED_PER_SOURCE = 20
        private const val FUTURE_TOLERANCE_MS = 10L * 60L * 1000L

        private val ROUTES = mapOf(
            "nacional-cnn" to Route("https://www.cnnbrasil.com.br/ultimas-noticias/", setOf("cnnbrasil.com.br")),
            "nacional-metropoles" to Route("https://www.metropoles.com/ultimas-noticias", setOf("metropoles.com")),
            "nacional-folha" to Route("https://www1.folha.uol.com.br/ultimas-noticias/", setOf("folha.uol.com.br")),
            "nacional-r7" to Route("https://noticias.r7.com/", setOf("r7.com")),
            "nacional-jovem-pan" to Route("https://jovempan.com.br/noticias/", setOf("jovempan.com.br")),
            "nacional-o-globo" to Route("https://oglobo.globo.com/ultimas-noticias/", setOf("oglobo.globo.com"))
        )

        private val DATE_PUBLISHED_REGEX = Regex("\\\"datePublished\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"", RegexOption.IGNORE_CASE)
        private val STOP_WORDS = setOf("de", "do", "da", "dos", "das", "e", "em", "no", "na", "nos", "nas", "a", "o", "as", "os")
        private val GENERIC_TITLES = setOf("ultimas noticias", "noticias", "leia mais", "veja mais", "saiba mais", "pagina inicial")
    }
}
