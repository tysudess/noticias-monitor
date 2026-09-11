from pathlib import Path
import hashlib
import re

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'desktop/src/main/kotlin/br/com/monitordenoticias/desktop'
DASH = BASE / 'DashboardV5Main.kt'
PDF = BASE / 'PdfEditorScreenV2.kt'
EXTRACTOR = BASE / 'ExtractorVideoScreen.kt'
ENGINE = BASE / 'ExtractorVideoEngine.kt'
SESSION = BASE / 'GloboplaySessionStore.kt'
HTML_FALLBACK = BASE / 'GloboplayHtmlFallback.kt'


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def require_file(path: Path):
    if not path.exists():
        raise SystemExit(f'Arquivo obrigatório ausente: {path.name}')


for required in (PDF, EXTRACTOR, ENGINE, SESSION, HTML_FALLBACK):
    require_file(required)

pdf_before = sha(PDF)
text = DASH.read_text(encoding='utf-8')

old_enum = '    PDF_EDITOR("Editor de PDF", "Monte, reorganize, recorte e exporte PDFs e imagens", Icons.Default.PictureAsPdf)\n'
new_enum = '    PDF_EDITOR("Editor de PDF", "Monte, reorganize, recorte e exporte PDFs e imagens", Icons.Default.PictureAsPdf),\n    EXTRACTOR("Extrator de Vídeos", "Baixe vídeos com o fluxo direto v3.0.1", Icons.Default.Download)\n'
if 'EXTRACTOR("Extrator de Vídeos"' not in text:
    if old_enum not in text:
        raise SystemExit('Ponto de inserção do enum V5Section não encontrado.')
    text = text.replace(old_enum, new_enum, 1)

old_when = '                                V5Section.SETTINGS -> V5SettingsScreen(c, tick)\n                                else -> Unit\n'
new_when = '                                V5Section.SETTINGS -> V5SettingsScreen(c, tick)\n                                V5Section.EXTRACTOR -> ExtractorVideoScreen { section = V5Section.HOME }\n                                else -> Unit\n'
if 'V5Section.EXTRACTOR -> ExtractorVideoScreen' not in text:
    if old_when not in text:
        raise SystemExit('Ponto de inserção da tela do Extrator não encontrado.')
    text = text.replace(old_when, new_when, 1)

DASH.write_text(text, encoding='utf-8')

# Completa Globoplay v3.0.1 no motor sem duplicar patch em execuções futuras.
engine = ENGINE.read_text(encoding='utf-8')
if 'private val globoplaySessionStore' not in engine:
    marker = '    private val activeProcess = AtomicReference<Process?>(null)\n'
    if marker not in engine:
        raise SystemExit('Ponto de inserção do GloboplaySessionStore não encontrado.')
    engine = engine.replace(
        marker,
        marker + '    private val globoplaySessionStore = GloboplaySessionStore(appDir.toFile())\n',
        1
    )

if 'GloboplayHtmlFallback.extractM3u8Candidates' not in engine:
    replacement = '''    private fun downloadGloboplay(\n        url: String,\n        quality: ExtractorQuality,\n        proxy: String,\n        update: (Int, String) -> Unit\n    ): File {\n        val exe = if (ytDlpStable.exists()) ytDlpStable else ytDlp\n        val id = Regex("(?:video|videos|v)/(?:[^0-9]*)([0-9]{5,})", RegexOption.IGNORE_CASE)\n            .find(url)?.groupValues?.getOrNull(1)\n            ?: Regex("([0-9]{6,})").find(url)?.groupValues?.getOrNull(1)\n\n        val runtimeCookie = globoplaySessionStore.createRuntimeCookieFile()\n        try {\n            val common = listOf("--force-ipv4", "--ignore-config", "--no-mtime")\n            var lastError = "Falha ao baixar conteúdo do Globoplay."\n\n            val first = runCatching {\n                update(0, "Globoplay: tentativa 1 — URL original...")\n                runYtDlp(url, quality.selector, proxy, common, update, exe, runtimeCookie, url)\n            }\n            if (first.isSuccess) return first.getOrThrow()\n            lastError = friendlyError(first.exceptionOrNull()?.message.orEmpty())\n            if (lastError.contains("DRM", true)) error(lastError)\n\n            if (!id.isNullOrBlank()) {\n                val second = runCatching {\n                    update(0, "Globoplay: tentativa 2 — globo:$id...")\n                    runYtDlp("globo:$id", quality.selector, proxy, common, update, exe, runtimeCookie, url)\n                }\n                if (second.isSuccess) return second.getOrThrow()\n                lastError = friendlyError(second.exceptionOrNull()?.message.orEmpty())\n                if (lastError.contains("DRM", true)) error(lastError)\n            }\n\n            val hlsExtra = common + listOf("--hls-use-mpegts", "--downloader", "m3u8:native")\n            val hls = runCatching {\n                update(0, "Globoplay: tentativa HLS nativa...")\n                runYtDlp(url, quality.selector, proxy, hlsExtra, update, exe, runtimeCookie, url)\n            }\n            if (hls.isSuccess) return hls.getOrThrow()\n            lastError = friendlyError(hls.exceptionOrNull()?.message.orEmpty())\n            if (lastError.contains("DRM", true)) error(lastError)\n\n            val candidates = GloboplayHtmlFallback.extractM3u8Candidates(url, proxy).take(15)\n            for ((index, candidate) in candidates.withIndex()) {\n                val fallback = runCatching {\n                    update(0, "Globoplay: mídia HLS ${index + 1}/${candidates.size}...")\n                    runYtDlp(candidate, quality.selector, proxy, hlsExtra, update, exe, runtimeCookie, url)\n                }\n                if (fallback.isSuccess) return fallback.getOrThrow()\n                lastError = friendlyError(fallback.exceptionOrNull()?.message.orEmpty())\n                if (lastError.contains("DRM", true)) error(lastError)\n            }\n\n            if (runtimeCookie == null && (lastError.contains("autent", true) || lastError.contains("sessão", true) || lastError.contains("login", true))) {\n                error("Globoplay requer autenticação. Abra Configurações > Globoplay, faça login e use SALVAR SESSÃO E VOLTAR.")\n            }\n            error(lastError)\n        } finally {\n            runCatching { runtimeCookie?.delete() }\n        }\n    }\n\n'''
    pattern = re.compile(r'    private fun downloadGloboplay\(.*?\n    private fun downloadGeneric\(', re.S)
    match = pattern.search(engine)
    if not match:
        raise SystemExit('Bloco downloadGloboplay não encontrado para substituição.')
    engine = engine[:match.start()] + replacement + '    private fun downloadGeneric(' + engine[match.end():]

if 'cookieFile: File? = null' not in engine:
    old_sig = '''        update: (Int, String) -> Unit,\n        executable: File = ytDlp\n    ): File {'''
    new_sig = '''        update: (Int, String) -> Unit,\n        executable: File = ytDlp,\n        cookieFile: File? = null,\n        referer: String? = null\n    ): File {'''
    if old_sig not in engine:
        raise SystemExit('Assinatura runYtDlp não encontrada.')
    engine = engine.replace(old_sig, new_sig, 1)

if 'if (cookieFile != null && cookieFile.exists())' not in engine:
    marker = '        addCommonRuntime(cmd, proxy)\n        cmd += extra\n'
    injection = '''        addCommonRuntime(cmd, proxy)\n        if (cookieFile != null && cookieFile.exists()) cmd += listOf("--cookies", cookieFile.absolutePath)\n        if (!referer.isNullOrBlank()) cmd += listOf("--referer", referer)\n        cmd += extra\n'''
    if marker not in engine:
        raise SystemExit('Ponto de argumentos do runYtDlp não encontrado.')
    engine = engine.replace(marker, injection, 1)

ENGINE.write_text(engine, encoding='utf-8')

if sha(PDF) != pdf_before:
    raise SystemExit('PROTEÇÃO: PdfEditorScreenV2.kt foi alterado durante a integração.')

updated = DASH.read_text(encoding='utf-8')
engine_updated = ENGINE.read_text(encoding='utf-8')
assert 'EXTRACTOR("Extrator de Vídeos"' in updated
assert 'V5Section.EXTRACTOR -> ExtractorVideoScreen' in updated
assert 'PDF_EDITOR("Editor de PDF"' in updated
assert 'GloboplayHtmlFallback.extractM3u8Candidates' in engine_updated
assert 'globoplaySessionStore.createRuntimeCookieFile()' in engine_updated
assert '--hls-use-mpegts' in engine_updated
assert 'm3u8:native' in engine_updated
assert '--cookies' in engine_updated
assert '--referer' in engine_updated
print('Integração segura concluída: aba Extrator + Globoplay v3.0.1; Editor de PDF permaneceu intacto.')
