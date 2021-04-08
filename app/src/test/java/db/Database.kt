package db

import co.appreactor.news.Database
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver

fun database(): Database {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    Database.Schema.create(driver)
    return Database(driver)
}