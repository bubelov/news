package db

import java.time.OffsetDateTime

data class EntriesAdapterRow(
    val id: String,
    val feedId: String,
    val extBookmarked: Boolean,
    val extShowPreviewImages: Boolean,
    val extOpenGraphImageUrl: String,
    val extOpenGraphImageWidth: Int,
    val extOpenGraphImageHeight: Int,
    val title: String,
    val feedTitle: String,
    val published: OffsetDateTime,
    val summary: String,
    val extRead: Boolean,
    val extOpenEntriesInBrowser: Boolean,
    val links: List<Link>,
)
