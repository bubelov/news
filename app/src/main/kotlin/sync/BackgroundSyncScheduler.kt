package sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import conf.ConfRepo
import org.koin.core.annotation.Single
import java.util.concurrent.TimeUnit

@Single
class BackgroundSyncScheduler(
    private val confRepo: ConfRepo,
    private val context: Context,
) {

    fun schedule() {
        val workManager = WorkManager.getInstance(context)
        val conf = confRepo.conf.value

        if (!conf.syncInBackground) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .build()

        val periodicSyncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            repeatInterval = conf.backgroundSyncIntervalMillis,
            repeatIntervalTimeUnit = TimeUnit.MILLISECONDS,
        )
            .setConstraints(constraints)
            .setInitialDelay(conf.backgroundSyncIntervalMillis, TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            periodicSyncRequest,
        )
    }

    companion object {
        private const val WORK_NAME = "sync"
    }
}