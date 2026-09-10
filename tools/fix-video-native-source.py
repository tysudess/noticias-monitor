from pathlib import Path

path = Path('desktop/src/main/kotlin/br/com/monitordenoticias/desktop/NativeVideoExtractor.kt')
text = path.read_text(encoding='utf-8')
text = text.replace('Icons.Default.MovieEdit', 'Icons.Default.Movie')
path.write_text(text, encoding='utf-8')
print('Native video source compatibility fixes applied.')
