package api.miniflux

data class CategoryJson(
    val id: Long,
    val title: String,
    val user_id: Long,
    val hide_globally: Boolean,
)