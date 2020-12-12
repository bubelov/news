package settings

import android.content.Context
import androidx.lifecycle.ViewModel
import co.appreactor.news.Database
import common.*
import logging.LoggedExceptionsRepository
import com.nextcloud.android.sso.exceptions.SSOException
import com.nextcloud.android.sso.helper.SingleAccountHelper
import kotlinx.coroutines.flow.first
import timber.log.Timber

class SettingsFragmentModel(
    private val loggedExceptionsRepository: LoggedExceptionsRepository,
    private val prefs: Preferences,
    private val db: Database,
) : ViewModel() {

    suspend fun getShowOpenedEntries() = prefs.showOpenedEntries()

    suspend fun setShowOpenedEntries(show: Boolean) = prefs.setShowOpenedEntries(show)

    suspend fun getShowPreviewImages() = prefs.showPreviewImages()

    suspend fun setShowPreviewImages(show: Boolean) = prefs.setShowPreviewImages(show)

    suspend fun getCropPreviewImages() = prefs.cropPreviewImages()

    suspend fun setCropPreviewImages(crop: Boolean) = prefs.setCropPreviewImages(crop)

    suspend fun getExceptionsCount() = loggedExceptionsRepository.count()

    suspend fun getAuthType() = prefs.getAuthType().first()

    suspend fun getAccountName(context: Context): String {
        val serverUrl = prefs.getNextcloudServerUrl().first()

        return if (serverUrl.isNotBlank()) {
            val username = prefs.getNextcloudServerUsername().first()
            "$username@${serverUrl.replace("https://", "")}"
        } else {
            try {
                val account = SingleAccountHelper.getCurrentSingleSignOnAccount(context)
                account.name
            } catch (e: SSOException) {
                Timber.e(e)
                "unknown"
            }
        }
    }

    fun logOut() {
        db.apply {
            transaction {
                entryQueries.deleteAll()
                entryEnclosureQueries.deleteAll()
                entryImageQueries.deleteAll()
                entryImagesMetadataQueries.deleteAll()
                feedQueries.deleteAll()
                loggedExceptionQueries.deleteAll()
                preferenceQueries.deleteAll()
            }
        }
    }
}