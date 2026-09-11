from pathlib import Path

p = Path('desktop/src/main/kotlin/br/com/monitordenoticias/desktop/EmbeddedPdfEditor.kt')
s = p.read_text(encoding='utf-8')
s = s.replace('import com.sun.jna.platform.win32.Kernel32\n', '')
s = s.replace('File(EmbeddedPdfEditorScreen::class.java.protectionDomain.codeSource.location.toURI()).parentFile', 'File(PdfEditorNativeHost::class.java.protectionDomain.codeSource.location.toURI()).parentFile')
p.write_text(s, encoding='utf-8')
print('Embedded PDF host source normalized')
