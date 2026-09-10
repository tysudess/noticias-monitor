package br.com.monitordenoticias.android

import java.text.Normalizer

/**
 * Política única de correspondência usada tanto durante a busca quanto na
 * validação/reparo do banco. Isso evita que um vídeo seja encontrado por uma
 * regra e apagado logo depois por outra regra diferente.
 */
object VideoMatchPolicy {
    fun phraseMatches(text: String, phrase: String): Boolean {
        val haystack = normalize(text)
        val wanted = normalize(phrase)
        if (wanted.isBlank()) return false
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

    private fun normalize(value: String): String = Normalizer.normalize(
        value.lowercase(),
        Normalizer.Form.NFD
    )
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    private val SEPTEMBER_7_EVENT_TOKENS = setOf(
        "desfile", "desfiles", "comemoracao", "comemoracoes", "independencia"
    )

    private val STOP_WORDS = setOf(
        "de", "do", "da", "dos", "das", "e", "em", "no", "na", "nos", "nas", "a", "o", "as", "os"
    )
}
