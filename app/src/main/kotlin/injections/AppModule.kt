package injections

import android.content.Context
import android.net.ConnectivityManager
import androidx.core.content.getSystemService
import api.NewsApi
import api.NewsApiSwitcher
import api.NewsApiWrapper
import auth.AuthRepository
import auth.AuthViewModel
import auth.NextcloudAuthModel
import auth.MinifluxAuthViewModel
import db.Database
import com.squareup.sqldelight.android.AndroidSqliteDriver
import com.squareup.sqldelight.db.SqlDriver
import common.App
import common.AppViewModel
import common.ConfRepository
import sync.NewsApiSync
import feeds.FeedsRepository
import entries.EntriesModel
import entries.EntriesRepository
import entry.EntryViewModel
import entriesimages.EntriesImagesRepository
import enclosures.EnclosuresRepository
import settings.SettingsViewModel
import common.NetworkMonitor
import db.entryAdapter
import entries.EntriesSupportingTextRepository
import feeds.FeedsViewModel
import feedsettings.FeedSettingsViewModel
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import search.SearchViewModel
import enclosures.EnclosuresModel

val appModule = module {

    single { get<Context>().resources }

    single { Database(driver = get(), EntryAdapter = get()) }
    single { Database.Schema }
    single<SqlDriver> {
        AndroidSqliteDriver(schema = get(), context = get(), name = App.DB_FILE_NAME)
    }
    single { entryAdapter() }

    single { get<Database>().feedQueries }
    single { get<Database>().entryQueries }
    single { get<Database>().entryEnclosureQueries }
    single { get<Database>().confQueries }

    singleOf(::NetworkMonitor)
    single { get<Context>().getSystemService<ConnectivityManager>()!! }

    single<NewsApi> { NewsApiWrapper() }
    single { get<NewsApi>() as NewsApiWrapper }
    singleOf(::NewsApiSwitcher)
    singleOf(::NewsApiSync)

    singleOf(::AuthRepository)
    singleOf(::FeedsRepository)
    singleOf(::EntriesRepository)
    singleOf(::EntriesSupportingTextRepository)
    singleOf(::EntriesImagesRepository)
    singleOf(::EnclosuresRepository)
    singleOf(::ConfRepository)

    viewModelOf(::AppViewModel)
    viewModelOf(::AuthViewModel)
    viewModelOf(::EntriesModel)
    viewModelOf(::EntryViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::NextcloudAuthModel)
    viewModelOf(::FeedsViewModel)
    viewModelOf(::FeedSettingsViewModel)
    viewModelOf(::SearchViewModel)
    viewModelOf(::MinifluxAuthViewModel)
    viewModelOf(::EnclosuresModel)
}