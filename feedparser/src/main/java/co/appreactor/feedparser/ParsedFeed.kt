package co.appreactor.feedparser

data class ParsedFeed (
    val id: String,
    val title: String,
    val selfLink: String,
    val alternateLink: String,
)