package br.com.monitordenoticias.android

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp

/**
 * Atalho usado pelo layout v2.7 para declarar margens horizontais iguais
 * combinadas com top/bottom diferentes.
 */
@Suppress("FunctionName")
fun PaddingValues(horizontal: Dp, top: Dp, bottom: Dp): PaddingValues =
    PaddingValues(start = horizontal, top = top, end = horizontal, bottom = bottom)
