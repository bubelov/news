package entries

import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
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
import db.ShortEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.time.OffsetDateTime

@Single
class EntriesRepo(
    private val api: Api,
    private val db: SQLiteDatabase,
) {

//    CREATE TABLE Entry (
//    content_type TEXT,
//    content_src TEXT,
//    content_text TEXT,
//    links TEXT AS List<Link> NOT NULL,
//    summary TEXT,
//    id TEXT PRIMARY KEY NOT NULL,
//    feed_id TEXT NOT NULL,
//    title TEXT NOT NULL,
//    published TEXT AS OffsetDateTime NOT NULL,
//    updated TEXT AS OffsetDateTime NOT NULL,
//    author_name TEXT NOT NULL,
//    ext_read INTEGER AS Boolean NOT NULL,
//    ext_read_synced INTEGER AS Boolean NOT NULL,
//    ext_bookmarked INTEGER AS Boolean NOT NULL,
//    ext_bookmarked_synced INTEGER AS Boolean NOT NULL,
//    ext_nc_guid_hash TEXT NOT NULL,
//    ext_comments_url TEXT NOT NULL,
//    ext_og_image_checked INTEGER AS Boolean NOT NULL,
//    ext_og_image_url TEXT NOT NULL,
//    ext_og_image_width INTEGER NOT NULL,
//    ext_og_image_height INTEGER NOT NULL
//    );

    fun insertOrReplace(entry: Entry) {
//        INSERT OR REPLACE
//        INTO Entry(
//                content_type,
//        content_src,
//        content_text,
//        links,
//        summary,
//        id,
//        feed_id,
//        title,
//        published,
//        updated,
//        author_name,
//        ext_read,
//        ext_read_synced,
//        ext_bookmarked,
//        ext_bookmarked_synced,
//        ext_nc_guid_hash,
//        ext_comments_url,
//        ext_og_image_checked,
//        ext_og_image_url,
//        ext_og_image_width,
//        ext_og_image_height
//        )
//        VALUES ?;
    }

    fun selectAll(): List<Entry> {
//        SELECT *
//        FROM Entry
//        ORDER BY published DESC;
        return emptyList()
    }

    suspend fun insertOrReplace(entries: List<Entry>) {
        withContext(Dispatchers.IO) {
            db.transaction {
                entries.forEach { entry ->
                    val postProcessedEntry = entry.postProcess()
                    insertOrReplace(postProcessedEntry)
                }
            }
        }
    }

    fun selectAllLinksPublishedAndTitle(): Flow<List<ShortEntry>> {
//        selectAllLinksPublishedAndTitle:
//        SELECT links, published, title
//        FROM Entry
//        ORDER BY published DESC;
        //return db.entryQueries.selectAllLinksPublishedAndTitle().asFlow().mapToList()
        return flowOf(emptyList())
    }

//    selectByIds:
//    SELECT id
//    FROM Entry
//    WHERE id IN :ids;

    fun selectById(entryId: String): Flow<Entry?> {
//        selectById:
//        SELECT *
//        FROM Entry
//        WHERE id = ?;
        //return db.entryQueries.selectById(entryId).asFlow().mapToOneOrNull()
        return flowOf(null)
    }

    fun selectByFeedIdAndReadAndBookmarked(
        feedId: String,
        read: Collection<Boolean>,
        bookmarked: Boolean,
    ): Flow<List<EntriesAdapterRow>> {
//        selectByFeedIdAndReadAndBookmarked:
//        SELECT *
//                FROM EntriesAdapterRow e
//        WHERE e.feed_id = ?
//        AND e.ext_read IN ?
//        AND e.ext_bookmarked = ?
//        ORDER BY e.published DESC;
//        return db.entryQueries.selectByFeedIdAndReadAndBookmarked(
//            feed_id = feedId,
//            ext_read = read,
//            ext_bookmarked = bookmarked,
//        ).asFlow().mapToList()
        return flowOf(emptyList())
    }

    fun selectByReadAndBookmarked(
        read: Collection<Boolean>,
        bookmarked: Boolean,
    ): Flow<List<EntriesAdapterRow>> {
//        selectByReadAndBookmarked:
//        SELECT *
//                FROM EntriesAdapterRow e
//        WHERE e.ext_read IN ?
//        AND e.ext_bookmarked = ?
//        ORDER BY e.published DESC
//        LIMIT 500;
//        return db.entryQueries.selectByReadAndBookmarked(
//            ext_read = read,
//            ext_bookmarked = bookmarked,
//        ).asFlow().mapToList()
        return flowOf(emptyList())
    }

//    selectCount:
//    SELECT COUNT(*)
//    FROM Entry;
    fun selectCount() = db.entryQueries.selectCount().asFlow().mapToOne()

    private fun selectMaxId(): Flow<String?> {
//        selectMaxId:
//        SELECT MAX(id + 0) FROM Entry;
        return db.entryQueries.selectMaxId().asFlow().mapToOneOrNull().map { it?.MAX }
    }

    private fun selectMaxUpdated(): Flow<String?> {
//        selectMaxUpdated:
//        SELECT MAX(updated)
//        FROM Entry;
        return db.entryQueries.selectMaxUpdated().asFlow().mapToOneOrNull().map { it?.MAX }
    }

    fun selectByFtsQuery(query: String): Flow<List<SelectByQuery>> {
//        -- https://www.sqlite.org/fts5.html
//        -- > It is an error to add types
//                -- However, SQLDelight insists on typing every field but it will strip types from the real schema
//        CREATE VIRTUAL TABLE entry_search USING fts5(
//                id TEXT NOT NULL,
//        title TEXT,
//        summary TEXT,
//        content_text TEXT,
//        content=Entry,
//        tokenize='trigram'
//        );
//
//        CREATE TRIGGER entry_fts_after_insert AFTER INSERT ON Entry BEGIN
//                INSERT
//        INTO entry_search(rowid, id, title, summary, content_text)
//        VALUES (new.rowid, new.id, new.title, new.summary, new.content_text);
//        END;
//
//        CREATE TRIGGER entry_fts_after_delete AFTER DELETE ON Entry BEGIN
//                INSERT
//        INTO entry_search(entry_search, rowid, id, title, summary, content_text)
//        VALUES ('delete', old.rowid, old.id, old.title, old.summary, old.content_text);
//        END;
//
//        CREATE TRIGGER entry_fts_after_update AFTER UPDATE ON Entry BEGIN
//                INSERT
//        INTO entry_search(entry_search, rowid, id, title, summary, content_text)
//        VALUES ('delete', old.rowid, old.id, old.title, old.summary, old.content_text);
//
//        INSERT
//        INTO entry_search(rowid, id, title, summary, content_text)
//        VALUES (new.rowid, new.id, new.title, new.summary, new.content_text);
//        END;
//
//        selectByQuery:
//        SELECT
//        e.id,
//        f.ext_show_preview_images,
//        e.ext_og_image_url,
//        e.ext_og_image_width,
//        e.ext_og_image_height,
//        e.title,
//        f.title AS feedTitle,
//        e.published,
//        e.summary,
//        e.ext_read,
//        f.ext_open_entries_in_browser,
//        e.links
//        FROM entry_search es
//        JOIN Entry e ON e.id = es.id
//        JOIN Feed f ON f.id = e.feed_id
//        WHERE es.title LIKE '%' || :query || '%'
//        OR es.summary LIKE '%' || :query || '%'
//        OR es.content_text LIKE '%' || :query || '%'
//        LIMIT 500;
        return db.entrySearchQueries.selectByQuery(query).asFlow().mapToList()
    }

    suspend fun updateReadByFeedId(read: Boolean, feedId: String) {
//        updateReadByFeedId:
//        UPDATE Entry
//        SET ext_read = :read, ext_read_synced = 0
//        WHERE ext_read != :read AND feed_id = :feedId;
        withContext(Dispatchers.IO) {
            db.entryQueries.updateReadByFeedId(read, feedId)
        }
    }

    suspend fun updateReadByBookmarked(read: Boolean, bookmarked: Boolean) {
//        updateReadByBookmarked:
//        UPDATE Entry
//        SET ext_read = :read, ext_read_synced = 0
//        WHERE ext_read != :read AND ext_bookmarked = :bookmarked;
        withContext(Dispatchers.IO) {
            db.entryQueries.updateReadByBookmarked(read = read, bookmarked = bookmarked)
        }
    }

    suspend fun updateReadAndReadSynced(id: String, read: Boolean, readSynced: Boolean) {
//        updateReadAndReadSynced:
//        UPDATE Entry
//        SET ext_read = ?, ext_read_synced = ?
//        WHERE id = ?;
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
//        updateBookmarkedAndBookmaredSynced:
//        UPDATE Entry
//        SET ext_bookmarked = ?, ext_bookmarked_synced = ?
//        WHERE id = ?;
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
            insertOrReplace(batch.getOrThrow())
        }
    }

    suspend fun syncReadEntries() {
        withContext(Dispatchers.IO) {
//            selectByReadSynced:
//            SELECT *
//            FROM EntryWithoutContent
//            WHERE ext_read_synced = ?
//            ORDER BY published DESC;
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
//                        updateReadSynced:
//                        UPDATE Entry
//                        SET ext_read_synced = ?
//                        WHERE id = ?;
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
//            selectByBookmarked:
//            SELECT *
//            FROM EntryWithoutContent
//            WHERE ext_bookmarked = ?
//            ORDER BY published DESC;

//            selectByBookmarkedSynced:
//            SELECT *
//            FROM EntryWithoutContent
//            WHERE ext_bookmarked_synced = ?
//            ORDER BY published DESC;
            val notSyncedEntries = db.entryQueries.selectByBookmarkedSynced(false).executeAsList()

            if (notSyncedEntries.isEmpty()) {
                return@withContext
            }

            val notSyncedBookmarkedEntries = notSyncedEntries.filter { it.ext_bookmarked }

            if (notSyncedBookmarkedEntries.isNotEmpty()) {
                api.markEntriesAsBookmarked(notSyncedBookmarkedEntries, true)

                db.entryQueries.transaction {
                    notSyncedBookmarkedEntries.forEach {
//                        updateBookmarkedSynced:
//                        UPDATE Entry
//                        SET ext_bookmarked_synced = ?
//                        WHERE id = ?;
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

//                    selectLinksById:
//                    SELECT links
//                    FROM Entry
//                    WHERE id = ?;

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