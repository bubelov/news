package db

fun Entry.withoutContent(): EntryWithoutContent {
    return EntryWithoutContent(
        links,
        summary,
        id,
        feedId,
        title,
        published,
        updated,
        authorName,
        read,
        readSynced,
        bookmarked,
        bookmarkedSynced,
        guidHash,
        commentsUrl,
        ogImageChecked,
        ogImageUrl,
        ogImageWidth,
        ogImageHeight,
    )
}