from pathlib import Path
import re

p = Path('desktop/src/main/kotlin/br/com/monitordenoticias/desktop/NativeVideoExtractor.kt')
s = p.read_text(encoding='utf-8')

# Remove the legacy JavaFX preview runtime entirely. The new Editor screen uses FFmpeg + Java Sound.
s, n1 = re.subn(
    r'\nprivate object VexFxRuntime \{.*?(?=\n@Composable\nfun V5NativeVideoExtractorScreen\(\))',
    '\n', s, count=1, flags=re.S
)

# Remove the old editor composable from the extractor file. Download/proxy/Globoplay code is outside this block.
s, n2 = re.subn(
    r'\n@Composable\nprivate fun VexEditorScreen\(state: VexCoreState\) \{.*?(?=\nprivate fun localSourceAtGlobal)',
    '\n', s, count=1, flags=re.S
)

# Remove JavaFX-only imports. Leaving harmless unused Compose imports is intentional to minimize changes around Download.
s = '\n'.join(line for line in s.splitlines() if not line.startswith('import javafx.')) + '\n'

if n1 != 1:
    raise SystemExit(f'legacy JavaFX runtime removal count={n1}')
if n2 != 1:
    raise SystemExit(f'legacy editor removal count={n2}')
if 'VexEditorScreen(' in s or 'javafx.' in s or 'VexFxPreview' in s:
    raise SystemExit('legacy embedded editor/runtime still present')

p.write_text(s, encoding='utf-8')
print('legacy embedded editor removed')
