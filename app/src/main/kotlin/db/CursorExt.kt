package db

import android.database.Cursor

public fun Cursor.getNullableBoolean(columnIndex: Int): Boolean? {
    if (isNull(columnIndex)) {
        return null
    } else {
        return getInt(columnIndex) == 1
    }
}