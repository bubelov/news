package feeds

interface FeedsAdapterCallback {

    fun onSettingsClick(feed: FeedsAdapterItem)

    fun onFeedClick(feed: FeedsAdapterItem)

    fun onOpenHtmlLinkClick(feed: FeedsAdapterItem)

    fun openLinkClick(feed: FeedsAdapterItem)

    fun onRenameClick(feed: FeedsAdapterItem)

    fun onDeleteClick(feed: FeedsAdapterItem)
}