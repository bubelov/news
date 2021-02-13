package feeds

data class FeedsAdapterItem(
    val id: String,
    val title: String,
    val selfLink: String,
    val alternateLink: String,
    val unreadCount: Int,
)
