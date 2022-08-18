package db

fun Entry.withoutContent(): EntryWithoutContent {
    return EntryWithoutContent(
        links,
        summary,
        id,
        feed_id,
        title,
        published,
        updated,
        author_name,
        ext_read,
        ext_read_synced,
        ext_bookmarked,
        ext_bookmarked_synced,
        ext_nc_guid_hash,
        ext_comments_url,
        ext_og_image_checked,
        ext_og_image_url,
        ext_og_image_width,
        ext_og_image_height,
    )
}