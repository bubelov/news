package org.vestifeed.db

import java.time.OffsetDateTime
import java.util.UUID
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Ignore
import org.vestifeed.db.table.Feed

class EntryQueriesTest {

    private lateinit var db: Database

    @Before
    fun before() {
        db = db()
    }

    @Test
    fun insertOrReplace() {
        val item = entry()
        db.entry.insertOrReplace(listOf(item))
        assertEquals(item, db.entry.selectById(item.id))
    }

    @Test
    fun selectById() {
        val items = listOf(
            db.entry.insertOrReplace(),
            db.entry.insertOrReplace(),
            db.entry.insertOrReplace(),
        )

        assertEquals(
            items[1],
            db.entry.selectById(items[1].id),
        )
    }

    @Test
    fun selectByReadAndBookmarked() {
        val feed = createFeed()
        db.feed.insertOrReplace(listOf(feed))

        val all = listOf(
            entry().copy(feedId = feed.id, extRead = true, extBookmarked = true),
            entry().copy(feedId = feed.id, extRead = true, extBookmarked = false),
            entry().copy(feedId = feed.id, extRead = false, extBookmarked = false),
        )

        db.entry.insertOrReplace(all)

        assertEquals(
            all.filter { !it.extRead && !it.extBookmarked }.map { it.id },
            db.entry.selectByReadAndBookmarked(
                extRead = false,
                extBookmarked = false
            ).map { it.id },
        )
    }

    @Test
    fun selectByReadSynced() {
        val all = listOf(
            entry().copy(extReadSynced = true),
            entry().copy(extReadSynced = false),
            entry().copy(extReadSynced = true),
        )

        db.entry.insertOrReplace(all)

        assertEquals(
            all.filter { it.extReadSynced }.map { it.withoutContent() }
                .sortedByDescending { it.published },
            db.entry.selectByReadSynced(true),
        )

        assertEquals(
            all.filter { !it.extReadSynced }.map { it.withoutContent() }
                .sortedByDescending { it.published },
            db.entry.selectByReadSynced(false),
        )
    }

    @Test
    fun selectByQuery() {
        val db = db()
        val feed = createFeed()
        db.feed.insertOrReplace(listOf(feed))

        val entries = listOf(
            entry().copy(feedId = feed.id, contentText = "Linux 5.19 introduces RSS API"),
            entry().copy(feedId = feed.id, contentText = "LinuX 5.19 introduces RSS API"),
            entry().copy(feedId = feed.id, contentText = "linux 5.19 introduces RSS API"),
            entry().copy(feedId = feed.id, contentText = "Injured Irons Destroy Specifically")
        )

        db.entry.insertOrReplace(entries)

        assertEquals(3, db.entry.selectByQuery("Linux").size)
        assertEquals(3, db.entry.selectByQuery("LinuX").size)
        assertEquals(3, db.entry.selectByQuery("linux").size)
        assertEquals(1, db.entry.selectByQuery("call").size)
    }
}

fun EntryQueries.insertOrReplace(): Entry {
    val entry = entry()
    insertOrReplace(listOf(entry))
    return entry
}

fun entry() = Entry(
    contentType = "",
    contentSrc = "",
    contentText = "",
    links = emptyList(),
    summary = "",
    id = UUID.randomUUID().toString(),
    feedId = "",
    title = "",
    published = OffsetDateTime.now(),
    updated = OffsetDateTime.now(),
    authorName = "",
    extRead = false,
    extReadSynced = true,
    extBookmarked = false,
    extBookmarkedSynced = true,
    extNextcloudGuidHash = "",
    extCommentsUrl = "",
    extOpenGraphImageChecked = true,
    extOpenGraphImageUrl = "",
    extOpenGraphImageWidth = 0,
    extOpenGraphImageHeight = 0,
)

fun entryWithoutContent() = EntryWithoutContent(
    links = emptyList(),
    summary = "",
    id = UUID.randomUUID().toString(),
    feedId = "",
    title = "",
    published = OffsetDateTime.now(),
    updated = OffsetDateTime.now(),
    authorName = "",
    extRead = false,
    extReadSynced = true,
    extBookmarked = false,
    extBookmarkedSynced = true,
    extNextcloudGuidHash = "",
    extCommentsUrl = "",
    extOpenGraphImageChecked = true,
    extOpenGraphImageUrl = "",
    extOpenGraphImageWidth = 0,
    extOpenGraphImageHeight = 0,
)

fun Entry.withoutContent() = EntryWithoutContent(
    links = links,
    summary = "",
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
    extOpenGraphImageChecked = true,
    extOpenGraphImageUrl = "",
    extOpenGraphImageWidth = 0,
    extOpenGraphImageHeight = 0,
)

fun EntryWithoutContent.toEntry(): Entry {
    return Entry(
        contentType = "",
        contentSrc = "",
        contentText = "",
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

private fun createFeed(
    id: String = UUID.randomUUID().toString(),
    links: List<Link> = emptyList(),
    title: String = "Test Feed",
    extOpenEntriesInBrowser: Boolean? = null,
    extBlockedWords: String = "",
    extShowPreviewImages: Boolean? = null,
) = Feed(
    id = id,
    links = links,
    title = title,
    extOpenEntriesInBrowser = extOpenEntriesInBrowser,
    extBlockedWords = extBlockedWords,
    extShowPreviewImages = extShowPreviewImages,
)