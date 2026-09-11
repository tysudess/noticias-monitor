from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'desktop/src/main/kotlin/br/com/monitordenoticias/desktop'
ENGINE = BASE / 'ExtractorVideoEngine.kt'
SNAPSHOT = BASE / 'YouTubeLiveSnapshot.kt'
if not ENGINE.exists() or not SNAPSHOT.exists():
    raise SystemExit('ExtractorVideoEngine.kt ou YouTubeLiveSnapshot.kt ausente.')

src = ENGINE.read_text(encoding='utf-8')

src = src.replace(
    'downloadYoutubeLive(url, quality, proxy, update)',
    'downloadYoutubeLive(url, quality, proxy, probe.timestamp, update)'
)

old_sig = '''    private fun downloadYoutubeLive(\n        url: String,\n        quality: ExtractorQuality,\n        proxy: String,\n        update: (Int, String) -> Unit\n    ): File {'''
new_sig = '''    private fun downloadYoutubeLive(\n        url: String,\n        quality: ExtractorQuality,\n        proxy: String,\n        liveStartTimestamp: Long?,\n        update: (Int, String) -> Unit\n    ): File {'''
if old_sig in src:
    src = src.replace(old_sig, new_sig, 1)

old_body = '''        val selector = quality.maxHeight?.let {\n            "bv*[height<=$it][ext=mp4]+ba[ext=m4a]/b[height<=$it]/best[height<=$it]"\n        } ?: quality.selector\n        return runYtDlp(url, selector, proxy, listOf("--live-from-start", "--hls-use-mpegts"), update)\n    }'''
new_body = '''        val wantedHeight = quality.maxHeight ?: 2160\n        val snapshot = YouTubeLiveSnapshot.download(\n            YouTubeLiveSnapshot.Request(\n                url = url,\n                wantedHeight = wantedHeight,\n                proxy = proxy,\n                knownStartTimestamp = liveStartTimestamp,\n                ytDlp = ytDlp,\n                ffmpeg = ffmpeg,\n                binDir = binDir,\n                deno = deno,\n                appDir = appDir.toFile(),\n                videosDir = videosDir\n            ),\n            onProcess = { activeProcess.set(it) },\n            update = update\n        )\n        if (snapshot.isSuccess) {\n            update(100, "Live salva do início até o ponto em que o download foi iniciado.")\n            return snapshot.getOrThrow()\n        }\n\n        update(0, "O snapshot HLS não ficou disponível. Tentando modo compatível do yt-dlp…")\n        val selector = quality.maxHeight?.let {\n            "b[height<=$it]/b"\n        } ?: "b"\n        val nowSeconds = System.currentTimeMillis() / 1000L\n        val targetSeconds = liveStartTimestamp?.takeIf { it > 0L && it < nowSeconds }?.let { nowSeconds - it }\n        val extra = mutableListOf("--live-from-start", "--hls-use-mpegts")\n        if (targetSeconds != null && targetSeconds > 0L) {\n            val end = formatLiveTime(targetSeconds)\n            update(0, "🔴 Live detectada. Baixando do início até o ponto atual ($end)...")\n            extra += listOf(\n                "--download-sections", "*00:00:00-$end",\n                "--force-keyframes-at-cuts",\n                "--concurrent-fragments", "4"\n            )\n        } else {\n            update(0, "🔴 Live detectada. Baixando do início até o ponto atual...")\n        }\n        return runYtDlp(url, selector, proxy, extra, update)\n    }\n\n    private fun formatLiveTime(seconds: Long): String {\n        val safe = seconds.coerceAtLeast(0L)\n        val hours = safe / 3600L\n        val minutes = (safe % 3600L) / 60L\n        val secs = safe % 60L\n        return "%02d:%02d:%02d".format(Locale.ROOT, hours, minutes, secs)\n    }'''
if old_body in src:
    src = src.replace(old_body, new_body, 1)

# Caso o fallback temporal já tenha sido aplicado em uma execução anterior,
# injeta o snapshot HLS antes dele de maneira idempotente.
if 'YouTubeLiveSnapshot.download(' not in src:
    marker = '''        val selector = quality.maxHeight?.let {\n            "bv*[height<=$it][ext=mp4]+ba[ext=m4a]/b[height<=$it]/best[height<=$it]"\n        } ?: quality.selector\n        val nowSeconds = System.currentTimeMillis() / 1000L\n'''
    replacement = '''        val wantedHeight = quality.maxHeight ?: 2160\n        val snapshot = YouTubeLiveSnapshot.download(\n            YouTubeLiveSnapshot.Request(\n                url = url, wantedHeight = wantedHeight, proxy = proxy, knownStartTimestamp = liveStartTimestamp,\n                ytDlp = ytDlp, ffmpeg = ffmpeg, binDir = binDir, deno = deno, appDir = appDir.toFile(), videosDir = videosDir\n            ),\n            onProcess = { activeProcess.set(it) },\n            update = update\n        )\n        if (snapshot.isSuccess) {\n            update(100, "Live salva do início até o ponto em que o download foi iniciado.")\n            return snapshot.getOrThrow()\n        }\n        update(0, "O snapshot HLS não ficou disponível. Tentando modo compatível do yt-dlp…")\n        val selector = quality.maxHeight?.let { "b[height<=$it]/b" } ?: "b"\n        val nowSeconds = System.currentTimeMillis() / 1000L\n'''
    if marker in src:
        src = src.replace(marker, replacement, 1)

ENGINE.write_text(src, encoding='utf-8')
updated = ENGINE.read_text(encoding='utf-8')
assert 'probe.timestamp' in updated
assert 'YouTubeLiveSnapshot.download' in updated
assert '--download-sections' in updated
assert '--force-keyframes-at-cuts' in updated
assert '--concurrent-fragments' in updated
assert 'formatLiveTime' in updated
print('YouTube Live: snapshot HLS congelado + fallback temporal integrados.')
