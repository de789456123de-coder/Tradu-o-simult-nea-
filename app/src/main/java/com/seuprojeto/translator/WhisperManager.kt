package com.seuprojeto.translator

import android.content.Context
import java.io.File
import java.io.FileOutputStream

class WhisperManager(private val context: Context) {
    companion object {
        private const val TAG = "WhisperManager"
    }

    private var whisperLib: WhisperLib? = null
    private var contextPtr: Long = 0
    private var captureManager: AudioCaptureManager? = null
    
    // A TRAVA DE SEGURANÇA: Garante que só exista 1 microfone ligado por vez!
    private var isListeningActive = false

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
            true
        } catch (e: Exception) {
            onStatusUpdate?.invoke("❌ Erro: ${e.message}")
            false
        }
    }

    fun startListening() {
        if (whisperLib == null || contextPtr == 0L) return
        
        // Se já está escutando, ignora o comando e não cria clones!
        if (isListeningActive) {
            AppLogger.log("Aviso: Ignorando startListening(), microfone já estava ativo.")
            return 
        }

        isListeningActive = true
        captureManager = AudioCaptureManager(whisperLib!!, contextPtr)
        onListeningState?.invoke(true)

        captureManager?.startRecordingAndTranscribing(
            onVolumeUpdate = { volume ->
                onStatusUpdate?.invoke("🎤 Mic Vol: $volume")
            },
            onTranscriptionResult = { result ->
                val clean = result.substringBeforeLast("[").trim()
                if (clean.contains("|") && !clean.startsWith("error")) {
                    val parts = clean.split("|", limit = 2)
                    if (parts[1].trim().isNotBlank()) {
                        onTranscription?.invoke(parts[1].trim(), parts[0].trim())
                    }
                }
            }
        )
    }

    fun stopListening() {
        if (!isListeningActive) return // Só para se realmente estiver rodando
        
        isListeningActive = false
        captureManager?.stopRecording()
        captureManager = null // Destrói o capturador velho
        onListeningState?.invoke(false)
        AppLogger.log("Microfone Desligado e destruído com sucesso.")
    }

    fun release() {
        stopListening()
        if (contextPtr != 0L) {
            whisperLib?.freeContext(contextPtr)
            contextPtr = 0L
        }
    }
}
