package entries

import db.Conf
import db.EntryWithoutContent
import db.Feed

data class EntriesAdapterItem(
    val entry: EntryWithoutContent,
    val feed: Feed,
    val conf: Conf,
    val showImage: Boolean,
    val cropImage: Boolean,
    val title: String,
    val subtitle: String,
    val summary: String,
    var read: Boolean,
)