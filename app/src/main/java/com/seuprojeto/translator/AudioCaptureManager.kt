package com.seuprojeto.translator

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.sqrt

class AudioCaptureManager(private val whisperLib: WhisperLib, private val contextPtr: Long) {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val whisperMutex = Mutex()
    private var captureScope: CoroutineScope? = null

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2

    @SuppressLint("MissingPermission")
    fun startRecordingAndTranscribing(onVolumeUpdate: (Int) -> Unit, onTranscriptionResult: (String) -> Unit) {
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate, channelConfig, audioFormat, bufferSize
        )

        audioRecord?.startRecording()
        isRecording = true
        captureScope = CoroutineScope(Dispatchers.IO + Job())

        captureScope?.launch {
            val chunkSamples = 1600
            val shortBuffer = ShortArray(chunkSamples)
            var isSpeaking = false
            var silenceMs = 0
            val MAX_SILENCE_MS = 800
            val MIN_VOICE_ENERGY = 1000.0
            val speechBuffer = mutableListOf<Float>()
            val preRollBuffer = ArrayDeque<FloatArray>()
            var loopCount = 0

            AppLogger.log("[VAD] Loop iniciado.")

            while (isRecording && isActive) {
                val readSize = audioRecord?.read(shortBuffer, 0, chunkSamples) ?: 0
                if (readSize > 0) {
                    var energy = 0.0
                    for (i in 0 until readSize) energy += (shortBuffer[i].toInt() * shortBuffer[i].toInt()).toDouble()
                    energy = sqrt(energy / readSize)

                    loopCount++
                    if (loopCount % 3 == 0) {
                        withContext(Dispatchers.Main) { onVolumeUpdate(energy.toInt()) }
                    }

                    val floatChunk = FloatArray(readSize) { i -> shortBuffer[i] / 32768.0f }

                    if (energy > MIN_VOICE_ENERGY) {
                        if (!isSpeaking) {
                            AppLogger.log("[VAD] Fala detectada! Energia=${energy.toInt()}")
                            isSpeaking = true
                            preRollBuffer.forEach { speechBuffer.addAll(it.toList()) }
                        }
                        silenceMs = 0
                    } else {
                        if (isSpeaking) silenceMs += 100
                        else {
                            preRollBuffer.addLast(floatChunk)
                            if (preRollBuffer.size > 3) preRollBuffer.removeFirst()
                        }
                    }

                    if (isSpeaking) speechBuffer.addAll(floatChunk.toList())

                    val reachedSilence = isSpeaking && silenceMs >= MAX_SILENCE_MS
                    val reachedMaxTime = isSpeaking && speechBuffer.size >= 80000

                    if (reachedSilence || reachedMaxTime) {
                        isSpeaking = false
                        if (speechBuffer.size > 8000) {
                            val floatArrayToSend = speechBuffer.toFloatArray()
                            AppLogger.log("[VAD] Enviando ${floatArrayToSend.size} amostras para Whisper.")

                            captureScope?.launch {
                                whisperMutex.withLock {
                                    if (!isRecording || !isActive) {
                                        AppLogger.log("[VAD] Zumbi destruído!")
                                        return@withLock
                                    }
                                    val t = System.currentTimeMillis()
                                    val result = whisperLib.transcribeData(contextPtr, floatArrayToSend)
                                    val elapsed = System.currentTimeMillis() - t
                                    AppLogger.log("[Whisper] ${elapsed}ms → $result")

                                    val parts = result.split("|")
                                    if (parts.size >= 2) {
                                        val text = parts[1].trim()
                                        val lower = text.lowercase()
                                        if (text.isNotBlank() &&
                                            !lower.contains("subtitles by") &&
                                            !lower.contains("amara.org") &&
                                            !lower.contains("[blank_audio]") &&
                                            !lower.contains("thank you for watching")) {
                                            withContext(Dispatchers.Main) {
                                                onTranscriptionResult("$result [${elapsed}ms]")
                                            }
                                        } else {
                                            AppLogger.log("[Whisper] Alucinação bloqueada: $text")
                                        }
                                    }
                                }
                            }
                        }
                        speechBuffer.clear()
                        preRollBuffer.clear()
                        silenceMs = 0
                    }
                }
            }
            AppLogger.log("[VAD] Loop encerrado.")
        }
    }

    fun stopRecording() {
        AppLogger.log("[AudioCapture] Stop — destruindo zumbis.")
        isRecording = false
        captureScope?.cancel()
        captureScope = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }
}
