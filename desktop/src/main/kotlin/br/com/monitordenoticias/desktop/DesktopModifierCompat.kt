package br.com.monitordenoticias.desktop

import androidx.compose.ui.Modifier

/**
 * Fallback used only when Modifier.weight() is called outside a RowScope/ColumnScope.
 * Scoped Compose weight modifiers still take precedence at normal layout call sites.
 */
@Suppress("UNUSED_PARAMETER")
internal fun Modifier.weight(weight: Float): Modifier = this
