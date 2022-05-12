package links

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import db.Link
import db.LinkQueries
import kotlinx.coroutines.flow.Flow

class LinksRepository(
    private val linkQueries: LinkQueries,
) {

    fun selectByFeedId(feedId: String): Flow<List<Link>> {
        return linkQueries.selectByFeedId(feedId).asFlow().mapToList()
    }

    fun selectEnclosures(): Flow<List<Link>> {
        return linkQueries.selectByRel("enclosure").asFlow().mapToList()
    }
}