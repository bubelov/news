package co.appreactor.nextcloud.news

data class Item (
    val id: Long,
    val title: String,
    val url: String,
    val author: String,
    val pubDate: Long,
    val feedId: Long
)