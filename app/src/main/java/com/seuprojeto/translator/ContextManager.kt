package com.seuprojeto.translator

object ContextManager {
    enum class ConversationContext(val emoji: String, val instruction: String) {
        GENERAL("🌐", "Tradução natural e equilibrada."),
        MEDICO_PACIENTE("🏥", "Contexto médico: Use termos leigos e empáticos. Explique termos técnicos de forma simples para o paciente."),
        MEDICO_MEDICO("⚕️", "Contexto clínico: Use terminologia médica rigorosa (ex: use 'dispneia' em vez de 'falta de ar', 'cefaleia' em vez de 'dor de cabeça')."),
        EMERGENCIA("🚨", "Contexto crítico: Tradução direta, urgente e sem ambiguidades. Seja extremamente objetivo."),
        NEGOCIOS("💼", "Contexto corporativo: Use linguagem formal, polida e termos de business."),
        NEGOCIACAO("🤝", "Contexto de negociação: Foco em persuasão, clareza e termos contratuais."),
        ADVOGADO("⚖️", "Contexto jurídico: Use termos legais precisos e linguagem formal."),
        TRIBUNAL("🏛️", "Contexto solene: Linguagem extremamente formal e respeitosa."),
        AMIGOS("😊", "Contexto informal: Pode usar gírias comuns e tom descontraído."),
        TURISMO("✈️", "Contexto de viagem: Foco em direções, hospitalidade e cortesia."),
        COMPRAS("🛍️", "Contexto comercial: Foco em preços, medidas e negociação de valores."),
        EDUCACAO("📚", "Contexto didático: Clareza explicativa e tom de professor/aluno."),
        ENTREVISTA("🎙️", "Contexto profissional: Tom sério, articulado e focado em competências."),
        CONFERENCIA("🎤", "Contexto de palestra: Linguagem técnica para grandes audiências."),
        TECNOLOGIA("💻", "Contexto de TI: Preserve termos técnicos em inglês (ex: 'deploy', 'framework', 'bug')."),
        FINANCEIRO("💰", "Contexto bancário: Foco em números, taxas e termos do mercado financeiro.")
    }

    // Glossários para detecção local
    val glossaries: Map<ConversationContext, Map<String, String>> = mapOf(
        ConversationContext.MEDICO_PACIENTE to mapOf(
            "dor" to "pain", "febre" to "fever", "remédio" to "medicine",
            "exame" to "exam", "cirurgia" to "surgery", "hospital" to "hospital"
        ),
        ConversationContext.TECNOLOGIA to mapOf(
            "deploy" to "deploy", "bug" to "bug", "framework" to "framework",
            "código" to "code", "sistema" to "system", "servidor" to "server"
        ),
        ConversationContext.FINANCEIRO to mapOf(
            "taxa" to "rate", "juros" to "interest", "investimento" to "investment",
            "lucro" to "profit", "prejuízo" to "loss"
        )
    )

    fun enrichTextForTranslation(
        text: String,
        context: ConversationContext,
        sourceLang: String,
        targetLang: String
    ): String = text
}
