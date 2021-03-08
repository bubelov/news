package settings

import android.content.Context
import androidx.lifecycle.ViewModel
import co.appreactor.news.Database
import common.*
import exceptions.AppExceptionsRepository
import com.nextcloud.android.sso.exceptions.SSOException
import com.nextcloud.android.sso.helper.SingleAccountHelper
import kotlinx.coroutines.runBlocking
import timber.log.Timber

class SettingsFragmentModel(
    private val appExceptionsRepository: AppExceptionsRepository,
    private val preferencesRepository: PreferencesRepository,
    private val db: Database,
) : ViewModel() {

    suspend fun getPreferences() = preferencesRepository.get()

    suspend fun savePreferences(action: Preferences.() -> Unit) = preferencesRepository.save(action)

    suspend fun getExceptionsCount() = appExceptionsRepository.selectCount()

    fun getAccountName(context: Context): String {
        val prefs = runBlocking { getPreferences() }

        return if (prefs.nextcloudServerUrl.isNotBlank()) {
            val username = prefs.nextcloudServerUsername
            "$username@${prefs.nextcloudServerUrl.replace("https://", "")}"
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