package api.miniflux

data class EntriesPayload(
    val total: Long,
    val entries: List<EntryJson>,
)