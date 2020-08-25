package co.appreactor.nextcloud.news

import co.appreactor.nextcloud.news.db.NewsFeed

data class FeedsPayload (
    val feeds: List<NewsFeed>
)