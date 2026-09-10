package br.com.monitordenoticias.android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class VideoViewModel(app: Application) : AndroidViewModel(app) {
    private val db = VideoDb(app).apply {
        removeInvalidListingEntries()
        repairStoredMatches()
    }
    private val repo = VideoRepository(app, db)
    private val prefs = app.getSharedPreferences(BackgroundMonitor.PREFS, 0)
    private val locale = Locale("pt", "BR")

    private val savedIds = loadSelectedSourcesForV29()
    private val now = System.currentTimeMillis()
    private val defaultFrom = now - 7L * 24L * 60L * 60L * 1000L
    private val initialStats = currentStats()
    private val initialVideoTerms = loadVideoTerms()

    private fun loadSelectedSourcesForV29(): Set<String> {
        val existing = prefs.getStringSet(KEY_SELECTED_SOURCES, null)
            ?.filter { VideoSourceCatalog.byId.containsKey(it) }
            ?.toSet()

        if (existing == null) {
            val defaults = VideoSourceCatalog.defaultIds
            prefs.edit()
                .putStringSet(KEY_SELECTED_SOURCES, defaults)
                .putBoolean(KEY_YOUTUBE_283_MIGRATED, true)
                .putBoolean(KEY_GLOBOPLAY_TELEJOURNALS_284_MIGRATED, true)
                .putBoolean(KEY_GLOBOPLAY_REGIONAL_SWEEPS_285_MIGRATED, true)
                .apply()
            return defaults
        }

        var selected = existing
        var changed = false
        val editor = prefs.edit()

        if (!prefs.getBoolean(KEY_YOUTUBE_283_MIGRATED, false)) {
            selected = selected + VideoSourceCatalog.youtubeOfficialIds
            editor.putBoolean(KEY_YOUTUBE_283_MIGRATED, true)
            changed = true
        }
        if (!prefs.getBoolean(KEY_GLOBOPLAY_TELEJOURNALS_284_MIGRATED, false)) {
            selected = selected + V284_STARTER_IDS.filter { VideoSourceCatalog.byId.containsKey(it) }
            editor.putBoolean(KEY_GLOBOPLAY_TELEJOURNALS_284_MIGRATED, true)
            changed = true
        }
        if (!prefs.getBoolean(KEY_GLOBOPLAY_REGIONAL_SWEEPS_285_MIGRATED, false)) {
            selected = selected + VideoSourceCatalog.globoplayRegionalSweepIds
            editor.putBoolean(KEY_GLOBOPLAY_REGIONAL_SWEEPS_285_MIGRATED, true)
            changed = true
        }
        if (!prefs.getBoolean(KEY_PROGRAM_SOURCES_3011_MIGRATED, false)) {
            selected = selected + VideoSourceCatalog.v3011StarterIds.filter { VideoSourceCatalog.byId.containsKey(it) }
            editor.putBoolean(KEY_PROGRAM_SOURCES_3011_MIGRATED, true)
            changed = true
        }

        if (changed) editor.putStringSet(KEY_SELECTED_SOURCES, selected).apply()
        return selected
    }

    private fun scopedItems(): List<VideoItem> = db.listRecent().filter { it.relevant }

    private val _state = MutableStateFlow(
        VideoState(
            items = scopedItems(),
            selectedSourceIds = savedIds,
            lastManualAt = prefs.getLong(KEY_LAST_MANUAL, 0L),
            periodStartDate = prefs.getString(KEY_PERIOD_START_DATE, formatDate(defaultFrom)) ?: formatDate(defaultFrom),
            periodStartTime = prefs.getString(KEY_PERIOD_START_TIME, formatTime(defaultFrom)) ?: formatTime(defaultFrom),
            periodEndDate = prefs.getString(KEY_PERIOD_END_DATE, formatDate(now)) ?: formatDate(now),
            periodEndTime = prefs.getString(KEY_PERIOD_END_TIME, formatTime(now)) ?: formatTime(now),
            totalStored = initialStats.first,
            capturedToday = initialStats.second,
            videoTerms = initialVideoTerms
        )
    )
    val state: StateFlow<VideoState> = _state

    init {
        VideoBackgroundMonitor.scheduleAll(app)
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            db.removeInvalidListingEntries()
            db.repairStoredMatches()
            val stats = currentStats()
            _state.value = _state.value.copy(
                items = scopedItems(),
                totalStored = stats.first,
                capturedToday = stats.second,
                videoTerms = loadVideoTerms()
            )
        }
    }

    fun searchNow() {
        if (_state.value.busy) return
        val selected = VideoSourceCatalog.selected(_state.value.selectedSourceIds)
        if (selected.isEmpty()) {
            _state.value = _state.value.copy(status = "⚠ Selecione pelo menos uma fonte de vídeo")
            return
        }
        val started = System.currentTimeMillis()
        _state.value = _state.value.copy(
            busy = true,
            status = "Buscando vídeos das últimas 24h...",
            searchProgress = LiveSearchProgress(active = true, kind = "Vídeos", startedAt = started),
            unstableSources = emptyList()
        )
        viewModelScope.launch {
            val result = repo.searchProgressive(selected) { update -> publishVideoUpdate(update, false) }
            db.removeInvalidListingEntries()
            db.repairStoredMatches()
            val finished = System.currentTimeMillis()
            prefs.edit().putLong(KEY_LAST_MANUAL, finished).apply()
            val stats = currentStats()
            val status = when {
                result.errors > 0 && result.foundCount == 0 -> "⚠ Busca concluída sem vídeos diretos • ${result.errors} fonte(s) instável(is)"
                result.newCount > 0 -> "✓ ${result.newCount} novo(s) vídeo(s) • ${result.foundCount} encontrado(s)"
                result.foundCount > 0 -> "✓ Busca concluída • ${result.foundCount} vídeo(s) encontrado(s)"
                else -> "✓ Busca concluída • nenhum vídeo para os Termos/Demandas"
            }
            _state.value = _state.value.copy(
                items = scopedItems(),
                busy = false,
                status = status,
                lastManualAt = finished,
                totalStored = stats.first,
                capturedToday = stats.second,
                searchProgress = _state.value.searchProgress.copy(active = false, finishedAt = finished),
                unstableSources = result.unstableSources
            )
        }
    }

    fun searchSavedPeriod() {
        val current = _state.value
        if (current.busy) return
        val selected = VideoSourceCatalog.selected(current.selectedSourceIds)
        if (selected.isEmpty()) {
            _state.value = current.copy(status = "⚠ Selecione pelo menos uma fonte de vídeo")
            return
        }
        val from = parseDateTime(current.periodStartDate, current.periodStartTime)
        val to = parseDateTime(current.periodEndDate, current.periodEndTime)
        if (from == null || to == null) {
            _state.value = current.copy(status = "⚠ Data ou hora inválida. Use dd/MM/aaaa e HH:mm.")
            return
        }
        if (from >= to) {
            _state.value = current.copy(status = "⚠ Período inválido: o início precisa ser anterior ao fim.")
            return
        }

        val started = System.currentTimeMillis()
        _state.value = current.copy(
            items = emptyList(),
            busy = true,
            status = "Pesquisando vídeos no período em tempo real...",
            searchProgress = LiveSearchProgress(active = true, kind = "Vídeos • Período", startedAt = started),
            unstableSources = emptyList()
        )
        viewModelScope.launch {
            val result = repo.searchPeriodProgressive(selected, from, to) { update -> publishVideoUpdate(update, true) }
            db.removeInvalidListingEntries()
            db.repairStoredMatches()
            val selectedIds = _state.value.selectedSourceIds
            val periodItems = db.listPeriod(from, to)
                .filter { it.relevant && it.sourceId in selectedIds }
            val finished = System.currentTimeMillis()
            val stats = currentStats()
            val status = when {
                result.errors > 0 && periodItems.isEmpty() -> "⚠ Pesquisa do período concluída sem vídeos • ${result.errors} fonte(s) instável(is)"
                periodItems.isNotEmpty() -> "✓ Período: ${periodItems.size} vídeo(s) relacionado(s) aos Termos/Demandas"
                else -> "✓ Período pesquisado • nenhum vídeo relacionado encontrado"
            }
            _state.value = _state.value.copy(
                items = periodItems,
                busy = false,
                status = status,
                lastManualAt = finished,
                totalStored = stats.first,
                capturedToday = stats.second,
                searchProgress = _state.value.searchProgress.copy(active = false, finishedAt = finished),
                unstableSources = result.unstableSources
            )
        }
    }

    private fun publishVideoUpdate(update: VideoSearchUpdate, periodMode: Boolean) {
        val current = _state.value
        val mergedItems = if (update.items.isEmpty()) current.items else {
            val map = linkedMapOf<String, VideoItem>()
            current.items.forEach { map[it.link] = it }
            update.items.filter { it.relevant }.forEach { map[it.link] = it }
            map.values.sortedByDescending { it.publishedAt }
        }
        val progress = update.progress
        val liveStatus = if (progress.active) {
            val position = if (progress.total > 0) "${progress.completed}/${progress.total}" else "…"
            "Buscando • $position • ${progress.currentSource}"
        } else current.status
        _state.value = current.copy(
            items = mergedItems,
            busy = progress.active,
            status = liveStatus,
            searchProgress = progress,
            totalStored = current.totalStored + update.items.count { it.capturedAt >= progress.startedAt && it.id > 0L }.coerceAtLeast(0)
        )
    }

    fun setPeriodStartDate(value: String) = updatePeriod { it.copy(periodStartDate = value) }
    fun setPeriodStartTime(value: String) = updatePeriod { it.copy(periodStartTime = value) }
    fun setPeriodEndDate(value: String) = updatePeriod { it.copy(periodEndDate = value) }
    fun setPeriodEndTime(value: String) = updatePeriod { it.copy(periodEndTime = value) }

    fun applyPeriodPreset(days: Int) {
        val end = System.currentTimeMillis()
        val start = when (days) {
            0 -> SimpleDateFormat("dd/MM/yyyy", locale).parse(formatDate(end))?.time ?: end
            1 -> end - 24L * 60L * 60L * 1000L
            else -> end - days.toLong() * 24L * 60L * 60L * 1000L
        }
        val updated = _state.value.copy(
            periodStartDate = formatDate(start),
            periodStartTime = if (days == 0) "00:00" else formatTime(start),
            periodEndDate = formatDate(end),
            periodEndTime = formatTime(end),
            status = "✓ Período de vídeos atualizado"
        )
        _state.value = updated
        persistPeriod(updated)
    }

    fun setSourceSelected(id: String, selected: Boolean) {
        if (!VideoSourceCatalog.byId.containsKey(id)) return
        val next = _state.value.selectedSourceIds.toMutableSet().apply {
            if (selected) add(id) else remove(id)
        }.toSet()
        prefs.edit().putStringSet(KEY_SELECTED_SOURCES, next).apply()
        _state.value = _state.value.copy(selectedSourceIds = next, status = "✓ ${next.size} fonte(s) de vídeo selecionada(s)")
    }

    fun setSources(ids: Set<String>, selected: Boolean) {
        val valid = ids.filter { VideoSourceCatalog.byId.containsKey(it) }.toSet()
        val next = _state.value.selectedSourceIds.toMutableSet().apply {
            if (selected) addAll(valid) else removeAll(valid)
        }.toSet()
        prefs.edit().putStringSet(KEY_SELECTED_SOURCES, next).apply()
        _state.value = _state.value.copy(selectedSourceIds = next, status = "✓ ${next.size} fonte(s) de vídeo selecionada(s)")
    }

    fun addVideoTerm(value: String) {
        val next = VideoTermStore.add(getApplication(), value, newsTermSeed())
        _state.value = _state.value.copy(videoTerms = next, status = "✓ Termo de vídeo adicionado")
    }

    fun removeVideoTerm(value: String) {
        val next = VideoTermStore.remove(getApplication(), value, newsTermSeed())
        _state.value = _state.value.copy(videoTerms = next, status = "✓ Termo de vídeo removido")
    }

    private fun loadVideoTerms(): List<String> = VideoTermStore.load(getApplication(), newsTermSeed())

    private fun newsTermSeed(): List<String> {
        val newsDb = NewsDb(getApplication())
        return try {
            newsDb.listTerms().ifEmpty { DEFAULT_VIDEO_TERM_SEED }
        } finally {
            newsDb.close()
        }
    }

    fun setFilter(filter: VideoFilter) {
        _state.value = _state.value.copy(filter = filter, status = "Pronto")
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            db.clear()
            _state.value = _state.value.copy(
                items = emptyList(),
                totalStored = 0,
                capturedToday = 0,
                status = "✓ Histórico de vídeos limpo"
            )
        }
    }

    private fun currentStats(): Pair<Int, Int> {
        val relevant = db.listAll(1000).filter { it.relevant }
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return relevant.size to relevant.count { it.capturedAt >= startOfDay }
    }

    private fun updatePeriod(block: (VideoState) -> VideoState) {
        val updated = block(_state.value)
        _state.value = updated
        persistPeriod(updated)
    }

    private fun persistPeriod(state: VideoState) {
        prefs.edit()
            .putString(KEY_PERIOD_START_DATE, state.periodStartDate)
            .putString(KEY_PERIOD_START_TIME, state.periodStartTime)
            .putString(KEY_PERIOD_END_DATE, state.periodEndDate)
            .putString(KEY_PERIOD_END_TIME, state.periodEndTime)
            .apply()
    }

    private fun parseDateTime(date: String, time: String): Long? = runCatching {
        SimpleDateFormat("dd/MM/yyyy HH:mm", locale).apply { isLenient = false }.parse("$date $time")?.time
    }.getOrNull()

    private fun formatDate(ms: Long): String = SimpleDateFormat("dd/MM/yyyy", locale).format(Date(ms))
    private fun formatTime(ms: Long): String = SimpleDateFormat("HH:mm", locale).format(Date(ms))

    override fun onCleared() {
        db.close()
        super.onCleared()
    }

    companion object {
        const val KEY_SELECTED_SOURCES = "video_selected_source_ids"
        const val KEY_LAST_MANUAL = "video_last_manual_at"
        const val KEY_YOUTUBE_283_MIGRATED = "video_v283_youtube_sources_added"
        const val KEY_GLOBOPLAY_TELEJOURNALS_284_MIGRATED = "video_v284_globoplay_telejournals_added"
        const val KEY_GLOBOPLAY_REGIONAL_SWEEPS_285_MIGRATED = "video_v285_globoplay_regional_sweeps_added"
        const val KEY_PROGRAM_SOURCES_3011_MIGRATED = "video_v3011_program_sources_added"
        const val KEY_PERIOD_START_DATE = "video_period_start_date"
        const val KEY_PERIOD_START_TIME = "video_period_start_time"
        const val KEY_PERIOD_END_DATE = "video_period_end_date"
        const val KEY_PERIOD_END_TIME = "video_period_end_time"

        private val DEFAULT_VIDEO_TERM_SEED = listOf(
            "Marinha do Brasil", "Capitania dos Portos", "Distrito Naval", "NAM Atlântico",
            "Cisne Branco", "Fragata Marinha do Brasil", "Navio-Patrulha Marinha", "Programa Nuclear da Marinha"
        )

        private val V284_STARTER_IDS = setOf(
            "globoplay-bom-dia-brasil", "globoplay-hora-1", "globoplay-jornal-hoje", "globoplay-jornal-nacional", "globoplay-jornal-da-globo",
            "globoplay-bom-dia-sp", "globoplay-sp1", "globoplay-sp2", "globoplay-bom-dia-rio", "globoplay-rj1", "globoplay-rj2",
            "globoplay-bom-dia-es", "globoplay-gazeta-meio-dia-es", "globoplay-bom-dia-minas", "globoplay-mg1", "globoplay-df1",
            "globoplay-bom-dia-rio-grande", "globoplay-tj1-tapajos", "globoplay-tj2-tapajos"
        )
    }
}
