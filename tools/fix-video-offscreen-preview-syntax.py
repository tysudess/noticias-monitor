from pathlib import Path

path = Path('desktop/src/main/kotlin/br/com/monitordenoticias/desktop/NativeVideoExtractor.kt')
text = path.read_text(encoding='utf-8')

# The main migration patch is intentionally idempotent, but older intermediate commits may
# already contain one VexFxRuntime block. If another one was inserted, keep only the last block.
marker = 'private object VexFxRuntime {'
first = text.find(marker)
second = text.find(marker, first + len(marker)) if first >= 0 else -1
while first >= 0 and second >= 0:
    text = text[:first] + text[second:]
    first = text.find(marker)
    second = text.find(marker, first + len(marker)) if first >= 0 else -1

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

if text.count(marker) != 1:
    raise SystemExit(f'ERRO: quantidade inesperada de VexFxRuntime: {text.count(marker)}')
if 'KeyFrame(javafx.util.Duration.millis(125.0)) { captureFrameFx() }' in text or \
   'KeyFrame(javafx.util.Duration.millis(80.0)) { captureFrameFx() }' in text:
    raise SystemExit('ERRO: sintaxe KeyFrame invalida ainda presente.')
if 'javafx.event.EventHandler { captureFrameFx() }' not in text:
    raise SystemExit('ERRO: EventHandler esperado nao foi encontrado no preview off-screen.')

path.write_text(text, encoding='utf-8')
print('Preview off-screen normalizado: runtime unico + KeyFrame corrigido.')
