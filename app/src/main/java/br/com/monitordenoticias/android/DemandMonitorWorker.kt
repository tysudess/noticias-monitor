package br.com.monitordenoticias.android

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class DemandMonitorWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        AutoRunLog.markDemandAttempt(applicationContext)
        val db = NewsDb(applicationContext)
        return try {
            val result = NewsRepository(db).searchAllDemands()
            AutoRunLog.markDemandCompleted(applicationContext, result)
            if (result.newCount > 0) {
                NotificationHelper.notify(
                    applicationContext,
                    "Demandas monitoradas",
                    "${result.newCount} nova(s) matéria(s) encontrada(s) em ${result.checkedCount} demanda(s)."
                )
            }
            if (result.checkedCount > 0 && result.errors == result.checkedCount) Result.retry() else Result.success()
        } catch (e: Exception) {
            AutoRunLog.markDemandFailed(applicationContext, e)
            Result.retry()
        } finally {
            db.close()
        }
    }
}
