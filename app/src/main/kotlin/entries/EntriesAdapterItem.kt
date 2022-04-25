package entries

data class EntriesAdapterItem(
    val id: String,
    val ogImageUrl: String,
    val ogImageWidth: Long,
    val ogImageHeight: Long,
    val cropImage: Boolean,
    val title: String,
    val subtitle: String,
    val supportingText: String,
    val podcast: Boolean,
    val podcastDownloadPercent: Long?,
    var read: Boolean,
)