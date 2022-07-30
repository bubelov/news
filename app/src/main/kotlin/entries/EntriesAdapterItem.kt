package entries

import db.Link

data class EntriesAdapterItem(
    val id: String,
    val showImage: Boolean,
    val cropImage: Boolean,
    val imageUrl: String,
    val imageWidth: Int,
    val imageHeight: Int,
    val title: String,
    val subtitle: String,
    val summary: String,
    var read: Boolean,
    val openInBrowser: Boolean,
    val useBuiltInBrowser: Boolean,
    val links: List<Link>,
)