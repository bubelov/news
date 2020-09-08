package co.appreactor.nextcloud.news.api

import co.appreactor.nextcloud.news.db.NewsFeed

data class FeedsPayload (
    val feeds: List<NewsFeed>
)