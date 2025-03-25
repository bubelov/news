package db

data class Feed(
    val id: String,
    val links: List<Link>,
    val title: String,
    val extOpenEntriesInBrowser: Boolean,
    val extBlockedWords: String,
    val extShowPreviewImages: Boolean?,
)
