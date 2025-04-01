package settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.appreactor.news.R
import conf.ConfRepo
import db.Conf
import db.Db
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import org.koin.android.annotation.KoinViewModel
import sync.BackgroundSyncScheduler

@KoinViewModel
class SettingsModel(
    private val app: Application,
    private val confRepo: ConfRepo,
    private val db: Db,
    private val syncScheduler: BackgroundSyncScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow<State>(State.Loading)
    val state = _state.asStateFlow()

    init {
        confRepo.conf.onEach { conf ->
            _state.update {
                val logOutTitle: String
                val logOutSubtitle: String

                when (conf.backend) {
                    ConfRepo.BACKEND_STANDALONE -> {
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
        }.launchIn(viewModelScope)
    }

    fun setSyncInBackground(value: Boolean) {
        confRepo.update { it.copy(sync_in_background = value) }
        syncScheduler.schedule()
    }

    fun setBackgroundSyncIntervalMillis(value: Long) {
        confRepo.update { it.copy(background_sync_interval_millis = value) }
        syncScheduler.schedule()
    }

    fun setSyncOnStartup(value: Boolean) {
        confRepo.update { it.copy(sync_on_startup = value) }
    }

    fun setShowReadEntries(value: Boolean) {
        confRepo.update { it.copy(show_read_entries = value) }
    }

    fun setShowPreviewImages(value: Boolean) {
        confRepo.update { it.copy(show_preview_images = value) }
    }

    fun setCropPreviewImages(value: Boolean) {
        confRepo.update { it.copy(crop_preview_images = value) }
    }

    fun setShowPreviewText(value: Boolean) {
        confRepo.update { it.copy(show_preview_text = value) }
    }

    fun setMarkScrolledEntriesAsRead(value: Boolean) {
        confRepo.update { it.copy(mark_scrolled_entries_as_read = value) }
    }

    fun setUseBuiltInBrowser(value: Boolean) {
        confRepo.update { it.copy(use_built_in_browser = value) }
    }

    fun logOut() {
        confRepo.update { ConfRepo.DEFAULT_CONF }

        db.apply {
            transaction {
//                deleteAll:
//                DELETE
//                FROM Feed;
                feedQueries.deleteAll()
//                deleteAll:
//                DELETE
//                FROM Entry;
                entryQueries.deleteAll()
            }
        }
    }

    private fun Conf.accountName(): String {
        return when (backend) {
            ConfRepo.BACKEND_STANDALONE -> ""
            ConfRepo.BACKEND_MINIFLUX -> {
                val username = miniflux_server_username
                "$username@${miniflux_server_url.extractDomain()}"
            }
            ConfRepo.BACKEND_NEXTCLOUD -> {
                val username = nextcloud_server_username
                "$username@${nextcloud_server_url.extractDomain()}"
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