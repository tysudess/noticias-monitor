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
    '    var history by remember { mutableStateOf(portableState.loadHistory()) }\n',
    1,
)

# O Extrator não possui mais proxy próprio. O proxy geral do Monitor será ligado futuramente.
src = src.replace(
    'engine.download(url.trim(), chosen, proxy)',
    'engine.download(url.trim(), chosen, "")',
)
src = src.replace(
    'GloboplayLoginWindow(null, engine, proxy)',
    'GloboplayLoginWindow(null, engine, "")',
)

# Remove o bloco de proxy da tela de Configurações.
proxy_block = '''                    Text("Proxy opcional", color = ExText, fontWeight = FontWeight.SemiBold)\n                    OutlinedTextField(\n                        value = proxy,\n                        onValueChange = { proxy = it },\n                        modifier = Modifier.fillMaxWidth(),\n                        placeholder = { Text("http://usuario:senha@servidor:porta", color = ExMuted) },\n                        singleLine = true,\n                        colors = OutlinedTextFieldDefaults.colors(\n                            focusedTextColor = ExText,\n                            unfocusedTextColor = ExText,\n                            focusedBorderColor = ExPurple,\n                            unfocusedBorderColor = ExBorder\n                        )\n                    )\n                    Text("O mesmo proxy é aplicado ao navegador interno, yt-dlp e fallbacks HTML/HLS.", color = ExMuted)\n\n'''
src = src.replace(proxy_block, '')

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

SCREEN.write_text(src, encoding='utf-8')
updated = SCREEN.read_text(encoding='utf-8')
for required in (
    'ExtractorPortableStateStore', 'loadQualityIndex', 'loadHistory',
    'addHistory', 'saveQualityIndex', 'LIMPAR HISTÓRICO',
    'engine.download(url.trim(), chosen, "")', 'GloboplayLoginWindow(null, engine, "")'
):
    assert required in updated, required
for forbidden in ('Proxy opcional', 'SALVAR PROXY', 'REMOVER PROXY', 'portableState.loadProxy()'):
    assert forbidden not in updated, forbidden
print('Persistência portable integrada; proxy específico do Extrator removido e conexão direta ativada.')
