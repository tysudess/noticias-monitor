from pathlib import Path

p = Path('desktop/src/main/kotlin/br/com/monitordenoticias/desktop/NativeNewsExtractor.kt')
s = p.read_text(encoding='utf-8')
s2 = s.replace('private const val SEP = "\\n" + "#".repeat(70) + "\\n\\n"', 'private val SEP = "\\n" + "#".repeat(70) + "\\n\\n"')
if s2 != s:
    p.write_text(s2, encoding='utf-8')
    print('Fixed non-constant separator declaration.')
else:
    print('No source fix required.')
