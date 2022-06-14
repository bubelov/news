package common

import android.content.Context
import androidx.lifecycle.ViewModel
import auth.accountSubtitle
import auth.accountTitle
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrDefault
import db.Database
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class AppViewModel(
    private val db: Database,
    context: Context,
) : ViewModel() {

    private val resources = context.resources

    fun accountTitle(): Flow<String> {
        return db.confQueries
            .select()
            .asFlow()
            .mapToOneOrDefault(ConfRepository.DEFAULT_CONF)
            .map { it.accountTitle(resources) }
    }

    fun accountSubtitle(): Flow<String> {
        return db.confQueries
            .select()
            .asFlow()
            .mapToOneOrDefault(ConfRepository.DEFAULT_CONF)
            .map { it.accountSubtitle() }
    }
}