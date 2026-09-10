package br.com.monitordenoticias.desktop

import androidx.compose.foundation.text.selection.SelectionContainer as FoundationSelectionContainer
import androidx.compose.runtime.Composable

@Composable
fun SelectionContainer(content: @Composable () -> Unit) {
    FoundationSelectionContainer(content = content)
}
