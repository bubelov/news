package org.vestifeed.api.miniflux

data class PostFeedArgs(
    val feed_url: String,
    val category_id: Long,
)