package navigation

import conf.ConfRepository
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfExtensionsTest {

    @Test
    fun removeProtocolPrefixFromAccountSubtitle() {
        var conf = ConfRepository.DEFAULT_CONF.copy(
            backend = ConfRepository.BACKEND_MINIFLUX,
            minifluxServerUrl = "https://acme.com",
        )

        assertEquals(
            expected = "@acme.com",
            conf.accountSubtitle(),
        )

        conf = ConfRepository.DEFAULT_CONF.copy(
            backend = ConfRepository.BACKEND_MINIFLUX,
            minifluxServerUrl = "http://acme.com",
        )

        assertEquals(
            expected = "@acme.com",
            conf.accountSubtitle(),
        )
    }
}