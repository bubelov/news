package co.appreactor.nextcloud.news.news

data class NewsAdapterRow(
    val id: Long,
    val title: String,
    val subtitle: String,
    val summary: String,
    val unread: Boolean,
    val imageUrl: String
)