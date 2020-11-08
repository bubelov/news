package co.appreactor.news.entries

import co.appreactor.news.db.EntryImage
import kotlinx.coroutines.flow.Flow

data class EntriesAdapterItem(
    val id: String,
    val title: String,
    val subtitle: Lazy<String>,
    val podcast: Boolean,
    val podcastDownloadPercent: Flow<Long?>,
    val image: Flow<EntryImage?>,
    val cachedImage: Lazy<EntryImage?>,
    val showImage: Boolean,
    val cropImage: Boolean,
    val supportingText: Flow<String>,
    val cachedSupportingText: String?,
    val opened: Boolean,
)