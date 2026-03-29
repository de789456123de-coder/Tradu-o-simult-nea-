package com.seuprojeto.translator

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var audioManager: AudioChannelManager
    private lateinit var speechManager: SpeechManager
    private lateinit var translationManager: TranslationManager
    private lateinit var geminiManager: GeminiManager

    private var isAudioReady = false
    private var modelsReady = false
    private var isContinuousMode = false
    private var lastDetectedLang = ""
    private var currentContext = ContextManager.ConversationContext.GENERAL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val contextName = intent.getStringExtra("SELECTED_CONTEXT") ?: "GENERAL"
        currentContext = try { ContextManager.ConversationContext.valueOf(contextName) } 
                        catch(e: Exception) { ContextManager.ConversationContext.GENERAL }

        translationManager = TranslationManager()
        geminiManager = GeminiManager()
        audioManager = AudioChannelManager(this)
        audioManager.init("pt", "en") { runOnUiThread { isAudioReady = true } }
        speechManager = SpeechManager(this)

        lifecycleScope.launch {
            setStatus("⬇️ Preparando Offline...")
            val ok1 = translationManager.prepareOfflineModel("pt", "en")
            val ok2 = translationManager.prepareOfflineModel("en", "pt")
            modelsReady = ok1 && ok2

            // TESTE DE CONEXÃO GEMINI
            setStatus("📡 Testando Gemini...")
            val testResult = geminiManager.translateWithContext("Oi", "pt", "en", "Teste rápido")
            if (!testResult.contains("Erro")) {
                setStatus("✨ Gemini Online (${currentContext.emoji})")
            } else {
                setStatus("✅ Modo Offline (${currentContext.emoji})")
            }
        }

        findViewById<Button>(R.id.btn_listen).setOnClickListener {
            if (!isContinuousMode) {
                isContinuousMode = true
                (it as Button).text = "⏹ Parar Conversa"
                speechManager.startListeningContinuous("pt-BR", "en-US")
            } else {
                isContinuousMode = false
                speechManager.stopListening()
                (it as Button).text = "▶ Iniciar Conversa"
                setStatus("● Pausado")
            }
        }

        speechManager.onSpeechResult = { text ->
            lifecycleScope.launch {
                setStatus("🔍 Analisando...")
                val detectedLang = translationManager.detectLanguageSmart(text, "pt", "en", lastDetectedLang, currentContext)
                lastDetectedLang = detectedLang
                val targetCode = if (detectedLang == "pt") "en" else "pt"
                
                setStatus("✨ Gemini Traduzindo...")
                var translated = geminiManager.translateWithContext(text, detectedLang, targetCode, currentContext.instruction)

                if (translated.contains("Erro")) {
                    setStatus("🔄 Offline (Sem Sinal)...")
                    translated = translationManager.translate(text, detectedLang, targetCode, currentContext)
                }

                runOnUiThread {
                    findViewById<TextView>(if (detectedLang == "pt") R.id.tv_left else R.id.tv_right).text = text
                    findViewById<TextView>(if (detectedLang == "pt") R.id.tv_right else R.id.tv_left).text = translated
                }

                if (isAudioReady) {
                    speechManager.stopListening()
                    if (detectedLang == "pt") audioManager.speakRight(translated) else audioManager.speakLeft(translated)
                    delay((translated.length * 85L) + 1200L)
                    if (isContinuousMode) speechManager.startListeningContinuous("pt-BR", "en-US")
                }
                setStatus("🎙 ${currentContext.emoji} Ouvindo...")
            }
        }
    }

    private fun setStatus(msg: String) { runOnUiThread { findViewById<TextView>(R.id.tv_status).text = msg } }
}
