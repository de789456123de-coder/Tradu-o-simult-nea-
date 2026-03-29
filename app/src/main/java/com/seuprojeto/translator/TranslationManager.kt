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
        
        // CORREÇÃO: Usando Callbacks nativos do Android em vez do .await()
        return suspendCancellableCoroutine { continuation ->
            translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener {
                    translators[key] = translator
                    continuation.resume(true)
                }
                .addOnFailureListener {
                    continuation.resume(false)
                }
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

        val translator = translators[key]
        if (translator == null) return "Erro: Modelo não baixado."

        // CORREÇÃO: Usando Callbacks nativos do Android em vez do .await()
        return suspendCancellableCoroutine { continuation ->
            translator.translate(text)
                .addOnSuccessListener { translatedText ->
                    continuation.resume(translatedText)
                }
                .addOnFailureListener { e ->
                    continuation.resume("Erro de tradução: ${e.message}")
                }
        }
    }

    suspend fun detectLanguageSmart(
        text: String,
        leftLang: String,
        rightLang: String,
        lastLang: String,
        context: ContextManager.ConversationContext
    ): String {
        return suspendCancellableCoroutine { continuation ->
            val languageIdentifier = LanguageIdentification.getClient()
            
            languageIdentifier.identifyPossibleLanguages(text)
                .addOnSuccessListener { identifiedLanguages ->
                    var leftConfidence = 0.0f
                    var rightConfidence = 0.0f
                    
                    val shortLeft = leftLang.take(2).lowercase()
                    val shortRight = rightLang.take(2).lowercase()

                    AppLogger.log("[ML Kit] Analisando probabilidades para o texto: '$text'")

                    for (language in identifiedLanguages) {
                        if (language.languageTag == shortLeft) leftConfidence = language.confidence
                        if (language.languageTag == shortRight) rightConfidence = language.confidence
                        AppLogger.log("[ML Kit] Idioma: ${language.languageTag} | Probabilidade: ${language.confidence}")
                    }

                    val detected = if (leftConfidence > 0.70f) {
                        AppLogger.log("[ML Kit] Decisão: Bateu $leftConfidence (> 0.70) -> É $leftLang!")
                        leftLang
                    } else if (rightConfidence > leftConfidence) {
                        AppLogger.log("[ML Kit] Decisão: Menor que 0.70, jogando para $rightLang!")
                        rightLang
                    } else {
                        if (leftConfidence > 0) leftLang else rightLang
                    }

                    continuation.resume(detected)
                }
                .addOnFailureListener {
                    continuation.resume(if (lastLang.isNotEmpty()) lastLang else leftLang)
                }
        }
    }

    fun release() {
        translators.values.forEach { it.close() }
        translators.clear()
    }
}
