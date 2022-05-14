package links

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import db.Database
import db.Link
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Single

@Single
class LinksRepository(
    private val db: Database,
) {

    fun selectByFeedId(feedId: String): Flow<List<Link>> {
        return db.linkQueries.selectByFeedId(feedId).asFlow().mapToList()
    }

    fun selectByEntryId(entryId: String?): Flow<List<Link>> {
        return db.linkQueries.selectByEntryid(entryId).asFlow().mapToList()
    }

    fun selectEnclosures(): Flow<List<Link>> {
        return db.linkQueries.selectByRel("enclosure").asFlow().mapToList()
    }
}