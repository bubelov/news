package feeds

import db.Feed

interface FeedsAdapterCallback {

    fun onFeedClick(feed: Feed)

    fun onOpenHtmlLinkClick(feed: Feed)

    fun openLinkClick(feed: Feed)

    fun onRenameClick(feed: Feed)

    fun onDeleteClick(feed: Feed)
}