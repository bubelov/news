package org.vestifeed.conf

import org.vestifeed.db.Conf
import org.vestifeed.db.Db
import kotlinx.coroutines.flow.StateFlow

class ConfRepo(
    private val db: Db,
) {

    val conf: StateFlow<Conf> = db.confQueries.conf

    fun update(newConf: (Conf) -> Conf) {
        db.confQueries.update(newConf)
    }
}
