package org.vestifeed.conf

import org.vestifeed.db.Conf
import org.vestifeed.db.Db

class ConfRepo(
    private val db: Db,
) {

    fun select(): Conf = db.confQueries.select()

    fun update(newConf: (Conf) -> Conf) {
        db.confQueries.update(newConf)
    }
}
