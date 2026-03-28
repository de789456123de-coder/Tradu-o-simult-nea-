package com.seuprojeto.translator

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.gridlayout.widget.GridLayout

class SetupActivity : AppCompatActivity() {

    private val languages = listOf(
        "Português (BR)" to "pt", "Inglês (US)" to "en",
        "Espanhol" to "es", "Francês" to "fr",
        "Alemão" to "de", "Italiano" to "it",
        "Holandês" to "nl", "Hebraico" to "he",
        "Árabe" to "ar", "Hindi" to "hi",
        "Japonês" to "ja", "Chinês" to "zh",
        "Coreano" to "ko", "Russo" to "ru",
        "Polonês" to "pl", "Sueco" to "sv",
        "Turco" to "tr", "Vietnamita" to "vi",
        "Indonésio" to "id", "Tailandês" to "th",
        "Ucraniano" to "uk", "Romeno" to "ro",
        "Húngaro" to "hu", "Tcheco" to "cs",
        "Finlandês" to "fi", "Dinamarquês" to "da",
        "Norueguês" to "no", "Africâner" to "af",
        "Swahili" to "sw", "Filipino" to "tl"
    )

    private var selectedContext = ContextManager.ConversationContext.GENERAL
    private val contextButtons = mutableMapOf<ContextManager.ConversationContext, Button>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        val names = languages.map { it.first }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)

        val spinnerLeft  = findViewById<Spinner>(R.id.spinner_left)
        val spinnerRight = findViewById<Spinner>(R.id.spinner_right)
        spinnerLeft.adapter  = adapter
        spinnerRight.adapter = adapter
        spinnerLeft.setSelection(0)
        spinnerRight.setSelection(1)

        // Monta grid de contextos
        val grid = findViewById<GridLayout>(R.id.grid_context)
        grid.columnCount = 2
        grid.rowCount = (ContextManager.ConversationContext.values().size + 1) / 2

        ContextManager.ConversationContext.values().forEach { context ->
            val btn = Button(this).apply {
                text = "${context.emoji}\n${context.displayName}"
                textSize = 11f
                setTextColor(Color.parseColor("#AAAAAA"))
                setBackgroundColor(Color.parseColor("#1A1A2E"))
                setPadding(12, 16, 12, 16)
                gravity = Gravity.CENTER
            }

            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(6, 6, 6, 6)
            }
            btn.layoutParams = params

            btn.setOnClickListener {
                selectedContext = context
                updateContextButtons()
            }

            contextButtons[context] = btn
            grid.addView(btn)
        }

        updateContextButtons()

        findViewById<Button>(R.id.btn_start).setOnClickListener {
            val leftCode  = languages[spinnerLeft.selectedItemPosition].second
            val rightCode = languages[spinnerRight.selectedItemPosition].second
            val leftName  = languages[spinnerLeft.selectedItemPosition].first
            val rightName = languages[spinnerRight.selectedItemPosition].first

            if (leftCode == rightCode) {
                Toast.makeText(this, "Escolha idiomas diferentes!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            startActivity(Intent(this, MainActivity::class.java).apply {
                putExtra("LEFT_LANG_CODE",  leftCode)
                putExtra("RIGHT_LANG_CODE", rightCode)
                putExtra("LEFT_LANG_NAME",  leftName)
                putExtra("RIGHT_LANG_NAME", rightName)
                putExtra("CONTEXT", selectedContext.name)
            })
        }
    }

    private fun updateContextButtons() {
        contextButtons.forEach { (context, btn) ->
            if (context == selectedContext) {
                btn.setBackgroundColor(Color.parseColor("#3D5AFE"))
                btn.setTextColor(Color.WHITE)
            } else {
                btn.setBackgroundColor(Color.parseColor("#1A1A2E"))
                btn.setTextColor(Color.parseColor("#AAAAAA"))
            }
        }
    }
}
