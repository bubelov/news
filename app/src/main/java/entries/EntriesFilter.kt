package entries

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

sealed class EntriesFilter : Parcelable {

    @Parcelize
    object OnlyNotBookmarked : EntriesFilter()

    @Parcelize
    object OnlyBookmarked : EntriesFilter()

    @Parcelize
    data class OnlyFromFeed(val feedId: String) : EntriesFilter()
}
