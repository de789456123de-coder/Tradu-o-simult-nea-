package com.seuprojeto.translator

object ContextManager {
    enum class ConversationContext(val emoji: String, val instruction: String) {
        GENERAL("🌐", "Tradução natural e equilibrada."),
        MEDICO_PACIENTE("🏥", "Contexto médico: Use termos leigos e empáticos. Explique termos técnicos de forma simples."),
        MEDICO_MEDICO("⚕️", "Contexto acadêmico/clínico: Use terminologia médica rigorosa (ex: use 'dispneia' em vez de 'falta de ar')."),
        EMERGENCIA("🚨", "Contexto crítico: Tradução direta, urgente e sem ambiguidades."),
        NEGOCIOS("💼", "Contexto corporativo: Use linguagem formal, polida e termos de business."),
        NEGOCIACAO("🤝", "Contexto de vendas: Foco em persuasão, clareza e termos contratuais."),
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
}
