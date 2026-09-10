package br.com.monitordenoticias.android

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar

object VideoBackgroundMonitor {
    private const val SCHEDULE_REQUEST_CODE = 2801
    const val ACTION_SCHEDULED_SCAN = "br.com.monitordenoticias.android.VIDEO_SCHEDULED_SCAN"
    private const val LEGACY_HOURLY_ACTION = "br.com.monitordenoticias.android.VIDEO_HEARTBEAT"
    private val SCHEDULE_HOURS = intArrayOf(8, 12, 15, 19, 21)

    private fun connectedConstraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * A partir da v3.0.1, vídeos deixam de usar varredura horária.
     * As execuções automáticas ficam concentradas em 08h, 12h, 15h, 19h e 21h
     * no horário local do aparelho. A busca manual continua disponível a qualquer momento.
     */
    fun scheduleAll(context: Context) {
        val app = context.applicationContext

        // Remove tanto WorkManager legado quanto um AlarmManager horário que ainda
        // possa ter sobrevivido à atualização da v3.0.0.
        WorkManager.getInstance(app).cancelUniqueWork("monitor_videos_1h")
        WorkManager.getInstance(app).cancelUniqueWork("monitor_videos_heartbeat")
        cancelLegacyHourlyAlarm(app)
        scheduleNext(app)
    }

    fun scheduleNext(context: Context) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(AlarmManager::class.java) ?: return
        val now = Calendar.getInstance()
        val next = nextScheduledTime(now)

        val intent = Intent(app, VideoHeartbeatReceiver::class.java)
            .setAction(ACTION_SCHEDULED_SCAN)
        val pendingIntent = PendingIntent.getBroadcast(
            app,
            SCHEDULE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Não exige permissão de alarme exato. O Android/Doze pode deslocar alguns
        // minutos, mas 08h, 12h, 15h, 19h e 21h continuam sendo os horários-alvo.
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            next.timeInMillis,
            pendingIntent
        )

        app.getSharedPreferences(BackgroundMonitor.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(VideoAutoRunLog.KEY_NEXT_HEARTBEAT_AT, next.timeInMillis)
            .apply()
    }

    private fun cancelLegacyHourlyAlarm(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val legacyIntent = Intent(context, VideoHeartbeatReceiver::class.java)
            .setAction(LEGACY_HOURLY_ACTION)
        val legacy = PendingIntent.getBroadcast(
            context,
            SCHEDULE_REQUEST_CODE,
            legacyIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager.cancel(legacy)
        legacy.cancel()
    }

    private fun nextScheduledTime(now: Calendar): Calendar {
        val next = now.clone() as Calendar
        next.set(Calendar.MINUTE, 0)
        next.set(Calendar.SECOND, 0)
        next.set(Calendar.MILLISECOND, 0)

        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val nextHour = SCHEDULE_HOURS.firstOrNull { it * 60 > currentMinutes }
        if (nextHour != null) {
            next.set(Calendar.HOUR_OF_DAY, nextHour)
        } else {
            next.add(Calendar.DAY_OF_YEAR, 1)
            next.set(Calendar.HOUR_OF_DAY, SCHEDULE_HOURS.first())
        }
        return next
    }

    fun enqueueScheduled(context: Context) {
        val app = context.applicationContext
        val request = OneTimeWorkRequestBuilder<VideoMonitorWorker>()
            .setConstraints(connectedConstraints())
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork(
            "monitor_videos_scheduled",
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}

object VideoAutoRunLog {
    const val KEY_ATTEMPT_AT = "auto_video_attempt_at"
    const val KEY_COMPLETED_AT = "auto_video_completed_at"
    const val KEY_FOUND = "auto_video_found"
    const val KEY_NEW = "auto_video_new"
    const val KEY_RELEVANT = "auto_video_relevant"
    const val KEY_NEW_RELEVANT = "auto_video_new_relevant"
    const val KEY_ERRORS = "auto_video_errors"
    const val KEY_ERROR_TEXT = "auto_video_error_text"
    const val KEY_NEXT_HEARTBEAT_AT = "auto_video_next_heartbeat_at"

    fun markAttempt(context: Context) {
        context.getSharedPreferences(BackgroundMonitor.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_ATTEMPT_AT, System.currentTimeMillis())
            .putString(KEY_ERROR_TEXT, "")
            .apply()
    }

    fun markCompleted(context: Context, result: VideoSearchResult) {
        context.getSharedPreferences(BackgroundMonitor.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_COMPLETED_AT, System.currentTimeMillis())
            .putInt(KEY_FOUND, result.foundCount)
            .putInt(KEY_NEW, result.newCount)
            .putInt(KEY_RELEVANT, result.relevantCount)
            .putInt(KEY_NEW_RELEVANT, result.newRelevantCount)
            .putInt(KEY_ERRORS, result.errors)
            .putString(KEY_ERROR_TEXT, if (result.errors > 0) "${result.errors} fonte(s) instável(is) nesta varredura" else "")
            .apply()
    }

    fun markFailed(context: Context, throwable: Throwable) {
        context.getSharedPreferences(BackgroundMonitor.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ERROR_TEXT, throwable.message?.take(180) ?: throwable.javaClass.simpleName)
            .apply()
    }
}

class VideoHeartbeatReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != VideoBackgroundMonitor.ACTION_SCHEDULED_SCAN) return
        VideoBackgroundMonitor.enqueueScheduled(context)
        VideoBackgroundMonitor.scheduleNext(context)
    }
}

class VideoBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED
            )
        ) return
        VideoBackgroundMonitor.scheduleAll(context)
    }
}
