package api.miniflux

data class EntryEnclosureJson(
    val id: Long,
    val user_id: Long,
    val entry_id: Long,
    val url: String,
    val mime_type: String,
    val size: Long,
)