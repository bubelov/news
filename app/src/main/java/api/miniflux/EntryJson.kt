package api.miniflux

data class EntryJson(
    val id: Long? = null,
    val url: String? = null,
    val title: String? = null,
    val author: String? = null,
    val created_at: String? = null,
    val published_at: String? = null,
    val changed_at: String? = null,
    val content: String? = null,
    val feed_id: Long? = null,
    val starred: Boolean? = null,
    val status: String? = null,
)