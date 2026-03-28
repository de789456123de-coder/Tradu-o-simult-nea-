package com.seuprojeto.translator

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class WhisperManager(private val context: Context) {

    companion object {
        private const val TAG = "WhisperManager"
        private const val SAMPLE_RATE = 16000
        private const val CHUNK_DURATION_MS = 4000L
        private const val MODEL_URL =
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin"
    }

    private var isRecording = false
    private var audioRecord: AudioRecord? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var whisperLib: WhisperLib? = null
    private var contextPtr: Long = 0

    var onTranscription: ((text: String, language: String) -> Unit)? = null
    var onListeningState: ((active: Boolean) -> Unit)? = null
    var onStatusUpdate: ((msg: String) -> Unit)? = null

    fun init(): Boolean {
        return try {
            // Baixa modelo se não existir
            val modelFile = File(context.filesDir, "ggml-tiny.bin")
            if (!modelFile.exists()) {
                onStatusUpdate?.invoke("⬇️ Baixando modelo Whisper (75MB)...")
                downloadModel(modelFile)
            }

            onStatusUpdate?.invoke("🔄 Carregando Whisper...")
            whisperLib = WhisperLib()
            contextPtr = whisperLib!!.initContext(modelFile.absolutePath)

            if (contextPtr == 0L) {
                onStatusUpdate?.invoke("❌ Whisper falhou ao carregar modelo")
                return false
            }

            onStatusUpdate?.invoke("✅ Whisper JNI pronto!")
            Log.d(TAG, "Whisper inicializado via JNI! ptr=$contextPtr")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Init erro: ${e.message}")
            onStatusUpdate?.invoke("❌ Erro JNI: ${e.message}")
            false
        }
    }

    private fun downloadModel(dest: File) {
        try {
            val url = URL(MODEL_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.connect()
            val total = conn.contentLength
            var downloaded = 0

            conn.inputStream.use { input ->
                FileOutputStream(dest).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        val pct = if (total > 0) downloaded * 100 / total else 0
                        onStatusUpdate?.invoke("⬇️ Baixando modelo... $pct%")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download erro: ${e.message}")
        }
    }

    fun startListening() {
        if (isRecording) return
        isRecording = true
        onListeningState?.invoke(true)

        scope.launch {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ) * 4

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                onStatusUpdate?.invoke("❌ Microfone falhou")
                isRecording = false
                return@launch
            }

            audioRecord?.startRecording()

            while (isRecording) {
                val pcmData = recordChunk(bufferSize)
                if (pcmData != null && hasVoice(pcmData)) {
                    onStatusUpdate?.invoke("🔄 Processando...")
                    // Converte PCM 16-bit para float [-1.0, 1.0]
                    val floatData = FloatArray(pcmData.size) { i ->
                        pcmData[i] / 32768.0f
                    }

                    val result = whisperLib?.transcribeData(contextPtr, floatData) ?: ""
                    Log.d(TAG, "Whisper resultado: $result")

                    if (result.contains("|") && !result.startsWith("error")) {
                        val parts = result.split("|", limit = 2)
                        val lang = parts[0].trim()
                        val text = parts[1].trim()
                        if (text.isNotBlank()) {
                            onTranscription?.invoke(text, lang)
                        }
                    }
                    onStatusUpdate?.invoke("🎙 Ouvindo (Whisper)...")
                }
            }
        }
    }

    private fun recordChunk(bufferSize: Int): ShortArray? {
        val buffer = ShortArray(bufferSize / 2)
        val recorded = mutableListOf<Short>()
        val startTime = System.currentTimeMillis()
        while (isRecording && System.currentTimeMillis() - startTime < CHUNK_DURATION_MS) {
            val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
            if (read > 0) recorded.addAll(buffer.take(read).toList())
        }
        return if (recorded.isNotEmpty()) recorded.toShortArray() else null
    }

    private fun hasVoice(audio: ShortArray): Boolean {
        var sum = 0.0
        for (s in audio) sum += s * s
        return Math.sqrt(sum / audio.size) > 200
    }

    fun stopListening() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        onListeningState?.invoke(false)
    }

    fun release() {
        stopListening()
        if (contextPtr != 0L) {
            whisperLib?.freeContext(contextPtr)
            contextPtr = 0L
        }
        scope.cancel()
    }
}
