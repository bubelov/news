package common

import java.util.concurrent.TimeUnit

data class Preferences(
    var authType: String = "",
    var nextcloudServerUrl: String = "",
    var nextcloudServerUsername: String = "",
    var nextcloudServerPassword: String = "",
    var initialSyncCompleted: Boolean = false,
    var lastEntriesSyncDateTime: String = "",
    var showOpenedEntries: Boolean = false,
    var sortOrder: String = PreferencesRepository.SORT_ORDER_DESCENDING,
    var showPreviewImages: Boolean = true,
    var cropPreviewImages: Boolean = true,
    var markScrolledEntriesAsRead: Boolean = false,
    var syncOnStartup: Boolean = true,
    var syncInBackground: Boolean = true,
    var backgroundSyncIntervalMillis: Long = TimeUnit.HOURS.toMillis(3)
)
