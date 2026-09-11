$ErrorActionPreference = 'Stop'

$coverPath = 'desktop/src/main/resources/pdf-default-cover.b64'
$raw = (Get-Content $coverPath -Raw).Trim()
$bytes = [Convert]::FromBase64String($raw)
if ($bytes.Length -lt 12) { throw 'Capa padrão embutida inválida.' }
$head = [Text.Encoding]::ASCII.GetString($bytes, 0, 4)
$webp = [Text.Encoding]::ASCII.GetString($bytes, 8, 4)
if ($head -ne 'RIFF' -or $webp -ne 'WEBP') { throw 'Capa padrão não é um WEBP válido.' }
Write-Host "Capa padrão válida: $($bytes.Length) bytes"

$pdf = 'desktop/src/main/kotlin/br/com/monitordenoticias/desktop/PdfEditorScreenV2.kt'
$p = Get-Content $pdf -Raw
$p = $p.Replace('fun PdfEditorScreenV2() { SwingPanel(modifier = Modifier.fillMaxSize(), factory = { PdfEditorV2Panel() }) }', 'fun PdfEditorScreenV2(onExit: () -> Unit = {}) { SwingPanel(modifier = Modifier.fillMaxSize(), factory = { PdfEditorV2Panel(onExit) }) }')
$p = $p.Replace('private class PdfEditorV2Panel : JPanel(BorderLayout()) {', 'private class PdfEditorV2Panel(private val onExit: () -> Unit) : JPanel(BorderLayout()) {')
$p = $p.Replace('right.add(styledSquareButton("⚙") { JOptionPane.showMessageDialog(this,"Configurações do Monitor ficam na aba Configurações.","Editor de PDF",JOptionPane.INFORMATION_MESSAGE) })', 'right.add(styledSquareButton("⚙") { onExit() })')
Set-Content $pdf $p -Encoding UTF8

$dashboard = 'desktop/src/main/kotlin/br/com/monitordenoticias/desktop/DashboardV5Main.kt'
$d = (Get-Content $dashboard -Raw).Replace("`r`n", "`n")
$fn = $d.IndexOf('private fun V5App(c: DesktopControllerV5)')
if ($fn -lt 0) { throw 'V5App não encontrado.' }
$start = $d.IndexOf('    Column(Modifier.fillMaxSize().background(V5Bg)) {', $fn)
if ($start -lt 0) { throw 'Bloco principal do V5App não encontrado.' }
$marker = "`n}`n`n@Composable`nprivate fun V5Sidebar"
$end = $d.IndexOf($marker, $start)
if ($end -lt 0) { throw 'Fim do V5App não encontrado.' }

$newBlock = @'
    if (section == V5Section.PDF_EDITOR) {
        PdfEditorScreenV2 { section = V5Section.HOME }
    } else {
        Column(Modifier.fillMaxSize().background(V5Bg)) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                V5Sidebar(section, { section = it }, c, tick)
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (section == V5Section.HOME) {
                        V5HomeHeader(c, { section = it }, tick)
                        V5Home(c, { section = it }, tick)
                    } else {
                        V5PageHeader(section, c, tick)
                        Box(
                            Modifier.weight(1f).fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 10.dp)
                        ) {
                            when (section) {
                                V5Section.NEWS -> V5NewsScreen(c, tick)
                                V5Section.VIDEOS -> V5VideosScreen(c, tick)
                                V5Section.DEMANDS -> V5DemandsScreen(c, tick)
                                V5Section.SOURCES -> V5SourcesScreen(c, tick)
                                V5Section.HISTORY -> V5HistoryScreen(c, tick)
                                V5Section.TERMS -> V5TermsScreen(c, tick)
                                V5Section.STOP -> V5StopScreen(c, tick)
                                V5Section.SETTINGS -> V5SettingsScreen(c, tick)
                                else -> Unit
                            }
                        }
                    }
                }
            }
            V5Footer(c, tick)
        }
    }
'@

$d = $d.Substring(0, $start) + $newBlock.TrimEnd() + $d.Substring($end)
Set-Content $dashboard $d -Encoding UTF8
Write-Host 'Integração V2 aplicada.'
