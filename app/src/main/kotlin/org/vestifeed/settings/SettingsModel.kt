package org.vestifeed.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.vestifeed.conf.ConfRepo
import org.vestifeed.db.Conf
import org.vestifeed.db.Db
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import org.vestifeed.R
import org.vestifeed.db.ConfQueries
import org.vestifeed.sync.BackgroundSyncScheduler

class SettingsModel(
    private val app: Application,
    private val confRepo: ConfRepo,
    private val db: Db,
    private val syncScheduler: BackgroundSyncScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow<State>(State.Loading)
    val state = _state.asStateFlow()

    init {
        refresh()
    }

    private fun refresh() {
        val conf = confRepo.select()
        _state.update {
            val logOutTitle: String
            val logOutSubtitle: String

            when (conf.backend) {
                ConfQueries.BACKEND_STANDALONE -> {
                    logOutTitle = app.getString(R.string.delete_all_data)
                    logOutSubtitle = ""
                }

                else -> {
                    logOutTitle = app.getString(R.string.log_out)
                    logOutSubtitle = conf.accountName()
                }
            }

            State.ShowingSettings(
                conf = conf,
                logOutTitle = logOutTitle,
                logOutSubtitle = logOutSubtitle,
            )
        }
    }

    fun setSyncInBackground(value: Boolean) {
        confRepo.update { it.copy(syncInBackground = value) }
        syncScheduler.schedule()
        refresh()
    }

    fun setBackgroundSyncIntervalMillis(value: Long) {
        confRepo.update { it.copy(backgroundSyncIntervalMillis = value) }
        syncScheduler.schedule()
        refresh()
    }

    fun setSyncOnStartup(value: Boolean) {
        confRepo.update { it.copy(syncOnStartup = value) }
        refresh()
    }

    fun setShowReadEntries(value: Boolean) {
        confRepo.update { it.copy(showReadEntries = value) }
        refresh()
    }

    fun setShowPreviewImages(value: Boolean) {
        confRepo.update { it.copy(showPreviewImages = value) }
        refresh()
    }

    fun setCropPreviewImages(value: Boolean) {
        confRepo.update { it.copy(cropPreviewImages = value) }
        refresh()
    }

    fun setShowPreviewText(value: Boolean) {
        confRepo.update { it.copy(showPreviewText = value) }
        refresh()
    }

    fun setMarkScrolledEntriesAsRead(value: Boolean) {
        confRepo.update { it.copy(markScrolledEntriesAsRead = value) }
        refresh()
    }

    fun setUseBuiltInBrowser(value: Boolean) {
        confRepo.update { it.copy(useBuiltInBrowser = value) }
        refresh()
    }

    fun logOut() {
        db.confQueries.delete()

        db.transaction {
            db.feedQueries.deleteAll()
            db.entryQueries.deleteAll()
        }
    }

    private fun Conf.accountName(): String {
        return when (backend) {
            ConfQueries.BACKEND_STANDALONE -> ""
            ConfQueries.BACKEND_MINIFLUX -> {
                minifluxServerUrl.extractDomain()
            }

            ConfQueries.BACKEND_NEXTCLOUD -> {
                val username = nextcloudServerUsername
                "$username@${nextcloudServerUrl.extractDomain()}"
            }

            else -> ""
        }
    }

    private fun String.extractDomain(): String {
        return replace("https://", "").replace("http://", "")
    }

    sealed class State {
        object Loading : State()
        data class ShowingSettings(
            val conf: Conf,
            val logOutTitle: String,
            val logOutSubtitle: String,
        ) : State()
    }
}
