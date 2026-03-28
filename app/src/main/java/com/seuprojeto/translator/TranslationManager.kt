package com.seuprojeto.translator

import android.util.Log
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.nl.languageid.LanguageIdentification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.resume

class TranslationManager(private val apiKey: String) {

    companion object {
        private const val TAG = "TranslationManager"
    }

    private val translators = mutableMapOf<String, com.google.mlkit.nl.translate.Translator>()
    private val langIdentifier = LanguageIdentification.getClient()

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

    // Pré-carrega o modelo de tradução offline para um par de idiomas
    suspend fun prepareOfflineModel(sourceLang: String, targetLang: String): Boolean {
        val key = "$sourceLang-$targetLang"
        if (translators.containsKey(key)) return true

        return suspendCancellableCoroutine { cont ->
            try {
                val sourceCode = toMlKitLang(sourceLang)
                val targetCode = toMlKitLang(targetLang)

                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(sourceCode)
                    .setTargetLanguage(targetCode)
                    .build()

                val translator = Translation.getClient(options)
                translators[key] = translator

                translator.downloadModelIfNeeded()
                    .addOnSuccessListener {
                        Log.d(TAG, "Modelo offline pronto: $key")
                        cont.resume(true)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Falha ao baixar modelo: ${e.message}")
                        cont.resume(false)
                    }
            } catch (e: Exception) {
                cont.resume(false)
            }
        }
    }

    // Tradução offline via ML Kit
    suspend fun translateOffline(text: String, sourceLang: String, targetLang: String): String {
        val key = "$sourceLang-$targetLang"
        val translator = translators[key] ?: return translateOnline(text, sourceLang, targetLang)

        return suspendCancellableCoroutine { cont ->
            translator.translate(text)
                .addOnSuccessListener { translated -> cont.resume(translated) }
                .addOnFailureListener {
                    // Fallback para online se offline falhar
                    kotlinx.coroutines.GlobalScope.kotlinx.coroutines.launch {
                        cont.resume(translateOnline(text, sourceLang, targetLang))
                    }
                }
        }
    }

    // Detecção de idioma offline via ML Kit
    suspend fun detectLanguageOffline(text: String): String {
        return suspendCancellableCoroutine { cont ->
            langIdentifier.identifyLanguage(text)
                .addOnSuccessListener { lang ->
                    if (lang == "und") cont.resume("pt") // undetermined
                    else cont.resume(lang)
                }
                .addOnFailureListener { cont.resume("pt") }
        }
    }

    suspend fun detectLanguageSmart(
        text: String,
        leftLang: String,
        rightLang: String,
        lastLang: String
    ): String {
        val words = text.lowercase().split(" ", ",", ".", "!", "?")
        val leftScore  = langSignatures[leftLang]?.count { it in words }  ?: 0
        val rightScore = langSignatures[rightLang]?.count { it in words } ?: 0
        val totalWords = words.size.coerceAtLeast(1)
        val confidence = Math.abs(leftScore - rightScore).toFloat() / totalWords

        if (confidence >= 0.15f) {
            return if (leftScore > rightScore) leftLang else rightLang
        }

        // Usa ML Kit offline para detectar
        val detected = detectLanguageOffline(text)
        return when {
            detected.startsWith(leftLang)  -> leftLang
            detected.startsWith(rightLang) -> rightLang
            lastLang == leftLang  -> rightLang
            lastLang == rightLang -> leftLang
            else -> leftLang
        }
    }

    // Tradução online como fallback
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

    // Tradução: tenta offline primeiro, fallback online
    suspend fun translate(text: String, sourceLang: String, targetLang: String): String {
        val key = "$sourceLang-$targetLang"
        return if (translators.containsKey(key)) {
            translateOffline(text, sourceLang, targetLang)
        } else {
            translateOnline(text, sourceLang, targetLang)
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
