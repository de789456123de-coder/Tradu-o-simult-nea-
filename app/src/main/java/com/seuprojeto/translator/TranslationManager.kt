package com.seuprojeto.translator

import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.common.model.DownloadConditions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class TranslationManager(private val apiKey: String) {

    private val translators = mutableMapOf<String, Translator>()
    private val conditions = DownloadConditions.Builder().build()

    private fun getMLKitLanguage(code: String): String {
        return when (code.take(2).lowercase()) {
            "pt" -> TranslateLanguage.PORTUGUESE
            "en" -> TranslateLanguage.ENGLISH
            "es" -> TranslateLanguage.SPANISH
            "fr" -> TranslateLanguage.FRENCH
            "de" -> TranslateLanguage.GERMAN
            "it" -> TranslateLanguage.ITALIAN
            "nl" -> TranslateLanguage.DUTCH
            "he" -> TranslateLanguage.HEBREW
            "ja" -> TranslateLanguage.JAPANESE
            "zh" -> TranslateLanguage.CHINESE
            "ko" -> TranslateLanguage.KOREAN
            "ru" -> TranslateLanguage.RUSSIAN
            "ar" -> TranslateLanguage.ARABIC
            "hi" -> TranslateLanguage.HINDI
            "pl" -> TranslateLanguage.POLISH
            else -> TranslateLanguage.ENGLISH
        }
    }

    suspend fun prepareOfflineModel(sourceLang: String, targetLang: String): Boolean {
        val src = getMLKitLanguage(sourceLang)
        val tgt = getMLKitLanguage(targetLang)
        val key = "${src}_${tgt}"
        if (translators.containsKey(key)) return true

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(src)
            .setTargetLanguage(tgt)
            .build()
        val translator = Translation.getClient(options)

        return suspendCancellableCoroutine { cont ->
            translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener { translators[key] = translator; cont.resume(true) }
                .addOnFailureListener { cont.resume(false) }
        }
    }

    suspend fun translate(
        text: String,
        sourceLang: String,
        targetLang: String,
        context: ContextManager.ConversationContext
    ): String {
        val src = getMLKitLanguage(sourceLang)
        val tgt = getMLKitLanguage(targetLang)
        val key = "${src}_${tgt}"
        val translator = translators[key] ?: return text

        return suspendCancellableCoroutine { cont ->
            translator.translate(text)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(text) }
        }
    }

    suspend fun detectLanguageSmart(
        text: String,
        leftLang: String,
        rightLang: String,
        lastLang: String,
        context: ContextManager.ConversationContext
    ): String {
        return suspendCancellableCoroutine { cont ->
            val identifier = LanguageIdentification.getClient()
            identifier.identifyPossibleLanguages(text)
                .addOnSuccessListener { languages ->
                    var leftConf = 0.0f
                    var rightConf = 0.0f
                    val shortLeft  = leftLang.take(2).lowercase()
                    val shortRight = rightLang.take(2).lowercase()

                    for (lang in languages) {
                        if (lang.languageTag == shortLeft)  leftConf  = lang.confidence
                        if (lang.languageTag == shortRight) rightConf = lang.confidence
                    }

                    val detected = when {
                        leftConf  > 0.70f -> leftLang
                        rightConf > leftConf -> rightLang
                        leftConf  > 0f -> leftLang
                        else -> if (lastLang.isNotEmpty()) lastLang else leftLang
                    }
                    cont.resume(detected)
                }
                .addOnFailureListener {
                    cont.resume(if (lastLang.isNotEmpty()) lastLang else leftLang)
                }
        }
    }

    fun release() {
        translators.values.forEach { it.close() }
        translators.clear()
    }
}
