from pathlib import Path

ROOT = Path('.')


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, got {count}")
    return text.replace(old, new, 1)


def replace_between(text: str, start: str, end: str, replacement: str, label: str) -> str:
    i = text.find(start)
    if i < 0:
        raise SystemExit(f"{label}: start marker not found")
    j = text.find(end, i + len(start))
    if j < 0:
        raise SystemExit(f"{label}: end marker not found")
    return text[:i] + replacement.rstrip() + "\n\n" + text[j:]


# ---------------------------------------------------------------------------
# 1. Desktop news terms: Windows uses DesktopNewsDb, not Android NewsDb.
#    Seed the canonical terms here too so the NEWS tab receives the same list.
# ---------------------------------------------------------------------------
desktop_db_path = ROOT / 'desktop/src/main/kotlin/br/com/monitordenoticias/android/DesktopNewsDb.kt'
desktop_db = desktop_db_path.read_text(encoding='utf-8')
old_seed = '''    private fun seedTerms() {
        val defaults = listOf(
            "Marinha do Brasil","Capitania dos Portos","Distrito Naval","NAM Atlântico",
            "Cisne Branco","Fragata Marinha do Brasil","Navio-Patrulha Marinha","Programa Nuclear da Marinha"
        )
        connection.prepareStatement("INSERT OR IGNORE INTO terms(term) VALUES(?)").use { ps ->
            defaults.forEach { value -> ps.setString(1, value); ps.addBatch() }
            ps.executeBatch()
        }
    }'''
new_seed = '''    private fun seedTerms() {
        connection.prepareStatement("INSERT OR IGNORE INTO terms(term) VALUES(?)").use { ps ->
            DEFAULT_MONITOR_TERMS.forEach { value ->
                ps.setString(1, value)
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }'''
desktop_db = replace_once(desktop_db, old_seed, new_seed, 'Desktop News default terms')
desktop_db_path.write_text(desktop_db, encoding='utf-8')


# ---------------------------------------------------------------------------
# 2. State sources: five strong LOCAL/REGIONAL publishers per UF.
#    National networks such as G1 remain in the national catalogue so that a
#    generic publisher label is not incorrectly duplicated across 27 states.
# ---------------------------------------------------------------------------
source_path = ROOT / 'app/src/main/java/br/com/monitordenoticias/android/SourceCatalog.kt'
source = source_path.read_text(encoding='utf-8')

by_state = '''    val byState = listOf(
        // Norte
        state("AC", "Acre", "Norte", "ac24horas", "AC24Horas"),
        state("AC", "Acre", "Norte", "ContilNet", "ContilNet Notícias"),
        state("AC", "Acre", "Norte", "Folha do Acre"),
        state("AC", "Acre", "Norte", "O Rio Branco", "Jornal O Rio Branco"),
        state("AC", "Acre", "Norte", "Ecos da Notícia", "Ecos da Noticia"),

        state("AP", "Amapá", "Norte", "Diário do Amapá", "Diario do Amapa"),
        state("AP", "Amapá", "Norte", "SelesNafes.com", "Seles Nafes"),
        state("AP", "Amapá", "Norte", "A Gazeta do Amapá", "Jornal A Gazeta do Amapá"),
        state("AP", "Amapá", "Norte", "Amapá Digital", "Amapa Digital"),
        state("AP", "Amapá", "Norte", "Portal do Amapá", "Portal Amapá", "Portal Amapa"),

        state("AM", "Amazonas", "Norte", "Portal do Holanda"),
        state("AM", "Amazonas", "Norte", "A Crítica", "A Critica"),
        state("AM", "Amazonas", "Norte", "D24AM", "Diário do Amazonas", "Diario do Amazonas"),
        state("AM", "Amazonas", "Norte", "Amazonas Atual"),
        state("AM", "Amazonas", "Norte", "Em Tempo", "Amazonas Em Tempo"),

        state("PA", "Pará", "Norte", "DOL", "Diário Online", "Diario Online"),
        state("PA", "Pará", "Norte", "O Liberal", "Oliberal.com"),
        state("PA", "Pará", "Norte", "Diário do Pará", "Diario do Para"),
        state("PA", "Pará", "Norte", "Roma News", "RomaNews"),
        state("PA", "Pará", "Norte", "Portal Canaã", "Portal Canaa"),

        state("RO", "Rondônia", "Norte", "Rondoniaovivo", "Rondônia ao Vivo"),
        state("RO", "Rondônia", "Norte", "Rondoniagora", "Rondônia Agora"),
        state("RO", "Rondônia", "Norte", "Portal de Rondônia", "Portal de Rondonia"),
        state("RO", "Rondônia", "Norte", "Rondonotícias", "Rondonoticias"),
        state("RO", "Rondônia", "Norte", "EuIdeal", "Eu Ideal"),

        state("RR", "Roraima", "Norte", "Folha BV", "Folha de Boa Vista"),
        state("RR", "Roraima", "Norte", "Roraima em Tempo"),
        state("RR", "Roraima", "Norte", "Roraima 1", "Roraima1"),
        state("RR", "Roraima", "Norte", "Portal Norte Roraima", "Portal Norte"),
        state("RR", "Roraima", "Norte", "Portal Roraima", "Roraima Portal"),

        state("TO", "Tocantins", "Norte", "Sou de Palmas"),
        state("TO", "Tocantins", "Norte", "AF Notícias", "AF Noticias"),
        state("TO", "Tocantins", "Norte", "Gazeta do Cerrado"),
        state("TO", "Tocantins", "Norte", "Agência Tocantins", "Agencia Tocantins"),
        state("TO", "Tocantins", "Norte", "T1 Notícias", "T1 Noticias"),

        // Nordeste
        state("AL", "Alagoas", "Nordeste", "TNH1"),
        state("AL", "Alagoas", "Nordeste", "Gazeta de Alagoas", "GazetaWeb"),
        state("AL", "Alagoas", "Nordeste", "Cada Minuto"),
        state("AL", "Alagoas", "Nordeste", "7Segundos", "7 Segundos"),
        state("AL", "Alagoas", "Nordeste", "Tribuna Hoje", "Tribuna Independente"),

        state("BA", "Bahia", "Nordeste", "BNews"),
        state("BA", "Bahia", "Nordeste", "Bahia Notícias", "Bahia Noticias"),
        state("BA", "Bahia", "Nordeste", "Correio 24 Horas", "Correio da Bahia", "Correio*"),
        state("BA", "Bahia", "Nordeste", "iBahia", "IBahia"),
        state("BA", "Bahia", "Nordeste", "A Tarde"),

        state("CE", "Ceará", "Nordeste", "O Povo"),
        state("CE", "Ceará", "Nordeste", "Diário do Nordeste", "Diario do Nordeste"),
        state("CE", "Ceará", "Nordeste", "GCMAIS", "GC Mais"),
        state("CE", "Ceará", "Nordeste", "CN7"),
        state("CE", "Ceará", "Nordeste", "Ceará Agora", "Ceara Agora"),

        state("MA", "Maranhão", "Nordeste", "Imirante"),
        state("MA", "Maranhão", "Nordeste", "O Imparcial"),
        state("MA", "Maranhão", "Nordeste", "Jornal Pequeno"),
        state("MA", "Maranhão", "Nordeste", "Atual7"),
        state("MA", "Maranhão", "Nordeste", "Marrapá", "Marrapa"),

        state("PB", "Paraíba", "Nordeste", "Portal Correio"),
        state("PB", "Paraíba", "Nordeste", "Jornal da Paraíba", "Jornal da Paraiba"),
        state("PB", "Paraíba", "Nordeste", "ClickPB"),
        state("PB", "Paraíba", "Nordeste", "WSCOM"),
        state("PB", "Paraíba", "Nordeste", "Polêmica Paraíba", "Polemica Paraiba"),

        state("PE", "Pernambuco", "Nordeste", "Jornal do Commercio", "JC Online", "JCPE", "JC PE"),
        state("PE", "Pernambuco", "Nordeste", "Diario de Pernambuco", "Diário de Pernambuco"),
        state("PE", "Pernambuco", "Nordeste", "Folha de Pernambuco"),
        state("PE", "Pernambuco", "Nordeste", "NE10"),
        state("PE", "Pernambuco", "Nordeste", "LeiaJá", "LeiaJa"),

        state("PI", "Piauí", "Nordeste", "Meio Norte"),
        state("PI", "Piauí", "Nordeste", "Cidade Verde"),
        state("PI", "Piauí", "Nordeste", "GP1"),
        state("PI", "Piauí", "Nordeste", "180graus", "180 Graus"),
        state("PI", "Piauí", "Nordeste", "Lupa1", "Lupa 1"),

        state("RN", "Rio Grande do Norte", "Nordeste", "Tribuna do Norte"),
        state("RN", "Rio Grande do Norte", "Nordeste", "Blog do BG", "BG"),
        state("RN", "Rio Grande do Norte", "Nordeste", "Agora RN"),
        state("RN", "Rio Grande do Norte", "Nordeste", "Via Certa Natal", "Via Certa"),
        state("RN", "Rio Grande do Norte", "Nordeste", "Saiba Mais", "Agência Saiba Mais"),

        state("SE", "Sergipe", "Nordeste", "Infonet"),
        state("SE", "Sergipe", "Nordeste", "Jornal da Cidade", "Jornal da Cidade Sergipe"),
        state("SE", "Sergipe", "Nordeste", "F5 News", "F5News"),
        state("SE", "Sergipe", "Nordeste", "FaxAju"),
        state("SE", "Sergipe", "Nordeste", "NE Notícias", "NE Noticias"),

        // Centro-Oeste
        state("DF", "Distrito Federal", "Centro-Oeste", "Metrópoles", "Metropoles"),
        state("DF", "Distrito Federal", "Centro-Oeste", "Correio Braziliense"),
        state("DF", "Distrito Federal", "Centro-Oeste", "Jornal de Brasília", "Jornal de Brasilia"),
        state("DF", "Distrito Federal", "Centro-Oeste", "GPS Brasília", "GPS Brasilia"),
        state("DF", "Distrito Federal", "Centro-Oeste", "Brasília Capital", "Brasilia Capital"),

        state("GO", "Goiás", "Centro-Oeste", "Portal 6", "Portal6"),
        state("GO", "Goiás", "Centro-Oeste", "O Popular"),
        state("GO", "Goiás", "Centro-Oeste", "Jornal Opção", "Jornal Opcao"),
        state("GO", "Goiás", "Centro-Oeste", "Mais Goiás", "Mais Goias"),
        state("GO", "Goiás", "Centro-Oeste", "Diário de Goiás", "Diario de Goias"),

        state("MT", "Mato Grosso", "Centro-Oeste", "MidiaNews"),
        state("MT", "Mato Grosso", "Centro-Oeste", "Olhar Direto"),
        state("MT", "Mato Grosso", "Centro-Oeste", "Gazeta Digital"),
        state("MT", "Mato Grosso", "Centro-Oeste", "RDNews"),
        state("MT", "Mato Grosso", "Centro-Oeste", "HiperNotícias", "HiperNoticias"),

        state("MS", "Mato Grosso do Sul", "Centro-Oeste", "Campo Grande News"),
        state("MS", "Mato Grosso do Sul", "Centro-Oeste", "Midiamax"),
        state("MS", "Mato Grosso do Sul", "Centro-Oeste", "TopMídiaNews", "Top Midia News", "TopMídia News"),
        state("MS", "Mato Grosso do Sul", "Centro-Oeste", "O Jacaré", "O Jacare"),
        state("MS", "Mato Grosso do Sul", "Centro-Oeste", "Correio do Estado"),

        // Sudeste
        state("ES", "Espírito Santo", "Sudeste", "Folha Vitória", "Folha Vitoria"),
        state("ES", "Espírito Santo", "Sudeste", "A Gazeta", "A Gazeta ES"),
        state("ES", "Espírito Santo", "Sudeste", "Tribuna Online", "A Tribuna ES", "A Tribuna Espírito Santo"),
        state("ES", "Espírito Santo", "Sudeste", "ES Hoje"),
        state("ES", "Espírito Santo", "Sudeste", "Século Diário", "Seculo Diario"),

        state("MG", "Minas Gerais", "Sudeste", "Itatiaia", "Rádio Itatiaia"),
        state("MG", "Minas Gerais", "Sudeste", "Estado de Minas", "EM.com.br"),
        state("MG", "Minas Gerais", "Sudeste", "O Tempo"),
        state("MG", "Minas Gerais", "Sudeste", "Hoje em Dia"),
        state("MG", "Minas Gerais", "Sudeste", "BHAZ", "Bhaz"),

        state("RJ", "Rio de Janeiro", "Sudeste", "O Globo", "oglobo"),
        state("RJ", "Rio de Janeiro", "Sudeste", "O Dia", "O Dia RJ"),
        state("RJ", "Rio de Janeiro", "Sudeste", "Extra", "Extra Online"),
        state("RJ", "Rio de Janeiro", "Sudeste", "Jornal do Brasil", "JB"),
        state("RJ", "Rio de Janeiro", "Sudeste", "Diário do Rio", "Diario do Rio"),

        state("SP", "São Paulo", "Sudeste", "Folha de S.Paulo", "Folha de São Paulo", "Folha"),
        state("SP", "São Paulo", "Sudeste", "Estadão", "O Estado de S. Paulo"),
        state("SP", "São Paulo", "Sudeste", "Valor Econômico", "Valor"),
        state("SP", "São Paulo", "Sudeste", "Diário de S.Paulo", "Diario de S.Paulo"),
        state("SP", "São Paulo", "Sudeste", "A Tribuna", "A Tribuna de Santos", "atribuna.com.br"),

        // Sul
        state("PR", "Paraná", "Sul", "Banda B"),
        state("PR", "Paraná", "Sul", "aRede", "Portal aRede"),
        state("PR", "Paraná", "Sul", "Tribuna do Paraná", "Tribuna do Parana"),
        state("PR", "Paraná", "Sul", "Bem Paraná", "Bem Parana"),
        state("PR", "Paraná", "Sul", "Gazeta do Povo"),

        state("SC", "Santa Catarina", "Sul", "ND Mais", "ND+"),
        state("SC", "Santa Catarina", "Sul", "NSC Total", "NSC"),
        state("SC", "Santa Catarina", "Sul", "SCC10", "SCC"),
        state("SC", "Santa Catarina", "Sul", "O Município", "O Municipio"),
        state("SC", "Santa Catarina", "Sul", "Oeste Mais", "OesteMais"),

        state("RS", "Rio Grande do Sul", "Sul", "GZH", "Zero Hora", "GaúchaZH", "GauchaZH"),
        state("RS", "Rio Grande do Sul", "Sul", "Correio do Povo"),
        state("RS", "Rio Grande do Sul", "Sul", "Jornal do Comércio", "Jornal do Comercio RS"),
        state("RS", "Rio Grande do Sul", "Sul", "Sul21"),
        state("RS", "Rio Grande do Sul", "Sul", "O Sul", "Jornal O Sul")
    )'''

source = replace_between(
    source,
    '    val byState = listOf(',
    '    val specialized = listOf(',
    by_state,
    'Five-per-state catalogue'
)

specialized = '''    val specialized = listOf(
        MediaSource(
            id = "especializada-defesatv",
            name = "DefesaTV",
            region = NATIONAL_REGION,
            state = "BR",
            stateName = "Brasil",
            group = "Mídia especializada",
            aliases = listOf("Defesa TV", "defesa.tv.br")
        ),
        MediaSource(
            id = "especializada-poder-naval",
            name = "Poder Naval",
            region = NATIONAL_REGION,
            state = "BR",
            stateName = "Brasil",
            group = "Mídia especializada",
            aliases = listOf("PoderNaval", "naval.com.br")
        ),
        MediaSource(
            id = "especializada-poder-aereo",
            name = "Poder Aéreo",
            region = NATIONAL_REGION,
            state = "BR",
            stateName = "Brasil",
            group = "Mídia especializada",
            aliases = listOf("Poder Aereo", "aereo.jor.br")
        ),
        MediaSource(
            id = "especializada-defesa-em-foco",
            name = "Defesa em Foco",
            region = NATIONAL_REGION,
            state = "BR",
            stateName = "Brasil",
            group = "Mídia especializada",
            aliases = listOf("defesaemfoco.com.br")
        ),
        MediaSource(
            id = "especializada-gbn-news",
            name = "GBN News",
            region = NATIONAL_REGION,
            state = "BR",
            stateName = "Brasil",
            group = "Mídia especializada",
            aliases = listOf("GBN Defense", "GBNNews", "gbnnews.com.br")
        ),
        MediaSource(
            id = "especializada-zona-militar",
            name = "Zona Militar",
            region = NATIONAL_REGION,
            state = "BR",
            stateName = "Brasil",
            group = "Mídia especializada",
            aliases = listOf("Zona-Militar", "zona-militar.com")
        ),
        MediaSource(
            id = "especializada-tecnologia-defesa",
            name = "Tecnodefesa",
            region = NATIONAL_REGION,
            state = "BR",
            stateName = "Brasil",
            group = "Mídia especializada",
            aliases = listOf("Tecnologia & Defesa", "Tecnologia e Defesa", "tecnodefesa.com.br")
        ),
        MediaSource(
            id = "especializada-agencia-marinha",
            name = "Agência Marinha de Notícias",
            region = NATIONAL_REGION,
            state = "BR",
            stateName = "Brasil",
            group = "Mídia especializada",
            aliases = listOf("Agência Marinha", "agencia.marinha.mil.br")
        ),
        MediaSource(
            id = "especializada-forcas-terrestres",
            name = "Forças Terrestres",
            region = NATIONAL_REGION,
            state = "BR",
            stateName = "Brasil",
            group = "Mídia especializada",
            aliases = listOf("Forcas Terrestres", "forte.jor.br")
        ),
        MediaSource(
            id = "especializada-defesanet",
            name = "DefesaNet",
            region = NATIONAL_REGION,
            state = "BR",
            stateName = "Brasil",
            group = "Mídia especializada",
            aliases = listOf("Defesa Net", "defesanet.com.br")
        ),
        MediaSource(
            id = "especializada-agencia-forca-aerea",
            name = "Agência Força Aérea",
            region = NATIONAL_REGION,
            state = "BR",
            stateName = "Brasil",
            group = "Mídia especializada",
            aliases = listOf("Agência FAB", "Agencia Forca Aerea", "fab.mil.br")
        )
    )'''

source = replace_between(
    source,
    '    val specialized = listOf(',
    '    val all: List<MediaSource>',
    specialized,
    'Specialized media catalogue'
)
source_path.write_text(source, encoding='utf-8')


# ---------------------------------------------------------------------------
# 3. Windows runtime icon. The MSI/EXE already has monitor-icon.ico, but the
#    Compose Window had no icon and Windows therefore showed the Java icon.
# ---------------------------------------------------------------------------
dash_path = ROOT / 'desktop/src/main/kotlin/br/com/monitordenoticias/desktop/DashboardV5Main.kt'
dash = dash_path.read_text(encoding='utf-8')

if 'import androidx.compose.ui.res.painterResource' not in dash:
    dash = replace_once(
        dash,
        'import androidx.compose.ui.platform.LocalDensity\n',
        'import androidx.compose.ui.platform.LocalDensity\nimport androidx.compose.ui.res.painterResource\n',
        'painterResource import'
    )

dash = replace_once(
    dash,
    '''fun main() = application {
    val trayState = rememberTrayState()
    var visible by remember { mutableStateOf(true) }''',
    '''fun main() = application {
    val trayState = rememberTrayState()
    val appIcon = painterResource("monitor-icon.svg")
    var visible by remember { mutableStateOf(true) }''',
    'application icon resource'
)

dash = replace_once(
    dash,
    '        icon = rememberVectorPainter(Icons.Default.Newspaper),',
    '        icon = appIcon,',
    'tray icon'
)

dash = replace_once(
    dash,
    '''        onCloseRequest = { visible = false },
        title = "Monitor de Notícias - Windows Portable v4.0.2",
        state = rememberWindowState(width = 1600.dp, height = 960.dp)''',
    '''        onCloseRequest = { visible = false },
        title = "Monitor de Notícias - Windows Portable v4.0.2",
        icon = appIcon,
        state = rememberWindowState(width = 1600.dp, height = 960.dp)''',
    'window icon'
)

dash = replace_once(
    dash,
    'Text("Mídia especializada: 8 veículos focados em Defesa, Forças Armadas e assuntos navais.", color = V5Ink, fontSize = 11.sp)',
    'Text("Mídia especializada: ${SourceCatalog.specialized.size} veículos focados em Defesa, Forças Armadas e assuntos navais.", color = V5Ink, fontSize = 11.sp)',
    'specialized count label'
)

dash_path.write_text(dash, encoding='utf-8')


# ---------------------------------------------------------------------------
# 4. Research/methodology note committed with the catalog for future review.
# ---------------------------------------------------------------------------
doc_path = ROOT / 'docs/state-sources-2026.md'
doc_path.write_text('''# Fontes estaduais — revisão 2026

## Critério

Esta revisão mantém **5 veículos locais/regionais por UF**. A seleção prioriza, nesta ordem:

1. evidência recente de audiência digital (Similarweb, Comscore, Google Analytics ou pesquisa de audiência publicada);
2. alcance estadual e produção jornalística própria;
3. relevância histórica e presença editorial contínua;
4. diversidade geográfica, quando o mercado estadual é muito concentrado na capital.

Edições estaduais de redes nacionais (por exemplo, G1) permanecem no catálogo nacional para evitar que um mesmo rótulo de publisher seja classificado simultaneamente em várias UFs. Ranking de audiência não é perfeitamente comparável entre todos os estados porque não existe uma única auditoria nacional, uniforme e pública para os 27 mercados. Por isso, nos estados sem ranking público recente, a lista usa os veículos locais mais consolidados e deve ser revisada periodicamente.

## Cinco veículos por UF

- **AC:** ac24horas; ContilNet; Folha do Acre; O Rio Branco; Ecos da Notícia.
- **AP:** Diário do Amapá; SelesNafes.com; A Gazeta do Amapá; Amapá Digital; Portal do Amapá.
- **AM:** Portal do Holanda; A Crítica; D24AM; Amazonas Atual; Em Tempo.
- **PA:** DOL; O Liberal; Diário do Pará; Roma News; Portal Canaã.
- **RO:** Rondoniaovivo; Rondoniagora; Portal de Rondônia; Rondonotícias; EuIdeal.
- **RR:** Folha BV; Roraima em Tempo; Roraima 1; Portal Norte Roraima; Portal Roraima.
- **TO:** Sou de Palmas; AF Notícias; Gazeta do Cerrado; Agência Tocantins; T1 Notícias.
- **AL:** TNH1; Gazeta de Alagoas/GazetaWeb; Cada Minuto; 7Segundos; Tribuna Hoje.
- **BA:** BNews; Bahia Notícias; Correio 24 Horas; iBahia; A Tarde.
- **CE:** O Povo; Diário do Nordeste; GCMAIS; CN7; Ceará Agora.
- **MA:** Imirante; O Imparcial; Jornal Pequeno; Atual7; Marrapá.
- **PB:** Portal Correio; Jornal da Paraíba; ClickPB; WSCOM; Polêmica Paraíba.
- **PE:** Jornal do Commercio; Diario de Pernambuco; Folha de Pernambuco; NE10; LeiaJá.
- **PI:** Meio Norte; Cidade Verde; GP1; 180graus; Lupa1.
- **RN:** Tribuna do Norte; Blog do BG; Agora RN; Via Certa Natal; Saiba Mais.
- **SE:** Infonet; Jornal da Cidade; F5 News; FaxAju; NE Notícias.
- **DF:** Metrópoles; Correio Braziliense; Jornal de Brasília; GPS Brasília; Brasília Capital.
- **GO:** Portal 6; O Popular; Jornal Opção; Mais Goiás; Diário de Goiás.
- **MT:** MidiaNews; Olhar Direto; Gazeta Digital; RDNews; HiperNotícias.
- **MS:** Campo Grande News; Midiamax; TopMídiaNews; O Jacaré; Correio do Estado.
- **ES:** Folha Vitória; A Gazeta; Tribuna Online; ES Hoje; Século Diário.
- **MG:** Itatiaia; Estado de Minas; O Tempo; Hoje em Dia; BHAZ.
- **RJ:** O Globo; O Dia; Extra; Jornal do Brasil; Diário do Rio.
- **SP:** Folha de S.Paulo; Estadão; Valor Econômico; Diário de S.Paulo; **A Tribuna (Santos)**.
- **PR:** Banda B; aRede; Tribuna do Paraná; Bem Paraná; Gazeta do Povo.
- **SC:** ND Mais; NSC Total; SCC10; O Município; Oeste Mais.
- **RS:** GZH/Zero Hora; Correio do Povo; Jornal do Comércio; Sul21; O Sul.

## Evidências especialmente fortes usadas na revisão

- Rondônia: pesquisa IHPEC 2025 colocou Rondoniaovivo muito à frente e identificou Rondoniagora, Portal de Rondônia, Rondonotícias e EuIdeal como o segundo grupo mais acessado.
- Pará: rankings mensais de 2025 baseados em Similarweb/Analytics colocaram DOL e O Liberal no topo, com Diário do Pará, Roma News e Portal Canaã no grupo principal.
- Bahia: dados publicados de Comscore colocaram BNews, Correio 24 Horas, Bahia Notícias e iBahia entre os maiores ambientes digitais do estado.
- Goiás: consolidado do primeiro semestre de 2026 colocou Portal 6, O Popular, Jornal Opção, Mais Goiás e Diário de Goiás exatamente nas cinco primeiras posições entre os sites jornalísticos goianos medidos.
- Mato Grosso do Sul: pesquisa Ranking Brasil de março de 2026 colocou Campo Grande News, Midiamax, TopMídiaNews, G1MS, O Jacaré e Correio do Estado no topo; como o G1 permanece nacional no catálogo, os cinco publishers locais seguintes foram selecionados.
- Espírito Santo: Folha Vitória publicou dados do Similarweb/Google Analytics que o colocaram na liderança estadual em 2025.
- Minas Gerais: Itatiaia reportou 690 milhões de acessos em 2025 e liderança digital no estado.
- Paraná: dados de 2025/2026 colocam Banda B na liderança entre portais locais da capital e aRede na liderança do interior, com Tribuna do Paraná e Bem Paraná também entre os maiores; Gazeta do Povo permanece por sua relevância estadual/nacional.
- Rio Grande do Sul: GZH/Zero Hora, Jornal do Comércio e Correio do Povo aparecem de forma recorrente entre os veículos mais relevantes/premiados da região Sul.

Revisão: setembro de 2026.
''', encoding='utf-8')

print('V8 source, terms and icon refinements applied.')
