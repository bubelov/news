package db

import android.content.Context
import java.io.File

private const val FILE_NAME = "news-v6.db"

fun Context.databaseFile(): File {
    return getDatabasePath(FILE_NAME)
}