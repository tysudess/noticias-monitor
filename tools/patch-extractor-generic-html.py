from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'desktop/src/main/kotlin/br/com/monitordenoticias/desktop'
ENGINE = BASE / 'ExtractorVideoEngine.kt'
FALLBACK = BASE / 'DirectMediaHtmlFallback.kt'
LIVE_SNAPSHOT = BASE / 'YouTubeLiveSnapshot.kt'
PORTABLE_STATE = BASE / 'ExtractorPortableStateStore.kt'

if not ENGINE.exists() or not FALLBACK.exists() or not LIVE_SNAPSHOT.exists() or not PORTABLE_STATE.exists():
    raise SystemExit('Motor, fallback HTML, YouTubeLiveSnapshot.kt ou ExtractorPortableStateStore.kt ausente.')

src = ENGINE.read_text(encoding='utf-8')

old_route = '                else -> downloadGeneric(cleanUrl, quality, proxy, "vídeo", update)\n'
new_route = '                else -> downloadGenericWithHtmlFallback(cleanUrl, quality, proxy, update)\n'
if old_route in src:
    src = src.replace(old_route, new_route, 1)

if 'private fun downloadGenericWithHtmlFallback(' not in src:
    marker = '    private fun downloadGeneric(\n'
    if marker not in src:
        raise SystemExit('Ponto de inserção do fallback genérico não encontrado.')
    method = '''    private fun downloadGenericWithHtmlFallback(\n        url: String,\n        quality: ExtractorQuality,\n        proxy: String,\n        update: (Int, String) -> Unit\n    ): File {\n        if (DirectMediaHtmlFallback.isDirectMediaUrl(url)) {\n            val isHls = url.contains(".m3u8", true)\n            val extra = if (isHls) listOf("--hls-use-mpegts", "--downloader", "m3u8:native") else emptyList()\n            return runYtDlp(url, quality.selector, proxy, extra, update, ytDlp, null, url)\n        }\n\n        var lastError = "Falha ao baixar vídeo."\n        val first = runCatching { downloadGeneric(url, quality, proxy, "vídeo", update) }\n        if (first.isSuccess) return first.getOrThrow()\n        lastError = friendlyError(first.exceptionOrNull()?.message.orEmpty())\n        if (lastError.contains("DRM", true)) error(lastError)\n\n        val candidates = DirectMediaHtmlFallback.extractCandidates(url, proxy).take(15)\n        for ((index, candidate) in candidates.withIndex()) {\n            val isHls = candidate.contains(".m3u8", true)\n            val extra = if (isHls) listOf("--hls-use-mpegts", "--downloader", "m3u8:native") else emptyList()\n            val attempt = runCatching {\n                update(0, "Mídia encontrada na página: ${index + 1}/${candidates.size}...")\n                runYtDlp(candidate, quality.selector, proxy, extra, update, ytDlp, null, url)\n            }\n            if (attempt.isSuccess) return attempt.getOrThrow()\n            lastError = friendlyError(attempt.exceptionOrNull()?.message.orEmpty())\n            if (lastError.contains("DRM", true)) error(lastError)\n        }\n        error(lastError)\n    }\n\n'''
    src = src.replace(marker, method + marker, 1)

ENGINE.write_text(src, encoding='utf-8')
updated = ENGINE.read_text(encoding='utf-8')
assert 'downloadGenericWithHtmlFallback(cleanUrl' in updated
assert 'DirectMediaHtmlFallback.extractCandidates' in updated
assert 'DirectMediaHtmlFallback.isDirectMediaUrl' in updated
print('Fallback genérico HTML/mídia direta integrado ao Extrator.')

# Live principal: congela a playlist HLS/DVR; fallback: --download-sections até o ponto do clique.
live_patch = ROOT / 'tools/patch-extractor-live-current-point.py'
if not live_patch.exists():
    raise SystemExit('patch-extractor-live-current-point.py ausente.')
runpy.run_path(str(live_patch), run_name='__main__')

# Persistência portable: histórico/qualidade em data/extractor e proxy protegido via DPAPI.
state_patch = ROOT / 'tools/patch-extractor-portable-state.py'
if not state_patch.exists():
    raise SystemExit('patch-extractor-portable-state.py ausente.')
runpy.run_path(str(state_patch), run_name='__main__')

# Seletores de compatibilidade: segunda tentativa exata da referência em todas as qualidades.
compat_patch = ROOT / 'tools/patch-extractor-compat-retry.py'
if not compat_patch.exists():
    raise SystemExit('patch-extractor-compat-retry.py ausente.')
runpy.run_path(str(compat_patch), run_name='__main__')

# Diretórios portáteis sempre relativos ao launcher real do jpackage.
paths_patch = ROOT / 'tools/patch-extractor-portable-paths.py'
if not paths_patch.exists():
    raise SystemExit('patch-extractor-portable-paths.py ausente.')
runpy.run_path(str(paths_patch), run_name='__main__')
