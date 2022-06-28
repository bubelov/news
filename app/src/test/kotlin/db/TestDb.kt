package db

import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver

fun testDb(): Db {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    Db.Schema.create(driver)
    return database(driver)
}