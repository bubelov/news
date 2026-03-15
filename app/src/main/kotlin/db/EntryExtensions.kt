package db

fun Entry.withoutContent(): EntryWithoutContent {
    return EntryWithoutContent(
        links = links,
        summary = summary,
        id = id,
        feedId = feedId,
        title = title,
        published = published,
        updated = updated,
        authorName = authorName,
        extRead = extRead,
        extReadSynced = extReadSynced,
        extBookmarked = extBookmarked,
        extBookmarkedSynced = extBookmarkedSynced,
        extNextcloudGuidHash = extNextcloudGuidHash,
        extCommentsUrl = extCommentsUrl,
        extOpenGraphImageChecked = extOpenGraphImageChecked,
        extOpenGraphImageUrl = extOpenGraphImageUrl,
        extOpenGraphImageWidth = extOpenGraphImageWidth,
        extOpenGraphImageHeight = extOpenGraphImageHeight,
    )
}
