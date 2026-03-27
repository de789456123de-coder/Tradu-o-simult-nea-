package com.seuprojeto.translator

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestMicPermission()

        translationManager = TranslationManager(API_KEY)

        audioManager = AudioChannelManager(this)
        audioManager.init(
            localeLeft = Locale.ENGLISH,
            localeRight = Locale("pt", "BR"),
            onReady = {
                runOnUiThread {
                    isAudioReady = true
                    Toast.makeText(this, "Pronto!", Toast.LENGTH_SHORT).show()
                }
            }
        )

        speechManager = SpeechManager(this)
        speechManager.init()

        // Um único callback — decide o canal pelo idioma detectado
        speechManager.onSpeechDetected = { text, language ->
            lifecycleScope.launch {
                if (language == "en") {
                    // Inglês detectado → traduz para PT → fala no canal DIREITO
                    runOnUiThread { findViewById<TextView>(R.id.tv_left).text = "EN: $text" }
                    val translated = translationManager.translate(text, "en", "pt")
                    runOnUiThread { findViewById<TextView>(R.id.tv_right).text = "PT: $translated" }
                    if (isAudioReady) audioManager.speakRight(translated)
                } else {
                    // Português detectado → traduz para EN → fala no canal ESQUERDO
                    runOnUiThread { findViewById<TextView>(R.id.tv_right).text = "PT: $text" }
                    val translated = translationManager.translate(text, "pt", "en")
                    runOnUiThread { findViewById<TextView>(R.id.tv_left).text = "EN: $translated" }
                    if (isAudioReady) audioManager.speakLeft(translated)
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
