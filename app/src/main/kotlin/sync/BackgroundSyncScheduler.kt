package sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import conf.ConfRepository
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single
import java.util.concurrent.TimeUnit

@Single
class BackgroundSyncScheduler(
    private val confRepo: ConfRepository,
    private val context: Context,
) {

    companion object {
        private const val WORK_NAME = "sync"
    }

    suspend fun schedule(override: Boolean) {
        val workManager = WorkManager.getInstance(context)
        val conf = confRepo.load().first()

        if (!conf.syncInBackground) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }

        val policy = if (override) {
            ExistingPeriodicWorkPolicy.REPLACE
        } else {
            ExistingPeriodicWorkPolicy.KEEP
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
            policy,
            periodicSyncRequest,
        )
    }
}