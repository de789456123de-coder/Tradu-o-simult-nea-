package com.seuprojeto.translator

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity

class SetupActivity : AppCompatActivity() {

    // Par: nome exibido → código do idioma
    private val languages = listOf(
        "Português (BR)" to "pt",
        "Inglês (US)"    to "en",
        "Espanhol"       to "es",
        "Francês"        to "fr",
        "Alemão"         to "de",
        "Italiano"       to "it",
        "Japonês"        to "ja",
        "Chinês"         to "zh",
        "Coreano"        to "ko",
        "Russo"          to "ru",
        "Árabe"          to "ar",
        "Hindi"          to "hi"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        val names = languages.map { it.first }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)

        val spinnerLeft = findViewById<Spinner>(R.id.spinner_left)
        val spinnerRight = findViewById<Spinner>(R.id.spinner_right)

        spinnerLeft.adapter = adapter
        spinnerRight.adapter = adapter

        // Padrão: PT no esquerdo, EN no direito
        spinnerLeft.setSelection(0)
        spinnerRight.setSelection(1)

        findViewById<Button>(R.id.btn_start).setOnClickListener {
            val leftCode = languages[spinnerLeft.selectedItemPosition].second
            val rightCode = languages[spinnerRight.selectedItemPosition].second
            val leftName = languages[spinnerLeft.selectedItemPosition].first
            val rightName = languages[spinnerRight.selectedItemPosition].first

            if (leftCode == rightCode) {
                android.widget.Toast.makeText(
                    this,
                    "Escolha idiomas diferentes!",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("LEFT_LANG_CODE", leftCode)
                putExtra("RIGHT_LANG_CODE", rightCode)
                putExtra("LEFT_LANG_NAME", leftName)
                putExtra("RIGHT_LANG_NAME", rightName)
            }
            startActivity(intent)
        }
    }
}
