package br.com.monitordenoticias.android

data class VideoItem(
    val id: Long = 0,
    val title: String,
    val sourceId: String,
    val sourceName: String,
    val publishedAt: Long,
    val link: String,
    val summary: String = "",
    val matchedTerm: String = "",
    val matchedDemand: String = "",
    val capturedAt: Long = System.currentTimeMillis()
) {
    val relevant: Boolean get() = matchedTerm.isNotBlank() || matchedDemand.isNotBlank()
    val demand: Boolean get() = matchedDemand.isNotBlank()
}

data class VideoSource(
    val id: String,
    val name: String,
    val group: String,
    val region: String = "Nacional",
    val state: String = "",
    val landingUrl: String,
    val linkHints: List<String> = emptyList(),
    val aliases: List<String> = emptyList(),
    val youtubeHandle: String = "",
    val searchUrlTemplate: String = "",
    val searchPrefix: String = ""
)

data class VideoSourceIssue(
    val sourceId: String,
    val sourceName: String,
    val failureCount: Int,
    val stage: String
)

data class VideoSearchResult(
    val items: List<VideoItem>,
    val foundCount: Int,
    val newCount: Int,
    val relevantCount: Int,
    val newRelevantCount: Int,
    val errors: Int,
    val unstableSources: List<VideoSourceIssue> = emptyList()
)

data class VideoSearchUpdate(
    val progress: LiveSearchProgress,
    val items: List<VideoItem> = emptyList()
)

data class VideoState(
    val items: List<VideoItem> = emptyList(),
    val selectedSourceIds: Set<String> = emptySet(),
    val busy: Boolean = false,
    val status: String = "Pronto",
    val filter: VideoFilter = VideoFilter.ALL,
    val lastManualAt: Long = 0L,
    val periodStartDate: String = "",
    val periodStartTime: String = "",
    val periodEndDate: String = "",
    val periodEndTime: String = "",
    val searchProgress: LiveSearchProgress = LiveSearchProgress(),
    val unstableSources: List<VideoSourceIssue> = emptyList(),
    val videoTerms: List<String> = emptyList(),
    val totalStored: Int = 0,
    val capturedToday: Int = 0
)

enum class VideoFilter { ALL, RELEVANT, DEMANDS }
