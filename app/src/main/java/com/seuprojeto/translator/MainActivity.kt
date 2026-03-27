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

    // Cole sua chave aqui
    private val API_KEY = "SUA_CHAVE_AQUI"

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

        // Ouviu inglês → traduz para português → fala no canal direito
        speechManager.onSpeechLeft = { text ->
            runOnUiThread { findViewById<TextView>(R.id.tv_left).text = "EN: $text" }
            lifecycleScope.launch {
                val translated = translationManager.translate(text, "en", "pt")
                runOnUiThread { findViewById<TextView>(R.id.tv_right).text = "PT: $translated" }
                if (isAudioReady) audioManager.speakRight(translated)
            }
        }

        // Ouviu português → traduz para inglês → fala no canal esquerdo
        speechManager.onSpeechRight = { text ->
            runOnUiThread { findViewById<TextView>(R.id.tv_right).text = "PT: $text" }
            lifecycleScope.launch {
                val translated = translationManager.translate(text, "pt", "en")
                runOnUiThread { findViewById<TextView>(R.id.tv_left).text = "EN: $translated" }
                if (isAudioReady) audioManager.speakLeft(translated)
            }
        }

        findViewById<Button>(R.id.btn_listen).setOnClickListener {
            if (!isListening) {
                speechManager.startListeningLeft("en-US")
                speechManager.startListeningRight("pt-BR")
                isListening = true
                findViewById<Button>(R.id.btn_listen).text = "Parar"
            } else {
                speechManager.stopAll()
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
