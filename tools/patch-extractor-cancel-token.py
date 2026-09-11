from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCREEN = ROOT / 'desktop/src/main/kotlin/br/com/monitordenoticias/desktop/ExtractorVideoScreen.kt'
if not SCREEN.exists():
    raise SystemExit('ExtractorVideoScreen.kt ausente.')

src = SCREEN.read_text(encoding='utf-8')

if 'var downloadToken by remember { mutableLongStateOf(0L) }' not in src:
    marker = '    var busy by remember { mutableStateOf(false) }\n'
    if marker not in src:
        raise SystemExit('Estado busy não encontrado.')
    src = src.replace(marker, marker + '    var downloadToken by remember { mutableLongStateOf(0L) }\n', 1)

if 'val operationToken = downloadToken + 1L' not in src:
    marker = '        val chosen = EXTRACTOR_QUALITIES[qualityIndex]\n'
    injection = '''        val chosen = EXTRACTOR_QUALITIES[qualityIndex]\n        val operationToken = downloadToken + 1L\n        downloadToken = operationToken\n'''
    if marker not in src:
        raise SystemExit('Ponto de início do download não encontrado.')
    src = src.replace(marker, injection, 1)

old_callback = '''                scope.launch {\n                    progress = pct.coerceIn(0, 100) / 100f\n                    if (msg.isNotBlank()) status = msg\n                }'''
new_callback = '''                scope.launch {\n                    if (downloadToken != operationToken) return@launch\n                    progress = pct.coerceIn(0, 100) / 100f\n                    if (msg.isNotBlank()) status = msg\n                }'''
if old_callback in src:
    src = src.replace(old_callback, new_callback, 1)

old_after = '''            busy = false\n            result.fold('''
new_after = '''            if (downloadToken != operationToken) return@launch\n            busy = false\n            result.fold('''
if old_after in src:
    src = src.replace(old_after, new_after, 1)

old_cancel = '''                            onClick = {\n                                engine.cancel()\n                                busy = false\n                                status = "Download cancelado."\n                            },'''
new_cancel = '''                            onClick = {\n                                downloadToken += 1L\n                                engine.cancel()\n                                busy = false\n                                status = "Download cancelado."\n                            },'''
if old_cancel in src:
    src = src.replace(old_cancel, new_cancel, 1)

SCREEN.write_text(src, encoding='utf-8')
updated = SCREEN.read_text(encoding='utf-8')
assert 'mutableLongStateOf(0L)' in updated
assert 'operationToken' in updated
assert 'downloadToken != operationToken' in updated
assert 'downloadToken += 1L' in updated
print('Cancelamento protegido contra callbacks/resultados obsoletos de downloads anteriores.')
