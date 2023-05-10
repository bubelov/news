package entries

import api.Api
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOne
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import db.Db
import db.EntriesAdapterRow
import db.Entry
import db.Feed
import db.SelectAllLinksPublishedAndTitle
import db.SelectByQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.time.OffsetDateTime

@Single
class EntriesRepo(
    private val api: Api,
    private val db: Db,
) {

    fun selectAllLinksPublishedAndTitle(): Flow<List<SelectAllLinksPublishedAndTitle>> {
        return db.entryQueries.selectAllLinksPublishedAndTitle().asFlow().mapToList()
    }

    fun selectById(entryId: String): Flow<Entry?> {
        return db.entryQueries.selectById(entryId).asFlow().mapToOneOrNull()
    }

    fun selectByFeedIdAndReadAndBookmarked(
        feedId: String,
        read: Collection<Boolean>,
        bookmarked: Boolean,
    ): Flow<List<EntriesAdapterRow>> {
        return db.entryQueries.selectByFeedIdAndReadAndBookmarked(
            feed_id = feedId,
            ext_read = read,
            ext_bookmarked = bookmarked,
        ).asFlow().mapToList()
    }

    fun selectByReadAndBookmarked(
        read: Collection<Boolean>,
        bookmarked: Boolean,
    ): Flow<List<EntriesAdapterRow>> {
        return db.entryQueries.selectByReadAndBookmarked(
            ext_read = read,
            ext_bookmarked = bookmarked,
        ).asFlow().mapToList()
    }

    fun selectCount() = db.entryQueries.selectCount().asFlow().mapToOne()

    private fun selectMaxId(): Flow<String?> {
        return db.entryQueries.selectMaxId().asFlow().mapToOneOrNull().map { it?.MAX }
    }

    private fun selectMaxUpdated(): Flow<String?> {
        return db.entryQueries.selectMaxUpdated().asFlow().mapToOneOrNull().map { it?.MAX }
    }

    fun selectByFtsQuery(query: String): Flow<List<SelectByQuery>> {
        return db.entrySearchQueries.selectByQuery(query).asFlow().mapToList()
    }

    suspend fun updateReadByFeedId(read: Boolean, feedId: String) {
        withContext(Dispatchers.IO) {
            db.entryQueries.updateReadByFeedId(read, feedId)
        }
    }

    suspend fun updateReadByBookmarked(read: Boolean, bookmarked: Boolean) {
        withContext(Dispatchers.IO) {
            db.entryQueries.updateReadByBookmarked(read = read, bookmarked = bookmarked)
        }
    }

    suspend fun updateReadAndReadSynced(id: String, read: Boolean, readSynced: Boolean) {
        withContext(Dispatchers.IO) {
            db.entryQueries.updateReadAndReadSynced(
                id = id,
                ext_read = read,
                ext_read_synced = readSynced,
            )
        }
    }

    suspend fun updateBookmarkedAndBookmaredSynced(
        id: String,
        bookmarked: Boolean,
        bookmarkedSynced: Boolean,
    ) {
        withContext(Dispatchers.IO) {
            db.entryQueries.updateBookmarkedAndBookmaredSynced(
                id = id,
                ext_bookmarked = bookmarked,
                ext_bookmarked_synced = bookmarkedSynced,
            )
        }
    }

    suspend fun syncAll(): Flow<SyncProgress> = flow {
        emit(SyncProgress(0L))

        var entriesLoaded = 0L
        emit(SyncProgress(entriesLoaded))

        api.getEntries(false).collect { batch ->
            entriesLoaded += batch.getOrThrow().size
            emit(SyncProgress(entriesLoaded))

            withContext(Dispatchers.IO) {
                db.entryQueries.transaction {
                    batch.getOrThrow().forEach { entry ->
                        val postProcessedEntry = entry.postProcess()
                        db.entryQueries.insertOrReplace(postProcessedEntry)
                    }
                }
            }
        }
    }

    suspend fun syncReadEntries() {
        withContext(Dispatchers.IO) {
            val unsyncedEntries = db.entryQueries.selectByReadSynced(false).executeAsList()

            if (unsyncedEntries.isEmpty()) {
                return@withContext
            }

            val unsyncedReadEntries = unsyncedEntries.filter { it.ext_read }

            if (unsyncedReadEntries.isNotEmpty()) {
                api.markEntriesAsRead(
                    entriesIds = unsyncedReadEntries.map { it.id },
                    read = true,
                )

                db.entryQueries.transaction {
                    unsyncedReadEntries.forEach {
                        db.entryQueries.updateReadSynced(true, it.id)
                    }
                }
            }

            val unsyncedUnreadEntries = unsyncedEntries.filter { !it.ext_read }

            if (unsyncedUnreadEntries.isNotEmpty()) {
                api.markEntriesAsRead(
                    entriesIds = unsyncedUnreadEntries.map { it.id },
                    read = false,
                )

                db.entryQueries.transaction {
                    unsyncedUnreadEntries.forEach {
                        db.entryQueries.updateReadSynced(true, it.id)
                    }
                }
            }
        }
    }

    suspend fun syncBookmarkedEntries() {
        withContext(Dispatchers.IO) {
            val notSyncedEntries = db.entryQueries.selectByBookmarkedSynced(false).executeAsList()

            if (notSyncedEntries.isEmpty()) {
                return@withContext
            }

            val notSyncedBookmarkedEntries = notSyncedEntries.filter { it.ext_bookmarked }

            if (notSyncedBookmarkedEntries.isNotEmpty()) {
                api.markEntriesAsBookmarked(notSyncedBookmarkedEntries, true)

                db.entryQueries.transaction {
                    notSyncedBookmarkedEntries.forEach {
                        db.entryQueries.updateBookmarkedSynced(true, it.id)
                    }
                }
            }

            val notSyncedNotBookmarkedEntries = notSyncedEntries.filterNot { it.ext_bookmarked }

            if (notSyncedNotBookmarkedEntries.isNotEmpty()) {
                api.markEntriesAsBookmarked(notSyncedNotBookmarkedEntries, false)

                db.entryQueries.transaction {
                    notSyncedNotBookmarkedEntries.forEach {
                        db.entryQueries.updateBookmarkedSynced(true, it.id)
                    }
                }
            }
        }
    }

    suspend fun syncNewAndUpdated(
        lastEntriesSyncDateTime: String,
        feeds: List<Feed>,
    ): Int {
        return withContext(Dispatchers.IO) {
            val lastSyncInstant = if (lastEntriesSyncDateTime.isNotBlank()) {
                OffsetDateTime.parse(lastEntriesSyncDateTime)
            } else {
                null
            }

            val maxUpdated = selectMaxUpdated().first()

            val maxUpdatedInstant = if (maxUpdated != null) {
                OffsetDateTime.parse(maxUpdated)
            } else {
                null
            }

            val entries = api.getNewAndUpdatedEntries(
                lastSync = lastSyncInstant,
                maxEntryId = selectMaxId().first(),
                maxEntryUpdated = maxUpdatedInstant,
            ).getOrThrow()

            db.transaction {
                entries.forEach { newEntry ->
                    val feed = feeds.firstOrNull { it.id == newEntry.feed_id }
                    val postProcessedEntry = newEntry.postProcess(feed)

                    val oldLinks = db.entryQueries.selectLinksById(newEntry.id).executeAsOneOrNull()
                        ?: emptyList()

                    db.entryQueries.insertOrReplace(
                        postProcessedEntry.copy(links = oldLinks.ifEmpty { newEntry.links })
                    )
                }
            }

            entries.size
        }
    }

    private fun Entry.postProcess(feed: Feed? = null): Entry {
        var processedEntry = this

        if (content_text != null && content_text.toByteArray().size / 1024 > 250) {
            processedEntry = processedEntry.copy(content_text = "Content is too large")
        }

        feed?.ext_blocked_words?.split(",")?.filter { it.isNotBlank() }?.forEach { word ->
            if (processedEntry.title.contains(word, ignoreCase = true)) {
                processedEntry = processedEntry.copy(ext_read = true)
            }
        }

        return processedEntry
    }

    data class SyncProgress(val itemsSynced: Long)
}