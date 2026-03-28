package com.seuprojeto.translator

import android.content.Context
import android.content.Intent
import android.media.AudioManager
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
        private const val RESTART_DELAY_MS = 150L
    }

    private var recognizer: SpeechRecognizer? = null
    private var isListening = false
    private val handler = Handler(Looper.getMainLooper())
    private var consecutiveErrors = 0

    // Alternância por silêncio
    private var currentChannel = "LEFT"  // começa sempre no esquerdo
    private var lastSpeechTime = 0L
    private val SILENCE_THRESHOLD_MS = 2000L  // 2 segundos de silêncio = troca de falante

    var onSpeechDetected: ((text: String, channel: String) -> Unit)? = null
    var onPartialSpeech: ((text: String, channel: String) -> Unit)? = null
    var onListeningState: ((active: Boolean) -> Unit)? = null
    var onChannelChanged: ((channel: String) -> Unit)? = null

    private val supportedLanguages = arrayOf(
        "pt-BR", "en-US", "es-ES", "fr-FR", "de-DE",
        "it-IT", "nl-NL", "he-IL", "ja-JP", "zh-CN",
        "ko-KR", "ru-RU", "ar-SA", "hi-IN", "pl-PL"
    )

    fun init() { createRecognizer() }

    private fun createRecognizer() {
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer?.setRecognitionListener(object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
                consecutiveErrors = 0
                onListeningState?.invoke(true)
            }

            override fun onBeginningOfSpeech() {
                val now = System.currentTimeMillis()
                val silence = now - lastSpeechTime

                // Se houve silêncio suficiente, troca o canal
                if (lastSpeechTime > 0 && silence >= SILENCE_THRESHOLD_MS) {
                    currentChannel = if (currentChannel == "LEFT") "RIGHT" else "LEFT"
                    onChannelChanged?.invoke(currentChannel)
                    Log.d(TAG, "Canal alternado para: $currentChannel (silêncio: ${silence}ms)")
                }
            }

            override fun onEndOfSpeech() {
                lastSpeechTime = System.currentTimeMillis()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: return
                if (partial.length >= 2) {
                    onPartialSpeech?.invoke(partial, currentChannel)
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: run {
                    restartIfNeeded(); return
                }
                if (text.length < 2) { restartIfNeeded(); return }

                Log.d(TAG, "[$currentChannel] $text")
                onSpeechDetected?.invoke(text, currentChannel)
                restartIfNeeded()
            }

            override fun onError(error: Int) {
                Log.e(TAG, "Erro: $error")
                consecutiveErrors++
                if (consecutiveErrors >= 3) {
                    consecutiveErrors = 0
                    handler.postDelayed({
                        if (isListening) { createRecognizer(); doListen() }
                    }, 500)
                    return
                }
                if (error != SpeechRecognizer.ERROR_CLIENT) restartIfNeeded()
            }

            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    fun startListening() {
        isListening = true
        currentChannel = "LEFT"
        lastSpeechTime = 0L
        doListen()
    }

    private fun doListen() {
        if (!isListening) return
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val useHeadset = audioManager.isWiredHeadsetOn || audioManager.isBluetoothScoOn

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", supportedLanguages)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 300L)
            if (useHeadset) putExtra("android.speech.extra.AUDIO_SOURCE", 4)
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

    fun resetChannel() {
        currentChannel = "LEFT"
        lastSpeechTime = 0L
    }

    fun release() {
        stopListening()
        recognizer?.destroy()
        recognizer = null
    }
}
