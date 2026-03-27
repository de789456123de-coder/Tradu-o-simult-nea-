package com.seuprojeto.translator

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var audioManager: AudioChannelManager
    private lateinit var speechManager: SpeechManager
    private lateinit var translationManager: TranslationManager
    private var isAudioReady = false
    private var isListening = false

    private val API_KEY = "AIzaSyAmTZS9c0xiaJZMe62s_AgsONhOsyboMFI"

    // Idioma "base" da conversa — o outro lado sempre recebe a tradução
    // Ex: userLang = "pt" → fala PT, ouve EN / fala EN, ouve PT
    private var userLang = "pt"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestMicPermission()

        translationManager = TranslationManager(API_KEY)

        audioManager = AudioChannelManager(this)
        audioManager.init(onReady = {
            runOnUiThread {
                isAudioReady = true
                Toast.makeText(this, "Pronto!", Toast.LENGTH_SHORT).show()
            }
        })

        speechManager = SpeechManager(this)
        speechManager.init()

        speechManager.onSpeechDetected = { text, _ ->
            lifecycleScope.launch {
                // Detecta idioma real via API Google
                val detectedLang = translationManager.detectLanguage(text)
                val targetLang = if (detectedLang == "pt") "en" else "pt"
                val targetLocale = if (targetLang == "en") Locale.ENGLISH else Locale("pt", "BR")

                runOnUiThread {
                    if (detectedLang == "pt") {
                        findViewById<TextView>(R.id.tv_right).text = "PT: $text"
                    } else {
                        findViewById<TextView>(R.id.tv_left).text = "$detectedLang: $text"
                    }
                }

                val translated = translationManager.translate(text, detectedLang, targetLang)

                runOnUiThread {
                    if (targetLang == "pt") {
                        findViewById<TextView>(R.id.tv_right).text = "PT: $translated"
                        audioManager.setLanguageRight(targetLocale)
                        if (isAudioReady) audioManager.speakRight(translated)
                    } else {
                        findViewById<TextView>(R.id.tv_left).text = "EN: $translated"
                        audioManager.setLanguageLeft(targetLocale)
                        if (isAudioReady) audioManager.speakLeft(translated)
                    }
                }
            }
        }

        findViewById<Button>(R.id.btn_listen).setOnClickListener {
            if (!isListening) {
                speechManager.startListening()
                isListening = true
                findViewById<Button>(R.id.btn_listen).text = "Parar"
            } else {
                speechManager.stopListening()
                isListening = false
                findViewById<Button>(R.id.btn_listen).text = "Iniciar Conversa"
            }
        }

        findViewById<Button>(R.id.btn_test_left).setOnClickListener {
            if (isAudioReady) audioManager.speakLeft("Hello! Left channel test.")
        }

        findViewById<Button>(R.id.btn_test_right).setOnClickListener {
            if (isAudioReady) audioManager.speakRight("Olá! Teste do canal direito.")
        }
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
