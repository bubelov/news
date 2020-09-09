package co.appreactor.nextcloud.news.api

import co.appreactor.nextcloud.news.db.Feed

data class FeedsPayload (
    val feeds: List<Feed>
)