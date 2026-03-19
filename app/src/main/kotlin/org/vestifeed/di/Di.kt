package org.vestifeed.di

import android.content.Context
import android.app.Application
import androidx.lifecycle.ViewModel
import org.vestifeed.api.Api
import org.vestifeed.api.HotSwapApi
import org.vestifeed.app.db
import org.vestifeed.auth.AuthModel
import org.vestifeed.auth.MinifluxAuthModel
import org.vestifeed.auth.NextcloudAuthModel
import org.vestifeed.conf.ConfRepo
import org.vestifeed.entries.EntriesModel
import org.vestifeed.entries.EntriesRepo
import org.vestifeed.enclosures.EnclosuresModel
import org.vestifeed.enclosures.EnclosuresRepo
import org.vestifeed.feeds.FeedsModel
import org.vestifeed.feeds.FeedsRepo
import org.vestifeed.feedsettings.FeedSettingsModel
import org.vestifeed.opengraph.OpenGraphImagesRepo
import org.vestifeed.search.SearchModel
import org.vestifeed.settings.SettingsModel
import org.vestifeed.sync.BackgroundSyncScheduler
import org.vestifeed.sync.Sync

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
            ConfRepo::class.java -> ConfRepo(context.db())
            Api::class.java, HotSwapApi::class.java -> HotSwapApi(
                get(ConfRepo::class.java),
                context.db(),
            )

            FeedsRepo::class.java -> FeedsRepo(
                get(Api::class.java),
                context.db(),
            )

            EntriesRepo::class.java -> EntriesRepo(
                get(Api::class.java),
                context.db(),
            )

            EnclosuresRepo::class.java -> EnclosuresRepo(
                context,
                context.db(),
            )

            OpenGraphImagesRepo::class.java -> OpenGraphImagesRepo(
                context,
                get(ConfRepo::class.java),
                context.db(),
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

            SettingsModel::class.java -> SettingsModel(
                context as Application,
                get(ConfRepo::class.java),
                context.db(),
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
