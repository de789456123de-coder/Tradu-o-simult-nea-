package com.seuprojeto.translator

object ContextManager {
    enum class ConversationContext(val emoji: String, val label: String, val instruction: String) {
        GERAL("🌐", "GERAL", "Tradução natural e equilibrada."),
        MEDICO_PACIENTE("🏥", "MÉDICO / PACIENTE", "Contexto médico: Use termos leigos e empáticos."),
        MEDICO_MEDICO("⚕️", "MÉDICO / MÉDICO", "Contexto clínico: Use terminologia médica técnica."),
        EMERGENCIA_MEDICA("🚨", "EMERGÊNCIA MÉDICA", "Urgência: Seja direto, rápido e preciso."),
        REUNIAO_NEGOCIOS("💼", "REUNIÃO DE NEGÓCIOS", "Formal: Use termos corporativos e polidos."),
        NEGOCIACAO("🤝", "NEGOCIAÇÃO", "Comercial: Foco em termos de valores e acordos."),
        ADVOGADO_CLIENTE("⚖️", "ADVOGADO / CLIENTE", "Jurídico: Explique termos legais de forma clara."),
        TRIBUNAL("🏛️", "TRIBUNAL", "Solenidade: Use linguagem extremamente formal."),
        ENTRE_AMIGOS("😊", "ENTRE AMIGOS", "Informal: Use gírias e tom descontraído."),
        TURISMO("✈️", "TURISMO", "Viagem: Foco em direções e hospitalidade."),
        COMPRAS("🛍️", "COMPRAS", "Comércio: Foco em preços e medidas."),
        AULA_EDUCACAO("📚", "AULA / EDUCAÇÃO", "Didático: Clareza explicativa."),
        ENTREVISTA("🎙️", "ENTREVISTA", "Profissional: Tom sério e articulado."),
        CONFERENCIA("🎤", "CONFERÊNCIA", "Palestra: Linguagem técnica para audiências."),
        TECNOLOGIA_TI("💻", "TECNOLOGIA / TI", "TI: Preserve termos como deploy, bug e framework."),
        FINANCEIRO_BANCO("💰", "FINANCEIRO / BANCO", "Financeiro: Foco em taxas e números.")
    }
}
