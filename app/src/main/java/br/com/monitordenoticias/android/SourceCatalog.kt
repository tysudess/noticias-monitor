package br.com.monitordenoticias.android

data class MediaSource(
    val id: String,
    val name: String,
    val region: String,
    val state: String,
    val stateName: String,
    val group: String,
    val aliases: List<String> = emptyList()
)

object SourceCatalog {
    const val ALL_REGION = "Todas"
    const val NATIONAL_REGION = "Nacional"

    val regions = listOf("Todas", "Nacional", "Norte", "Nordeste", "Centro-Oeste", "Sudeste", "Sul")

    val states = listOf(
        Triple("AC", "Acre", "Norte"), Triple("AP", "Amapá", "Norte"), Triple("AM", "Amazonas", "Norte"),
        Triple("PA", "Pará", "Norte"), Triple("RO", "Rondônia", "Norte"), Triple("RR", "Roraima", "Norte"),
        Triple("TO", "Tocantins", "Norte"),
        Triple("AL", "Alagoas", "Nordeste"), Triple("BA", "Bahia", "Nordeste"), Triple("CE", "Ceará", "Nordeste"),
        Triple("MA", "Maranhão", "Nordeste"), Triple("PB", "Paraíba", "Nordeste"), Triple("PE", "Pernambuco", "Nordeste"),
        Triple("PI", "Piauí", "Nordeste"), Triple("RN", "Rio Grande do Norte", "Nordeste"), Triple("SE", "Sergipe", "Nordeste"),
        Triple("DF", "Distrito Federal", "Centro-Oeste"), Triple("GO", "Goiás", "Centro-Oeste"),
        Triple("MT", "Mato Grosso", "Centro-Oeste"), Triple("MS", "Mato Grosso do Sul", "Centro-Oeste"),
        Triple("ES", "Espírito Santo", "Sudeste"), Triple("MG", "Minas Gerais", "Sudeste"),
        Triple("RJ", "Rio de Janeiro", "Sudeste"), Triple("SP", "São Paulo", "Sudeste"),
        Triple("PR", "Paraná", "Sul"), Triple("SC", "Santa Catarina", "Sul"), Triple("RS", "Rio Grande do Sul", "Sul")
    )

    val national = listOf(
        national("nacional-o-globo", "O Globo", "oglobo", "Globo"),
        national("nacional-correio-brasiliense", "Correio Braziliense", "Correio Braziliense"),
        national("nacional-g1", "G1", "g1", "Globo.com"),
        national("nacional-r7", "R7", "R7.com"),
        national("nacional-revista-oeste", "Revista Oeste", "Oeste"),
        national("nacional-folha", "Folha de S.Paulo", "Folha de São Paulo", "Folha"),
        national("nacional-estadao", "Estadão", "O Estado de S. Paulo", "Estado de S. Paulo"),
        national("nacional-valor", "Valor Econômico", "Valor"),
        national("nacional-antagonista", "O Antagonista", "Antagonista"),
        national("nacional-cnn", "CNN Brasil", "CNN"),
        national("nacional-jovem-pan", "Jovem Pan", "JP"),
        national("nacional-estado-minas", "Estado de Minas", "EM.com.br"),
        national("nacional-metropoles", "Metrópoles", "Metropoles")
    )

    val byState = listOf(
        // Norte
        state("AC", "Acre", "Norte", "ac24horas", "ac24horas"),
        state("AC", "Acre", "Norte", "ContilNet", "ContilNet Notícias"),
        state("AC", "Acre", "Norte", "O Rio Branco", "Jornal O Rio Branco"),
        state("AC", "Acre", "Norte", "Ecos da Notícia", "Ecos da Noticia"),

        state("AP", "Amapá", "Norte", "Diário do Amapá", "Diario do Amapa"),
        state("AP", "Amapá", "Norte", "SelesNafes.com", "Seles Nafes"),
        state("AP", "Amapá", "Norte", "Amapá Digital", "Amapa Digital"),
        state("AP", "Amapá", "Norte", "Portal do Amapá", "Portal Amapá", "Portal Amapa"),

        state("AM", "Amazonas", "Norte", "A Crítica", "A Critica"),
        state("AM", "Amazonas", "Norte", "Amazonas Atual"),
        state("AM", "Amazonas", "Norte", "D24AM", "Diário do Amazonas", "Diario do Amazonas"),
        state("AM", "Amazonas", "Norte", "Portal do Holanda"),

        state("PA", "Pará", "Norte", "O Liberal", "Oliberal.com"),
        state("PA", "Pará", "Norte", "Diário do Pará", "Diario do Para"),
        state("PA", "Pará", "Norte", "DOL", "Diário Online", "Diario Online"),
        state("PA", "Pará", "Norte", "Roma News", "RomaNews"),

        state("RO", "Rondônia", "Norte", "Rondônia Dinâmica", "Rondonia Dinamica"),
        state("RO", "Rondônia", "Norte", "Rondoniaovivo", "Rondônia ao Vivo"),
        state("RO", "Rondônia", "Norte", "Tudo Rondônia", "Tudo Rondonia"),
        state("RO", "Rondônia", "Norte", "O Observador", "O Observador RO"),

        state("RR", "Roraima", "Norte", "Folha BV", "Folha de Boa Vista"),
        state("RR", "Roraima", "Norte", "Roraima em Tempo"),
        state("RR", "Roraima", "Norte", "Portal Norte Roraima", "Portal Norte"),
        state("RR", "Roraima", "Norte", "Roraima 1", "Roraima1"),

        state("TO", "Tocantins", "Norte", "Jornal do Tocantins"),
        state("TO", "Tocantins", "Norte", "Conexão Tocantins", "Conexao Tocantins"),
        state("TO", "Tocantins", "Norte", "Gazeta do Cerrado"),
        state("TO", "Tocantins", "Norte", "AF Notícias", "AF Noticias"),

        // Nordeste
        state("AL", "Alagoas", "Nordeste", "Gazeta de Alagoas"),
        state("AL", "Alagoas", "Nordeste", "TNH1"),
        state("AL", "Alagoas", "Nordeste", "Cada Minuto"),
        state("AL", "Alagoas", "Nordeste", "Tribuna Hoje"),

        state("BA", "Bahia", "Nordeste", "A Tarde"),
        state("BA", "Bahia", "Nordeste", "Correio 24 Horas", "Correio da Bahia", "Correio*"),
        state("BA", "Bahia", "Nordeste", "Bahia Notícias", "Bahia Noticias"),
        state("BA", "Bahia", "Nordeste", "BNews", "Bahia Notícias BNews"),

        state("CE", "Ceará", "Nordeste", "Diário do Nordeste", "Diario do Nordeste"),
        state("CE", "Ceará", "Nordeste", "O Povo"),
        state("CE", "Ceará", "Nordeste", "GCMAIS", "GC Mais"),
        state("CE", "Ceará", "Nordeste", "CN7"),

        state("MA", "Maranhão", "Nordeste", "O Imparcial"),
        state("MA", "Maranhão", "Nordeste", "Jornal Pequeno"),
        state("MA", "Maranhão", "Nordeste", "Imirante"),
        state("MA", "Maranhão", "Nordeste", "Atual7"),

        state("PB", "Paraíba", "Nordeste", "Jornal da Paraíba", "Jornal da Paraiba"),
        state("PB", "Paraíba", "Nordeste", "ClickPB"),
        state("PB", "Paraíba", "Nordeste", "Portal Correio"),
        state("PB", "Paraíba", "Nordeste", "MaisPB"),

        state("PE", "Pernambuco", "Nordeste", "Jornal do Commercio", "JC Online"),
        state("PE", "Pernambuco", "Nordeste", "Diario de Pernambuco", "Diário de Pernambuco"),
        state("PE", "Pernambuco", "Nordeste", "Folha de Pernambuco"),
        state("PE", "Pernambuco", "Nordeste", "NE10"),

        state("PI", "Piauí", "Nordeste", "Meio Norte"),
        state("PI", "Piauí", "Nordeste", "Cidade Verde"),
        state("PI", "Piauí", "Nordeste", "O Dia", "O Dia Piauí"),
        state("PI", "Piauí", "Nordeste", "GP1"),

        state("RN", "Rio Grande do Norte", "Nordeste", "Tribuna do Norte"),
        state("RN", "Rio Grande do Norte", "Nordeste", "Agora RN"),
        state("RN", "Rio Grande do Norte", "Nordeste", "Blog do BG", "BG"),
        state("RN", "Rio Grande do Norte", "Nordeste", "Saiba Mais", "Agência Saiba Mais"),

        state("SE", "Sergipe", "Nordeste", "Jornal da Cidade", "Jornal da Cidade Sergipe"),
        state("SE", "Sergipe", "Nordeste", "Infonet"),
        state("SE", "Sergipe", "Nordeste", "FaxAju"),
        state("SE", "Sergipe", "Nordeste", "NE Notícias", "NE Noticias"),

        // Centro-Oeste
        state("DF", "Distrito Federal", "Centro-Oeste", "Correio Braziliense"),
        state("DF", "Distrito Federal", "Centro-Oeste", "Metrópoles", "Metropoles"),
        state("DF", "Distrito Federal", "Centro-Oeste", "Jornal de Brasília", "Jornal de Brasilia"),
        state("DF", "Distrito Federal", "Centro-Oeste", "GPS Brasília", "GPS Brasilia"),

        state("GO", "Goiás", "Centro-Oeste", "O Popular"),
        state("GO", "Goiás", "Centro-Oeste", "Mais Goiás", "Mais Goias"),
        state("GO", "Goiás", "Centro-Oeste", "Diário de Goiás", "Diario de Goias"),
        state("GO", "Goiás", "Centro-Oeste", "Sagres", "Sagres Online"),

        state("MT", "Mato Grosso", "Centro-Oeste", "MidiaNews"),
        state("MT", "Mato Grosso", "Centro-Oeste", "Gazeta Digital"),
        state("MT", "Mato Grosso", "Centro-Oeste", "RDNews"),
        state("MT", "Mato Grosso", "Centro-Oeste", "Olhar Direto"),

        state("MS", "Mato Grosso do Sul", "Centro-Oeste", "Campo Grande News"),
        state("MS", "Mato Grosso do Sul", "Centro-Oeste", "Midiamax"),
        state("MS", "Mato Grosso do Sul", "Centro-Oeste", "Correio do Estado"),
        state("MS", "Mato Grosso do Sul", "Centro-Oeste", "O Jacaré", "O Jacare"),

        // Sudeste
        state("ES", "Espírito Santo", "Sudeste", "A Gazeta", "A Gazeta ES"),
        state("ES", "Espírito Santo", "Sudeste", "Folha Vitória", "Folha Vitoria"),
        state("ES", "Espírito Santo", "Sudeste", "ES Hoje"),
        state("ES", "Espírito Santo", "Sudeste", "Século Diário", "Seculo Diario"),

        state("MG", "Minas Gerais", "Sudeste", "Estado de Minas", "EM.com.br"),
        state("MG", "Minas Gerais", "Sudeste", "O Tempo"),
        state("MG", "Minas Gerais", "Sudeste", "Itatiaia", "Rádio Itatiaia"),
        state("MG", "Minas Gerais", "Sudeste", "Hoje em Dia"),

        state("RJ", "Rio de Janeiro", "Sudeste", "O Globo", "oglobo"),
        state("RJ", "Rio de Janeiro", "Sudeste", "Extra", "Extra Online"),
        state("RJ", "Rio de Janeiro", "Sudeste", "O Dia", "O Dia RJ"),
        state("RJ", "Rio de Janeiro", "Sudeste", "Jornal do Brasil", "JB"),

        state("SP", "São Paulo", "Sudeste", "Folha de S.Paulo", "Folha de São Paulo", "Folha"),
        state("SP", "São Paulo", "Sudeste", "Estadão", "O Estado de S. Paulo"),
        state("SP", "São Paulo", "Sudeste", "Diário de S.Paulo", "Diario de S.Paulo"),
        state("SP", "São Paulo", "Sudeste", "Valor Econômico", "Valor"),

        // Sul
        state("PR", "Paraná", "Sul", "Gazeta do Povo"),
        state("PR", "Paraná", "Sul", "Bem Paraná", "Bem Parana"),
        state("PR", "Paraná", "Sul", "Banda B"),
        state("PR", "Paraná", "Sul", "Tribuna do Paraná", "Tribuna do Parana"),

        state("SC", "Santa Catarina", "Sul", "NSC Total", "NSC"),
        state("SC", "Santa Catarina", "Sul", "ND Mais", "ND+"),
        state("SC", "Santa Catarina", "Sul", "SCC10", "SCC"),
        state("SC", "Santa Catarina", "Sul", "O Município", "O Municipio"),

        state("RS", "Rio Grande do Sul", "Sul", "Zero Hora", "ZH"),
        state("RS", "Rio Grande do Sul", "Sul", "Correio do Povo"),
        state("RS", "Rio Grande do Sul", "Sul", "Jornal do Comércio", "Jornal do Comercio RS"),
        state("RS", "Rio Grande do Sul", "Sul", "Sul21")
    )

    val specialized = listOf(
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
            id = "especializada-poder-aereo",
            name = "Poder Aéreo",
            region = NATIONAL_REGION,
            state = "BR",
            stateName = "Brasil",
            group = "Mídia especializada",
            aliases = listOf("Poder Aereo", "aereo.jor.br")
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
            id = "especializada-tecnologia-defesa",
            name = "Tecnologia & Defesa",
            region = NATIONAL_REGION,
            state = "BR",
            stateName = "Brasil",
            group = "Mídia especializada",
            aliases = listOf("Tecnologia e Defesa", "tecnodefesa.com.br")
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
    )

    val all: List<MediaSource> = (national + byState + specialized).distinctBy { it.id }
    val byId: Map<String, MediaSource> = all.associateBy { it.id }

    fun selected(ids: Set<String>): List<MediaSource> = ids.mapNotNull(byId::get)

    private fun national(id: String, name: String, vararg aliases: String) = MediaSource(
        id = id,
        name = name,
        region = NATIONAL_REGION,
        state = "BR",
        stateName = "Brasil",
        group = "Jornais nacionais",
        aliases = aliases.toList()
    )

    private fun state(
        state: String,
        stateName: String,
        region: String,
        name: String,
        vararg aliases: String
    ) = MediaSource(
        id = "${state.lowercase()}-${slug(name)}",
        name = name,
        region = region,
        state = state,
        stateName = stateName,
        group = "$stateName • $region",
        aliases = aliases.toList()
    )

    private fun slug(value: String): String = value.lowercase()
        .replace("á", "a").replace("à", "a").replace("ã", "a").replace("â", "a")
        .replace("é", "e").replace("ê", "e")
        .replace("í", "i")
        .replace("ó", "o").replace("ô", "o").replace("õ", "o")
        .replace("ú", "u").replace("ç", "c")
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
}
