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
            "https://github.com/de789456123de-coder/Tradu-o-simult-nea-/releases/download/v1.0-whisper/whisper-arm64.tar.gz"
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
            val whisperDir = File(context.filesDir, "whisper2")
            val modelFile = File(whisperDir, "models/ggml-tiny.bin")

            if (!modelFile.exists()) {
                onStatusUpdate?.invoke("⬇️ Baixando Whisper (67MB)...")
                whisperDir.mkdirs()
                downloadAndExtract(whisperDir)
            }

            if (!modelFile.exists()) {
                onStatusUpdate?.invoke("❌ Modelo não encontrado após download")
                return false
            }

            onStatusUpdate?.invoke("🔄 Carregando Whisper JNI...")
            whisperLib = WhisperLib()
            contextPtr = whisperLib!!.initContext(modelFile.absolutePath)

            if (contextPtr == 0L) {
                onStatusUpdate?.invoke("❌ Falha ao carregar modelo")
                return false
            }

            onStatusUpdate?.invoke("✅ Whisper JNI pronto!")
            Log.d(TAG, "Whisper OK! ptr=$contextPtr model=${modelFile.absolutePath}")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "JNI erro: ${e.message}")
            onStatusUpdate?.invoke("❌ JNI erro: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Init erro: ${e.message}")
            onStatusUpdate?.invoke("❌ Erro: ${e.message}")
            false
        }
    }

    private fun downloadAndExtract(destDir: File) {
        try {
            val tarFile = File(context.cacheDir, "whisper.tar.gz")
            onStatusUpdate?.invoke("⬇️ Conectando...")

            // Segue redirects manualmente
            var downloadUrl = MODEL_URL
            var conn: HttpURLConnection
            var redirectCount = 0
            while (true) {
                conn = URL(downloadUrl).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 30000
                conn.readTimeout = 60000
                conn.connect()
                val code = conn.responseCode
                if (code in 300..399) {
                    downloadUrl = conn.getHeaderField("Location") ?: break
                    conn.disconnect()
                    if (++redirectCount > 5) break
                } else break
            }

            val total = conn.contentLength
            Log.d(TAG, "Download: $downloadUrl size=$total")
            onStatusUpdate?.invoke("⬇️ Baixando... 0%")

            var downloaded = 0
            conn.inputStream.use { input ->
                FileOutputStream(tarFile).use { output ->
                    val buffer = ByteArray(32768)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        val pct = if (total > 0) downloaded * 100 / total else 0
                        if (pct % 10 == 0) onStatusUpdate?.invoke("⬇️ Baixando... $pct%")
                    }
                }
            }

            onStatusUpdate?.invoke("📦 Extraindo...")
            Log.d(TAG, "Extraindo ${tarFile.length()} bytes para ${destDir.absolutePath}")

            val proc = ProcessBuilder("tar", "-xzf", tarFile.absolutePath, "-C", destDir.absolutePath)
                .redirectErrorStream(true)
                .start()
            val out = proc.inputStream.bufferedReader().readText()
            val exit = proc.waitFor()
            Log.d(TAG, "Extração exit=$exit out=$out")

            tarFile.delete()

            // Lista o que foi extraído
            destDir.walkTopDown().forEach { Log.d(TAG, "Extraído: ${it.absolutePath}") }

        } catch (e: Exception) {
            Log.e(TAG, "Download/extract erro: ${e.message}")
            onStatusUpdate?.invoke("❌ Download falhou: ${e.message}")
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
                    val floatData = FloatArray(pcmData.size) { i -> pcmData[i] / 32768.0f }
                    val result = whisperLib?.transcribeData(contextPtr, floatData) ?: ""
                    Log.d(TAG, "Resultado: $result")

                    if (result.contains("|") && !result.startsWith("error")) {
                        val parts = result.split("|", limit = 2)
                        val lang = parts[0].trim()
                        val text = parts[1].trim()
                        if (text.isNotBlank()) onTranscription?.invoke(text, lang)
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
