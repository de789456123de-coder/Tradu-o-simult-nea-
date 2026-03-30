package com.seuprojeto.translator

object ContextManager {
    enum class ConversationContext(val emoji: String, val instruction: String) {
        GENERAL("🌐", "Tradução natural e equilibrada."),
        MEDICO_PACIENTE("🏥", "Contexto médico: Use termos leigos e empáticos para o paciente."),
        MEDICO_MEDICO("⚕️", "Contexto clínico: Use terminologia médica rigorosa."),
        EMERGENCIA("🚨", "Contexto crítico: Tradução direta e urgente."),
        NEGOCIOS("💼", "Contexto corporativo: Linguagem formal e termos de business."),
        NEGOCIACAO("🤝", "Contexto de negociação: Foco em persuasão e termos contratuais."),
        ADVOGADO("⚖️", "Contexto jurídico: Termos legais precisos e linguagem formal."),
        TRIBUNAL("🏛️", "Contexto solene: Linguagem extremamente formal."),
        AMIGOS("😊", "Contexto informal: Pode usar gírias e tom descontraído."),
        TURISMO("✈️", "Contexto de viagem: Foco em direções e cortesia."),
        COMPRAS("🛍️", "Contexto comercial: Foco em preços e negociação."),
        EDUCACAO("📚", "Contexto didático: Clareza explicativa."),
        ENTREVISTA("🎙️", "Contexto profissional: Tom sério e articulado."),
        CONFERENCIA("🎤", "Contexto de palestra: Linguagem técnica para grandes audiências."),
        TECNOLOGIA("💻", "Contexto de TI: Preserve termos técnicos em inglês."),
        FINANCEIRO("💰", "Contexto bancário: Foco em números e termos financeiros.")
    }

    val glossaries: Map<ConversationContext, Map<String, String>> = mapOf(
        ConversationContext.MEDICO_PACIENTE to mapOf(
            "dor" to "pain", "febre" to "fever", "remédio" to "medicine"
        ),
        ConversationContext.TECNOLOGIA to mapOf(
            "deploy" to "deploy", "bug" to "bug", "servidor" to "server"
        )
    )

    fun enrichTextForTranslation(
        text: String,
        context: ConversationContext,
        sourceLang: String,
        targetLang: String
    ): String = text
}
