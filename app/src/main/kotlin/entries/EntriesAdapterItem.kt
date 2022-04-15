package entries

import db.EntryImage

data class EntriesAdapterItem(
    val id: String,
    val image: EntryImage?,
    val cropImage: Boolean,
    val title: String,
    val subtitle: String,
    val supportingText: String,
    val podcast: Boolean,
    val podcastDownloadPercent: Long?,
    var read: Boolean,
)