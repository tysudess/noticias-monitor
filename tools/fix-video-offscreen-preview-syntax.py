from pathlib import Path

path = Path('desktop/src/main/kotlin/br/com/monitordenoticias/desktop/NativeVideoExtractor.kt')
text = path.read_text(encoding='utf-8')

replacements = {
    'KeyFrame(javafx.util.Duration.millis(125.0)) { captureFrameFx() }':
        'KeyFrame(javafx.util.Duration.millis(125.0), javafx.event.EventHandler { captureFrameFx() })',
    'KeyFrame(javafx.util.Duration.millis(80.0)) { captureFrameFx() }':
        'KeyFrame(javafx.util.Duration.millis(80.0), javafx.event.EventHandler { captureFrameFx() })',
}

changed = False
for old, new in replacements.items():
    if old in text:
        text = text.replace(old, new)
        changed = True

if 'KeyFrame(javafx.util.Duration.millis(125.0)) { captureFrameFx() }' in text or \
   'KeyFrame(javafx.util.Duration.millis(80.0)) { captureFrameFx() }' in text:
    raise SystemExit('ERRO: sintaxe KeyFrame bloqueante/invalida ainda presente.')

if 'javafx.event.EventHandler { captureFrameFx() }' not in text:
    raise SystemExit('ERRO: EventHandler esperado nao foi encontrado no preview off-screen.')

path.write_text(text, encoding='utf-8')
print('Sintaxe JavaFX KeyFrame corrigida.' if changed else 'Sintaxe JavaFX KeyFrame ja estava corrigida.')
