package com.reader.android.data.bridge

import com.reader.android.AppProvider
import com.reader.android.data.network.FixtureHttpTransport
import com.reader.android.data.network.HttpResponse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifies RealCoreBridge respects AppProvider.isNetworkAllowed gate.
 * When network is disabled (default), RealCoreBridge construction throws.
 * When network is enabled via enableNetworkForTestingOnly(), construction succeeds.
 */
class RealCoreBridgeGateTest {

    private lateinit var transport: FixtureHttpTransport

    @Before
    fun setup() {
        AppProvider.initForTesting()
        transport = FixtureHttpTransport()
        transport.default(HttpResponse(code = 200, body = "", headers = emptyMap(), requestUrl = ""))
    }

    @Test
    fun `RealCoreBridge construction throws when network disabled`() {
        // Default: isNetworkAllowed = false
        val exception = assertThrows(IllegalStateException::class.java) {
            RealCoreBridge(transport)
        }
        assertTrue(exception.message!!.contains("network is disabled"))
    }

    @Test
    fun `RealCoreBridge construction succeeds when network enabled`() {
        AppProvider.enableNetworkForTestingOnly()
        // Should not throw
        RealCoreBridge(transport)
    }

    @Test
    fun `after initForTesting network is disabled again`() {
        AppProvider.enableNetworkForTestingOnly()
        assertTrue(AppProvider.isNetworkAllowed)

        AppProvider.initForTesting()
        // Should throw again
        assertThrows(IllegalStateException::class.java) {
            RealCoreBridge(transport)
        }
    }
}
