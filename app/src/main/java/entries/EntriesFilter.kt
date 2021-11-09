package entries

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

sealed class EntriesFilter : Parcelable {

    @Parcelize
    object Bookmarked : EntriesFilter()

    @Parcelize
    object NotBookmarked : EntriesFilter()

    @Parcelize
    data class BelongToFeed(val feedId: String) : EntriesFilter()
}
