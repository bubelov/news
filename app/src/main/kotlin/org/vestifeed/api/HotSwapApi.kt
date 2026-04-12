package org.vestifeed.api

import org.vestifeed.api.miniflux.MinifluxApiBuilder
import org.vestifeed.api.standalone.StandaloneNewsApi
import org.vestifeed.db.Database
import kotlinx.coroutines.flow.Flow
import okhttp3.HttpUrl
import org.vestifeed.db.table.ConfSchema
import org.vestifeed.db.table.Entry
import org.vestifeed.db.table.EntryQueries
import org.vestifeed.db.table.Feed
import org.vestifeed.db.table.Link
import java.time.OffsetDateTime

class HotSwapApi(private val db: Database) : Api {

    private lateinit var api: Api

    init {
        updateApi()
    }

    private fun updateApi() {
        val conf = db.conf.select()
        api = when (conf.backend) {
            ConfSchema.BACKEND_STANDALONE -> {
                StandaloneNewsApi(db)
            }

            ConfSchema.BACKEND_MINIFLUX -> {
                MinifluxApiBuilder().build(
                    url = conf.minifluxServerUrl,
                    token = conf.minifluxServerToken,
                    trustSelfSignedCerts = conf.minifluxServerTrustSelfSignedCerts,
                )
            }

            else -> {
                StandaloneNewsApi(db)
            }
        }
    }

    override suspend fun addFeed(url: HttpUrl): Api.AddFeedResult {
        updateApi()
        return api.addFeed(url)
    }

    override suspend fun getFeeds(): List<Feed> {
        updateApi()
        return api.getFeeds()
    }

    override suspend fun updateFeedTitle(feedId: String, newTitle: String): Result<Unit> {
        updateApi()
        return api.updateFeedTitle(feedId, newTitle)
    }

    override suspend fun deleteFeed(feedId: String): Result<Unit> {
        updateApi()
        return api.deleteFeed(feedId)
    }

    override suspend fun getEntries(includeReadEntries: Boolean): Flow<List<Pair<Entry, List<Link>>>> {
        updateApi()
        return api.getEntries(includeReadEntries)
    }

    override suspend fun getNewAndUpdatedEntries(
        maxEntryId: String?,
        maxEntryUpdated: OffsetDateTime?,
        lastSync: OffsetDateTime?,
    ): List<Pair<Entry, List<Link>>> {
        updateApi()
        return api.getNewAndUpdatedEntries(maxEntryId, maxEntryUpdated, lastSync)
    }

    override suspend fun markEntriesAsRead(entriesIds: List<String>, read: Boolean) {
        updateApi()
        return api.markEntriesAsRead(entriesIds, read)
    }

    override suspend fun markEntriesAsBookmarked(
        entries: List<EntryQueries.EntryWithoutContent>,
        bookmarked: Boolean,
    ) {
        updateApi()
        return api.markEntriesAsBookmarked(entries, bookmarked)
    }
}