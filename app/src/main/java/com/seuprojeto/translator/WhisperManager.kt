package com.seuprojeto.translator

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream

class WhisperManager(private val context: Context) {

    companion object {
        private const val TAG = "WhisperManager"
        private const val SAMPLE_RATE = 16000
        // Apenas 1.5 segundos de áudio para detecção de idioma
        private const val DETECT_SAMPLES = 16000 * 2
    }

    private var whisperLib: WhisperLib? = null
    private var contextPtr: Long = 0
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var onStatusUpdate: ((msg: String) -> Unit)? = null

    fun init(): Boolean {
        return try {
            val modelFile = File(context.filesDir, "ggml-tiny.bin")
            if (!modelFile.exists()) {
                onStatusUpdate?.invoke("📦 Preparando modelo...")
                context.assets.open("ggml-tiny.bin").use { input ->
                    FileOutputStream(modelFile).use { output -> input.copyTo(output) }
                }
            }
            onStatusUpdate?.invoke("🔄 Carregando Whisper...")
            whisperLib = WhisperLib()
            contextPtr = whisperLib!!.initContext(modelFile.absolutePath)
            if (contextPtr == 0L) {
                onStatusUpdate?.invoke("❌ Falha Whisper")
                return false
            }
            onStatusUpdate?.invoke("✅ Whisper pronto!")
            true
        } catch (e: UnsatisfiedLinkError) {
            onStatusUpdate?.invoke("❌ JNI: ${e.message?.take(50)}")
            false
        } catch (e: Exception) {
            onStatusUpdate?.invoke("❌ Erro: ${e.message?.take(50)}")
            false
        }
    }

    // Detecta idioma gravando 2 segundos de áudio
    suspend fun detectLanguage(leftLang: String, rightLang: String): String {
        if (whisperLib == null || contextPtr == 0L) return leftLang

        return withContext(Dispatchers.IO) {
            try {
                val bufferSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2

                val audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, bufferSize
                )

                audioRecord.startRecording()
                val shortBuffer = ShortArray(DETECT_SAMPLES)
                var totalRead = 0
                while (totalRead < DETECT_SAMPLES) {
                    val read = audioRecord.read(shortBuffer, totalRead, DETECT_SAMPLES - totalRead)
                    if (read > 0) totalRead += read else break
                }
                audioRecord.stop()
                audioRecord.release()

                val floatBuffer = FloatArray(totalRead) { i -> shortBuffer[i] / 32768.0f }
                val result = whisperLib!!.transcribeData(contextPtr, floatBuffer)
                val detectedLang = result.split("|").firstOrNull()?.trim() ?: leftLang

                Log.d(TAG, "Idioma detectado: $detectedLang (resultado: $result)")

                // Força para o par configurado
                when {
                    detectedLang.startsWith(leftLang) -> leftLang
                    detectedLang.startsWith(rightLang) -> rightLang
                    else -> leftLang
                }
            } catch (e: Exception) {
                Log.e(TAG, "detectLanguage erro: ${e.message}")
                leftLang
            }
        }
    }

    fun isReady() = whisperLib != null && contextPtr != 0L

    fun release() {
        if (contextPtr != 0L) {
            whisperLib?.freeContext(contextPtr)
            contextPtr = 0L
        }
        scope.cancel()
    }
}
