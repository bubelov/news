package db

import java.time.OffsetDateTime

data class ShortEntry(
    val links: List<Link>,
    val published: OffsetDateTime,
    val title: String,
)
