package settings

import android.content.Context
import androidx.lifecycle.ViewModel
import co.appreactor.news.Database
import common.*
import exceptions.AppExceptionsRepository
import com.nextcloud.android.sso.exceptions.SSOException
import com.nextcloud.android.sso.helper.SingleAccountHelper
import common.Preferences.Companion.AUTH_TYPE
import common.Preferences.Companion.CROP_PREVIEW_IMAGES
import common.Preferences.Companion.MARK_SCROLLED_ENTRIES_AS_READ
import common.Preferences.Companion.NEXTCLOUD_SERVER_URL
import common.Preferences.Companion.NEXTCLOUD_SERVER_USERNAME
import common.Preferences.Companion.SHOW_OPENED_ENTRIES
import common.Preferences.Companion.SHOW_PREVIEW_IMAGES
import feeds.FeedsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import opml.OpmlElement
import timber.log.Timber

class SettingsFragmentModel(
    private val appExceptionsRepository: AppExceptionsRepository,
    private val feedsRepository: FeedsRepository,
    private val prefs: Preferences,
    private val db: Database,
) : ViewModel() {

    val state = MutableStateFlow<State>(State.NoActivity)

    suspend fun getShowOpenedEntries() = prefs.getBoolean(SHOW_OPENED_ENTRIES)

    fun setShowOpenedEntries(show: Boolean) = prefs.putBooleanBlocking(SHOW_OPENED_ENTRIES, show)

    suspend fun getShowPreviewImages() = prefs.getBoolean(SHOW_PREVIEW_IMAGES)

    fun setShowPreviewImages(show: Boolean) = prefs.putBooleanBlocking(SHOW_PREVIEW_IMAGES, show)

    suspend fun getCropPreviewImages() = prefs.getBoolean(CROP_PREVIEW_IMAGES)

    fun setCropPreviewImages(crop: Boolean) = prefs.putBooleanBlocking(CROP_PREVIEW_IMAGES, crop)

    suspend fun getMarkScrolledEntriesAsRead() = prefs.getBoolean(MARK_SCROLLED_ENTRIES_AS_READ)

    fun setMarkScrolledEntriesAsRead(mark: Boolean) = prefs.putBooleanBlocking(
        key = MARK_SCROLLED_ENTRIES_AS_READ,
        value = mark,
    )

    suspend fun getExceptionsCount() = appExceptionsRepository.selectCount()

    fun getAuthType() = prefs.getStringBlocking(AUTH_TYPE)

    fun getAccountName(context: Context): String {
        val serverUrl = prefs.getStringBlocking(NEXTCLOUD_SERVER_URL)

        return if (serverUrl.isNotBlank()) {
            val username = prefs.getStringBlocking(NEXTCLOUD_SERVER_USERNAME)
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

    suspend fun getAllFeeds() = feedsRepository.getAll()

    suspend fun importFeeds(feeds: List<OpmlElement>): FeedImportResult {
        var added = 0
        var exists = 0
        var failed = 0
        val errors = mutableListOf<String>()

        state.value = State.ImportingFeeds(
            imported = 0,
            total = feeds.size,
        )

        val cachedFeeds = feedsRepository.getAll().first()

        feeds.forEach { opml ->
            if (cachedFeeds.any { it.selfLink == opml.xmlUrl }) {
                exists++
                return@forEach
            }

            runCatching {
                feedsRepository.add(opml.xmlUrl)
            }.onSuccess {
                added++
            }.onFailure {
                errors += "Failed to import feed ${opml.xmlUrl}\nReason: ${it.message}"
                Timber.e(it)
                failed++
            }

            state.value = State.ImportingFeeds(
                imported = added + exists + failed,
                total = feeds.size,
            )
        }

        state.value = State.NoActivity

        return FeedImportResult(
            added = added,
            exists = exists,
            failed = failed,
            errors = errors,
        )
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

    data class FeedImportResult(
        val added: Int,
        val exists: Int,
        val failed: Int,
        val errors: List<String>,
    )

    sealed class State {
        object NoActivity : State()
        data class ImportingFeeds(val imported: Int, val total: Int) : State()
    }
}