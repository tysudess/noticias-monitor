package br.com.monitordenoticias.android

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MonitorViewModel(app: Application) : AndroidViewModel(app) {
    private val db = NewsDb(app)
    private val repo = NewsRepository(db)
    private val prefs = app.getSharedPreferences("monitor_prefs", 0)
    private val locale = Locale("pt", "BR")
    private val autoRunListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == AutoRunLog.KEY_NEWS_COMPLETED_AT || key == AutoRunLog.KEY_DEMAND_COMPLETED_AT) {
            refresh()
        }
    }

    private val savedInterval = prefs.getInt("interval_minutes", 30).coerceAtLeast(15)
    private val savedSourceIds = prefs.getStringSet("selected_source_ids", emptySet())
        .orEmpty().filter { SourceCatalog.byId.containsKey(it) }.toSet()
    private val savedSearchAll = prefs.getBoolean("search_all_sources", true)

    private val now = System.currentTimeMillis()
    private val defaultFrom = now - 7L * 24L * 60L * 60L * 1000L

    private val _state = MutableStateFlow(
        AppState(
            intervalMinutes = savedInterval,
            selectedSourceIds = savedSourceIds,
            searchAllSources = savedSearchAll,
            periodStartDate = prefs.getString("period_start_date", formatDate(defaultFrom)) ?: formatDate(defaultFrom),
            periodStartTime = prefs.getString("period_start_time", formatTime(defaultFrom)) ?: formatTime(defaultFrom),
            periodEndDate = prefs.getString("period_end_date", formatDate(now)) ?: formatDate(now),
            periodEndTime = prefs.getString("period_end_time", formatTime(now)) ?: formatTime(now)
        )
    )
    val state: StateFlow<AppState> = _state

    init {
        prefs.registerOnSharedPreferenceChangeListener(autoRunListener)
        refresh()
        schedule(savedInterval)
        scheduleDemandMonitor()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val current = _state.value
            val recent = scopedRecent(current)
            publish {
                it.copy(
                    news = recent,
                    history = db.listNews(),
                    terms = db.listTerms(),
                    demands = db.listDemands()
                )
            }
        }
    }

    fun search() {
        if (_state.value.busy) return
        val current = _state.value
        if (!current.searchAllSources && current.selectedSourceIds.isEmpty()) {
            _state.value = current.copy(status = "⚠ Selecione pelo menos uma fonte ou ative ‘Buscar em todos os veículos’.")
            return
        }

        val started = System.currentTimeMillis()
        _state.value = current.copy(
            busy = true,
            status = sourceStatusPrefix("Buscando notícias em tempo real"),
            searchProgress = LiveSearchProgress(active = true, kind = "Notícias", startedAt = started)
        )
        viewModelScope.launch {
            val latest = _state.value
            val sources = SourceCatalog.selected(latest.selectedSourceIds)
            val result = repo.searchProgressive(sources, latest.searchAllSources) { update -> publishNewsUpdate(update) }
            val finished = System.currentTimeMillis()
            val status = when {
                result.errors > 0 && result.foundCount == 0 -> "⚠ Não foi possível consultar as fontes. Verifique sua conexão."
                result.errors > 0 -> "Busca parcial: ${result.newCount} nova(s) • ${result.errors} consulta(s) falharam"
                result.newCount == 0 -> "Busca concluída: ${result.foundCount} resultado(s) no escopo selecionado"
                result.newDemandCount > 0 -> "✓ ${result.newCount} nova(s) • ${result.newDemandCount} demanda(s) encontrada(s)"
                else -> "✓ ${result.newCount} nova(s) notícia(s) encontrada(s)"
            }
            // Reconstrói a lista a partir do banco após a busca. Assim nenhum capturedAt
            // temporário vindo do RSS sobrevive quando a matéria já existia no histórico.
            val persistedNews = scopedRecent(_state.value)
            _state.value = _state.value.copy(
                news = persistedNews,
                history = db.listNews(),
                busy = false,
                status = status,
                lastUpdatedAt = finished,
                searchProgress = _state.value.searchProgress.copy(active = false, finishedAt = finished)
            )
        }
    }

    private fun publishNewsUpdate(update: NewsSearchUpdate) {
        val current = _state.value
        val merged = if (update.items.isEmpty()) current.news else {
            val map = linkedMapOf<String, News>()
            current.news.forEach { map[it.link] = it }
            update.items.forEach { map[it.link] = it }
            map.values.sortedByDescending { it.date }
        }
        val p = update.progress
        val status = if (p.active) {
            val pos = if (p.total > 0) "${p.completed}/${p.total}" else "…"
            "Buscando • $pos • ${p.currentSource}"
        } else current.status
        _state.value = current.copy(
            news = merged,
            history = if (update.items.isEmpty()) current.history else mergeHistory(current.history, update.items),
            busy = p.active,
            status = status,
            searchProgress = p,
            lastUpdatedAt = System.currentTimeMillis()
        )
    }

    private fun mergeHistory(current: List<News>, incoming: List<News>): List<News> {
        val map = linkedMapOf<String, News>()
        current.forEach { map[it.link] = it }
        incoming.forEach { map[it.link] = it }
        return map.values.sortedByDescending { it.date }.take(500)
    }

    fun searchAllDemandsNow() {
        if (_state.value.demandSearchBusy) return
        val active = _state.value.demands.filter { it.active }
        if (active.isEmpty()) {
            _state.value = _state.value.copy(status = "Nenhuma demanda ativa para pesquisar")
            return
        }
        val started = System.currentTimeMillis()
        _state.value = _state.value.copy(
            demandSearchBusy = true,
            demandBusyId = null,
            status = "Buscando demandas em tempo real...",
            searchProgress = LiveSearchProgress(active = true, kind = "Demandas", startedAt = started, total = active.size)
        )
        viewModelScope.launch {
            var found = 0
            var fresh = 0
            var errors = 0
            active.forEachIndexed { index, demand ->
                _state.value = _state.value.copy(
                    searchProgress = _state.value.searchProgress.copy(
                        currentSource = demand.vehicle,
                        currentQuery = demand.subject,
                        completed = index,
                        found = found,
                        newCount = fresh,
                        errors = errors
                    ),
                    status = "Demandas • $index/${active.size} • ${demand.vehicle}"
                )
                val result = repo.searchDemand(demand)
                found += result.foundCount
                fresh += result.newCount
                if (result.error != null) errors++
                _state.value = _state.value.copy(
                    demands = db.listDemands(),
                    history = db.listNews(),
                    searchProgress = _state.value.searchProgress.copy(
                        completed = index + 1,
                        found = found,
                        newCount = fresh,
                        errors = errors
                    )
                )
            }
            val finished = System.currentTimeMillis()
            val status = when {
                errors == active.size -> "⚠ As buscas de demandas falharam"
                found > 0 -> "✓ $found resultado(s) de demanda encontrado(s)"
                else -> "Pronto"
            }
            _state.value = _state.value.copy(
                demandSearchBusy = false,
                demandBusyId = null,
                demands = db.listDemands(),
                history = db.listNews(),
                status = status,
                searchProgress = _state.value.searchProgress.copy(
                    active = false,
                    finishedAt = finished,
                    completed = active.size,
                    found = found,
                    newCount = fresh,
                    errors = errors
                )
            )
        }
    }

    fun searchDemandNow(id: Long) {
        if (_state.value.demandSearchBusy || _state.value.demandBusyId != null) return
        val demand = _state.value.demands.firstOrNull { it.id == id } ?: return
        val started = System.currentTimeMillis()
        _state.value = _state.value.copy(
            demandBusyId = id,
            status = "Buscando ${demand.vehicle}...",
            searchProgress = LiveSearchProgress(
                active = true,
                kind = "Demanda",
                startedAt = started,
                total = 1,
                currentSource = demand.vehicle,
                currentQuery = demand.subject
            )
        )
        viewModelScope.launch {
            val result = repo.searchDemand(demand)
            val finished = System.currentTimeMillis()
            val status = if (result.error != null) {
                "⚠ Falha ao pesquisar ${demand.vehicle}"
            } else if (result.foundCount > 0) {
                "✓ ${demand.vehicle}: ${result.foundCount} resultado(s) encontrado(s)"
            } else {
                "Pronto"
            }
            _state.value = _state.value.copy(
                demandBusyId = null,
                demands = db.listDemands(),
                history = db.listNews(),
                status = status,
                searchProgress = _state.value.searchProgress.copy(
                    active = false,
                    finishedAt = finished,
                    completed = 1,
                    found = result.foundCount,
                    newCount = result.newCount,
                    errors = if (result.error != null) 1 else 0
                )
            )
        }
    }

    fun searchSavedPeriod() {
        val current = _state.value
        if (current.busy) return
        if (!current.searchAllSources && current.selectedSourceIds.isEmpty()) {
            _state.value = current.copy(status = "⚠ Selecione pelo menos uma fonte ou ative a busca em todos os veículos.")
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
        searchPeriod(from, to)
    }

    private fun searchPeriod(from: Long, to: Long) {
        val started = System.currentTimeMillis()
        _state.value = _state.value.copy(
            news = emptyList(),
            busy = true,
            status = "Pesquisando período em tempo real...",
            searchProgress = LiveSearchProgress(active = true, kind = "Notícias • Período", startedAt = started)
        )
        viewModelScope.launch {
            val latest = _state.value
            val result = repo.searchPeriodProgressive(
                from,
                to,
                SourceCatalog.selected(latest.selectedSourceIds),
                latest.searchAllSources
            ) { update -> publishNewsUpdate(update.copy(progress = update.progress.copy(kind = "Notícias • Período"))) }
            val finished = System.currentTimeMillis()
            val status = if (result.errors > 0 && result.foundCount == 0) "⚠ Não foi possível concluir a pesquisa externa."
            else "Período: ${result.foundCount} matéria(s) no escopo selecionado"
            _state.value = _state.value.copy(
                news = result.items,
                history = db.listNews(),
                busy = false,
                status = status,
                lastUpdatedAt = finished,
                searchProgress = _state.value.searchProgress.copy(active = false, finishedAt = finished)
            )
        }
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
        val newState = _state.value.copy(
            periodStartDate = formatDate(start),
            periodStartTime = if (days == 0) "00:00" else formatTime(start),
            periodEndDate = formatDate(end),
            periodEndTime = formatTime(end),
            status = "✓ Período atualizado"
        )
        _state.value = newState
        persistPeriod(newState)
    }

    fun setSourceSelected(id: String, selected: Boolean) {
        if (!SourceCatalog.byId.containsKey(id)) return
        val ids = _state.value.selectedSourceIds.toMutableSet().apply { if (selected) add(id) else remove(id) }.toSet()
        val searchAll = if (selected) false else _state.value.searchAllSources
        prefs.edit().putStringSet("selected_source_ids", ids).putBoolean("search_all_sources", searchAll).apply()
        _state.value = _state.value.copy(selectedSourceIds = ids, searchAllSources = searchAll, status = "✓ ${ids.size} fonte(s) selecionada(s)")
        refresh()
    }

    fun setVisibleSources(ids: Set<String>, selected: Boolean) {
        val valid = ids.filter { SourceCatalog.byId.containsKey(it) }.toSet()
        val result = _state.value.selectedSourceIds.toMutableSet().apply { if (selected) addAll(valid) else removeAll(valid) }.toSet()
        val searchAll = if (selected && valid.isNotEmpty()) false else _state.value.searchAllSources
        prefs.edit().putStringSet("selected_source_ids", result).putBoolean("search_all_sources", searchAll).apply()
        _state.value = _state.value.copy(selectedSourceIds = result, searchAllSources = searchAll, status = if (selected) "✓ Fontes visíveis selecionadas" else "✓ Fontes visíveis desmarcadas")
        refresh()
    }

    fun setSearchAllSources(enabled: Boolean) {
        prefs.edit().putBoolean("search_all_sources", enabled).apply()
        _state.value = _state.value.copy(searchAllSources = enabled, status = if (enabled) "✓ Busca aberta ativada" else "Selecione as fontes desejadas")
        refresh()
    }

    fun addTerm(value: String) { viewModelScope.launch(Dispatchers.IO) { db.addTerm(value); refresh() } }
    fun removeTerm(value: String) { viewModelScope.launch(Dispatchers.IO) { db.removeTerm(value); refresh() } }
    fun addDemand(vehicle: String, subject: String) { viewModelScope.launch(Dispatchers.IO) { db.addDemand(vehicle, subject); refresh() } }
    fun removeDemand(id: Long) { viewModelScope.launch(Dispatchers.IO) { db.removeDemand(id); refresh() } }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            db.clearHistory(); refresh(); publish { it.copy(status = "✓ Histórico limpo com sucesso") }
        }
    }

    fun setTab(tab: Int) { _state.value = _state.value.copy(selectedTab = tab, status = "Pronto") }
    fun setDemandFilter(enabled: Boolean) { _state.value = _state.value.copy(showOnlyDemands = enabled) }

    fun setInterval(minutes: Int) {
        val safe = minutes.coerceAtLeast(15)
        prefs.edit().putInt("interval_minutes", safe).apply()
        _state.value = _state.value.copy(intervalMinutes = safe, status = "✓ Intervalo salvo: $safe min")
        schedule(safe)
    }

    private fun scopedRecent(state: AppState): List<News> {
        val recent = db.listRecent()
        if (state.searchAllSources) return recent
        val selected = SourceCatalog.selected(state.selectedSourceIds)
        if (selected.isEmpty()) return emptyList()
        return recent.filter { news -> selected.any { source -> sourceMatchesStrict(news.source, source) } }
    }

    private fun sourceMatchesStrict(actualSource: String, selected: MediaSource): Boolean {
        val actualNormalized = normalize(actualSource)
        val actualKey = compact(actualSource)
        val hostKey = publisherHostKey(actualSource)
        return (listOf(selected.name) + selected.aliases).any { candidate ->
            val candidateNormalized = normalize(candidate)
            val candidateKey = compact(candidate)
            if (candidateNormalized.isBlank() || candidateKey.isBlank()) false
            else if (actualNormalized == candidateNormalized || actualKey == candidateKey || hostKey == candidateKey) true
            else {
                val meaningfulTokens = candidateNormalized.split(' ').filter { it.length >= 2 && it !in setOf("de", "do", "da", "dos", "das") }
                meaningfulTokens.size >= 2 && candidateKey.length >= 6 && (actualKey.startsWith(candidateKey) || hostKey.startsWith(candidateKey))
            }
        }
    }

    private fun publisherHostKey(value: String): String {
        val cleaned = value.trim().lowercase()
        return compact(if ('.' in cleaned) cleaned.substringBefore('.') else cleaned)
    }

    private fun compact(value: String): String = normalize(value).replace(" ", "")
    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim()

    private fun updatePeriod(block: (AppState) -> AppState) {
        val updated = block(_state.value); _state.value = updated; persistPeriod(updated)
    }

    private fun persistPeriod(state: AppState) {
        prefs.edit().putString("period_start_date", state.periodStartDate).putString("period_start_time", state.periodStartTime)
            .putString("period_end_date", state.periodEndDate).putString("period_end_time", state.periodEndTime).apply()
    }

    private fun parseDateTime(date: String, time: String): Long? = runCatching {
        SimpleDateFormat("dd/MM/yyyy HH:mm", locale).apply { isLenient = false }.parse("$date $time")?.time
    }.getOrNull()

    private fun formatDate(ms: Long): String = SimpleDateFormat("dd/MM/yyyy", locale).format(Date(ms))
    private fun formatTime(ms: Long): String = SimpleDateFormat("HH:mm", locale).format(Date(ms))

    private fun sourceStatusPrefix(prefix: String): String {
        val state = _state.value
        return if (state.searchAllSources) "$prefix • qualquer veículo" else "$prefix • ${state.selectedSourceIds.size} fonte(s)"
    }

    private fun schedule(minutes: Int) {
        val req = PeriodicWorkRequestBuilder<MonitorWorker>(minutes.toLong(), TimeUnit.MINUTES).build()
        WorkManager.getInstance(getApplication()).enqueueUniquePeriodicWork("monitor_noticias", ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    private fun scheduleDemandMonitor() {
        val req = PeriodicWorkRequestBuilder<DemandMonitorWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(getApplication()).enqueueUniquePeriodicWork("monitor_demandas_1h", ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    private fun publish(block: (AppState) -> AppState) { _state.value = block(_state.value) }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(autoRunListener)
        db.close()
        super.onCleared()
    }
}
