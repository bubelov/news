package co.appreactor.news.api

data class FeedJson(
    val id: Long? = null,
    val url: String? = null,
    val title: String? = null,
    val faviconLink: String? = null,
    val added: Long? = null,
    val folderId: Long? = null,
    val unreadCount: Long? = null,
    val ordering: Long? = null,
    val link: String? = null,
    val pinned: Boolean? = null,
    val updateErrorCount: Long? = null,
    val lastUpdateError: String? = null
)