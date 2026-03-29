package com.seuprojeto.translator

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var audioManager: AudioChannelManager
    private lateinit var whisperManager: WhisperManager
    private lateinit var translationManager: TranslationManager
    private var isAudioReady = false
    private var isListening = false
    private var modelsReady = false

    private val API_KEY = "AIzaSyAmTZS9c0xiaJZMe62s_AgsONhOsyboMFI"
    private var leftLangCode = "pt"; private var rightLangCode = "en"
    private var currentContext = ContextManager.ConversationContext.GENERAL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        AppLogger.init(this) // Inicia o Raio-X Turbinado
        AppLogger.log("[MainActivity] onCreate() disparado.")

        leftLangCode = intent.getStringExtra("LEFT_LANG_CODE") ?: "pt"
        rightLangCode = intent.getStringExtra("RIGHT_LANG_CODE") ?: "en"

        requestMicPermission()
        
        translationManager = TranslationManager(API_KEY)
        audioManager = AudioChannelManager(this)
        audioManager.init(leftLangCode, rightLangCode) { 
            runOnUiThread { isAudioReady = true; AppLogger.log("[MainActivity] AudioChannelManager (TTS) pronto.") } 
        }

        whisperManager = WhisperManager(this)

        lifecycleScope.launch {
            AppLogger.log("[MainActivity] Iniciando download/prep de modelos offline...")
            modelsReady = translationManager.prepareOfflineModel(leftLangCode, rightLangCode)
            AppLogger.log("[MainActivity] Inicializando WhisperManager...")
            val whisperOk = whisperManager.init()
            setStatus(if (whisperOk) "✅ Pronto" else "❌ Erro Whisper")
        }

        whisperManager.onStatusUpdate = { msg -> setStatus(msg) }

        whisperManager.onTranscription = { text, detectedLang ->
            AppLogger.log("[MainActivity] Recebeu onTranscription. Lang: $detectedLang | Texto: '$text'")
            lifecycleScope.launch {
                setStatus("🔄 Traduzindo...")

                val isLeft = detectedLang == leftLangCode
                val sourceCode = if (isLeft) leftLangCode else rightLangCode
                val targetCode = if (isLeft) rightLangCode else leftLangCode
                
                runOnUiThread { animateText(findViewById(if (isLeft) R.id.tv_left else R.id.tv_right), text) }
                
                AppLogger.log("[MainActivity] Chamando TranslationManager API ($sourceCode -> $targetCode)...")
                val translated = translationManager.translate(text, sourceCode, targetCode, currentContext)
                AppLogger.log("[MainActivity] Tradução concluída: '$translated'")
                
                runOnUiThread { animateText(findViewById(if (isLeft) R.id.tv_right else R.id.tv_left), translated) }
                
                if (isAudioReady) {
                    AppLogger.log("[MainActivity] Pausando microfone para o celular falar a tradução.")
                    whisperManager.stopListening()
                    
                    if (isLeft) audioManager.speakRight(translated) else audioManager.speakLeft(translated)
                    
                    val tempoFalaMs = (translated.length * 85L) + 1200L
                    AppLogger.log("[MainActivity] Aplicando delay calculado de ${tempoFalaMs}ms...")
                    delay(tempoFalaMs)
                    
                    AppLogger.log("[MainActivity] Delay concluído. Religando microfone.")
                    whisperManager.startListening()
                } else {
                    AppLogger.log("[MainActivity] Aviso: TTS não estava pronto.")
                }
                setStatus("● Ouvindo (Whisper)")
            }
        }

        findViewById<Button>(R.id.btn_listen).setOnClickListener {
            AppLogger.log("[MainActivity] Botão Ouvir Clicado. Estado atual isListening=$isListening")
            if (!isListening) {
                whisperManager.startListening()
                isListening = true
                findViewById<Button>(R.id.btn_listen).text = "⏹ Parar"
                setStatus("🎙 Ouvindo...")
            } else {
                whisperManager.stopListening()
                isListening = false
                findViewById<Button>(R.id.btn_listen).text = "▶ Iniciar"
                setStatus("● Pausado")
            }
        }
    }

    private fun requestMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }

    private fun setStatus(msg: String) { runOnUiThread { findViewById<TextView>(R.id.tv_status).text = msg } }
    private fun animateText(view: TextView, text: String) { runOnUiThread { view.text = text } }
}
