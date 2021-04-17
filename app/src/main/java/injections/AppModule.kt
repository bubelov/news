package injections

import android.content.Context
import android.net.ConnectivityManager
import api.NewsApi
import api.NewsApiSwitcher
import api.NewsApiWrapper
import auth.AuthViewModel
import auth.DirectAuthViewModel
import co.appreactor.news.Database
import com.squareup.sqldelight.android.AndroidSqliteDriver
import common.PreferencesRepository
import sync.NewsApiSync
import feeds.FeedsRepository
import exceptions.AppExceptionsViewModel
import exceptions.AppExceptionsRepository
import entries.EntriesViewModel
import entries.EntriesRepository
import entry.EntryViewModel
import entriesimages.EntriesImagesRepository
import podcasts.PodcastsRepository
import settings.SettingsViewModel
import common.ConnectivityProbe
import entries.EntriesSharedViewModel
import entries.EntriesSupportingTextRepository
import exception.AppExceptionViewModel
import feeds.FeedsViewModel
import feedsettings.FeedSettingsViewModel
import logentries.LogEntriesRepository
import logentries.LogEntriesViewModel
import org.koin.android.experimental.dsl.viewModel
import org.koin.dsl.module
import org.koin.experimental.builder.single
import search.SearchViewModel

val appModule = module {

    single {
        Database(
            AndroidSqliteDriver(
                schema = Database.Schema,
                context = get(),
                name = "news.db",
            )
        )
    }

    single { get<Database>().feedQueries }
    single { get<Database>().entryQueries }
    single { get<Database>().entryImagesMetadataQueries }
    single { get<Database>().entryImageQueries }
    single { get<Database>().entryEnclosureQueries }
    single { get<Database>().preferenceQueries }
    single { get<Database>().loggedExceptionQueries }
    single { get<Database>().logEntryQueries }

    single {
        val context = get<Context>()
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        ConnectivityProbe(connectivityManager)
    }

    single<NewsApi> { NewsApiWrapper() }
    single { get<NewsApi>() as NewsApiWrapper }
    single<NewsApiSwitcher>()
    single<NewsApiSync>()

    single<FeedsRepository>()
    single<EntriesRepository>()
    single<EntriesSupportingTextRepository>()
    single<EntriesImagesRepository>()
    single<PodcastsRepository>()
    single<PreferencesRepository>()
    single<AppExceptionsRepository>()
    single<LogEntriesRepository>()

    viewModel<AuthViewModel>()
    viewModel<EntriesViewModel>()
    viewModel<EntriesSharedViewModel>()
    viewModel<EntryViewModel>()
    viewModel<SettingsViewModel>()
    viewModel<DirectAuthViewModel>()
    viewModel<AppExceptionsViewModel>()
    viewModel<AppExceptionViewModel>()
    viewModel<FeedsViewModel>()
    viewModel<FeedSettingsViewModel>()
    viewModel<SearchViewModel>()
    viewModel<LogEntriesViewModel>()
}