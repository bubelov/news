package sync

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import common.App
import common.NewsApiSync
import common.PreferencesRepository
import db.LogEntry
import kotlinx.coroutines.runBlocking
import logentries.LogEntriesRepository
import org.joda.time.DateTime
import org.koin.android.ext.android.get
import timber.log.Timber
import java.util.*

class SyncWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    override fun doWork(): Result {
        return runBlocking {
            val app = applicationContext as App
            val prefs = app.get<PreferencesRepository>().get()
            val sync = app.get<NewsApiSync>()
            val log = app.get<LogEntriesRepository>()

            log.insert(logEntry().copy(message = "Starting background sync"))

            if (!prefs.initialSyncCompleted) {
                log.insert(
                    logEntry().copy(
                        message = "Tried to sync in background before initial sync is completed"
                    )
                )

                return@runBlocking Result.retry()
            }

            runCatching {
                sync.sync(
                    syncFeeds = true,
                    syncEntriesFlags = true,
                    syncNewAndUpdatedEntries = true,
                )
            }.onFailure {
                Timber.e(it)
                log.insert(logEntry().copy(message = "Background sync failed"))
                return@runBlocking Result.failure()
            }

            log.insert(logEntry().copy(message = "Finished background sync"))
            return@runBlocking Result.success()
        }
    }

    private fun logEntry() = LogEntry(
        id = UUID.randomUUID().toString(),
        date = DateTime.now().toString(),
        tag = "sync",
        message = "",
    )
}