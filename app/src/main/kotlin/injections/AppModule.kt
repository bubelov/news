package injections

import android.content.Context
import android.net.ConnectivityManager
import androidx.core.content.getSystemService
import api.NewsApi
import api.NewsApiSwitcher
import api.NewsApiWrapper
import auth.AccountsRepository
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
import enclosures.AudioEnclosuresRepository
import settings.SettingsViewModel
import common.NetworkMonitor
import db.entryAdapter
import db.linkAdapter
import feeds.FeedsViewModel
import feedsettings.FeedSettingsViewModel
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import search.SearchViewModel
import enclosures.EnclosuresModel

val appModule = module {

    single { get<Context>().resources }

    single { Database(driver = get(), EntryAdapter = get(), LinkAdapter = get()) }
    single { Database.Schema }
    single<SqlDriver> {
        AndroidSqliteDriver(schema = get(), context = get(), name = App.DB_FILE_NAME)
    }
    single { entryAdapter() }
    single { linkAdapter() }

    single { get<Database>().confQueries }
    single { get<Database>().feedQueries }
    single { get<Database>().entryQueries }
    single { get<Database>().linkQueries }

    singleOf(::NetworkMonitor)
    single { get<Context>().getSystemService<ConnectivityManager>()!! }

    single<NewsApi> { NewsApiWrapper() }
    single { get<NewsApi>() as NewsApiWrapper }
    singleOf(::NewsApiSwitcher)
    singleOf(::NewsApiSync)

    singleOf(::ConfRepository)
    singleOf(::AccountsRepository)
    singleOf(::FeedsRepository)
    singleOf(::EntriesRepository)
    singleOf(::EntriesImagesRepository)
    singleOf(::AudioEnclosuresRepository)

    viewModelOf(::AppViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::AuthViewModel)
    viewModelOf(::MinifluxAuthViewModel)
    viewModelOf(::NextcloudAuthModel)
    viewModelOf(::FeedsViewModel)
    viewModelOf(::FeedSettingsViewModel)
    viewModelOf(::EntriesModel)
    viewModelOf(::SearchViewModel)
    viewModelOf(::EntryViewModel)
    viewModelOf(::EnclosuresModel)
}