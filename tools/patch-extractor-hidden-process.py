from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'desktop/src/main/kotlin/br/com/monitordenoticias/desktop'
ENGINE = BASE / 'ExtractorVideoEngine.kt'
HIDDEN = BASE / 'HiddenWindowsProcess.kt'

if not ENGINE.exists() or not HIDDEN.exists():
    raise SystemExit('ExtractorVideoEngine.kt ou HiddenWindowsProcess.kt ausente.')

src = ENGINE.read_text(encoding='utf-8')

old_cancel = '    fun cancel() {\n        activeProcess.getAndSet(null)?.let { runCatching { it.destroyForcibly() } }\n    }'
new_cancel = '    fun cancel() {\n        HiddenWindowsProcess.destroyTree(activeProcess.getAndSet(null))\n    }'
if old_cancel in src:
    src = src.replace(old_cancel, new_cancel, 1)

old_start = 'ProcessBuilder(cmd).directory(appDir.toFile()).redirectErrorStream(true).start()'
src = src.replace(old_start, 'HiddenWindowsProcess.start(cmd, appDir.toFile())')

# Em timeout, encerra também filhos (yt-dlp/ffmpeg/ffprobe) em vez de só o wrapper.
src = src.replace('            process.destroyForcibly()\n            readerThread.join(1000)', '            HiddenWindowsProcess.destroyTree(process)\n            readerThread.join(1000)')

ENGINE.write_text(src, encoding='utf-8')

updated = ENGINE.read_text(encoding='utf-8')
assert 'HiddenWindowsProcess.destroyTree(activeProcess.getAndSet(null))' in updated
assert 'HiddenWindowsProcess.start(cmd, appDir.toFile())' in updated
print('Subprocessos do Extrator configurados para execução oculta no Windows.')
