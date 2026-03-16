package di

import android.content.Context
import android.app.Application
import androidx.lifecycle.ViewModel
import api.Api
import api.HotSwapApi
import auth.AuthModel
import auth.MinifluxAuthModel
import auth.NextcloudAuthModel
import conf.ConfRepo
import db.Db
import db.db
import entries.EntriesModel
import entries.EntriesRepo
import enclosures.EnclosuresModel
import enclosures.EnclosuresRepo
import entry.EntryModel
import feeds.FeedsModel
import feeds.FeedsRepo
import feedsettings.FeedSettingsModel
import opengraph.OpenGraphImagesRepo
import search.SearchModel
import settings.SettingsModel
import sync.BackgroundSyncScheduler
import sync.Sync

object Di {
    private lateinit var context: Context

    private val singletons = mutableMapOf<Class<*>, Any>()

    fun init(appContext: Context) {
        context = appContext
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(clazz: Class<T>): T {
        return singletons.getOrPut(clazz) {
            createInstance(clazz)
        } as T
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : ViewModel> getViewModel(clazz: Class<T>): T {
        return createViewModel(clazz) as T
    }

    @Suppress("UNCHECKED_CAST")
    private fun createInstance(clazz: Class<*>): Any {
        return when (clazz) {
            Db::class.java -> context.db()
            ConfRepo::class.java -> ConfRepo(get(Db::class.java))
            Api::class.java, HotSwapApi::class.java -> HotSwapApi(
                get(ConfRepo::class.java),
                get(Db::class.java)
            )
            FeedsRepo::class.java -> FeedsRepo(
                get(Api::class.java),
                get(Db::class.java)
            )
            EntriesRepo::class.java -> EntriesRepo(
                get(Api::class.java),
                get(Db::class.java)
            )
            EnclosuresRepo::class.java -> EnclosuresRepo(
                context,
                get(Db::class.java)
            )
            OpenGraphImagesRepo::class.java -> OpenGraphImagesRepo(
                context,
                get(ConfRepo::class.java),
                get(Db::class.java)
            )
            Sync::class.java -> Sync(
                get(ConfRepo::class.java),
                get(FeedsRepo::class.java),
                get(EntriesRepo::class.java)
            )
            BackgroundSyncScheduler::class.java -> BackgroundSyncScheduler(
                get(ConfRepo::class.java),
                context
            )
            else -> throw IllegalArgumentException("Unknown class: $clazz")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun createViewModel(clazz: Class<out ViewModel>): ViewModel {
        return when (clazz) {
            FeedsModel::class.java -> FeedsModel(
                get(ConfRepo::class.java),
                get(FeedsRepo::class.java),
                get(EntriesRepo::class.java)
            )
            EntriesModel::class.java -> EntriesModel(
                get(ConfRepo::class.java),
                get(EntriesRepo::class.java),
                get(FeedsRepo::class.java),
                get(Sync::class.java)
            )
            EntryModel::class.java -> EntryModel(
                context as Application,
                get(EnclosuresRepo::class.java),
                get(EntriesRepo::class.java),
                get(FeedsRepo::class.java),
                get(Sync::class.java),
                get(ConfRepo::class.java)
            )
            SettingsModel::class.java -> SettingsModel(
                context as Application,
                get(ConfRepo::class.java),
                get(Db::class.java),
                get(BackgroundSyncScheduler::class.java)
            )
            SearchModel::class.java -> SearchModel(
                get(ConfRepo::class.java),
                get(EntriesRepo::class.java),
                get(Sync::class.java)
            )
            FeedSettingsModel::class.java -> FeedSettingsModel(
                get(FeedsRepo::class.java)
            )
            EnclosuresModel::class.java -> EnclosuresModel(
                get(EnclosuresRepo::class.java),
                get(EntriesRepo::class.java)
            )
            AuthModel::class.java -> AuthModel(
                get(ConfRepo::class.java),
                get(BackgroundSyncScheduler::class.java)
            )
            MinifluxAuthModel::class.java -> MinifluxAuthModel(
                get(ConfRepo::class.java),
                get(BackgroundSyncScheduler::class.java)
            )
            NextcloudAuthModel::class.java -> NextcloudAuthModel(
                get(ConfRepo::class.java),
                get(BackgroundSyncScheduler::class.java)
            )
            else -> throw IllegalArgumentException("Unknown ViewModel class: $clazz")
        }
    }
}
