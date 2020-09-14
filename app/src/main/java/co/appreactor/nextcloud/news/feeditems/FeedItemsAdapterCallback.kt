package co.appreactor.nextcloud.news.feeditems

interface FeedItemsAdapterCallback {

    fun onItemClick(item: FeedItemsAdapterItem)

    fun onDownloadPodcastClick(item: FeedItemsAdapterItem)

    fun onPlayPodcastClick(item: FeedItemsAdapterItem)
}