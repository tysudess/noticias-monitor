package br.com.monitordenoticias.android

import java.net.URLEncoder

object VideoSourceCatalog {
    private val portalNational = listOf(
        VideoSource(
            id = "video-globoplay-jornalismo",
            name = "Globoplay Jornalismo (geral)",
            group = "Globo / Globoplay",
            landingUrl = "https://globoplay.globo.com/categorias/jornalismo/",
            linkHints = listOf("/v/"),
            aliases = listOf("Globo", "Globoplay", "GloboNews", "Globo News"),
            searchUrlTemplate = "https://globoplay.globo.com/busca/?q={query}"
        ),
        VideoSource(
            id = "video-r7-record",
            name = "R7 / Record",
            group = "Record",
            landingUrl = "https://noticias.r7.com/videos/",
            linkHints = listOf("/videos/"),
            aliases = listOf("R7", "Record", "Record TV", "Record News"),
            searchUrlTemplate = "https://noticias.r7.com/busca?q={query}"
        ),
        VideoSource(
            id = "video-cnn-brasil",
            name = "CNN Brasil",
            group = "CNN",
            landingUrl = "https://www.cnnbrasil.com.br/ao-vivo/",
            linkHints = listOf("/ao-vivo/", "/videos/"),
            aliases = listOf("CNN", "CNN Brasil"),
            searchUrlTemplate = "https://www.cnnbrasil.com.br/?s={query}"
        ),
        VideoSource(
            id = "video-sbt-news",
            name = "SBT News",
            group = "SBT",
            landingUrl = "https://sbtnews.sbt.com.br/videos/ao-vivo",
            linkHints = listOf("/videos/"),
            aliases = listOf("SBT", "SBT News"),
            searchUrlTemplate = "https://sbtnews.sbt.com.br/busca?q={query}"
        ),
        VideoSource(
            id = "video-band",
            name = "Band Jornalismo",
            group = "Band",
            landingUrl = "https://www.band.com.br/videos",
            linkHints = listOf("/videos/"),
            aliases = listOf("Band", "Band Jornalismo", "BandNews", "Band News"),
            searchUrlTemplate = "https://www.band.com.br/busca?q={query}"
        )
    )

    /** Programas nacionais com página própria de vídeos e varredura única por fonte. */
    val portalProgramsNational = listOf(
        VideoSource(
            id = "video-band-jornal-da-band",
            name = "Jornal da Band",
            group = "Band • Jornal da Band",
            landingUrl = "https://www.band.com.br/programas/jornal-da-band",
            linkHints = listOf("/noticias/jornal-da-band/videos/"),
            aliases = listOf("Band", "Jornal da Band", "JDB"),
            searchUrlTemplate = "https://www.band.com.br/busca?q={query}",
            searchPrefix = "Jornal da Band"
        ),
        VideoSource(
            id = "video-band-brasil-urgente",
            name = "Brasil Urgente",
            group = "Band • Brasil Urgente",
            landingUrl = "https://www.band.com.br/programas/brasil-urgente",
            linkHints = listOf("/noticias/brasil-urgente/videos/"),
            aliases = listOf("Band", "Brasil Urgente", "BU"),
            searchUrlTemplate = "https://www.band.com.br/busca?q={query}",
            searchPrefix = "Brasil Urgente"
        ),
        VideoSource(
            id = "video-r7-jornal-da-record",
            name = "Jornal da Record",
            group = "Record • Jornal da Record",
            landingUrl = "https://noticias.r7.com/jr-na-tv/videos/",
            linkHints = listOf("/jr-na-tv/videos/"),
            aliases = listOf("Record", "Record TV", "Jornal da Record", "JR", "JR na TV"),
            searchUrlTemplate = "https://noticias.r7.com/busca?q={query}",
            searchPrefix = "Jornal da Record"
        ),
        VideoSource(
            id = "video-r7-domingo-espetacular",
            name = "Domingo Espetacular",
            group = "Record • Domingo Espetacular",
            landingUrl = "https://record.r7.com/domingo-espetacular/videos/",
            linkHints = listOf("/domingo-espetacular/videos/", "/domingo-espetacular/video/"),
            aliases = listOf("Record", "Record TV", "Domingo Espetacular"),
            searchUrlTemplate = "https://www.r7.com/busca?q={query}",
            searchPrefix = "Domingo Espetacular"
        ),
        VideoSource(
            id = "video-r7-balanco-geral-sp",
            name = "Balanço Geral SP",
            group = "Record • Balanço Geral",
            region = "Sudeste",
            state = "SP",
            landingUrl = "https://record.r7.com/balanco-geral/videos/",
            linkHints = listOf("/balanco-geral/videos/"),
            aliases = listOf("Record", "Record TV", "Balanço Geral", "Balanço Geral SP", "BG SP"),
            searchUrlTemplate = "https://www.r7.com/busca?q={query}",
            searchPrefix = "Balanço Geral SP"
        )
    )

    /** Programas regionais relevantes com página própria de vídeos. */
    val portalProgramsRegional = listOf(
        VideoSource(
            id = "video-band-jornal-do-rio",
            name = "Jornal do Rio",
            group = "Band Regional • Jornal do Rio",
            region = "Sudeste",
            state = "RJ",
            landingUrl = "https://www.band.com.br/rio-de-janeiro/videos",
            linkHints = listOf("/rio-de-janeiro/videos/"),
            aliases = listOf("Band", "Band Rio", "Jornal do Rio"),
            searchUrlTemplate = "https://www.band.com.br/busca?q={query}",
            searchPrefix = "Jornal do Rio"
        ),
        VideoSource(
            id = "video-r7-balanco-geral-rj",
            name = "Balanço Geral RJ",
            group = "Record • Balanço Geral",
            region = "Sudeste",
            state = "RJ",
            landingUrl = "https://record.r7.com/balanco-geral-rj/videos/",
            linkHints = listOf("/balanco-geral-rj/videos/"),
            aliases = listOf("Record", "Record TV", "Balanço Geral", "Balanço Geral RJ", "BG RJ"),
            searchUrlTemplate = "https://www.r7.com/busca?q={query}",
            searchPrefix = "Balanço Geral RJ"
        )
    )

    /** Canais oficiais no YouTube monitorados como fontes independentes. */
    val youtubeOfficial = listOf(
        youtube("youtube-cnn-brasil", "CNN Brasil", "@CNNBrasil", listOf("CNN", "CNN Brasil")),
        youtube("youtube-jovem-pan-news", "Jovem Pan News", "@jovempannews", listOf("Jovem Pan", "Jovem Pan News", "JP News")),
        youtube("youtube-globonews", "GloboNews", "@globonews", listOf("GloboNews", "Globo News", "Globo")),
        youtube("youtube-record-news", "Record News", "@recordnews", listOf("Record News", "RecordNews")),
        youtube("youtube-jornal-da-record", "Jornal da Record", "@JornaldaRecord", listOf("Jornal da Record", "JR", "Record TV")),
        youtube("youtube-band-jornalismo", "Band Jornalismo", "@bandjornalismo", listOf("Band", "Band Jornalismo", "BandNews", "Band News", "BandNews TV")),
        youtube("youtube-sbt-news", "SBT News", "@sbtnews", listOf("SBT", "SBT News", "SBT Jornalismo"))
    )

    /** Telejornais nacionais Globo/Globoplay. */
    val globoplayTelejournalsNational = listOf(
        nationalGlobo("globoplay-bom-dia-brasil", "Bom Dia Brasil"),
        nationalGlobo("globoplay-hora-1", "Hora 1"),
        nationalGlobo("globoplay-jornal-hoje", "Jornal Hoje"),
        nationalGlobo("globoplay-jornal-nacional", "Jornal Nacional"),
        nationalGlobo("globoplay-jornal-da-globo", "Jornal da Globo"),
        nationalGlobo("globoplay-fantastico", "Fantástico")
    )

    /**
     * Catálogo individualizado dos principais telejornais locais das afiliadas
     * Globo. Cada item carrega Região e UF para que a seleção de fontes de vídeo
     * funcione do mesmo modo que a seleção de fontes de notícias.
     *
     * Quando uma página fixa de programa/trecho não é estável, a landingUrl usa
     * a busca do próprio Globoplay pelo nome do programa; nas buscas por Termos,
     * searchPrefix força "nome do telejornal + termo".
     */
    val globoplayRegionalSpecific = listOf(
        // NORTE — AC
        regionalGlobo("globoplay-bom-dia-acre", "Bom Dia Acre", "AC", "Norte", aliases = listOf("BDAC", "Rede Amazônica Acre")),
        regionalGlobo("globoplay-jac1", "Jornal do Acre 1ª Edição", "AC", "Norte", aliases = listOf("JAC1", "Jornal do Acre 1")),
        regionalGlobo("globoplay-jac2", "Jornal do Acre 2ª Edição", "AC", "Norte", aliases = listOf("JAC2", "Jornal do Acre 2")),

        // AP
        regionalGlobo("globoplay-bom-dia-amapa", "Bom Dia Amapá", "AP", "Norte"),
        regionalGlobo("globoplay-jap1", "Jornal do Amapá 1ª Edição", "AP", "Norte", aliases = listOf("JAP1", "Jornal do Amapá 1")),
        regionalGlobo("globoplay-jap2", "Jornal do Amapá 2ª Edição", "AP", "Norte", aliases = listOf("JAP2", "Jornal do Amapá 2")),

        // AM
        regionalGlobo("globoplay-bom-dia-amazonia-am", "Bom Dia Amazônia", "AM", "Norte", aliases = listOf("Bom Dia Amazonas", "Rede Amazônica Amazonas")),
        regionalGlobo("globoplay-jam1", "Jornal do Amazonas 1ª Edição", "AM", "Norte", aliases = listOf("JAM1", "JAM 1ª Edição")),
        regionalGlobo("globoplay-jam2", "Jornal do Amazonas 2ª Edição", "AM", "Norte", aliases = listOf("JAM2", "JAM 2ª Edição")),

        // PA — Belém / TV Liberal
        regionalGlobo("globoplay-bom-dia-para", "Bom Dia Pará", "PA", "Norte", aliases = listOf("TV Liberal")),
        regionalGlobo("globoplay-jl1", "Jornal Liberal 1ª Edição", "PA", "Norte", aliases = listOf("JL1", "Jornal Liberal 1", "TV Liberal")),
        regionalGlobo("globoplay-jl2", "Jornal Liberal 2ª Edição", "PA", "Norte", aliases = listOf("JL2", "Jornal Liberal 2", "TV Liberal")),
        // PA — Santarém / TV Tapajós
        regionalGlobo("globoplay-bom-dia-santarem", "Bom Dia Santarém", "PA", "Norte", aliases = listOf("TV Tapajós")),
        regionalGlobo("globoplay-tj1-tapajos", "Jornal Tapajós 1ª Edição", "PA", "Norte", aliases = listOf("TJ1", "Jornal Tapajós 1", "TV Tapajós"), landingOverride = "https://globoplay.globo.com/v/6897326/"),
        regionalGlobo("globoplay-tj2-tapajos", "Jornal Tapajós 2ª Edição", "PA", "Norte", aliases = listOf("TJ2", "Jornal Tapajós 2", "TV Tapajós"), landingOverride = "https://globoplay.globo.com/v/13630892/"),

        // RO
        regionalGlobo("globoplay-bom-dia-amazonia-ro", "Bom Dia Amazônia", "RO", "Norte", aliases = listOf("Bom Dia Rondônia", "Rede Amazônica Rondônia")),
        regionalGlobo("globoplay-jro1", "Jornal de Rondônia 1ª Edição", "RO", "Norte", aliases = listOf("JRO1", "Jornal de Rondônia 1")),
        regionalGlobo("globoplay-jro2", "Jornal de Rondônia 2ª Edição", "RO", "Norte", aliases = listOf("JRO2", "Jornal de Rondônia 2")),

        // RR
        regionalGlobo("globoplay-bom-dia-amazonia-rr", "Bom Dia Amazônia", "RR", "Norte", aliases = listOf("Bom Dia Roraima", "Rede Amazônica Roraima")),
        regionalGlobo("globoplay-jrr1", "Jornal de Roraima 1ª Edição", "RR", "Norte", aliases = listOf("JRR1", "Jornal de Roraima 1")),
        regionalGlobo("globoplay-jrr2", "Jornal de Roraima 2ª Edição", "RR", "Norte", aliases = listOf("JRR2", "Jornal de Roraima 2")),

        // TO
        regionalGlobo("globoplay-bom-dia-tocantins", "Bom Dia Tocantins", "TO", "Norte", aliases = listOf("TV Anhanguera Tocantins")),
        regionalGlobo("globoplay-ja1-to", "Jornal Anhanguera 1ª Edição Tocantins", "TO", "Norte", aliases = listOf("JA1 Tocantins", "JA 1ª Edição Tocantins")),
        regionalGlobo("globoplay-ja2-to", "Jornal Anhanguera 2ª Edição Tocantins", "TO", "Norte", aliases = listOf("JA2 Tocantins", "JA 2ª Edição Tocantins")),

        // NORDESTE — AL
        regionalGlobo("globoplay-bom-dia-alagoas", "Bom Dia Alagoas", "AL", "Nordeste", aliases = listOf("TV Gazeta AL")),
        regionalGlobo("globoplay-al1", "AL1", "AL", "Nordeste", aliases = listOf("AL 1ª Edição", "ALTV 1ª Edição")),
        regionalGlobo("globoplay-al2", "AL2", "AL", "Nordeste", aliases = listOf("AL 2ª Edição", "ALTV 2ª Edição")),

        // BA
        regionalGlobo("globoplay-jornal-da-manha-ba", "Jornal da Manhã", "BA", "Nordeste", aliases = listOf("TV Bahia", "Jornal da Manhã Bahia", "Bom Dia Bahia")),
        regionalGlobo("globoplay-bahia-meio-dia", "Bahia Meio Dia", "BA", "Nordeste", aliases = listOf("BMD", "TV Bahia 1ª Edição")),
        regionalGlobo("globoplay-batv", "BATV", "BA", "Nordeste", aliases = listOf("Bahia TV", "TV Bahia 2ª Edição")),

        // CE
        regionalGlobo("globoplay-bom-dia-ceara", "Bom Dia Ceará", "CE", "Nordeste", aliases = listOf("TV Verdes Mares")),
        regionalGlobo("globoplay-cetv1", "CETV 1ª Edição", "CE", "Nordeste", aliases = listOf("CETV1", "CE1")),
        regionalGlobo("globoplay-cetv2", "CETV 2ª Edição", "CE", "Nordeste", aliases = listOf("CETV2", "CE2")),

        // MA
        regionalGlobo("globoplay-bom-dia-mirante", "Bom Dia Mirante", "MA", "Nordeste", aliases = listOf("TV Mirante")),
        regionalGlobo("globoplay-jmtv1", "JMTV 1ª Edição", "MA", "Nordeste", aliases = listOf("JMTV1", "Jornal Mirante 1ª Edição")),
        regionalGlobo("globoplay-jmtv2", "JMTV 2ª Edição", "MA", "Nordeste", aliases = listOf("JMTV2", "Jornal Mirante 2ª Edição")),

        // PB
        regionalGlobo("globoplay-bom-dia-paraiba", "Bom Dia Paraíba", "PB", "Nordeste", aliases = listOf("TV Cabo Branco")),
        regionalGlobo("globoplay-jpb1", "JPB1", "PB", "Nordeste", aliases = listOf("JPB 1ª Edição", "Jornal da Paraíba 1ª Edição")),
        regionalGlobo("globoplay-jpb2", "JPB2", "PB", "Nordeste", aliases = listOf("JPB 2ª Edição", "Jornal da Paraíba 2ª Edição")),

        // PE
        regionalGlobo("globoplay-bom-dia-pe", "Bom Dia Pernambuco", "PE", "Nordeste", aliases = listOf("Bom Dia PE", "Globo Pernambuco")),
        regionalGlobo("globoplay-ne1", "NE1", "PE", "Nordeste", aliases = listOf("NETV 1ª Edição", "NE 1ª Edição")),
        regionalGlobo("globoplay-ne2", "NE2", "PE", "Nordeste", aliases = listOf("NETV 2ª Edição", "NE 2ª Edição")),

        // PI
        regionalGlobo("globoplay-bom-dia-piaui", "Bom Dia Piauí", "PI", "Nordeste", aliases = listOf("TV Clube")),
        regionalGlobo("globoplay-pi1", "PI1", "PI", "Nordeste", aliases = listOf("PITV 1ª Edição", "PI 1ª Edição")),
        regionalGlobo("globoplay-pi2", "PI2", "PI", "Nordeste", aliases = listOf("PITV 2ª Edição", "PI 2ª Edição")),

        // RN
        regionalGlobo("globoplay-bom-dia-rn", "Bom Dia RN", "RN", "Nordeste", aliases = listOf("Inter TV Cabugi")),
        regionalGlobo("globoplay-rn1", "RN1", "RN", "Nordeste", aliases = listOf("RNTV 1ª Edição", "RN 1ª Edição")),
        regionalGlobo("globoplay-rn2", "RN2", "RN", "Nordeste", aliases = listOf("RNTV 2ª Edição", "RN 2ª Edição")),

        // SE
        regionalGlobo("globoplay-bom-dia-sergipe", "Bom Dia Sergipe", "SE", "Nordeste", aliases = listOf("TV Sergipe")),
        regionalGlobo("globoplay-se1", "SE1", "SE", "Nordeste", aliases = listOf("SETV 1ª Edição", "SE 1ª Edição")),
        regionalGlobo("globoplay-se2", "SE2", "SE", "Nordeste", aliases = listOf("SETV 2ª Edição", "SE 2ª Edição")),

        // CENTRO-OESTE — DF
        regionalGlobo("globoplay-bom-dia-df", "Bom Dia DF", "DF", "Centro-Oeste", aliases = listOf("Globo Brasília")),
        regionalGlobo("globoplay-df1", "DF1", "DF", "Centro-Oeste", aliases = listOf("DFTV 1ª Edição", "DF 1"), landingOverride = "https://globoplay.globo.com/v/14711882/"),
        regionalGlobo("globoplay-df2", "DF2", "DF", "Centro-Oeste", aliases = listOf("DFTV 2ª Edição", "DF 2")),

        // GO
        regionalGlobo("globoplay-bom-dia-goias", "Bom Dia Goiás", "GO", "Centro-Oeste", aliases = listOf("TV Anhanguera")),
        regionalGlobo("globoplay-ja1-go", "Jornal Anhanguera 1ª Edição", "GO", "Centro-Oeste", aliases = listOf("JA1 Goiás", "JA 1ª Edição")),
        regionalGlobo("globoplay-ja2-go", "Jornal Anhanguera 2ª Edição", "GO", "Centro-Oeste", aliases = listOf("JA2 Goiás", "JA 2ª Edição")),

        // MT
        regionalGlobo("globoplay-bom-dia-mt", "Bom Dia Mato Grosso", "MT", "Centro-Oeste", aliases = listOf("TV Centro América")),
        regionalGlobo("globoplay-mt1", "MT1", "MT", "Centro-Oeste", aliases = listOf("MTTV 1ª Edição", "MT 1ª Edição")),
        regionalGlobo("globoplay-mt2", "MT2", "MT", "Centro-Oeste", aliases = listOf("MTTV 2ª Edição", "MT 2ª Edição")),

        // MS
        regionalGlobo("globoplay-bom-dia-ms", "Bom Dia MS", "MS", "Centro-Oeste", aliases = listOf("TV Morena")),
        regionalGlobo("globoplay-mstv1", "MSTV 1ª Edição", "MS", "Centro-Oeste", aliases = listOf("MS1", "MSTV1")),
        regionalGlobo("globoplay-mstv2", "MSTV 2ª Edição", "MS", "Centro-Oeste", aliases = listOf("MS2", "MSTV2")),

        // SUDESTE — ES
        regionalGlobo("globoplay-bom-dia-es", "Bom Dia ES", "ES", "Sudeste", aliases = listOf("TV Gazeta ES"), landingOverride = "https://globoplay.globo.com/v/11645540/"),
        regionalGlobo("globoplay-gazeta-meio-dia-es", "Gazeta Meio Dia", "ES", "Sudeste", aliases = listOf("ESTV 1ª Edição", "ESTV1", "ES1"), landingOverride = "https://globoplay.globo.com/v/5423638/"),
        regionalGlobo("globoplay-estv2", "ESTV 2ª Edição", "ES", "Sudeste", aliases = listOf("ESTV2", "ES2")),

        // MG
        regionalGlobo("globoplay-bom-dia-minas", "Bom Dia Minas", "MG", "Sudeste", aliases = listOf("Globo Minas"), landingOverride = "https://globoplay.globo.com/v/5535965/"),
        regionalGlobo("globoplay-mg1", "MG1", "MG", "Sudeste", aliases = listOf("MGTV 1ª Edição", "MG Primeira Edição"), landingOverride = "https://globoplay.globo.com/v/12552834/"),
        regionalGlobo("globoplay-mg2", "MG2", "MG", "Sudeste", aliases = listOf("MGTV 2ª Edição", "MG Segunda Edição")),

        // RJ
        regionalGlobo("globoplay-bom-dia-rio", "Bom Dia Rio", "RJ", "Sudeste", landingOverride = "https://globoplay.globo.com/v/8383434/"),
        regionalGlobo("globoplay-rj1", "RJ1", "RJ", "Sudeste", aliases = listOf("RJTV 1ª Edição", "RJ Primeira Edição"), landingOverride = "https://globoplay.globo.com/v/5976232/"),
        regionalGlobo("globoplay-rj2", "RJ2", "RJ", "Sudeste", aliases = listOf("RJTV 2ª Edição", "RJ Segunda Edição"), landingOverride = "https://globoplay.globo.com/v/12954402/"),

        // SP — capital
        regionalGlobo("globoplay-bom-dia-sp", "Bom Dia SP", "SP", "Sudeste", aliases = listOf("Bom Dia São Paulo", "BDSP"), landingOverride = "https://globoplay.globo.com/v/5701776/"),
        regionalGlobo("globoplay-sp1", "SP1", "SP", "Sudeste", aliases = listOf("SPTV 1ª Edição", "SP Primeira Edição"), landingOverride = "https://globoplay.globo.com/v/12096579/"),
        regionalGlobo("globoplay-sp2", "SP2", "SP", "Sudeste", aliases = listOf("SPTV 2ª Edição", "SP Segunda Edição"), landingOverride = "https://globoplay.globo.com/v/5854721/"),
        // SP — afiliadas principais
        regionalGlobo("globoplay-bom-dia-cidade-eptv", "Bom Dia Cidade • EPTV", "SP", "Sudeste", aliases = listOf("EPTV Campinas", "EPTV Ribeirão", "EPTV Central")),
        regionalGlobo("globoplay-eptv1", "EPTV1", "SP", "Sudeste", aliases = listOf("EPTV 1ª Edição")),
        regionalGlobo("globoplay-eptv2", "EPTV2", "SP", "Sudeste", aliases = listOf("EPTV 2ª Edição")),
        regionalGlobo("globoplay-bom-dia-cidade-tem", "Bom Dia Cidade • TV TEM", "SP", "Sudeste", aliases = listOf("TV TEM")),
        regionalGlobo("globoplay-tem-noticias-1", "TEM Notícias 1ª Edição", "SP", "Sudeste", aliases = listOf("TV TEM 1ª Edição")),
        regionalGlobo("globoplay-tem-noticias-2", "TEM Notícias 2ª Edição", "SP", "Sudeste", aliases = listOf("TV TEM 2ª Edição")),
        regionalGlobo("globoplay-bom-dia-regiao-tribuna", "Bom Dia Região • TV Tribuna", "SP", "Sudeste", aliases = listOf("TV Tribuna Santos")),
        regionalGlobo("globoplay-jornal-tribuna-1", "Jornal Tribuna 1ª Edição", "SP", "Sudeste", aliases = listOf("JT1", "TV Tribuna")),
        regionalGlobo("globoplay-jornal-tribuna-2", "Jornal Tribuna 2ª Edição", "SP", "Sudeste", aliases = listOf("JT2", "TV Tribuna")),
        regionalGlobo("globoplay-bom-dia-fronteira", "Bom Dia Fronteira", "SP", "Sudeste", aliases = listOf("TV Fronteira")),
        regionalGlobo("globoplay-fronteira-noticias-1", "Fronteira Notícias 1ª Edição", "SP", "Sudeste", aliases = listOf("TV Fronteira 1ª Edição")),
        regionalGlobo("globoplay-fronteira-noticias-2", "Fronteira Notícias 2ª Edição", "SP", "Sudeste", aliases = listOf("TV Fronteira 2ª Edição")),
        regionalGlobo("globoplay-bom-dia-vanguarda", "Bom Dia Vanguarda", "SP", "Sudeste", aliases = listOf("TV Vanguarda")),
        regionalGlobo("globoplay-link-vanguarda", "Link Vanguarda", "SP", "Sudeste", aliases = listOf("TV Vanguarda 1ª Edição")),
        regionalGlobo("globoplay-jornal-vanguarda", "Jornal Vanguarda", "SP", "Sudeste", aliases = listOf("TV Vanguarda 2ª Edição")),

        // SUL — PR
        regionalGlobo("globoplay-bom-dia-parana", "Bom Dia Paraná", "PR", "Sul", aliases = listOf("RPC")),
        regionalGlobo("globoplay-meio-dia-parana", "Meio Dia Paraná", "PR", "Sul", aliases = listOf("RPC 1ª Edição")),
        regionalGlobo("globoplay-boa-noite-parana", "Boa Noite Paraná", "PR", "Sul", aliases = listOf("RPC 2ª Edição")),

        // SC
        regionalGlobo("globoplay-bom-dia-sc", "Bom Dia Santa Catarina", "SC", "Sul", aliases = listOf("Bom Dia SC", "NSC TV")),
        regionalGlobo("globoplay-jornal-do-almoco-sc", "Jornal do Almoço • SC", "SC", "Sul", aliases = listOf("NSC TV 1ª Edição", "Jornal do Almoço")),
        regionalGlobo("globoplay-nsc-noticias", "NSC Notícias", "SC", "Sul", aliases = listOf("NSC TV 2ª Edição")),

        // RS
        regionalGlobo("globoplay-bom-dia-rio-grande", "Bom Dia Rio Grande", "RS", "Sul", aliases = listOf("RBS TV"), landingOverride = "https://globoplay.globo.com/v/12316554/"),
        regionalGlobo("globoplay-jornal-do-almoco-rs", "Jornal do Almoço • RS", "RS", "Sul", aliases = listOf("RBS TV 1ª Edição", "Jornal do Almoço")),
        regionalGlobo("globoplay-rbs-noticias", "RBS Notícias", "RS", "Sul", aliases = listOf("RBS TV 2ª Edição"))
    )

    /**
     * Cobertura ampla preservada como rede de segurança. Essas três fontes podem
     * capturar programas/afiliadas que mudem de nome e ainda não estejam no catálogo.
     */
    val globoplayRegionalSweeps = listOf(
        globoplaySweep("globoplay-regionais-bom-dia", "Globoplay • Cobertura ampla — Bom Dia", "Bom Dia", listOf("Bom Dia regional")),
        globoplaySweep("globoplay-regionais-primeira-edicao", "Globoplay • Cobertura ampla — 1ª Edições", "1ª Edição", listOf("Primeira Edição", "1a Edição")),
        globoplaySweep("globoplay-regionais-segunda-edicao", "Globoplay • Cobertura ampla — 2ª Edições", "2ª Edição", listOf("Segunda Edição", "2a Edição"))
    )

    val globoplayTelejournalsRegional: List<VideoSource> = globoplayRegionalSpecific + globoplayRegionalSweeps
    val national: List<VideoSource> = portalNational + portalProgramsNational + youtubeOfficial + globoplayTelejournalsNational

    private val bandRegional = listOf(
        VideoSource(
            id = "video-band-brasilia", name = "Band Brasília", group = "Band Regional", region = "Centro-Oeste", state = "DF",
            landingUrl = "https://www.band.com.br/band-brasilia/videos", linkHints = listOf("/band-brasilia/videos/", "/videos/"), aliases = listOf("Band Brasília", "Band DF"),
            searchUrlTemplate = "https://www.band.com.br/busca?q={query}", searchPrefix = "Band Brasília"
        ),
        VideoSource(
            id = "video-band-minas", name = "Band Minas", group = "Band Regional", region = "Sudeste", state = "MG",
            landingUrl = "https://www.band.com.br/minas-gerais", linkHints = listOf("/band-minas/videos/", "/minas-gerais/videos/", "/videos/"), aliases = listOf("Band Minas", "Band Minas Gerais"),
            searchUrlTemplate = "https://www.band.com.br/busca?q={query}", searchPrefix = "Band Minas"
        ),
        VideoSource(
            id = "video-band-rio", name = "Band Rio", group = "Band Regional", region = "Sudeste", state = "RJ",
            landingUrl = "https://www.band.com.br/rio-de-janeiro/videos", linkHints = listOf("/rio-de-janeiro/videos/", "/videos/"), aliases = listOf("Band Rio", "Band Rio de Janeiro"),
            searchUrlTemplate = "https://www.band.com.br/busca?q={query}", searchPrefix = "Band Rio"
        ),
        VideoSource(
            id = "video-band-parana", name = "Band Paraná", group = "Band Regional", region = "Sul", state = "PR",
            landingUrl = "https://www.band.com.br/band-parana", linkHints = listOf("/band-parana/videos/", "/videos/"), aliases = listOf("Band Paraná", "Band PR"),
            searchUrlTemplate = "https://www.band.com.br/busca?q={query}", searchPrefix = "Band Paraná"
        ),
        VideoSource(
            id = "video-band-bahia", name = "Band Bahia", group = "Band Regional", region = "Nordeste", state = "BA",
            landingUrl = "https://www.band.com.br/ao-vivo/band-bahia", linkHints = listOf("/band-bahia/videos/", "/videos/"), aliases = listOf("Band Bahia", "Band BA"),
            searchUrlTemplate = "https://www.band.com.br/busca?q={query}", searchPrefix = "Band Bahia"
        )
    )

    val regional: List<VideoSource> = bandRegional + portalProgramsRegional + globoplayTelejournalsRegional
    val all: List<VideoSource> = national + regional
    val byId: Map<String, VideoSource> = all.associateBy { it.id }
    val youtubeOfficialIds: Set<String> = youtubeOfficial.map { it.id }.toSet()
    val globoplayTelejournalIds: Set<String> = (globoplayTelejournalsNational + globoplayRegionalSpecific).map { it.id }.toSet()
    val globoplayRegionalSweepIds: Set<String> = globoplayRegionalSweeps.map { it.id }.toSet()
    val portalProgramScanIds: Set<String> = (portalProgramsNational + portalProgramsRegional + bandRegional).map { it.id }.toSet() +
        setOf("video-r7-record", "video-sbt-news", "video-band")
    val v3011StarterIds: Set<String> = setOf(
        "video-band-jornal-da-band",
        "video-band-brasil-urgente",
        "video-r7-jornal-da-record",
        "video-r7-domingo-espetacular",
        "video-r7-balanco-geral-sp",
        "globoplay-fantastico"
    )
    val defaultIds: Set<String> = national.map { it.id }.toSet()

    fun selected(ids: Set<String>): List<VideoSource> = ids.mapNotNull(byId::get)

    private fun youtube(id: String, label: String, handle: String, aliases: List<String>): VideoSource = VideoSource(
        id = id,
        name = "YouTube • $label",
        group = "YouTube oficial • $label",
        landingUrl = "https://www.youtube.com/$handle/videos",
        linkHints = listOf("/watch"),
        aliases = aliases,
        youtubeHandle = handle
    )

    private fun nationalGlobo(id: String, program: String): VideoSource = VideoSource(
        id = id,
        name = "Globoplay • $program",
        group = "Globo / Globoplay • Telejornal nacional",
        landingUrl = globoplaySearch(program),
        linkHints = listOf("/v/"),
        aliases = listOf("Globo", "Globoplay", program),
        searchUrlTemplate = "https://globoplay.globo.com/busca/?q={query}",
        searchPrefix = program
    )

    private fun regionalGlobo(
        id: String,
        program: String,
        state: String,
        region: String,
        aliases: List<String> = emptyList(),
        landingOverride: String = ""
    ): VideoSource = VideoSource(
        id = id,
        name = "Globoplay • $program",
        group = "Globo / Globoplay • Telejornal regional",
        region = region,
        state = state,
        landingUrl = landingOverride.ifBlank { globoplaySearch(program) },
        linkHints = listOf("/v/"),
        aliases = (listOf("Globo", "Globoplay", program) + aliases).distinct(),
        searchUrlTemplate = "https://globoplay.globo.com/busca/?q={query}",
        searchPrefix = program
    )

    private fun globoplaySweep(id: String, name: String, program: String, aliases: List<String>): VideoSource = VideoSource(
        id = id,
        name = name,
        group = "Globo / Globoplay • Cobertura regional ampla",
        region = "Todas",
        state = "BR",
        landingUrl = globoplaySearch(program),
        linkHints = listOf("/v/"),
        aliases = (REGIONAL_GLOBO_ALIASES + aliases).distinct(),
        searchUrlTemplate = "https://globoplay.globo.com/busca/?q={query}",
        searchPrefix = program
    )

    private fun globoplaySearch(program: String): String =
        "https://globoplay.globo.com/busca/?q=${URLEncoder.encode(program, "UTF-8")}"

    private val REGIONAL_GLOBO_ALIASES get() = listOf(
        "TV Globo", "Globo SP", "Globo Rio", "Globo Minas", "Globo Brasília", "Globo Pernambuco",
        "Rede Amazônica", "TV Acre", "TV Amapá", "TV Amazonas", "TV Rondônia", "TV Roraima",
        "TV Gazeta", "TV Gazeta AL", "TV Gazeta ES", "TV Bahia", "TV Verdes Mares", "TV Anhanguera",
        "TV Mirante", "TV Centro América", "TV Morena", "TV Liberal", "TV Tapajós", "TV Cabo Branco",
        "RPC", "TV Clube", "Inter TV Cabugi", "RBS TV", "NSC TV", "EPTV", "TV TEM", "TV Tribuna",
        "TV Fronteira", "TV Sergipe", "TV Anhanguera Tocantins"
    )
}
