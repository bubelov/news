package api.miniflux

data class PutStatusArgs(
    val entry_ids: List<Long>,
    val status: String,
)