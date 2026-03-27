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

    var onSpeechDetected: ((text: String, language: String) -> Unit)? = null

    // Idiomas suportados pelo reconhecedor
    private val supportedLanguages = arrayOf(
        "pt-BR", "en-US", "es-ES", "fr-FR", "de-DE",
        "it-IT", "ja-JP", "zh-CN", "ko-KR", "ru-RU"
    )

    fun init() {
        recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onEndOfSpeech() {}

            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: return
                val lang = results.getString("android.speech.extra.LANGUAGE") ?: "pt"
                Log.d(TAG, "Texto: $text | Idioma: $lang")
                onSpeechDetected?.invoke(text, lang)
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
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
            // Todos os idiomas aceitos
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", supportedLanguages)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }
        recognizer?.startListening(intent)
    }

    fun stopListening() {
        isListening = false
        recognizer?.stopListening()
    }

    fun release() {
        isListening = false
        recognizer?.destroy()
    }
}
