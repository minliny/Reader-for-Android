package io.reader.android.runtime.http

import org.json.JSONObject
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderHttpDescriptorTest {
    @Test
    fun canonicalEmptyGetBodyDoesNotBecomeAnOkHttpRequestBody() {
        val descriptor = ReaderHttpDescriptor.decode(JSONObject()
            .put("url", "http://127.0.0.1:8080/library.js")
            .put("method", "GET")
            .put("body", "")
            .put("charset", "utf-8")
            .toString(), ReaderHttpLimits())
        assertNull(descriptor.body)
    }

    @Test
    fun emptyPostBodyStillGetsAnExplicitRequestBody() {
        val descriptor = ReaderHttpDescriptor.decode(JSONObject()
            .put("url", "http://127.0.0.1:8080/submit")
            .put("method", "POST")
            .put("body", "")
            .put("charset", "utf-8")
            .toString(), ReaderHttpLimits())
        assertNotNull(descriptor.body)
    }
}
