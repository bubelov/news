package settings

import androidx.lifecycle.ViewModel
import auth.AuthRepository
import co.appreactor.news.Database
import common.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class SettingsViewModel(
    private val prefs: PreferencesRepository,
    private val authRepository: AuthRepository,
    private val db: Database,
) : ViewModel() {

    fun getPreferences() = runBlocking { prefs.get() }

    fun savePreferences(action: Preferences.() -> Unit) = runBlocking { prefs.save(action) }

    fun getAccountName(): String = runBlocking { authRepository.account().first().subtitle }

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