package co.appreactor.news.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import co.appreactor.news.common.*
import co.appreactor.news.logging.LoggedExceptionsRepository
import com.nextcloud.android.sso.AccountImporter
import com.nextcloud.android.sso.exceptions.SSOException
import com.nextcloud.android.sso.helper.SingleAccountHelper
import com.squareup.sqldelight.db.SqlDriver
import kotlinx.coroutines.flow.first
import timber.log.Timber

class SettingsFragmentModel(
    private val loggedExceptionsRepository: LoggedExceptionsRepository,
    private val prefs: Preferences,
    private val dbDriver: SqlDriver,
) : ViewModel() {

    suspend fun getShowReadEntries() = prefs.showReadEntries()

    suspend fun setShowReadEntries(show: Boolean) = prefs.setShowReadEntries(show)

    suspend fun getShowPreviewImages() = prefs.showPreviewImages()

    suspend fun setShowPreviewImages(show: Boolean) = prefs.setShowPreviewImages(show)

    suspend fun getCropPreviewImages() = prefs.cropPreviewImages()

    suspend fun setCropPreviewImages(crop: Boolean) = prefs.setCropPreviewImages(crop)

    suspend fun getExceptionsCount() = loggedExceptionsRepository.count()

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

    fun logOut(context: Context) {
        AccountImporter.clearAllAuthTokens(context)
        dbDriver.close()
        context.deleteDatabase(App.DB_FILE_NAME)
    }
}