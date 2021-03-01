package entries

import api.*
import api.GetEntriesResult
import common.Preferences
import db.Entry
import db.EntryQueries
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOne
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.joda.time.Instant
import timber.log.Timber

class EntriesRepository(
    private val entryQueries: EntryQueries,
    private val newsApi: NewsApi,
    private val prefs: Preferences,
) {

    data class SyncProgress(val itemsSynced: Long)

    suspend fun getAll() = withContext(Dispatchers.IO) {
        entryQueries.selectAll().asFlow().mapToList()
    }

    suspend fun get(entryId: String) = withContext(Dispatchers.IO) {
        entryQueries.selectById(entryId).asFlow().mapToOneOrNull()
    }

    suspend fun selectByFeedId(feedId: String) = withContext(Dispatchers.IO) {
        entryQueries.selectByFeedId(feedId).asFlow().mapToList()
    }

    suspend fun getOpened() = withContext(Dispatchers.IO) {
        entryQueries.selectByOpened(true).asFlow().mapToList()
    }

    suspend fun getNotOpened() = withContext(Dispatchers.IO) {
        entryQueries.selectByOpened(false).asFlow().mapToList()
    }

    suspend fun setOpened(id: String, opened: Boolean) = withContext(Dispatchers.IO) {
        entryQueries.apply {
            transaction {
                updateOpened(opened, id)
                updateOpenedSynced(false, id)
            }
        }
    }

    suspend fun getBookmarked() = withContext(Dispatchers.IO) {
        entryQueries.selectByBookmarked(true).asFlow().mapToList()
    }

    suspend fun setBookmarked(id: String, bookmarked: Boolean) = withContext(Dispatchers.IO) {
        entryQueries.apply {
            transaction {
                updateBookmarked(bookmarked, id)
                updateBookmarkedSynced(false, id)
            }
        }
    }

    suspend fun getCount() = withContext(Dispatchers.IO) {
        entryQueries.selectCount().asFlow().mapToOne()
    }

    suspend fun getUnreadCount(feedId: String) = withContext(Dispatchers.IO) {
        entryQueries.selectUnreadCount(feedId).asFlow().mapToOne()
    }

    private suspend fun getMaxUpdated() = withContext(Dispatchers.IO) {
        entryQueries.selectMaxUpdaded().executeAsOneOrNull()?.MAX
    }

    suspend fun search(query: String) = withContext(Dispatchers.IO) {
        entryQueries.selectByQuery(query).asFlow().mapToList()
    }

    fun deleteByFeedId(feedId: String) {
        entryQueries.deleteByFeedId(feedId)
    }

    suspend fun syncAll(): Flow<SyncProgress> = flow {
        emit(SyncProgress(0L))

        withContext(Dispatchers.IO) {
            newsApi.getAllEntries().collect { result ->
                when (result) {
                    is GetEntriesResult.Loading -> {
                        emit(SyncProgress(result.entriesLoaded))
                    }

                    is GetEntriesResult.Success -> {
                        entryQueries.transaction {
                            result.entries.forEach {
                                entryQueries.insertOrReplace(it.toSafeToInsertEntry())
                            }
                        }

                        prefs.putString(
                            key = Preferences.LAST_ENTRIES_SYNC_DATE_TIME,
                            value = Instant.now().toString()
                        )
                    }
                }
            }
        }
    }

    suspend fun syncOpenedEntries() = withContext(Dispatchers.IO) {
        val notSyncedEntries = entryQueries.selectByOpenedSynced(false).executeAsList()

        if (notSyncedEntries.isEmpty()) {
            return@withContext
        }

        val notSyncedOpenedEntries = notSyncedEntries.filter { it.opened }

        if (notSyncedOpenedEntries.isNotEmpty()) {
            newsApi.markAsOpened(
                entriesIds = notSyncedOpenedEntries.map { it.id },
                opened = true,
            )

            entryQueries.transaction {
                notSyncedOpenedEntries.forEach {
                    entryQueries.updateOpenedSynced(true, it.id)
                }
            }
        }

        val notSyncedNotOpenedEntries = notSyncedEntries.filterNot { it.opened }

        if (notSyncedNotOpenedEntries.isNotEmpty()) {
            newsApi.markAsOpened(
                entriesIds = notSyncedNotOpenedEntries.map { it.id },
                opened = false,
            )

            entryQueries.transaction {
                notSyncedNotOpenedEntries.forEach {
                    entryQueries.updateOpenedSynced(true, it.id)
                }
            }
        }
    }

    suspend fun syncBookmarkedEntries() = withContext(Dispatchers.IO) {
        val notSyncedEntries = entryQueries.selectByBookmarkedSynced(false).executeAsList()

        if (notSyncedEntries.isEmpty()) {
            return@withContext
        }

        val notSyncedBookmarkedEntries = notSyncedEntries.filter { it.bookmarked }

        if (notSyncedBookmarkedEntries.isNotEmpty()) {
            newsApi.markAsBookmarked(notSyncedBookmarkedEntries, true)

            entryQueries.transaction {
                notSyncedBookmarkedEntries.forEach {
                    entryQueries.updateBookmarkedSynced(true, it.id)
                }
            }
        }

        val notSyncedNotBookmarkedEntries = notSyncedEntries.filterNot { it.bookmarked }

        if (notSyncedNotBookmarkedEntries.isNotEmpty()) {
            newsApi.markAsBookmarked(notSyncedNotBookmarkedEntries, false)

            entryQueries.transaction {
                notSyncedNotBookmarkedEntries.forEach {
                    entryQueries.updateBookmarkedSynced(true, it.id)
                }
            }
        }
    }

    suspend fun syncNewAndUpdated() = withContext(Dispatchers.IO) {
        val threshold =
            getMaxUpdated() ?: prefs.getString(Preferences.LAST_ENTRIES_SYNC_DATE_TIME).first()

        if (threshold.isBlank()) {
            throw Exception("Can not find any reference dates")
        }

        val since = Instant.parse(threshold)
        val entries = newsApi.getNewAndUpdatedEntries(since)

        entryQueries.transaction {
            entries.forEach {
                entryQueries.insertOrReplace(it.toSafeToInsertEntry())
            }
        }

        prefs.putString(
            key = Preferences.LAST_ENTRIES_SYNC_DATE_TIME,
            value = Instant.now().toString()
        )
    }

    private fun Entry.toSafeToInsertEntry(): Entry {
        var safeEntry = this

        if (content.toByteArray().size / 1024 > 100) {
            Timber.d("Entry content is larger than 100 KiB ($link)")
            safeEntry = safeEntry.copy(content = "Content is too large")
        }

        return safeEntry
    }
}