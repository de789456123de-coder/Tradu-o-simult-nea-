package com.seuprojeto.translator

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class SpeechManager(private val context: Context) {

    companion object {
        private const val TAG = "SpeechManager"
    }

    private var recognizer: SpeechRecognizer? = null
    private var isListening = false

    // Callback com o texto E o idioma detectado ("pt" ou "en")
    var onSpeechDetected: ((text: String, language: String) -> Unit)? = null

    fun init() {
        recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onEndOfSpeech() {}

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: return

                // Detecta idioma pelo texto reconhecido
                val lang = results.getString("android.speech.extra.LANGUAGE") ?: detectLanguage(text)
                Log.d(TAG, "Texto: $text | Idioma: $lang")
                onSpeechDetected?.invoke(text, lang)

                // Reinicia automaticamente
                if (isListening) startListening()
            }

            override fun onError(error: Int) {
                Log.e(TAG, "Erro: $error")
                if (isListening) startListening()
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    fun startListening() {
        isListening = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // Aceita múltiplos idiomas
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR,en-US")
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-US", "pt-BR"))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }
        recognizer?.startListening(intent)
    }

    fun stopListening() {
        isListening = false
        recognizer?.stopListening()
    }

    // Detecção simples por palavras comuns
    private fun detectLanguage(text: String): String {
        val lower = text.lowercase()
        val ptWords = listOf("que", "não", "sim", "ola", "olá", "como", "para", "por", "com", "uma", "você", "estou", "obrigado", "bom", "dia", "boa", "tarde", "noite")
        val enWords = listOf("the", "and", "is", "are", "you", "what", "how", "hello", "thank", "good", "morning", "please", "yes", "no", "can", "will")

        val ptScore = ptWords.count { lower.contains(it) }
        val enScore = enWords.count { lower.contains(it) }

        return if (enScore > ptScore) "en" else "pt"
    }

    fun release() {
        isListening = false
        recognizer?.destroy()
    }
}
