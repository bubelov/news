package org.vestifeed.db

import java.time.OffsetDateTime
import java.util.UUID
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Before

class EntryQueriesTest {

    private lateinit var db: Db

    @Before
    fun before() {
        db = db()
    }

    @Test
    fun insertOrReplace() {
        val item = entry()
        db.entryQueries.insertOrReplace(item)
        assertEquals(item, db.entryQueries.selectById(item.id))
    }

    @Test
    fun selectById() {
        val items = listOf(
            db.entryQueries.insertOrReplace(),
            db.entryQueries.insertOrReplace(),
            db.entryQueries.insertOrReplace(),
        )

        assertEquals(
            items[1],
            db.entryQueries.selectById(items[1].id),
        )
    }

    @Test
    fun selectByReadAndBookmarked() {
        val feed = db.insertRandomFeed()

        val all = listOf(
            entry().copy(feedId = feed.id, extRead = true, extBookmarked = true),
            entry().copy(feedId = feed.id, extRead = true, extBookmarked = false),
            entry().copy(feedId = feed.id, extRead = false, extBookmarked = false),
        )

        all.forEach { db.entryQueries.insertOrReplace(it) }

        assertEquals(
            all.filter { !it.extRead && !it.extBookmarked }.map { it.id },
            db.entryQueries.selectByReadAndBookmarked(
                extRead = listOf(false),
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

        all.forEach { db.entryQueries.insertOrReplace(it) }

        assertEquals(
            all.filter { it.extReadSynced }.map { it.withoutContent() }
                .sortedByDescending { it.published },
            db.entryQueries.selectByReadSynced(true),
        )

        assertEquals(
            all.filter { !it.extReadSynced }.map { it.withoutContent() }
                .sortedByDescending { it.published },
            db.entryQueries.selectByReadSynced(false),
        )
    }
}

fun EntryQueries.insertOrReplace(): Entry {
    val entry = entry()
    insertOrReplace(entry)
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