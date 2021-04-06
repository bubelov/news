package sync

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import common.App
import common.NewsApiSync
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.get
import timber.log.Timber

class SyncWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val app = applicationContext as App
        val sync = app.get<NewsApiSync>()

        runCatching {
            runBlocking {
                sync.sync(
                    syncFeeds = true,
                    syncEntriesFlags = true,
                    syncNewAndUpdatedEntries = true,
                )
            }
        }.onFailure {
            Timber.e(it)
            return Result.failure()
        }

        return Result.success()
    }
}