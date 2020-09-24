package co.appreactor.news.di

import android.content.Context
import android.net.ConnectivityManager
import co.appreactor.news.Database
import co.appreactor.news.auth.AuthFragmentModel
import co.appreactor.news.auth.DirectAuthFragmentModel
import co.appreactor.news.common.Preferences
import co.appreactor.news.common.NewsApiSync
import co.appreactor.news.feeds.FeedsRepository
import co.appreactor.news.logging.LoggedExceptionsFragmentModel
import co.appreactor.news.logging.LoggedExceptionsRepository
import co.appreactor.news.entries.EntriesFragmentModel
import co.appreactor.news.entries.EntriesRepository
import co.appreactor.news.entry.EntryFragmentModel
import co.appreactor.news.entriesimages.EntriesImagesRepository
import co.appreactor.news.entriesenclosures.EntriesEnclosuresRepository
import co.appreactor.news.settings.SettingsFragmentModel
import co.appreactor.news.bookmarks.BookmarksFragmentModel
import co.appreactor.news.common.ConnectivityProbe
import co.appreactor.news.entries.EntriesSupportingTextRepository
import co.appreactor.news.feeds.FeedsFragmentModel
import com.squareup.sqldelight.android.AndroidSqliteDriver
import com.squareup.sqldelight.db.SqlDriver
import org.koin.android.experimental.dsl.viewModel
import org.koin.dsl.module
import org.koin.experimental.builder.single

val appModule = module {

    single<SqlDriver> {
        AndroidSqliteDriver(
            schema = Database.Schema,
            context = get(),
            name = "news.db"
        )
    }

    single {
        val context = get<Context>()
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        ConnectivityProbe(connectivityManager)
    }

    single<NewsApiSync>()

    single<FeedsRepository>()
    single<EntriesRepository>()
    single<EntriesSupportingTextRepository>()
    single<EntriesImagesRepository>()
    single<EntriesEnclosuresRepository>()
    single<Preferences>()
    single<LoggedExceptionsRepository>()

    single { Database(get()) }
    single { get<Database>().feedQueries }
    single { get<Database>().entryQueries }
    single { get<Database>().entryImagesMetadataQueries }
    single { get<Database>().entryImageQueries }
    single { get<Database>().entryEnclosureQueries }
    single { get<Database>().preferenceQueries }
    single { get<Database>().loggedExceptionQueries }

    viewModel<AuthFragmentModel>()
    viewModel<EntriesFragmentModel>()
    viewModel<EntryFragmentModel>()
    viewModel<BookmarksFragmentModel>()
    viewModel<SettingsFragmentModel>()
    viewModel<DirectAuthFragmentModel>()
    viewModel<LoggedExceptionsFragmentModel>()
    viewModel<FeedsFragmentModel>()
}