package org.vestifeed.entries

import android.os.Parcel
import android.os.Parcelable

sealed class EntriesFilter : Parcelable {

    abstract override fun describeContents(): Int

    abstract override fun writeToParcel(parcel: Parcel, flags: Int)

    object Unread : EntriesFilter() {
        override fun describeContents(): Int = 0

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeInt(0)
        }
    }

    object Bookmarked : EntriesFilter() {
        override fun describeContents(): Int = 0

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeInt(1)
        }
    }

    data class BelongToFeed(val feedId: String) : EntriesFilter() {
        override fun describeContents(): Int = 0

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeInt(2)
            parcel.writeString(feedId)
        }
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<EntriesFilter> = object : Parcelable.Creator<EntriesFilter> {
            override fun createFromParcel(parcel: Parcel): EntriesFilter {
                return when (parcel.readInt()) {
                    0 -> Unread
                    1 -> Bookmarked
                    2 -> BelongToFeed(parcel.readString()!!)
                    else -> throw IllegalArgumentException("Unknown type")
                }
            }

            override fun newArray(size: Int): Array<EntriesFilter?> {
                return arrayOfNulls(size)
            }
        }
    }
}
