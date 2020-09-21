package co.appreactor.news.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import co.appreactor.news.common.*
import co.appreactor.news.feeds.FeedsRepository
import co.appreactor.news.logging.LoggedExceptionsRepository
import co.appreactor.news.entries.EntriesRepository
import com.nextcloud.android.sso.AccountImporter
import com.nextcloud.android.sso.exceptions.SSOException
import com.nextcloud.android.sso.helper.SingleAccountHelper
import kotlinx.coroutines.flow.first
import timber.log.Timber

class SettingsFragmentModel(
    private val feedsRepository: FeedsRepository,
    private val entriesRepository: EntriesRepository,
    private val loggedExceptionsRepository: LoggedExceptionsRepository,
    private val prefs: Preferences,
) : ViewModel() {

    suspend fun getShowReadEntries() = prefs.showReadEntries()

    suspend fun setShowReadEntries(show: Boolean) = prefs.setShowReadEntries(show)

    suspend fun getShowPreviewImages() = prefs.showPreviewImages()

    suspend fun setShowPreviewImages(show: Boolean) = prefs.setShowPreviewImages(show)

    suspend fun getCropPreviewImages() = prefs.cropPreviewImages()

    suspend fun setCropPreviewImages(crop: Boolean) = prefs.setCropPreviewImages(crop)

    suspend fun getExceptionsCount() = loggedExceptionsRepository.count()

    suspend fun getAccountName(context: Context): String {
        val serverUrl = prefs.getServerUrl().first()

        return if (serverUrl.isNotBlank()) {
            val username = prefs.getServerUsername().first()
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

    suspend fun clearData(context: Context) {
        AccountImporter.clearAllAuthTokens(context)

        feedsRepository.clear()
        entriesRepository.clear()

        prefs.clear()
    }
}