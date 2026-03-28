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
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WhisperManager(private val context: Context) {

    companion object {
        private const val TAG = "WhisperManager"
        private const val SAMPLE_RATE = 16000
        private const val CHUNK_DURATION_MS = 4000L
        private const val DOWNLOAD_URL =
            "https://github.com/de789456123de-coder/Tradu-o-simult-nea-/releases/download/v1.0-whisper/whisper-arm64.tar.gz"
    }

    private var isRecording = false
    private var audioRecord: AudioRecord? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var whisperBin: String
    private lateinit var modelPath: String
    private lateinit var libDir: String

    var onTranscription: ((text: String, language: String) -> Unit)? = null
    var onListeningState: ((active: Boolean) -> Unit)? = null
    var onDownloadProgress: ((progress: String) -> Unit)? = null

    fun init(): Boolean {
        return try {
            val filesDir = context.filesDir
            val whisperDir = File(filesDir, "whisper")
            whisperDir.mkdirs()

            whisperBin = File(whisperDir, "build-android/bin/whisper-cli").absolutePath
            modelPath  = File(whisperDir, "models/ggml-tiny.bin").absolutePath
            libDir     = File(whisperDir, "build-android/src").absolutePath

            // Verifica se já foi baixado
            if (!File(whisperBin).exists()) {
                Log.d(TAG, "Binários não encontrados, baixando...")
                onDownloadProgress?.invoke("⬇️ Baixando Whisper (67MB)...")
                downloadAndExtract(whisperDir)
            }

            // Dá permissão de execução
            Runtime.getRuntime().exec(arrayOf("chmod", "+x", whisperBin)).waitFor()
            Log.d(TAG, "WhisperManager pronto! bin=$whisperBin")
            File(whisperBin).exists()
        } catch (e: Exception) {
            Log.e(TAG, "Erro init: ${e.message}")
            false
        }
    }

    private fun downloadAndExtract(destDir: File) {
        try {
            // Download
            val tarFile = File(context.cacheDir, "whisper-arm64.tar.gz")
            val url = URL(DOWNLOAD_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.connect()
            val total = conn.contentLength
            var downloaded = 0

            conn.inputStream.use { input ->
                FileOutputStream(tarFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        val pct = if (total > 0) (downloaded * 100 / total) else 0
                        onDownloadProgress?.invoke("⬇️ Baixando Whisper... $pct%")
                    }
                }
            }

            onDownloadProgress?.invoke("📦 Extraindo...")

            // Extrai o tar.gz
            val process = ProcessBuilder("tar", "-xzf", tarFile.absolutePath, "-C", destDir.absolutePath)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val exit = process.waitFor()
            Log.d(TAG, "Extração: exit=$exit output=$output")

            tarFile.delete()
            onDownloadProgress?.invoke("✅ Whisper pronto!")
        } catch (e: Exception) {
            Log.e(TAG, "Erro download: ${e.message}")
            onDownloadProgress?.invoke("❌ Erro ao baixar Whisper")
        }
    }

    fun startListening() {
        if (isRecording) return
        isRecording = true
        onListeningState?.invoke(true)

        scope.launch {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            ) * 4

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord falhou")
                isRecording = false
                return@launch
            }

            audioRecord?.startRecording()

            while (isRecording) {
                val audioData = recordChunk(bufferSize)
                if (audioData != null && hasVoiceActivity(audioData)) {
                    val wavFile = saveWav(audioData)
                    val result = transcribe(wavFile)
                    wavFile.delete()
                    if (result != null && result.text.isNotBlank()) {
                        onTranscription?.invoke(result.text, result.language)
                    }
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

    private fun hasVoiceActivity(audio: ShortArray): Boolean {
        var sum = 0.0
        for (sample in audio) sum += sample * sample
        val rms = Math.sqrt(sum / audio.size)
        return rms > 200
    }

    private fun saveWav(pcmData: ShortArray): File {
        val wavFile = File(context.cacheDir, "w_${System.currentTimeMillis()}.wav")
        FileOutputStream(wavFile).use { out ->
            val header = ByteArray(44)
            val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            buf.put("RIFF".toByteArray())
            buf.putInt(pcmData.size * 2 + 36)
            buf.put("WAVE".toByteArray())
            buf.put("fmt ".toByteArray())
            buf.putInt(16); buf.putShort(1); buf.putShort(1)
            buf.putInt(SAMPLE_RATE); buf.putInt(SAMPLE_RATE * 2)
            buf.putShort(2); buf.putShort(16)
            buf.put("data".toByteArray())
            buf.putInt(pcmData.size * 2)
            out.write(header)
            val pcmBytes = ByteArray(pcmData.size * 2)
            ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).let { b ->
                for (s in pcmData) b.putShort(s)
            }
            out.write(pcmBytes)
        }
        return wavFile
    }

    data class WhisperResult(val text: String, val language: String)

    private fun transcribe(wavFile: File): WhisperResult? {
        return try {
            val env = mutableMapOf<String, String>()
            env["LD_LIBRARY_PATH"] = "$libDir:${File(context.filesDir, "whisper/build-android/ggml/src").absolutePath}"

            val process = ProcessBuilder(
                whisperBin, "-m", modelPath, "-f", wavFile.absolutePath,
                "-l", "auto", "--no-timestamps", "-t", "4"
            )
            .directory(File(libDir))
            .redirectErrorStream(true)
            .apply { environment().putAll(env) }
            .start()

            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            Log.d(TAG, "Whisper: $output")

            val lang = Regex("auto-detected language: (\\w+)").find(output)
                ?.groupValues?.get(1) ?: "pt"

            val text = output.lines()
                .filter { it.isNotBlank() }
                .filterNot { it.startsWith("whisper_") || it.startsWith("main:") ||
                             it.startsWith("system_info") || it.startsWith("[") ||
                             it.startsWith("ggml_") }
                .lastOrNull()?.trim() ?: ""

            if (text.isNotBlank()) WhisperResult(text, lang) else null
        } catch (e: Exception) {
            Log.e(TAG, "Transcribe erro: ${e.message}")
            null
        }
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
        scope.cancel()
    }
}
