from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ENGINE = ROOT / 'desktop/src/main/kotlin/br/com/monitordenoticias/desktop/ExtractorVideoEngine.kt'
if not ENGINE.exists():
    raise SystemExit('ExtractorVideoEngine.kt ausente.')

src = ENGINE.read_text(encoding='utf-8')
old = 'val live = json.optBoolean("is_live", false) || liveStatus == "is_live" || liveStatus == "post_live"'
new = 'val live = json.optBoolean("is_live", false) || liveStatus == "is_live"'
if old in src:
    src = src.replace(old, new, 1)
elif new not in src:
    raise SystemExit('Expressão de detecção de live do YouTube não encontrada.')

ENGINE.write_text(src, encoding='utf-8')
updated = ENGINE.read_text(encoding='utf-8')
assert 'liveStatus == "post_live"' not in updated
assert 'json.optBoolean("is_live", false) || liveStatus == "is_live"' in updated
print('YouTube post_live segue fluxo normal; somente live ativa usa snapshot DVR.')
