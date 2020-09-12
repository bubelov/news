package co.appreactor.nextcloud.news.feeditems

interface FeedItemsAdapterCallback {

    fun onItemClick(item: FeedItemsAdapterRow)

    fun onDownloadPodcastClick(item: FeedItemsAdapterRow)

    fun onPlayPodcastClick(item: FeedItemsAdapterRow)

    suspend fun generateSummary(feedItemId: Long): String
}