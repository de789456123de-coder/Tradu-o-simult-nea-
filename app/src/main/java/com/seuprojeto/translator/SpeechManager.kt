package com.seuprojeto.translator

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class SpeechManager(private val context: Context) {

    companion object {
        private const val TAG = "SpeechManager"
        private const val RESTART_DELAY_MS = 200L
    }

    private var recognizer: SpeechRecognizer? = null
    private var isListening = false
    private val handler = Handler(Looper.getMainLooper())
    private var consecutiveErrors = 0

    private var leftLang = "pt-BR"
    private var rightLang = "en-US"

    private val langToSTT = mapOf(
        "pt" to "pt-BR", "en" to "en-US", "es" to "es-ES",
        "fr" to "fr-FR", "de" to "de-DE", "it" to "it-IT",
        "nl" to "nl-NL", "he" to "he-IL", "ja" to "ja-JP",
        "zh" to "zh-CN", "ko" to "ko-KR", "ru" to "ru-RU",
        "ar" to "ar-SA", "hi" to "hi-IN", "pl" to "pl-PL"
    )

    var onSpeechResult: ((text: String, confidences: FloatArray?) -> Unit)? = null
    var onPartialSpeech: ((text: String) -> Unit)? = null
    var onListeningState: ((active: Boolean) -> Unit)? = null

    fun init(leftLangCode: String = "pt", rightLangCode: String = "en") {
        leftLang = langToSTT[leftLangCode] ?: "pt-BR"
        rightLang = langToSTT[rightLangCode] ?: "en-US"
        createRecognizer()
    }

    private fun createRecognizer() {
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer?.setRecognitionListener(object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
                consecutiveErrors = 0
                onListeningState?.invoke(true)
            }

            override fun onBeginningOfSpeech() {}
            override fun onEndOfSpeech() {}

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: return
                if (partial.length >= 2) onPartialSpeech?.invoke(partial)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                val text = matches?.firstOrNull() ?: run { restartIfNeeded(); return }
                if (text.length < 2) { restartIfNeeded(); return }
                onSpeechResult?.invoke(text, confidences)
                restartIfNeeded()
            }

            override fun onError(error: Int) {
                Log.e(TAG, "STT erro: $error")
                consecutiveErrors++
                if (consecutiveErrors >= 3) {
                    consecutiveErrors = 0
                    handler.postDelayed({
                        if (isListening) { createRecognizer(); doListen() }
                    }, 800)
                    return
                }
                if (error != SpeechRecognizer.ERROR_CLIENT &&
                    error != SpeechRecognizer.ERROR_NO_MATCH) {
                    handler.postDelayed({ if (isListening) doListen() }, 300)
                } else restartIfNeeded()
            }

            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    fun startListening() {
        isListening = true
        doListen()
    }

    private fun doListen() {
        if (!isListening) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, leftLang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, leftLang)
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES",
                arrayOf(leftLang, rightLang))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 300L)
        }
        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            handler.postDelayed({ if (isListening) doListen() }, 500)
        }
    }

    private fun restartIfNeeded() {
        if (isListening) handler.postDelayed({ doListen() }, RESTART_DELAY_MS)
        else onListeningState?.invoke(false)
    }

    fun stopListening() {
        isListening = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.stopListening()
        onListeningState?.invoke(false)
    }

    fun release() {
        stopListening()
        recognizer?.destroy()
        recognizer = null
    }
}
