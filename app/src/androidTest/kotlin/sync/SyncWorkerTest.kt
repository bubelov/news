package sync

import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.testing.TestWorkerBuilder
import app.App
import conf.ConfRepo
import org.junit.Before
import org.koin.android.ext.android.get
import org.junit.Test

class SyncWorkerTest {

    private lateinit var app: App

    @Before
    fun setup() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        app = instrumentation.targetContext.applicationContext as App
    }

    @Test
    fun retryIfInitialSyncIncomplete() {
        val confRepo = app.get<ConfRepo>()
        confRepo.update { it.copy(initial_sync_completed = false) }
        val workerBuilder = TestWorkerBuilder.from(app, SyncWorker::class.java)
        val worker = workerBuilder.build()
        val result = worker.doWork()
        assert(result is ListenableWorker.Result.Retry)
    }
}