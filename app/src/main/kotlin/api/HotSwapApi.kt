package api

import api.miniflux.MinifluxApiAdapter
import api.miniflux.MinifluxApiBuilder
import api.nextcloud.NextcloudApiAdapter
import api.nextcloud.NextcloudApiBuilder
import api.standalone.StandaloneNewsApi
import conf.ConfRepo
import db.Db
import db.Entry
import db.EntryWithoutContent
import db.Feed
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import org.koin.core.annotation.Single
import java.time.OffsetDateTime

@Single(binds = [Api::class])
class HotSwapApi(
    private val confRepo: ConfRepo,
    private val db: Db,
) : Api {

    private lateinit var api: Api

    init {
        GlobalScope.launch {
            confRepo.conf.collectLatest { conf ->
                when (conf.backend) {
                    ConfRepo.BACKEND_STANDALONE -> {
                        api = StandaloneNewsApi(db)
                    }

                    ConfRepo.BACKEND_MINIFLUX -> {
                        api = MinifluxApiAdapter(
                            MinifluxApiBuilder().build(
                                url = conf.minifluxServerUrl,
                                username = conf.minifluxServerUsername,
                                password = conf.minifluxServerPassword,
                                trustSelfSignedCerts = conf.minifluxServerTrustSelfSignedCerts,
                            )
                        )
                    }

                    ConfRepo.BACKEND_NEXTCLOUD -> {
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

    override suspend fun addFeed(url: HttpUrl): Result<Feed> {
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