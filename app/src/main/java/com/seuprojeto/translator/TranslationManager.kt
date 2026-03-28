package com.seuprojeto.translator

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class TranslationManager(private val apiKey: String) {

    private val langSignatures = mapOf(
        "pt" to listOf("que", "não", "sim", "como", "para", "com", "uma", "você",
                       "estou", "obrigado", "então", "mas", "porque", "aqui", "isso",
                       "esse", "ela", "ele", "foi", "ser", "aí", "né", "tá", "pra"),
        "en" to listOf("the", "and", "is", "are", "you", "what", "how", "hello",
                       "thank", "good", "please", "yes", "can", "will", "this",
                       "that", "have", "from", "but", "they", "with", "your"),
        "es" to listOf("que", "los", "las", "una", "como", "pero", "más", "por",
                       "con", "para", "hay", "muy", "este", "ella", "ellos"),
        "fr" to listOf("les", "des", "une", "que", "pour", "dans", "avec", "sur",
                       "pas", "mais", "est", "qui", "par", "tout", "plus"),
        "de" to listOf("die", "der", "und", "den", "von", "mit", "das", "ist",
                       "nicht", "auch", "sich", "sie", "ein", "eine", "als"),
        "it" to listOf("che", "non", "una", "con", "per", "del", "sono", "come",
                       "più", "anche", "questa", "suo", "loro", "hanno")
    )

    suspend fun detectLanguageSmart(
        text: String,
        leftLang: String,
        rightLang: String,
        lastLang: String
    ): String {
        val words = text.lowercase().split(" ", ",", ".", "!", "?")
        val leftScore = langSignatures[leftLang]?.count { it in words } ?: 0
        val rightScore = langSignatures[rightLang]?.count { it in words } ?: 0
        val totalWords = words.size.coerceAtLeast(1)
        val diff = Math.abs(leftScore - rightScore)
        val confidence = diff.toFloat() / totalWords

        if (confidence >= 0.15f) {
            return if (leftScore > rightScore) leftLang else rightLang
        }

        val apiDetected = detectLanguageApi(text)
        return when {
            apiDetected.startsWith(leftLang)  -> leftLang
            apiDetected.startsWith(rightLang) -> rightLang
            lastLang == leftLang  -> rightLang
            lastLang == rightLang -> leftLang
            else -> leftLang
        }
    }

    suspend fun detectLanguage(text: String): String = detectLanguageApi(text)

    private suspend fun detectLanguageApi(text: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val encodedText = URLEncoder.encode(text, "UTF-8")
                val urlStr = "https://translation.googleapis.com/language/translate/v2/detect?key=$apiKey&q=$encodedText"
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                val response = conn.inputStream.bufferedReader().readText()
                JSONObject(response)
                    .getJSONObject("data")
                    .getJSONArray("detections")
                    .getJSONArray(0)
                    .getJSONObject(0)
                    .getString("language")
            } catch (e: Exception) { "pt" }
        }
    }

    suspend fun translate(text: String, sourceLang: String, targetLang: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val encodedText = URLEncoder.encode(text, "UTF-8")
                val urlStr = "https://translation.googleapis.com/language/translate/v2" +
                    "?key=$apiKey&q=$encodedText&source=$sourceLang&target=$targetLang&format=text"
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                val response = conn.inputStream.bufferedReader().readText()
                JSONObject(response)
                    .getJSONObject("data")
                    .getJSONArray("translations")
                    .getJSONObject(0)
                    .getString("translatedText")
            } catch (e: Exception) { text }
        }
    }
}
