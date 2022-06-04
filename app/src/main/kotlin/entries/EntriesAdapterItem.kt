package entries

import db.Link

data class EntriesAdapterItem(
    val id: String,
    val ogImageUrl: String,
    val ogImageWidth: Long,
    val ogImageHeight: Long,
    val cropImage: Boolean,
    val title: String,
    val subtitle: String,
    val summary: String,
    val audioEnclosure: Link?,
    var read: Boolean,
)