package org.vestifeed.db

import androidx.sqlite.driver.bundled.BundledSQLiteDriver

fun db() = Database(BundledSQLiteDriver(), ":memory:")