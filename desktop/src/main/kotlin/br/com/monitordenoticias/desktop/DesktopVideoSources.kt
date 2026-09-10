package br.com.monitordenoticias.desktop

import br.com.monitordenoticias.android.VideoSource
import br.com.monitordenoticias.android.VideoSourceCatalog

/**
 * Fontes adicionais do aplicativo Windows.
 *
 * Mantemos este complemento fora do catálogo Android para permitir evoluir a
 * experiência Desktop sem alterar os coletores compartilhados. Como cada item
 * informa youtubeHandle, o VideoRepository usa o mesmo coletor oficial de
 * canais do YouTube usado pelas demais fontes.
 */
object DesktopVideoSources {
    val extras: List<VideoSource> = listOf(
        VideoSource(
            id = "youtube-g1",
            name = "YouTube • g1",
            group = "YouTube oficial • g1",
            landingUrl = "https://www.youtube.com/channel/UCaGmdJSSiR7fkh2A-c6emsA/videos",
            linkHints = listOf("/watch"),
            aliases = listOf("g1", "G1", "Globo", "Portal g1"),
            youtubeHandle = "@g1"
        ),
        VideoSource(
            id = "youtube-domingo-espetacular",
            name = "YouTube • Domingo Espetacular",
            group = "YouTube oficial • Domingo Espetacular",
            landingUrl = "https://www.youtube.com/channel/UCP-Vg2PcmLiWpEdvMI1R35w/videos",
            linkHints = listOf("/watch"),
            aliases = listOf("Domingo Espetacular", "Record", "Record TV"),
            youtubeHandle = "@domingoespetacular"
        )
    )

    val all: List<VideoSource> by lazy {
        (VideoSourceCatalog.all + extras).distinctBy { it.id }
    }

    val defaultIds: Set<String> by lazy {
        VideoSourceCatalog.defaultIds + extras.map { it.id }
    }

    private val byId: Map<String, VideoSource> by lazy { all.associateBy { it.id } }

    fun selected(ids: Set<String>): List<VideoSource> = ids.mapNotNull(byId::get)
}
