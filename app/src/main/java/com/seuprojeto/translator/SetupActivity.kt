package com.seuprojeto.translator

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity

class SetupActivity : AppCompatActivity() {

    private val languages = listOf(
        // Américas
        "Português (BR)"        to "pt",
        "Inglês (US)"           to "en",
        "Espanhol"              to "es",
        "Francês (CA)"          to "fr",
        "Haitiano Crioulo"      to "ht",
        "Guarani"               to "gn",
        // Europa Ocidental
        "Francês"               to "fr",
        "Alemão"                to "de",
        "Italiano"              to "it",
        "Holandês"              to "nl",
        "Português (PT)"        to "pt",
        "Espanhol (ES)"         to "es",
        "Catalão"               to "ca",
        "Galego"                to "gl",
        "Basco"                 to "eu",
        // Europa do Norte
        "Sueco"                 to "sv",
        "Norueguês"             to "no",
        "Dinamarquês"           to "da",
        "Finlandês"             to "fi",
        "Islandês"              to "is",
        // Europa do Leste
        "Russo"                 to "ru",
        "Polonês"               to "pl",
        "Tcheco"                to "cs",
        "Eslovaco"              to "sk",
        "Húngaro"               to "hu",
        "Romeno"                to "ro",
        "Búlgaro"               to "bg",
        "Croata"                to "hr",
        "Sérvio"                to "sr",
        "Ucraniano"             to "uk",
        // Oriente Médio
        "Hebraico"              to "he",
        "Árabe"                 to "ar",
        "Persa (Farsi)"         to "fa",
        "Turco"                 to "tr",
        "Armênio"               to "hy",
        "Georgiano"             to "ka",
        // Ásia
        "Hindi"                 to "hi",
        "Bengali"               to "bn",
        "Urdu"                  to "ur",
        "Punjabi"               to "pa",
        "Gujarati"              to "gu",
        "Tâmil"                 to "ta",
        "Telugu"                to "te",
        "Kannada"               to "kn",
        "Malaiala"              to "ml",
        "Sinhala"               to "si",
        "Nepalês"               to "ne",
        "Chinês (Simplificado)" to "zh",
        "Chinês (Tradicional)"  to "zh-TW",
        "Japonês"               to "ja",
        "Coreano"               to "ko",
        "Vietnamita"            to "vi",
        "Tailandês"             to "th",
        "Indonésio"             to "id",
        "Malaio"                to "ms",
        "Filipino"              to "tl",
        "Khmer"                 to "km",
        "Birmanês"              to "my",
        // África
        "Swahili"               to "sw",
        "Amárico"               to "am",
        "Yorùbá"                to "yo",
        "Igbo"                  to "ig",
        "Hausa"                 to "ha",
        "Zulu"                  to "zu",
        "Xhosa"                 to "xh",
        "Somali"                to "so",
        "Africâner"             to "af"
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

        spinnerLeft.setSelection(0)  // Português
        spinnerRight.setSelection(1) // Inglês

        findViewById<Button>(R.id.btn_start).setOnClickListener {
            val leftCode = languages[spinnerLeft.selectedItemPosition].second
            val rightCode = languages[spinnerRight.selectedItemPosition].second
            val leftName = languages[spinnerLeft.selectedItemPosition].first
            val rightName = languages[spinnerRight.selectedItemPosition].first

            if (leftCode == rightCode) {
                android.widget.Toast.makeText(
                    this, "Escolha idiomas diferentes!", android.widget.Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            startActivity(Intent(this, MainActivity::class.java).apply {
                putExtra("LEFT_LANG_CODE", leftCode)
                putExtra("RIGHT_LANG_CODE", rightCode)
                putExtra("LEFT_LANG_NAME", leftName)
                putExtra("RIGHT_LANG_NAME", rightName)
            })
        }
    }
}
