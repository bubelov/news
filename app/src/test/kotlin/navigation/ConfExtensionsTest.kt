package navigation

import conf.ConfRepo
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfExtensionsTest {

    @Test
    fun removeProtocolPrefixFromAccountSubtitle() {
        var conf = ConfRepo.DEFAULT_CONF.copy(
            backend = ConfRepo.BACKEND_MINIFLUX,
            minifluxServerUrl = "https://acme.com",
        )

        assertEquals(
            expected = "@acme.com",
            conf.accountSubtitle(),
        )

        conf = ConfRepo.DEFAULT_CONF.copy(
            backend = ConfRepo.BACKEND_MINIFLUX,
            minifluxServerUrl = "http://acme.com",
        )

        assertEquals(
            expected = "@acme.com",
            conf.accountSubtitle(),
        )
    }
}