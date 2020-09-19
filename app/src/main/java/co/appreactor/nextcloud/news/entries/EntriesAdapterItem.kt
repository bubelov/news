package co.appreactor.nextcloud.news.entries

import co.appreactor.nextcloud.news.db.EntryImage
import kotlinx.coroutines.flow.Flow

data class EntriesAdapterItem(
    val id: String,
    val title: String,
    val subtitle: Lazy<String>,
    val viewed: Boolean,
    val podcast: Boolean,
    val podcastDownloadPercent: Flow<Long?>,
    val image: Flow<EntryImage?>,
    val cachedImage: Lazy<EntryImage?>,
    val showImage: Boolean,
    val cropImage: Boolean,
    val summary: Flow<String>,
    val cachedSummary: String?
)