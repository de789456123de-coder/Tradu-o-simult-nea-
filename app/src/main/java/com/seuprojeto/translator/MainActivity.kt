package com.seuprojeto.translator

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var audioManager: AudioChannelManager
    private lateinit var speechManager: SpeechManager
    private lateinit var translationManager: TranslationManager
    private var isAudioReady = false
    private var isListening = false
    private var modelsReady = false

    private val API_KEY = "AIzaSyAmTZS9c0xiaJZMe62s_AgsONhOsyboMFI"

    private var leftLangCode = "pt"
    private var rightLangCode = "en"
    private var leftLangName = "Português"
    private var rightLangName = "Inglês"
    private var lastDetectedLang = ""

    // Histórico para melhorar detecção
    private val recentLangs = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        leftLangCode  = intent.getStringExtra("LEFT_LANG_CODE")  ?: "pt"
        rightLangCode = intent.getStringExtra("RIGHT_LANG_CODE") ?: "en"
        leftLangName  = intent.getStringExtra("LEFT_LANG_NAME")  ?: "Português"
        rightLangName = intent.getStringExtra("RIGHT_LANG_NAME") ?: "Inglês"

        updateLabels()
        requestMicPermission()

        translationManager = TranslationManager(API_KEY)
        audioManager = AudioChannelManager(this)
        audioManager.init(
            leftLang = leftLangCode,
            rightLang = rightLangCode,
            onReady = { runOnUiThread { isAudioReady = true } }
        )

        speechManager = SpeechManager(this)
        speechManager.init()

        // Baixa modelos offline
        lifecycleScope.launch {
            setStatus("⬇️ Preparando modo offline...")
            val ok1 = translationManager.prepareOfflineModel(leftLangCode, rightLangCode)
            val ok2 = translationManager.prepareOfflineModel(rightLangCode, leftLangCode)
            modelsReady = ok1 && ok2
            setStatus(if (modelsReady) "✅ Pronto — modo offline" else "✅ Pronto — modo online")
        }

        speechManager.onSpeechDetected = { text, _ ->
            lifecycleScope.launch {
                setStatus("🔄 Detectando...")

                // Detecção com histórico de contexto
                val detectedLang = translationManager.detectLanguageSmart(
                    text, leftLangCode, rightLangCode,
                    recentLangs.lastOrNull() ?: lastDetectedLang
                )

                // Atualiza histórico (máx 5)
                recentLangs.add(detectedLang)
                if (recentLangs.size > 5) recentLangs.removeAt(0)
                lastDetectedLang = detectedLang

                setStatus("🔄 Traduzindo...")

                if (detectedLang == leftLangCode) {
                    runOnUiThread { findViewById<TextView>(R.id.tv_left).text = text }
                    val translated = translationManager.translate(text, leftLangCode, rightLangCode)
                    runOnUiThread { findViewById<TextView>(R.id.tv_right).text = translated }
                    if (isAudioReady) audioManager.speakRight(translated)
                } else {
                    runOnUiThread { findViewById<TextView>(R.id.tv_right).text = text }
                    val translated = translationManager.translate(text, rightLangCode, leftLangCode)
                    runOnUiThread { findViewById<TextView>(R.id.tv_left).text = translated }
                    if (isAudioReady) audioManager.speakLeft(translated)
                }

                val mode = if (modelsReady) "offline" else "online"
                setStatus("● Ouvindo ($mode)")
            }
        }

        // Botão iniciar/parar
        findViewById<Button>(R.id.btn_listen).setOnClickListener {
            if (!isListening) {
                speechManager.startListening()
                isListening = true
                findViewById<Button>(R.id.btn_listen).text = "⏹ Parar"
                setStatus("● Ouvindo...")
            } else {
                speechManager.stopListening()
                isListening = false
                recentLangs.clear()
                lastDetectedLang = ""
                findViewById<Button>(R.id.btn_listen).text = "▶ Iniciar Conversa"
                setStatus("● Pausado")
            }
        }

        // Botão trocar idiomas
        findViewById<ImageButton>(R.id.btn_swap).setOnClickListener {
            val wasListening = isListening
            if (wasListening) {
                speechManager.stopListening()
                isListening = false
            }

            // Troca os idiomas
            val tmpCode = leftLangCode
            val tmpName = leftLangName
            leftLangCode  = rightLangCode
            leftLangName  = rightLangName
            rightLangCode = tmpCode
            rightLangName = tmpName

            recentLangs.clear()
            lastDetectedLang = ""

            updateLabels()
            audioManager.init(
                leftLang = leftLangCode,
                rightLang = rightLangCode,
                onReady = { runOnUiThread { isAudioReady = true } }
            )

            // Baixa modelos para o novo par
            lifecycleScope.launch {
                setStatus("⬇️ Preparando novo par...")
                val ok1 = translationManager.prepareOfflineModel(leftLangCode, rightLangCode)
                val ok2 = translationManager.prepareOfflineModel(rightLangCode, leftLangCode)
                modelsReady = ok1 && ok2
                setStatus("🔄 Idiomas trocados!")

                if (wasListening) {
                    speechManager.startListening()
                    isListening = true
                    setStatus("● Ouvindo...")
                }
            }
        }
    }

    private fun updateLabels() {
        findViewById<TextView>(R.id.tv_left_label).text  = "🎧 $leftLangName"
        findViewById<TextView>(R.id.tv_right_label).text = "🎧 $rightLangName"
    }

    private fun setStatus(msg: String) {
        runOnUiThread { findViewById<TextView>(R.id.tv_status).text = msg }
    }

    private fun requestMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioManager.release()
        speechManager.release()
        translationManager.release()
    }
}
