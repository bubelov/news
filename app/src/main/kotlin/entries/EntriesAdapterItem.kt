package entries

import db.EntryWithoutContent
import db.Link

data class EntriesAdapterItem(
    val entry: EntryWithoutContent,
    val showImage: Boolean,
    val cropImage: Boolean,
    val title: String,
    val subtitle: String,
    val summary: String,
    val audioEnclosure: Link?,
    var read: Boolean,
)