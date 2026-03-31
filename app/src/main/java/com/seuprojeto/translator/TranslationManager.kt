package com.seuprojeto.translator

import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.common.model.DownloadConditions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class TranslationManager(private val apiKey: String = "") {

    private val translators = mutableMapOf<String, Translator>()
    private val conditions = DownloadConditions.Builder().build()

    private fun toMLKit(code: String): String = when (code.take(2).lowercase()) {
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

    suspend fun prepareOfflineModel(sourceLang: String, targetLang: String): Boolean {
        val src = toMLKit(sourceLang)
        val tgt = toMLKit(targetLang)
        val key = "${src}_${tgt}"
        if (translators.containsKey(key)) return true

        val translator = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(src)
                .setTargetLanguage(tgt)
                .build()
        )

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
        context: ContextManager.ConversationContext = ContextManager.ConversationContext.GENERAL
    ): String {
        val key = "${toMLKit(sourceLang)}_${toMLKit(targetLang)}"
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
        context: ContextManager.ConversationContext = ContextManager.ConversationContext.GENERAL
    ): String {
        // FASE 1: Análise rápida por palavras-chave
        // Se tiver palavras exclusivas de um idioma, decide na hora sem chamar ML Kit
        val words = text.lowercase().split(" ", ",", ".", "!", "?", ";")

        val leftSignatures = langSignatures[leftLang.take(2)] ?: emptyList()
        val rightSignatures = langSignatures[rightLang.take(2)] ?: emptyList()

        val leftHits  = words.count { it in leftSignatures }
        val rightHits = words.count { it in rightSignatures }

        // Se tiver hits claros em um lado → decide imediatamente
        if (leftHits > 0 && rightHits == 0) return leftLang
        if (rightHits > 0 && leftHits == 0) return rightLang

        // FASE 2: ML Kit com regra binária agressiva
        return suspendCancellableCoroutine { cont ->
            LanguageIdentification.getClient()
                .identifyPossibleLanguages(text)
                .addOnSuccessListener { languages ->
                    var leftConf  = 0f
                    var rightConf = 0f
                    val shortLeft  = leftLang.take(2).lowercase()
                    val shortRight = rightLang.take(2).lowercase()

                    for (lang in languages) {
                        if (lang.languageTag == shortLeft)  leftConf  = lang.confidence
                        if (lang.languageTag == shortRight) rightConf = lang.confidence
                    }

                    // REGRA BINÁRIA AGRESSIVA:
                    // Se leftLang não bateu 60% de confiança → assume rightLang
                    // Elimina zona cinza — ou é PT ou é EN, sem dúvida
                    val detected = when {
                        leftConf >= 0.60f  -> leftLang   // 60%+ de PT → é PT
                        rightConf > 0f     -> rightLang  // qualquer indício do outro → é EN
                        lastLang.isNotEmpty() -> lastLang // mantém o último
                        else               -> leftLang
                    }

                    cont.resume(detected)
                }
                .addOnFailureListener {
                    // Sem ML Kit → usa último idioma ou leftLang
                    cont.resume(if (lastLang.isNotEmpty()) lastLang else leftLang)
                }
        }
    }

    // Palavras-chave exclusivas por idioma para detecção rápida
    private val langSignatures = mapOf(
        "pt" to listOf(
            "que", "não", "sim", "como", "para", "com", "uma", "você",
            "estou", "obrigado", "então", "mas", "porque", "aqui", "isso",
            "esse", "ela", "ele", "foi", "ser", "aí", "né", "tá", "pra",
            "muito", "bem", "também", "quando", "onde", "quem", "qual",
            "ainda", "já", "só", "mais", "menos", "sempre", "nunca",
            "preciso", "quero", "posso", "tenho", "vou", "estava", "fazer"
        ),
        "en" to listOf(
            "the", "and", "is", "are", "you", "what", "how", "hello",
            "thank", "good", "please", "yes", "can", "will", "this",
            "that", "have", "from", "but", "they", "with", "your",
            "was", "were", "been", "has", "had", "would", "could",
            "should", "does", "did", "not", "just", "very", "also",
            "when", "where", "who", "which", "there", "here", "about",
            "know", "think", "want", "need", "going", "come", "make"
        ),
        "es" to listOf(
            "que", "los", "las", "una", "como", "pero", "más", "por",
            "con", "para", "hay", "muy", "este", "ella", "ellos",
            "también", "cuando", "donde", "quien", "todo", "bien",
            "sí", "gracias", "hola", "porque", "siempre", "nunca"
        ),
        "fr" to listOf(
            "les", "des", "une", "que", "pour", "dans", "avec", "sur",
            "pas", "mais", "est", "qui", "par", "tout", "plus",
            "aussi", "comme", "quand", "bien", "oui", "non", "merci"
        ),
        "de" to listOf(
            "die", "der", "und", "den", "von", "mit", "das", "ist",
            "nicht", "auch", "sich", "sie", "ein", "eine", "als",
            "wenn", "aber", "oder", "weil", "dass", "gut", "ja", "nein"
        ),
        "it" to listOf(
            "che", "non", "una", "con", "per", "del", "sono", "come",
            "più", "anche", "questa", "suo", "loro", "quando", "dove",
            "bene", "sì", "grazie", "ciao", "molto"
        ),
        "nl" to listOf(
            "de", "het", "een", "van", "en", "in", "is", "dat",
            "op", "te", "zijn", "met", "voor", "niet", "ook",
            "maar", "als", "bij", "dan", "hoe", "wat", "waar"
        ),
        "he" to listOf(
            "של", "את", "הוא", "היא", "אני", "כן", "לא", "תודה",
            "שלום", "מה", "איך", "כי", "אבל", "גם", "רק", "יש"
        )
    )

    fun release() {
        translators.values.forEach { it.close() }
        translators.clear()
    }
}
