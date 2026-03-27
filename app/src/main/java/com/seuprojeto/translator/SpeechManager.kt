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

    private var recognizerLeft: SpeechRecognizer? = null
    private var recognizerRight: SpeechRecognizer? = null

    var onSpeechLeft: ((String) -> Unit)? = null
    var onSpeechRight: ((String) -> Unit)? = null

    fun init() {
        recognizerLeft = SpeechRecognizer.createSpeechRecognizer(context)
        recognizerRight = SpeechRecognizer.createSpeechRecognizer(context)
        recognizerLeft?.setRecognitionListener(buildListener("LEFT") { text ->
            onSpeechLeft?.invoke(text)
        })
        recognizerRight?.setRecognitionListener(buildListener("RIGHT") { text ->
            onSpeechRight?.invoke(text)
        })
    }

    fun startListeningLeft(languageCode: String = "en-US") {
        recognizerLeft?.startListening(buildIntent(languageCode))
    }

    fun startListeningRight(languageCode: String = "pt-BR") {
        recognizerRight?.startListening(buildIntent(languageCode))
    }

    fun stopAll() {
        recognizerLeft?.stopListening()
        recognizerRight?.stopListening()
    }

    private fun buildIntent(languageCode: String): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }
    }

    private fun buildListener(channel: String, onResult: (String) -> Unit): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onEndOfSpeech() {}
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                Log.d(TAG, "$channel: $text")
                onResult(text)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onError(error: Int) {
                Log.e(TAG, "$channel erro: $error")
                if (channel == "LEFT") startListeningLeft()
                else startListeningRight()
            }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    fun release() {
        recognizerLeft?.destroy()
        recognizerRight?.destroy()
    }
}
