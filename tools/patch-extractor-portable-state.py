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

# O campo continua responsivo; DPAPI só é acionado quando o usuário pede para salvar.
src = src.replace(
    '                        onValueChange = { proxy = it },\n',
    '                        onValueChange = { proxy = it },\n',
    1,
)
proxy_info = '                    Text("O mesmo proxy é aplicado ao navegador interno, yt-dlp e fallbacks HTML/HLS.", color = ExMuted)\n'
if 'Text("SALVAR PROXY"' not in src and proxy_info in src:
    src = src.replace(
        proxy_info,
        '''                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {\n                        Button(\n                            enabled = !busy,\n                            onClick = {\n                                val result = portableState.saveProxy(proxy)\n                                settingsStatus = result.fold(\n                                    onSuccess = { if (proxy.isBlank()) "Proxy removido." else "Proxy salvo com proteção DPAPI." },\n                                    onFailure = { "Falha ao salvar proxy: ${it.message.orEmpty()}" }\n                                )\n                            },\n                            colors = ButtonDefaults.buttonColors(containerColor = ExBlue)\n                        ) { Text("SALVAR PROXY", fontWeight = FontWeight.Bold) }\n                        OutlinedButton(\n                            enabled = proxy.isNotBlank(),\n                            onClick = {\n                                proxy = ""\n                                portableState.deleteProxy()\n                                settingsStatus = "Proxy removido."\n                            }\n                        ) { Text("REMOVER PROXY") }\n                    }\n                    Text("O mesmo proxy é aplicado ao navegador interno, yt-dlp e fallbacks HTML/HLS. Credenciais são armazenadas com DPAPI do Windows.", color = ExMuted)\n''',
        1,
    )

SCREEN.write_text(src, encoding='utf-8')
updated = SCREEN.read_text(encoding='utf-8')
for required in (
    'ExtractorPortableStateStore', 'loadQualityIndex', 'loadHistory', 'loadProxy',
    'addHistory', 'saveQualityIndex', 'saveProxy', 'LIMPAR HISTÓRICO',
    'SALVAR PROXY', 'REMOVER PROXY'
):
    assert required in updated, required
print('Histórico, qualidade e proxy persistentes integrados em data/extractor/ sem DPAPI a cada tecla.')
