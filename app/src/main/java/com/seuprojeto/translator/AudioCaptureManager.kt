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

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2

    @SuppressLint("MissingPermission")
    fun startRecordingAndTranscribing(onVolumeUpdate: (Int) -> Unit, onTranscriptionResult: (String) -> Unit) {
        AppLogger.log("[AudioCapture] Preparando AudioRecord (UNPROCESSED)...")
        audioRecord = AudioRecord(MediaRecorder.AudioSource.UNPROCESSED, sampleRate, channelConfig, audioFormat, bufferSize)

        AppLogger.log("[AudioCapture] startRecording() disparado.")
        audioRecord?.startRecording()
        isRecording = true

        CoroutineScope(Dispatchers.IO).launch {
            val chunkSamples = 1600
            val shortBuffer = ShortArray(chunkSamples)
            var isSpeaking = false
            var silenceMs = 0
            val MAX_SILENCE_MS = 800
            val MIN_VOICE_ENERGY = 1000.0

            val speechBuffer = mutableListOf<Float>()
            val preRollBuffer = ArrayDeque<FloatArray>()
            var loopCount = 0

            AppLogger.log("[AudioCapture] Loop infinito do VAD iniciado.")
            while (isRecording) {
                val readSize = audioRecord?.read(shortBuffer, 0, chunkSamples) ?: 0
                if (readSize > 0) {
                    var energy = 0.0
                    for (i in 0 until readSize) energy += (shortBuffer[i].toInt() * shortBuffer[i].toInt()).toDouble()
                    energy = sqrt(energy / readSize)

                    loopCount++
                    if (loopCount % 3 == 0) {
                        withContext(Dispatchers.Main) { onVolumeUpdate(energy.toInt()) }
                    }

                    val floatChunk = FloatArray(readSize)
                    for (i in 0 until readSize) floatChunk[i] = shortBuffer[i] / 32768.0f

                    if (energy > MIN_VOICE_ENERGY) {
                        if (!isSpeaking) {
                            AppLogger.log("[AudioCapture] Início de fala detectado! (Energia: ${energy.toInt()})")
                            isSpeaking = true
                            preRollBuffer.forEach { speechBuffer.addAll(it.toList()) }
                        }
                        silenceMs = 0
                    } else {
                        if (isSpeaking) {
                            silenceMs += 100
                        } else {
                            preRollBuffer.addLast(floatChunk)
                            if (preRollBuffer.size > 3) preRollBuffer.removeFirst()
                        }
                    }

                    if (isSpeaking) speechBuffer.addAll(floatChunk.toList())

                    val reachedSilence = isSpeaking && silenceMs >= MAX_SILENCE_MS
                    val reachedMaxTime = isSpeaking && speechBuffer.size >= 80000

                    if (reachedSilence || reachedMaxTime) {
                        val motivo = if (reachedSilence) "Silêncio de ${MAX_SILENCE_MS}ms" else "Limite de 5s atingido"
                        AppLogger.log("[AudioCapture] Guilhotina ativada ($motivo). Fechando pacote...")
                        isSpeaking = false 
                        
                        if (speechBuffer.size > 8000) { 
                            val floatArrayToSend = speechBuffer.toFloatArray()
                            AppLogger.log("[AudioCapture] Tamanho do áudio capturado: ${floatArrayToSend.size} amostras.")
                            
                            launch {
                                AppLogger.log("[AudioCapture] Coroutine criada. Aguardando Mutex do C++...")
                                whisperMutex.withLock {
                                    AppLogger.log("[AudioCapture] Mutex obtido! Injetando ${floatArrayToSend.size} no C++.")
                                    val startTime = System.currentTimeMillis()
                                    val result = whisperLib.transcribeData(contextPtr, floatArrayToSend)
                                    val elapsed = System.currentTimeMillis() - startTime
                                    AppLogger.log("[AudioCapture] C++ devolveu em ${elapsed}ms: $result")

                                    val parts = result.split("|")
                                    if (parts.size >= 2) {
                                        val text = parts[1].trim().lowercase()
                                        if (text.isNotBlank() && !text.contains("subtitles by") && !text.contains("[blank_audio]")) {
                                            withContext(Dispatchers.Main) { onTranscriptionResult("$result [${elapsed}ms]") }
                                        } else {
                                            AppLogger.log("[AudioCapture] Alucinação bloqueada pelo filtro: $text")
                                        }
                                    }
                                }
                            }
                        } else {
                            AppLogger.log("[AudioCapture] Áudio muito curto descartado (${speechBuffer.size} amostras).")
                        }
                        speechBuffer.clear()
                        preRollBuffer.clear()
                        silenceMs = 0
                    }
                }
            }
            AppLogger.log("[AudioCapture] Loop do VAD encerrado.")
        }
    }

    fun stopRecording() {
        AppLogger.log("[AudioCapture] stopRecording() chamado. Encerrando hardware de áudio.")
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }
}
