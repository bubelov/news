package co.appreactor.news.entries

import co.appreactor.news.api.*
import co.appreactor.news.common.Preferences
import co.appreactor.news.db.EntryQueries
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

class EntriesRepository(
    private val entryQueries: EntryQueries,
    private val newsApi: NewsApi,
    private val prefs: Preferences,
) {

    data class SyncNotViewedAndBookmarkedProgress(val itemsSynced: Long)

    suspend fun getAll() = withContext(Dispatchers.IO) {
        entryQueries.selectAll().asFlow().mapToList()
    }

    suspend fun get(entryId: String) = withContext(Dispatchers.IO) {
        entryQueries.selectById(entryId).asFlow().mapToOneOrNull()
    }

    suspend fun getViewed() = withContext(Dispatchers.IO) {
        entryQueries.selectByViewed(viewed = true).asFlow().mapToList()
    }

    suspend fun getNotViewed() = withContext(Dispatchers.IO) {
        entryQueries.selectByViewed(viewed = false).asFlow().mapToList()
    }

    suspend fun setViewed(id: String, viewed: Boolean) = withContext(Dispatchers.IO) {
        entryQueries.updateViewed(
            viewed = viewed,
            id = id
        )
    }

    suspend fun getBookmarked() = withContext(Dispatchers.IO) {
        entryQueries.selectBookmarked().asFlow().mapToList()
    }

    suspend fun setBookmarked(id: String, bookmarked: Boolean) = withContext(Dispatchers.IO) {
        entryQueries.updateBookmarked(
            bookmarked = bookmarked,
            id = id
        )
    }

    suspend fun getNotViewedAndBookmarked() = withContext(Dispatchers.IO) {
        entryQueries.selectNotViewedAndBookmarked().asFlow().mapToList()
    }

    suspend fun getCount() = withContext(Dispatchers.IO) {
        entryQueries.selectCount().asFlow().mapToOne()
    }

    private suspend fun getMaxUpdated() = withContext(Dispatchers.IO) {
        entryQueries.selectMaxUpdaded().executeAsOneOrNull()?.MAX
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        entryQueries.deleteAll()
    }

    fun deleteByFeedId(feedId: String) {
        entryQueries.deleteByFeedId(feedId)
    }

    suspend fun syncNotViewedAndBookmarked(): Flow<SyncNotViewedAndBookmarkedProgress> = flow {
        emit(SyncNotViewedAndBookmarkedProgress(0L))

        withContext(Dispatchers.IO) {
            newsApi.getNotViewedEntries().collect { result ->
                when (result) {
                    is GetNotViewedEntriesResult.Loading -> {
                        emit(SyncNotViewedAndBookmarkedProgress(result.entriesLoaded))
                    }

                    is GetNotViewedEntriesResult.Success -> {
                        val bookmarkedEntries = newsApi.getBookmarkedEntries()

                        entryQueries.transaction {
                            (result.entries + bookmarkedEntries).forEach {
                                entryQueries.insertOrReplace(it)
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

    suspend fun syncViewedFlags() = withContext(Dispatchers.IO) {
        val unsyncedItems = entryQueries.selectByViewedSynced(false).executeAsList()

        if (unsyncedItems.isEmpty()) {
            return@withContext
        }

        val unsyncedViewedEntries = unsyncedItems.filter { it.viewed }

        if (unsyncedViewedEntries.isNotEmpty()) {
            newsApi.markAsViewed(
                entriesIds = unsyncedViewedEntries.map { it.id },
                viewed = true,
            )

            entryQueries.transaction {
                unsyncedViewedEntries.forEach {
                    entryQueries.updateViewedSynced(true, it.id)
                }
            }
        }

        val unsyncedNotViewedEntries = unsyncedItems.filterNot { it.viewed }

        if (unsyncedNotViewedEntries.isNotEmpty()) {
            newsApi.markAsViewed(
                entriesIds = unsyncedNotViewedEntries.map { it.id },
                viewed = false,
            )

            entryQueries.transaction {
                unsyncedNotViewedEntries.forEach {
                    entryQueries.updateViewedSynced(true, it.id)
                }
            }
        }
    }

    suspend fun syncBookmarkedFlags() = withContext(Dispatchers.IO) {
        val unsyncedItems = entryQueries.selectByBookmarkedSynced(false).executeAsList()

        if (unsyncedItems.isEmpty()) {
            return@withContext
        }

        val unsyncedBookmarkedEntries = unsyncedItems.filter { it.bookmarked }

        if (unsyncedBookmarkedEntries.isNotEmpty()) {
            newsApi.markAsBookmarked(unsyncedBookmarkedEntries, true)

            entryQueries.transaction {
                unsyncedBookmarkedEntries.forEach {
                    entryQueries.updateBookmarkedSynced(true, it.id)
                }
            }
        }

        val unsyncedNotBookmarkedEntries = unsyncedItems.filterNot { it.bookmarked }

        if (unsyncedNotBookmarkedEntries.isNotEmpty()) {
            newsApi.markAsBookmarked(unsyncedNotBookmarkedEntries, false)

            entryQueries.transaction {
                unsyncedNotBookmarkedEntries.forEach {
                    entryQueries.updateBookmarkedSynced(true, it.id)
                }
            }
        }
    }

    suspend fun syncNewAndUpdated() = withContext(Dispatchers.IO) {
        val threshold = getMaxUpdated() ?: prefs.getString(Preferences.LAST_ENTRIES_SYNC_DATE_TIME).first()

        if (threshold.isBlank()) {
            throw Exception("Can not find any reference dates")
        }

        val since = Instant.parse(threshold)
        val entries = newsApi.getNewAndUpdatedEntries(since)

        entryQueries.transaction {
            entries.forEach {
                entryQueries.insertOrReplace(it)
            }
        }

        prefs.putString(
            key = Preferences.LAST_ENTRIES_SYNC_DATE_TIME,
            value = Instant.now().toString()
        )
    }
}