package io.reader.rebuild.design

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransientToastStateTest {
    @Test
    fun deduplicatesOnlyWithinTheSameForegroundHostForTwoSeconds() {
        var now = 0L
        val first = TransientToastState { now }
        val second = TransientToastState { now }
        assertFalse(first.present("write", "保存完成", enabled = false))
        assertNull(first.message.value)
        assertTrue(first.present("write", "保存完成", enabled = true))
        val original = checkNotNull(first.message.value)
        assertTrue(second.present("write", "保存完成", enabled = true))
        now = 1_999
        assertFalse(first.present("write", "保存完成", enabled = true))
        now = 2_000
        assertTrue(first.present("write", "保存完成", enabled = true))
        first.expire(original)
        assertTrue(first.message.value != null)
        first.hide()
        assertNull(first.message.value)
    }
}
