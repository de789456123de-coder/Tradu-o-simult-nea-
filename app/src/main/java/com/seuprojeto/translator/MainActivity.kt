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

    private var leftLangCode = "pt"
    private var rightLangCode = "en"
    private var leftLangName = "Português"
    private var rightLangName = "Inglês"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Recebe idiomas da tela de configuração
        leftLangCode = intent.getStringExtra("LEFT_LANG_CODE") ?: "pt"
        rightLangCode = intent.getStringExtra("RIGHT_LANG_CODE") ?: "en"
        leftLangName = intent.getStringExtra("LEFT_LANG_NAME") ?: "Português"
        rightLangName = intent.getStringExtra("RIGHT_LANG_NAME") ?: "Inglês"

        // Atualiza labels
        findViewById<TextView>(R.id.tv_left).text = "$leftLangName: aguardando..."
        findViewById<TextView>(R.id.tv_right).text = "$rightLangName: aguardando..."

        requestMicPermission()
        translationManager = TranslationManager(API_KEY)

        audioManager = AudioChannelManager(this)
        audioManager.init(
            localeLeft = Locale(leftLangCode),
            localeRight = Locale(rightLangCode),
            onReady = {
                runOnUiThread {
                    isAudioReady = true
                    Toast.makeText(this, "Pronto!", Toast.LENGTH_SHORT).show()
                }
            }
        )

        speechManager = SpeechManager(this)
        speechManager.init()

        speechManager.onSpeechDetected = { text, _ ->
            lifecycleScope.launch {
                val detectedLang = translationManager.detectLanguage(text)

                if (detectedLang == leftLangCode || isClosestTo(detectedLang, leftLangCode)) {
                    // Fala no canal esquerdo → traduz para direito
                    runOnUiThread { findViewById<TextView>(R.id.tv_left).text = "$leftLangName: $text" }
                    val translated = translationManager.translate(text, leftLangCode, rightLangCode)
                    runOnUiThread { findViewById<TextView>(R.id.tv_right).text = "$rightLangName: $translated" }
                    if (isAudioReady) audioManager.speakRight(translated)
                } else {
                    // Fala no canal direito → traduz para esquerdo
                    runOnUiThread { findViewById<TextView>(R.id.tv_right).text = "$rightLangName: $text" }
                    val translated = translationManager.translate(text, rightLangCode, leftLangCode)
                    runOnUiThread { findViewById<TextView>(R.id.tv_left).text = "$leftLangName: $translated" }
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
            if (isAudioReady) audioManager.speakLeft("Teste do canal esquerdo.")
        }

        findViewById<Button>(R.id.btn_test_right).setOnClickListener {
            if (isAudioReady) audioManager.speakRight("Right channel test.")
        }
    }

    // Verifica se o idioma detectado é variante do idioma configurado
    // Ex: "pt-BR" bate com "pt"
    private fun isClosestTo(detected: String, configured: String): Boolean {
        return detected.startsWith(configured) || configured.startsWith(detected)
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
