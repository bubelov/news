package co.appreactor.nextcloud.news.news

interface NewsAdapterCallback {

    fun onRowClick(row: NewsAdapterRow)

    fun onDownloadPodcastClick(row: NewsAdapterRow)

    fun onPlayPodcastClick(row: NewsAdapterRow)
}