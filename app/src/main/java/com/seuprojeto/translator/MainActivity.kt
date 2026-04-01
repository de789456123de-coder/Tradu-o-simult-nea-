package com.seuprojeto.translator

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ConversationEntry(
    val original: String,
    val translated: String,
    val sourceLang: String,
    val targetLang: String,
    val timestamp: String
)

class MainActivity : AppCompatActivity() {

    private lateinit var audioManager: AudioChannelManager
    private lateinit var speechManager: SpeechManager
    private lateinit var translationManager: TranslationManager
    private lateinit var geminiManager: GeminiManager

    private var isAudioReady = false
    private var modelsReady = false
    private var isContinuousMode = false
    private var isLeftTalking = true
    private var lastDetectedLang = ""
    private var geminiEnabled = false
    private var isSpeaking = false  // TTS ativo — não sobrepor
    private var currentContext = ContextManager.ConversationContext.GENERAL
    private var ttsVolume = 80  // Volume padrão 80%

    private var leftLangCode = "pt"
    private var rightLangCode = "en"
    private var leftLangName = "Português"
    private var rightLangName = "Inglês"

    private val conversationHistory = mutableListOf<ConversationEntry>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        leftLangCode  = intent.getStringExtra("LEFT_LANG_CODE")  ?: "pt"
        rightLangCode = intent.getStringExtra("RIGHT_LANG_CODE") ?: "en"
        leftLangName  = intent.getStringExtra("LEFT_LANG_NAME")  ?: "Português"
        rightLangName = intent.getStringExtra("RIGHT_LANG_NAME") ?: "Inglês"

        currentContext = try {
            ContextManager.ConversationContext.valueOf(
                intent.getStringExtra("CONTEXT") ?: "GENERAL")
        } catch (e: Exception) { ContextManager.ConversationContext.GENERAL }

        updateLabels()
        requestMicPermission()

        translationManager = TranslationManager()
        geminiManager = GeminiManager()
        audioManager = AudioChannelManager(this)
        audioManager.init(leftLang = leftLangCode, rightLang = rightLangCode,
            onReady = { runOnUiThread { isAudioReady = true } })
        speechManager = SpeechManager(this)

        lifecycleScope.launch {
            setStatus("⬇️ Preparando offline...")
            val ok1 = translationManager.prepareOfflineModel(leftLangCode, rightLangCode)
            val ok2 = translationManager.prepareOfflineModel(rightLangCode, leftLangCode)
            modelsReady = ok1 && ok2
            setStatus("✅ ${currentContext.emoji} Pronto! Toque para falar.")
        }

        // === BOTÃO GEMINI ===
        findViewById<Button>(R.id.btn_gemini)?.apply {
            text = "🤖 Gemini OFF"
            setBackgroundColor(0xFF333333.toInt())
            setOnClickListener {
                geminiEnabled = !geminiEnabled
                text = if (geminiEnabled) "✨ Gemini ON" else "🤖 Gemini OFF"
                setBackgroundColor(if (geminiEnabled) 0xFF7C4DFF.toInt() else 0xFF333333.toInt())
                setStatus(if (geminiEnabled) "✨ Gemini ${currentContext.emoji} ativo"
                          else "🔄 Modo offline (ML Kit)")
            }
        }

        // === SEEKBAR VOLUME ===
        findViewById<SeekBar>(R.id.seekbar_volume)?.apply {
            progress = ttsVolume
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    ttsVolume = progress
                    audioManager.setVolume(progress / 100f)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

        // === SALVAR PDF ===
        findViewById<Button>(R.id.btn_save_pdf)?.setOnClickListener {
            if (conversationHistory.isEmpty()) {
                setStatus("⚠️ Nenhuma conversa para salvar")
                return@setOnClickListener
            }
            saveConversationAsPdf()
        }

        // === WALKIE-TALKIE ===
        findViewById<CardView>(R.id.card_left).setOnClickListener {
            if (!modelsReady || isSpeaking) return@setOnClickListener
            vibrateShort()
            isLeftTalking = true
            setActiveCard(left = true)
            speechManager.startListening(getLangCode(leftLangCode))
            setStatus("🎙 $leftLangName...")
        }

        findViewById<CardView>(R.id.card_right).setOnClickListener {
            if (!modelsReady || isSpeaking) return@setOnClickListener
            vibrateShort()
            isLeftTalking = false
            setActiveCard(left = false)
            speechManager.startListening(getLangCode(rightLangCode))
            setStatus("🎙 $rightLangName...")
        }

        // === BOTÃO CONTÍNUO ===
        findViewById<Button>(R.id.btn_listen).setOnClickListener {
            if (!isContinuousMode) {
                isContinuousMode = true
                lastDetectedLang = ""
                (it as Button).text = "⏹ Parar"
                speechManager.startListeningContinuous(
                    getLangCode(leftLangCode), getLangCode(rightLangCode))
                setStatus("🎙 Ouvindo...")
            } else {
                isContinuousMode = false
                speechManager.stopListening()
                (it as Button).text = "▶ Iniciar Conversa"
                resetActiveCards()
                setStatus("● Pausado")
            }
        }

        speechManager.onPartialSpeech = { partial ->
            runOnUiThread {
                val tvId = if (isLeftTalking) R.id.tv_left else R.id.tv_right
                findViewById<TextView>(tvId).text = "💬 $partial"
            }
        }

        speechManager.onListeningState = { active ->
            runOnUiThread { setMicIcon(active) }
        }

        speechManager.onError = { _ ->
            runOnUiThread { resetActiveCards() }
            if (isContinuousMode && !isSpeaking) {
                Handler(Looper.getMainLooper()).postDelayed({
                    if (isContinuousMode && !isSpeaking)
                        speechManager.startListeningContinuous(
                            getLangCode(leftLangCode), getLangCode(rightLangCode))
                }, 600)
            }
        }

        speechManager.onSpeechResult = { text ->
            lifecycleScope.launch {
                val detectedLang = if (isContinuousMode) {
                    translationManager.detectLanguageSmart(
                        text, leftLangCode, rightLangCode, lastDetectedLang, currentContext)
                } else {
                    if (isLeftTalking) leftLangCode else rightLangCode
                }
                lastDetectedLang = detectedLang

                val sourceCode = detectedLang
                val targetCode = if (detectedLang == leftLangCode) rightLangCode else leftLangCode
                val sourceTvId = if (detectedLang == leftLangCode) R.id.tv_left else R.id.tv_right
                val targetTvId = if (detectedLang == leftLangCode) R.id.tv_right else R.id.tv_left

                runOnUiThread {
                    setActiveCard(left = detectedLang == leftLangCode)
                    animateText(findViewById(sourceTvId), text)
                }

                val translated: String
                if (geminiEnabled) {
                    setStatus("✨ Gemini traduzindo...")
                    val result = geminiManager.translateWithContext(
                        text, sourceCode, targetCode, currentContext.instruction)
                    translated = if (result.startsWith("Erro")) {
                        setStatus("🔄 Gemini falhou → Offline")
                        translationManager.translate(text, sourceCode, targetCode, currentContext)
                    } else {
                        setStatus("✨ Gemini ✓")
                        result
                    }
                } else {
                    setStatus("🔄 Traduzindo...")
                    translated = translationManager.translate(text, sourceCode, targetCode, currentContext)
                }

                runOnUiThread { animateText(findViewById(targetTvId), translated) }

                // Adiciona ao histórico
                val entry = ConversationEntry(
                    original = text,
                    translated = translated,
                    sourceLang = sourceCode,
                    targetLang = targetCode,
                    timestamp = dateFormat.format(Date())
                )
                conversationHistory.add(entry)
                addToHistoryView(entry, detectedLang == leftLangCode)

                // TTS — só fala se não estiver falando
                if (isAudioReady && !isSpeaking) {
                    isSpeaking = true
                    speechManager.stopListening()
                    vibrateLong()

                    if (detectedLang == leftLangCode) audioManager.speakRight(translated)
                    else audioManager.speakLeft(translated)

                    delay((translated.length * 80L) + 1000L)
                    isSpeaking = false
                }

                runOnUiThread { resetActiveCards() }

                if (isContinuousMode) {
                    speechManager.startListeningContinuous(
                        getLangCode(leftLangCode), getLangCode(rightLangCode))
                    setStatus(if (geminiEnabled) "✨ Gemini ON — Ouvindo..." else "🎙 Ouvindo...")
                } else {
                    setStatus("✅ Traduzido. Toque para falar.")
                }
            }
        }

        findViewById<ImageButton>(R.id.btn_swap).setOnClickListener {
            speechManager.stopListening()
            isContinuousMode = false
            val tmpCode = leftLangCode; val tmpName = leftLangName
            leftLangCode = rightLangCode; leftLangName = rightLangName
            rightLangCode = tmpCode; rightLangName = tmpName
            updateLabels()
            audioManager.init(leftLang = leftLangCode, rightLang = rightLangCode,
                onReady = { runOnUiThread { isAudioReady = true } })
            (findViewById<Button>(R.id.btn_listen)).text = "▶ Iniciar Conversa"
            setStatus("🔄 Trocado!")
        }
    }

    private fun addToHistoryView(entry: ConversationEntry, isLeft: Boolean) {
        runOnUiThread {
            val historyLayout = findViewById<LinearLayout>(R.id.history_layout) ?: return@runOnUiThread
            val scrollView = findViewById<ScrollView>(R.id.history_scroll)

            val itemView = layoutInflater.inflate(
                if (isLeft) R.layout.history_item_left else R.layout.history_item_right,
                historyLayout, false)

            itemView.findViewById<TextView>(R.id.tv_history_original)?.text = entry.original
            itemView.findViewById<TextView>(R.id.tv_history_translated)?.text = entry.translated
            itemView.findViewById<TextView>(R.id.tv_history_time)?.text = entry.timestamp

            historyLayout.addView(itemView)
            scrollView?.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private fun saveConversationAsPdf() {
        try {
            val doc = PdfDocument()
            val paint = Paint().apply { color = Color.BLACK; textSize = 14f }
            val titlePaint = Paint().apply { color = Color.BLACK; textSize = 18f; isFakeBoldText = true }
            val grayPaint = Paint().apply { color = Color.GRAY; textSize = 11f }

            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            var page = doc.startPage(pageInfo)
            var canvas = page.canvas
            var y = 50f

            // Título
            canvas.drawText("Tradutor Simultâneo — Conversa", 40f, y, titlePaint)
            y += 20f
            canvas.drawText(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date()),
                40f, y, grayPaint)
            y += 30f
            canvas.drawLine(40f, y, 555f, y, grayPaint)
            y += 20f

            for (entry in conversationHistory) {
                if (y > 780f) {
                    doc.finishPage(page)
                    val newPageInfo = PdfDocument.PageInfo.Builder(595, 842,
                        doc.pages.size + 1).create()
                    page = doc.startPage(newPageInfo)
                    canvas = page.canvas
                    y = 40f
                }

                canvas.drawText("[${entry.timestamp}] ${entry.sourceLang} → ${entry.targetLang}",
                    40f, y, grayPaint)
                y += 18f
                canvas.drawText("▶ ${entry.original}", 40f, y, paint)
                y += 18f
                canvas.drawText("↳ ${entry.translated}", 40f, y, paint)
                y += 28f
            }

            doc.finishPage(page)

            val fileName = "conversa_${SimpleDateFormat("yyyyMMdd_HHmmss",
                Locale.getDefault()).format(Date())}.pdf"
            val file = File(getExternalFilesDir(null), fileName)
            doc.writeTo(FileOutputStream(file))
            doc.close()

            setStatus("📄 PDF salvo: $fileName")
            vibrateShort()
        } catch (e: Exception) {
            setStatus("❌ Erro ao salvar PDF: ${e.message?.take(40)}")
        }
    }

    private fun vibrateShort() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    }

    private fun vibrateLong() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(150)
        }
    }

    private fun getLangCode(code: String) = when(code) {
        "pt" -> "pt-BR"; "en" -> "en-US"; "es" -> "es-ES"
        "fr" -> "fr-FR"; "de" -> "de-DE"; "it" -> "it-IT"
        "nl" -> "nl-NL"; "he" -> "he-IL"; "ja" -> "ja-JP"
        "zh" -> "zh-CN"; "ko" -> "ko-KR"; "ru" -> "ru-RU"
        "ar" -> "ar-SA"; "hi" -> "hi-IN"
        else -> "en-US"
    }

    private fun animateText(view: TextView, text: String) {
        view.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_up))
        view.text = text
    }

    private fun setActiveCard(left: Boolean) {
        val pulse = AnimationUtils.loadAnimation(this, R.anim.pulse)
        if (left) {
            findViewById<CardView>(R.id.card_left).setCardBackgroundColor(0xFF1A1A3E.toInt())
            findViewById<CardView>(R.id.card_right).setCardBackgroundColor(0xFF0F1A12.toInt())
            findViewById<View>(R.id.indicator_left).apply { visibility = View.VISIBLE; startAnimation(pulse) }
            findViewById<View>(R.id.indicator_right).apply { visibility = View.INVISIBLE; clearAnimation() }
        } else {
            findViewById<CardView>(R.id.card_right).setCardBackgroundColor(0xFF1A2E1A.toInt())
            findViewById<CardView>(R.id.card_left).setCardBackgroundColor(0xFF12122A.toInt())
            findViewById<View>(R.id.indicator_right).apply { visibility = View.VISIBLE; startAnimation(pulse) }
            findViewById<View>(R.id.indicator_left).apply { visibility = View.INVISIBLE; clearAnimation() }
        }
    }

    private fun resetActiveCards() {
        findViewById<CardView>(R.id.card_left).setCardBackgroundColor(0xFF12122A.toInt())
        findViewById<CardView>(R.id.card_right).setCardBackgroundColor(0xFF0F1A12.toInt())
        findViewById<View>(R.id.indicator_left).apply { visibility = View.INVISIBLE; clearAnimation() }
        findViewById<View>(R.id.indicator_right).apply { visibility = View.INVISIBLE; clearAnimation() }
    }

    private fun setMicIcon(active: Boolean) {
        val icon = findViewById<TextView>(R.id.tv_mic_icon)
        icon.text = if (active) "🎙" else "●"
        icon.textSize = if (active) 14f else 10f
    }

    private fun updateLabels() {
        runOnUiThread {
            findViewById<TextView>(R.id.tv_left_label).text  = "🎧 $leftLangName"
            findViewById<TextView>(R.id.tv_right_label).text = "🎧 $rightLangName"
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
        translationManager.release()
    }
}
