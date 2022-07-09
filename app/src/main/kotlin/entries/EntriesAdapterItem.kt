package entries

import db.EntryWithoutContent

data class EntriesAdapterItem(
    val entry: EntryWithoutContent,
    val showImage: Boolean,
    val cropImage: Boolean,
    val title: String,
    val subtitle: String,
    val summary: String,
    var read: Boolean,
)