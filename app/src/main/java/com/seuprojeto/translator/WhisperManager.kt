package com.seuprojeto.translator

import android.content.Context
import java.io.File
import java.io.FileOutputStream

class WhisperManager(private val context: Context) {
    private var whisperLib: WhisperLib? = null
    private var contextPtr: Long = 0
    private var captureManager: AudioCaptureManager? = null
    private var isListeningActive = false

    var onTranscription: ((text: String, language: String) -> Unit)? = null
    var onListeningState: ((active: Boolean) -> Unit)? = null
    var onStatusUpdate: ((msg: String) -> Unit)? = null

    fun init(): Boolean {
        AppLogger.log("[WhisperManager] init() chamado. Preparando modelo...")
        return try {
            val modelFile = File(context.filesDir, "ggml-tiny.bin")
            if (!modelFile.exists()) {
                AppLogger.log("[WhisperManager] Copiando ggml-tiny.bin dos assets...")
                onStatusUpdate?.invoke("📦 Preparando modelo...")
                context.assets.open("ggml-tiny.bin").use { input ->
                    FileOutputStream(modelFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            
            AppLogger.log("[WhisperManager] Inicializando JNI Context...")
            onStatusUpdate?.invoke("🔄 Carregando Whisper JNI...")
            whisperLib = WhisperLib()
            contextPtr = whisperLib!!.initContext(modelFile.absolutePath)

            if (contextPtr == 0L) {
                AppLogger.log("[WhisperManager] ERRO FATAL: Falha ao carregar JNI.")
                onStatusUpdate?.invoke("❌ Falha ao carregar modelo")
                return false
            }
            
            AppLogger.log("[WhisperManager] JNI carregado com sucesso! Ponteiro: $contextPtr")
            onStatusUpdate?.invoke("✅ Whisper JNI pronto!")
            true
        } catch (e: Exception) {
            AppLogger.log("[WhisperManager] ERRO no init: ${e.message}")
            onStatusUpdate?.invoke("❌ Erro: ${e.message}")
            false
        }
    }

    fun startListening() {
        AppLogger.log("[WhisperManager] startListening() solicitado. isListeningActive = $isListeningActive")
        if (whisperLib == null || contextPtr == 0L) {
            AppLogger.log("[WhisperManager] Ignorado: WhisperLib nulo ou ponteiro zero.")
            return
        }
        
        if (isListeningActive) {
            AppLogger.log("[WhisperManager] BLOQUEADO: Tentativa de clonar microfone evitada.")
            return 
        }

        isListeningActive = true
        AppLogger.log("[WhisperManager] Criando novo AudioCaptureManager.")
        captureManager = AudioCaptureManager(whisperLib!!, contextPtr)
        onListeningState?.invoke(true)

        captureManager?.startRecordingAndTranscribing(
            onVolumeUpdate = { volume ->
                onStatusUpdate?.invoke("🎤 Mic Vol: $volume")
            },
            onTranscriptionResult = { result ->
                AppLogger.log("[WhisperManager] Resultado bruto recebido do C++: $result")
                val clean = result.substringBeforeLast("[").trim()
                if (clean.contains("|") && !clean.startsWith("error")) {
                    val parts = clean.split("|", limit = 2)
                    if (parts[1].trim().isNotBlank()) {
                        AppLogger.log("[WhisperManager] Enviando texto limpo para MainActivity: ${parts[1].trim()}")
                        onTranscription?.invoke(parts[1].trim(), parts[0].trim())
                    }
                }
            }
        )
    }

    fun stopListening() {
        AppLogger.log("[WhisperManager] stopListening() solicitado. isListeningActive = $isListeningActive")
        if (!isListeningActive) return
        
        isListeningActive = false
        AppLogger.log("[WhisperManager] Parando captura e destruindo AudioCaptureManager.")
        captureManager?.stopRecording()
        captureManager = null 
        onListeningState?.invoke(false)
    }

    fun release() {
        AppLogger.log("[WhisperManager] release() chamado. Limpando C++.")
        stopListening()
        if (contextPtr != 0L) {
            whisperLib?.freeContext(contextPtr)
            contextPtr = 0L
        }
    }
}
