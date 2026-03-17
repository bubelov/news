CREATE TABLE IF NOT EXISTS feed (
    id TEXT PRIMARY KEY NOT NULL,
    links TEXT,
    title TEXT NOT NULL,
    ext_open_entries_in_browser INTEGER,
    ext_blocked_words TEXT NOT NULL,
    ext_show_preview_images INTEGER
);

CREATE TABLE IF NOT EXISTS entry (
    content_type TEXT,
    content_src TEXT,
    content_text TEXT,
    links TEXT,
    summary TEXT,
    id TEXT PRIMARY KEY NOT NULL,
    feed_id TEXT NOT NULL,
    title TEXT NOT NULL,
    published TEXT NOT NULL,
    updated TEXT NOT NULL,
    author_name TEXT NOT NULL,
    ext_read INTEGER NOT NULL,
    ext_read_synced INTEGER NOT NULL,
    ext_bookmarked INTEGER NOT NULL,
    ext_bookmarked_synced INTEGER NOT NULL,
    ext_nc_guid_hash TEXT NOT NULL,
    ext_comments_url TEXT NOT NULL,
    ext_og_image_checked INTEGER NOT NULL,
    ext_og_image_url TEXT NOT NULL,
    ext_og_image_width INTEGER NOT NULL,
    ext_og_image_height INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS conf (
    backend TEXT NOT NULL,
    miniflux_server_url TEXT NOT NULL,
    miniflux_server_trust_self_signed_certs INTEGER NOT NULL,
    miniflux_server_username TEXT NOT NULL,
    miniflux_server_password TEXT NOT NULL,
    nextcloud_server_url TEXT NOT NULL,
    nextcloud_server_trust_self_signed_certs INTEGER NOT NULL,
    nextcloud_server_username TEXT NOT NULL,
    nextcloud_server_password TEXT NOT NULL,
    initial_sync_completed INTEGER NOT NULL,
    last_entries_sync_datetime TEXT NOT NULL,
    show_read_entries INTEGER NOT NULL,
    sort_order TEXT NOT NULL,
    show_preview_images INTEGER NOT NULL,
    crop_preview_images INTEGER NOT NULL,
    mark_scrolled_entries_as_read INTEGER NOT NULL,
    sync_on_startup INTEGER NOT NULL,
    sync_in_background INTEGER NOT NULL,
    background_sync_interval_millis INTEGER NOT NULL,
    use_built_in_browser INTEGER NOT NULL,
    show_preview_text INTEGER NOT NULL,
    synced_on_startup INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_entry_feed_id ON entry(feed_id);
CREATE INDEX IF NOT EXISTS idx_entry_published ON entry(published);
