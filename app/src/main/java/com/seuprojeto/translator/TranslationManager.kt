package com.seuprojeto.translator

import android.util.Log
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.*
import com.google.mlkit.common.model.DownloadConditions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class TranslationManager {
    private val translators = mutableMapOf<String, Translator>()

    suspend fun prepareOfflineModel(source: String, target: String): Boolean {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.fromLanguageTag(source) ?: TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.fromLanguageTag(target) ?: TranslateLanguage.PORTUGUESE)
            .build()
        val translator = Translation.getClient(options)
        return suspendCancellableCoroutine { cont ->
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
                .addOnSuccessListener { translators["${source}_${target}"] = translator; cont.resume(true) }
                .addOnFailureListener { cont.resume(false) }
        }
    }

    suspend fun translate(text: String, source: String, target: String, ctx: ContextManager.ConversationContext): String {
        val translator = translators["${source}_${target}"] ?: return text
        return suspendCancellableCoroutine { cont ->
            translator.translate(text).addOnSuccessListener { cont.resume(it) }.addOnFailureListener { cont.resume(text) }
        }
    }

    suspend fun detectLanguageSmart(text: String, left: String, right: String, last: String, ctx: ContextManager.ConversationContext): String {
        return suspendCancellableCoroutine { cont ->
            LanguageIdentification.getClient().identifyPossibleLanguages(text)
                .addOnSuccessListener { languages ->
                    var leftConf = 0f; var rightConf = 0f
                    languages.forEach { 
                        if (it.languageTag == left) leftConf = it.confidence
                        if (it.languageTag == right) rightConf = it.confidence
                    }
                    val detected = if (leftConf > 0.70f) left else if (rightConf > leftConf) right else left
                    cont.resume(detected)
                }
                .addOnFailureListener { cont.resume(if (last.isNotEmpty()) last else left) }
        }
    }
}
