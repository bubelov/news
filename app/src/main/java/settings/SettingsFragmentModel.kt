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
import common.Preferences.Companion.NEXTCLOUD_SERVER_URL
import common.Preferences.Companion.NEXTCLOUD_SERVER_USERNAME
import common.Preferences.Companion.SHOW_OPENED_ENTRIES
import common.Preferences.Companion.SHOW_PREVIEW_IMAGES
import feeds.FeedsRepository
import kotlinx.coroutines.flow.first
import opml.OpmlElement
import timber.log.Timber

class SettingsFragmentModel(
    private val appExceptionsRepository: AppExceptionsRepository,
    private val feedsRepository: FeedsRepository,
    private val prefs: Preferences,
    private val db: Database,
) : ViewModel() {

    suspend fun getShowOpenedEntries() = prefs.getBoolean(SHOW_OPENED_ENTRIES)

    fun setShowOpenedEntries(show: Boolean) = prefs.putBooleanBlocking(SHOW_OPENED_ENTRIES, show)

    suspend fun getShowPreviewImages() = prefs.getBoolean(SHOW_PREVIEW_IMAGES)

    fun setShowPreviewImages(show: Boolean) = prefs.putBooleanBlocking(SHOW_PREVIEW_IMAGES, show)

    suspend fun getCropPreviewImages() = prefs.getBoolean(CROP_PREVIEW_IMAGES)

    fun setCropPreviewImages(crop: Boolean) = prefs.putBooleanBlocking(CROP_PREVIEW_IMAGES, crop)

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
                failed++
            }
        }

        return FeedImportResult(
            added = added,
            exists = exists,
            failed = failed,
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
    )
}