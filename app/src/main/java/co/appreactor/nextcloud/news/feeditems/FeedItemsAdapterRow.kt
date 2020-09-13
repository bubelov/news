package co.appreactor.nextcloud.news.feeditems

import kotlinx.coroutines.flow.Flow

data class FeedItemsAdapterRow(
    val id: Long,
    val title: String,
    val subtitle: String,
    val unread: Boolean,
    val imageUrl: String,
    val cropImage: Boolean,
    val podcast: Boolean,
    val podcastDownloadPercent: Long?,
    val summary: Flow<String>
)