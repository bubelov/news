package co.appreactor.nextcloud.news.feeds

import co.appreactor.nextcloud.news.db.Feed

interface FeedsAdapterCallback {

    fun onOpenFeedWebsiteClick(feed: Feed)

    fun onOpenFeedXmlClick(feed: Feed)

    fun onRenameFeedClick(feed: Feed)

    fun onDeleteFeedClick(feed: Feed)
}