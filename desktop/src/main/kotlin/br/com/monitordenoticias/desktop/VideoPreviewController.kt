package br.com.monitordenoticias.desktop

/**
 * Mantém o contrato usado pela tela do Editor de Vídeo, mas troca o motor
 * JavaFX pelo host PySide6/QMediaPlayer/QAudioOutput/QVideoWidget proveniente
 * do editor antigo que já funcionava.
 */
internal typealias VideoPreviewController = QtEmbeddedVideoPreviewController
