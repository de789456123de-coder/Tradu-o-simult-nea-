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
        private const val MIN_CONFIDENCE = 0.4f
        private const val RESTART_DELAY_MS = 150L
        private const val MIN_TEXT_LENGTH = 2
    }

    private var recognizer: SpeechRecognizer? = null
    private var isListening = false
    private val handler = Handler(Looper.getMainLooper())
    private var lastPartial = ""
    private var consecutiveErrors = 0

    var onSpeechDetected: ((text: String, language: String) -> Unit)? = null
    var onPartialSpeech: ((text: String) -> Unit)? = null
    var onListeningState: ((active: Boolean) -> Unit)? = null

    // Idiomas suportados — quanto mais, melhor a detecção
    private val supportedLanguages = arrayOf(
        "pt-BR", "en-US", "es-ES", "fr-FR", "de-DE",
        "it-IT", "ja-JP", "zh-CN", "ko-KR", "ru-RU",
        "nl-NL", "ar-SA", "hi-IN", "pl-PL", "sv-SE",
        "tr-TR", "vi-VN", "id-ID", "th-TH", "he-IL"
    )

    fun init() {
        createRecognizer()
    }

    private fun createRecognizer() {
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer?.setRecognitionListener(object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
                consecutiveErrors = 0
                onListeningState?.invoke(true)
                Log.d(TAG, "Pronto para ouvir")
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "Fala iniciada")
            }

            override fun onEndOfSpeech() {
                Log.d(TAG, "Fala encerrada")
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: return
                if (partial != lastPartial && partial.length >= MIN_TEXT_LENGTH) {
                    lastPartial = partial
                    onPartialSpeech?.invoke(partial)
                }
            }

            override fun onResults(results: Bundle?) {
                lastPartial = ""
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

                if (matches.isNullOrEmpty()) {
                    restartIfNeeded()
                    return
                }

                // Pega o resultado com maior confiança
                var bestText = ""
                var bestConf = 0f
                matches.forEachIndexed { i, text ->
                    val conf = confidences?.getOrNull(i) ?: 0.5f
                    if (conf > bestConf && text.length >= MIN_TEXT_LENGTH) {
                        bestConf = conf
                        bestText = text
                    }
                }

                // Ignora se confiança muito baixa
                if (bestConf < MIN_CONFIDENCE && bestText.isEmpty()) {
                    Log.d(TAG, "Confiança baixa ($bestConf), ignorando")
                    restartIfNeeded()
                    return
                }

                if (bestText.isEmpty()) bestText = matches.first()

                val lang = results?.getString("android.speech.extra.LANGUAGE") ?: ""
                Log.d(TAG, "Resultado: '$bestText' conf=$bestConf lang=$lang")
                onSpeechDetected?.invoke(bestText, lang)

                restartIfNeeded()
            }

            override fun onError(error: Int) {
                val msg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "erro de áudio"
                    SpeechRecognizer.ERROR_CLIENT -> "erro do cliente"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "sem permissão"
                    SpeechRecognizer.ERROR_NETWORK -> "sem rede"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "timeout de rede"
                    SpeechRecognizer.ERROR_NO_MATCH -> "sem correspondência"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "reconhecedor ocupado"
                    SpeechRecognizer.ERROR_SERVER -> "erro do servidor"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "timeout de fala"
                    else -> "erro $error"
                }
                Log.e(TAG, "Erro: $msg")
                consecutiveErrors++

                // Recria o reconhecedor após muitos erros seguidos
                if (consecutiveErrors >= 3) {
                    Log.w(TAG, "Muitos erros, recriando reconhecedor...")
                    consecutiveErrors = 0
                    handler.postDelayed({
                        if (isListening) {
                            createRecognizer()
                            startListening()
                        }
                    }, 500)
                    return
                }

                // Erros normais — só reinicia
                if (error != SpeechRecognizer.ERROR_CLIENT) {
                    restartIfNeeded()
                }
            }

            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    fun startListening() {
        isListening = true
        lastPartial = ""
        doListen()
    }

    private fun doListen() {
        if (!isListening) return

        // Detecta microfone do fone se conectado
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val useHeadset = audioManager.isWiredHeadsetOn || audioManager.isBluetoothScoOn

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", supportedLanguages)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 800L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 300L)

            // Usa microfone do fone se disponível
            if (useHeadset) {
                putExtra("android.speech.extra.AUDIO_SOURCE", 4) // VOICE_COMMUNICATION
                Log.d(TAG, "Usando microfone do fone")
            } else {
                Log.d(TAG, "Usando microfone do celular")
            }
        }

        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar: ${e.message}")
            handler.postDelayed({ if (isListening) doListen() }, 500)
        }
    }

    private fun restartIfNeeded() {
        if (isListening) {
            handler.postDelayed({ doListen() }, RESTART_DELAY_MS)
        } else {
            onListeningState?.invoke(false)
        }
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
