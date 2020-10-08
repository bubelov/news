package co.appreactor.news.common

suspend fun Preferences.getAuthType() = getString(Preferences.AUTH_TYPE)
suspend fun Preferences.setAuthType(authType: String) =
    putString(Preferences.AUTH_TYPE, authType)

suspend fun Preferences.getNextcloudServerUrl() = getString(Preferences.NEXTCLOUD_SERVER_URL)
suspend fun Preferences.setNextcloudServerUrl(nextcloudServerUrl: String) =
    putString(Preferences.NEXTCLOUD_SERVER_URL, nextcloudServerUrl)

suspend fun Preferences.getNextcloudServerUsername() = getString(Preferences.NEXTCLOUD_SERVER_USERNAME)
suspend fun Preferences.setNextcloudServerUsername(nextcloudServerUsername: String) =
    putString(Preferences.NEXTCLOUD_SERVER_USERNAME, nextcloudServerUsername)

suspend fun Preferences.getNextcloudServerPassword() = getString(Preferences.NEXTCLOUD_SERVER_PASSWORD)
suspend fun Preferences.setNextcloudServerPassword(nextcloudServerPassword: String) =
    putString(Preferences.NEXTCLOUD_SERVER_PASSWORD, nextcloudServerPassword)

suspend fun Preferences.initialSyncCompleted() = getBoolean(Preferences.INITIAL_SYNC_COMPLETED, false)
suspend fun Preferences.setInitialSyncCompleted(initialSyncCompleted: Boolean) =
    putBoolean(Preferences.INITIAL_SYNC_COMPLETED, initialSyncCompleted)

suspend fun Preferences.showReadEntries() = getBoolean(Preferences.SHOW_READ_ENTRIES, false)
suspend fun Preferences.setShowReadEntries(showReadNews: Boolean) =
    putBoolean(Preferences.SHOW_READ_ENTRIES, showReadNews)

suspend fun Preferences.showPreviewImages() = getBoolean(Preferences.SHOW_PREVIEW_IMAGES, true)
suspend fun Preferences.setShowPreviewImages(showPreviewImages: Boolean) =
    putBoolean(Preferences.SHOW_PREVIEW_IMAGES, showPreviewImages)

suspend fun Preferences.cropPreviewImages() = getBoolean(Preferences.CROP_PREVIEW_IMAGES, true)
suspend fun Preferences.setCropPreviewImages(cropPreviewImages: Boolean) =
    putBoolean(Preferences.CROP_PREVIEW_IMAGES, cropPreviewImages)