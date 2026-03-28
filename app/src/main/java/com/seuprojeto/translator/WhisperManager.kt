package com.seuprojeto.translator

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WhisperManager(private val context: Context) {

    companion object {
        private const val TAG = "WhisperManager"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val CHUNK_DURATION_MS = 4000L // 4 segundos por chunk
    }

    private var isRecording = false
    private var audioRecord: AudioRecord? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var whisperBin: String
    private lateinit var modelPath: String
    private lateinit var libDir: String

    var onTranscription: ((text: String, language: String) -> Unit)? = null
    var onListeningState: ((active: Boolean) -> Unit)? = null
    var onPartialResult: ((text: String) -> Unit)? = null

    fun init(): Boolean {
        return try {
            // Extrai binários dos assets para o diretório do app
            val filesDir = context.filesDir
            val whisperDir = File(filesDir, "whisper")
            whisperDir.mkdirs()

            val assets = listOf(
                "whisper/whisper-cli",
                "whisper/libwhisper.so",
                "whisper/libggml.so",
                "whisper/libggml-base.so",
                "whisper/libggml-cpu.so",
                "whisper/ggml-tiny.bin"
            )

            assets.forEach { asset ->
                val outFile = File(filesDir, asset)
                if (!outFile.exists()) {
                    outFile.parentFile?.mkdirs()
                    context.assets.open(asset).use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d(TAG, "Extraído: $asset")
                }
            }

            whisperBin = File(filesDir, "whisper/whisper-cli").absolutePath
            modelPath  = File(filesDir, "whisper/ggml-tiny.bin").absolutePath
            libDir     = File(filesDir, "whisper").absolutePath

            // Dá permissão de execução
            Runtime.getRuntime().exec("chmod +x $whisperBin").waitFor()
            Log.d(TAG, "WhisperManager inicializado!")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao inicializar: ${e.message}")
            false
        }
    }

    fun startListening() {
        if (isRecording) return
        isRecording = true
        onListeningState?.invoke(true)

        scope.launch {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
            ) * 4

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord não inicializou")
                isRecording = false
                return@launch
            }

            audioRecord?.startRecording()
            Log.d(TAG, "Gravando...")

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
        val samplesNeeded = (SAMPLE_RATE * CHUNK_DURATION_MS / 1000).toInt()
        val buffer = ShortArray(bufferSize / 2)
        val recorded = mutableListOf<Short>()
        val startTime = System.currentTimeMillis()

        while (isRecording &&
               System.currentTimeMillis() - startTime < CHUNK_DURATION_MS) {
            val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
            if (read > 0) recorded.addAll(buffer.take(read).toList())
        }

        return if (recorded.isNotEmpty()) recorded.toShortArray() else null
    }

    // VAD simples — detecta se há voz pelo volume RMS
    private fun hasVoiceActivity(audio: ShortArray): Boolean {
        var sum = 0.0
        for (sample in audio) sum += sample * sample
        val rms = Math.sqrt(sum / audio.size)
        Log.d(TAG, "RMS: $rms")
        return rms > 300 // threshold de silêncio
    }

    private fun saveWav(pcmData: ShortArray): File {
        val wavFile = File(context.cacheDir, "whisper_input_${System.currentTimeMillis()}.wav")
        val totalDataLen = pcmData.size * 2 + 36
        val byteRate = SAMPLE_RATE * 2

        FileOutputStream(wavFile).use { out ->
            // WAV header
            val header = ByteArray(44)
            val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            buf.put("RIFF".toByteArray())
            buf.putInt(totalDataLen)
            buf.put("WAVE".toByteArray())
            buf.put("fmt ".toByteArray())
            buf.putInt(16)
            buf.putShort(1)
            buf.putShort(1)
            buf.putInt(SAMPLE_RATE)
            buf.putInt(byteRate)
            buf.putShort(2)
            buf.putShort(16)
            buf.put("data".toByteArray())
            buf.putInt(pcmData.size * 2)
            out.write(header)

            // PCM data
            val pcmBytes = ByteArray(pcmData.size * 2)
            val pcmBuf = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
            for (s in pcmData) pcmBuf.putShort(s)
            out.write(pcmBytes)
        }
        return wavFile
    }

    data class WhisperResult(val text: String, val language: String)

    private fun transcribe(wavFile: File): WhisperResult? {
        return try {
            val process = ProcessBuilder(
                whisperBin,
                "-m", modelPath,
                "-f", wavFile.absolutePath,
                "-l", "auto",
                "--no-timestamps",
                "-t", "4"
            )
            .directory(File(libDir))
            .redirectErrorStream(true)
            .apply {
                environment()["LD_LIBRARY_PATH"] = libDir
            }
            .start()

            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()

            Log.d(TAG, "Whisper output: $output")

            // Extrai idioma detectado
            val langRegex = Regex("auto-detected language: (\\w+)")
            val langMatch = langRegex.find(output)
            val detectedLang = langMatch?.groupValues?.get(1) ?: "pt"

            // Extrai texto transcrito (última linha não vazia)
            val lines = output.lines()
                .filter { it.isNotBlank() }
                .filterNot { it.startsWith("whisper_") }
                .filterNot { it.startsWith("main:") }
                .filterNot { it.startsWith("system_info") }
                .filterNot { it.startsWith("[") }

            val text = lines.lastOrNull()?.trim() ?: ""

            if (text.isNotBlank()) WhisperResult(text, detectedLang) else null
        } catch (e: Exception) {
            Log.e(TAG, "Erro no Whisper: ${e.message}")
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
