package api.miniflux

data class FeedJson(
    val id: Long? = null,
    val title: String = "",
    val feed_url: String = "",
    val site_url: String = "",
)