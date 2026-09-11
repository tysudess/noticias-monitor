from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
ENGINE = ROOT / 'desktop/src/main/kotlin/br/com/monitordenoticias/desktop/ExtractorVideoEngine.kt'
if not ENGINE.exists():
    raise SystemExit('ExtractorVideoEngine.kt ausente.')

src = ENGINE.read_text(encoding='utf-8')
pattern = re.compile(r'    private fun downloadR7\(.*?\n    private fun downloadGeneric(?:WithHtmlFallback)?\(', re.S)
match = pattern.search(src)
if not match:
    raise SystemExit('Bloco downloadR7 não encontrado.')

replacement = '''    private fun downloadR7(\n        url: String,\n        quality: ExtractorQuality,\n        proxy: String,\n        update: (Int, String) -> Unit\n    ): File {\n        var lastError = "Falha ao baixar conteúdo do R7/Record."\n        val r7Base = listOf("--force-ipv4", "--no-mtime")\n\n        val direct = runCatching {\n            update(0, "R7/Record: tentando a página diretamente...")\n            runYtDlpQuality(url, quality, proxy, r7Base, update, ytDlp, null, url)\n        }\n        if (direct.isSuccess) return direct.getOrThrow()\n        lastError = friendlyError(direct.exceptionOrNull()?.message.orEmpty())\n\n        // Igual ao fluxo de referência: um diagnóstico genérico/DRM na página da\n        // matéria não prova que os candidatos de mídia estejam protegidos.\n        // Só concluímos depois de inspecionar/testar os candidatos encontrados.\n        update(0, "R7/Record: método principal falhou. Procurando vídeos dentro da página...")\n        val candidates = R7HtmlFallback.extractMediaCandidates(url, proxy).take(15)\n        for ((index, candidate) in candidates.withIndex()) {\n            val isHls = candidate.contains(".m3u8", true)\n            val extra = if (isHls) {\n                r7Base + listOf("--hls-use-mpegts", "--downloader", "m3u8:native")\n            } else {\n                r7Base\n            }\n            val attempt = runCatching {\n                update(0, "R7/Record: mídia ${index + 1}/${candidates.size}...")\n                runYtDlpQuality(candidate, quality, proxy, extra, update, ytDlp, null, url)\n            }\n            if (attempt.isSuccess) return attempt.getOrThrow()\n            lastError = friendlyError(attempt.exceptionOrNull()?.message.orEmpty())\n        }\n        error(lastError)\n    }\n\n'''

suffix = '    private fun downloadGenericWithHtmlFallback(' if 'private fun downloadGenericWithHtmlFallback(' in match.group(0) else '    private fun downloadGeneric('
src = src[:match.start()] + replacement + suffix + src[match.end():]
ENGINE.write_text(src, encoding='utf-8')
updated = ENGINE.read_text(encoding='utf-8')
assert 'val r7Base = listOf("--force-ipv4", "--no-mtime")' in updated
assert 'runYtDlpQuality(url, quality, proxy, r7Base' in updated
assert 'método principal falhou. Procurando vídeos dentro da página' in updated
print('R7/Record alinhado à referência: IPv4 + no-mtime + busca de candidatos antes do diagnóstico final.')
