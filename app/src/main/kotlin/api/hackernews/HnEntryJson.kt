package api.miniflux

data class HnEntryJson(
    val by: String = "",
    val id: Long = 0,
    val parent: Long = 0,
    val text: String? = "",
    val time: Long = 0,
    val type: String = "",
    val dead: Boolean? = false,
    val deleted: Boolean? = false,
    val kids: List<Long>?,
    val descendants: Long?,
    val title: String? = ""
)
