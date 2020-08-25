package co.appreactor.nextcloud.news

import co.appreactor.nextcloud.news.db.NewsItem

data class ItemsPayload (
    val items: List<NewsItem>
)