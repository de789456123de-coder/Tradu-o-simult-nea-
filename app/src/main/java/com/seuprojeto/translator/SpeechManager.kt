package com.seuprojeto.translator

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class SpeechManager(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null
    var isListening = false

    var onSpeechResult: ((text: String) -> Unit)? = null
    var onPartialSpeech: ((text: String) -> Unit)? = null
    var onListeningState: ((active: Boolean) -> Unit)? = null
    var onError: ((msg: String) -> Unit)? = null

    init { createRecognizer() }

    private fun createRecognizer() {
        recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { onListeningState?.invoke(true) }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { onListeningState?.invoke(false) }
            override fun onError(error: Int) {
                isListening = false
                onListeningState?.invoke(false)
                val msg = when(error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "Não entendi"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Silêncio..."
                    SpeechRecognizer.ERROR_CLIENT -> "Reiniciando..."
                    else -> "Erro STT: $error"
                }
                onError?.invoke(msg)
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                onListeningState?.invoke(false)
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: return
                if (text.isNotBlank()) onSpeechResult?.invoke(text)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: return
                if (partial.isNotBlank()) onPartialSpeech?.invoke(partial)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    // Modo Walkie-Talkie — idioma fixo
    fun startListening(langCode: String) {
        if (isListening) stopListening()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langCode)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
        }
        isListening = true
        try { recognizer?.startListening(intent) }
        catch (e: Exception) { isListening = false; onError?.invoke("Erro ao abrir microfone") }
    }

    // Modo contínuo — bilíngue com detecção automática
    fun startListeningContinuous(primaryLang: String, secondaryLang: String) {
        if (isListening) stopListening()
        recognizer?.destroy()
        createRecognizer()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, primaryLang)
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf(primaryLang, secondaryLang))
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
        }
        isListening = true
        try { recognizer?.startListening(intent) }
        catch (e: Exception) { isListening = false; onError?.invoke("Erro ao abrir microfone") }
    }

    fun stopListening() {
        isListening = false
        recognizer?.stopListening()
        onListeningState?.invoke(false)
    }

    fun release() {
        stopListening()
        recognizer?.destroy()
        recognizer = null
    }
}
