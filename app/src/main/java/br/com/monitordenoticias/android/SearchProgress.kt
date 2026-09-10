package br.com.monitordenoticias.android

/**
 * Estado comum das buscas manuais em tempo real.
 *
 * O cronômetro é calculado na UI a partir de startedAt/finishedAt para não
 * precisar acordar o ViewModel a cada segundo.
 */
data class LiveSearchProgress(
    val active: Boolean = false,
    val kind: String = "",
    val startedAt: Long = 0L,
    val finishedAt: Long = 0L,
    val completed: Int = 0,
    val total: Int = 0,
    val currentSource: String = "",
    val currentQuery: String = "",
    val found: Int = 0,
    val newCount: Int = 0,
    val errors: Int = 0
) {
    val fraction: Float
        get() = if (total <= 0) 0f else (completed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}
