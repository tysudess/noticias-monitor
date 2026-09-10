package br.com.monitordenoticias.android

data class News(
    val id: Long = 0,
    val title: String,
    val source: String,
    val date: Long,
    val link: String,
    val snippet: String = "",
    val important: Boolean = false,
    val demand: Boolean = false,
    val matchedTerm: String = "",
    val matchedDemand: String = "",
    val capturedAt: Long = System.currentTimeMillis()
)

data class Demand(
    val id: Long = 0,
    val vehicle: String,
    val subject: String,
    val active: Boolean = true,
    val lastCheckedAt: Long = 0,
    val lastFoundCount: Int = 0,
    val lastNewCount: Int = 0,
    val lastError: String = ""
)

data class DemandSearchResult(
    val demand: Demand,
    val items: List<News>,
    val foundCount: Int,
    val newCount: Int,
    val error: String? = null
)

data class DemandSweepResult(
    val checkedCount: Int,
    val foundCount: Int,
    val newCount: Int,
    val errors: Int,
    val items: List<News>
)

data class SearchResult(
    val items: List<News>,
    val foundCount: Int,
    val newCount: Int,
    val newDemandCount: Int,
    val errors: Int = 0
)

data class NewsSearchUpdate(
    val progress: LiveSearchProgress,
    val items: List<News> = emptyList()
)

data class AppState(
    val news: List<News> = emptyList(),
    val history: List<News> = emptyList(),
    val demands: List<Demand> = emptyList(),
    val terms: List<String> = emptyList(),
    val selectedTab: Int = 0,
    val busy: Boolean = false,
    val demandSearchBusy: Boolean = false,
    val demandBusyId: Long? = null,
    val status: String = "Pronto",
    val intervalMinutes: Int = 30,
    val lastUpdatedAt: Long? = null,
    val showOnlyDemands: Boolean = false,
    val selectedSourceIds: Set<String> = emptySet(),
    val searchAllSources: Boolean = true,
    val periodStartDate: String = "",
    val periodStartTime: String = "00:00",
    val periodEndDate: String = "",
    val periodEndTime: String = "23:59",
    val autoNewsAttemptAt: Long = 0L,
    val autoNewsCompletedAt: Long = 0L,
    val autoNewsFound: Int = 0,
    val autoNewsNew: Int = 0,
    val autoNewsErrors: Int = 0,
    val autoNewsErrorText: String = "",
    val autoDemandAttemptAt: Long = 0L,
    val autoDemandCompletedAt: Long = 0L,
    val autoDemandChecked: Int = 0,
    val autoDemandFound: Int = 0,
    val autoDemandNew: Int = 0,
    val autoDemandErrors: Int = 0,
    val autoDemandErrorText: String = "",
    val nextBackgroundHeartbeatAt: Long = 0L,
    val searchProgress: LiveSearchProgress = LiveSearchProgress()
)
