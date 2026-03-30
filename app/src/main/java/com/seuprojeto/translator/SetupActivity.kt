package com.seuprojeto.translator

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SetupActivity : AppCompatActivity() {

    private var selectedContext = ContextManager.ConversationContext.GENERAL
    private var selectedBtn: Button? = null

    private val languages = listOf(
        "Português" to "pt", "Inglês" to "en", "Espanhol" to "es",
        "Francês" to "fr", "Alemão" to "de", "Italiano" to "it",
        "Holandês" to "nl", "Hebraico" to "he", "Japonês" to "ja",
        "Chinês" to "zh", "Coreano" to "ko", "Russo" to "ru",
        "Árabe" to "ar", "Hindi" to "hi"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        val langNames = languages.map { it.first }
        val adapterLeft = ArrayAdapter(this, android.R.layout.simple_spinner_item, langNames)
            .apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        val adapterRight = ArrayAdapter(this, android.R.layout.simple_spinner_item, langNames)
            .apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        findViewById<Spinner>(R.id.spinner_left).apply {
            adapter = adapterLeft
            setSelection(0)
        }
        findViewById<Spinner>(R.id.spinner_right).apply {
            adapter = adapterRight
            setSelection(1)
        }

        // Popula grid com botões de contexto via TableLayout dinâmico
        val grid = findViewById<android.widget.GridLayout>(R.id.grid_context)
        grid.columnCount = 2

        ContextManager.ConversationContext.values().forEach { ctx ->
            val btn = Button(this).apply {
                text = "${ctx.emoji} ${ctx.name.replace("_", " ")
                    .lowercase().replaceFirstChar { it.uppercase() }}"
                textSize = 10f
                setTextColor(Color.WHITE)
                setBackgroundColor(
                    if (ctx == selectedContext) 0xFF3D5AFE.toInt()
                    else 0xFF1E1E2E.toInt()
                )
                setPadding(8, 8, 8, 8)
                setOnClickListener {
                    selectedBtn?.setBackgroundColor(0xFF1E1E2E.toInt())
                    setBackgroundColor(0xFF3D5AFE.toInt())
                    selectedBtn = this
                    selectedContext = ctx
                }
            }
            val params = android.widget.GridLayout.LayoutParams().apply {
                width = 0
                height = android.widget.GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = android.widget.GridLayout.spec(
                    android.widget.GridLayout.UNDEFINED, 1f)
                setMargins(4, 4, 4, 4)
            }
            grid.addView(btn, params)
        }

        // Seleciona primeiro botão visualmente
        (grid.getChildAt(0) as? Button)?.let {
            selectedBtn = it
        }

        findViewById<Button>(R.id.btn_start).setOnClickListener {
            val spinLeft  = findViewById<Spinner>(R.id.spinner_left)
            val spinRight = findViewById<Spinner>(R.id.spinner_right)
            val leftIdx  = spinLeft.selectedItemPosition
            val rightIdx = spinRight.selectedItemPosition

            if (leftIdx == rightIdx) {
                Toast.makeText(this, "Escolha idiomas diferentes!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            startActivity(Intent(this, MainActivity::class.java).apply {
                putExtra("LEFT_LANG_CODE",  languages[leftIdx].second)
                putExtra("RIGHT_LANG_CODE", languages[rightIdx].second)
                putExtra("LEFT_LANG_NAME",  languages[leftIdx].first)
                putExtra("RIGHT_LANG_NAME", languages[rightIdx].first)
                putExtra("CONTEXT", selectedContext.name)
            })
        }
    }
}
