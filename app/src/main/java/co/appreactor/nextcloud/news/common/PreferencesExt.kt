package co.appreactor.nextcloud.news.common

suspend fun Preferences.getServerUrl() = getString(Preferences.SERVER_URL)
suspend fun Preferences.setServerUrl(serverUrl: String) = putString(Preferences.SERVER_URL, serverUrl)

suspend fun Preferences.getServerUsername() = getString(Preferences.SERVER_USERNAME)
suspend fun Preferences.setServerUsername(serverUsername: String) =
    putString(Preferences.SERVER_USERNAME, serverUsername)

suspend fun Preferences.getServerPassword() = getString(Preferences.SERVER_PASSWORD)
suspend fun Preferences.setServerPassword(serverPassword: String) =
    putString(Preferences.SERVER_PASSWORD, serverPassword)

suspend fun Preferences.initialSyncCompleted() = getBoolean(Preferences.INITIAL_SYNC_COMPLETED, false)
suspend fun Preferences.setInitialSyncCompleted(initialSyncCompleted: Boolean) =
    putBoolean(Preferences.INITIAL_SYNC_COMPLETED, initialSyncCompleted)

suspend fun Preferences.showReadNews() = getBoolean(Preferences.SHOW_READ_NEWS, false)
suspend fun Preferences.setShowReadNews(showReadNews: Boolean) =
    putBoolean(Preferences.SHOW_READ_NEWS, showReadNews)

suspend fun Preferences.showFeedImages() = getBoolean(Preferences.SHOW_FEED_IMAGES, false)
suspend fun Preferences.setShowFeedImages(showFeedImages: Boolean) =
    putBoolean(Preferences.SHOW_FEED_IMAGES, showFeedImages)

suspend fun Preferences.cropFeedImages() = getBoolean(Preferences.CROP_FEED_IMAGES, true)
suspend fun Preferences.setCropFeedImages(cropFeedImages: Boolean) =
    putBoolean(Preferences.CROP_FEED_IMAGES, cropFeedImages)