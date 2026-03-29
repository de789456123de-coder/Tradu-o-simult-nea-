package com.seuprojeto.translator

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class WhisperManager(private val context: Context) {

    companion object {
        private const val TAG = "WhisperManager"
    }

    private var whisperLib: WhisperLib? = null
    private var contextPtr: Long = 0
    private var captureManager: AudioCaptureManager? = null

    var onTranscription: ((text: String, language: String) -> Unit)? = null
    var onListeningState: ((active: Boolean) -> Unit)? = null
    var onStatusUpdate: ((msg: String) -> Unit)? = null

    fun init(): Boolean {
        return try {
            val modelFile = File(context.filesDir, "ggml-tiny.bin")
            if (!modelFile.exists()) {
                onStatusUpdate?.invoke("📦 Preparando modelo...")
                context.assets.open("ggml-tiny.bin").use { input ->
                    FileOutputStream(modelFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            onStatusUpdate?.invoke("🔄 Carregando Whisper JNI...")
            whisperLib = WhisperLib()
            contextPtr = whisperLib!!.initContext(modelFile.absolutePath)

            if (contextPtr == 0L) {
                onStatusUpdate?.invoke("❌ Falha ao carregar modelo")
                return false
            }

            onStatusUpdate?.invoke("✅ Whisper JNI pronto!")
            Log.d(TAG, "Whisper OK ptr=$contextPtr")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "JNI erro: ${e.message}")
            onStatusUpdate?.invoke("❌ JNI: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Init erro: ${e.message}")
            onStatusUpdate?.invoke("❌ Erro: ${e.message}")
            false
        }
    }

    fun startListening() {
        if (whisperLib == null || contextPtr == 0L) {
            onStatusUpdate?.invoke("❌ Whisper não inicializado")
            return
        }

        captureManager = AudioCaptureManager(whisperLib!!, contextPtr)
        onListeningState?.invoke(true)

        captureManager?.startRecordingAndTranscribing(
            onVolumeUpdate = { _ -> },
            onTranscriptionResult = { result ->
                AppLogger.log("[WhisperManager] Resultado: $result")
                val clean = result.substringBeforeLast("[").trim()
                if (clean.contains("|") && !clean.startsWith("error")) {
                    val parts = clean.split("|", limit = 2)
                    val lang = parts[0].trim()
                    val text = parts[1].trim()
                    if (text.isNotBlank()) {
                        onTranscription?.invoke(text, lang)
                        onStatusUpdate?.invoke("🎙 Ouvindo (Whisper)...")
                    }
                }
            }
        )
    }

    fun stopListening() {
        captureManager?.stopRecording()
        captureManager = null
        onListeningState?.invoke(false)
    }

    fun release() {
        stopListening()
        if (contextPtr != 0L) {
            whisperLib?.freeContext(contextPtr)
            contextPtr = 0L
        }
    }
}
