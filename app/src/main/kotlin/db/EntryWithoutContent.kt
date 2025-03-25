package db

import java.time.OffsetDateTime

data class EntryWithoutContent(
    val links: List<Link>,
    val summary: String?,
    val id: String,
    val feedId: String,
    val title: String,
    val published: OffsetDateTime,
    val updated: OffsetDateTime,
    val authorName: String,
    val extRead: Boolean,
    val extReadSynced: Boolean,
    val extBookmarked: Boolean,
    val extBookmarkedSynced: Boolean,
    val extNextcloudGuidHash: String,
    val extCommentsUrl: String,
    val extOpenGraphImageChecked: Boolean,
    val extOpenGraphImageUrl: String,
    val extOpenGraphImageWidth: Int,
    val extOpenGraphImageHeight: Int,
)
