package co.appreactor.nextcloud.news.feeds

import co.appreactor.nextcloud.news.db.Feed

fun interface FeedsAdapterCallback {
    fun onClick(feed: Feed)
}