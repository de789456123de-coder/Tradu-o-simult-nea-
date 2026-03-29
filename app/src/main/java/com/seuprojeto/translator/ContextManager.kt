package com.seuprojeto.translator

object ContextManager {
    enum class ConversationContext(val emoji: String, val instruction: String) {
        GENERAL("🌐", "Tradução natural."),
        MEDICO_PACIENTE("🏥", "Contexto médico: Use termos leigos e empáticos."),
        MEDICO_MEDICO("⚕️", "Contexto clínico: Use terminologia médica técnica."),
        EMERGENCIA("🚨", "Urgência: Seja direto e rápido."),
        TECNOLOGIA("💻", "TI: Preserve termos como deploy, bug e framework."),
        AMIGOS("😊", "Informal: Use tom descontraído.")
        // Adicione outros se precisar, mas mantenha estes para testar
    }
}
