package org.vestifeed.api

import org.vestifeed.api.miniflux.MinifluxApiAdapter
import org.vestifeed.api.miniflux.MinifluxApiBuilder
import org.vestifeed.api.nextcloud.NextcloudApiAdapter
import org.vestifeed.api.nextcloud.NextcloudApiBuilder
import org.vestifeed.api.standalone.StandaloneNewsApi
import org.vestifeed.db.Database
import org.vestifeed.db.Entry
import org.vestifeed.db.EntryWithoutContent
import kotlinx.coroutines.flow.Flow
import okhttp3.HttpUrl
import org.vestifeed.db.ConfQueries
import org.vestifeed.db.table.Feed
import java.time.OffsetDateTime

class HotSwapApi(private val db: Database) : Api {

    private lateinit var api: Api

    init {
        updateApi()
    }

    private fun updateApi() {
        val conf = db.conf.select()
        api = when (conf.backend) {
            ConfQueries.BACKEND_STANDALONE -> {
                StandaloneNewsApi(db)
            }

            ConfQueries.BACKEND_MINIFLUX -> {
                MinifluxApiAdapter(
                    MinifluxApiBuilder().build(
                        url = conf.minifluxServerUrl,
                        token = conf.minifluxServerToken,
                        trustSelfSignedCerts = conf.minifluxServerTrustSelfSignedCerts,
                    )
                )
            }

            ConfQueries.BACKEND_NEXTCLOUD -> {
                NextcloudApiAdapter(
                    NextcloudApiBuilder().build(
                        url = conf.nextcloudServerUrl,
                        username = conf.nextcloudServerUsername,
                        password = conf.nextcloudServerPassword,
                        trustSelfSignedCerts = conf.nextcloudServerTrustSelfSignedCerts,
                    )
                )
            }

            else -> {
                StandaloneNewsApi(db)
            }
        }
    }

    override suspend fun addFeed(url: HttpUrl): Result<Pair<Feed, List<Entry>>> {
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

    override suspend fun getEntries(includeReadEntries: Boolean): Flow<List<Entry>> {
        updateApi()
        return api.getEntries(includeReadEntries)
    }

    override suspend fun getNewAndUpdatedEntries(
        maxEntryId: String?,
        maxEntryUpdated: OffsetDateTime?,
        lastSync: OffsetDateTime?,
    ): List<Entry> {
        updateApi()
        return api.getNewAndUpdatedEntries(maxEntryId, maxEntryUpdated, lastSync)
    }

    override suspend fun markEntriesAsRead(entriesIds: List<String>, read: Boolean) {
        updateApi()
        return api.markEntriesAsRead(entriesIds, read)
    }

    override suspend fun markEntriesAsBookmarked(
        entries: List<EntryWithoutContent>,
        bookmarked: Boolean,
    ) {
        updateApi()
        return api.markEntriesAsBookmarked(entries, bookmarked)
    }
}