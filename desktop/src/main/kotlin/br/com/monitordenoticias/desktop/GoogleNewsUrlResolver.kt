package br.com.monitordenoticias.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Connection
import org.jsoup.Jsoup
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/** Resolves news.google.com RSS wrapper links to the publisher URL. */
object GoogleNewsUrlResolver {
    private val cache = ConcurrentHashMap<String, String>()

    suspend fun resolve(input: String): String = withContext(Dispatchers.IO) {
        if (!isGoogleNews(input)) return@withContext input
        cache[input]?.let { return@withContext it }
        val decoded = resolveBlocking(input)
        val resolved = decoded?.takeIf { it.startsWith("http") && !isGoogleNews(it) } ?: input
        cache[input] = resolved
        resolved
    }

    private fun resolveBlocking(input: String): String? {
        val id = runCatching { URI(input).path.substringAfterLast('/').trim() }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null

        // Formato antigo: alguns IDs ainda carregam a URL em texto dentro do payload base64.
        decodeLegacyPayload(id)?.let { return it }

        // Formato atual: obtém assinatura/timestamp da página do artigo e consulta Fbv4je.
        decodeWithSignedBatch(id)?.let { return it }

        // Fallback para variantes que ainda respondem ao batchexecute baseado apenas no ID.
        decodeWithIdOnlyBatch(id)?.let { return it }

        return null
    }

    private fun decodeLegacyPayload(id: String): String? = runCatching {
        val padded = id + "=".repeat((4 - id.length % 4) % 4)
        val bytes = Base64.getUrlDecoder().decode(padded)
        val text = String(bytes, StandardCharsets.ISO_8859_1)
        val start = listOf(text.indexOf("https://"), text.indexOf("http://")).filter { it >= 0 }.minOrNull() ?: return@runCatching null
        var end = text.length
        for (i in start until text.length) {
            val ch = text[i]
            if (ch.code < 32 || ch == '\u0000') { end = i; break }
        }
        text.substring(start, end).trim().takeIf { candidate ->
            runCatching { URI(candidate).host != null }.getOrDefault(false) && !isGoogleNews(candidate)
        }
    }.getOrNull()

    private fun decodeWithSignedBatch(id: String): String? = runCatching {
        val doc = Jsoup.connect("https://news.google.com/articles/$id?hl=pt-BR&gl=BR&ceid=BR:pt-419")
            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/125 Safari/537.36")
            .timeout(15000)
            .followRedirects(true)
            .get()

        val node = doc.selectFirst("c-wiz > div[data-n-a-sg][data-n-a-ts]")
            ?: doc.selectFirst("[data-n-a-id='$id'][data-n-a-sg][data-n-a-ts]")
            ?: doc.selectFirst("[data-n-a-sg][data-n-a-ts]")
            ?: return@runCatching null

        val signature = node.attr("data-n-a-sg").takeIf { it.isNotBlank() } ?: return@runCatching null
        val timestamp = node.attr("data-n-a-ts").takeIf { it.isNotBlank() } ?: return@runCatching null
        val articleId = node.attr("data-n-a-id").ifBlank { id }
        val inner = "[\"garturlreq\",[[\"pt-BR\",\"BR\",[\"FINANCE_TOP_INDICES\",\"WEB_TEST_1_0_0\"],null,null,1,1,\"BR:pt-419\",null,480,null,null,null,null,null,0,5],\"pt-BR\",\"BR\",1,[2,4,8],1,1,null,0,0,null,0],\"${jsonEscape(articleId)}\",$timestamp,\"${jsonEscape(signature)}\"]"
        val rpc = JSONArray().put(JSONArray().put(JSONArray().put("Fbv4je").put(inner).put(JSONObject.NULL).put("generic")))
        postBatch(rpc.toString())
    }.getOrNull()

    private fun decodeWithIdOnlyBatch(id: String): String? = runCatching {
        val inner = "[\"garturlreq\",[[\"en-US\",\"US\",[\"FINANCE_TOP_INDICES\",\"WEB_TEST_1_0_0\"],null,null,1,1,\"US:en\",null,180,null,null,null,null,null,0,null,null,[1608992183,723341000]],\"en-US\",\"US\",1,[2,3,4,8],1,0,\"655000234\",0,0,null,0],\"${jsonEscape(id)}\"]"
        val rpc = JSONArray().put(JSONArray().put(JSONArray().put("Fbv4je").put(inner).put(JSONObject.NULL).put("generic")))
        postBatch(rpc.toString())
    }.getOrNull()

    private fun postBatch(payload: String): String? {
        val body = Jsoup.connect("https://news.google.com/_/DotsSplashUi/data/batchexecute?rpcids=Fbv4je")
            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/125 Safari/537.36")
            .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
            .header("Referer", "https://news.google.com/")
            .requestBody("f.req=" + URLEncoder.encode(payload, StandardCharsets.UTF_8.name()))
            .method(Connection.Method.POST)
            .ignoreContentType(true)
            .timeout(15000)
            .execute()
            .body()
        return extractPublisherUrl(body)
    }

    private fun extractPublisherUrl(body: String): String? {
        val afterGuard = body.substringAfter("\n\n", body).trim()
        runCatching {
            val outer = JSONArray(afterGuard)
            for (i in 0 until outer.length()) {
                val row = outer.optJSONArray(i) ?: continue
                val cell = row.optString(2)
                if (cell.isBlank()) continue
                val decoded = runCatching { JSONArray(cell) }.getOrNull()
                val candidate = decoded?.optString(1).orEmpty()
                if (candidate.startsWith("http") && !isGoogleNews(candidate)) return candidate
            }
        }

        // Fallback tolerante a pequenas mudanças no envelope de resposta.
        val normalized = body
            .replace("\\/", "/")
            .replace("\\u003d", "=")
            .replace("\\u0026", "&")
            .replace("\\u0025", "%")
        val candidates = Regex("https?://[^\\\"\\s]+")
            .findAll(normalized)
            .map { it.value.trimEnd(',', ']', '}', '\\') }
            .filter { !isGoogleNews(it) && !it.contains("googleusercontent.com", true) && !it.contains("gstatic.com", true) }
            .toList()
        return candidates.firstOrNull()
    }

    private fun isGoogleNews(url: String): Boolean = runCatching {
        URI(url).host.orEmpty().contains("news.google.com", true)
    }.getOrDefault(url.contains("news.google.com", true))

    private fun jsonEscape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")
}
