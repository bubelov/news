package co.appreactor.nextcloud.news.api

import co.appreactor.nextcloud.news.db.FeedItem

data class FeedItemsPayload (
    val items: List<FeedItem>
)