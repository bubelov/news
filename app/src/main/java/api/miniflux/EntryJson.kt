package api.miniflux

data class EntryJson(
    val id: Long,
    val feed_id: Long,
    val status: String,
    val title: String,
    val url: String,
    val published_at: String,
    val created_at: String,
    val changed_at: String,
    val content: String,
    val author: String,
    val starred: Boolean,
    val enclosures: List<EntryEnclosureJson>?,
)