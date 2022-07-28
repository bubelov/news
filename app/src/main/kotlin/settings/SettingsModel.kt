package settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
                        logOutTitle = "Delete all data"
                        logOutSubtitle = ""
                    }
                    else -> {
                        logOutTitle = "Log out"
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
        confRepo.update { it.copy(syncInBackground = value) }
        syncScheduler.schedule()
    }

    fun setBackgroundSyncIntervalMillis(value: Long) {
        confRepo.update { it.copy(backgroundSyncIntervalMillis = value) }
        syncScheduler.schedule()
    }

    fun setSyncOnStartup(value: Boolean) {
        confRepo.update { it.copy(syncOnStartup = value) }
    }

    fun setShowReadEntries(value: Boolean) {
        confRepo.update { it.copy(showReadEntries = value) }
    }

    fun setShowPreviewImages(value: Boolean) {
        confRepo.update { it.copy(showPreviewImages = value) }
    }

    fun setCropPreviewImages(value: Boolean) {
        confRepo.update { it.copy(cropPreviewImages = value) }
    }

    fun setShowPreviewText(value: Boolean) {
        confRepo.update { it.copy(showPreviewText = value) }
    }

    fun setMarkScrolledEntriesAsRead(value: Boolean) {
        confRepo.update { it.copy(markScrolledEntriesAsRead = value) }
    }

    fun setUseBuiltInBrowser(value: Boolean) {
        confRepo.update { it.copy(useBuiltInBrowser = value) }
    }

    fun logOut() {
        confRepo.update { ConfRepo.DEFAULT_CONF }

        db.apply {
            transaction {
                feedQueries.deleteAll()
                entryQueries.deleteAll()
            }
        }
    }

    private fun Conf.accountName(): String {
        return when (backend) {
            ConfRepo.BACKEND_STANDALONE -> ""
            ConfRepo.BACKEND_MINIFLUX -> {
                val username = minifluxServerUsername
                "$username@${minifluxServerUrl.extractDomain()}"
            }
            ConfRepo.BACKEND_NEXTCLOUD -> {
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