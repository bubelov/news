package api

import api.miniflux.MinifluxApiAdapter
import api.miniflux.MinifluxApiBuilder
import api.nextcloud.NextcloudNewsApiAdapter
import api.nextcloud.NextcloudNewsApiBuilder
import api.standalone.StandaloneNewsApi
import conf.ConfRepository
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

@Single(binds = [NewsApi::class])
class HotSwapNewsApi(
    private val confRepo: ConfRepository,
    private val db: Db,
) : NewsApi {

    lateinit var api: NewsApi

    init {
        GlobalScope.launch {
            confRepo.load().collectLatest { conf ->
                when (conf.backend) {
                    ConfRepository.BACKEND_STANDALONE -> {
                        api = StandaloneNewsApi(db)
                    }

                    ConfRepository.BACKEND_MINIFLUX -> {
                        api = MinifluxApiAdapter(
                            MinifluxApiBuilder().build(
                                url = conf.minifluxServerUrl,
                                username = conf.minifluxServerUsername,
                                password = conf.minifluxServerPassword,
                                trustSelfSignedCerts = conf.minifluxServerTrustSelfSignedCerts,
                            )
                        )
                    }

                    ConfRepository.BACKEND_NEXTCLOUD -> {
                        api = NextcloudNewsApiAdapter(
                            NextcloudNewsApiBuilder().build(
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

    override suspend fun getFeeds(): List<Feed> {
        return api.getFeeds()
    }

    override suspend fun updateFeedTitle(feedId: String, newTitle: String) {
        api.updateFeedTitle(feedId, newTitle)
    }

    override suspend fun deleteFeed(feedId: String) {
        api.deleteFeed(feedId)
    }

    override suspend fun getEntries(includeReadEntries: Boolean): Flow<List<Entry>> {
        return api.getEntries(includeReadEntries)
    }

    override suspend fun getNewAndUpdatedEntries(
        maxEntryId: String?,
        maxEntryUpdated: OffsetDateTime?,
        lastSync: OffsetDateTime?,
    ): List<Entry> {
        return api.getNewAndUpdatedEntries(maxEntryId, maxEntryUpdated, lastSync)
    }

    override suspend fun markEntriesAsRead(entriesIds: List<String>, read: Boolean) {
        api.markEntriesAsRead(entriesIds, read)
    }

    override suspend fun markEntriesAsBookmarked(
        entries: List<EntryWithoutContent>,
        bookmarked: Boolean,
    ) {
        api.markEntriesAsBookmarked(entries, bookmarked)
    }
}