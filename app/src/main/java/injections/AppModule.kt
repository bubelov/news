package injections

import android.content.Context
import android.net.ConnectivityManager
import api.NewsApi
import api.NewsApiSwitcher
import api.NewsApiWrapper
import auth.AuthRepository
import auth.AuthViewModel
import auth.DirectAuthViewModel
import auth.MinifluxAuthViewModel
import db.Database
import com.squareup.sqldelight.android.AndroidSqliteDriver
import common.App
import common.AppViewModel
import common.ConfRepository
import sync.NewsApiSync
import feeds.FeedsRepository
import entries.EntriesViewModel
import entries.EntriesRepository
import entry.EntryViewModel
import entriesimages.EntriesImagesRepository
import podcasts.PodcastsRepository
import settings.SettingsViewModel
import common.NetworkMonitor
import db.entryAdapter
import entries.EntriesSharedViewModel
import entries.EntriesSupportingTextRepository
import feeds.FeedsViewModel
import feedsettings.FeedSettingsViewModel
import log.ExceptionViewModel
import log.LogRepository
import log.LogViewModel
import org.koin.android.experimental.dsl.viewModel
import org.koin.dsl.module
import org.koin.experimental.builder.single
import search.SearchViewModel

val appModule = module {

    single {
        Database(
            driver = AndroidSqliteDriver(
                schema = Database.Schema,
                context = get(),
                name = App.DB_FILE_NAME,
            ),
            EntryAdapter = entryAdapter()
        )
    }

    single { get<Database>().feedQueries }
    single { get<Database>().entryQueries }
    single { get<Database>().entryImagesMetadataQueries }
    single { get<Database>().entryImageQueries }
    single { get<Database>().entryEnclosureQueries }
    single { get<Database>().confQueries }
    single { get<Database>().logQueries }

    single {
        val context = get<Context>()
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        NetworkMonitor(connectivityManager)
    }

    single { get<Context>().resources }

    single<NewsApi> { NewsApiWrapper() }
    single { get<NewsApi>() as NewsApiWrapper }
    single<NewsApiSwitcher>()
    single<NewsApiSync>()

    single<AuthRepository>()
    single<FeedsRepository>()
    single<EntriesRepository>()
    single<EntriesSupportingTextRepository>()
    single<EntriesImagesRepository>()
    single<PodcastsRepository>()
    single<ConfRepository>()
    single<LogRepository>()

    viewModel<AppViewModel>()
    viewModel<AuthViewModel>()
    viewModel<EntriesViewModel>()
    viewModel<EntriesSharedViewModel>()
    viewModel<EntryViewModel>()
    viewModel<SettingsViewModel>()
    viewModel<DirectAuthViewModel>()
    viewModel<ExceptionViewModel>()
    viewModel<FeedsViewModel>()
    viewModel<FeedSettingsViewModel>()
    viewModel<SearchViewModel>()
    viewModel<LogViewModel>()
    viewModel<MinifluxAuthViewModel>()
}