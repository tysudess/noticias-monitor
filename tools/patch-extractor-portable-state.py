from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCREEN = ROOT / 'desktop/src/main/kotlin/br/com/monitordenoticias/desktop/ExtractorVideoScreen.kt'
STORE = ROOT / 'desktop/src/main/kotlin/br/com/monitordenoticias/desktop/ExtractorPortableStateStore.kt'

if not SCREEN.exists() or not STORE.exists():
    raise SystemExit('ExtractorVideoScreen.kt ou ExtractorPortableStateStore.kt ausente.')

src = SCREEN.read_text(encoding='utf-8')

src = src.replace(
    '    val updater = remember { YtDlpUpdater(engine) }\n',
    '    val updater = remember { YtDlpUpdater(engine) }\n    val portableState = remember { ExtractorPortableStateStore(engine.appDir.toFile()) }\n',
    1,
)
src = src.replace(
    '    var qualityIndex by remember { mutableIntStateOf(1) }\n',
    '    var qualityIndex by remember { mutableIntStateOf(portableState.loadQualityIndex(1)) }\n',
    1,
)
src = src.replace(
    '    var history by remember { mutableStateOf(listOf<String>()) }\n    var proxy by remember { mutableStateOf("") }\n',
    '    var history by remember { mutableStateOf(portableState.loadHistory()) }\n    var proxy by remember { mutableStateOf(portableState.loadProxy()) }\n',
    1,
)
src = src.replace(
    '                    history = listOf(file.absolutePath) + history.take(49)\n',
    '                    history = portableState.addHistory(file.absolutePath)\n',
    1,
)
src = src.replace(
    '                            RadioButton(selected = qualityIndex == index, onClick = { if (!busy) qualityIndex = index })\n',
    '                            RadioButton(selected = qualityIndex == index, onClick = { if (!busy) { qualityIndex = index; portableState.saveQualityIndex(index) } })\n',
    1,
)
src = src.replace(
    '                            Modifier.fillMaxWidth().clickable(enabled = !busy) { qualityIndex = index }.padding(vertical = 3.dp),\n',
    '                            Modifier.fillMaxWidth().clickable(enabled = !busy) { qualityIndex = index; portableState.saveQualityIndex(index) }.padding(vertical = 3.dp),\n',
    1,
)
src = src.replace(
    '                    if (history.isEmpty()) Text("Nenhum download nesta sessão.", color = ExMuted)\n                    history.forEach { Text(it, color = ExText, fontSize = 12.sp) }\n',
    '''                    if (history.isEmpty()) Text("Nenhum download salvo.", color = ExMuted)\n                    if (history.isNotEmpty()) {\n                        OutlinedButton(onClick = {\n                            portableState.clearHistory()\n                            history = emptyList()\n                        }) { Text("LIMPAR HISTÓRICO") }\n                    }\n                    history.forEach { Text(it, color = ExText, fontSize = 12.sp) }\n''',
    1,
)
src = src.replace(
    '                        onValueChange = { proxy = it },\n',
    '                        onValueChange = { proxy = it; portableState.saveProxy(it) },\n',
    1,
)

SCREEN.write_text(src, encoding='utf-8')
updated = SCREEN.read_text(encoding='utf-8')
for required in (
    'ExtractorPortableStateStore', 'loadQualityIndex', 'loadHistory', 'loadProxy',
    'addHistory', 'saveQualityIndex', 'saveProxy', 'LIMPAR HISTÓRICO'
):
    assert required in updated, required
print('Histórico, qualidade e proxy persistentes integrados em data/extractor/.')
