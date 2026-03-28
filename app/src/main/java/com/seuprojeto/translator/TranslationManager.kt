package com.seuprojeto.translator

import android.util.Log
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.resume

class TranslationManager(private val apiKey: String) {

    companion object { private const val TAG = "TranslationManager" }

    private val translators = mutableMapOf<String, com.google.mlkit.nl.translate.Translator>()
    private val langIdentifier = LanguageIdentification.getClient()

    private val langSignatures = mapOf(
        "pt" to listOf("que", "não", "sim", "como", "para", "com", "uma", "você",
                       "estou", "obrigado", "então", "mas", "porque", "aqui",
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

    suspend fun prepareOfflineModel(sourceLang: String, targetLang: String): Boolean {
        val key = "$sourceLang-$targetLang"
        if (translators.containsKey(key)) return true
        return suspendCancellableCoroutine { cont ->
            try {
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(toMlKitLang(sourceLang))
                    .setTargetLanguage(toMlKitLang(targetLang))
                    .build()
                val translator = Translation.getClient(options)
                translators[key] = translator
                translator.downloadModelIfNeeded()
                    .addOnSuccessListener { cont.resume(true) }
                    .addOnFailureListener { cont.resume(false) }
            } catch (e: Exception) { cont.resume(false) }
        }
    }

    suspend fun detectLanguageSmart(
        text: String,
        leftLang: String,
        rightLang: String,
        lastLang: String,
        context: ContextManager.ConversationContext = ContextManager.ConversationContext.GENERAL
    ): String {
        val words = text.lowercase().split(" ", ",", ".", "!", "?")
        var leftScore  = (langSignatures[leftLang]?.count { it in words } ?: 0) * if (leftLang == "pt") 2 else 1
        var rightScore = (langSignatures[rightLang]?.count { it in words } ?: 0) * if (rightLang == "pt") 2 else 1

        // Boost por palavras do glossário do contexto
        if (context != ContextManager.ConversationContext.GENERAL) {
            val glossary = ContextManager.glossaries[context]
            glossary?.keys?.forEach { term ->
                if (text.lowercase().contains(term)) leftScore += 2
            }
        }

        val confidence = Math.abs(leftScore - rightScore).toFloat() /
            words.size.coerceAtLeast(1)

        if (confidence >= 0.08f) {
            return if (leftScore > rightScore) leftLang else rightLang
        }

        val detectedRaw = detectLanguageOffline(text)
        // Nunca aceita terceiro idioma — força para o par configurado
        val detected = when {
            detectedRaw.startsWith(leftLang) -> leftLang
            detectedRaw.startsWith(rightLang) -> rightLang
            else -> leftLang
        }
        return when {
            detected.startsWith(leftLang)  -> leftLang
            detected.startsWith(rightLang) -> rightLang
            lastLang == leftLang  -> rightLang
            lastLang == rightLang -> leftLang
            else -> leftLang
        }
    }

    private suspend fun detectLanguageOffline(text: String): String {
        return suspendCancellableCoroutine { cont ->
            langIdentifier.identifyLanguage(text)
                .addOnSuccessListener { lang -> cont.resume(if (lang == "und") "pt" else lang) }
                .addOnFailureListener { cont.resume("pt") }
        }
    }

    suspend fun translate(
        text: String,
        sourceLang: String,
        targetLang: String,
        context: ContextManager.ConversationContext = ContextManager.ConversationContext.GENERAL
    ): String {
        // Aplica glossário antes de traduzir
        val enriched = ContextManager.enrichTextForTranslation(text, context, sourceLang, targetLang)
        val key = "$sourceLang-$targetLang"
        val translator = translators[key]
        return if (translator != null) {
            translateOffline(translator, enriched)
        } else {
            translateOnline(enriched, sourceLang, targetLang)
        }
    }

    private suspend fun translateOffline(
        translator: com.google.mlkit.nl.translate.Translator,
        text: String
    ): String {
        return suspendCancellableCoroutine { cont ->
            translator.translate(text)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(text) }
        }
    }

    suspend fun translateOnline(text: String, sourceLang: String, targetLang: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val encodedText = URLEncoder.encode(text, "UTF-8")
                val urlStr = "https://translation.googleapis.com/language/translate/v2" +
                    "?key=$apiKey&q=$encodedText&source=$sourceLang&target=$targetLang&format=text"
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"; connectTimeout = 5000; readTimeout = 5000
                }
                val response = conn.inputStream.bufferedReader().readText()
                JSONObject(response).getJSONObject("data")
                    .getJSONArray("translations").getJSONObject(0)
                    .getString("translatedText")
            } catch (e: Exception) { text }
        }
    }

    private fun toMlKitLang(lang: String): String {
        return when (lang) {
            "pt" -> TranslateLanguage.PORTUGUESE
            "en" -> TranslateLanguage.ENGLISH
            "es" -> TranslateLanguage.SPANISH
            "fr" -> TranslateLanguage.FRENCH
            "de" -> TranslateLanguage.GERMAN
            "it" -> TranslateLanguage.ITALIAN
            "ja" -> TranslateLanguage.JAPANESE
            "zh" -> TranslateLanguage.CHINESE
            "ko" -> TranslateLanguage.KOREAN
            "ru" -> TranslateLanguage.RUSSIAN
            "nl" -> TranslateLanguage.DUTCH
            "ar" -> TranslateLanguage.ARABIC
            "hi" -> TranslateLanguage.HINDI
            "pl" -> TranslateLanguage.POLISH
            "sv" -> TranslateLanguage.SWEDISH
            "tr" -> TranslateLanguage.TURKISH
            "vi" -> TranslateLanguage.VIETNAMESE
            "id" -> TranslateLanguage.INDONESIAN
            "th" -> TranslateLanguage.THAI
            else -> TranslateLanguage.ENGLISH
        }
    }

    fun release() {
        translators.values.forEach { translator -> translator.close() }
        langIdentifier.close()
    }
}
