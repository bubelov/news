package sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import co.appreactor.news.R
import common.App
import common.AppActivity
import common.PreferencesRepository
import db.LogEntry
import entries.EntriesRepository
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
            val entriesRepository = app.get<EntriesRepository>()

            log.insert(logEntry().copy(message = "Starting background sync"))

            if (!prefs.initialSyncCompleted) {
                log.insert(
                    logEntry().copy(
                        message = "Tried to sync in background before initial sync is completed"
                    )
                )

                return@runBlocking Result.retry()
            }

            val syncResult = sync.sync(
                syncFeeds = true,
                syncEntriesFlags = true,
                syncNewAndUpdatedEntries = true,
            )

            when (syncResult) {
                is SyncResult.Ok -> {
                    log.insert(
                        logEntry().copy(
                            message = "Got ${syncResult.newAndUpdatedEntries} new and updated entries"
                        )
                    )

                    if (syncResult.newAndUpdatedEntries > 0) {
                        runCatching {
                            val unreadEntries = entriesRepository.selectByReadAndBookmarked(
                                read = false,
                                bookmarked = false,
                            ).size

                            if (unreadEntries > 0) {
                                showUnreadEntriesNotification(unreadEntries, app)
                            }
                        }.onFailure {
                            Timber.e(it)
                            log.insert(
                                logEntry().copy(
                                    message = "Failed to show unread entries notification (${it.message})"
                                )
                            )
                        }
                    }
                }
                is SyncResult.Err -> {
                    Timber.e(syncResult.e)
                    log.insert(logEntry().copy(message = "Background sync failed (${syncResult.e.message})"))
                    return@runBlocking Result.failure()
                }
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

    private fun showUnreadEntriesNotification(unreadEntries: Int, context: Context) {
        createNotificationChannel(context)

        val intent = Intent(context, AppActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(context, 0, intent, 0)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_baseline_list_alt_24)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(
                context.getString(
                    R.string.you_have_d_unread_news,
                    unreadEntries
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            notify(NOTIFICATION_ID, builder.build())
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.unread_news)
            val descriptionText = context.getString(R.string.unread_news)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "unread_entries"
        private const val NOTIFICATION_ID = 1
    }
}