package org.vestifeed.api

import org.vestifeed.api.miniflux.MinifluxApiAdapter
import org.vestifeed.api.miniflux.MinifluxApiBuilder
import org.vestifeed.api.nextcloud.NextcloudApiAdapter
import org.vestifeed.api.nextcloud.NextcloudApiBuilder
import org.vestifeed.api.standalone.StandaloneNewsApi
import org.vestifeed.conf.ConfRepo
import org.vestifeed.db.Db
import org.vestifeed.db.Entry
import org.vestifeed.db.EntryWithoutContent
import org.vestifeed.db.Feed
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import org.vestifeed.db.ConfQueries
import java.time.OffsetDateTime

class HotSwapApi(
    private val confRepo: ConfRepo,
    private val db: Db,
) : Api {

    private lateinit var api: Api

    init {
        GlobalScope.launch {
            confRepo.conf.collectLatest { conf ->
                when (conf.backend) {
                    ConfQueries.BACKEND_STANDALONE -> {
                        api = StandaloneNewsApi(db)
                    }

                    ConfQueries.BACKEND_MINIFLUX -> {
                        api = MinifluxApiAdapter(
                            MinifluxApiBuilder().build(
                                url = conf.minifluxServerUrl,
                                token = conf.minifluxServerToken,
                                trustSelfSignedCerts = conf.minifluxServerTrustSelfSignedCerts,
                            )
                        )
                    }

                    ConfQueries.BACKEND_NEXTCLOUD -> {
                        api = NextcloudApiAdapter(
                            NextcloudApiBuilder().build(
                                url = conf.nextcloudServerUrl,
                                username = conf.nextcloudServerUsername,
                                password = conf.nextcloudServerPassword,
                                trustSelfSignedCerts = conf.nextcloudServerTrustSelfSignedCerts,
                            )
                        )
                    }
                }
            }
        }
    }

    override suspend fun addFeed(url: HttpUrl): Result<Pair<Feed, List<Entry>>> {
        return api.addFeed(url)
    }

    override suspend fun getFeeds(): Result<List<Feed>> {
        return api.getFeeds()
    }

    override suspend fun updateFeedTitle(feedId: String, newTitle: String): Result<Unit> {
        return api.updateFeedTitle(feedId, newTitle)
    }

    override suspend fun deleteFeed(feedId: String): Result<Unit> {
        return api.deleteFeed(feedId)
    }

    override suspend fun getEntries(includeReadEntries: Boolean): Flow<Result<List<Entry>>> {
        return api.getEntries(includeReadEntries)
    }

    override suspend fun getNewAndUpdatedEntries(
        maxEntryId: String?,
        maxEntryUpdated: OffsetDateTime?,
        lastSync: OffsetDateTime?,
    ): Result<List<Entry>> {
        return api.getNewAndUpdatedEntries(maxEntryId, maxEntryUpdated, lastSync)
    }

    override suspend fun markEntriesAsRead(entriesIds: List<String>, read: Boolean): Result<Unit> {
        return api.markEntriesAsRead(entriesIds, read)
    }

    override suspend fun markEntriesAsBookmarked(
        entries: List<EntryWithoutContent>,
        bookmarked: Boolean,
    ): Result<Unit> {
        return api.markEntriesAsBookmarked(entries, bookmarked)
    }
}