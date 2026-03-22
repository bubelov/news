package org.vestifeed.auth

import androidx.lifecycle.ViewModel
import org.vestifeed.conf.ConfRepo
import org.vestifeed.db.Db
import org.vestifeed.sync.BackgroundSyncScheduler
import java.util.concurrent.TimeUnit

class AuthModel(
    private val db: Db,
    private val syncScheduler: BackgroundSyncScheduler,
) : ViewModel() {

    fun setStandaloneBackend() {
        db.confQueries.update {
            it.copy(
                backend = ConfRepo.BACKEND_STANDALONE,
                syncOnStartup = false,
                backgroundSyncIntervalMillis = TimeUnit.HOURS.toMillis(12),
            )
        }

        syncScheduler.schedule()
    }
}