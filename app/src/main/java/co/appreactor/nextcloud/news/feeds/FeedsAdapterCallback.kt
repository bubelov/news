package co.appreactor.nextcloud.news.feeds

import co.appreactor.nextcloud.news.db.Feed

interface FeedsAdapterCallback {

    fun onOpenWebsiteClick(feed: Feed)

    fun onOpenRssFeedClick(feed: Feed)

    fun onDeleteClick(feed: Feed)
}