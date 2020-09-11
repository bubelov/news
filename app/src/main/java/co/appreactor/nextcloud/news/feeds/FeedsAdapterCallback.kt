package co.appreactor.nextcloud.news.feeds

import co.appreactor.nextcloud.news.db.Feed

interface FeedsAdapterCallback {

    fun onOpenWebsiteClick(feed: Feed)

    fun onOpenFeedClick(feed: Feed)

    fun onDeleteClick(feed: Feed)
}