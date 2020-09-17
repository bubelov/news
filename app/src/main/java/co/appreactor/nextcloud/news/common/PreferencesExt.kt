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

suspend fun Preferences.showReadEntries() = getBoolean(Preferences.SHOW_READ_ENTRIES, false)
suspend fun Preferences.setShowReadEntries(showReadNews: Boolean) =
    putBoolean(Preferences.SHOW_READ_ENTRIES, showReadNews)

suspend fun Preferences.showPreviewImages() = getBoolean(Preferences.SHOW_PREVIEW_IMAGES, false)
suspend fun Preferences.setShowPreviewImages(showPreviewImages: Boolean) =
    putBoolean(Preferences.SHOW_PREVIEW_IMAGES, showPreviewImages)

suspend fun Preferences.cropPreviewImages() = getBoolean(Preferences.CROP_PREVIEW_IMAGES, true)
suspend fun Preferences.setCropPreviewImages(cropPreviewImages: Boolean) =
    putBoolean(Preferences.CROP_PREVIEW_IMAGES, cropPreviewImages)