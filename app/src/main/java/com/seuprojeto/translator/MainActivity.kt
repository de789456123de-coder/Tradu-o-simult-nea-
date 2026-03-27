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
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var audioManager: AudioChannelManager
    private lateinit var speechManager: SpeechManager
    private var isAudioReady = false
    private var isListening = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestMicPermission()

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

        speechManager.onSpeechLeft = { text ->
            runOnUiThread { findViewById<TextView>(R.id.tv_left).text = "EN: $text" }
            if (isAudioReady) audioManager.speakRight(text)
        }

        speechManager.onSpeechRight = { text ->
            runOnUiThread { findViewById<TextView>(R.id.tv_right).text = "PT: $text" }
            if (isAudioReady) audioManager.speakLeft(text)
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
