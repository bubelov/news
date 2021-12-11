package feeds

import kotlinx.coroutines.flow.Flow

data class FeedsAdapterItem(
    val id: String,
    val title: String,
    val selfLink: String,
    val alternateLink: String,
    val unreadCount: Flow<Long>,
)
