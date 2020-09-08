package co.appreactor.nextcloud.news.feeds

import co.appreactor.nextcloud.news.db.NewsFeed

fun interface NewsFeedsAdapterCallback {

    fun onClick(feed: NewsFeed)
}