package db

data class Link(
    val href: String,
    val rel: String,
    val type: String,
    val hreflang: String,
    val title: String,
    val length: Long?,
)
