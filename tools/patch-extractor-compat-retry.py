from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ENGINE = ROOT / 'desktop/src/main/kotlin/br/com/monitordenoticias/desktop/ExtractorVideoEngine.kt'
if not ENGINE.exists():
    raise SystemExit('ExtractorVideoEngine.kt ausente.')

src = ENGINE.read_text(encoding='utf-8')

if 'private fun runYtDlpQuality(' not in src:
    marker = '    private fun runYtDlp(\n'
    if marker not in src:
        raise SystemExit('runYtDlp não encontrado para inserir fallback compatível.')
    helper = '''    private fun runYtDlpQuality(\n        url: String,\n        quality: ExtractorQuality,\n        proxy: String,\n        extra: List<String>,\n        update: (Int, String) -> Unit,\n        executable: File = ytDlp,\n        cookieFile: File? = null,\n        referer: String? = null\n    ): File {\n        val primary = runCatching {\n            runYtDlp(url, quality.selector, proxy, extra, update, executable, cookieFile, referer)\n        }\n        if (primary.isSuccess) return primary.getOrThrow()\n        if (quality.compat == quality.selector) throw primary.exceptionOrNull() ?: IllegalStateException("Falha no formato principal.")\n        update(0, "Formato principal indisponível. Tentando modo compatível em ${quality.label}...")\n        return runYtDlp(url, quality.compat, proxy, extra, update, executable, cookieFile, referer)\n    }\n\n'''
    src = src.replace(marker, helper + marker, 1)

replacements = {
    'return runYtDlp(url, quality.selector, proxy, emptyList(), update)':
        'return runYtDlpQuality(url, quality, proxy, emptyList(), update)',
    'runYtDlp(url, quality.selector, proxy, common, update, exe, runtimeCookie, url)':
        'runYtDlpQuality(url, quality, proxy, common, update, exe, runtimeCookie, url)',
    'runYtDlp("globo:$id", quality.selector, proxy, common, update, exe, runtimeCookie, url)':
        'runYtDlpQuality("globo:$id", quality, proxy, common, update, exe, runtimeCookie, url)',
    'runYtDlp(url, quality.selector, proxy, hlsExtra, update, exe, runtimeCookie, url)':
        'runYtDlpQuality(url, quality, proxy, hlsExtra, update, exe, runtimeCookie, url)',
    'runYtDlp(candidate, quality.selector, proxy, hlsExtra, update, exe, runtimeCookie, url)':
        'runYtDlpQuality(candidate, quality, proxy, hlsExtra, update, exe, runtimeCookie, url)',
    'runYtDlp(url, quality.selector, proxy, emptyList(), update, ytDlp, null, url)':
        'runYtDlpQuality(url, quality, proxy, emptyList(), update, ytDlp, null, url)',
    'runYtDlp(candidate, quality.selector, proxy, extra, update, ytDlp, null, url)':
        'runYtDlpQuality(candidate, quality, proxy, extra, update, ytDlp, null, url)',
    'return runYtDlp(url, quality.selector, proxy, extra, update, ytDlp, null, url)':
        'return runYtDlpQuality(url, quality, proxy, extra, update, ytDlp, null, url)',
}
for old, new in replacements.items():
    src = src.replace(old, new)

ENGINE.write_text(src, encoding='utf-8')
updated = ENGINE.read_text(encoding='utf-8')
assert 'private fun runYtDlpQuality(' in updated
assert 'quality.compat' in updated
assert 'Formato principal indisponível. Tentando modo compatível' in updated
print('Retry automático com seletores compatíveis integrado às rotas do Extrator.')
