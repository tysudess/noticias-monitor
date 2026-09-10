package br.com.monitordenoticias.android

/**
 * Vocabulário inicial do Monitor de Notícias.
 *
 * Esta lista é aplicada na criação do banco e, uma única vez, nas instalações
 * existentes quando a versão que introduziu estes padrões é aberta.
 */
val DEFAULT_MONITOR_TERMS: List<String> = listOf(
    "Marinha do Brasil",
    "Capitania dos Portos",
    "Distrito Naval",
    "NAM Atlântico",
    "Cisne Branco",
    "Fragata Marinha do Brasil",
    "Navio-Patrulha Marinha",
    "Programa Nuclear da Marinha",
    "CAPITANIA FLUVIAL",
    "MARINHA",
    "EXÉRCITO",
    "STM",
    "FAB",
    "FORÇA AÉREA BRASILEIRA",
    "FORÇAS ARMADAS",
    "FRAGATA",
    "SUBMARINO",
    "MAIOR NAVIO DA AMÉRICA LATINA",
    "MANCHAS DE ÓLEO",
    "MILITARES",
    "MILITAR",
    "MINISTRO DA DEFESA",
    "MINISTÉRIO DA DEFESA",
    "PROSUB",
    "ENGEPRON"
).distinctBy { it.lowercase() }
