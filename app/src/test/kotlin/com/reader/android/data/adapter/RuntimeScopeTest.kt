package com.reader.android.data.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeScopeTest {

    @Test
    fun `isolated scope has all isolation enabled`() {
        val scope = RuntimeScope.isolated("http://source.com")
        assertTrue(scope.cookieIsolation)
        assertTrue(scope.jsContextIsolation)
        assertTrue(scope.keyIsolation)
        assertEquals("http://source.com", scope.sourceUrl)
    }

    @Test
    fun `custom scope can disable specific isolation`() {
        val scope = RuntimeScope("http://s.com", cookieIsolation = false)
        assertTrue(!scope.cookieIsolation)
        assertTrue(scope.jsContextIsolation)
    }

    @Test
    fun `different sources have separate scopes`() {
        val a = RuntimeScope.isolated("http://a.com")
        val b = RuntimeScope.isolated("http://b.com")
        assertTrue(a.sourceUrl != b.sourceUrl)
    }
}
