package com.seuprojeto.translator

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import kotlin.math.sqrt

class AudioCaptureManager(private val whisperLib: WhisperLib, private val contextPtr: Long) {

    private var audioRecord: AudioRecord? = null
    private var isRecording = false

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2

    @SuppressLint("MissingPermission")
    fun startRecordingAndTranscribing(
        onVolumeUpdate: (Int) -> Unit,
        onTranscriptionResult: (String) -> Unit
    ) {
        // Fonte UNPROCESSED entrega sua voz limpa pro Whisper sem corromper
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.UNPROCESSED,
            sampleRate, channelConfig, audioFormat, bufferSize
        )

        AppLogger.log("Microfone Ligado - Iniciando loop de captura"); audioRecord?.startRecording()
        isRecording = true

        CoroutineScope(Dispatchers.IO).launch {
            val chunkSamples = 1600
            val shortBuffer = ShortArray(chunkSamples)

            var AppLogger.log("Silêncio detectado. Fechando pacote de áudio."); isSpeaking = false
            var silenceMs = 0
            val MAX_SILENCE_MS = 800
            val MIN_VOICE_ENERGY = 1000.0

            val speechBuffer = mutableListOf<Float>()
            var loopCount = 0

            while (isRecording) {
                val readSize = audioRecord?.read(shortBuffer, 0, chunkSamples) ?: 0
                if (readSize > 0) {
                    var energy = 0.0
                    for (i in 0 until readSize) {
                        energy += (shortBuffer[i].toInt() * shortBuffer[i].toInt()).toDouble()
                    }
                    energy = sqrt(energy / readSize)

                    loopCount++
                    if (loopCount % 3 == 0) {
                        withContext(Dispatchers.Main) {
                            onVolumeUpdate(energy.toInt())
                        }
                    }

                    if (energy > MIN_VOICE_ENERGY) {
                        isSpeaking = true
                        silenceMs = 0
                    } else {
                        if (isSpeaking) silenceMs += 100
                    }

                    if (isSpeaking) {
                        for (i in 0 until readSize) {
                            speechBuffer.add(shortBuffer[i] / 32768.0f)
                        }
                    }

                    if (isSpeaking && silenceMs >= MAX_SILENCE_MS) {
                        AppLogger.log("Silêncio detectado. Fechando pacote de áudio."); isSpeaking = false 
                        
                        if (speechBuffer.size > 8000) { 
                            val floatArrayToSend = speechBuffer.toFloatArray()
                            
                            // ISOLAMENTO: O Whisper processa aqui e o microfone continua lendo lá em cima!
                            launch {
                                val startTime = System.currentTimeMillis()
                                AppLogger.log("Enviando ${floatArrayToSend.size} samples para o C++ (Whisper)"); val result = whisperLib.transcribeData(contextPtr, floatArrayToSend)
                                val elapsed = System.currentTimeMillis() - startTime; AppLogger.log("C++ Retornou em ${elapsed}ms: $result")

                                val parts = result.split("|")
                                if (parts.size >= 2) {
                                    val text = parts[1].trim()
                                    val lower = text.lowercase()
                                    if (text.isNotBlank() &&
                                        !lower.contains("subtitles by") &&
                                        !lower.contains("amara.org") &&
                                        !lower.contains("www.") &&
                                        !lower.contains("[blank_audio]") &&
                                        !lower.contains("слушаю") &&
                                        !lower.contains("thank you for")) {
                                        withContext(Dispatchers.Main) {
                                            onTranscriptionResult("$result [${elapsed}ms]")
                                        }
                                    }
                                }
                            }
                        }
                        speechBuffer.clear()
                        silenceMs = 0
                    }
                }
            }
        }
    }

    fun stopRecording() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }
}
