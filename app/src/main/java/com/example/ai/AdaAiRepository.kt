package com.example.ai

import android.net.Uri
import android.util.Log
import com.example.data.ChatTurn
import com.example.data.GeminiStructuredReply
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class AssistantContextSnapshot(
    val activeName: String,
    val activeRole: String,
    val availability: String,
    val weather: String,
    val currentTime: String,
    val pendingRoutines: List<String>,
    val reminders: List<String>,
    val unreadAlliance: List<String>,
    val notifications: List<String>,
    val familyMembers: List<String>,
    val contacts: List<String>,
    val detectedSpeaker: String? = null,
)

/**
 * Gemini-free open-knowledge & utility assistant backend.
 * Uses local context + public keyless Spanish knowledge endpoints (Wikipedia ES & DuckDuckGo Instant Answer)
 * with zero API keys and zero daily quota limits.
 */
class AdaAiRepository {

    suspend fun generateAssistantReply(
        userMessage: String,
        history: List<ChatTurn>,
        contextSnapshot: AssistantContextSnapshot,
    ): Result<GeminiStructuredReply> = withContext(Dispatchers.IO) {
        try {
            val trimmed = userMessage.trim()
            if (trimmed.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Escribe o di una consulta primero."))
            }

            // 1. Re-check deterministic calculations/conversions if invoked directly
            LocalUtilitySolver.trySolve(trimmed)?.let { solved ->
                return@withContext Result.success(
                    GeminiStructuredReply(reply = solved, actionType = "none")
                )
            }

            val topic = extractSearchTopic(trimmed)
            var sawNetworkFailure = false

            val summary = withTimeout(10_000L) {
                // 2. Direct Spanish Wikipedia summary when topic looks concise
                val direct = tryFetch { fetchWikipediaSummary(topic) }
                if (direct.networkError) sawNetworkFailure = true
                if (!direct.text.isNullOrBlank()) return@withTimeout direct.text

                // 3. Spanish Wikipedia search -> top article summary / snippet
                val searched = tryFetch { searchSpanishWikipedia(topic, trimmed) }
                if (searched.networkError) sawNetworkFailure = true
                if (!searched.text.isNullOrBlank()) return@withTimeout searched.text

                // 4. DuckDuckGo Instant Answer fallback
                val ddg = tryFetch { fetchDuckDuckGoInstantAnswer(trimmed) }
                if (ddg.networkError) sawNetworkFailure = true
                ddg.text
            }

            if (!summary.isNullOrBlank()) {
                return@withContext Result.success(
                    GeminiStructuredReply(reply = summary, actionType = "none")
                )
            }

            if (sawNetworkFailure) {
                return@withContext Result.failure(
                    IOException("Sin conexión a internet para consultar esa información. Tus rutinas, recordatorios, cálculos, hora y mensajes siguen disponibles sin conexión.")
                )
            }

            val politeReply = "No encontré un resumen directo sobre \"$trimmed\" en la búsqueda abierta. " +
                "Puedes consultarme datos o definiciones por tema (por ejemplo: \"qué es la fotosíntesis\"), " +
                "hacer cálculos o conversiones, o pedirme acciones de tu Red Alianza."
            Result.success(GeminiStructuredReply(reply = politeReply, actionType = "none"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error in open knowledge lookup", e)
            Result.failure(
                Exception("No se pudo completar la búsqueda de información (${e.localizedMessage ?: "error de red"}).", e)
            )
        }
    }

    private data class LookupAttempt(
        val text: String? = null,
        val networkError: Boolean = false,
    )

    private inline fun tryFetch(block: () -> String?): LookupAttempt {
        return try {
            LookupAttempt(text = block(), networkError = false)
        } catch (e: IOException) {
            Log.w(TAG, "Network lookup failed", e)
            LookupAttempt(text = null, networkError = true)
        } catch (e: Exception) {
            Log.w(TAG, "Lookup parse warning", e)
            LookupAttempt(text = null, networkError = false)
        }
    }

    private fun fetchWikipediaSummary(title: String): String? {
        val cleanTitle = title.trim().replace(' ', '_')
        if (cleanTitle.length < 2) return null
        val url = "https://es.wikipedia.org/api/rest_v1/page/summary/${Uri.encode(cleanTitle)}"
        val body = httpGet(url) ?: return null
        val json = JSONObject(body)
        if (json.optString("type") == "disambiguation") return null
        val extract = json.optString("extract").trim()
        if (extract.isBlank()) return null
        return condenseForSpeech(extract)
    }

    private fun searchSpanishWikipedia(topic: String, originalQuery: String): String? {
        val query = topic.ifBlank { originalQuery }
        val url = "https://es.wikipedia.org/w/api.php?action=query&list=search&srsearch=${Uri.encode(query)}&utf8=&format=json&srlimit=3"
        val body = httpGet(url) ?: return null
        val root = JSONObject(body)
        val results = root.optJSONObject("query")?.optJSONArray("search") ?: return null
        if (results.length() == 0) return null

        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            val matchedTitle = item.optString("title").trim()
            if (matchedTitle.isNotEmpty()) {
                val summary = fetchWikipediaSummary(matchedTitle)
                if (!summary.isNullOrBlank()) return summary
            }
            val rawSnippet = item.optString("snippet")
                .replace(Regex("<[^>]+>"), "")
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .trim()
            if (rawSnippet.length >= 25) {
                return condenseForSpeech("$matchedTitle: $rawSnippet")
            }
        }
        return null
    }

    private fun fetchDuckDuckGoInstantAnswer(query: String): String? {
        val url = "https://api.duckduckgo.com/?q=${Uri.encode(query)}&format=json&no_html=1&skip_disambig=1&kl=es-es"
        val body = httpGet(url) ?: return null
        val json = JSONObject(body)
        val candidates = listOf(
            json.optString("Answer").trim(),
            json.optString("AbstractText").trim(),
            json.optString("Definition").trim(),
        )
        val best = candidates.firstOrNull { it.isNotBlank() } ?: return null
        return condenseForSpeech(best)
    }

    private fun httpGet(endpoint: String): String? {
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 5_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "AdaAlianzaAndroid/1.0 (https://redalianza.app)")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND) return null
            if (code !in 200..299) {
                throw IOException("HTTP $code")
            }
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun extractSearchTopic(raw: String): String {
        val noPunct = raw.trim().replace(Regex("[¿?¡!]"), "").trim()
        val prefixes = listOf(
            Regex("^(?:dime|cuentame|cuéntame|explicame|explícame|busca|buscar|sabes|sabe)\\s+(?:sobre|acerca de)?\\s*", RegexOption.IGNORE_CASE),
            Regex("^(?:que|qué)\\s+(?:es|son|significa|fue|fueron)\\s+(?:la|el|los|las|un|una|unos|unas)?\\s*", RegexOption.IGNORE_CASE),
            Regex("^(?:quien|quién|quienes|quiénes)\\s+(?:es|fue|son|fueron)\\s+", RegexOption.IGNORE_CASE),
            Regex("^(?:para que|para qué)\\s+sirve\\s+(?:la|el|los|las|un|una)?\\s*", RegexOption.IGNORE_CASE),
            Regex("^(?:definicion|definición|significado|historia)\\s+de\\s+(?:la|el|los|las)?\\s*", RegexOption.IGNORE_CASE),
            Regex("^(?:cual|cuál)\\s+(?:es|fue)\\s+(?:la|el)\\s+", RegexOption.IGNORE_CASE),
        )
        var result = noPunct
        for (regex in prefixes) {
            val updated = result.replace(regex, "").trim()
            if (updated.isNotBlank()) result = updated
        }
        return result.ifBlank { noPunct }
    }

    private fun condenseForSpeech(text: String): String {
        val singleLine = text.replace(Regex("\\s+"), " ").trim()
        val sentences = singleLine.split(Regex("(?<=[.!?])\\s+"))
        val combined = StringBuilder()
        for (sentence in sentences) {
            if (combined.isNotEmpty() && combined.length + sentence.length > 260) break
            if (combined.isNotEmpty()) combined.append(' ')
            combined.append(sentence)
            if (combined.length >= 160) break
        }
        val out = combined.toString().trim()
        return if (out.isNotEmpty()) out else singleLine.take(260)
    }

    companion object {
        private const val TAG = "AdaAiRepository"
    }
}
