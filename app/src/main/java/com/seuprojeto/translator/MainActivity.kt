package com.seuprojeto.translator

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
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

    private val API_KEY = "AIzaSyAmTZS9c0xiaJZMe62s_AgsONhOsyboMFI"

    private var leftLangCode = "pt"
    private var rightLangCode = "en"
    private var leftLangName = "Português"
    private var rightLangName = "Inglês"
    private var lastDetectedLang = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        leftLangCode  = intent.getStringExtra("LEFT_LANG_CODE")  ?: "pt"
        rightLangCode = intent.getStringExtra("RIGHT_LANG_CODE") ?: "en"
        leftLangName  = intent.getStringExtra("LEFT_LANG_NAME")  ?: "Português"
        rightLangName = intent.getStringExtra("RIGHT_LANG_NAME") ?: "Inglês"

        findViewById<TextView>(R.id.tv_left_label).text  = "🎧 $leftLangName"
        findViewById<TextView>(R.id.tv_right_label).text = "🎧 $rightLangName"

        requestMicPermission()
        translationManager = TranslationManager(API_KEY)

        audioManager = AudioChannelManager(this)
        audioManager.init(
            leftLang = leftLangCode,
            rightLang = rightLangCode,
            onReady = {
                runOnUiThread {
                    isAudioReady = true
                    setStatus("✅ Pronto!")
                }
            }
        )

        speechManager = SpeechManager(this)
        speechManager.init()

        speechManager.onSpeechDetected = { text, _ ->
            lifecycleScope.launch {
                setStatus("🔄 Detectando...")
                val detectedLang = translationManager.detectLanguageSmart(
                    text, leftLangCode, rightLangCode, lastDetectedLang
                )
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
                setStatus("● Ouvindo...")
            }
        }

        findViewById<Button>(R.id.btn_listen).setOnClickListener {
            if (!isListening) {
                speechManager.startListening()
                isListening = true
                findViewById<Button>(R.id.btn_listen).text = "Parar"
                setStatus("● Ouvindo...")
            } else {
                speechManager.stopListening()
                isListening = false
                findViewById<Button>(R.id.btn_listen).text = "Iniciar Conversa"
                setStatus("● Pausado")
            }
        }
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
    }
}
