package co.appreactor.news.api.nextcloud

data class ItemJson(
    val id: Long? = null,
    val guid: String? = null,
    val guidHash: String? = null,
    val url: String? = null,
    val title: String? = null,
    val author: String? = null,
    val pubDate: Long? = null,
    val updatedDate: Long? = null,
    val body: String? = null,
    val enclosureMime: String? = null,
    val enclosureLink: String? = null,
    val mediaThumbnail: String? = null,
    val mediaDescription: String? = null,
    val feedId: Long? = null,
    val unread: Boolean? = null,
    val starred: Boolean? = null,
    val lastModified: Long? = null,
    val rtl: Boolean? = null,
    val fingerprint: String? = null,
    val contentHash: String? = null,
)