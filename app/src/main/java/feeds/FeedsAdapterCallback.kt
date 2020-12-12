package feeds

import db.Feed

interface FeedsAdapterCallback {

    fun onOpenHtmlLinkClick(feed: Feed)

    fun openLinkClick(feed: Feed)

    fun onRenameClick(feed: Feed)

    fun onDeleteClick(feed: Feed)
}