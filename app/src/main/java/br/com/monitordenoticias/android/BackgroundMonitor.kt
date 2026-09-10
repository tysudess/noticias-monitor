package br.com.monitordenoticias.android

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Centraliza o monitoramento em segundo plano.
 *
 * O WorkManager permanece como agendador principal. Um alarme inexacto permitido
 * durante idle funciona como heartbeat de recuperação: se o Android/Samsung
 * atrasar o trabalho periódico por tempo demais, o heartbeat solicita uma nova
 * execução. A busca continua respeitando as restrições de rede do Android.
 */
object BackgroundMonitor {
    const val PREFS = "monitor_prefs"
    private const val HEARTBEAT_REQUEST_CODE = 2601
    private const val HEARTBEAT_INTERVAL_MS = 60L * 60L * 1000L
    private const val STALE_AFTER_MS = 50L * 60L * 1000L

    private fun connectedConstraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun scheduleAll(context: Context, newsIntervalMinutes: Int) {
        val app = context.applicationContext
        scheduleNews(app, newsIntervalMinutes)
        scheduleDemands(app)
        scheduleHeartbeat(app)
    }

    fun scheduleNews(context: Context, minutes: Int) {
        val safe = minutes.coerceAtLeast(15)
        val request = PeriodicWorkRequestBuilder<MonitorWorker>(safe.toLong(), TimeUnit.MINUTES)
            .setConstraints(connectedConstraints())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "monitor_noticias",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun scheduleDemands(context: Context) {
        val request = PeriodicWorkRequestBuilder<DemandMonitorWorker>(1, TimeUnit.HOURS)
            .setConstraints(connectedConstraints())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "monitor_demandas_1h",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun scheduleHeartbeat(context: Context, delayMs: Long = HEARTBEAT_INTERVAL_MS) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(app, BackgroundHeartbeatReceiver::class.java)
            .setAction("br.com.monitordenoticias.android.BACKGROUND_HEARTBEAT")
        val pendingIntent = PendingIntent.getBroadcast(
            app,
            HEARTBEAT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val safeDelay = delayMs.coerceAtLeast(60_000L)
        val triggerElapsed = SystemClock.elapsedRealtime() + safeDelay
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerElapsed,
            pendingIntent
        )
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(AutoRunLog.KEY_NEXT_HEARTBEAT_AT, System.currentTimeMillis() + safeDelay)
            .apply()
    }

    /** Solicita trabalhos de recuperação somente quando a última tentativa está atrasada. */
    fun enqueueRecoveryIfStale(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val wm = WorkManager.getInstance(app)

        val lastNews = prefs.getLong(AutoRunLog.KEY_NEWS_ATTEMPT_AT, 0L)
        if (force || lastNews == 0L || now - lastNews >= STALE_AFTER_MS) {
            val news = OneTimeWorkRequestBuilder<MonitorWorker>()
                .setConstraints(connectedConstraints())
                .build()
            wm.enqueueUniqueWork("monitor_noticias_heartbeat", ExistingWorkPolicy.REPLACE, news)
        }

        val lastDemand = prefs.getLong(AutoRunLog.KEY_DEMAND_ATTEMPT_AT, 0L)
        if (force || lastDemand == 0L || now - lastDemand >= STALE_AFTER_MS) {
            val demand = OneTimeWorkRequestBuilder<DemandMonitorWorker>()
                .setConstraints(connectedConstraints())
                .build()
            wm.enqueueUniqueWork("monitor_demandas_heartbeat", ExistingWorkPolicy.REPLACE, demand)
        }
    }
}

object AutoRunLog {
    const val KEY_NEXT_HEARTBEAT_AT = "background_next_heartbeat_at"

    const val KEY_NEWS_ATTEMPT_AT = "auto_news_attempt_at"
    const val KEY_NEWS_COMPLETED_AT = "auto_news_completed_at"
    const val KEY_NEWS_FOUND = "auto_news_found"
    const val KEY_NEWS_NEW = "auto_news_new"
    const val KEY_NEWS_ERRORS = "auto_news_errors"
    const val KEY_NEWS_ERROR_TEXT = "auto_news_error_text"

    const val KEY_DEMAND_ATTEMPT_AT = "auto_demand_attempt_at"
    const val KEY_DEMAND_COMPLETED_AT = "auto_demand_completed_at"
    const val KEY_DEMAND_CHECKED = "auto_demand_checked"
    const val KEY_DEMAND_FOUND = "auto_demand_found"
    const val KEY_DEMAND_NEW = "auto_demand_new"
    const val KEY_DEMAND_ERRORS = "auto_demand_errors"
    const val KEY_DEMAND_ERROR_TEXT = "auto_demand_error_text"

    fun markNewsAttempt(context: Context) {
        context.getSharedPreferences(BackgroundMonitor.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_NEWS_ATTEMPT_AT, System.currentTimeMillis())
            .putString(KEY_NEWS_ERROR_TEXT, "")
            .apply()
    }

    fun markNewsCompleted(context: Context, result: SearchResult) {
        context.getSharedPreferences(BackgroundMonitor.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_NEWS_COMPLETED_AT, System.currentTimeMillis())
            .putInt(KEY_NEWS_FOUND, result.foundCount)
            .putInt(KEY_NEWS_NEW, result.newCount)
            .putInt(KEY_NEWS_ERRORS, result.errors)
            .putString(
                KEY_NEWS_ERROR_TEXT,
                if (result.errors > 0) "${result.errors} consulta(s) com falha" else ""
            )
            .apply()
    }

    fun markNewsFailed(context: Context, throwable: Throwable) {
        context.getSharedPreferences(BackgroundMonitor.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_NEWS_ERROR_TEXT, throwable.message?.take(180) ?: throwable.javaClass.simpleName)
            .apply()
    }

    fun markDemandAttempt(context: Context) {
        context.getSharedPreferences(BackgroundMonitor.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_DEMAND_ATTEMPT_AT, System.currentTimeMillis())
            .putString(KEY_DEMAND_ERROR_TEXT, "")
            .apply()
    }

    fun markDemandCompleted(context: Context, result: DemandSweepResult) {
        context.getSharedPreferences(BackgroundMonitor.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_DEMAND_COMPLETED_AT, System.currentTimeMillis())
            .putInt(KEY_DEMAND_CHECKED, result.checkedCount)
            .putInt(KEY_DEMAND_FOUND, result.foundCount)
            .putInt(KEY_DEMAND_NEW, result.newCount)
            .putInt(KEY_DEMAND_ERRORS, result.errors)
            .putString(
                KEY_DEMAND_ERROR_TEXT,
                if (result.errors > 0) "${result.errors} demanda(s) com falha" else ""
            )
            .apply()
    }

    fun markDemandFailed(context: Context, throwable: Throwable) {
        context.getSharedPreferences(BackgroundMonitor.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DEMAND_ERROR_TEXT, throwable.message?.take(180) ?: throwable.javaClass.simpleName)
            .apply()
    }
}

class BackgroundHeartbeatReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        BackgroundMonitor.enqueueRecoveryIfStale(context)
        BackgroundMonitor.scheduleHeartbeat(context)
    }
}

class MonitorBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val prefs = context.getSharedPreferences(BackgroundMonitor.PREFS, Context.MODE_PRIVATE)
        val interval = prefs.getInt("interval_minutes", 30).coerceAtLeast(15)
        BackgroundMonitor.scheduleAll(context, interval)
        BackgroundMonitor.enqueueRecoveryIfStale(context)
    }
}
