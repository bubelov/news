package opml

data class Outline(
    val text: String,
    val type: String,
    val xmlUrl: String,
    val openEntriesInBrowser: Boolean,
    val blockedWords: String,
    val showPreviewImages: Boolean?,
)