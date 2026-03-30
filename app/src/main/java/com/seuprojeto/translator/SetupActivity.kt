package com.seuprojeto.translator

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.gridlayout.widget.GridLayout
import android.view.Gravity
import android.graphics.Color

class SetupActivity : AppCompatActivity() {

    private var selectedContext = ContextManager.ConversationContext.GENERAL

    private val languages = listOf(
        "Português" to "pt",
        "Inglês" to "en",
        "Espanhol" to "es",
        "Francês" to "fr",
        "Alemão" to "de",
        "Italiano" to "it",
        "Holandês" to "nl",
        "Hebraico" to "he",
        "Japonês" to "ja",
        "Chinês" to "zh",
        "Coreano" to "ko",
        "Russo" to "ru",
        "Árabe" to "ar",
        "Hindi" to "hi"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        // Configura spinners
        val langNames = languages.map { it.first }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, langNames).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        val spinnerLeft = findViewById<Spinner>(R.id.spinner_left)
        val spinnerRight = findViewById<Spinner>(R.id.spinner_right)

        spinnerLeft.adapter = adapter
        spinnerRight.adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_item, langNames).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        // Padrão: PT esquerdo, EN direito
        spinnerLeft.setSelection(0)
        spinnerRight.setSelection(1)

        // Popula grid de contextos
        val grid = findViewById<GridLayout>(R.id.grid_context)
        grid.columnCount = 2

        ContextManager.ConversationContext.values().forEach { ctx ->
            val btn = Button(this).apply {
                text = "${ctx.emoji} ${ctx.name.replace("_", " ").lowercase()
                    .replaceFirstChar { it.uppercase() }}"
                textSize = 11f
                setTextColor(Color.WHITE)
                setBackgroundColor(if (ctx == selectedContext) 0xFF3D5AFE.toInt() else 0xFF1E1E2E.toInt())
                setPadding(8, 8, 8, 8)
                setOnClickListener {
                    selectedContext = ctx
                    // Atualiza cores
                    for (i in 0 until grid.childCount) {
                        (grid.getChildAt(i) as? Button)
                            ?.setBackgroundColor(0xFF1E1E2E.toInt())
                    }
                    setBackgroundColor(0xFF3D5AFE.toInt())
                }
            }

            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(4, 4, 4, 4)
            }
            grid.addView(btn, params)
        }

        // Botão iniciar
        findViewById<Button>(R.id.btn_start).setOnClickListener {
            val leftIdx = spinnerLeft.selectedItemPosition
            val rightIdx = spinnerRight.selectedItemPosition

            if (leftIdx == rightIdx) {
                Toast.makeText(this, "Escolha idiomas diferentes!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            startActivity(Intent(this, MainActivity::class.java).apply {
                putExtra("LEFT_LANG_CODE", languages[leftIdx].second)
                putExtra("RIGHT_LANG_CODE", languages[rightIdx].second)
                putExtra("LEFT_LANG_NAME", languages[leftIdx].first)
                putExtra("RIGHT_LANG_NAME", languages[rightIdx].first)
                putExtra("CONTEXT", selectedContext.name)
            })
        }
    }
}
