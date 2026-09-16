package com.avsp.pro.m8

import org.junit.Assert.*
import org.junit.Test

class M8TimelineTest {
    @Test fun fiveSecondCtaIsValid() {
        val t = M8Timeline("p1", ctaStart = 10.0, ctaEnd = 15.0)
        assertTrue(t.validate().isEmpty())
        assertTrue(t.validateCta())
    }
    @Test fun badCtaIsRejected() {
        val t = M8Timeline("p1", ctaStart = 10.0, ctaEnd = 14.0)
        assertTrue(t.validate().isNotEmpty())
    }
}
