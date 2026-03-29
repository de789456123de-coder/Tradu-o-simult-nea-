package com.seuprojeto.translator

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var speechManager: SpeechManager
    private lateinit var translationManager: TranslationManager
    private lateinit var geminiManager: GeminiManager
    private var isContinuous = false
    private var currentContext = ContextManager.ConversationContext.GENERAL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val contextName = intent.getStringExtra("SELECTED_CONTEXT") ?: "GENERAL"
        currentContext = try { ContextManager.ConversationContext.valueOf(contextName) } catch(e: Exception) { ContextManager.ConversationContext.GENERAL }

        speechManager = SpeechManager(this)
        translationManager = TranslationManager()
        geminiManager = GeminiManager()

        lifecycleScope.launch {
            setStatus("📡 Verificando sistemas...")
            translationManager.prepareOfflineModel("pt", "en")
            translationManager.prepareOfflineModel("en", "pt")
            setStatus("✨ ${currentContext.emoji} Pronto")
        }

        findViewById<Button>(R.id.btn_listen).setOnClickListener {
            if (!isContinuous) {
                isContinuous = true
                (it as Button).text = "⏹ Parar"
                speechManager.startListeningContinuous("pt-BR", "en-US")
            } else {
                isContinuous = false
                speechManager.stopListening()
                (it as Button).text = "▶ Iniciar"
            }
        }

        speechManager.onSpeechResult = { text ->
            lifecycleScope.launch {
                setStatus("🔍 Traduzindo...")
                val detected = translationManager.detectLanguageSmart(text, "pt", "en", "", currentContext)
                val target = if (detected == "pt") "en" else "pt"
                
                var res = geminiManager.translateWithContext(text, detected, target, currentContext.instruction)
                if (res.contains("Erro")) res = translationManager.translate(text, detected, target, currentContext)

                findViewById<TextView>(if (detected == "pt") R.id.tv_left else R.id.tv_right).text = text
                findViewById<TextView>(if (detected == "pt") R.id.tv_right else R.id.tv_left).text = res
                
                setStatus("🎙 Ouvindo...")
                if (isContinuous) {
                    delay(2000)
                    speechManager.startListeningContinuous("pt-BR", "en-US")
                }
            }
        }
    }

    private fun setStatus(m: String) { findViewById<TextView>(R.id.tv_status).text = m }
}
