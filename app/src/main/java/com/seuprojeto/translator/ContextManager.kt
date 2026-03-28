package com.seuprojeto.translator

object ContextManager {

    enum class ConversationContext(val displayName: String, val emoji: String) {
        GENERAL("Geral", "🌐"),
        MEDICAL_PATIENT("Médico / Paciente", "🏥"),
        MEDICAL_MEDICAL("Médico / Médico", "⚕️"),
        EMERGENCY("Emergência Médica", "🚨"),
        BUSINESS_MEETING("Reunião de Negócios", "💼"),
        BUSINESS_NEGOTIATION("Negociação", "🤝"),
        LEGAL_CLIENT("Advogado / Cliente", "⚖️"),
        LEGAL_COURT("Tribunal", "🏛️"),
        FRIENDS("Entre Amigos", "😊"),
        TOURISM("Turismo", "✈️"),
        SHOPPING("Compras", "🛍️"),
        EDUCATION("Aula / Educação", "📚"),
        INTERVIEW("Entrevista", "🎤"),
        CONFERENCE("Conferência", "🎯"),
        TECH("Tecnologia / TI", "💻"),
        FINANCE("Financeiro / Banco", "💰")
    }

    // Glossários PT → EN por contexto
    val glossaries: Map<ConversationContext, Map<String, String>> = mapOf(

        ConversationContext.MEDICAL_PATIENT to mapOf(
            "pressão arterial" to "blood pressure",
            "frequência cardíaca" to "heart rate",
            "batimentos cardíacos" to "heartbeat",
            "dor de cabeça" to "headache",
            "febre" to "fever",
            "temperatura" to "temperature",
            "receita médica" to "prescription",
            "remédio" to "medication",
            "medicamento" to "medicine",
            "comprimido" to "tablet",
            "cápsula" to "capsule",
            "dosagem" to "dosage",
            "sintoma" to "symptom",
            "diagnóstico" to "diagnosis",
            "tratamento" to "treatment",
            "exame" to "medical exam",
            "resultado" to "result",
            "análise de sangue" to "blood test",
            "raio-x" to "x-ray",
            "tomografia" to "ct scan",
            "ressonância" to "mri",
            "ultrassom" to "ultrasound",
            "cirurgia" to "surgery",
            "operação" to "operation",
            "anestesia" to "anesthesia",
            "internação" to "hospitalization",
            "alta médica" to "medical discharge",
            "pronto-socorro" to "emergency room",
            "uti" to "icu",
            "alergia" to "allergy",
            "infecção" to "infection",
            "inflamação" to "inflammation",
            "antibiótico" to "antibiotic",
            "anti-inflamatório" to "anti-inflammatory",
            "analgésico" to "painkiller",
            "dor" to "pain",
            "náusea" to "nausea",
            "tontura" to "dizziness",
            "falta de ar" to "shortness of breath",
            "tosse" to "cough",
            "gripe" to "flu",
            "resfriado" to "cold",
            "diabetes" to "diabetes",
            "hipertensão" to "hypertension",
            "colesterol" to "cholesterol",
            "gestante" to "pregnant",
            "gravidez" to "pregnancy",
            "consulta" to "appointment",
            "retorno" to "follow-up",
            "histórico médico" to "medical history",
            "plano de saúde" to "health insurance",
            "convênio" to "health plan"
        ),

        ConversationContext.MEDICAL_MEDICAL to mapOf(
            "hipótese diagnóstica" to "diagnostic hypothesis",
            "quadro clínico" to "clinical picture",
            "anamnese" to "anamnesis",
            "ausculta" to "auscultation",
            "percussão" to "percussion",
            "palpação" to "palpation",
            "crepitação" to "crepitation",
            "dispneia" to "dyspnea",
            "taquicardia" to "tachycardia",
            "bradicardia" to "bradycardia",
            "arritmia" to "arrhythmia",
            "infarto" to "myocardial infarction",
            "avc" to "stroke",
            "isquemia" to "ischemia",
            "hemorragia" to "hemorrhage",
            "edema" to "edema",
            "necrose" to "necrosis",
            "metástase" to "metastasis",
            "biopsia" to "biopsy",
            "hematoma" to "hematoma",
            "fratura" to "fracture",
            "luxação" to "dislocation",
            "sutura" to "suture",
            "desfibrilador" to "defibrillator",
            "oxigenoterapia" to "oxygen therapy",
            "ventilação mecânica" to "mechanical ventilation",
            "intubação" to "intubation",
            "sedação" to "sedation",
            "prognóstico" to "prognosis",
            "protocolo" to "protocol",
            "conduta" to "clinical conduct",
            "prescrição" to "prescription",
            "posologia" to "dosage regimen",
            "via oral" to "oral route",
            "via intravenosa" to "intravenous route",
            "via subcutânea" to "subcutaneous route",
            "hemograma" to "blood count",
            "leucócitos" to "leukocytes",
            "plaquetas" to "platelets",
            "glicemia" to "blood glucose",
            "creatinina" to "creatinine",
            "ureia" to "urea"
        ),

        ConversationContext.EMERGENCY to mapOf(
            "socorro" to "help",
            "emergência" to "emergency",
            "acidente" to "accident",
            "inconsciente" to "unconscious",
            "não respira" to "not breathing",
            "parada cardíaca" to "cardiac arrest",
            "ressuscitação" to "resuscitation",
            "desfibrilação" to "defibrillation",
            "sangramento" to "bleeding",
            "hemorragia" to "hemorrhage",
            "fratura" to "fracture",
            "queimadura" to "burn",
            "overdose" to "overdose",
            "envenenamento" to "poisoning",
            "alergia grave" to "severe allergy",
            "choque anafilático" to "anaphylactic shock",
            "convulsão" to "seizure",
            "desmaio" to "fainting",
            "traumatismo craniano" to "head trauma",
            "ligue para o samu" to "call the ambulance",
            "ambulância" to "ambulance",
            "bombeiros" to "fire department",
            "polícia" to "police",
            "preciso de ajuda" to "i need help",
            "onde dói" to "where does it hurt",
            "quanto tempo" to "how long ago",
            "tomou algum remédio" to "did you take any medication"
        ),

        ConversationContext.BUSINESS_MEETING to mapOf(
            "pauta" to "agenda",
            "reunião" to "meeting",
            "apresentação" to "presentation",
            "relatório" to "report",
            "meta" to "target",
            "objetivo" to "objective",
            "estratégia" to "strategy",
            "orçamento" to "budget",
            "receita" to "revenue",
            "lucro" to "profit",
            "prejuízo" to "loss",
            "investimento" to "investment",
            "retorno sobre investimento" to "return on investment",
            "roi" to "roi",
            "kpi" to "kpi",
            "indicador" to "indicator",
            "prazo" to "deadline",
            "entrega" to "delivery",
            "cronograma" to "schedule",
            "projeto" to "project",
            "equipe" to "team",
            "gestor" to "manager",
            "diretor" to "director",
            "ceo" to "ceo",
            "acionista" to "shareholder",
            "conselho" to "board",
            "proposta" to "proposal",
            "contrato" to "contract",
            "parceria" to "partnership",
            "cliente" to "client",
            "fornecedor" to "supplier",
            "concorrente" to "competitor",
            "mercado" to "market",
            "participação de mercado" to "market share",
            "crescimento" to "growth",
            "expansão" to "expansion",
            "fusão" to "merger",
            "aquisição" to "acquisition"
        ),

        ConversationContext.BUSINESS_NEGOTIATION to mapOf(
            "proposta" to "proposal",
            "contraproposta" to "counteroffer",
            "desconto" to "discount",
            "preço" to "price",
            "valor" to "value",
            "pagamento" to "payment",
            "prazo de pagamento" to "payment term",
            "parcelamento" to "installment",
            "à vista" to "cash payment",
            "comissão" to "commission",
            "margem" to "margin",
            "negociação" to "negotiation",
            "acordo" to "agreement",
            "termos" to "terms",
            "condições" to "conditions",
            "cláusula" to "clause",
            "garantia" to "guarantee",
            "multa" to "penalty",
            "rescisão" to "termination",
            "renovação" to "renewal",
            "exclusividade" to "exclusivity",
            "confidencialidade" to "confidentiality",
            "nda" to "nda",
            "fechamos" to "we have a deal",
            "aceito" to "i accept",
            "não aceito" to "i do not accept",
            "preciso pensar" to "i need to think about it",
            "vamos avançar" to "let us move forward"
        ),

        ConversationContext.LEGAL_CLIENT to mapOf(
            "processo" to "lawsuit",
            "ação judicial" to "legal action",
            "petição" to "petition",
            "recurso" to "appeal",
            "sentença" to "sentence",
            "decisão" to "decision",
            "liminar" to "injunction",
            "audiência" to "hearing",
            "juiz" to "judge",
            "promotor" to "prosecutor",
            "advogado" to "lawyer",
            "réu" to "defendant",
            "autor" to "plaintiff",
            "testemunha" to "witness",
            "prova" to "evidence",
            "contrato" to "contract",
            "cláusula" to "clause",
            "rescisão" to "termination",
            "indenização" to "indemnification",
            "dano moral" to "moral damage",
            "dano material" to "material damage",
            "multa" to "fine",
            "prazo" to "deadline",
            "notificação" to "notice",
            "intimação" to "subpoena",
            "mandado" to "warrant",
            "habeas corpus" to "habeas corpus",
            "sigilo" to "confidentiality",
            "direito" to "right",
            "dever" to "duty",
            "obrigação" to "obligation",
            "responsabilidade" to "liability",
            "culpa" to "fault",
            "dolo" to "intent",
            "prescrição" to "statute of limitations"
        ),

        ConversationContext.LEGAL_COURT to mapOf(
            "excelência" to "your honor",
            "meritíssimo" to "your honor",
            "tribuna" to "podium",
            "plenário" to "plenary",
            "alegação" to "allegation",
            "sustentação oral" to "oral argument",
            "julgamento" to "trial",
            "veredicto" to "verdict",
            "condenação" to "conviction",
            "absolvição" to "acquittal",
            "pena" to "sentence",
            "regime fechado" to "closed regime",
            "regime aberto" to "open regime",
            "liberdade condicional" to "parole",
            "fiança" to "bail",
            "prisão preventiva" to "pretrial detention",
            "flagrante" to "caught in the act",
            "inquérito" to "inquiry",
            "denúncia" to "indictment",
            "defesa" to "defense",
            "acusação" to "prosecution"
        ),

        ConversationContext.FRIENDS to mapOf(
            "cara" to "dude",
            "mano" to "bro",
            "valeu" to "thanks",
            "beleza" to "cool",
            "show" to "awesome",
            "legal" to "cool",
            "ótimo" to "great",
            "que saudade" to "i missed you",
            "sinto sua falta" to "i miss you",
            "como vai" to "how are you",
            "tudo bem" to "all good",
            "que horas são" to "what time is it",
            "vamos sair" to "let us go out",
            "bora" to "let us go",
            "festa" to "party",
            "rolar" to "happening",
            "curtir" to "enjoy",
            "balada" to "nightclub",
            "cerveja" to "beer",
            "comida" to "food",
            "filme" to "movie",
            "série" to "series",
            "jogo" to "game",
            "futebol" to "soccer",
            "treino" to "workout"
        ),

        ConversationContext.TOURISM to mapOf(
            "hotel" to "hotel",
            "pousada" to "inn",
            "reserva" to "reservation",
            "check-in" to "check-in",
            "check-out" to "check-out",
            "quarto" to "room",
            "suite" to "suite",
            "café da manhã" to "breakfast",
            "restaurante" to "restaurant",
            "cardápio" to "menu",
            "conta" to "bill",
            "gorjeta" to "tip",
            "táxi" to "taxi",
            "uber" to "uber",
            "aeroporto" to "airport",
            "voo" to "flight",
            "passagem" to "ticket",
            "passaporte" to "passport",
            "visto" to "visa",
            "bagagem" to "luggage",
            "mala" to "suitcase",
            "turista" to "tourist",
            "atração" to "attraction",
            "museu" to "museum",
            "praia" to "beach",
            "mapa" to "map",
            "onde fica" to "where is",
            "como chego" to "how do i get to",
            "quanto custa" to "how much does it cost",
            "câmbio" to "currency exchange",
            "dólar" to "dollar",
            "euro" to "euro"
        ),

        ConversationContext.SHOPPING to mapOf(
            "quanto custa" to "how much is it",
            "preço" to "price",
            "desconto" to "discount",
            "promoção" to "sale",
            "caro" to "expensive",
            "barato" to "cheap",
            "tamanho" to "size",
            "cor" to "color",
            "modelo" to "model",
            "estoque" to "stock",
            "disponível" to "available",
            "esgotado" to "out of stock",
            "troca" to "exchange",
            "devolução" to "return",
            "nota fiscal" to "receipt",
            "cartão de crédito" to "credit card",
            "débito" to "debit",
            "pix" to "pix transfer",
            "parcelado" to "installments",
            "garantia" to "warranty",
            "entrega" to "delivery",
            "frete" to "shipping",
            "embrulho" to "wrapping",
            "vitrine" to "display window",
            "provador" to "fitting room"
        ),

        ConversationContext.EDUCATION to mapOf(
            "aula" to "class",
            "professor" to "teacher",
            "aluno" to "student",
            "escola" to "school",
            "universidade" to "university",
            "faculdade" to "college",
            "matéria" to "subject",
            "disciplina" to "discipline",
            "prova" to "test",
            "trabalho" to "assignment",
            "nota" to "grade",
            "aprovado" to "passed",
            "reprovado" to "failed",
            "recuperação" to "remedial",
            "formatura" to "graduation",
            "diploma" to "diploma",
            "pesquisa" to "research",
            "artigo" to "article",
            "tese" to "thesis",
            "dissertação" to "dissertation",
            "bolsa" to "scholarship",
            "estágio" to "internship",
            "currículo" to "curriculum",
            "ementa" to "syllabus",
            "biblioteca" to "library",
            "laboratório" to "laboratory"
        ),

        ConversationContext.TECH to mapOf(
            "software" to "software",
            "hardware" to "hardware",
            "sistema" to "system",
            "aplicativo" to "application",
            "servidor" to "server",
            "banco de dados" to "database",
            "código" to "code",
            "programação" to "programming",
            "desenvolvimento" to "development",
            "bug" to "bug",
            "erro" to "error",
            "atualização" to "update",
            "versão" to "version",
            "deploy" to "deployment",
            "backup" to "backup",
            "segurança" to "security",
            "senha" to "password",
            "usuário" to "user",
            "interface" to "interface",
            "rede" to "network",
            "internet" to "internet",
            "nuvem" to "cloud",
            "inteligência artificial" to "artificial intelligence",
            "machine learning" to "machine learning",
            "algoritmo" to "algorithm",
            "dados" to "data",
            "análise" to "analysis",
            "dashboard" to "dashboard",
            "api" to "api",
            "integração" to "integration"
        ),

        ConversationContext.FINANCE to mapOf(
            "conta corrente" to "checking account",
            "poupança" to "savings account",
            "investimento" to "investment",
            "rendimento" to "yield",
            "juros" to "interest",
            "taxa" to "rate",
            "imposto" to "tax",
            "declaração" to "tax return",
            "cpf" to "tax id",
            "cnpj" to "company tax id",
            "empréstimo" to "loan",
            "financiamento" to "financing",
            "parcela" to "installment",
            "débito" to "debit",
            "crédito" to "credit",
            "transferência" to "transfer",
            "ted" to "bank transfer",
            "doc" to "bank transfer",
            "pix" to "instant payment",
            "boleto" to "bank slip",
            "fatura" to "invoice",
            "extrato" to "statement",
            "saldo" to "balance",
            "limite" to "limit",
            "cartão" to "card",
            "ação" to "stock",
            "fundo" to "fund",
            "renda fixa" to "fixed income",
            "renda variável" to "variable income",
            "tesouro direto" to "government bond",
            "corretora" to "brokerage"
        )
    )

    // Palavras-chave para auto-detectar contexto pela fala
    val contextKeywords: Map<ConversationContext, List<String>> = mapOf(
        ConversationContext.MEDICAL_PATIENT to listOf(
            "dor", "febre", "remédio", "médico", "consulta", "sintoma",
            "exame", "receita", "pressão", "sangue", "hospital"
        ),
        ConversationContext.MEDICAL_MEDICAL to listOf(
            "diagnóstico", "protocolo", "prescrição", "anamnese",
            "prognóstico", "conduta", "internação", "uti"
        ),
        ConversationContext.EMERGENCY to listOf(
            "socorro", "emergência", "acidente", "inconsciente",
            "ambulância", "urgente", "ajuda", "samu"
        ),
        ConversationContext.BUSINESS_MEETING to listOf(
            "reunião", "relatório", "meta", "estratégia",
            "orçamento", "projeto", "equipe", "prazo"
        ),
        ConversationContext.BUSINESS_NEGOTIATION to listOf(
            "proposta", "desconto", "preço", "contrato",
            "acordo", "negociação", "fechar", "valor"
        ),
        ConversationContext.LEGAL_CLIENT to listOf(
            "processo", "advogado", "contrato", "direito",
            "indenização", "cláusula", "judicial", "ação"
        ),
        ConversationContext.LEGAL_COURT to listOf(
            "excelência", "tribunal", "sentença", "julgamento",
            "veredicto", "defesa", "acusação", "juiz"
        ),
        ConversationContext.FRIENDS to listOf(
            "cara", "mano", "bora", "valeu", "festa",
            "curtir", "balada", "saudade"
        ),
        ConversationContext.TOURISM to listOf(
            "hotel", "voo", "passagem", "passaporte", "turismo",
            "aeroporto", "reserva", "restaurante"
        ),
        ConversationContext.SHOPPING to listOf(
            "preço", "desconto", "tamanho", "estoque",
            "troca", "garantia", "entrega", "comprar"
        ),
        ConversationContext.TECH to listOf(
            "software", "código", "sistema", "servidor",
            "bug", "api", "dados", "aplicativo"
        ),
        ConversationContext.FINANCE to listOf(
            "conta", "investimento", "juros", "imposto",
            "empréstimo", "taxa", "saldo", "transferência"
        )
    )

    // Aplica glossário ao texto antes de traduzir
    fun applyGlossary(
        text: String,
        context: ConversationContext,
        sourceLang: String
    ): String {
        if (context == ConversationContext.GENERAL) return text
        val glossary = glossaries[context] ?: return text
        var result = text.lowercase()

        if (sourceLang == "pt") {
            glossary.forEach { (pt, _) ->
                if (result.contains(pt)) {
                    result = result.replace(pt, "[$pt]")
                }
            }
        }
        return text
    }

    // Detecta contexto automaticamente pela fala
    fun detectContext(text: String): ConversationContext? {
        val lower = text.lowercase()
        var bestContext: ConversationContext? = null
        var bestScore = 0

        contextKeywords.forEach { (context, keywords) ->
            val score = keywords.count { lower.contains(it) }
            if (score > bestScore) {
                bestScore = score
                bestContext = context
            }
        }

        return if (bestScore >= 2) bestContext else null
    }

    // Enriquece o texto com contexto para melhorar tradução
    fun enrichTextForTranslation(
        text: String,
        context: ConversationContext,
        sourceLang: String,
        targetLang: String
    ): String {
        if (context == ConversationContext.GENERAL) return text
        val glossary = glossaries[context] ?: return text
        var result = text

        if (sourceLang == "pt" && targetLang == "en") {
            glossary.forEach { (pt, en) ->
                val regex = Regex("\\b${Regex.escape(pt)}\\b", RegexOption.IGNORE_CASE)
                result = regex.replace(result, en)
            }
        } else if (sourceLang == "en" && targetLang == "pt") {
            glossary.entries.forEach { (pt, en) ->
                val regex = Regex("\\b${Regex.escape(en)}\\b", RegexOption.IGNORE_CASE)
                result = regex.replace(result, pt)
            }
        }

        return result
    }
}
