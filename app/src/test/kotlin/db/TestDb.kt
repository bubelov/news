package db

import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver

fun testDb(): Database {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    Database.Schema.create(driver)
    return database(driver)
}